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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

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
        DeviceDataRepository.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        sensorManager = MySensorManager(context)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        PolarOfflineGattCoordinator.resetForTesting()
        sensorManager.shutDown()
        DeviceDataRepository.resetForTesting()
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
}
