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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
}
