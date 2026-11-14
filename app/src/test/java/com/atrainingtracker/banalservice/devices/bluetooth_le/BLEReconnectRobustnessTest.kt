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

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying deterministic search timeout lifecycle, exponential reconnect backoff,
 * and shutdown guards in MyBTLEDevice (REQ-CON-020.2, TST-CON-012.2).
 */
class BLEReconnectRobustnessTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private lateinit var mockBluetoothManager: BluetoothManager
    private lateinit var mockAdapter: BluetoothAdapter
    private lateinit var mockRemoteDevice: BluetoothDevice

    private val postedDelayedRunnables = mutableListOf<Pair<Runnable, Long>>()
    private val removedRunnables = mutableListOf<Runnable>()

    private class TestReconnectBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : MyBTLEDevice(context, sensorManager, DeviceType.HRM, deviceId, address) {
        override fun addSensors() {}
        override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {}

        fun triggerNotifyStopSearching(success: Boolean) {
            super.notifyStopSearching(success)
        }

        fun setHasConnectedInSession(hasConnected: Boolean) {
            setHasConnectedInSessionForTesting(hasConnected)
        }

        fun isScanning(): Boolean = isScanningForTesting
        fun getScanCallback(): ScanCallback? = scanCallbackForTesting
    }

    @Before
    fun setUp() {
        MyBTLEDevice.clearScanTimestampsForTesting()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        postedDelayedRunnables.clear()
        removedRunnables.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            java.util.Objects.equals(firstArg<CharSequence?>(), secondArg<CharSequence?>())
        }
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(androidx.core.content.ContextCompat::class)
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_SCAN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_ADMIN)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(any(), Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { androidx.core.content.ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(com.atrainingtracker.banalservice.database.DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<com.atrainingtracker.banalservice.database.DevicesDatabaseManager>(relaxed = true)
        every { com.atrainingtracker.banalservice.database.DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }

        mockkConstructor(android.bluetooth.le.ScanFilter.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setDeviceAddress(any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(android.bluetooth.le.ScanSettings.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().setScanMode(any()) } answers { self as android.bluetooth.le.ScanSettings.Builder }
        every { anyConstructed<android.bluetooth.le.ScanSettings.Builder>().build() } returns mockk(relaxed = true)

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)
        mockBluetoothManager = mockk(relaxed = true)
        mockAdapter = mockk(relaxed = true)
        mockRemoteDevice = mockk(relaxed = true)

        val mockScanner = mockk<BluetoothLeScanner>(relaxed = true)
        every { mockAdapter.bluetoothLeScanner } returns mockScanner
        every { mockAdapter.isEnabled } returns true

        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice(any<String>()) } returns mockRemoteDevice
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        unmockkAll()
    }

    private fun injectTrackingHandler(device: MyBTLEDevice): Handler {
        val mockHandler = mockk<Handler>(relaxed = true)
        every { mockHandler.postDelayed(any(), any()) } answers {
            val r = firstArg<Runnable>()
            val delay = secondArg<Long>()
            postedDelayedRunnables.add(Pair(r, delay))
            true
        }
        every { mockHandler.removeCallbacks(any<Runnable>()) } answers {
            val r = firstArg<Runnable>()
            removedRunnables.add(r)
        }
        every { mockHandler.removeCallbacksAndMessages(null) } answers {}

        val handlerField = MyBTLEDevice::class.java.getDeclaredField("mHandler")
        handlerField.isAccessible = true
        handlerField.set(device, mockHandler)
        return mockHandler
    }

    @Test
    fun testStartSearchingCancelsPriorTimeoutAndSchedulesNew() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 201L, "AA:BB:CC:DD:EE:11")
        injectTrackingHandler(device)

        device.startSearching()

        // Verify prior search timeout was removed
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))

        // Verify configurable BLE search timeout (default 5,000ms) was scheduled
        val timeoutScheduled = postedDelayedRunnables.any {
            it.first == device.searchTimeoutRunnable && it.second == TrainingApplication.getBtleSearchPeriodMs()
        }
        assertTrue(timeoutScheduled)
    }

    @Test
    fun testNotifyStopSearchingSuccessCancelsTimeoutAndResetsBackoff() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 202L, "AA:BB:CC:DD:EE:12")
        injectTrackingHandler(device)

        device.triggerNotifyStopSearching(true)

        // Verify search timeout was cancelled
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))
        assertEquals(1000L, device.currentReconnectDelayMs)
    }

    @Test
    fun testExponentialReconnectBackoffOnDisconnect() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 203L, "AA:BB:CC:DD:EE:13")
        injectTrackingHandler(device)

        val callback = device.gattCallbackForTesting

        // Initial delay starts at 1,000 ms
        assertEquals(1000L, device.currentReconnectDelayMs)

        // 1st Disconnect: schedules at 1,000ms, advances backoff to 2,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(2000L, device.currentReconnectDelayMs)
        assertEquals(1000L, postedDelayedRunnables.last().second)

        // 2nd Disconnect: schedules at 2,000ms, advances to 4,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(4000L, device.currentReconnectDelayMs)
        assertEquals(2000L, postedDelayedRunnables.last().second)

        // 3rd Disconnect: schedules at 4,000ms, advances to 8,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(8000L, device.currentReconnectDelayMs)
        assertEquals(4000L, postedDelayedRunnables.last().second)

        // 4th Disconnect: schedules at 8,000ms, advances to 16,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(16000L, device.currentReconnectDelayMs)
        assertEquals(8000L, postedDelayedRunnables.last().second)

        // 5th Disconnect: schedules at 16,000ms, remains capped at 16,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertEquals(16000L, device.currentReconnectDelayMs)
        assertEquals(16000L, postedDelayedRunnables.last().second)

        // Now simulate successful reconnection: delay must reset to 1,000ms
        device.triggerNotifyStopSearching(true)
        assertEquals(1000L, device.currentReconnectDelayMs)
    }

    @Test
    fun testShutDownSetsFlagRemovesCallbacksAndSuppressesDisconnect() {
        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 204L, "AA:BB:CC:DD:EE:14")
        val handler = injectTrackingHandler(device)

        assertFalse(device.isShutDown)

        device.shutDown()

        assertTrue(device.isShutDown)
        assertTrue(removedRunnables.contains(device.searchTimeoutRunnable))
        assertTrue(removedRunnables.contains(device.reconnectRunnable))
        verify { handler.removeCallbacksAndMessages(null) }

        // Clear runnables list and verify disconnect after shutdown does not schedule reconnect
        postedDelayedRunnables.clear()
        device.gattCallbackForTesting.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_DISCONNECTED)
        assertTrue(postedDelayedRunnables.isEmpty())

        // Also verify startSearching after shutdown is a no-op
        device.startSearching()
        assertTrue(postedDelayedRunnables.isEmpty())
    }

    @Test
    fun testMidWorkoutDisconnect_persistsReconnectionAfterSearchTimeout_reconnectsSuccessfully() {
        // REQ-CON-036.1, REQ-CON-036.2, TST-CON-036.1
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 205L, "AA:BB:CC:DD:EE:15")
        injectTrackingHandler(device)

        // Device previously connected in session
        device.setHasConnectedInSession(true)
        assertTrue(device.isMidSessionOrWorkoutActive)

        // Mid-workout LSTO Disconnect (HCI status = 8)
        val callback = device.gattCallbackForTesting
        callback.onConnectionStateChange(mockGatt, 8, BluetoothProfile.STATE_DISCONNECTED)

        // 1st Reconnect runnable posted at 1,000ms; next delay advanced to 2,000ms
        assertEquals(2000L, device.currentReconnectDelayMs)
        assertEquals(1000L, postedDelayedRunnables.last().second)
        val firstReconnectRunnable = postedDelayedRunnables.last().first

        // Simulate 1st reconnect firing -> calls startSearching()
        postedDelayedRunnables.clear()
        firstReconnectRunnable.run()

        // startSearching schedules search timeout (5,000ms)
        assertTrue(postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })

        // 1st Search timeout fires (zero scan results due to body shadowing)
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()

        // MUST NOT abandon reconnection! Must reschedule reconnect at 2,000ms; next backoff advanced to 4,000ms
        val secondReconnectScheduled = postedDelayedRunnables.any {
            it.first == device.reconnectRunnable && it.second == 2000L
        }
        assertTrue("mReconnectRunnable must be rescheduled after search timeout mid-workout", secondReconnectScheduled)
        assertEquals(4000L, device.currentReconnectDelayMs)

        // 2nd Search timeout fires -> must reschedule at 4,000ms; next backoff 8,000ms
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()
        val thirdReconnectScheduled = postedDelayedRunnables.any {
            it.first == device.reconnectRunnable && it.second == 4000L
        }
        assertTrue("mReconnectRunnable must back off exponentially on successive timeouts", thirdReconnectScheduled)
        assertEquals(8000L, device.currentReconnectDelayMs)

        // Peripheral returns and connects: backoff must reset to 1,000ms
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
        assertEquals(1000L, device.currentReconnectDelayMs)
        assertTrue(device.hasConnectedInSession())
    }

    @Test
    fun testInitialStartupSearch_offlineSensor_terminatesCleanlyWithoutInfiniteRetry() {
        // REQ-CON-036.1, TST-CON-036.2
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 206L, "AA:BB:CC:DD:EE:16")
        injectTrackingHandler(device)

        assertFalse(device.hasConnectedInSession())
        assertFalse(device.isMidSessionOrWorkoutActive)

        // Startup search starts
        device.startSearching()
        assertTrue(postedDelayedRunnables.any { it.first == device.searchTimeoutRunnable })

        // Search timeout fires for offline sensor
        postedDelayedRunnables.clear()
        device.searchTimeoutRunnable.run()

        // Verify mReconnectRunnable is NOT posted (terminates cleanly without infinite retries)
        val reconnectScheduled = postedDelayedRunnables.any { it.first == device.reconnectRunnable }
        assertFalse("Never-connected offline sensor at startup must NOT schedule infinite reconnect loops", reconnectScheduled)
        assertFalse(device.isReceivingData)
    }

    @Test
    fun testScanQuotaExceeded_throttlesScanAndFallsBackToDirectConnect() {
        // REQ-CON-036.3, TST-CON-036.3
        MyBTLEDevice.clearScanTimestampsForTesting()

        // Seed 4 timestamps within 30s window to saturate quota
        val now = System.currentTimeMillis()
        for (i in 0 until 4) {
            MyBTLEDevice.addScanTimestampForTesting(now - (i * 1000L))
        }
        assertEquals(4, MyBTLEDevice.getScanTimestampsCountForTesting())

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 207L, "AA:BB:CC:DD:EE:17")
        injectTrackingHandler(device)

        // startSearching should detect quota saturation, skip scan assist, and fall back to direct connect
        device.startSearching()

        assertFalse("Scan assist must be throttled when scan quota is saturated", device.isScanning())
    }

    @Test
    fun testOnScanFailed_fallsBackToGattConnect() {
        // REQ-CON-036.4, TST-CON-036.3
        MyBTLEDevice.clearScanTimestampsForTesting()

        val device = TestReconnectBTLEDevice(mockContext, mockSensorManager, 208L, "AA:BB:CC:DD:EE:18")
        injectTrackingHandler(device)

        device.startSearching()
        assertTrue(device.isScanning())

        val scanCallback = device.getScanCallback()
        assertNotNull(scanCallback)

        // Trigger onScanFailed (e.g. application registration failed)
        scanCallback!!.onScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)

        assertFalse("mIsScanning must be cleared on scan failure", device.isScanning())
    }
}
