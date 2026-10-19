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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/**
 * Unit test suite verifying immutable BLE notification payload dispatch and decoupled snapshot
 * characteristics (REQ-CON-020.1, TST-CON-012.1).
 */
class BLEPayloadDispatchTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt
    private val postedRunnables = mutableListOf<Runnable>()

    // Use IdentityHashMap so instances with matching UUIDs do not collide in map lookups
    private val characteristicValues = Collections.synchronizedMap(IdentityHashMap<BluetoothGattCharacteristic, ByteArray>())
    private val characteristicUuids = Collections.synchronizedMap(IdentityHashMap<BluetoothGattCharacteristic, UUID>())

    private class TestHarnessBTLEDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : MyBTLEDevice(context, sensorManager, DeviceType.HRM, deviceId, address) {

        val receivedCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        override fun addSensors() {}

        override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic != null) {
                receivedCharacteristics.add(characteristic)
            }
        }

        public override fun obtainSnapshotCharacteristic(
            source: BluetoothGattCharacteristic,
            payload: ByteArray
        ): BluetoothGattCharacteristic? {
            return super.obtainSnapshotCharacteristic(source, payload)
        }
    }

    @Before
    fun setUp() {
        postedRunnables.clear()
        characteristicValues.clear()
        characteristicUuids.clear()

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

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
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

        mockkConstructor(BluetoothGattCharacteristic::class)
        every { anyConstructed<BluetoothGattCharacteristic>().setValue(any<ByteArray>()) } answers {
            val instance = self as BluetoothGattCharacteristic
            val bytes = firstArg<ByteArray>()
            // Crucial: clone the bytes so internal backing storage is distinct per instance
            characteristicValues[instance] = bytes.clone()
            true
        }
        every { anyConstructed<BluetoothGattCharacteristic>().value } answers {
            val instance = self as BluetoothGattCharacteristic
            characteristicValues[instance]
        }
        every { anyConstructed<BluetoothGattCharacteristic>().getValue() } answers {
            val instance = self as BluetoothGattCharacteristic
            characteristicValues[instance]
        }
        every { anyConstructed<BluetoothGattCharacteristic>().uuid } answers {
            val instance = self as BluetoothGattCharacteristic
            characteristicUuids[instance] ?: BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        }
        every { anyConstructed<BluetoothGattCharacteristic>().getUuid() } answers {
            val instance = self as BluetoothGattCharacteristic
            characteristicUuids[instance] ?: BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        }
        every { anyConstructed<BluetoothGattCharacteristic>().properties } returns BluetoothGattCharacteristic.PROPERTY_NOTIFY
        every { anyConstructed<BluetoothGattCharacteristic>().permissions } returns 0

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)

        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun injectImmediateHandler(device: MyBTLEDevice) {
        val mockHandler = mockk<Handler>(relaxed = true)
        every { mockHandler.post(any()) } answers {
            val r = firstArg<Runnable>()
            postedRunnables.add(r)
            true
        }
        val handlerField = MyBTLEDevice::class.java.getDeclaredField("mHandler")
        handlerField.isAccessible = true
        handlerField.set(device, mockHandler)
    }

    @Test
    fun testBleNotificationPayloadClonedAndDecoupled_Api33Signature() {
        val device = TestHarnessBTLEDevice(mockContext, mockSensorManager, 101L, "AA:BB:CC:DD:EE:01")
        injectImmediateHandler(device)

        val hrmUuid = BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        val characteristic = BluetoothGattCharacteristic(
            hrmUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        characteristicUuids[characteristic] = hrmUuid
        val originalBytes = byteArrayOf(0x01, 0x50)
        characteristic.setValue(originalBytes)

        val callback = device.gattCallbackForTesting

        // Invoke API 33 signature: onCharacteristicChanged(gatt, characteristic, value)
        callback.onCharacteristicChanged(mockGatt, characteristic, originalBytes)

        // Simulate native Binder thread buffer reuse: in-place mutation of the original byte array
        originalBytes[0] = 0xFF.toByte()
        originalBytes[1] = 0xAA.toByte()

        // Drain the posted Runnables on the main thread
        postedRunnables.forEach { it.run() }

        // Assert that the delivered payload remains the uncorrupted initial snapshot
        assertEquals(1, device.receivedCharacteristics.size)
        val received = device.receivedCharacteristics[0]
        assertNotNull(received.value)
        assertArrayEquals(byteArrayOf(0x01, 0x50), received.value)
    }

    @Test
    fun testBleNotificationPayloadClonedAndDecoupled_LegacySignature() {
        val device = TestHarnessBTLEDevice(mockContext, mockSensorManager, 102L, "AA:BB:CC:DD:EE:02")
        injectImmediateHandler(device)

        val hrmUuid = BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        val characteristic = BluetoothGattCharacteristic(
            hrmUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        characteristicUuids[characteristic] = hrmUuid
        val originalBytes = byteArrayOf(0x10, 0x42)
        characteristic.setValue(originalBytes)

        val callback = device.gattCallbackForTesting

        // Invoke legacy 2-arg signature
        callback.onCharacteristicChanged(mockGatt, characteristic)

        // Mutate characteristic buffer in place
        originalBytes[0] = 0x00
        originalBytes[1] = 0x00

        // Drain main-thread runnables
        postedRunnables.forEach { it.run() }

        assertEquals(1, device.receivedCharacteristics.size)
        val received = device.receivedCharacteristics[0]
        assertNotNull(received.value)
        assertArrayEquals(byteArrayOf(0x10, 0x42), received.value)
    }

    @Test
    fun testCharacteristicReadClonesPayloadOnSuccess() {
        val device = TestHarnessBTLEDevice(mockContext, mockSensorManager, 103L, "AA:BB:CC:DD:EE:03")
        injectImmediateHandler(device)

        val hrmUuid = BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        val characteristic = BluetoothGattCharacteristic(
            hrmUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            0
        )
        characteristicUuids[characteristic] = hrmUuid
        val originalBytes = byteArrayOf(0x02, 0x75)
        characteristic.setValue(originalBytes)

        val callback = device.gattCallbackForTesting
        callback.onCharacteristicRead(mockGatt, characteristic, BluetoothGatt.GATT_SUCCESS)

        // Mutate buffer in place
        originalBytes[0] = 0x00
        originalBytes[1] = 0x00

        // Drain main-thread runnables
        postedRunnables.forEach { it.run() }

        assertEquals(1, device.receivedCharacteristics.size)
        val received = device.receivedCharacteristics[0]
        assertArrayEquals(byteArrayOf(0x02, 0x75), received.value)
    }

    @Test
    fun testCharacteristicPoolIndexWrappingAndShutdownSafety() {
        val device = TestHarnessBTLEDevice(mockContext, mockSensorManager, 104L, "AA:BB:CC:DD:EE:04")
        injectImmediateHandler(device)
        val hrmUuid = BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        val source = BluetoothGattCharacteristic(hrmUuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        characteristicUuids[source] = hrmUuid

        // Allocate 16 snapshots: 8-slot ring buffer wraps around cleanly twice
        val snapshots = mutableListOf<BluetoothGattCharacteristic>()
        for (i in 0 until 16) {
            val payload = byteArrayOf(i.toByte())
            val snapshot = device.obtainSnapshotCharacteristic(source, payload)
            assertNotNull(snapshot)
            snapshots.add(snapshot!!)
        }

        // Verify slot wrapping: snapshots 0 and 8 share the same instance in the 8-slot ring buffer
        assertSame(snapshots[0], snapshots[8])
        assertSame(snapshots[1], snapshots[9])

        // Verify shutdown returns null
        device.shutDown()
        val afterShutdown = device.obtainSnapshotCharacteristic(source, byteArrayOf(0x01))
        assertNull(afterShutdown)
    }

    @Test
    fun testCharacteristicPoolConcurrentAccess() {
        val device = TestHarnessBTLEDevice(mockContext, mockSensorManager, 105L, "AA:BB:CC:DD:EE:05")
        injectImmediateHandler(device)
        val hrmUuid = BluetoothConstants.getCharacteristicUUID(DeviceType.HRM)
        val source = BluetoothGattCharacteristic(hrmUuid, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0)
        characteristicUuids[source] = hrmUuid

        val threads = 4
        val iterationsPerThread = 50
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val latch = java.util.concurrent.CountDownLatch(threads)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)

        for (t in 0 until threads) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val snapshot = device.obtainSnapshotCharacteristic(source, byteArrayOf((t * 100 + i).toByte()))
                        if (snapshot == null) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(0, errors.get())
    }
}
