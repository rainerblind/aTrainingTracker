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

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying BTLERunSpeedDevice thread lifecycle hygiene, stale distance
 * accumulation timeout, disconnect state reset, and field inheritance (REQ-CON-026, TST-CON-026).
 */
class BTLERunSpeedDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt

    private class TestBTLERunSpeedDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLERunSpeedDevice(context, sensorManager, deviceId, address) {

        public override fun unregisterSensors() {
            super.unregisterSensors()
        }

        public override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.measurementCharacteristicUpdate(gatt, characteristic)
        }

        override fun notifySensorsChanged() {
            // No-op for unit testing
        }

        fun getDeviceAddress(): String? = mAddress
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { mockDevicesDb.getCalibrationFactor(any()) } returns 1.0
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } returns true
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } returns Unit
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } returns Unit

        mockContext = mockk(relaxed = true)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { mockContext.mainLooper } returns mockLooper

        mockSensorManager = mockk(relaxed = true)
        mockGatt = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-026.1: Verify shutDown() terminates mScheduler and cancels the calculator handle,
     * preventing thread pool worker leaks.
     */
    @Test
    fun testShutDown_cancelsHandleAndShutsDownScheduler() {
        val device = TestBTLERunSpeedDevice(mockContext, mockSensorManager, 301L, "AA:BB:CC:DD:EE:01")
        val scheduler = device.schedulerForTesting
        val handle = device.distanceCalculatorHandleForTesting

        assertFalse("Scheduler must initially be active", scheduler.isShutdown)
        assertFalse("Calculator handle must initially be active", handle.isCancelled)

        device.shutDown()

        assertTrue("Scheduler must be shut down on shutDown()", scheduler.isShutdown)
        assertTrue("Calculator handle must be cancelled on shutDown()", handle.isCancelled)
    }

    /**
     * TST-CON-026.2: Verify distanceCalculator suppresses distance accumulation when no fresh
     * packets have arrived within the 5000ms threshold, and accumulates when packets are fresh.
     */
    @Test
    fun testDistanceCalculator_packetTimeoutGating_suppressesStaleSpeedAccumulation() {
        val device = TestBTLERunSpeedDevice(mockContext, mockSensorManager, 302L, "AA:BB:CC:DD:EE:02")
        try {
            device.isDistancePresentForTesting = false
            device.speedForTesting = 3.5

            // Case A: Packet timestamp is 6000ms ago (> 5000ms timeout)
            device.lastMeasurementTimestampForTesting = System.currentTimeMillis() - 6000L
            device.distanceCalculatorRunnableForTesting.run()

            assertEquals("Stale speed must not accumulate into distance", 0.0, device.distanceForTesting, 0.001)

            // Case B: Packet timestamp is 1000ms ago (fresh)
            device.lastMeasurementTimestampForTesting = System.currentTimeMillis() - 1000L
            device.distanceCalculatorRunnableForTesting.run()

            assertEquals("Fresh speed must accumulate into distance", 3.5, device.distanceForTesting, 0.001)

            // Case C: Subsequent execution when stale again (> 5000ms ago)
            device.lastMeasurementTimestampForTesting = System.currentTimeMillis() - 5500L
            device.distanceCalculatorRunnableForTesting.run()

            assertEquals("Distance must remain unchanged when stale", 3.5, device.distanceForTesting, 0.001)
        } finally {
            device.shutDown()
        }
    }

    /**
     * TST-CON-026.3: Verify unregisterSensors() resets mSpeed to 0.0 and clears the timestamp.
     */
    @Test
    fun testUnregisterSensors_resetsSpeedAndTimestamp() {
        val device = TestBTLERunSpeedDevice(mockContext, mockSensorManager, 303L, "AA:BB:CC:DD:EE:03")
        try {
            device.speedForTesting = 4.2
            device.lastMeasurementTimestampForTesting = System.currentTimeMillis()

            device.unregisterSensors()

            assertEquals("mSpeed must be reset to 0.0 on unregisterSensors()", 0.0, device.speedForTesting, 0.0)
            assertEquals("mLastMeasurementTimestamp must be reset to 0L", 0L, device.lastMeasurementTimestampForTesting)
        } finally {
            device.shutDown()
        }
    }

    /**
     * TST-CON-026.4: Verify BTLERunSpeedDevice does not declare duplicate shadow fields for
     * mAddress or mTotalDistance, cleanly inheriting state from MyBTLEDevice.
     */
    @Test
    fun testFieldShadowing_noShadowAddressOrTotalDistance() {
        val declaredFieldNames = BTLERunSpeedDevice::class.java.declaredFields.map { it.name }.toSet()

        assertFalse("BTLERunSpeedDevice must not shadow mAddress", declaredFieldNames.contains("mAddress"))
        assertFalse("BTLERunSpeedDevice must not declare mTotalDistance", declaredFieldNames.contains("mTotalDistance"))

        val device = TestBTLERunSpeedDevice(mockContext, mockSensorManager, 304L, "AA:BB:CC:DD:EE:04")
        try {
            assertEquals("AA:BB:CC:DD:EE:04", device.getDeviceAddress())
        } finally {
            device.shutDown()
        }
    }

    /**
     * TST-CON-026.2: Verify measurementCharacteristicUpdate parses speed and records packet timestamp.
     */
    @Test
    fun testMeasurementCharacteristicUpdate_recordsTimestampAndSpeed() {
        val device = TestBTLERunSpeedDevice(mockContext, mockSensorManager, 305L, "AA:BB:CC:DD:EE:05")
        try {
            val mockChar = mockk<BluetoothGattCharacteristic>()
            val payload = byteArrayOf(0x00, 0x00, 0x02, 0x50) // flag=0, speedInt=512 (2.0 m/s), cadence=80
            every { mockChar.value } returns payload
            every { mockChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) } returns 0
            every { mockChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1) } returns 512
            every { mockChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3) } returns 80

            val before = System.currentTimeMillis()
            device.measurementCharacteristicUpdate(mockGatt, mockChar)
            val after = System.currentTimeMillis()

            assertEquals(2.0, device.speedForTesting, 0.01)
            assertTrue("Timestamp must be recorded", device.lastMeasurementTimestampForTesting in before..after)
        } finally {
            device.shutDown()
        }
    }
}
