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

package com.atrainingtracker.banalservice.devices.bluetooth_le.search_new

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
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
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

/**
 * Unit test suite verifying robust GATT characteristic queue progression,
 * null-safe enqueueing, and state resetting in BTSearchForNewDevicesEngine
 * (REQ-CON-018, TST-CON-009, ATT-2224).
 */
class BTSearchForNewDevicesEngineTest {

    private lateinit var mockContext: Context
    private lateinit var mockCallback: BTSearchForNewDevicesEngine.IBTSearchForNewDevicesEngineInterface
    private val postedRunnables = mutableListOf<Runnable>()

    // Test harness exposing protected members for test verification
    private class TestSearchEngine(
        context: Context,
        deviceType: DeviceType,
        callback: IBTSearchForNewDevicesEngineInterface
    ) : BTSearchForNewDevicesEngine(context, deviceType, callback) {

        fun getGattsMap(): MutableMap<String, BluetoothGatt> = mBTGatts
        fun getInformedMap(): MutableMap<String, Boolean> = mInformedDevices
        fun getNameMap(): MutableMap<String, String> = mNameMap
        fun getManufacturerMap(): MutableMap<String, String> = mManufacturerMap
        fun getBatteryMap(): MutableMap<String, Int> = mBatteryPercentage
        fun getQueueMap(): MutableMap<String, Queue<BluetoothGattCharacteristic>> = mReadCharacteristicQueue

        public override fun enqueueCharacteristicIfPresent(
            address: String,
            service: BluetoothGattService?,
            charUuid: UUID?
        ) {
            super.enqueueCharacteristicIfPresent(address, service, charUuid)
        }

        public override fun resetTrackingState() {
            super.resetTrackingState()
        }

        public override fun readNextCharacteristic(address: String) {
            super.readNextCharacteristic(address)
        }

        public override fun newDeviceFound(address: String) {
            super.newDeviceFound(address)
        }
    }

    @Before
    fun setUp() {
        postedRunnables.clear()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(ActivityCompat::class)
        every {
            ActivityCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT)
        } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockk(relaxed = true)

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            postedRunnables.add(runnable)
            true
        }

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper

        val mockBluetoothManager = mockk<BluetoothManager>(relaxed = true)
        val mockBluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        every { mockContext.getSystemService(Context.BLUETOOTH_SERVICE) } returns mockBluetoothManager
        every { mockBluetoothManager.adapter } returns mockBluetoothAdapter

        mockCallback = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun drainPostedRunnables() {
        while (postedRunnables.isNotEmpty()) {
            val runnable = postedRunnables.removeAt(0)
            runnable.run()
        }
    }

    /**
     * TST-CON-009.1: Defensive queue enqueueing must omit null characteristics
     * when services or characteristics are missing.
     */
    @Test
    fun testEnqueue_omitsNullCharacteristics() {
        val engine = TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)
        val testAddress = "AA:BB:CC:DD:EE:01"
        val queue = LinkedList<BluetoothGattCharacteristic>()
        engine.getQueueMap()[testAddress] = queue

        // 1. Service is null -> should not crash or add to queue
        engine.enqueueCharacteristicIfPresent(testAddress, null, UUID.randomUUID())
        assertTrue("Queue must remain empty when service is null", queue.isEmpty())

        // 2. Characteristic UUID is null -> should not crash or add to queue
        val mockService = mockk<BluetoothGattService>()
        engine.enqueueCharacteristicIfPresent(testAddress, mockService, null)
        assertTrue("Queue must remain empty when UUID is null", queue.isEmpty())

        // 3. Service returns null characteristic -> should not add null to queue
        val charUuid = UUID.randomUUID()
        every { mockService.getCharacteristic(charUuid) } returns null
        engine.enqueueCharacteristicIfPresent(testAddress, mockService, charUuid)
        assertTrue("Queue must remain empty when characteristic is missing from service", queue.isEmpty())

        // 4. Valid characteristic -> should add non-null characteristic
        val mockChar = mockk<BluetoothGattCharacteristic>()
        val validUuid = UUID.randomUUID()
        every { mockService.getCharacteristic(validUuid) } returns mockChar
        engine.enqueueCharacteristicIfPresent(testAddress, mockService, validUuid)
        assertEquals("Queue must contain exactly 1 element", 1, queue.size)
        assertEquals(mockChar, queue.peek())
    }

    /**
     * TST-CON-009.2: Deadlock-free read progression must advance immediately
     * if polled characteristic is null or if gatt.readCharacteristic returns false.
     */
    @Test
    fun testReadNextCharacteristic_handlesNullAndReadFailureGracefully() {
        val engine = TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)
        val testAddress = "AA:BB:CC:DD:EE:02"
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        engine.getGattsMap()[testAddress] = mockGatt

        // Create a queue with:
        // 1. A null entry (simulating any legacy null insertion)
        // 2. A characteristic where gatt.readCharacteristic() returns false
        val queue = LinkedList<BluetoothGattCharacteristic?>()
        queue.add(null)

        val failingChar = mockk<BluetoothGattCharacteristic>()
        every { failingChar.uuid } returns UUID.randomUUID()
        every { mockGatt.readCharacteristic(failingChar) } returns false
        queue.add(failingChar)

        @Suppress("UNCHECKED_CAST")
        engine.getQueueMap()[testAddress] = queue as Queue<BluetoothGattCharacteristic>

        // Act: trigger readNextCharacteristic
        engine.readNextCharacteristic(testAddress)
        drainPostedRunnables()

        // Assert: Queue was drained completely and discovery finished
        assertTrue("Queue must be completely drained", queue.isEmpty())
        // For HRM, when queue is empty, newDeviceFound should be called
        verify(exactly = 1) {
            mockCallback.onNewDeviceFound(DeviceType.HRM, testAddress, any(), any(), -1)
        }
    }

    /**
     * TST-CON-009.3: Comprehensive lifecycle & state reset must clear all internal tracking
     * maps and disconnect/close existing GATT connections, enabling rediscovery.
     */
    @Test
    fun testResetTrackingState_clearsAllMapsAndEnablesRediscovery() {
        val engine = TestSearchEngine(mockContext, DeviceType.HRM, mockCallback)
        val testAddress = "AA:BB:CC:DD:EE:03"
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)

        engine.getGattsMap()[testAddress] = mockGatt
        engine.getInformedMap()[testAddress] = true
        engine.getNameMap()[testAddress] = "Test HRM Sensor"
        engine.getManufacturerMap()[testAddress] = "Test Manufacturer"
        engine.getBatteryMap()[testAddress] = 95
        engine.getQueueMap()[testAddress] = LinkedList()

        // Act: reset tracking state
        engine.resetTrackingState()
        drainPostedRunnables()

        // Verify all tracking structures are cleared
        assertTrue("mBTGatts must be empty", engine.getGattsMap().isEmpty())
        assertTrue("mInformedDevices must be empty", engine.getInformedMap().isEmpty())
        assertTrue("mNameMap must be empty", engine.getNameMap().isEmpty())
        assertTrue("mManufacturerMap must be empty", engine.getManufacturerMap().isEmpty())
        assertTrue("mBatteryPercentage must be empty", engine.getBatteryMap().isEmpty())
        assertTrue("mReadCharacteristicQueue must be empty", engine.getQueueMap().isEmpty())

        // Verify gatt disconnect and close were requested
        verify(exactly = 1) { mockGatt.disconnect() }
        verify(exactly = 1) { mockGatt.close() }

        // Verify that the same device can be reported again after reset
        engine.newDeviceFound(testAddress)
        verify(exactly = 1) {
            mockCallback.onNewDeviceFound(DeviceType.HRM, testAddress, null, null, -1)
        }
    }

    /**
     * Test Bike Power discovery handles empty queue with power service present.
     */
    @Test
    fun testBikePowerDiscovery_completesWhenQueueEmpty() {
        val engine = TestSearchEngine(mockContext, DeviceType.BIKE_POWER, mockCallback)
        val testAddress = "AA:BB:CC:DD:EE:04"
        val mockGatt = mockk<BluetoothGatt>(relaxed = true)
        val mockPowerService = mockk<BluetoothGattService>()

        every { mockGatt.getService(any()) } returns mockPowerService
        engine.getGattsMap()[testAddress] = mockGatt
        val queue = LinkedList<BluetoothGattCharacteristic>()
        engine.getQueueMap()[testAddress] = queue

        // Act: readNextCharacteristic with empty queue
        engine.readNextCharacteristic(testAddress)
        drainPostedRunnables()

        // Verify newDeviceFound is called for BIKE_POWER
        verify(exactly = 1) {
            mockCallback.onNewDeviceFound(DeviceType.BIKE_POWER, testAddress, any(), any(), -1)
        }
    }
}
