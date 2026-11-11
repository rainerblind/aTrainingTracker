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
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying the thread-safe serialized GATT operation queue in MyBTLEDevice (TST-CON-028.1, REQ-CON-028.2).
 */
class MyBTLEDeviceQueueTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private lateinit var testDevice: TestBTLEDevice

    private class TestBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceType: DeviceType,
        deviceId: Long,
        address: String
    ) : MyBTLEDevice(context, sensorManager, deviceType, deviceId, address) {
        override fun addSensors() {}
        override fun measurementCharacteristicUpdate(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {}

        fun setMockGatt(gatt: BluetoothGatt, handler: Handler) {
            mBluetoothGatt = gatt
            mHandler = handler
        }

        fun getLegacyQueue(): java.util.Queue<BluetoothGattCharacteristic> = mReadCharacteristicQueue

        fun setBatteryCharacteristic(char: BluetoothGattCharacteristic?) {
            val field = MyBTLEDevice::class.java.getDeclaredField("mBatteryCharacteristic")
            field.isAccessible = true
            field.set(this, char)
        }

        fun getBatteryReadRunnable(): Runnable {
            val field = MyBTLEDevice::class.java.getDeclaredField("mBatteryReadRunnable")
            field.isAccessible = true
            return field.get(this) as Runnable
        }

        public override fun readNextCharacteristic() {
            super.readNextCharacteristic()
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
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
            ContextCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), any())
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<java.io.Serializable>()) } answers { self as android.content.Intent }

        mockkConstructor(BluetoothGattCharacteristic::class)
        every { anyConstructed<BluetoothGattCharacteristic>().setValue(any<ByteArray>()) } returns true
        every { anyConstructed<BluetoothGattCharacteristic>().value } returns byteArrayOf()
        every { anyConstructed<BluetoothGattCharacteristic>().getValue() } returns byteArrayOf()
        every { anyConstructed<BluetoothGattCharacteristic>().uuid } returns BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL
        every { anyConstructed<BluetoothGattCharacteristic>().getUuid() } returns BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL
        every { anyConstructed<BluetoothGattCharacteristic>().getIntValue(any(), any()) } returns 85
        every { anyConstructed<BluetoothGattCharacteristic>().properties } returns BluetoothGattCharacteristic.PROPERTY_NOTIFY
        every { anyConstructed<BluetoothGattCharacteristic>().permissions } returns 0

        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockContext = mockk(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)

        val mockHandler = mockk<Handler>(relaxed = true)
        every { mockHandler.post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }

        testDevice = TestBTLEDevice(mockContext, mockSensorManager, DeviceType.HRM, 1L, "AA:BB:CC:DD:EE:FF")
        testDevice.setMockGatt(mockGatt, mockHandler)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testQueue_executesOperationsSequentiallyWithoutDropping() {
        val desc1 = mockk<BluetoothGattDescriptor>(relaxed = true)
        val desc2 = mockk<BluetoothGattDescriptor>(relaxed = true)

        every { mockGatt.writeDescriptor(any()) } returns true

        // Enqueue two descriptor write operations
        testDevice.enqueueWriteDescriptor(desc1, byteArrayOf(0x01, 0x00))
        testDevice.enqueueWriteDescriptor(desc2, byteArrayOf(0x02, 0x00))

        // First operation should be executing and queue should contain second operation
        assertTrue("Queue should have pending operation active", testDevice.isGattOperationPendingForTesting)

        // Simulate callback for first descriptor write
        testDevice.gattCallbackForTesting.onDescriptorWrite(mockGatt, desc1, BluetoothGatt.GATT_SUCCESS)

        // Simulate callback for second descriptor write
        testDevice.gattCallbackForTesting.onDescriptorWrite(mockGatt, desc2, BluetoothGatt.GATT_SUCCESS)

        assertEquals("Queue should be empty after both callbacks complete", 0, testDevice.gattQueueForTesting.size)
        assertFalse("GATT operation should no longer be pending", testDevice.isGattOperationPendingForTesting)
    }

    @Test
    fun testQueue_clearsOnDisconnect() {
        val desc = mockk<BluetoothGattDescriptor>(relaxed = true)
        testDevice.enqueueWriteDescriptor(desc, byteArrayOf(0x01))

        testDevice.disconnectFromGatt()

        assertEquals("Queue must be empty after disconnect", 0, testDevice.gattQueueForTesting.size)
        assertFalse("Pending flag must be false after disconnect", testDevice.isGattOperationPendingForTesting)
    }

    /**
     * REQ-CON-028.2 / TST-CON-028.1: Verify characteristic read operation serializes strictly
     * behind an in-flight descriptor write via mGattQueue without split-brain collision.
     */
    @Test
    fun testQueue_characteristicReadSerializesWithDescriptorWrite() {
        val desc = mockk<BluetoothGattDescriptor>(relaxed = true)
        val char = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { mockGatt.writeDescriptor(any()) } returns true
        every { mockGatt.readCharacteristic(any()) } returns true

        // Enqueue write descriptor first, then read characteristic
        testDevice.enqueueWriteDescriptor(desc, byteArrayOf(0x01, 0x00))
        testDevice.enqueueReadCharacteristic(char)

        assertTrue("First operation must be active pending", testDevice.isGattOperationPendingForTesting)
        assertEquals("Second operation must wait in queue", 1, testDevice.gattQueueForTesting.size)
        assertTrue(testDevice.gattQueueForTesting.peek() is MyBTLEDevice.ReadCharacteristicOp)

        // Simulate descriptor write completion callback
        testDevice.gattCallbackForTesting.onDescriptorWrite(mockGatt, desc, BluetoothGatt.GATT_SUCCESS)

        // Second operation is now active and queue is empty
        assertTrue("Read operation must now be pending", testDevice.isGattOperationPendingForTesting)
        assertEquals(0, testDevice.gattQueueForTesting.size)

        // Simulate characteristic read completion callback
        testDevice.gattCallbackForTesting.onCharacteristicRead(mockGatt, char, BluetoothGatt.GATT_SUCCESS)

        assertFalse("GATT operation must not be pending after read completes", testDevice.isGattOperationPendingForTesting)
        assertEquals(0, testDevice.gattQueueForTesting.size)
    }

    /**
     * REQ-CON-028.2 / TST-CON-028.1: Verify mBatteryReadRunnable routes through enqueueReadCharacteristic
     * rather than adding directly to mReadCharacteristicQueue outside mGattQueue.
     */
    @Test
    fun testBatteryReadRunnable_enqueuesViaGattQueue() {
        val batteryChar = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { mockGatt.readCharacteristic(any()) } returns true

        testDevice.setBatteryCharacteristic(batteryChar)
        val runnable = testDevice.getBatteryReadRunnable()

        runnable.run()

        // Should have enqueued read operation into mGattQueue and initiated execution
        assertTrue("mGattOperationPending must be true after battery read runnable runs", testDevice.isGattOperationPendingForTesting)
        assertTrue("Legacy mReadCharacteristicQueue must remain empty", testDevice.getLegacyQueue().isEmpty())

        // Simulate callback
        testDevice.gattCallbackForTesting.onCharacteristicRead(mockGatt, batteryChar, BluetoothGatt.GATT_SUCCESS)
        assertFalse("Pending flag must be cleared after onCharacteristicRead", testDevice.isGattOperationPendingForTesting)
    }

    /**
     * REQ-CON-028.2 / TST-CON-028.1: Verify readNextCharacteristic drains legacy mReadCharacteristicQueue
     * into mGattQueue for backward compatibility.
     */
    @Test
    fun testReadNextCharacteristic_drainsLegacyReadQueueIntoGattQueue() {
        val char1 = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { mockGatt.readCharacteristic(any()) } returns true

        testDevice.getLegacyQueue().add(char1)
        testDevice.readNextCharacteristic()

        // Legacy queue must be empty, operation moved to mGattQueue
        assertTrue("Legacy queue must be drained", testDevice.getLegacyQueue().isEmpty())
        assertTrue("Operation must be pending in GATT queue", testDevice.isGattOperationPendingForTesting)
    }
}
