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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Before
    fun setUp() {
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

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)
        mockBluetoothManager = mockk(relaxed = true)
        mockAdapter = mockk(relaxed = true)
        mockRemoteDevice = mockk(relaxed = true)

        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns mockAdapter
        every { mockAdapter.getRemoteDevice(any<String>()) } returns mockRemoteDevice
    }

    @After
    fun tearDown() {
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

        // Verify 10,000ms timeout was scheduled
        val timeoutScheduled = postedDelayedRunnables.any {
            it.first == device.searchTimeoutRunnable && it.second == 10000L
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
}
