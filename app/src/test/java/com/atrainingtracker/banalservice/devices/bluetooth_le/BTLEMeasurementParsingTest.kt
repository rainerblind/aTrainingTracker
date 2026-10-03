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
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit test suite verifying BLE GATT measurement characteristic parsing resilience,
 * bounds checking, null-safe unboxing, and crash immunity (REQ-CON-014, TST-CON-005, ATT-1485).
 */
class BTLEMeasurementParsingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockGatt: BluetoothGatt

    private class TestBikePowerDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEBikePowerDevice(context, sensorManager, deviceId, address) {
        public override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.measurementCharacteristicUpdate(gatt, characteristic)
        }

        fun getPowerSensor() = mPowerSensor
        fun getCadenceSensor() = mCadenceSensor
        fun getSpeedSensor() = mSpeedSensor
        fun getDistanceSensor() = mDistanceSensor
        fun getPowerBalanceSensor() = mPowerBalanceSensor
    }

    private class TestBikeSpeedAndCadenceDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEBikeSpeedAndCadenceDevice(context, sensorManager, deviceId, address) {
        public override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.measurementCharacteristicUpdate(gatt, characteristic)
        }

        fun getCadenceSensor() = mCadenceSensor
        fun getSpeedSensor() = mSpeedSensor
        fun getDistanceSensor() = mDistanceSensor
    }

    private class TestRunSpeedDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLERunSpeedDevice(context, sensorManager, deviceId, address) {
        public override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.measurementCharacteristicUpdate(gatt, characteristic)
        }

        fun getCadenceSensor() = mCadenceSensor
        fun getSpeedSensor() = mSpeedSensor
        fun getDistanceSensor() = mDistanceSensor
    }

    private class TestHeartRateDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        address: String
    ) : BTLEHeartRateDevice(context, sensorManager, deviceId, address) {
        public override fun measurementCharacteristicUpdate(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.measurementCharacteristicUpdate(gatt, characteristic)
        }

        fun getHeartRateSensor() = mHeartRateSensor
    }

    @Before
    fun setUp() {
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
        // Return flags supporting Power Balance (1), Wheel Revolution (2), and Crank Revolution (4) = 7
        every { mockDevicesDbManager.getBikePowerSensorFlags(any()) } returns 7
        every { mockDevicesDbManager.getCalibrationFactor(any()) } returns 2.096
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers { true }
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } answers { }
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } answers { }

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

    private fun createMockCharacteristic(bytes: ByteArray?): BluetoothGattCharacteristic {
        val char = mockk<BluetoothGattCharacteristic>(relaxed = true)
        every { char.value } returns bytes
        every { char.getIntValue(any(), any()) } answers {
            val format = firstArg<Int>()
            val offset = secondArg<Int>()
            if (bytes == null) null
            else {
                val typeLen = when (format) {
                    BluetoothGattCharacteristic.FORMAT_UINT8, BluetoothGattCharacteristic.FORMAT_SINT8 -> 1
                    BluetoothGattCharacteristic.FORMAT_UINT16, BluetoothGattCharacteristic.FORMAT_SINT16 -> 2
                    BluetoothGattCharacteristic.FORMAT_UINT32, BluetoothGattCharacteristic.FORMAT_SINT32 -> 4
                    else -> 1
                }
                if (offset + typeLen > bytes.size) null
                else {
                    when (format) {
                        BluetoothGattCharacteristic.FORMAT_UINT8 -> bytes[offset].toInt() and 0xFF
                        BluetoothGattCharacteristic.FORMAT_SINT8 -> bytes[offset].toInt()
                        BluetoothGattCharacteristic.FORMAT_UINT16 -> {
                            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                        }
                        BluetoothGattCharacteristic.FORMAT_SINT16 -> {
                            val unsigned = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
                            if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
                        }
                        BluetoothGattCharacteristic.FORMAT_UINT32, BluetoothGattCharacteristic.FORMAT_SINT32 -> {
                            (bytes[offset].toInt() and 0xFF) or
                                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                        }
                        else -> null
                    }
                }
            }
        }
        return char
    }

    /**
     * TST-CON-005.1: Reproduction of production crash ATT-1485.
     * Verify that when a Cycling Power packet has flags declaring Crank Revolution Data present (0x0020),
     * but the payload is truncated (missing the 2-byte crankEventTime), BTLEBikePowerDevice parses
     * instantaneous power cleanly, discards the truncated crank data, and throws ZERO exceptions.
     */
    @Test
    fun testBikePower_truncatedCrankData_doesNotCrashAndDiscardsGracefully() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 101L, "AA:BB:CC:DD:EE:01")

        // Flags = 0x0020 (Crank Revolution Data Present)
        // Power = 200 W (0x00C8)
        // Truncated Crank data: only 2 bytes for cumulative revs (0x000A), missing 2 bytes for timestamp
        // Total bytes = 6 bytes (should be 8 bytes)
        val truncatedBytes = byteArrayOf(
            0x20.toByte(), 0x00.toByte(), // Flags: UINT16 = 0x0020
            0xC8.toByte(), 0x00.toByte(), // Power: SINT16 = 200
            0x0A.toByte(), 0x00.toByte()  // Crank revs: UINT16 = 10, but NO timestamp follows!
        )
        val characteristic = createMockCharacteristic(truncatedBytes)

        device.measurementCharacteristicUpdate(mockGatt, characteristic)

        // Instantaneous power parsed cleanly
        assertEquals(200, device.getPowerSensor().value)
        // Cadence sensor was not corrupted with invalid data
        assertNull(device.getCadenceSensor().value)
    }

    /**
     * TST-CON-005.2: Verify truncated Wheel Revolution Data handling.
     * Flags declare wheel data present (0x0010), but payload has only 4 bytes (needs 4 + 6 = 10 bytes).
     */
    @Test
    fun testBikePower_truncatedWheelData_doesNotCrash() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 102L, "AA:BB:CC:DD:EE:02")

        val bytes = byteArrayOf(
            0x10.toByte(), 0x00.toByte(), // Flags: 0x0010 (Wheel Data Present)
            0x96.toByte(), 0x00.toByte()  // Power: 150 W, but 0 bytes of wheel data follow
        )
        val characteristic = createMockCharacteristic(bytes)

        device.measurementCharacteristicUpdate(mockGatt, characteristic)

        assertEquals(150, device.getPowerSensor().value)
        assertNull(device.getSpeedSensor().value)
    }

    /**
     * TST-CON-005.3: Verify truncated Pedal Power Balance handling.
     * Flags declare balance present (0x0001), but payload has only 4 bytes (needs 4 + 1 = 5 bytes).
     */
    @Test
    fun testBikePower_truncatedPowerBalance_doesNotCrash() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 103L, "AA:BB:CC:DD:EE:03")

        val bytes = byteArrayOf(
            0x01.toByte(), 0x00.toByte(), // Flags: 0x0001 (Power Balance Present)
            0xDC.toByte(), 0x00.toByte()  // Power: 220 W, but missing 1-byte balance
        )
        val characteristic = createMockCharacteristic(bytes)

        device.measurementCharacteristicUpdate(mockGatt, characteristic)

        assertEquals(220, device.getPowerSensor().value)
        assertNull(device.getPowerBalanceSensor().value)
    }

    /**
     * TST-CON-005.4: Verify empty, 1-byte, 2-byte, 3-byte, and null characteristic values.
     */
    @Test
    fun testBikePower_emptyOrTruncatedHeader_doesNotCrash() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 104L, "AA:BB:CC:DD:EE:04")

        // Null value
        val nullChar = createMockCharacteristic(null)
        device.measurementCharacteristicUpdate(mockGatt, nullChar)
        assertNull(device.getPowerSensor().value)

        // 0 bytes
        val emptyChar = createMockCharacteristic(ByteArray(0))
        device.measurementCharacteristicUpdate(mockGatt, emptyChar)
        assertNull(device.getPowerSensor().value)

        // 1 byte
        val oneByteChar = createMockCharacteristic(byteArrayOf(0x01))
        device.measurementCharacteristicUpdate(mockGatt, oneByteChar)
        assertNull(device.getPowerSensor().value)

        // 2 bytes
        val twoBytesChar = createMockCharacteristic(byteArrayOf(0x00, 0x00))
        device.measurementCharacteristicUpdate(mockGatt, twoBytesChar)
        assertNull(device.getPowerSensor().value)

        // 3 bytes
        val threeBytesChar = createMockCharacteristic(byteArrayOf(0x00, 0x00, 0x50))
        device.measurementCharacteristicUpdate(mockGatt, threeBytesChar)
        assertNull(device.getPowerSensor().value)
    }

    /**
     * TST-CON-005.5: Verify specification conformance and mathematical fidelity on full valid packets.
     */
    @Test
    fun testBikePower_validFullPacket_parsesAllSensorsCorrectly() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 105L, "AA:BB:CC:DD:EE:05")

        // Packet 1:
        // Flags: 0x0031 (Power Balance + Wheel Data + Crank Data)
        // Power: 250 W (0x00FA)
        // Balance: 100 (50.0%)
        // Wheel: 1000 revs (0x000003E8), event time 2048 (0x0800)
        // Crank: 500 revs (0x01F4), event time 1024 (0x0400)
        val packet1 = byteArrayOf(
            0x31.toByte(), 0x00.toByte(), // Flags (2)
            0xFA.toByte(), 0x00.toByte(), // Power: 250 (2)
            0x64.toByte(),                // Balance: 100 (1)
            0xE8.toByte(), 0x03.toByte(), 0x00.toByte(), 0x00.toByte(), // Wheel revs: 1000 (4)
            0x00.toByte(), 0x08.toByte(), // Wheel time: 2048 (2)
            0xF4.toByte(), 0x01.toByte(), // Crank revs: 500 (2)
            0x00.toByte(), 0x04.toByte()  // Crank time: 1024 (2)
        )
        val char1 = createMockCharacteristic(packet1)
        device.measurementCharacteristicUpdate(mockGatt, char1)

        assertEquals(250, device.getPowerSensor().value)
        assertEquals(50.0, device.getPowerBalanceSensor().value, 0.001)

        // Packet 2: advancing revs and time
        // Wheel: 1010 revs (+10 revs), event time 4096 (+2048) -> speed = mCalibrationFactor * 10 * 2048 / 2048
        // Crank: 520 revs (+20 revs), event time 2048 (+1024) -> cadence = 60 * 20 * 1024 / 1024 = 1200 rpm
        val packet2 = byteArrayOf(
            0x31.toByte(), 0x00.toByte(),
            0x2C.toByte(), 0x01.toByte(), // Power: 300
            0x5A.toByte(),                // Balance: 90 -> 45.0%
            0xF2.toByte(), 0x03.toByte(), 0x00.toByte(), 0x00.toByte(), // Wheel revs: 1010
            0x00.toByte(), 0x10.toByte(), // Wheel time: 4096
            0x08.toByte(), 0x02.toByte(), // Crank revs: 520
            0x00.toByte(), 0x08.toByte()  // Crank time: 2048
        )
        val char2 = createMockCharacteristic(packet2)
        device.measurementCharacteristicUpdate(mockGatt, char2)

        assertEquals(300, device.getPowerSensor().value)
        assertEquals(45.0, device.getPowerBalanceSensor().value, 0.001)
        assertNotNull(device.getSpeedSensor().value)
        assertTrue(device.getSpeedSensor().value > 0.0)
        assertNotNull(device.getCadenceSensor().value)
        assertEquals(1200.0, device.getCadenceSensor().value, 0.001)
    }

    /**
     * TST-CON-005.6: BTLEBikeDevice parity test with truncated wheel and crank data.
     */
    @Test
    fun testBikeDevice_truncatedPackets_doesNotCrash() {
        val device = TestBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 106L, "AA:BB:CC:DD:EE:06")

        // 0 bytes
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(ByteArray(0)))

        // Flags = 0x03 (wheel + crank present), but only 4 bytes total
        val truncated = byteArrayOf(
            0x03.toByte(), // Flag: wheel + crank present
            0x0A.toByte(), 0x00.toByte(), 0x00.toByte() // 3 bytes instead of 7 for wheel
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(truncated))

        // No exceptions thrown
        assertNull(device.getSpeedSensor().value)
        assertNull(device.getCadenceSensor().value)
    }

    /**
     * TST-CON-005.7: BTLERunSpeedDevice parity test with truncated stride and distance data.
     */
    @Test
    fun testRunSpeedDevice_truncatedPackets_doesNotCrash() {
        val device = TestRunSpeedDevice(mockContext, mockSensorManager, 107L, "AA:BB:CC:DD:EE:07")

        // Less than 4 bytes
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(byteArrayOf(0x01, 0x02)))

        // Distance present (bit 1) with stride present (bit 0), but length only 6 (missing 4 bytes of distance at offset 6)
        val truncatedDistance = byteArrayOf(
            0x03.toByte(),               // Flag: stride present (0x01) + distance present (0x02)
            0x00.toByte(), 0x04.toByte(), // Speed: 1024 -> 4.0 m/s
            0x50.toByte(),               // Cadence: 80
            0x5A.toByte(), 0x00.toByte()  // Stride: 90 cm, but NO 4 bytes distance at offset 6!
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(truncatedDistance))

        // Speed and cadence parse cleanly
        assertNotNull(device.getSpeedSensor().value)
        assertEquals(80, device.getCadenceSensor().value)
    }

    /**
     * TST-CON-005.8: BTLEHeartRateDevice parity test with truncated packets.
     */
    @Test
    fun testHeartRateDevice_truncatedPackets_doesNotCrash() {
        val device = TestHeartRateDevice(mockContext, mockSensorManager, 108L, "AA:BB:CC:DD:EE:08")

        // Null value
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(null))
        assertNull(device.getHeartRateSensor().value)

        // 1 byte
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(byteArrayOf(0x00)))
        assertNull(device.getHeartRateSensor().value)

        // Flag = 0x01 (UINT16 format), but only 2 bytes total (needs 3 bytes)
        val truncatedUint16 = byteArrayOf(0x01.toByte(), 0x78.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(truncatedUint16))
        assertNull(device.getHeartRateSensor().value)

        // Valid UINT8 HR (2 bytes: flag 0x00, HR 140)
        val validUint8 = byteArrayOf(0x00.toByte(), 140.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(validUint8))
        assertEquals(140, device.getHeartRateSensor().value)
    }
}
