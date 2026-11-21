/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unit tests for [PolarOnlineStreamingBridge] single-GATT PMD streaming, `HR`/`RR` forwarding,
 * `ACC`/`GYRO` acquisition, and [DeviceManager] routing (`ATT-8016`, `REQ-SET-112.2`, `TST-SET-114.3`).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PolarOnlineStreamingBridgeTest {

    private lateinit var context: Context
    private lateinit var sensorManager: MySensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DevicesDatabaseManager.resetForTesting()
        DeviceDataRepository.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        PolarPmdStreamCoordinator.resetWarningsForTesting()
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        PolarPmdStreamCoordinator.resetWarningsForTesting()
        sensorManager.shutDown()
        DeviceDataRepository.resetForTesting()
        DevicesDatabaseManager.resetForTesting()
    }

    @Test
    fun testDeviceManagerRouting_usesPolarBridgeWhenOptedIn_andBtleFallbackWhenDisabled() {
        // TST-SET-114.3
        val db = DevicesDatabaseManager.getInstance(context)
        val optedInId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:11",
            "Polar H10 1234ABCD",
            "Polar Electro",
            95,
            true
        )
        db.setPolarLiveImuEnabled(optedInId, true)

        val disabledId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:12",
            "Polar H10 99887766",
            "Polar Electro",
            95,
            true
        )

        val service = Robolectric.setupService(BANALService::class.java)
        val repo = DeviceDataRepository(context, Dispatchers.Unconfined, Dispatchers.Unconfined)
        val dm = DeviceManager(service, sensorManager, repo)
        val optedInDevice = dm.createNewBluetoothLEDevice(optedInId, DeviceType.HRM, "AA:BB:CC:DD:EE:11")
        val disabledDevice = dm.createNewBluetoothLEDevice(disabledId, DeviceType.HRM, "AA:BB:CC:DD:EE:12")

        assertTrue("Opted-in Polar H10 must instantiate PolarOnlineStreamingBridge", optedInDevice is PolarOnlineStreamingBridge)
        assertTrue("Disabled Polar H10 must keep BTLEHeartRateDevice", disabledDevice is BTLEHeartRateDevice)

        dm.shutDown()
    }

    @Test
    fun testPersistentSingleGattSession_streamsHrAndAccGyroWithAutoReconnect() {
        // TST-SET-114.3
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:33",
            "Polar Verity Sense ABCD1234",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSettings = PolarSensorSetting(
            mapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
                PolarSensorSetting.SettingType.RESOLUTION to 16,
                PolarSensorSetting.SettingType.RANGE to 8
            )
        )
        val gyroSettings = PolarSensorSetting(
            mapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
                PolarSensorSetting.SettingType.RESOLUTION to 16,
                PolarSensorSetting.SettingType.RANGE to 2000
            )
        )
        coEvery { mockApi.requestStreamSettings("ABCD1234", PolarBleApi.PolarDeviceDataType.ACC) } returns accSettings
        coEvery { mockApi.requestStreamSettings("ABCD1234", PolarBleApi.PolarDeviceDataType.GYRO) } returns gyroSettings
        every { mockApi.startHrStreaming("ABCD1234") } returns flowOf(
            PolarHrData(
                listOf(PolarHrData.PolarHrSample(148, 148, 0, listOf(410, 415), listOf(410, 415), true, true, true))
            )
        )
        every { mockApi.startAccStreaming("ABCD1234", any()) } returns flowOf(
            PolarAccelerometerData(listOf(PolarAccelerometerData.PolarAccelerometerDataSample(1_000_000L, 0, 0, 1000)))
        )
        every { mockApi.startGyroStreaming("ABCD1234", any()) } returns flowOf(
            PolarGyroData(listOf(PolarGyroData.PolarGyroDataSample(1_000_000L, 0.1f, -0.2f, 0.3f)))
        )

        val receivedAcc = mutableListOf<PolarAccelerometerData>()
        val receivedGyro = mutableListOf<PolarGyroData>()
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = "AA:BB:CC:DD:EE:33",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.addImuFrameListener(object : PolarOnlineStreamingBridge.ImuFrameListener {
            override fun onAccFrame(polarId: String, data: PolarAccelerometerData) {
                receivedAcc.add(data)
            }

            override fun onGyroFrame(polarId: String, data: PolarGyroData) {
                receivedGyro.add(data)
            }
        })

        bridge.startSearching()
        val cb = bridge.getSdkCallbackForTesting()
        cb.deviceConnected(PolarDeviceInfo("ABCD1234", "AA:BB:CC:DD:EE:33", -55, "Polar Verity Sense", true))
        cb.bleSdkFeatureReady("ABCD1234", PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        cb.bleSdkFeatureReady("ABCD1234", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

        verify { mockApi.setAutomaticReconnection(true) }
        verify { mockApi.connectToDevice("ABCD1234") }
        assertSame(mockApi, PolarOfflineGattCoordinator.getActiveSession("ABCD1234"))
        assertEquals(148, bridge.getSensor(SensorType.HR).value)
        assertEquals(1, receivedAcc.size)
        assertEquals(1, receivedGyro.size)

        bridge.shutDown()
        assertNull(PolarOfflineGattCoordinator.getActiveSession("ABCD1234"))
    }

    @Test
    fun testProcessHrData_dispatchesRrBeatSourceAndCumulativeTimestamps() {
        // TST-SET-114.3
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:44",
            "Polar H10 11223344",
            "Polar Electro",
            95,
            true
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:44",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )
        val events = mutableListOf<RrBeatEvent>()
        bridge.addRrBeatListener(RrBeatListener { events.add(it) })

        bridge.processHrData(
            PolarHrData(
                listOf(PolarHrData.PolarHrSample(152, 152, 0, listOf(500, 500), listOf(500, 500), true, true, true))
            ),
            packetArrivalMs = 10_000L
        )
        bridge.processHrData(
            PolarHrData(
                listOf(PolarHrData.PolarHrSample(154, 154, 0, listOf(490), listOf(490), true, true, true))
            ),
            packetArrivalMs = 10_500L
        )

        assertEquals(154, bridge.getSensor(SensorType.HR).value)
        assertNotNull(bridge.getSensor(SensorType.RR_INTERVAL))
        assertEquals(3, events.size)
        assertEquals(9_500L, events[0].beatTimestampMs)
        assertEquals(10_000L, events[1].beatTimestampMs)
        assertEquals(10_490L, events[2].beatTimestampMs)

        bridge.shutDown()
    }

    @Test
    fun testH10Bridge_seedsStridesBaselineAndPublishesCanonicalStridesPerMinWithStandstillZeroing() {
        // TST-SET-116.1
        // TST-SET-116.3
        // TST-SET-116.4
        // TST-SET-116.5
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:77",
            "Polar H10 77889900",
            "Polar Electro",
            95,
            true
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:77",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )

        // Feed 10s of 100 Hz H10 chest running frames at 170 steps/min (85.0 strides/min) in 200ms chunks
        // and verify stride 1 increments immediately from 0 -> 1 without baseline swallowing.
        var firstNonZeroStride: Int? = null
        val accSamples = (0..1000).map { idx ->
            val tMs = idx * 10L
            val zMg = (1000.0 + 650.0 * sin(2.0 * PI * (170.0 / 60.0) * (tMs / 1000.0))).roundToInt()
            PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
        }
        for (chunk in accSamples.chunked(20)) {
            bridge.processAccData(PolarAccelerometerData(chunk))
            val currentStrides = bridge.getSensor(SensorType.STRIDES).value as Int
            if (firstNonZeroStride == null && currentStrides > 0) {
                firstNonZeroStride = currentStrides
            }
        }

        val cadenceSpm = bridge.getSensor(SensorType.CADENCE).value as Double
        val strides = bridge.getSensor(SensorType.STRIDES).value as Int
        assertEquals("First detected stride must publish 1 immediately (seeded 0 baseline)", 1, firstNonZeroStride)
        assertEquals("H10 live bridge must publish 85.0 strides/min (half-step equivalence)", 85.0, cadenceSpm, 1.0)
        assertTrue("Seeded MyIntegerAccumulatorSensor must reflect >= 12 strides, got $strides", strides >= 12)

        // Feed 4s of flat standstill -> CADENCE must zero to 0.0 with zero phantom strides.
        val standstillSamples = (1001..1400).map { idx ->
            val tMs = idx * 10L
            PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, 1000)
        }
        bridge.processAccData(PolarAccelerometerData(standstillSamples))

        assertEquals(0.0, bridge.getSensor(SensorType.CADENCE).value as Double, 0.0)
        assertEquals(strides, bridge.getSensor(SensorType.STRIDES).value as Int)

        bridge.shutDown()
    }

    @Test
    fun testVerityBridge_processesGyroAndAccIntoStridesPerMin() {
        // TST-SET-116.2
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:88",
            "Polar Verity Sense 8899AABB",
            "Polar Electro",
            95,
            true
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = "AA:BB:CC:DD:EE:88",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )

        val strideFreqHz = 84.0 / 60.0 // 84.0 strides/min
        val packetStepMs = 1000.0 / 52.0
        for (pktStart in 0 until 520 step 10) {
            val accList = mutableListOf<PolarAccelerometerData.PolarAccelerometerDataSample>()
            val gyroList = mutableListOf<PolarGyroData.PolarGyroDataSample>()
            for (k in pktStart until minOf(pktStart + 10, 520)) {
                val tMs = (k * packetStepMs).roundToInt().toLong()
                val tNs = tMs * 1_000_000L
                val wave = sin(2.0 * PI * strideFreqHz * (tMs / 1000.0))
                accList.add(PolarAccelerometerData.PolarAccelerometerDataSample(tNs, 0, 0, 1000))
                gyroList.add(
                    PolarGyroData.PolarGyroDataSample(
                        tNs,
                        (120.0 * wave).toFloat(),
                        (-110.0 * wave).toFloat(),
                        (80.0 * wave).toFloat()
                    )
                )
            }
            bridge.processAccData(PolarAccelerometerData(accList))
            bridge.processGyroData(PolarGyroData(gyroList))
        }

        val cadenceSpm = bridge.getSensor(SensorType.CADENCE).value as Double
        val strides = bridge.getSensor(SensorType.STRIDES).value as Int
        assertEquals("Verity Sense arm-swing bridge must publish 84.0 strides/min", 84.0, cadenceSpm, 1.5)
        assertTrue("Verity Sense bridge must increment SensorType.STRIDES, got $strides", strides >= 10)
        assertNull("Verity Sense must never register H10 Running Dynamics VO", bridge.getSensor(SensorType.VERTICAL_OSCILLATION))
        assertNull("Verity Sense must never register H10 Running Dynamics GCT", bridge.getSensor(SensorType.GROUND_CONTACT_TIME))
        assertEquals(false, bridge.highRateRunningDynamicsActive)

        bridge.shutDown()
    }

    @Test
    fun testH10Running200HzActivation_publishesRunningDynamics_andResetsOnSportSwitch() {
        // TST-SET-118.2
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:99",
            "Polar H10 99001122",
            "Polar Electro",
            95,
            true
        )

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val selected200Setting = PolarSensorSetting(
            mapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to 200,
                PolarSensorSetting.SettingType.RESOLUTION to 16,
                PolarSensorSetting.SettingType.RANGE to 8
            )
        )
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
            every { maxSettings() } returns selected200Setting
        }
        val capturedSettings = mutableListOf<PolarSensorSetting>()
        coEvery { mockApi.requestStreamSettings("99001122", PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startAccStreaming("99001122", capture(capturedSettings)) } returns flowOf()
        every { mockApi.startHrStreaming("99001122") } returns flowOf()

        var currentSport = BSportType.RUN
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:99",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { currentSport },
            apiFactory = { _, _ -> mockApi }
        )

        bridge.startSearching()
        val cb = bridge.getSdkCallbackForTesting()
        cb.deviceConnected(PolarDeviceInfo("99001122", "AA:BB:CC:DD:EE:99", -55, "Polar H10", true))
        cb.bleSdkFeatureReady("99001122", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

        assertTrue("H10 in BSportType.RUN must activate highRateRunningDynamicsActive", bridge.highRateRunningDynamicsActive)
        assertTrue("H10 must negotiate 200 Hz ACC sample rate", capturedSettings.isNotEmpty())
        assertEquals(200, capturedSettings.first().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.first())

        // Stream 8s of 200 Hz sternum running ACC at 172 spm, ~8.5 cm VO
        val fStep = 172.0 / 60.0
        val omega = 2.0 * PI * fStep
        val ampMg = ((0.085 / 2.0) * omega * omega / 9.80665) * 1000.0
        val samples200Hz = (0..1600).map { i ->
            val tMs = i * 5L
            val zMg = (1000.0 + ampMg * cos(omega * (tMs / 1000.0))).roundToInt()
            PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
        }
        for (chunk in samples200Hz.chunked(40)) {
            bridge.processAccData(PolarAccelerometerData(chunk))
        }

        val voCm = bridge.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double
        val gctMs = bridge.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double
        assertNotNull("VERTICAL_OSCILLATION sensor must emit value on 200 Hz H10 run", voCm)
        assertNotNull("GROUND_CONTACT_TIME sensor must emit value on 200 Hz H10 run", gctMs)
        assertTrue("VO must stay inside physiological 5.0..13.0 cm (got $voCm)", voCm!! in 5.0..13.0)
        assertTrue("GCT must stay inside physiological 140..400 ms (got $gctMs)", gctMs!! in 140.0..400.0)

        // Switching to BIKE cleanly deactivates 200 Hz flag and unregisters VO/GCT
        currentSport = BSportType.BIKE
        bridge.syncCadenceSensorRegistration()
        assertEquals(false, bridge.highRateRunningDynamicsActive)
        assertNull(bridge.getSensor(SensorType.VERTICAL_OSCILLATION))
        assertNull(bridge.getSensor(SensorType.GROUND_CONTACT_TIME))

        bridge.shutDown()
    }

    @Test
    fun testSearchTimeout_triggersNotifyStopSearchingFalse_andDisconnects() = runTest {
        // Traces to: REQ-CON-045.1, TST-CON-045.1
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:01",
            "Polar H10 00112233",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        shadowApp.clearBroadcastIntents()

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:01",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        try {
            bridge.startSearching()
            assertTrue("Bridge must be in searching state", bridge.isSearching)
            verify(exactly = 1) { mockApi.connectToDevice("00112233") }

            // Advance time past the 5000ms timeout
            testScheduler.advanceTimeBy(5001L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertFalse("Bridge must stop searching after timeout", bridge.isSearching)
            verify(atLeast = 1) { mockApi.disconnectFromDevice("00112233") }
            val stopIntent = shadowApp.broadcastIntents.lastOrNull {
                it.action == BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == deviceId
            }
            assertNotNull("SEARCHING_STOPPED_FOR_ONE_INTENT must be broadcast", stopIntent)
            assertFalse("Timeout must notify failure (success=false)", stopIntent!!.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, true))
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testStartSearching_withExistingPolarApi_reconnectsAndArmsTimeout() = runTest {
        // Traces to: REQ-CON-045.2, TST-CON-045.2
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:02",
            "Polar H10 22334455",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:02",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        try {
            bridge.startSearching()
            verify(exactly = 1) { mockApi.connectToDevice("22334455") }

            // Simulate disconnect -> bridge stays disconnected
            bridge.getSdkCallbackForTesting().deviceDisconnected(PolarDeviceInfo("22334455", "AA:BB:CC:DD:EE:02", -60, "Polar H10", true))
            assertFalse(bridge.isReceivingData())

            // Re-trigger startSearching with polarApi already populated
            bridge.startSearching()
            assertTrue(bridge.isSearching)
            assertNotNull(bridge.getSearchTimeoutJobForTesting())
            assertTrue(bridge.getSearchTimeoutJobForTesting()!!.isActive)
            verify(exactly = 2) { mockApi.connectToDevice("22334455") }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testStartSearching_whenAlreadyConnected_immediatelyDispatchesStopSearchingTrue() = runTest {
        // Traces to: REQ-CON-045.2, TST-CON-045.2
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:03",
            "Polar H10 33445566",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        shadowApp.clearBroadcastIntents()

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:03",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        try {
            bridge.startSearching()
            bridge.getSdkCallbackForTesting().deviceConnected(PolarDeviceInfo("33445566", "AA:BB:CC:DD:EE:03", -50, "Polar H10", true))
            assertTrue(bridge.isReceivingData())
            shadowApp.clearBroadcastIntents()

            // Invoke startSearching again while already connected
            bridge.startSearching()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val stopIntent = shadowApp.broadcastIntents.lastOrNull {
                it.action == BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == deviceId
            }
            assertNotNull("SEARCHING_STOPPED_FOR_ONE_INTENT must be broadcast immediately", stopIntent)
            assertTrue("Must notify search success", stopIntent!!.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false))
            assertNull("Timeout watchdog should not be armed when already connected", bridge.getSearchTimeoutJobForTesting())
            verify(exactly = 1) { mockApi.connectToDevice("33445566") }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testDeviceConnected_cancelsSearchTimeoutWatchdog() = runTest {
        // Traces to: REQ-CON-045.3, TST-CON-045.3
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:04",
            "Polar H10 44556677",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        shadowApp.clearBroadcastIntents()

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:04",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        try {
            bridge.startSearching()
            assertTrue(bridge.isSearching)
            assertNotNull(bridge.getSearchTimeoutJobForTesting())

            // Simulate connection before timeout
            bridge.getSdkCallbackForTesting().deviceConnected(PolarDeviceInfo("44556677", "AA:BB:CC:DD:EE:04", -50, "Polar H10", true))
            assertNull("Timeout watchdog must be cancelled and null on connection", bridge.getSearchTimeoutJobForTesting())
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val stopIntent = shadowApp.broadcastIntents.lastOrNull {
                it.action == BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == deviceId
            }
            assertNotNull("SEARCHING_STOPPED_FOR_ONE_INTENT must be broadcast", stopIntent)
            assertTrue("Connection must notify success", stopIntent!!.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, false))

            // Advance time past the original 5000ms timeout
            testScheduler.advanceTimeBy(6000L)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val stopIntentsCount = shadowApp.broadcastIntents.count {
                it.action == BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == deviceId
            }
            assertEquals("No additional timeout events should be sent", 1, stopIntentsCount)
            verify(exactly = 0) { mockApi.disconnectFromDevice(any()) }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testBlePowerStateChanged_false_cancelsSearchTimeoutAndResetsState() = runTest {
        // Traces to: REQ-CON-045.4, TST-CON-045.4
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:05",
            "Polar H10 55667788",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        shadowApp.clearBroadcastIntents()

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:05",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        try {
            bridge.startSearching()
            assertTrue(bridge.isSearching)

            // BT adapter powers off
            bridge.getSdkCallbackForTesting().blePowerStateChanged(false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertFalse(bridge.isSearching)
            assertNull(bridge.getSearchTimeoutJobForTesting())

            val stopIntent = shadowApp.broadcastIntents.lastOrNull {
                it.action == BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT &&
                    it.getLongExtra(BANALService.DEVICE_ID, -1L) == deviceId
            }
            assertNotNull("SEARCHING_STOPPED_FOR_ONE_INTENT must be broadcast on BT power off while searching", stopIntent)
            assertFalse("Power off while searching must notify failure", stopIntent!!.getBooleanExtra(BANALService.SEARCHING_FINISHED_SUCCESS, true))
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testShutDown_cancelsSearchTimeoutCleanly() = runTest {
        // Traces to: REQ-CON-045.4, TST-CON-045.4
        val db = DevicesDatabaseManager.getInstance(context)
        val deviceId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:06",
            "Polar H10 66778899",
            "Polar Electro",
            95,
            true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = deviceId,
            macAddress = "AA:BB:CC:DD:EE:06",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5000L }
        )

        bridge.startSearching()
        val job = bridge.getSearchTimeoutJobForTesting()
        assertNotNull(job)
        assertTrue(job!!.isActive)

        bridge.shutDown()
        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun testTwoBridges_shareSinglePolarBleApi_multiplexCallbacksAndFilterForeignDevices() = runTest {
        // TST-SET-120.1, TST-SET-120.2, TST-SET-120.3
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "24:AC:AC:25:0F:A6",
            "Polar H10",
            "Polar Electro",
            95,
            true
        )
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "24:AC:AC:24:82:56",
            "Polar Verity Sense A1B2C3D4",
            "Polar Electro",
            95,
            true
        )

        val sharedMockApi = mockk<PolarBleApi>(relaxed = true)
        var factoryCalls = 0
        var capturedFeatures: Set<PolarBleApi.PolarBleSdkFeature>? = null
        val sharedFactory: (Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi = { _, features ->
            factoryCalls++
            capturedFeatures = features
            sharedMockApi
        }
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val h10Bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "24:AC:AC:25:0F:A6",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = sharedFactory,
            searchTimeoutMsProvider = { 5000L }
        )
        val verityBridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = "24:AC:AC:24:82:56",
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = sharedFactory,
            searchTimeoutMsProvider = { 5000L }
        )

        h10Bridge.startSearching()
        verityBridge.startSearching()

        assertEquals(1, factoryCalls)
        assertEquals(PolarOfflineGattCoordinator.ALL_POLAR_FEATURES, capturedFeatures)
        verify(exactly = 1) { sharedMockApi.setApiCallback(any()) }

        // Foreign deviceConnected on h10Bridge callback must be ignored; matching MAC connects H10 only
        val verityInfo = PolarDeviceInfo("A1B2C3D4", "24:AC:AC:24:82:56", -55, "Polar Verity Sense", true)
        h10Bridge.getSdkCallbackForTesting().deviceConnected(verityInfo)
        assertFalse(h10Bridge.isReceivingData())

        val h10Info = PolarDeviceInfo("1C3B2421", "24:AC:AC:25:0F:A6", -48, "Polar H10", true)
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        mux.deviceConnected(h10Info)
        assertTrue(h10Bridge.isReceivingData())
        assertFalse(verityBridge.isReceivingData())

        // Foreign deviceDisconnected must not disconnect H10
        h10Bridge.getSdkCallbackForTesting().deviceDisconnected(verityInfo)
        assertTrue(h10Bridge.isReceivingData())

        // Shutting down H10 bridge keeps Verity's shared singleton alive
        h10Bridge.shutDown()
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 0) { sharedMockApi.shutDown() }

        // Shutting down Verity bridge releases the final reference and shuts down singleton cleanly
        verityBridge.shutDown()
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 1) { sharedMockApi.shutDown() }
    }

    @Test
    fun testProcessHrData_driftReAnchorClampsMonotonicAndFlagsMultiSecondGap() {
        // TST-TRK-085.1, TST-TRK-085.2
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "AA:BB:CC:DD:EE:99",
            "Polar H10 99001122",
            "Polar Electro",
            95,
            true
        )
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = "AA:BB:CC:DD:EE:99",
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )
        val events = mutableListOf<RrBeatEvent>()
        bridge.addRrBeatListener(RrBeatListener { events.add(it) })

        bridge.processHrData(
            PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(800), listOf(800), true, true, true))),
            packetArrivalMs = 100_000L
        )
        // Bundle with 4x800ms arriving at 100_500L (drift = 2700 ms > 2000 ms) must clamp >= 100_000L
        bridge.processHrData(
            PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(800, 800, 800, 800), listOf(800, 800, 800, 800), true, true, true))),
            packetArrivalMs = 100_500L
        )
        // Multi-second 5196 ms RR arriving on time (0 drift) + sub-minimum 180 ms RR
        bridge.processHrData(
            PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(5196), listOf(5196), true, true, true))),
            packetArrivalMs = 105_696L
        )
        bridge.processHrData(
            PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(180), listOf(180), true, true, true))),
            packetArrivalMs = 105_876L
        )

        var prevTs = 0L
        for (event in events) {
            assertTrue("Timestamp ${event.beatTimestampMs} must be >= $prevTs", event.beatTimestampMs >= prevTs)
            prevTs = event.beatTimestampMs
        }
        val multiSecEvent = events[events.size - 2]
        assertEquals(RrBeatEvent.ARTIFACT_GAP, multiSecEvent.artifactFlag)
        assertTrue("Expected gapBeats > 0 for 5196ms RR", multiSecEvent.gapBeats > 0)
        assertEquals(RrBeatEvent.ARTIFACT_GAP, events.last().artifactFlag)

        bridge.shutDown()
    }

    @Test
    fun testRenamedVeritySense_seedsAliasesPreConnectAndHydratesViaDisCallbacks() {
        // TST-CON-049.3
        val mac = "AA:BB:CC:DD:EE:61"
        PolarOfflineCapabilityStore.recordDisMetadata(context, mac, modelNumber = "Verity Sense", serialNumber = "CAFE5678")

        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            mac,
            "Armband",
            "Polar Electro",
            95,
            true
        )

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        // Pre-connect verification: polarId, alias seeding, and isVeritySense (RR_PRIORITY_PPG parity) BEFORE deviceConnected
        assertEquals("CAFE5678", bridge.polarId)
        assertTrue(PolarOfflineGattCoordinator.matchesDeviceIdentifier("CAFE5678", mac))
        assertTrue(bridge.isVeritySense)

        bridge.shutDown()
    }

    @Test
    fun testSdkDisInformationReceived_hydratesRenamedSensorAndPreemptsLegacyOfflineRec() {
        // TST-CON-049.3
        val verityMac = "AA:BB:CC:DD:EE:62"
        val h10Mac = "AA:BB:CC:DD:EE:63"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, verityMac, "Armband", "", 95, true)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, h10Mac, "Chest Strap", "", 95, true)
        // Legacy PFTP mark on H10 must be overridden once DIS/SDK reports H10
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, h10Mac)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val verityBridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        verityBridge.startSearching()
        val multiplexer = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        multiplexer.disInformationReceived(verityMac, BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER, "Verity Sense")
        multiplexer.disInformationReceived(verityMac, DisInfo(BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER.toString(), "DEAD0001"))

        assertTrue(verityBridge.isVeritySense)
        assertEquals("DEAD0001", verityBridge.polarId)
        assertTrue(PolarOfflineGattCoordinator.matchesDeviceIdentifier("DEAD0001", verityMac))

        val h10Bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = h10Mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        h10Bridge.startSearching()
        multiplexer.disInformationReceived(h10Mac, BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER, "H10")
        multiplexer.bleSdkFeatureReady(h10Mac, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)

        assertFalse("Renamed H10 with DIS H10 must never classify as Verity Sense", h10Bridge.isVeritySense)

        verityBridge.shutDown()
        h10Bridge.shutDown()
    }

    @Test
    fun testStreamSettingsFailure_isCaughtGracefullyAndNonHexDisSerialPreservesCanonical8HexId() {
        // Stage 6 Bounce 1/3 heal: GYRO stream settings rejection never crashes Dispatchers.IO,
        // and non-8-hex DIS serial never overwrites canonical 8-hex PolarDeviceInfo.deviceId.
        val mac = "AA:BB:CC:DD:EE:64"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Chest Strap", "", 95, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        coEvery {
            mockApi.requestStreamSettings(any(), PolarBleApi.PolarDeviceDataType.ACC)
        } throws IllegalStateException("Simulated ACC setting rejection")
        coEvery {
            mockApi.requestStreamSettings(any(), PolarBleApi.PolarDeviceDataType.GYRO)
        } throws UnsupportedOperationException("H10 does not support GYRO")

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        mux.deviceConnected(PolarDeviceInfo("0A1B2C3D", mac, -55, "Chest Strap", true))
        mux.disInformationReceived(mac, BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER, "1234567890123")
        assertEquals("0A1B2C3D", bridge.polarId)

        // Trigger online streaming readiness as Verity Sense temporarily so both ACC and GYRO launch
        mux.disInformationReceived(mac, BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER, "Verity Sense")
        mux.bleSdkFeatureReady("0A1B2C3D", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

        assertFalse(bridge.isAccStreamingForTesting())
        assertFalse(bridge.isGyroStreamingForTesting())
        assertEquals(null, PolarOfflineGattCoordinator.getAccChannelOwner("0A1B2C3D"))

        bridge.shutDown()
    }

    @Test
    fun testConnectPersistentSession_whenSessionAlreadyOpenAndFeaturesReady_syncsConnectedAndStartsStreams() {
        // TST-CON-050.2
        val mac = "24:AC:AC:24:82:56"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sense CAFE1234", "Polar Electro", 95, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val gyroSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.isFeatureReady("CAFE1234", PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true
        every { mockApi.isFeatureReady("CAFE1234", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        every { mockApi.isFeatureReady("CAFE1234", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING) } returns true
        every { mockApi.startHrStreaming("CAFE1234") } returns flowOf(
            PolarHrData(listOf(PolarHrData.PolarHrSample(132, 132, 0, listOf(455), listOf(455), true, true, true)))
        )
        coEvery { mockApi.requestStreamSettings("CAFE1234", PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        coEvery { mockApi.requestStreamSettings("CAFE1234", PolarBleApi.PolarDeviceDataType.GYRO) } returns gyroSetting
        every { mockApi.startAccStreaming("CAFE1234", any()) } returns flowOf(
            PolarAccelerometerData(listOf(PolarAccelerometerData.PolarAccelerometerDataSample(1000L, 1, 2, 1000)))
        )
        every { mockApi.startGyroStreaming("CAFE1234", any()) } returns flowOf(
            PolarGyroData(listOf(PolarGyroData.PolarGyroDataSample(1000L, 0.1f, 0.2f, 0.3f)))
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )

        // Start searching without emitting deviceConnected or bleSdkFeatureReady -> SESSION_OPEN sync must recover
        bridge.startSearching()

        assertTrue("Bridge must sync isReceivingData on already-open session", bridge.isReceivingData())
        assertTrue(PolarOnlineStreamingBridge.getActiveBridgesForTesting().contains(bridge))
        verify(exactly = 1) { mockApi.startHrStreaming("CAFE1234") }
        verify(exactly = 1) { mockApi.startAccStreaming("CAFE1234", any()) }
        verify(exactly = 1) { mockApi.startGyroStreaming("CAFE1234", any()) }
        assertEquals(132, bridge.getSensor(SensorType.HR).value)
        assertEquals(132, sensorManager.getSensor(SensorType.HR)?.value)

        bridge.shutDown()
    }

    @Test
    fun testPolarBleSessionId_remainsConsistentAcrossMidSessionMacTo8HexFlip() {
        // TST-CON-050.3
        val mac = "AA:BB:CC:DD:EE:70"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Armband", "", 95, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.startHrStreaming(mac) } returns flowOf(
            PolarHrData(listOf(PolarHrData.PolarHrSample(141, 141, 0, listOf(425), listOf(425), true, true, true)))
        )
        coEvery { mockApi.requestStreamSettings(mac, PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        every { mockApi.startAccStreaming(mac, any()) } returns flowOf(
            PolarAccelerometerData(listOf(PolarAccelerometerData.PolarAccelerometerDataSample(1000L, 1, 2, 1000)))
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()
        assertEquals(mac, bridge.getPolarBleSessionIdForTesting())

        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        mux.deviceConnected(PolarDeviceInfo("CAFE9999", mac, -55, "Armband", true))
        // Display/canonical polarId updated to 8-hex, but active SDK session stays locked to mac
        assertEquals("CAFE9999", bridge.polarId)
        assertEquals(mac, bridge.getPolarBleSessionIdForTesting())

        mux.bleSdkFeatureReady("CAFE9999", PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        mux.bleSdkFeatureReady("CAFE9999", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

        verify(exactly = 1) { mockApi.connectToDevice(mac) }
        verify(exactly = 1) { mockApi.startHrStreaming(mac) }
        coVerify(exactly = 1) { mockApi.requestStreamSettings(mac, PolarBleApi.PolarDeviceDataType.ACC) }
        verify(exactly = 1) { mockApi.startAccStreaming(mac, any()) }
        verify(exactly = 0) { mockApi.startHrStreaming("CAFE9999") }

        bridge.shutDown()
        verify(exactly = 1) { mockApi.disconnectFromDevice(mac) }
    }

    @Test
    fun testDeviceDisconnected_cancelsStaleHrAccGyroJobs() {
        // TST-CON-050.3
        val mac = "AA:BB:CC:DD:EE:71"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sense DEAD7171", "Polar Electro", 95, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val gyroSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.startHrStreaming("DEAD7171") } returns flow { awaitCancellation() }
        coEvery { mockApi.requestStreamSettings("DEAD7171", PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        coEvery { mockApi.requestStreamSettings("DEAD7171", PolarBleApi.PolarDeviceDataType.GYRO) } returns gyroSetting
        every { mockApi.startAccStreaming("DEAD7171", any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming("DEAD7171", any()) } returns flow { awaitCancellation() }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        val info = PolarDeviceInfo("DEAD7171", mac, -55, "Polar Sense DEAD7171", true)
        mux.deviceConnected(info)
        mux.bleSdkFeatureReady("DEAD7171", PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        mux.bleSdkFeatureReady("DEAD7171", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

        assertTrue(bridge.isHrJobActiveForTesting())
        assertTrue(bridge.isAccJobActiveForTesting())
        assertTrue(bridge.isGyroJobActiveForTesting())

        mux.deviceDisconnected(info)

        assertFalse(bridge.isReceivingData())
        assertFalse(bridge.isHrJobActiveForTesting())
        assertFalse(bridge.isAccJobActiveForTesting())
        assertFalse(bridge.isGyroJobActiveForTesting())
        assertFalse(bridge.isAccStreamingForTesting())
        assertFalse(bridge.isGyroStreamingForTesting())

        bridge.shutDown()
    }

    @Test
    fun testConnectPersistentSession_whenSessionOpenWithDeferredFeatureReady_marksConnectedOnFirstFeature() {
        // TST-CON-050.2 (Bounce 1/3 heal): SESSION_OPEN where isFeatureReady is initially false across all features
        val mac = "24:AC:AC:24:82:58"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sense CAFE5858", "Polar Electro", 95, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.isFeatureReady(any(), any()) } returns false
        every { mockApi.startHrStreaming(any()) } returns flowOf(
            PolarHrData(listOf(PolarHrData.PolarHrSample(128, 128, 0, listOf(468), listOf(468), true, true, true)))
        )

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()
        assertFalse(bridge.isReceivingData())

        // Deferred feature callback arrives without re-emitting deviceConnected
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        mux.bleSdkFeatureReady("CAFE5858", PolarBleApi.PolarBleSdkFeature.FEATURE_HR)

        assertTrue("First bleSdkFeatureReady on SESSION_OPEN must mark bridge connected", bridge.isReceivingData())
        assertTrue(PolarOnlineStreamingBridge.getActiveBridgesForTesting().contains(bridge))
        assertEquals(128, bridge.getSensor(SensorType.HR).value)
        assertFalse("Search timeout watchdog must be cancelled", bridge.getSearchTimeoutJobForTesting()?.isActive == true)

        bridge.shutDown()
    }

    @Test
    fun testShutDown_whenActiveSdkStreamIdDiffersFromCoordinatorSessionKey_cleansUpCoordinatorSession() {
        // TST-CON-050.2 / .3 (Bounce 1/3 heal): coordinatorSessionKey preserved for releasePersistentSession
        val mac = "24:AC:AC:24:82:59"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Armband", "", 95, true)
        PolarOfflineCapabilityStore.recordDisMetadata(context, mac, modelNumber = "Verity Sense", serialNumber = "CAFE5959")

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.isFeatureReady(mac, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.requestStreamSettings(mac, PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        every { mockApi.startAccStreaming(mac, any()) } returns flowOf()

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.startSearching()

        assertEquals("CAFE5959", bridge.getCoordinatorSessionKeyForTesting())
        assertEquals(mac, bridge.getPolarBleSessionIdForTesting())
        assertSame(mockApi, PolarOfflineGattCoordinator.getActiveSession("CAFE5959"))

        bridge.shutDown()

        assertNull(PolarOfflineGattCoordinator.getActiveSession("CAFE5959"))
        assertNull(PolarOfflineGattCoordinator.getActiveSession(mac))
        assertEquals(0, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
    }

    @Test
    fun testRealBtleHeartRateShutDownWithCallback_runsSubclassCleanupAndGattCloseBeforeCallback() {
        // TST-CON-050.1 (Bounce 1/3 heal): Real MyBTLEDevice.shutDown(Runnable) -> BTLEHeartRateDevice.shutDown()
        val mac = "AA:BB:CC:DD:EE:80"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar H10 8080ABCD", "Polar Electro", 95, true)

        val btleDevice = BTLEHeartRateDevice(context, sensorManager, devId, mac)
        val rrReceived = mutableListOf<RrBeatEvent>()
        val rrListener = RrBeatListener { rrReceived.add(it) }
        btleDevice.addRrBeatListener(rrListener)

        // Emit one RR beat so mRrIntervalSensor and RR state exist, then attach mocked BluetoothGatt
        btleDevice.parseHeartRateMeasurement(byteArrayOf(0x10, 72, 0x00, 0x04), 1000L)
        assertEquals(1, rrReceived.size)

        val events = mutableListOf<String>()
        Shadows.shadowOf(context.applicationContext as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        every { mockGatt.disconnect() } answers { events.add("gatt_disconnect") }
        every { mockGatt.close() } answers { events.add("gatt_close") }
        btleDevice.setBluetoothGattForTesting(mockGatt)

        val callbackRan = AtomicBoolean(false)
        btleDevice.shutDown {
            events.add("callback")
            callbackRan.set(true)
        }

        // Subclass BTLEHeartRateDevice.shutDown() cleared RR sensor & listeners immediately, while callback awaits mHandler GATT close
        assertNull("RR sensor must be cleared on shutDown", btleDevice.rrIntervalSensor)
        btleDevice.parseHeartRateMeasurement(byteArrayOf(0x10, 74, 0x00, 0x04), 2000L)
        assertEquals("BTLEHeartRateDevice.shutDown() must clear mRrBeatListeners", 1, rrReceived.size)
        assertFalse("Completion callback must wait for mHandler GATT close", callbackRan.get())

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(callbackRan.get())
        assertEquals(listOf("gatt_disconnect", "gatt_close", "callback"), events)
    }

    @Test
    fun testReverseHandoff_polarBridgeToBtleHeartRateDevice_preservesProxySensorAndRrArbitration() = kotlinx.coroutines.runBlocking {
        // TST-CON-050.1 / .4 (Bounce 1/3 heal): Real DeviceManager reverse handoff Bridge -> BTLEHeartRateDevice
        val mac = "AA:BB:CC:DD:EE:81"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar H10 8181ABCD", "Polar Electro", 95, true)
        db.setPolarLiveImuEnabled(devId, true)

        val repo = DeviceDataRepository(context.applicationContext as Application, Dispatchers.Unconfined, Dispatchers.Unconfined)
        DeviceDataRepository.resetForTesting(repo)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> mockApi }

        val service = Robolectric.setupService(BANALService::class.java)
        val deviceManager = DeviceManager(service, sensorManager, repo)
        val forwardedRr = mutableListOf<RrBeatEvent>()
        deviceManager.addRrBeatListener { forwardedRr.add(it) }

        deviceManager.createPairedRemoteDevices(com.atrainingtracker.banalservice.Protocol.BLUETOOTH_LE)
        val bridge = deviceManager.remoteDeviceList.single { it.deviceId == devId } as PolarOnlineStreamingBridge
        bridge.startSearching()
        PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting().deviceConnected(
            PolarDeviceInfo("8181ABCD", mac, -55, "Polar H10 8181ABCD", true)
        )

        // Deliver HR + RR via bridge -> ProxySensor and DeviceManager RR arbitration receive them
        bridge.processHrData(
            PolarHrData(listOf(PolarHrData.PolarHrSample(135, 135, 0, listOf(444), listOf(444), true, true, true))),
            packetArrivalMs = 1000L
        )
        assertEquals(135, sensorManager.getSensor(SensorType.HR)?.value)
        assertEquals(1, forwardedRr.size)

        // Toggle polarLiveImuEnabled = false via repo.updateDevice() and dispatch POLAR_LIVE_IMU_CHANGED
        repo.updateDevice(repo.getDeviceSnapshotById(devId)!!.copy(polarLiveImuEnabled = false))
        val toggleIntent = Intent(BANALService.POLAR_LIVE_IMU_CHANGED)
            .putExtra(BANALService.DEVICE_ID, devId)
            .putExtra(BANALService.POLAR_LIVE_IMU_ENABLED, false)
        deviceManager.polarLiveImuChangedReceiverForTesting.onReceive(context, toggleIntent)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val replacement = deviceManager.remoteDeviceList.single { it.deviceId == devId }
        assertTrue("Reverse toggle must replace bridge with BTLEHeartRateDevice", replacement is BTLEHeartRateDevice)
        val btleDevice = replacement as BTLEHeartRateDevice
        // Simulate GATT service connection registering replacement HR sensor into MySensorManager
        sensorManager.registerSensor(btleDevice.heartRateSensor)

        // Replacement BTLEHeartRateDevice continues delivering HR and RR through retained ProxySensor & DeviceManager
        btleDevice.parseHeartRateMeasurement(byteArrayOf(0x10, 142.toByte(), 0x00, 0x04), 2000L)
        assertEquals(142, sensorManager.getSensor(SensorType.HR)?.value)
        assertEquals(2, forwardedRr.size)
        assertSame(btleDevice, deviceManager.activePrimaryRrSourceForTesting)

        deviceManager.shutDown()
    }

    @Test
    fun testMidSessionDisconnectAndTimeout_usesExtendedBackoffAndCancelsOnShutDown() = runTest {
        // Traces to: REQ-CON-052.2, TST-CON-052.2
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        try {
            val db = DevicesDatabaseManager.getInstance(context)
            val deviceId = db.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM,
                "AA:BB:CC:DD:EE:77",
                "Polar Sense 77889900",
                "Polar Electro",
                90,
                true
            )
            val mockApi = mockk<PolarBleApi>(relaxed = true)
            every { mockApi.startHrStreaming(any()) } returns flow { awaitCancellation() }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val bridge = PolarOnlineStreamingBridge(
                context = context,
                mySensorManager = sensorManager,
                deviceId = deviceId,
                macAddress = "AA:BB:CC:DD:EE:77",
                dispatcher = testDispatcher,
                sdkIntProvider = { 34 },
                sportTypeProvider = { BSportType.RUN },
                apiFactory = { _, _ -> mockApi },
                searchTimeoutMsProvider = { 5000L }
            )

            try {
                assertTrue(bridge.shouldKeepAliveOnSearchTimeout())
                bridge.startSearching()
                assertEquals(1_000L, bridge.getCurrentReconnectDelayMsForTesting())
                verify(exactly = 1) { mockApi.connectToDevice("77889900") }

                // Simulate mid-workout timeout chain (5s search watchdog -> 1s -> 2s -> 4s -> 8s -> 16s -> 32s...)
                testScheduler.advanceTimeBy(5_001L)
                testScheduler.runCurrent()
                assertFalse("Queue must unblock on timeout even during TRACKING", bridge.isSearching)
                assertEquals(2_000L, bridge.getCurrentReconnectDelayMsForTesting())
                assertTrue(bridge.getReconnectJobForTesting()?.isActive == true)

                val observedDelays = mutableListOf(2_000L)
                // Fire 1s backoff -> triggers reconnect attempt -> wait 5s timeout -> advances to 4s -> repeat
                val waitSteps = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L)
                for (stepMs in waitSteps) {
                    testScheduler.advanceTimeBy(stepMs + 1L)
                    testScheduler.runCurrent()
                    testScheduler.advanceTimeBy(5_001L)
                    testScheduler.runCurrent()
                    observedDelays.add(bridge.getCurrentReconnectDelayMsForTesting())
                }
                assertEquals(
                    listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 300_000L, 300_000L),
                    observedDelays
                )

                // Reconnect succeeds -> delay resets to 1,000 ms
                bridge.getSdkCallbackForTesting().deviceConnected(
                    PolarDeviceInfo("77889900", "AA:BB:CC:DD:EE:77", -55, "Polar Sense", true)
                )
                assertEquals(1_000L, bridge.getCurrentReconnectDelayMsForTesting())
                assertNull(bridge.getReconnectJobForTesting())

                // When periodic research disabled mid-workout -> caps at 16,000 ms
                com.atrainingtracker.trainingtracker.settings.SensorSearchSettings.setPeriodicResearchEnabled(context, false)
                bridge.getSdkCallbackForTesting().deviceDisconnected(
                    PolarDeviceInfo("77889900", "AA:BB:CC:DD:EE:77", -55, "Polar Sense", true)
                )
                assertEquals(2_000L, bridge.getCurrentReconnectDelayMsForTesting())
                val countBeforeShutDown = bridge.getReconnectJobForTesting()
                assertNotNull(countBeforeShutDown)
                bridge.shutDown()
                assertTrue(bridge.getReconnectJobForTesting()?.isCancelled != false)
            } finally {
                bridge.shutDown()
            }
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        }
    }

    @Test
    fun testTimeoutDisconnectEcho_doesNotDoubleAdvance_andBlePowerStateCancelsReconnect_andExceptionReschedules() = runTest {
        // ATT-8965 Bounce 2 regression tests for Fix 4a/4b/4c
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        try {
            val db = DevicesDatabaseManager.getInstance(context)
            val deviceId = db.insertNewBluetoothDeviceIntoDB(
                DeviceType.HRM,
                "AA:BB:CC:DD:EE:88",
                "Polar Sense 88990011",
                "Polar Electro",
                90,
                true
            )
            val mockApi = mockk<PolarBleApi>(relaxed = true)
            every { mockApi.startHrStreaming(any()) } returns flow { awaitCancellation() }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val bridge = PolarOnlineStreamingBridge(
                context = context,
                mySensorManager = sensorManager,
                deviceId = deviceId,
                macAddress = "AA:BB:CC:DD:EE:88",
                dispatcher = testDispatcher,
                sdkIntProvider = { 34 },
                sportTypeProvider = { BSportType.RUN },
                apiFactory = { _, _ -> mockApi },
                searchTimeoutMsProvider = { 5000L }
            )

            try {
                // 4b: Timeout arms 1s reconnect (next=2s); synchronous deviceDisconnected echo MUST NOT double-advance to 4s
                bridge.startSearching()
                testScheduler.advanceTimeBy(5_001L)
                testScheduler.runCurrent()
                assertEquals(2_000L, bridge.getCurrentReconnectDelayMsForTesting())
                val armedJob = bridge.getReconnectJobForTesting()
                assertTrue(armedJob?.isActive == true)

                bridge.getSdkCallbackForTesting().deviceDisconnected(
                    PolarDeviceInfo("88990011", "AA:BB:CC:DD:EE:88", -55, "Polar Sense", true)
                )
                assertEquals(2_000L, bridge.getCurrentReconnectDelayMsForTesting())
                assertSame(armedJob, bridge.getReconnectJobForTesting())

                // 4c: blePowerStateChanged(false) cancels pending reconnect; blePowerStateChanged(true) resumes cleanly
                bridge.getSdkCallbackForTesting().blePowerStateChanged(false)
                assertNull(bridge.getReconnectJobForTesting())
                assertTrue(armedJob!!.isCancelled)

                bridge.getSdkCallbackForTesting().blePowerStateChanged(true)
                assertTrue(bridge.getReconnectJobForTesting()?.isActive == true)
                assertEquals(4_000L, bridge.getCurrentReconnectDelayMsForTesting())

                // 4a: Transient connectToDevice exception inside background reconnect reschedules rather than killing reconnect
                every { mockApi.connectToDevice("88990011") } throws IllegalStateException("Transient BT stack error")
                testScheduler.advanceTimeBy(2_001L)
                testScheduler.runCurrent()
                assertFalse(bridge.isSearching)
                assertTrue(
                    "Transient exception must reschedule follow-up reconnect job",
                    bridge.getReconnectJobForTesting()?.isActive == true
                )
                assertEquals(8_000L, bridge.getCurrentReconnectDelayMsForTesting())
            } finally {
                bridge.shutDown()
            }
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        }
    }

    @Test
    fun testPerDeviceHrv_polarBridgeRegistersLazilyCalculatesAndClearsOnDisconnectAndReset_TST_HRV_050_3() {
        // REQ-HRV-050.3 / TST-HRV-050.3
        val mac = "AA:BB:CC:DD:EE:99"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar H10 9900ABCD", "Polar Electro", 90, true)
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            apiFactory = { _, _ -> mockApi }
        )

        try {
            // Rule 52: Zero HRV sensors before first RR sample
            assertNull(bridge.getSensor(SensorType.HRV_DFA_A1))
            assertNull(bridge.getSensor(SensorType.HRV_SDNN))

            val now = System.currentTimeMillis()
            val startTs = now - 35_000L
            for (i in 0 until 40) {
                val rr = if (i % 2 == 0) 780 else 820
                bridge.processHrData(
                    PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(rr), listOf(rr), true, true, true))),
                    packetArrivalMs = startTs + i * 800L
                )
            }

            bridge.getPerDeviceHrvController().calculateMetrics()
            assertNotNull("Polar bridge must register per-device HRV_RMSSD", bridge.getSensor(SensorType.HRV_RMSSD)?.value)
            assertNotNull("Polar bridge must register per-device HRV_SDNN", bridge.getSensor(SensorType.HRV_SDNN)?.value)
            assertNotNull("Polar bridge must register per-device HRV_ARTIFACT_RATE", bridge.getSensor(SensorType.HRV_ARTIFACT_RATE)?.value)
            assertNotNull("Polar bridge must expose latestArtifactRatePercent", bridge.latestArtifactRatePercent)

            // Disconnect tears down sensors and pushes null while activated
            val sdnnSensor = bridge.getSensor(SensorType.HRV_SDNN)!!
            val seen = mutableListOf<Any?>()
            sdnnSensor.addSensorListener { seen.add(it) }
            bridge.shutDown()
            assertNull("shutDown must push null to listeners before deactivation", seen.last())
            assertNull(bridge.getSensor(SensorType.HRV_DFA_A1))
            assertNull(bridge.getSensor(SensorType.HRV_SDNN))
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testVeritySensePpiStreaming_decoupledLifecycleAtomicReentrancyAndTeardown_TST_HRV_051_2() = runTest {
        // REQ-HRV-051.1 / REQ-HRV-051.2 / TST-HRV-051.2
        val mac = "AA:BB:CC:DD:EE:A1"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sense A1B2C3D4", "Polar Electro", 90, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.isFeatureReady(any(), PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.requestStreamSettings(any(), any()) } returns PolarSensorSetting(
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52)
        )
        every { mockApi.startAccStreaming(any(), any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(any(), any()) } returns flow { awaitCancellation() }
        coEvery { mockApi.getAvailableOnlineStreamDataTypes("A1B2C3D4") } returns setOf(
            PolarBleApi.PolarDeviceDataType.HR,
            PolarBleApi.PolarDeviceDataType.ACC,
            PolarBleApi.PolarDeviceDataType.GYRO,
            PolarBleApi.PolarDeviceDataType.PPI
        )
        val ppiSampleClean = PolarPpiData.PolarPpiSample(
            ppi = 812,
            errorEstimate = 10,
            hr = 74,
            blockerBit = false,
            skinContactStatus = true,
            skinContactSupported = true,
            timeStamp = 0uL
        )
        val ppiSampleBlocked = PolarPpiData.PolarPpiSample(
            ppi = 610,
            errorEstimate = 40,
            hr = 74,
            blockerBit = true,
            skinContactStatus = true,
            skinContactSupported = true,
            timeStamp = 0uL
        )
        every { mockApi.startPpiStreaming("A1B2C3D4") } returns flow {
            emit(PolarPpiData(listOf(ppiSampleClean, ppiSampleBlocked)))
            awaitCancellation()
        }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.BIKE },
            apiFactory = { _, _ -> mockApi }
        )

        val receivedEvents = mutableListOf<RrBeatEvent>()
        bridge.addRrBeatListener(RrBeatListener { receivedEvents.add(it) })

        try {
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo("A1B2C3D4", mac, -55, "Polar Sense A1B2C3D4", true))

            // Fire rapid duplicate readiness callbacks BEFORE advancing dispatcher -> atomic CAS must guard!
            callback.bleSdkFeatureReady("A1B2C3D4", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            callback.bleSdkFeatureReady("A1B2C3D4", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            callback.bleSdkFeatureReady("A1B2C3D4", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)
            testScheduler.runCurrent()

            assertTrue("PPI streaming flag must be active", bridge.isPpiStreamingForTesting())
            assertTrue("PPI job must be active", bridge.isPpiJobActiveForTesting())
            verify(exactly = 1) { mockApi.startPpiStreaming("A1B2C3D4") }

            // Clean beat emitted to mRrIntervalSensor and RrBeatListener; blocked beat stamped ARTIFACT_GAP & NOT emitted to mRrIntervalSensor
            assertEquals(2, receivedEvents.size)
            assertTrue(receivedEvents[0].isOpticalPpi)
            assertEquals(RrBeatEvent.ARTIFACT_NORMAL, receivedEvents[0].artifactFlag)
            assertEquals(812.0, receivedEvents[0].rrMs, 0.001)
            assertTrue(receivedEvents[1].isOpticalPpi)
            assertEquals(RrBeatEvent.ARTIFACT_GAP, receivedEvents[1].artifactFlag)
            assertEquals(0, receivedEvents[1].gapBeats)
            assertEquals(
                "mRrIntervalSensor must hold last clean interval (812) and ignore blocked sample (610)",
                812,
                bridge.getSensor(SensorType.RR_INTERVAL)?.value
            )

            // onAccumulatorsReset resets processor state (Rule 51) without dropping active PPI job
            PolarOnlineStreamingBridge::class.java
                .getDeclaredMethod("onAccumulatorsReset")
                .apply { isAccessible = true }
                .invoke(bridge)
            val postResetArrival = 1_750_000_000_000L
            bridge.processPpiData(PolarPpiData(listOf(ppiSampleClean)), postResetArrival)
            assertEquals(postResetArrival, receivedEvents.last().beatTimestampMs)
            assertEquals(0, receivedEvents.last().gapBeats)

            // Disconnect cancels PPI job cleanly; reconnect re-arms cleanly
            callback.deviceDisconnected(PolarDeviceInfo("A1B2C3D4", mac, -55, "Polar Sense A1B2C3D4", true))
            testScheduler.runCurrent()
            assertFalse("Disconnect must clear PPI active flag", bridge.isPpiStreamingForTesting())
            assertFalse("Disconnect must cancel PPI job", bridge.isPpiJobActiveForTesting())

            // Reconnect and verify blePowerStateChanged(false) cancels PPI job
            callback.deviceConnected(PolarDeviceInfo("A1B2C3D4", mac, -55, "Polar Sense A1B2C3D4", true))
            callback.bleSdkFeatureReady("A1B2C3D4", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertTrue(bridge.isPpiStreamingForTesting())
            verify(exactly = 2) { mockApi.startPpiStreaming("A1B2C3D4") }

            callback.blePowerStateChanged(false)
            testScheduler.runCurrent()
            assertFalse(bridge.isPpiStreamingForTesting())
            assertFalse(bridge.isPpiJobActiveForTesting())

            // shutDown invokes stopStreaming(PmdMeasurementType.PPI) for Verity Sense
            bridge.shutDown()
            verify(exactly = 1) { mockApi.stopStreaming("A1B2C3D4", PmdMeasurementType.PPI) }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testPolarH10_neverProbesStartsOrStopsOpticalPpiStreaming_TST_HRV_051_2() = runTest {
        // REQ-HRV-051.2 / TST-HRV-051.2
        val mac = "AA:BB:CC:DD:EE:B2"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar H10 B2C3D4E5", "Polar Electro", 90, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        coEvery { mockApi.requestStreamSettings(any(), any()) } returns PolarSensorSetting(
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 50)
        )
        every { mockApi.startAccStreaming(any(), any()) } returns flow { awaitCancellation() }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo("B2C3D4E5", mac, -50, "Polar H10 B2C3D4E5", true))
            callback.bleSdkFeatureReady("B2C3D4E5", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            assertFalse("H10 must never mark PPI active", bridge.isPpiStreamingForTesting())
            coVerify(exactly = 0) { mockApi.getAvailableOnlineStreamDataTypes(any()) }
            verify(exactly = 0) { mockApi.startPpiStreaming(any()) }

            bridge.shutDown()
            verify(exactly = 0) { mockApi.stopStreaming(any(), PmdMeasurementType.PPI) }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testVeritySensePpi_lateDisIdentificationAndExceptionIsolation_TST_HRV_051_2() = runTest {
        // REQ-HRV-051.2 / TST-HRV-051.2
        val mac = "AA:BB:CC:DD:EE:C3"
        val db = DevicesDatabaseManager.getInstance(context)
        // Generic name initially so isVeritySense starts false until DIS Model Number arrives
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sensor C3D4E5F6", "Polar Electro", 90, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.isFeatureReady(any(), PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.requestStreamSettings(any(), any()) } returns PolarSensorSetting(
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52)
        )
        every { mockApi.startAccStreaming(any(), any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(any(), any()) } returns flow { awaitCancellation() }

        var probeAttempts = 0
        coEvery { mockApi.getAvailableOnlineStreamDataTypes("C3D4E5F6") } answers {
            probeAttempts++
            if (probeAttempts == 1) {
                throw IllegalStateException("Transient PMD capability read failure")
            }
            setOf(PolarBleApi.PolarDeviceDataType.PPI)
        }
        every { mockApi.startPpiStreaming("C3D4E5F6") } returns flow { awaitCancellation() }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.BIKE },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo("C3D4E5F6", mac, -52, "Polar Sensor C3D4E5F6", true))
            callback.bleSdkFeatureReady("C3D4E5F6", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            // Before DIS identifies Verity Sense, PPI has not started yet
            assertFalse(bridge.isPpiStreamingForTesting())

            // DIS Model Number arrives -> identifies Verity Sense; first probe throws transient error
            callback.disInformationReceived("C3D4E5F6", DisInfo(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER.toString(), "Polar Verity Sense"))
            testScheduler.runCurrent()

            // Exception inside PPI coroutine must NOT crash bridge scope and must reset flag cleanly
            assertFalse("Transient probe failure must reset PPI active flag cleanly", bridge.isPpiStreamingForTesting())
            assertTrue("ACC stream must remain alive after PPI probe failure", bridge.isAccJobActiveForTesting())

            // Subsequent readiness callback (e.g. FEATURE_POLAR_OFFLINE_RECORDING) retries PPI independently of GYRO!
            callback.bleSdkFeatureReady("C3D4E5F6", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)
            testScheduler.runCurrent()
            assertTrue("Decoupled retry must launch PPI even though GYRO is already active", bridge.isPpiStreamingForTesting())
            verify(exactly = 1) { mockApi.startPpiStreaming("C3D4E5F6") }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testVeritySensePpi_syncAlreadyOpenSessionLaunchesPpiIndependently_TST_HRV_051_2() = runTest {
        // REQ-HRV-051.2 / TST-HRV-051.2
        val mac = "AA:BB:CC:DD:EE:D4"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Sense D4E5F607", "Polar Electro", 90, true)

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.isFeatureReady("D4E5F607", PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.requestStreamSettings(any(), any()) } returns PolarSensorSetting(
            mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52)
        )
        every { mockApi.startAccStreaming(any(), any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(any(), any()) } returns flow { awaitCancellation() }
        coEvery { mockApi.getAvailableOnlineStreamDataTypes("D4E5F607") } returns setOf(PolarBleApi.PolarDeviceDataType.PPI)
        every { mockApi.startPpiStreaming("D4E5F607") } returns flow { awaitCancellation() }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.BIKE },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            // startSearching calls connectPersistentSession -> syncAlreadyOpenSessionIfReady on already-ready session
            bridge.startSearching()
            testScheduler.runCurrent()

            assertTrue("syncAlreadyOpenSessionIfReady must launch PPI independently", bridge.isPpiStreamingForTesting())
            assertTrue("PPI job must be active on already-open session", bridge.isPpiJobActiveForTesting())
            verify(exactly = 1) { mockApi.startPpiStreaming("D4E5F607") }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testDynamicRrAndImuSensors_deactivateOnDisconnectWhileStridesPreservesTotal_TST_FIL_038_2() {
        // REQ-FIL-038.2 / TST-FIL-038.2
        val mac = "AA:BB:CC:DD:EE:E5"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar H10 E5F60718", "Polar Electro", 90, true)
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.syncCadenceSensorRegistration(runActive = true)
            bridge.processHrData(
                PolarHrData(listOf(PolarHrData.PolarHrSample(75, 75, 0, listOf(805), listOf(805), true, true, true))),
                packetArrivalMs = 100_000L
            )

            val rrSensor = bridge.getSensor(SensorType.RR_INTERVAL)!!
            val cadSensor = bridge.getSensor(SensorType.CADENCE)!!
            val voSensor = bridge.getSensor(SensorType.VERTICAL_OSCILLATION)!!
            val gctSensor = bridge.getSensor(SensorType.GROUND_CONTACT_TIME)!!
            val stridesSensor = bridge.getSensor(SensorType.STRIDES)!!

            @Suppress("UNCHECKED_CAST")
            (cadSensor as com.atrainingtracker.banalservice.sensor.MySensor<Double>).newValue(88.0)
            @Suppress("UNCHECKED_CAST")
            (voSensor as com.atrainingtracker.banalservice.sensor.MySensor<Double>).newValue(8.2)
            @Suppress("UNCHECKED_CAST")
            (gctSensor as com.atrainingtracker.banalservice.sensor.MySensor<Double>).newValue(245.0)
            (stridesSensor as com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor).resetTo(15)

            val rrSeen = mutableListOf<Any?>()
            val cadSeen = mutableListOf<Any?>()
            val voSeen = mutableListOf<Any?>()
            val gctSeen = mutableListOf<Any?>()
            val stridesSeen = mutableListOf<Any?>()

            rrSensor.addSensorListener { rrSeen.add(it) }
            cadSensor.addSensorListener { cadSeen.add(it) }
            voSensor.addSensorListener { voSeen.add(it) }
            gctSensor.addSensorListener { gctSeen.add(it) }
            stridesSensor.addSensorListener { stridesSeen.add(it) }

            bridge.shutDown()

            assertFalse(rrSensor.isActivated)
            assertFalse(cadSensor.isActivated)
            assertFalse(voSensor.isActivated)
            assertFalse(gctSensor.isActivated)
            assertFalse(stridesSensor.isActivated)

            assertNull("RR_INTERVAL must notify null on Polar disconnect", rrSeen.last())
            assertNull("CADENCE must notify null on Polar disconnect", cadSeen.last())
            assertNull("VERTICAL_OSCILLATION must notify null on Polar disconnect", voSeen.last())
            assertNull("GROUND_CONTACT_TIME must notify null on Polar disconnect", gctSeen.last())
            assertEquals("STRIDES accumulator must NOT push null on disconnect", 15, stridesSeen.last())
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testReentrantHandshakeBurst_preventsHrToggleStormAndDuplicatePmdStarts_TST_CON_053_1() = runTest {
        // REQ-CON-053.1 / TST-CON-053.1
        val mac = "24:AC:AC:24:82:56"
        val polarId = "24825632"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Verity Sense $polarId", "Polar Electro", 90, true)

        val accSettingsGate = CompletableDeferred<PolarSensorSetting>()
        val gyroSettingsGate = CompletableDeferred<PolarSensorSetting>()
        val ppiProbeGate = CompletableDeferred<Set<PolarBleApi.PolarDeviceDataType>>()
        val veritySetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.isFeatureReady(any(), PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true
        every { mockApi.isFeatureReady(any(), PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.requestStreamSettings(polarId, PolarBleApi.PolarDeviceDataType.ACC) } coAnswers { accSettingsGate.await() }
        coEvery { mockApi.requestStreamSettings(polarId, PolarBleApi.PolarDeviceDataType.GYRO) } coAnswers { gyroSettingsGate.await() }
        coEvery { mockApi.getAvailableOnlineStreamDataTypes(polarId) } coAnswers { ppiProbeGate.await() }
        every { mockApi.startHrStreaming(polarId) } returns flow { awaitCancellation() }
        every { mockApi.startAccStreaming(polarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(polarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startPpiStreaming(polarId) } returns flow { awaitCancellation() }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            // Lock ACC by offline recording first -> ACC CAS must roll back cleanly
            assertTrue(PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_OFFLINE))
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Verity Sense $polarId", true))
            testScheduler.runCurrent()

            assertFalse("ACC CAS must roll back cleanly when offline recording holds channel", bridge.isAccStreamingForTesting())
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_OFFLINE)

            // Fire rapid burst of overlapping callbacks while settings/capability probes are suspended
            repeat(4) { callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) }
            repeat(3) { callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) }
            repeat(2) { callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING) }
            repeat(2) {
                callback.disInformationReceived(
                    polarId,
                    DisInfo(BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER.toString(), "Polar Verity Sense")
                )
            }
            testScheduler.runCurrent()

            // Complete suspended probes now
            accSettingsGate.complete(veritySetting)
            gyroSettingsGate.complete(veritySetting)
            ppiProbeGate.complete(setOf(PolarBleApi.PolarDeviceDataType.PPI))
            testScheduler.runCurrent()

            verify(exactly = 1) { mockApi.startHrStreaming(polarId) }
            verify(exactly = 1) { mockApi.startAccStreaming(polarId, any()) }
            verify(exactly = 1) { mockApi.startGyroStreaming(polarId, any()) }
            verify(exactly = 1) { mockApi.startPpiStreaming(polarId) }
            assertTrue(bridge.isHrJobActiveForTesting())
            assertTrue(bridge.isAccStreamingForTesting())
            assertTrue(bridge.isGyroStreamingForTesting())
            assertTrue(bridge.isPpiStreamingForTesting())
        } finally {
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_OFFLINE)
            bridge.shutDown()
        }
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun testPmdAlreadyInStateRecovery_recoversAccGyroAndPpiAfterStopStreamingAndBackoff_TST_CON_053_2() = runTest {
        // REQ-CON-053.2 / TST-CON-053.2
        val mac = "24:AC:AC:24:82:57"
        val polarId = "24825633"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Verity Sense $polarId", "Polar Electro", 90, true)

        val veritySetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val alreadyInState = BleControlPointCommandError(
            "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
        )

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        coEvery { mockApi.requestStreamSettings(polarId, any()) } returns veritySetting
        coEvery { mockApi.getAvailableOnlineStreamDataTypes(polarId) } returns setOf(PolarBleApi.PolarDeviceDataType.PPI)

        var accAttempts = 0
        every { mockApi.startAccStreaming(polarId, any()) } answers {
            val attempt = ++accAttempts
            flow {
                if (attempt == 1) throw alreadyInState
                emit(PolarAccelerometerData(listOf(PolarAccelerometerData.PolarAccelerometerDataSample(1_000_000L, 10, 20, 1000))))
                awaitCancellation()
            }
        }
        var gyroAttempts = 0
        every { mockApi.startGyroStreaming(polarId, any()) } answers {
            val attempt = ++gyroAttempts
            flow {
                if (attempt == 1) throw alreadyInState
                emit(PolarGyroData(listOf(PolarGyroData.PolarGyroDataSample(1_000_000L, 1.0f, 2.0f, 3.0f))))
                awaitCancellation()
            }
        }
        var ppiAttempts = 0
        every { mockApi.startPpiStreaming(polarId) } answers {
            val attempt = ++ppiAttempts
            flow {
                if (attempt == 1) throw alreadyInState
                emit(
                    PolarPpiData(
                        listOf(
                            PolarPpiData.PolarPpiSample(800, 10, 75, false, true, true, 0uL)
                        )
                    )
                )
                awaitCancellation()
            }
        }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )
        var accFrames = 0
        var gyroFrames = 0
        val ppiBeats = mutableListOf<RrBeatEvent>()
        bridge.addImuFrameListener(object : PolarOnlineStreamingBridge.ImuFrameListener {
            override fun onAccFrame(polarId: String, data: PolarAccelerometerData) { accFrames++ }
            override fun onGyroFrame(polarId: String, data: PolarGyroData) { gyroFrames++ }
        })
        bridge.addRrBeatListener(RrBeatListener { ppiBeats.add(it) })

        try {
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Verity Sense $polarId", true))
            callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            // First attempt hit ERROR_ALREADY_IN_STATE -> stopStreaming issued for ACC, GYRO, PPI; waiting 500ms backoff
            verify(exactly = 1) { mockApi.stopStreaming(polarId, PmdMeasurementType.ACC) }
            verify(exactly = 1) { mockApi.stopStreaming(polarId, PmdMeasurementType.GYRO) }
            verify(exactly = 1) { mockApi.stopStreaming(polarId, PmdMeasurementType.PPI) }
            assertEquals(0, accFrames)

            testScheduler.advanceTimeBy(501L)
            testScheduler.runCurrent()

            assertEquals("ACC must recover and deliver frames after 500ms backoff", 1, accFrames)
            assertEquals("GYRO must recover and deliver frames after 500ms backoff", 1, gyroFrames)
            assertEquals("PPI must recover and deliver optical beats after 500ms backoff", 1, ppiBeats.size)
            assertNull("Recovered PMD streams must not emit blocked-stream warning", PolarPmdStreamCoordinator.streamWarning.value)
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("DEPRECATION")
    fun testPmdAlreadyInStateExhaustion_emitsSingleDeduplicatedWarningAndClearsOnDisconnect_TST_CON_053_3() = runTest {
        // REQ-CON-053.3 / TST-CON-053.3
        val mac = "24:AC:AC:24:82:58"
        val polarId = "24825634"
        val db = DevicesDatabaseManager.getInstance(context)
        val devId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, mac, "Polar Verity Sense $polarId", "Polar Electro", 90, true)

        val veritySetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val alwaysAlreadyInState = BleControlPointCommandError(
            "pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE",
            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
        )

        val mockApi = mockk<PolarBleApi>(relaxed = true)
        coEvery { mockApi.requestStreamSettings(polarId, any()) } returns veritySetting
        coEvery { mockApi.getAvailableOnlineStreamDataTypes(polarId) } returns setOf(PolarBleApi.PolarDeviceDataType.PPI)
        every { mockApi.startAccStreaming(polarId, any()) } returns flow { throw alwaysAlreadyInState }
        every { mockApi.startGyroStreaming(polarId, any()) } returns flow { throw alwaysAlreadyInState }
        every { mockApi.startPpiStreaming(polarId) } returns flow { throw alwaysAlreadyInState }

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = devId,
            macAddress = mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val callback = bridge.getSdkCallbackForTesting()
            callback.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Verity Sense $polarId", true))
            callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)

            // Advance past 500 + 1000 + 2000 ms backoff retries
            testScheduler.advanceTimeBy(4_000L)
            testScheduler.runCurrent()

            verify(exactly = 3) { mockApi.stopStreaming(polarId, PmdMeasurementType.ACC) }
            verify(exactly = 3) { mockApi.stopStreaming(polarId, PmdMeasurementType.GYRO) }
            verify(exactly = 3) { mockApi.stopStreaming(polarId, PmdMeasurementType.PPI) }

            val warning = PolarPmdStreamCoordinator.streamWarning.value
            assertNotNull("Exhausted PMD recovery must surface a status warning", warning)
            assertEquals(polarId, warning!!.polarId)
            assertEquals(PolarPmdStreamCoordinator.MAX_ALREADY_IN_STATE_RETRIES, warning.retryCount)

            // Consume warning + redundant deviceConnected echo must NOT reset warningEmittedForSession mid-session
            PolarPmdStreamCoordinator.consumeStreamWarning(polarId)
            assertNull(PolarPmdStreamCoordinator.streamWarning.value)
            callback.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Verity Sense $polarId", true))
            callback.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.advanceTimeBy(4_000L)
            testScheduler.runCurrent()
            assertNull("Redundant already-connected callback must not re-spam warning mid-session", PolarPmdStreamCoordinator.streamWarning.value)

            // Disconnect resets session state and clears any active warning cleanly
            callback.deviceDisconnected(PolarDeviceInfo(polarId, mac, -52, "Polar Verity Sense $polarId", true))
            testScheduler.runCurrent()
            assertNull(PolarPmdStreamCoordinator.streamWarning.value)
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-CON-054.1 / REQ-CON-054.3 / TST-CON-054.1 / TST-CON-054.3
    @Test
    fun testSilentHrWatchdog_resubscribesThenDisconnectsPreservingRegistrationsAndShutDownDisconnectsAllAliases_TST_CON_054_3() = runTest {
        val mac = "24:AC:AC:24:82:56"
        val polarId = "24825632"
        val hrFlows = mutableListOf<kotlinx.coroutines.flow.MutableSharedFlow<PolarHrData>>()
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.startHrStreaming(any()) } answers {
            kotlinx.coroutines.flow.MutableSharedFlow<PolarHrData>(extraBufferCapacity = 4).also { hrFlows.add(it) }
        }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = 901L,
            macAddress = mac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.silentHrWatchdogTimeoutMs = 1_000L

        try {
            bridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            mux.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Sense $polarId", true))
            mux.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            testScheduler.runCurrent()
            assertTrue(bridge.isReceivingData())
            assertEquals(1, hrFlows.size)

            // 1st silent window (1_000 ms) -> cancels HR slot & re-subscribes startHrStreaming
            testScheduler.advanceTimeBy(1_001L)
            testScheduler.runCurrent()
            assertTrue(bridge.isReceivingData())
            assertEquals("Silent watchdog must re-subscribe HR stream once", 2, hrFlows.size)

            // 2nd silent window -> disconnects aliases with clearRegistrations=false and schedules reconnect
            testScheduler.advanceTimeBy(1_001L)
            testScheduler.runCurrent()
            assertFalse("Persistent silence must exit connected state", bridge.isReceivingData())
            verify(atLeast = 1) { mockApi.disconnectFromDevice(mac) }
            verify(atLeast = 1) { mockApi.disconnectFromDevice(polarId) }
            assertNotNull("In-session watchdog must preserve activeSession for reconnect", PolarOfflineGattCoordinator.getActiveSession(polarId))
            assertNotNull("Persistent silence must schedule backoff reconnect", bridge.getReconnectJobForTesting())

            // Reconnect succeeds and first HR sample disarms watchdog cleanly
            mux.deviceConnected(PolarDeviceInfo(polarId, mac, -52, "Polar Sense $polarId", true))
            mux.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            testScheduler.runCurrent()
            assertTrue(bridge.isReceivingData())
            val countBeforeSample = hrFlows.size
            bridge.processHrData(
                PolarHrData(listOf(PolarHrData.PolarHrSample(72, 72, 0, emptyList(), emptyList(), false, true, true))),
                100_000L
            )
            testScheduler.advanceTimeBy(5_000L)
            testScheduler.runCurrent()
            assertTrue("Receiving HR sample must disarm watchdog", bridge.isReceivingData())
            assertEquals(countBeforeSample, hrFlows.size)
        } finally {
            bridge.shutDown()
        }
        assertNull("shutDown() must clear coordinator registration", PolarOfflineGattCoordinator.getActiveSession(polarId))
    }

    // REQ-CON-054.3 / TST-CON-054.3
    @Test
    fun testStaleWatchdogGenerationTick_afterDisconnectReconnectOrHrPacket_doesNotCancelHrSlotOrLeakTimer_TST_CON_054_3() = runTest {
        val mac = "24:AC:AC:24:82:57"
        val polarId = "24825633"
        val hrFlows = mutableListOf<kotlinx.coroutines.flow.MutableSharedFlow<PolarHrData>>()
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.startHrStreaming(any()) } answers {
            kotlinx.coroutines.flow.MutableSharedFlow<PolarHrData>(extraBufferCapacity = 4).also { hrFlows.add(it) }
        }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = 902L,
            macAddress = mac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )
        bridge.silentHrWatchdogTimeoutMs = 1_000L

        try {
            bridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            val info = PolarDeviceInfo(polarId, mac, -52, "Polar Sense $polarId", true)

            // Session 1 connects -> captures gen1
            mux.deviceConnected(info)
            mux.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            testScheduler.runCurrent()
            val gen1 = bridge.getSilentHrWatchdogGenerationForTesting()
            assertEquals(1, hrFlows.size)

            // Disconnect + reconnect -> new generation gen2 and fresh HR slot
            mux.deviceDisconnected(info)
            mux.deviceConnected(info)
            mux.bleSdkFeatureReady(polarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            testScheduler.runCurrent()
            val gen2 = bridge.getSilentHrWatchdogGenerationForTesting()
            assertTrue(gen2 > gen1)
            assertEquals(2, hrFlows.size)

            // Stale gen1 timeout firing against session 2 must be ignored completely (no HR slot cancel/re-subscribe)
            bridge.triggerSilentHrWatchdogTimeoutForTesting(gen1)
            testScheduler.runCurrent()
            assertEquals("Stale gen1 tick must not cancel/re-subscribe live gen2 HR stream", 2, hrFlows.size)
            assertTrue(bridge.isHrJobActiveForTesting())

            // HR packet disarms gen2 -> stale gen2 tick must NOT re-subscribe or leak a follow-up watchdog job
            bridge.processHrData(
                PolarHrData(listOf(PolarHrData.PolarHrSample(68, 68, 0, emptyList(), emptyList(), false, true, true))),
                200_000L
            )
            assertNull("Disarming on HR packet must clear silentHrWatchdogJob", bridge.getSilentHrWatchdogJobForTesting())
            bridge.triggerSilentHrWatchdogTimeoutForTesting(gen2)
            testScheduler.runCurrent()
            assertEquals("Stale gen2 tick after HR packet must not re-subscribe HR stream", 2, hrFlows.size)
            assertNull("Stale gen2 tick must not leak a follow-up watchdog job", bridge.getSilentHrWatchdogJobForTesting())
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-120.2 / TST-SET-122.2
    @Test
    fun testOfflineGattYield_pausesReconnectSearchTimeoutAndSilentWatchdogThenResumes_TST_SET_122_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:56"
        val verityId = "24825632"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val h10Mac = "AA:BB:CC:DD:EE:88"
        val h10Id = "AABBCC88"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10Id", "Polar Electro", 90, true
        )

        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val verityBridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> sharedApi },
            searchTimeoutMsProvider = { 5_000L }
        )
        verityBridge.silentHrWatchdogTimeoutMs = 1_000L

        val h10Bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10DevId,
            macAddress = h10Mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> sharedApi },
            searchTimeoutMsProvider = { 5_000L }
        )

        try {
            // Establish session then disconnect so verityBridge enters backoff reconnect loop
            verityBridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            val verityInfo = PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true)
            mux.deviceConnected(verityInfo)
            assertTrue(verityBridge.isReceivingData())
            mux.deviceDisconnected(verityInfo)
            assertFalse(verityBridge.isReceivingData())
            assertNotNull("Disconnected mid-session bridge must arm reconnectJob", verityBridge.getReconnectJobForTesting())

            // Start sibling H10 searching too to prove sibling isolation
            h10Bridge.startSearching()
            assertTrue(h10Bridge.isSearching)

            clearMocks(sharedApi, answers = false, recordedCalls = true)

            // Hold offline GATT yield lease for Verity Sense across > 15s (exceeding 1s reconnect, 5s search timeout, and 1s silent watchdog)
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                assertTrue(verityBridge.isOfflineGattYielded())
                assertFalse("Sibling H10 must NOT be yielded", h10Bridge.isOfflineGattYielded())
                assertNull("Offline yield must cancel verity reconnectJob", verityBridge.getReconnectJobForTesting())
                assertNull("Offline yield must cancel verity searchTimeoutJob", verityBridge.getSearchTimeoutJobForTesting())
                assertNull("Offline yield must cancel verity silentHrWatchdogJob", verityBridge.getSilentHrWatchdogJobForTesting())

                // Calling startSearching while yielded must NOT invoke connectToDevice
                verityBridge.startSearching()
                testScheduler.advanceTimeBy(15_000L)
                testScheduler.runCurrent()

                verify(exactly = 0) { sharedApi.connectToDevice(verityId) }
                verify(exactly = 0) { sharedApi.disconnectFromDevice(verityId) }
                verify(exactly = 0) { sharedApi.disconnectFromDevice(verityMac) }
                // Sibling H10 was unaffected and timed out at 5s normally
                verify(atLeast = 1) { sharedApi.disconnectFromDevice(h10Id) }
            }

            // After lease releases, verityBridge cleanly resumes searching/connecting
            assertFalse(verityBridge.isOfflineGattYielded())
            testScheduler.runCurrent()
            verify(atLeast = 1) { sharedApi.connectToDevice(verityId) }

            // Also verify connected silent bridge defers silentHrWatchdog during yield and re-arms on release
            mux.deviceConnected(verityInfo)
            testScheduler.runCurrent()
            assertNotNull(verityBridge.getSilentHrWatchdogJobForTesting())
            val genBeforeYield = verityBridge.getSilentHrWatchdogGenerationForTesting()
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                assertNull("Silent HR watchdog must be cancelled during offline yield", verityBridge.getSilentHrWatchdogJobForTesting())
                testScheduler.advanceTimeBy(5_000L)
                testScheduler.runCurrent()
                assertTrue("Connected bridge must stay connected without watchdog teardown during yield", verityBridge.isReceivingData())
            }
            assertTrue("Watchdog generation must advance cleanly across yield", verityBridge.getSilentHrWatchdogGenerationForTesting() > genBeforeYield)
            assertNotNull("Silent HR watchdog must re-arm on resume when still silent", verityBridge.getSilentHrWatchdogJobForTesting())
        } finally {
            verityBridge.shutDown()
            h10Bridge.shutDown()
        }
    }

    // REQ-SET-120.2 / TST-SET-122.2
    @Test
    fun testOfflineGattYield_whenSharedSessionConnectedDuringBackfill_syncsOpenSessionOnResume_TST_SET_122_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:59"
        val verityId = "24825639"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        every { sharedApi.isFeatureReady(verityId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns false
        val testDispatcher = StandardTestDispatcher(testScheduler)
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> sharedApi },
            searchTimeoutMsProvider = { 5_000L }
        )

        try {
            bridge.startSearching()
            assertTrue(bridge.isSearching)
            clearMocks(sharedApi, answers = false, recordedCalls = true)

            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                // Simulate offline backfill establishing SESSION_OPEN with FEATURE_HR ready on sharedApi
                every { sharedApi.isFeatureReady(verityId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR) } returns true
            }

            testScheduler.runCurrent()
            assertTrue("Resume must sync already-open shared session", bridge.isReceivingData())
            assertNull("Synced open session must not schedule redundant reconnect", bridge.getReconnectJobForTesting())
            verify(exactly = 0) { sharedApi.connectToDevice(verityId) }
            verify(atLeast = 1) { sharedApi.startHrStreaming(verityId) }
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-120.3 / TST-SET-122.3
    @Test
    fun testDeviceConnecting_extendsSearchTimeoutOnceToHandshakeGraceAndPreserves5sAbsentTimeout_TST_SET_122_3() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:60"
        val verityId = "24825640"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5_000L }
        )
        bridge.connectingHandshakeTimeoutMs = 15_000L

        try {
            // 1. Live handshake: deviceConnecting at t=1_000 ms extends timeout to 15_000 ms so t=5_001 ms does NOT tear down GATT
            bridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            val info = PolarDeviceInfo(verityId, verityMac, -55, "Polar Sense $verityId", true)

            testScheduler.advanceTimeBy(1_000L)
            testScheduler.runCurrent()
            mux.deviceConnecting(info)

            testScheduler.advanceTimeBy(4_500L) // t = 5_500 ms (> 5s scan timeout, within 15s handshake grace)
            testScheduler.runCurrent()
            assertTrue("Live handshake must stay searching past 5s once deviceConnecting fired", bridge.isSearching)
            verify(exactly = 0) { mockApi.disconnectFromDevice(any()) }

            // deviceConnected at t = 8_000 ms completes handshake cleanly
            testScheduler.advanceTimeBy(2_500L)
            mux.deviceConnected(info)
            testScheduler.runCurrent()
            assertTrue(bridge.isReceivingData())
            assertNull(bridge.getSearchTimeoutJobForTesting())
            verify(exactly = 0) { mockApi.disconnectFromDevice(any()) }

            // 2. Absent sensor: no deviceConnecting -> still times out promptly at 5_000 ms (REQ-CON-045.1)
            mux.deviceDisconnected(info)
            bridge.startSearching()
            testScheduler.advanceTimeBy(5_001L)
            testScheduler.runCurrent()
            assertFalse("Absent sensor without deviceConnecting must still time out at 5s", bridge.isSearching)
            verify(atLeast = 1) { mockApi.disconnectFromDevice(verityId) }
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-120.3 / TST-SET-122.3
    @Test
    fun testDeviceConnecting_ignoresSiblingAndDuplicateCallbacksAndTimesOutAfterHandshakeGrace_TST_SET_122_3() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:61"
        val verityId = "24825641"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi },
            searchTimeoutMsProvider = { 5_000L }
        )
        bridge.connectingHandshakeTimeoutMs = 15_000L

        try {
            // 1. Sibling deviceConnecting must NOT extend this bridge's 5s timeout
            bridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            mux.deviceConnecting(PolarDeviceInfo("DEADBEEF", "11:22:33:44:55:66", -60, "Polar H10 DEADBEEF", true))
            testScheduler.advanceTimeBy(5_001L)
            testScheduler.runCurrent()
            assertFalse("Sibling deviceConnecting must not extend search timeout", bridge.isSearching)
            verify(exactly = 1) { mockApi.disconnectFromDevice(verityId) }

            // 2. Duplicate deviceConnecting callbacks extend AT MOST ONCE per attempt, then bounded 15s grace times out cleanly
            clearMocks(mockApi, answers = false, recordedCalls = true)
            val targetInfo = PolarDeviceInfo(verityId, verityMac, -55, "Polar Sense $verityId", true)
            bridge.startSearching()
            testScheduler.advanceTimeBy(1_000L)
            mux.deviceConnecting(targetInfo) // arms 15_000 ms deadline at t = 16_000 ms

            // Repeated deviceConnecting at t = 10_000 ms must NOT reset the 15_000 ms deadline
            testScheduler.advanceTimeBy(9_000L)
            mux.deviceConnecting(targetInfo)
            testScheduler.runCurrent()
            assertTrue(bridge.isSearching)

            // Advancing 6_001 ms (to t = 16_001 ms) must fire bounded timeout despite the duplicate callback at t = 10s
            testScheduler.advanceTimeBy(6_001L)
            testScheduler.runCurrent()
            assertFalse("Stalled handshake must time out after single 15s grace window", bridge.isSearching)
            verify(exactly = 1) { mockApi.disconnectFromDevice(verityId) }
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-120.2 / TST-SET-122.2
    @Test
    fun testOfflineGattYield_deviceConnectedDuringYield_armsSilentWatchdogOnResumeUnlessDisarmedByHr_TST_SET_122_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:62"
        val verityId = "24825642"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> sharedApi },
            searchTimeoutMsProvider = { 5_000L }
        )
        bridge.silentHrWatchdogTimeoutMs = 1_000L

        try {
            bridge.startSearching()
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            val info = PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true)

            // 1. Sensor connects DURING offline yield (no HR packet yet) -> watchdog timer deferred during yield, armed on resume
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                mux.deviceConnected(info)
                testScheduler.runCurrent()
                assertTrue(bridge.isReceivingData())
                assertNull("Silent HR watchdog timer must stay deferred while yielded", bridge.getSilentHrWatchdogJobForTesting())
            }
            assertNotNull("Silent HR watchdog timer MUST arm after yield release when still silent", bridge.getSilentHrWatchdogJobForTesting())

            // 2. Disconnect, reconnect DURING yield, AND receive HR packet during yield -> stays disarmed (null) on resume
            mux.deviceDisconnected(info)
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                mux.deviceConnected(info)
                bridge.processHrData(
                    PolarHrData(listOf(PolarHrData.PolarHrSample(74, 74, 0, emptyList(), emptyList(), false, true, true))),
                    100_000L
                )
                testScheduler.runCurrent()
            }
            assertNull("HR packet during yield must disarm watchdog so resume does NOT re-arm it", bridge.getSilentHrWatchdogJobForTesting())
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-120.2 / TST-SET-122.2
    @Test
    fun testOfflineGattYield_stalePauseCallDoesNotWedgeAndShouldKeepAliveInWaitingForStartMode_TST_SET_122_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val verityMac = "24:AC:AC:24:82:63"
        val verityId = "24825643"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true
        )
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityDevId,
            macAddress = verityMac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> sharedApi },
            searchTimeoutMsProvider = { 5_000L }
        )

        try {
            assertFalse("Idle WAITING_FOR_START bridge before yield must not keep alive", bridge.shouldKeepAliveOnSearchTimeout())

            // Inside offline yield in WAITING_FOR_START mode, shouldKeepAliveOnSearchTimeout() MUST be true
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                assertTrue(bridge.isOfflineGattYielded())
                assertTrue(
                    "Yielded bridge in WAITING_FOR_START mode must keep alive so DeviceManager does not shutDown() mid-backfill",
                    bridge.shouldKeepAliveOnSearchTimeout()
                )
            }

            assertFalse(bridge.isOfflineGattYielded())
            assertFalse(bridge.shouldKeepAliveOnSearchTimeout())

            // Stale out-of-order pauseForOfflineSync() when no lease is active must NOT wedge bridge or cancel active search
            bridge.startSearching()
            assertNotNull(bridge.getSearchTimeoutJobForTesting())
            bridge.pauseForOfflineSync()
            assertFalse("Stale pauseForOfflineSync() must NOT wedge isOfflineGattYielded()", bridge.isOfflineGattYielded())
            assertNotNull("Stale pauseForOfflineSync() must NOT cancel active searchTimeoutJob", bridge.getSearchTimeoutJobForTesting())
            assertTrue(bridge.isSearching)
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-122.1, REQ-SET-122.2, REQ-SET-122.3 / TST-SET-124.1, TST-SET-124.3
    @Test
    fun testH10LateRunActivation_renegotiatesAcc100To200HzPreservesStridesAndEmitsVoGct_TST_SET_124_1() = runTest {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:46"
        val h10IdStr = "93460001"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10IdStr", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
            every { maxSettings() } returns PolarSensorSetting(
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to 200,
                    PolarSensorSetting.SettingType.RESOLUTION to 16,
                    PolarSensorSetting.SettingType.RANGE to 8
                )
            )
        }
        val capturedRates = mutableListOf<Int?>()
        val accFlows = mutableListOf<MutableSharedFlow<PolarAccelerometerData>>()
        coEvery { mockApi.requestStreamSettings(h10IdStr, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startHrStreaming(h10IdStr) } returns flowOf()
        every { mockApi.startAccStreaming(h10IdStr, any()) } answers {
            val setting = secondArg<PolarSensorSetting>()
            capturedRates.add(setting.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            MutableSharedFlow<PolarAccelerometerData>(extraBufferCapacity = 64).also { accFlows.add(it) }
        }

        var currentSport = BSportType.UNKNOWN
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10DevId,
            macAddress = h10Mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { currentSport },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(h10IdStr, h10Mac, -55, "Polar H10 $h10IdStr", true))
            cb.bleSdkFeatureReady(h10IdStr, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            assertEquals("H10 connecting during BSportType.UNKNOWN must initially negotiate 100 Hz", listOf(100), capturedRates)
            assertEquals(PolarOfflineGattCoordinator.ACC_OWNER_ONLINE, PolarOfflineGattCoordinator.getAccChannelOwner(h10IdStr))

            // Switch to BSportType.RUN (simulating Tier-3 inference detected inside processAccData on active 100 Hz stream)
            val fStep = 162.0 / 60.0
            val omega = 2.0 * PI * fStep
            val ampMg = ((0.085 / 2.0) * omega * omega / 9.80665) * 1000.0
            currentSport = BSportType.RUN

            // Emit 4 s of 100 Hz frames BEFORE draining renegotiation so STRIDES accumulate and self-trigger renegotiation inside ACC collector
            val samples100Hz = (0 until 400).map { i ->
                val tMs = i * 10L
                val zMg = (1000.0 + ampMg * cos(omega * (tMs / 1000.0))).roundToInt()
                PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
            }
            for (chunk in samples100Hz.chunked(200)) {
                accFlows[0].tryEmit(PolarAccelerometerData(chunk))
            }
            testScheduler.runCurrent()

            val stridesBefore200Hz = (bridge.getSensor(SensorType.STRIDES)?.value as? Int) ?: 0
            assertTrue(
                "100 Hz ACC must accumulate > 0 STRIDES before/during upgrade (got $stridesBefore200Hz)",
                stridesBefore200Hz > 0
            )
            assertEquals("Tier-3 RUN activation must renegotiate ACC from 100 Hz -> 200 Hz", listOf(100, 200), capturedRates)
            verify(atLeast = 1) { mockApi.stopStreaming(h10IdStr, PmdMeasurementType.ACC) }
            assertEquals(
                "Replacement 200 Hz ACC stream must retain ACC_OWNER_ONLINE after renegotiation",
                PolarOfflineGattCoordinator.ACC_OWNER_ONLINE,
                PolarOfflineGattCoordinator.getAccChannelOwner(h10IdStr)
            )
            assertTrue("highRateRunningDynamicsActive must be true in RUN", bridge.highRateRunningDynamicsActive)

            // Emit 8 s of 200 Hz frames into replacement ACC flow
            val samples200Hz = (0 until 1600).map { i ->
                val tMs = 4_500L + i * 5L
                val zMg = (1000.0 + ampMg * cos(omega * (tMs / 1000.0))).roundToInt()
                PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
            }
            for (chunk in samples200Hz.chunked(40)) {
                accFlows[1].tryEmit(PolarAccelerometerData(chunk))
            }
            testScheduler.runCurrent()

            val stridesAfter200Hz = (bridge.getSensor(SensorType.STRIDES)?.value as? Int) ?: 0
            assertTrue(
                "Cumulative STRIDES must continue monotonically across 100 -> 200 Hz renegotiation (before=$stridesBefore200Hz, after=$stridesAfter200Hz)",
                stridesAfter200Hz > stridesBefore200Hz
            )
            val voCm = bridge.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double
            val gctMs = bridge.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double
            assertNotNull("VERTICAL_OSCILLATION must emit after 100 -> 200 Hz renegotiation", voCm)
            assertNotNull("GROUND_CONTACT_TIME must emit after 100 -> 200 Hz renegotiation", gctMs)
            assertTrue("VO must be within 5.0..13.0 cm (got $voCm)", voCm!! in 5.0..13.0)
            assertTrue("GCT must be within 140..400 ms (got $gctMs)", gctMs!! in 140.0..400.0)

            // Verify end-to-end MySensorManager proxy & all-sensors propagation for WorkoutSamples.db/CSV/FIT
            val proxyVo = sensorManager.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double
            val proxyGct = sensorManager.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double
            assertEquals("MySensorManager proxy VO must match H10 VO", voCm, proxyVo)
            assertEquals("MySensorManager proxy GCT must match H10 GCT", gctMs, proxyGct)

            // Switch RUN -> BIKE via MULTISPORT_TRANSITION_INTENT -> unregisters VO/GCT and downgrades ACC to 100 Hz
            currentSport = BSportType.BIKE
            context.sendBroadcast(Intent(BANALService.MULTISPORT_TRANSITION_INTENT).setPackage(context.packageName))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            testScheduler.runCurrent()

            assertFalse("highRateRunningDynamicsActive must be false in BIKE", bridge.highRateRunningDynamicsActive)
            assertNull("VO sensor must unregister on RUN -> BIKE", bridge.getSensor(SensorType.VERTICAL_OSCILLATION))
            assertNull("GCT sensor must unregister on RUN -> BIKE", bridge.getSensor(SensorType.GROUND_CONTACT_TIME))
            assertEquals("RUN -> BIKE must downgrade ACC back to 100 Hz", listOf(100, 200, 100), capturedRates)
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-122.1 / TST-SET-124.1
    @Test
    fun testH10RapidSportFlipDuringRenegotiation_convergesToLatestSport_andUnsupported200HzHasZeroLoop_TST_SET_124_1() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val testDispatcher = StandardTestDispatcher(testScheduler)

        // Part A: Rapid UNKNOWN -> RUN -> BIKE flip before pending renegotiation relaunches converges directly to 100 Hz
        val h10MacA = "AA:BB:CC:DD:EE:47"
        val h10IdA = "93460002"
        val devIdA = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, h10MacA, "Polar H10 $h10IdA", "Polar Electro", 90, true)
        val mockApiA = mockk<PolarBleApi>(relaxed = true)
        val accAvailableA = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
        }
        val capturedRatesA = mutableListOf<Int?>()
        coEvery { mockApiA.requestStreamSettings(h10IdA, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailableA
        every { mockApiA.startHrStreaming(h10IdA) } returns flowOf()
        every { mockApiA.startAccStreaming(h10IdA, any()) } answers {
            capturedRatesA.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            flow { awaitCancellation() }
        }
        var sportA = BSportType.UNKNOWN
        val bridgeA = PolarOnlineStreamingBridge(
            context = context, mySensorManager = sensorManager, deviceId = devIdA, macAddress = h10MacA,
            dispatcher = testDispatcher, sdkIntProvider = { 34 }, sportTypeProvider = { sportA }, apiFactory = { _, _ -> mockApiA }
        )
        try {
            bridgeA.startSearching()
            bridgeA.getSdkCallbackForTesting().deviceConnected(PolarDeviceInfo(h10IdA, h10MacA, -55, "Polar H10 $h10IdA", true))
            bridgeA.getSdkCallbackForTesting().bleSdkFeatureReady(h10IdA, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertEquals(listOf(100), capturedRatesA)

            // Queue renegotiation for RUN, then immediately flip to BIKE before coroutine executes
            sportA = BSportType.RUN
            bridgeA.syncCadenceSensorRegistration()
            sportA = BSportType.BIKE
            bridgeA.syncCadenceSensorRegistration()
            testScheduler.runCurrent()

            assertEquals("Relaunch must dynamically re-evaluate latest sport (BIKE -> 100 Hz)", listOf(100, 100), capturedRatesA)
            assertFalse(bridgeA.highRateRunningDynamicsActive)
            assertNull(bridgeA.getSensor(SensorType.VERTICAL_OSCILLATION))
        } finally {
            bridgeA.shutDown()
        }

        // Part B: Firmware omitting 200 Hz (50/100 Hz only) -> zero restart loops across repeated processAccData calls
        val h10MacB = "AA:BB:CC:DD:EE:48"
        val h10IdB = "93460003"
        val devIdB = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, h10MacB, "Polar H10 $h10IdB", "Polar Electro", 90, true)
        val mockApiB = mockk<PolarBleApi>(relaxed = true)
        val accOnly100 = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
        }
        val capturedRatesB = mutableListOf<Int?>()
        coEvery { mockApiB.requestStreamSettings(h10IdB, PolarBleApi.PolarDeviceDataType.ACC) } returns accOnly100
        every { mockApiB.startHrStreaming(h10IdB) } returns flowOf()
        every { mockApiB.startAccStreaming(h10IdB, any()) } answers {
            capturedRatesB.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            flow { awaitCancellation() }
        }
        var sportB = BSportType.UNKNOWN
        val bridgeB = PolarOnlineStreamingBridge(
            context = context, mySensorManager = sensorManager, deviceId = devIdB, macAddress = h10MacB,
            dispatcher = testDispatcher, sdkIntProvider = { 34 }, sportTypeProvider = { sportB }, apiFactory = { _, _ -> mockApiB }
        )
        try {
            bridgeB.startSearching()
            bridgeB.getSdkCallbackForTesting().deviceConnected(PolarDeviceInfo(h10IdB, h10MacB, -55, "Polar H10 $h10IdB", true))
            bridgeB.getSdkCallbackForTesting().bleSdkFeatureReady(h10IdB, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            sportB = BSportType.RUN
            repeat(15) { idx ->
                bridgeB.processAccData(
                    PolarAccelerometerData(listOf(PolarAccelerometerData.PolarAccelerometerDataSample((idx + 1) * 10_000_000L, 0, 0, 1000)))
                )
            }
            testScheduler.runCurrent()

            assertEquals("Unsupported 200 Hz firmware must stay at 100 Hz with zero restart loops", listOf(100), capturedRatesB)
            verify(exactly = 0) { mockApiB.stopStreaming(h10IdB, PmdMeasurementType.ACC) }
        } finally {
            bridgeB.shutDown()
        }

        // Part C: Polar Verity Sense stays pinned at 52 Hz across sport transitions with zero renegotiation or VO/GCT
        val verityMac = "AA:BB:CC:DD:EE:49"
        val verityId = "93460004"
        val verityDevId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, verityMac, "Polar Sense $verityId", "Polar Electro", 90, true)
        val mockApiV = mockk<PolarBleApi>(relaxed = true)
        val verityAccSetting = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(52),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
        }
        val verityRates = mutableListOf<Int?>()
        coEvery { mockApiV.requestStreamSettings(verityId, PolarBleApi.PolarDeviceDataType.ACC) } returns verityAccSetting
        coEvery { mockApiV.requestStreamSettings(verityId, PolarBleApi.PolarDeviceDataType.GYRO) } returns verityAccSetting
        coEvery { mockApiV.getAvailableOnlineStreamDataTypes(verityId) } returns emptySet()
        every { mockApiV.startHrStreaming(verityId) } returns flowOf()
        every { mockApiV.startGyroStreaming(verityId, any()) } returns flow { awaitCancellation() }
        every { mockApiV.startAccStreaming(verityId, any()) } answers {
            verityRates.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            flow { awaitCancellation() }
        }
        var sportV = BSportType.UNKNOWN
        val bridgeV = PolarOnlineStreamingBridge(
            context = context, mySensorManager = sensorManager, deviceId = verityDevId, macAddress = verityMac,
            dispatcher = testDispatcher, sdkIntProvider = { 34 }, sportTypeProvider = { sportV }, apiFactory = { _, _ -> mockApiV }
        )
        try {
            bridgeV.startSearching()
            bridgeV.getSdkCallbackForTesting().deviceConnected(PolarDeviceInfo(verityId, verityMac, -55, "Polar Sense $verityId", true))
            bridgeV.getSdkCallbackForTesting().bleSdkFeatureReady(verityId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            sportV = BSportType.RUN
            bridgeV.syncCadenceSensorRegistration()
            testScheduler.runCurrent()
            sportV = BSportType.BIKE
            bridgeV.syncCadenceSensorRegistration()
            testScheduler.runCurrent()

            assertEquals("Verity Sense must stay pinned at 52 Hz with zero renegotiations", listOf(52), verityRates)
            assertNull(bridgeV.getSensor(SensorType.VERTICAL_OSCILLATION))
            assertNull(bridgeV.getSensor(SensorType.GROUND_CONTACT_TIME))
        } finally {
            bridgeV.shutDown()
        }
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testH10AccRenegotiation_cancelledOnDisconnectOfflineYieldAndShutdownWithoutLateRestart_TST_SET_124_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:50"
        val h10Id = "93460005"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(DeviceType.HRM, h10Mac, "Polar H10 $h10Id", "Polar Electro", 90, true)
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
        }
        val capturedRates = mutableListOf<Int?>()
        coEvery { mockApi.requestStreamSettings(h10Id, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startHrStreaming(h10Id) } returns flowOf()
        every { mockApi.startAccStreaming(h10Id, any()) } answers {
            capturedRates.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            flow { awaitCancellation() }
        }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        var currentSport = BSportType.UNKNOWN
        val bridge = PolarOnlineStreamingBridge(
            context = context, mySensorManager = sensorManager, deviceId = h10DevId, macAddress = h10Mac,
            dispatcher = testDispatcher, sdkIntProvider = { 34 }, sportTypeProvider = { currentSport }, apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val info = PolarDeviceInfo(h10Id, h10Mac, -55, "Polar H10 $h10Id", true)
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(info)
            cb.bleSdkFeatureReady(h10Id, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertEquals(listOf(100), capturedRates)

            // 1. Offline GATT yield cancels pending renegotiation so startAccStreaming is never called during yield
            currentSport = BSportType.RUN
            bridge.syncCadenceSensorRegistration()
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context, macAddress = h10Mac, polarId = h10Id,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING),
                timeoutMs = 30_000L, disconnectOnExit = false
            ) {
                testScheduler.runCurrent()
                assertEquals("Offline yield must cancel pending renegotiation without launching 200 Hz", listOf(100), capturedRates)
            }

            // 2. deviceDisconnected() cancels pending renegotiation without late startAccStreaming
            bridge.syncCadenceSensorRegistration()
            cb.deviceDisconnected(info)
            testScheduler.runCurrent()
            assertEquals("Disconnect must cancel pending renegotiation without late restart", listOf(100), capturedRates)

            // 3. Reconnect at 100 Hz (UNKNOWN), queue renegotiation for RUN, then shutDown() -> zero late restart
            currentSport = BSportType.UNKNOWN
            cb.deviceConnected(info)
            cb.bleSdkFeatureReady(h10Id, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertEquals(listOf(100, 100), capturedRates)

            currentSport = BSportType.RUN
            bridge.syncCadenceSensorRegistration()
            bridge.shutDown()
            testScheduler.runCurrent()
            assertEquals("shutDown() must cancel pending renegotiation without late restart", listOf(100, 100), capturedRates)
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-122.1, REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testH10OfflineYieldMidStopStreaming_abortsWithoutRestartDuringYieldAndRecovers200HzVoGctOnResume_TST_SET_124_2() = runTest {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:51"
        val h10Id = "93460006"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10Id", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
            every { maxSettings() } returns PolarSensorSetting(
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to 200,
                    PolarSensorSetting.SettingType.RESOLUTION to 16,
                    PolarSensorSetting.SettingType.RANGE to 8
                )
            )
        }
        val capturedRates = mutableListOf<Int?>()
        val accFlows = mutableListOf<MutableSharedFlow<PolarAccelerometerData>>()
        val yieldEntered = CompletableDeferred<Unit>()
        val releaseYield = CompletableDeferred<Unit>()
        val stopHookFired = AtomicBoolean(false)

        coEvery { mockApi.requestStreamSettings(h10Id, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startHrStreaming(h10Id) } returns flowOf()
        every { mockApi.startAccStreaming(h10Id, any()) } answers {
            capturedRates.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            MutableSharedFlow<PolarAccelerometerData>(extraBufferCapacity = 64).also { accFlows.add(it) }
        }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        var currentSport = BSportType.UNKNOWN
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10DevId,
            macAddress = h10Mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { currentSport },
            apiFactory = { _, _ -> mockApi }
        )

        var yieldJob: kotlinx.coroutines.Job? = null
        every { mockApi.stopStreaming(h10Id, PmdMeasurementType.ACC) } answers {
            if (stopHookFired.compareAndSet(false, true)) {
                // Verify onOldSlotCancelled() already cancelled 100 Hz slot and released ACC_OWNER_ONLINE
                assertFalse("Old 100 Hz ACC slot must already be inactive when stopStreaming runs", bridge.isAccStreamingForTesting())
                assertNull("ACC_OWNER_ONLINE must already be released when stopStreaming runs", PolarOfflineGattCoordinator.getAccChannelOwner(h10Id))
                // Simulate offline GATT yield arriving mid-stopStreaming() with disconnectOnExit = false
                yieldJob = launch(Dispatchers.Unconfined) {
                    PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                        context = context,
                        macAddress = h10Mac,
                        polarId = h10Id,
                        features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING),
                        timeoutMs = 30_000L,
                        disconnectOnExit = false
                    ) {
                        yieldEntered.complete(Unit)
                        releaseYield.await()
                    }
                }
            }
        }

        try {
            bridge.startSearching()
            val info = PolarDeviceInfo(h10Id, h10Mac, -55, "Polar H10 $h10Id", true)
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(info)
            cb.bleSdkFeatureReady(h10Id, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertEquals("Initial H10 ACC in UNKNOWN must be 100 Hz", listOf(100), capturedRates)

            // Trigger 100 -> 200 Hz renegotiation; offline yield arrives mid-stopStreaming()
            currentSport = BSportType.RUN
            bridge.syncCadenceSensorRegistration()
            testScheduler.runCurrent()

            assertTrue("Mid-stopStreaming offline yield must have entered", yieldEntered.isCompleted)
            assertTrue("Bridge must observe active offline yield", bridge.isOfflineGattYielded())
            assertEquals("Zero startAccStreaming calls permitted while offline yield is active", listOf(100), capturedRates)
            assertFalse("ACC stream must remain inactive while yielded", bridge.isAccStreamingForTesting())
            assertNull("ACC_OWNER_ONLINE must stay free during offline yield", PolarOfflineGattCoordinator.getAccChannelOwner(h10Id))

            // Release offline yield (disconnectOnExit = false, bridge still connected) -> must automatically recover 200 Hz ACC + VO/GCT
            releaseYield.complete(Unit)
            yieldJob?.join()
            testScheduler.runCurrent()

            assertFalse(bridge.isOfflineGattYielded())
            assertEquals("resumeAfterOfflineSync must automatically recover 200 Hz ACC stream", listOf(100, 200), capturedRates)
            assertTrue("ACC stream must be active again post-yield", bridge.isAccStreamingForTesting())
            assertEquals(
                "ACC_OWNER_ONLINE must be re-acquired by recovered 200 Hz ACC stream",
                PolarOfflineGattCoordinator.ACC_OWNER_ONLINE,
                PolarOfflineGattCoordinator.getAccChannelOwner(h10Id)
            )

            // Emit 8 s of 200 Hz running frames into recovered ACC flow and verify VO & GCT emit cleanly
            val fStep = 162.0 / 60.0
            val omega = 2.0 * PI * fStep
            val ampMg = ((0.085 / 2.0) * omega * omega / 9.80665) * 1000.0
            val samples200Hz = (0 until 1600).map { i ->
                val tMs = 1_000L + i * 5L
                val zMg = (1000.0 + ampMg * cos(omega * (tMs / 1000.0))).roundToInt()
                PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
            }
            for (chunk in samples200Hz.chunked(40)) {
                accFlows[1].tryEmit(PolarAccelerometerData(chunk))
            }
            testScheduler.runCurrent()

            val voCm = bridge.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double
            val gctMs = bridge.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double
            assertNotNull("Recovered 200 Hz ACC stream must emit VERTICAL_OSCILLATION", voCm)
            assertNotNull("Recovered 200 Hz ACC stream must emit GROUND_CONTACT_TIME", gctMs)
            assertTrue("VO must be within 5.0..13.0 cm (got $voCm)", voCm!! in 5.0..13.0)
            assertTrue("GCT must be within 140..400 ms (got $gctMs)", gctMs!! in 140.0..400.0)
            assertEquals(voCm, sensorManager.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double)
            assertEquals(gctMs, sensorManager.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double)
        } finally {
            if (!releaseYield.isCompleted) releaseYield.complete(Unit)
            bridge.shutDown()
        }
    }

    // REQ-SET-122.1, REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testH10FeatureReadyDuringOfflineYield_defersAccAndRecovers200HzVoGctOnResume_TST_SET_124_2() = runTest {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:52"
        val h10Id = "93460007"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10Id", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
            every { maxSettings() } returns PolarSensorSetting(
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to 200,
                    PolarSensorSetting.SettingType.RESOLUTION to 16,
                    PolarSensorSetting.SettingType.RANGE to 8
                )
            )
        }
        val capturedRates = mutableListOf<Int?>()
        val accFlows = mutableListOf<MutableSharedFlow<PolarAccelerometerData>>()
        coEvery { mockApi.requestStreamSettings(h10Id, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startHrStreaming(h10Id) } returns flowOf()
        every { mockApi.startAccStreaming(h10Id, any()) } answers {
            capturedRates.add(secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull())
            MutableSharedFlow<PolarAccelerometerData>(extraBufferCapacity = 64).also { accFlows.add(it) }
        }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10DevId,
            macAddress = h10Mac,
            dispatcher = testDispatcher,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val info = PolarDeviceInfo(h10Id, h10Mac, -55, "Polar H10 $h10Id", true)
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()

            // One-shot deviceConnected + FEATURE_POLAR_ONLINE_STREAMING arrive while offline GATT yield is active (disconnectOnExit = false)
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = h10Mac,
                polarId = h10Id,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING),
                timeoutMs = 30_000L,
                disconnectOnExit = false
            ) {
                mux.deviceConnected(info)
                mux.bleSdkFeatureReady(h10Id, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
                testScheduler.runCurrent()

                assertTrue("Bridge must be connected during yield", bridge.isReceivingData())
                assertTrue("Bridge must observe active offline yield", bridge.isOfflineGattYielded())
                assertTrue("Zero startAccStreaming calls permitted while offline yield is active", capturedRates.isEmpty())
                assertFalse("ACC stream must remain deferred while yielded", bridge.isAccStreamingForTesting())
                assertNull("ACC_OWNER_ONLINE must remain free while yielded", PolarOfflineGattCoordinator.getAccChannelOwner(h10Id))
            }

            // Yield released (disconnectOnExit = false) -> resumeAfterOfflineSync() must automatically start 200 Hz ACC & emit VO/GCT
            testScheduler.runCurrent()
            assertFalse(bridge.isOfflineGattYielded())
            assertEquals("Deferred one-shot FEATURE_POLAR_ONLINE_STREAMING must recover 200 Hz ACC on resumeAfterOfflineSync()", listOf(200), capturedRates)
            assertTrue("ACC stream must be active after yield release", bridge.isAccStreamingForTesting())
            assertEquals(
                "ACC_OWNER_ONLINE must be acquired on recovered ACC stream",
                PolarOfflineGattCoordinator.ACC_OWNER_ONLINE,
                PolarOfflineGattCoordinator.getAccChannelOwner(h10Id)
            )

            // Emit 8 s of 200 Hz running frames into recovered ACC flow and assert live VO/GCT emission
            val fStep = 162.0 / 60.0
            val omega = 2.0 * PI * fStep
            val ampMg = ((0.085 / 2.0) * omega * omega / 9.80665) * 1000.0
            val samples200Hz = (0 until 1600).map { i ->
                val tMs = 1_000L + i * 5L
                val zMg = (1000.0 + ampMg * cos(omega * (tMs / 1000.0))).roundToInt()
                PolarAccelerometerData.PolarAccelerometerDataSample(tMs * 1_000_000L, 0, 0, zMg)
            }
            for (chunk in samples200Hz.chunked(40)) {
                accFlows[0].tryEmit(PolarAccelerometerData(chunk))
            }
            testScheduler.runCurrent()

            val voCm = bridge.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double
            val gctMs = bridge.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double
            assertNotNull("Recovered ACC stream must emit VERTICAL_OSCILLATION", voCm)
            assertNotNull("Recovered ACC stream must emit GROUND_CONTACT_TIME", gctMs)
            assertTrue("VO must be within 5.0..13.0 cm (got $voCm)", voCm!! in 5.0..13.0)
            assertTrue("GCT must be within 140..400 ms (got $gctMs)", gctMs!! in 140.0..400.0)
            assertEquals(voCm, sensorManager.getSensor(SensorType.VERTICAL_OSCILLATION)?.value as? Double)
            assertEquals(gctMs, sensorManager.getSensor(SensorType.GROUND_CONTACT_TIME)?.value as? Double)
        } finally {
            bridge.shutDown()
        }
    }

    // REQ-SET-122.2 / TST-SET-124.2
    @Test
    fun testH10StaleAbortedRenegotiationAndConcurrentYieldResume_respectsReplacementAndLinearizesPreAcquire_TST_SET_124_2() = runTest {
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Mac = "AA:BB:CC:DD:EE:53"
        val h10Id = "93460008"
        val h10DevId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10Id", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accAvailable = mockk<PolarSensorSetting> {
            every { settings } returns mutableMapOf(
                PolarSensorSetting.SettingType.SAMPLE_RATE to setOf(50, 100, 200),
                PolarSensorSetting.SettingType.RESOLUTION to setOf(16),
                PolarSensorSetting.SettingType.RANGE to setOf(8)
            )
            every { maxSettings() } returns PolarSensorSetting(
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to 200,
                    PolarSensorSetting.SettingType.RESOLUTION to 16,
                    PolarSensorSetting.SettingType.RANGE to 8
                )
            )
        }
        val capturedRates = java.util.Collections.synchronizedList(mutableListOf<Int?>())
        val firstStartReady = java.util.concurrent.CountDownLatch(1)
        val secondStartReady = java.util.concurrent.CountDownLatch(1)
        val thirdStartReady = java.util.concurrent.CountDownLatch(1)
        val stop1Entered = java.util.concurrent.CountDownLatch(1)
        val stop1Release = java.util.concurrent.CountDownLatch(1)
        val stop2Entered = java.util.concurrent.CountDownLatch(1)
        val stop2Release = java.util.concurrent.CountDownLatch(1)
        val stopCounter = java.util.concurrent.atomic.AtomicInteger(0)

        coEvery { mockApi.requestStreamSettings(h10Id, PolarBleApi.PolarDeviceDataType.ACC) } returns accAvailable
        every { mockApi.startHrStreaming(h10Id) } returns flowOf()
        every { mockApi.startAccStreaming(h10Id, any()) } answers {
            val rate = secondArg<PolarSensorSetting>().settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull()
            capturedRates.add(rate)
            when (capturedRates.size) {
                1 -> firstStartReady.countDown()
                2 -> secondStartReady.countDown()
                3 -> thirdStartReady.countDown()
            }
            flow { awaitCancellation() }
        }
        every { mockApi.stopStreaming(h10Id, PmdMeasurementType.ACC) } answers {
            when (stopCounter.incrementAndGet()) {
                1 -> {
                    stop1Entered.countDown()
                    stop1Release.await(5L, java.util.concurrent.TimeUnit.SECONDS)
                }
                2 -> {
                    stop2Entered.countDown()
                    stop2Release.await(5L, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }
        var currentSport = BSportType.UNKNOWN
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10DevId,
            macAddress = h10Mac,
            dispatcher = Dispatchers.IO,
            sdkIntProvider = { 34 },
            sportTypeProvider = { currentSport },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val info = PolarDeviceInfo(h10Id, h10Mac, -55, "Polar H10 $h10Id", true)
            val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
            mux.deviceConnected(info)
            mux.bleSdkFeatureReady(h10Id, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            assertTrue("Initial 100 Hz ACC must start", firstStartReady.await(5L, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(100), capturedRates.toList())

            // 1. Trigger renegotiation #1 (100 -> 200 Hz) and pause it inside stopStreaming()
            currentSport = BSportType.RUN
            bridge.syncCadenceSensorRegistration()
            assertTrue("Renegotiation #1 must enter stopStreaming", stop1Entered.await(5L, java.util.concurrent.TimeUnit.SECONDS))

            // 2. Yield cancels renegotiation #1 while stopStreaming #1 is blocked; exiting yield recovers 200 Hz ACC cleanly
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context, macAddress = h10Mac, polarId = h10Id,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING),
                timeoutMs = 30_000L, disconnectOnExit = false
            ) {
                assertFalse("isActive(ACC) must be false during yield", bridge.isAccStreamingForTesting())
            }
            assertTrue("Post-yield resume must recover 200 Hz ACC", secondStartReady.await(5L, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(100, 200), capturedRates.toList())

            // 3. Trigger replacement renegotiation #2 (200 -> 100 Hz) and hold it in stopStreaming() (isActive=false, isRenegotiating=true)
            currentSport = BSportType.BIKE
            bridge.syncCadenceSensorRegistration()
            assertTrue("Replacement renegotiation #2 must enter stopStreaming", stop2Entered.await(5L, java.util.concurrent.TimeUnit.SECONDS))

            // 4. Release stale renegotiation #1 so its finally { onAccRenegotiationAborted() } runs WHILE replacement #2 is active
            stop1Release.countDown()
            Thread.sleep(60L)
            assertEquals(
                "Stale cancelled renegotiation's onAborted must NOT launch ACC while replacement renegotiation is in progress",
                listOf(100, 200),
                capturedRates.toList()
            )

            // 5. Release replacement renegotiation #2 -> finishes cleanly at 100 Hz (BIKE)
            stop2Release.countDown()
            assertTrue("Replacement renegotiation #2 must launch 100 Hz ACC", thirdStartReady.await(5L, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(100, 200, 100), capturedRates.toList())
        } finally {
            stop1Release.countDown()
            stop2Release.countDown()
            bridge.shutDown()
        }
    }

    @Test
    fun testVeritySensePpi_gatedToRestingAndExplicitlyStoppedOnWorkoutEntryWithoutInterruptingImu() = runTest {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:91"
        val verityPolarId = "E1F2A3B4"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityPolarId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val gyroSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.isFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) } returns true
        coEvery { mockApi.getAvailableOnlineStreamDataTypes(verityPolarId) } returns setOf(
            PolarBleApi.PolarDeviceDataType.ACC,
            PolarBleApi.PolarDeviceDataType.GYRO,
            PolarBleApi.PolarDeviceDataType.PPI
        )
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.GYRO) } returns gyroSetting
        every { mockApi.startHrStreaming(verityPolarId) } returns flow {
            emit(PolarHrData(listOf(PolarHrData.PolarHrSample(74, 74, 0, emptyList(), emptyList(), true, true, false))))
            awaitCancellation()
        }
        every { mockApi.startAccStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startPpiStreaming(verityPolarId) } returns flow {
            emit(
                PolarPpiData(
                    listOf(
                        PolarPpiData.PolarPpiSample(
                            ppi = 659, hr = 91, errorEstimate = 10, blockerBit = false,
                            skinContactStatus = true, skinContactSupported = true, timeStamp = 0uL
                        )
                    )
                )
            )
            awaitCancellation()
        }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(verityPolarId, verityMac, -55, "Polar Sense $verityPolarId", true))
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            assertTrue("PPI must stream during resting READY mode", bridge.isPpiStreamingForTesting())
            assertTrue("HR must be active", bridge.isHrJobActiveForTesting())
            assertTrue("ACC must be active", bridge.isAccStreamingForTesting())
            assertTrue("GYRO must be active", bridge.isGyroStreamingForTesting())
            assertEquals(659, bridge.getSensor(SensorType.RR_INTERVAL)?.value)

            // Transition READY -> TRACKING: stop PPI explicitly, clear latched RR, preserve HR/ACC/GYRO
            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            bridge.syncVerityPpiForTrackingModeForTesting()
            testScheduler.runCurrent()

            assertFalse("PPI must stop immediately on TRACKING entry", bridge.isPpiStreamingForTesting())
            verify(exactly = 1) { mockApi.stopStreaming(verityPolarId, PmdMeasurementType.PPI) }
            assertNull("Latched RR sensor must be cleared to null on workout entry", bridge.getSensor(SensorType.RR_INTERVAL)?.value)
            assertTrue("HR stream must remain active", bridge.isHrJobActiveForTesting())
            assertTrue("ACC stream must remain active", bridge.isAccStreamingForTesting())
            assertTrue("GYRO stream must remain active", bridge.isGyroStreamingForTesting())

            // Re-firing FEATURE_POLAR_ONLINE_STREAMING while TRACKING must NOT restart PPI
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()
            assertFalse("PPI must not start while TRACKING", bridge.isPpiStreamingForTesting())
            verify(exactly = 1) { mockApi.startPpiStreaming(verityPolarId) }

            // Transition TRACKING -> READY: resting PPI resumes cleanly
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.syncVerityPpiForTrackingModeForTesting()
            testScheduler.runCurrent()
            assertTrue("PPI must resume when returning to READY", bridge.isPpiStreamingForTesting())
            verify(exactly = 2) { mockApi.startPpiStreaming(verityPolarId) }
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.shutDown()
        }
    }

    @Test
    fun testProcessHrData_zeroOrLostContactDoesNotDisarmWatchdogAndClearsAfterTenSeconds() = runTest {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:92"
        val verityPolarId = "E1F2A3B5"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityPolarId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.startHrStreaming(verityPolarId) } returns flow { awaitCancellation() }
        val spySensorManager = spyk(sensorManager)

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = spySensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(verityPolarId, verityMac, -55, "Polar Sense $verityPolarId", true))
            testScheduler.runCurrent()

            assertNotNull("Silent HR watchdog must be armed after connect", bridge.getSilentHrWatchdogJobForTesting())

            // hr == 0 or lost contact must NOT disarm silentHrWatchdog
            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 0,
                            correctedHr = 0,
                            ppgQuality = 0,
                            rrsMs = emptyList(),
                            rrs = emptyList(),
                            contactStatus = true,
                            contactStatusSupported = true,
                            rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 1_000L
            )
            assertNotNull("hr == 0 must NOT disarm silent HR watchdog", bridge.getSilentHrWatchdogJobForTesting())

            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 82,
                            correctedHr = 82,
                            ppgQuality = 0,
                            rrsMs = emptyList(),
                            rrs = emptyList(),
                            contactStatus = false,
                            contactStatusSupported = true,
                            rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 2_000L
            )
            assertNotNull("Lost contact must NOT disarm silent HR watchdog", bridge.getSilentHrWatchdogJobForTesting())

            // Valid HR sample disarms watchdog and updates sensor
            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 140,
                            correctedHr = 140,
                            ppgQuality = 0,
                            rrsMs = emptyList(),
                            rrs = emptyList(),
                            contactStatus = true,
                            contactStatusSupported = true,
                            rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 5_000L
            )
            assertNull("Valid HR sample must disarm silent HR watchdog", bridge.getSilentHrWatchdogJobForTesting())
            assertEquals(140, bridge.getSensor(SensorType.HR)?.value)

            // 10s of lost-contact packets clears HR to null and triggers primary HR re-evaluation
            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 0,
                            correctedHr = 0,
                            ppgQuality = 0,
                            rrsMs = emptyList(),
                            rrs = emptyList(),
                            contactStatus = false,
                            contactStatusSupported = true,
                            rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 6_000L
            )
            assertEquals(140, bridge.getSensor(SensorType.HR)?.value)

            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 0,
                            correctedHr = 0,
                            ppgQuality = 0,
                            rrsMs = emptyList(),
                            rrs = emptyList(),
                            contactStatus = false,
                            contactStatusSupported = true,
                            rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 16_000L
            )
            assertNull("10s of invalid HR must clear mHeartRateSensor to null", bridge.getSensor(SensorType.HR)?.value)
            verify(atLeast = 1) { spySensorManager.reevaluatePrimaryHrSensor() }
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testProcessPpiData_sustainedArtifactGapClearsLatchedRrSensor() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:93"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense E1F2A3B6", "Polar Electro", 90, true
        )
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )

        try {
            val cleanSample = PolarPpiData.PolarPpiSample(
                ppi = 659, hr = 91, errorEstimate = 10, blockerBit = false,
                skinContactStatus = true, skinContactSupported = true, timeStamp = 0uL
            )
            val blockedSample = PolarPpiData.PolarPpiSample(
                ppi = 659, hr = 91, errorEstimate = 10, blockerBit = true,
                skinContactStatus = true, skinContactSupported = true, timeStamp = 0uL
            )
            bridge.processPpiData(PolarPpiData(listOf(cleanSample)), packetArrivalMs = 1_000L)
            assertEquals(659, bridge.getSensor(SensorType.RR_INTERVAL)?.value)

            bridge.processPpiData(PolarPpiData(listOf(blockedSample)), packetArrivalMs = 2_000L)
            assertEquals(659, bridge.getSensor(SensorType.RR_INTERVAL)?.value)

            bridge.processPpiData(PolarPpiData(listOf(blockedSample)), packetArrivalMs = 12_000L)
            assertNull("10s of ARTIFACT_GAP-only PPI beats must clear latched 659ms RR to null", bridge.getSensor(SensorType.RR_INTERVAL)?.value)
        } finally {
            bridge.shutDown()
        }
    }

    @Test
    fun testProcessPpiData_droppedInTrackingModeWithoutReLatchingRr() {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:94"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense E1F2A3B7", "Polar Electro", 90, true
        )
        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = Dispatchers.Unconfined,
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN }
        )

        try {
            val cleanSample = PolarPpiData.PolarPpiSample(
                ppi = 659, hr = 91, errorEstimate = 10, blockerBit = false,
                skinContactStatus = true, skinContactSupported = true, timeStamp = 0uL
            )
            val received = mutableListOf<RrBeatEvent>()
            bridge.addRrBeatListener(RrBeatListener { received.add(it) })

            // In-flight Dispatchers.IO PPI packet arriving after TRACKING started must be dropped cleanly
            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            bridge.processPpiData(PolarPpiData(listOf(cleanSample)), packetArrivalMs = 2_000L)
            assertNull("processPpiData in TRACKING must not create or latch RR_INTERVAL", bridge.getSensor(SensorType.RR_INTERVAL))
            assertEquals("processPpiData in TRACKING must not dispatch RR beats", 0, received.size)
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.shutDown()
        }
    }

    @Test
    fun testRecoverHrStreamAndWorkoutConnect_forceStopsOrphanedFirmwarePpiEvenWhenNotLocallyStreaming() = runTest {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:95"
        val verityPolarId = "E1F2A3B8"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityPolarId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val accSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        val gyroSetting = PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.ACC) } returns accSetting
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.GYRO) } returns gyroSetting
        every { mockApi.startHrStreaming(verityPolarId) } returns flow { awaitCancellation() }
        every { mockApi.startAccStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            // 1. Connecting directly during TRACKING (post-crash restart) must force-stop orphaned firmware PPI
            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(verityPolarId, verityMac, -55, "Polar Sense $verityPolarId", true))
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            assertFalse("PPI must never stream locally in TRACKING", bridge.isPpiStreamingForTesting())
            verify(exactly = 1) { mockApi.stopStreaming(verityPolarId, PmdMeasurementType.PPI) }

            // 2. Subsequent HR staleness recovery must also force-stop firmware PPI even though wasStreamingPpi == false
            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 140, correctedHr = 140, ppgQuality = 0, rrsMs = emptyList(), rrs = emptyList(),
                            contactStatus = true, contactStatusSupported = true, rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = 1_000L
            )
            bridge.triggerHrStalenessCheckForTesting(nowMs = 17_000L)
            testScheduler.runCurrent()

            verify(exactly = 2) { mockApi.stopStreaming(verityPolarId, PmdMeasurementType.PPI) }
            assertNotNull("evaluateHrStaleness must re-arm staleness timer for 60s recovery cooldown", bridge.getHrStalenessJobForTesting())
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.shutDown()
        }
    }

    @Test
    fun testStartPpiCollection_abortsIfTrackingStartsAcrossGetAvailableOnlineStreamDataTypes() = runTest {
        // Verifies REQ-SET-126 / TST-SET-128
        val verityMac = "AA:BB:CC:DD:EE:96"
        val verityPolarId = "E1F2A3B9"
        val db = DevicesDatabaseManager.getInstance(context)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, verityMac, "Polar Sense $verityPolarId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        val gate = CompletableDeferred<Set<PolarBleApi.PolarDeviceDataType>>()
        coEvery { mockApi.getAvailableOnlineStreamDataTypes(verityPolarId) } coAnswers { gate.await() }
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.ACC) } returns
            PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        coEvery { mockApi.requestStreamSettings(verityPolarId, PolarBleApi.PolarDeviceDataType.GYRO) } returns
            PolarSensorSetting(mapOf(PolarSensorSetting.SettingType.SAMPLE_RATE to 52))
        every { mockApi.startAccStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }
        every { mockApi.startGyroStreaming(verityPolarId, any()) } returns flow { awaitCancellation() }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = verityId,
            macAddress = verityMac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(verityPolarId, verityMac, -55, "Polar Sense $verityPolarId", true))
            cb.bleSdkFeatureReady(verityPolarId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
            testScheduler.runCurrent()

            // Workout begins while getAvailableOnlineStreamDataTypes() is suspended -> PPI must NOT start!
            TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
            gate.complete(setOf(PolarBleApi.PolarDeviceDataType.PPI, PolarBleApi.PolarDeviceDataType.ACC, PolarBleApi.PolarDeviceDataType.GYRO))
            testScheduler.runCurrent()

            assertFalse("PPI slot must exit cleanly without starting when mode flipped to TRACKING mid-suspend", bridge.isPpiStreamingForTesting())
            verify(exactly = 0) { mockApi.startPpiStreaming(verityPolarId) }
        } finally {
            TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
            bridge.shutDown()
        }
    }

    @Test
    fun testHrStalenessTimer_rearmsAfterOfflineYieldResume() = runTest {
        // Verifies REQ-SET-126 / TST-SET-128
        val h10Mac = "AA:BB:CC:DD:EE:97"
        val h10PolarId = "E1F2A3BA"
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM, h10Mac, "Polar H10 $h10PolarId", "Polar Electro", 90, true
        )
        val mockApi = mockk<PolarBleApi>(relaxed = true)
        every { mockApi.startHrStreaming(h10PolarId) } returns flow { awaitCancellation() }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val bridge = PolarOnlineStreamingBridge(
            context = context,
            mySensorManager = sensorManager,
            deviceId = h10Id,
            macAddress = h10Mac,
            dispatcher = StandardTestDispatcher(testScheduler),
            sdkIntProvider = { 34 },
            sportTypeProvider = { BSportType.RUN },
            apiFactory = { _, _ -> mockApi }
        )

        try {
            bridge.startSearching()
            val cb = bridge.getSdkCallbackForTesting()
            cb.deviceConnected(PolarDeviceInfo(h10PolarId, h10Mac, -55, "Polar H10 $h10PolarId", true))
            testScheduler.runCurrent()

            bridge.processHrData(
                PolarHrData(
                    listOf(
                        PolarHrData.PolarHrSample(
                            hr = 145, correctedHr = 145, ppgQuality = 0, rrsMs = emptyList(), rrs = emptyList(),
                            contactStatus = true, contactStatusSupported = true, rrAvailable = false
                        )
                    )
                ),
                packetArrivalMs = System.currentTimeMillis()
            )
            assertNotNull("HR staleness timer must be armed after valid HR", bridge.getHrStalenessJobForTesting())

            // Offline yield cancels staleness timer; resuming while connected MUST re-arm it
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context, macAddress = h10Mac, polarId = h10PolarId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING),
                timeoutMs = 30_000L, disconnectOnExit = false
            ) {
                assertNull("HR staleness timer must be cancelled during offline yield", bridge.getHrStalenessJobForTesting())
            }
            assertNotNull("HR staleness timer must be re-armed on resumeAfterOfflineSync()", bridge.getHrStalenessJobForTesting())
        } finally {
            bridge.shutDown()
        }
    }
}
