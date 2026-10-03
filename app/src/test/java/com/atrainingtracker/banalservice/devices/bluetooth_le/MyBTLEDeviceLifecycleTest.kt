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
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Queue
import java.util.UUID

/**
 * Automated unit test suite verifying BLE GATT lifecycle management, thread concurrency,
 * TOCTOU race condition immunity, queue draining, and NullPointerException shielding
 * in MyBTLEDevice (REQ-CON-012, TST-CON-003, ATT-1348).
 */
class MyBTLEDeviceLifecycleTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic
    private val postedRunnables = mutableListOf<Runnable>()
    private val delayedRunnables = mutableListOf<Runnable>()
    private val removedRunnables = mutableListOf<Runnable>()

    // Concrete test harness subclass exposing protected APIs
    private class TestBTLEDevice(
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

        fun getQueue(): Queue<BluetoothGattCharacteristic> = mReadCharacteristicQueue
        fun setGatt(gatt: BluetoothGatt?) { mBluetoothGatt = gatt }
        fun getGatt(): BluetoothGatt? = mBluetoothGatt
        public override fun readNextCharacteristic() { super.readNextCharacteristic() }
        public override fun disconnectFromGatt() { super.disconnectFromGatt() }

        fun triggerCharacteristicUpdate(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val method = MyBTLEDevice::class.java.getDeclaredMethod(
                "characteristicUpdate",
                BluetoothGatt::class.java,
                BluetoothGattCharacteristic::class.java
            )
            method.isAccessible = true
            method.invoke(this, gatt, characteristic)
        }

        fun getGattCallback(): BluetoothGattCallback {
            val field = MyBTLEDevice::class.java.getDeclaredField("mGattCallback")
            field.isAccessible = true
            return field.get(this) as BluetoothGattCallback
        }
    }

    @Before
    fun setUp() {
        postedRunnables.clear()
        delayedRunnables.clear()
        removedRunnables.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.equals(any(), any()) } answers {
            java.util.Objects.equals(firstArg<CharSequence?>(), secondArg<CharSequence?>())
        }
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val s = firstArg<CharSequence?>()
            s == null || s.isEmpty()
        }

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            postedRunnables.add(runnable)
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            delayedRunnables.add(runnable)
            true
        }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers {
            val runnable = firstArg<Runnable>()
            removedRunnables.add(runnable)
        }
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers {
            postedRunnables.clear()
            delayedRunnables.clear()
        }

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        mockSensorManager = mockk(relaxed = true)

        mockGatt = mockk(relaxed = true)
        mockCharacteristic = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TC-1: Verify that when mReadCharacteristicQueue contains characteristics but mBluetoothGatt
     * is null, readNextCharacteristic() processes the queue and executes posted runnables
     * without throwing NullPointerException (fixing ATT-1348).
     */
    @Test
    fun testReadNextCharacteristic_whenGattIsNull_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.readNextCharacteristic()

        // Assert: Queue was polled, runnable was posted, and running it throws no NPE
        assertTrue(device.getQueue().isEmpty())
        assertEquals(1, postedRunnables.size)

        // Execute posted runnable on looper
        postedRunnables.first().run()
        // Success: No NullPointerException thrown
    }

    /**
     * TC-2: Verify that when mReadCharacteristicQueue is empty and sensors are not registered,
     * the empty-queue service discovery branch does not crash when mBluetoothGatt is null.
     */
    @Test
    fun testReadNextCharacteristic_whenQueueEmpty_andGattIsNull_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)
        assertTrue(device.getQueue().isEmpty())

        // Act
        device.readNextCharacteristic()

        // Assert: Runnable was posted for service registration
        assertEquals(1, postedRunnables.size)

        // Execute posted runnable
        postedRunnables.first().run()
        // Success: Defensive null guard on mBluetoothGatt returned cleanly without NPE
    }

    /**
     * TC-3: Verify that if mBluetoothGatt is set to null concurrently between the posting of
     * the runnable and its execution on the main Looper (TOCTOU race), no crash occurs.
     */
    @Test
    fun testReadNextCharacteristic_toctouRaceConditionSimulation_doesNotThrowNpe() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act: Post read runnable
        device.readNextCharacteristic()
        assertEquals(1, postedRunnables.size)

        // Simulate concurrent disconnection on a Binder thread before Looper processes runnable
        device.setGatt(null)

        // Execute the queued runnable on the main Looper
        postedRunnables.first().run()

        // Verify: No NPE was thrown
        verify(exactly = 0) { mockGatt.readCharacteristic(any()) }
    }

    /**
     * TC-4: Verify that invoking disconnectFromGatt() purges all queued characteristics,
     * nullifies mBluetoothGatt under mGattLock, and cancels pending battery re-reads.
     */
    @Test
    fun testDisconnectFromGatt_clearsQueue_andNullifiesGattUnderLock() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)
        device.getQueue().add(mockk(relaxed = true))

        // Act
        device.disconnectFromGatt()

        // Assert: Queue is cleared and GATT reference is immediately nullified
        assertTrue(device.getQueue().isEmpty())
        assertNull(device.getGatt())

        // Execute the asynchronous cleanup posted by disconnectFromGatt
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }
    }

    /**
     * TC-5: Verify that the 5-minute delayed battery re-read runnable is cancelled
     * when disconnectFromGatt() is invoked, preventing execution against dead connections.
     */
    @Test
    fun testDelayedBatteryRunnable_cancelledOnDisconnection_doesNotExecute() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)

        val batteryChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { batteryChar.uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL
        every { batteryChar.value } returns byteArrayOf(85)
        every { batteryChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) } returns 85

        // Act 1: Simulate characteristic update for battery level
        device.triggerCharacteristicUpdate(mockGatt, batteryChar)

        // Verify delayed runnable was scheduled
        assertEquals(1, delayedRunnables.size)
        val scheduledRunnable = delayedRunnables.first()

        // Act 2: Peripheral disconnects
        device.disconnectFromGatt()

        // Verify removeCallbacks was invoked for the battery runnable
        assertTrue(removedRunnables.contains(scheduledRunnable))

        // Act 3: Simulate looper firing the delayed runnable anyway
        scheduledRunnable.run()

        // Verify: Queue remains empty, zero reads attempted because GATT is null
        assertTrue(device.getQueue().isEmpty())
    }

    /**
     * TC-6: Verify that onConnectionStateChange(STATE_CONNECTED) guards discoverServices()
     * if mBluetoothGatt was not initialized or returned null.
     */
    @Test
    fun testOnConnectionStateChange_connected_guardsDiscoverServicesWhenGattNull() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(null)

        val callback = device.getGattCallback()

        // Act
        callback.onConnectionStateChange(mockGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)

        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Verify: Zero exceptions, discoverServices not called on null
        verify(exactly = 0) { mockGatt.discoverServices() }
    }

    /**
     * TC-7: Verify that shutDown() cleanly clears callbacks, queue, and nullifies GATT.
     */
    @Test
    fun testShutDown_cleansUpAllCallbacksAndGatt() {
        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.shutDown()

        // Assert
        assertTrue(device.getQueue().isEmpty())
        assertNull(device.getGatt())
    }

    /**
     * TC-8: Verify that when BLUETOOTH_CONNECT permission is missing, operations
     * terminate cleanly without SecurityException.
     */
    @Test
    fun testReadNextCharacteristic_whenPermissionDenied_returnsEarlyWithoutCallingGatt() {
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_DENIED

        val device = TestBTLEDevice(mockContext, mockSensorManager, 1L, "AA:BB:CC:DD:EE:FF")
        device.setGatt(mockGatt)
        device.getQueue().add(mockCharacteristic)

        // Act
        device.readNextCharacteristic()
        assertEquals(1, postedRunnables.size)
        postedRunnables.first().run()

        // Assert: Permission check aborted execution before calling gatt
        verify(exactly = 0) { mockGatt.readCharacteristic(any()) }
    }
}
