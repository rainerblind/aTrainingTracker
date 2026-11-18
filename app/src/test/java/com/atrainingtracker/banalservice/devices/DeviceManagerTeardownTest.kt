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

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying defensive sensor teardown and unregistration in DeviceManager (REQ-UI-349, TST-UI-309.4).
 */
class DeviceManagerTeardownTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Long>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.havePermission(any()) } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockDevicesDbManager = mockk(relaxed = true)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockLocationManager = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)
    }

    @After
    fun tearDown() {
        try {
            unmockkAll()
        } catch (_: Throwable) {
        }
    }

    /**
     * TST-UI-309.4: DeviceManager.removeAndStopRemoteDevice executes defensively
     * without throwing NullPointerException when device is not in mMyRemoteDevices.
     */
    @Test
    fun testRemoveAndStopRemoteDevice_safeWhenDeviceNotRegistered() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        // Act: invoking removeAndStopRemoteDevice for an unmapped device ID must execute gracefully without NPE
        deviceManager.removeAndStopRemoteDevice(99999L)
    }

    /**
     * TST-TRK-039.4: createPairedRemoteDevices queries paired remote devices via DeviceDataRepository (REQ-TRK-039.5, REQ-TRK-066.3).
     */
    @Test
    fun testCreatePairedRemoteDevices_delegatesToRepository() {
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, mockRepo)
        deviceManager.createPairedRemoteDevices(Protocol.BLUETOOTH_LE)

        verify(exactly = 1) { mockRepo.getPairedRemoteDevices(Protocol.BLUETOOTH_LE) }
    }

    /**
     * TST-CON-050.1: POLAR_LIVE_IMU_CHANGED tears down existing BTLEHeartRateDevice and waits
     * for its GATT close completion callback before creating/searching the replacement PolarOnlineStreamingBridge,
     * preserving RrBeatSource wiring and < API 33 fallback.
     */
    @Test
    fun testPolarLiveImuChanged_waitsForGattTeardownCompletionBeforeCreatingReplacement() {
        val deviceId = 42L
        val mac = "24:AC:AC:24:82:56"
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        every { mockRepo.isPaired(deviceId) } returns true
        every { mockRepo.getProtocol(deviceId) } returns Protocol.BLUETOOTH_LE
        every { mockRepo.getDeviceType(deviceId) } returns DeviceType.HRM
        every { mockRepo.getBluetoothMACAddress(deviceId) } returns mac
        every { mockRepo.isPolarLiveImuBridgeEligible(deviceId, mac) } returns true
        every { TrainingApplication.getNumberOfSearchTries() } returns 3

        mockkConstructor(PolarOnlineStreamingBridge::class)
        every { anyConstructed<PolarOnlineStreamingBridge>().deviceId } returns deviceId
        every { anyConstructed<PolarOnlineStreamingBridge>().deviceType } returns DeviceType.HRM
        every { anyConstructed<PolarOnlineStreamingBridge>().startSearching() } just Runs
        every { anyConstructed<PolarOnlineStreamingBridge>().shutDown() } just Runs
        every { anyConstructed<PolarOnlineStreamingBridge>().shutDown(any()) } just Runs

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, mockRepo)
        deviceManager.setSdkIntForTesting(34)

        val oldBtleDevice = mockk<BTLEHeartRateDevice>(relaxed = true)
        every { oldBtleDevice.deviceId } returns deviceId
        val teardownCallbackSlot = slot<Runnable>()
        every { oldBtleDevice.shutDown(capture(teardownCallbackSlot)) } just Runs
        deviceManager.mMyRemoteDevices[deviceId] = oldBtleDevice

        val toggleIntent = mockk<Intent>(relaxed = true)
        every { toggleIntent.hasExtra(BANALService.DEVICE_ID) } returns true
        every { toggleIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns deviceId
        every { toggleIntent.getBooleanExtra(BANALService.POLAR_LIVE_IMU_ENABLED, false) } returns true

        deviceManager.polarLiveImuChangedReceiverForTesting.onReceive(mockBanalService, toggleIntent)

        // Old device removed & shutDown(callback) invoked, but replacement NOT created until callback runs
        verify(exactly = 1) { oldBtleDevice.shutDown(any()) }
        assertFalse(deviceManager.mMyRemoteDevices.containsKey(deviceId))
        verify(exactly = 0) { anyConstructed<PolarOnlineStreamingBridge>().startSearching() }

        // Complete async GATT close -> replacement bridge is created and searched
        teardownCallbackSlot.captured.run()
        val replacement = deviceManager.mMyRemoteDevices[deviceId]
        assertTrue(replacement is PolarOnlineStreamingBridge)
        verify(exactly = 1) { anyConstructed<PolarOnlineStreamingBridge>().startSearching() }

        deviceManager.shutDown()
    }

    /**
     * TST-CON-050.1: Below API 33, toggling polarLiveImuEnabled keeps BTLEHeartRateDevice fallback
     * without tearing down or creating PolarOnlineStreamingBridge.
     */
    @Test
    fun testPolarLiveImuChanged_belowApi33KeepsBtleHeartRateFallback() {
        val deviceId = 43L
        val mac = "24:AC:AC:25:0F:A6"
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        every { mockRepo.isPaired(deviceId) } returns true
        every { mockRepo.getProtocol(deviceId) } returns Protocol.BLUETOOTH_LE
        every { mockRepo.getDeviceType(deviceId) } returns DeviceType.HRM
        every { mockRepo.getBluetoothMACAddress(deviceId) } returns mac
        every { mockRepo.isPolarLiveImuBridgeEligible(deviceId, mac) } returns true

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, mockRepo)
        deviceManager.setSdkIntForTesting(32)

        val oldBtleDevice = mockk<BTLEHeartRateDevice>(relaxed = true)
        deviceManager.mMyRemoteDevices[deviceId] = oldBtleDevice

        val toggleIntent = mockk<Intent>(relaxed = true)
        every { toggleIntent.hasExtra(BANALService.DEVICE_ID) } returns true
        every { toggleIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns deviceId
        every { toggleIntent.getBooleanExtra(BANALService.POLAR_LIVE_IMU_ENABLED, false) } returns true

        deviceManager.polarLiveImuChangedReceiverForTesting.onReceive(mockBanalService, toggleIntent)

        verify(exactly = 0) { oldBtleDevice.shutDown(any()) }
        assertSame(oldBtleDevice, deviceManager.mMyRemoteDevices[deviceId])
    }

    /**
     * TST-CON-050.1 (Bounce 1/3 heal): Rapid follow-up POLAR_LIVE_IMU_CHANGED while GATT teardown
     * is still in flight must NOT bypass the teardown wait; replacement uses the latest state only
     * once the active teardown completes.
     */
    @Test
    fun testPolarLiveImuChanged_rapidToggleWaitsForActiveTeardownAndUsesLatestState() {
        val deviceId = 44L
        val mac = "24:AC:AC:24:82:57"
        var bridgeEligibleInRepo = true
        val mockRepo = mockk<DeviceDataRepository>(relaxed = true)
        every { mockRepo.isPaired(deviceId) } returns true
        every { mockRepo.getProtocol(deviceId) } returns Protocol.BLUETOOTH_LE
        every { mockRepo.getDeviceType(deviceId) } returns DeviceType.HRM
        every { mockRepo.getBluetoothMACAddress(deviceId) } returns mac
        every { mockRepo.isPolarLiveImuBridgeEligible(deviceId, mac) } answers { bridgeEligibleInRepo }
        every { TrainingApplication.getNumberOfSearchTries() } returns 3

        mockkConstructor(BTLEHeartRateDevice::class)
        every { anyConstructed<BTLEHeartRateDevice>().deviceId } returns deviceId
        every { anyConstructed<BTLEHeartRateDevice>().deviceType } returns DeviceType.HRM
        every { anyConstructed<BTLEHeartRateDevice>().startSearching() } just Runs
        every { anyConstructed<BTLEHeartRateDevice>().shutDown() } just Runs

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager, mockRepo)
        deviceManager.setSdkIntForTesting(34)

        val oldBtleDevice = mockk<BTLEHeartRateDevice>(relaxed = true)
        every { oldBtleDevice.deviceId } returns deviceId
        val teardownCallbackSlot = slot<Runnable>()
        every { oldBtleDevice.shutDown(capture(teardownCallbackSlot)) } just Runs
        deviceManager.mMyRemoteDevices[deviceId] = oldBtleDevice

        val enableIntent = mockk<Intent>(relaxed = true)
        every { enableIntent.hasExtra(BANALService.DEVICE_ID) } returns true
        every { enableIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns deviceId
        every { enableIntent.getBooleanExtra(BANALService.POLAR_LIVE_IMU_ENABLED, false) } returns true

        val disableIntent = mockk<Intent>(relaxed = true)
        every { disableIntent.hasExtra(BANALService.DEVICE_ID) } returns true
        every { disableIntent.getLongExtra(BANALService.DEVICE_ID, -1) } returns deviceId
        every { disableIntent.getBooleanExtra(BANALService.POLAR_LIVE_IMU_ENABLED, false) } returns false

        // 1. Toggle true -> starts async teardown
        deviceManager.polarLiveImuChangedReceiverForTesting.onReceive(mockBanalService, enableIntent)
        assertFalse(deviceManager.mMyRemoteDevices.containsKey(deviceId))

        // 2. Rapidly toggle back to false BEFORE teardown callback completes
        bridgeEligibleInRepo = false
        deviceManager.polarLiveImuChangedReceiverForTesting.onReceive(mockBanalService, disableIntent)
        assertFalse(
            "Replacement must NOT be created while active GATT teardown is still pending",
            deviceManager.mMyRemoteDevices.containsKey(deviceId)
        )
        verify(exactly = 0) { anyConstructed<BTLEHeartRateDevice>().startSearching() }

        // 3. Teardown finishes -> recreates BTLEHeartRateDevice (latest state) and starts searching
        teardownCallbackSlot.captured.run()
        assertTrue(deviceManager.mMyRemoteDevices[deviceId] is BTLEHeartRateDevice)
        verify(exactly = 1) { anyConstructed<BTLEHeartRateDevice>().startSearching() }

        deviceManager.shutDown()
    }
}
