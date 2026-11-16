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
}
