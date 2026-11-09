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
import android.content.Intent
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

        public override fun registerSensors() {
            super.registerSensors()
        }

        public override fun unregisterSensors() {
            super.unregisterSensors()
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

        public override fun registerSensors() {
            super.registerSensors()
        }

        public override fun unregisterSensors() {
            super.unregisterSensors()
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

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

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
        // Crank: 502 revs (+2 revs), event time 2048 (+1024) -> cadence = 60 * 2 * 1024 / 1024 = 120.0 rpm
        val packet2 = byteArrayOf(
            0x31.toByte(), 0x00.toByte(),
            0x2C.toByte(), 0x01.toByte(), // Power: 300
            0x5A.toByte(),                // Balance: 90 -> 45.0%
            0xF2.toByte(), 0x03.toByte(), 0x00.toByte(), 0x00.toByte(), // Wheel revs: 1010
            0x00.toByte(), 0x10.toByte(), // Wheel time: 4096
            0xF6.toByte(), 0x01.toByte(), // Crank revs: 502 (0x01F6)
            0x00.toByte(), 0x08.toByte()  // Crank time: 2048
        )
        val char2 = createMockCharacteristic(packet2)
        device.measurementCharacteristicUpdate(mockGatt, char2)

        assertEquals(300, device.getPowerSensor().value)
        assertEquals(45.0, device.getPowerBalanceSensor().value, 0.001)
        assertNotNull(device.getSpeedSensor().value)
        assertTrue(device.getSpeedSensor().value > 0.0)
        assertNotNull(device.getCadenceSensor().value)
        assertEquals(120.0, device.getCadenceSensor().value, 0.001)
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

    /**
     * TST-CON-022.1: BLE Pedal Power Balance Reference Flag Normalization.
     * Verify that when Bit 1 == 0 (Right reference), raw value scaled by 0.5% is emitted as Right pedal percentage.
     */
    @Test
    fun testBikePower_pedalPowerBalance_rightReference_parsedCleanly() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 109L, "AA:BB:CC:DD:EE:09")

        // Flags: 0x0001 (Bit 0 = 1 Balance Present, Bit 1 = 0 Right Reference)
        // Power: 250 W (0x00FA)
        // Balance raw: 104 (0x68) -> 52.0% Right
        val bytes = byteArrayOf(
            0x01.toByte(), 0x00.toByte(),
            0xFA.toByte(), 0x00.toByte(),
            104.toByte()
        )
        val characteristic = createMockCharacteristic(bytes)

        device.measurementCharacteristicUpdate(mockGatt, characteristic)

        assertEquals(250, device.getPowerSensor().value)
        assertEquals(52.0, device.getPowerBalanceSensor().value, 0.001)
    }

    /**
     * TST-CON-022.1: BLE Pedal Power Balance Reference Flag Normalization.
     * Verify that when Bit 1 == 1 (Left reference), raw value (48% Left) is automatically inverted
     * to Right pedal percentage (52% Right) pursuant to REQ-CON-022.1 and REQ-UI-377.1.
     */
    @Test
    fun testBikePower_pedalPowerBalance_leftReference_automaticallyInvertedToRight() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 110L, "AA:BB:CC:DD:EE:10")

        // Flags: 0x0003 (Bit 0 = 1 Balance Present, Bit 1 = 1 Left Reference)
        // Power: 250 W (0x00FA)
        // Balance raw: 96 (0x60) -> 48.0% Left -> inverted to 52.0% Right
        val bytes = byteArrayOf(
            0x03.toByte(), 0x00.toByte(),
            0xFA.toByte(), 0x00.toByte(),
            96.toByte()
        )
        val characteristic = createMockCharacteristic(bytes)

        device.measurementCharacteristicUpdate(mockGatt, characteristic)

        assertEquals(250, device.getPowerSensor().value)
        assertEquals(52.0, device.getPowerBalanceSensor().value, 0.001)
    }

    /**
     * TST-CON-022.1: BLE Pedal Power Balance boundary value verification.
     */
    @Test
    fun testBikePower_pedalPowerBalance_boundaryValues() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 111L, "AA:BB:CC:DD:EE:11")

        // 1. Bit 1 == 1 (Left reference), raw = 0 (0% Left -> 100% Right)
        val leftZeroBytes = byteArrayOf(0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(leftZeroBytes))
        assertEquals(100.0, device.getPowerBalanceSensor().value, 0.001)

        // 2. Bit 1 == 1 (Left reference), raw = 200 (100% Left -> 0% Right)
        val leftFullBytes = byteArrayOf(0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 200.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(leftFullBytes))
        assertEquals(0.0, device.getPowerBalanceSensor().value, 0.001)

        // 3. Bit 1 == 0 (Right reference), raw = 0 (0% Right)
        val rightZeroBytes = byteArrayOf(0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(rightZeroBytes))
        assertEquals(0.0, device.getPowerBalanceSensor().value, 0.001)

        // 4. Bit 1 == 0 (Right reference), raw = 200 (100% Right)
        val rightFullBytes = byteArrayOf(0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 200.toByte())
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(rightFullBytes))
        assertEquals(100.0, device.getPowerBalanceSensor().value, 0.001)
    }

    /**
     * TST-CON-024.1: BTLEBikeDevice 16-Bit Crank Rollover & Precision Verification.
     * Verifies that counter wrap from 65535 to 1 yields positive revDiff (2) and 120.0 rpm without negative spikes.
     */
    @Test
    fun testBikeDevice_crankRollover_calculates120RpmWithoutNegativeSpike() {
        val device = TestBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 112L, "AA:BB:CC:DD:EE:12")

        // Packet 1: Flags = 0x02 (Crank present), revs = 65535 (0xFFFF), time = 1000 (0x03E8)
        val packet1 = byteArrayOf(
            0x02.toByte(),
            0xFF.toByte(), 0xFF.toByte(), // revs: 65535
            0xE8.toByte(), 0x03.toByte()  // time: 1000
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(packet1))
        assertNull(device.getCadenceSensor().value)

        // Packet 2: revs = 1 (0x0001), time = 2024 (0x07E8 -> +1024 ticks)
        val packet2 = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), // revs: 1 (wrapped from 65535 -> revDiff = 2)
            0xE8.toByte(), 0x07.toByte()  // time: 2024 (+1024 ticks = 1.0 s)
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(packet2))

        assertNotNull(device.getCadenceSensor().value)
        assertEquals(120.0, device.getCadenceSensor().value, 0.001)
    }

    /**
     * TST-CON-024.2: BTLEBikePowerDevice 16-Bit Crank Rollover & Precision Verification.
     * Verifies that Cycling Power characteristic crank rollover from 65535 to 1 yields 120.0 rpm.
     */
    @Test
    fun testBikePower_crankRollover_calculates120RpmWithoutNegativeSpike() {
        val device = TestBikePowerDevice(mockContext, mockSensorManager, 113L, "AA:BB:CC:DD:EE:13")

        // Packet 1: Flags = 0x0020 (Crank present), Power = 200 W, revs = 65535, time = 1000
        val packet1 = byteArrayOf(
            0x20.toByte(), 0x00.toByte(),
            0xC8.toByte(), 0x00.toByte(), // Power: 200
            0xFF.toByte(), 0xFF.toByte(), // revs: 65535
            0xE8.toByte(), 0x03.toByte()  // time: 1000
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(packet1))
        assertNull(device.getCadenceSensor().value)

        // Packet 2: revs = 1, time = 2024 (+1024 ticks -> 120.0 rpm)
        val packet2 = byteArrayOf(
            0x20.toByte(), 0x00.toByte(),
            0xC8.toByte(), 0x00.toByte(), // Power: 200
            0x01.toByte(), 0x00.toByte(), // revs: 1
            0xE8.toByte(), 0x07.toByte()  // time: 2024
        )
        device.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(packet2))

        assertNotNull(device.getCadenceSensor().value)
        assertEquals(120.0, device.getCadenceSensor().value, 0.001)
    }

    /**
     * TST-CON-024.3: Cadence Plausibility Guard (> 250.0 rpm) Verification.
     * Verifies that samples with calculated cadence > 250.0 rpm are discarded in both CSC and CPS.
     */
    @Test
    fun testBikeDevice_and_BikePower_implausibleCadenceOver250Rpm_isDiscarded() {
        val cscDevice = TestBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 114L, "AA:BB:CC:DD:EE:14")
        val pwrDevice = TestBikePowerDevice(mockContext, mockSensorManager, 115L, "AA:BB:CC:DD:EE:15")

        // Baseline: revs = 100, time = 1000
        cscDevice.measurementCharacteristicUpdate(
            mockGatt,
            createMockCharacteristic(byteArrayOf(0x02, 0x64, 0x00, 0xE8.toByte(), 0x03))
        )
        pwrDevice.measurementCharacteristicUpdate(
            mockGatt,
            createMockCharacteristic(byteArrayOf(0x20, 0x00, 0xC8.toByte(), 0x00, 0x64, 0x00, 0xE8.toByte(), 0x03))
        )

        // Implausible packet: revs = 150 (+50 revs in 1024 ticks = 3000 rpm > 250 rpm)
        val cscOver = byteArrayOf(0x02, 0x96.toByte(), 0x00, 0xE8.toByte(), 0x07)
        val pwrOver = byteArrayOf(0x20, 0x00, 0xC8.toByte(), 0x00, 0x96.toByte(), 0x00, 0xE8.toByte(), 0x07)

        cscDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(cscOver))
        pwrDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(pwrOver))

        assertNull("CSC cadence > 250 rpm MUST be discarded", cscDevice.getCadenceSensor().value)
        assertNull("CPS cadence > 250 rpm MUST be discarded", pwrDevice.getCadenceSensor().value)
    }

    /**
     * TST-CON-024.4: Disconnect State Reset Verification.
     * Verifies that unregisterSensors() resets mLastCrankRevolutionsValid and mLastWheelRevolutionsValid.
     */
    @Test
    fun testBikeDevice_and_BikePower_disconnect_resetsRevolutionValidFlags() {
        val cscDevice = TestBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 116L, "AA:BB:CC:DD:EE:16")
        val pwrDevice = TestBikePowerDevice(mockContext, mockSensorManager, 117L, "AA:BB:CC:DD:EE:17")

        // Establish active state
        val p1 = byteArrayOf(0x02, 0x0A, 0x00, 0xE8.toByte(), 0x03)
        cscDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(p1))
        val p2 = byteArrayOf(0x02, 0x0C, 0x00, 0xE8.toByte(), 0x07)
        cscDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(p2))
        assertEquals(120.0, cscDevice.getCadenceSensor().value, 0.001)

        val pw1 = byteArrayOf(0x20, 0x00, 0xC8.toByte(), 0x00, 0x0A, 0x00, 0xE8.toByte(), 0x03)
        pwrDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(pw1))
        val pw2 = byteArrayOf(0x20, 0x00, 0xC8.toByte(), 0x00, 0x0C, 0x00, 0xE8.toByte(), 0x07)
        pwrDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(pw2))
        assertEquals(120.0, pwrDevice.getCadenceSensor().value, 0.001)

        // Trigger disconnect teardown (resets mLastCrankRevolutionsValid and deactivates sensors)
        cscDevice.unregisterSensors()
        pwrDevice.unregisterSensors()

        // Reconnect establishes session (reactivates sensors)
        cscDevice.registerSensors()
        pwrDevice.registerSensors()

        // After disconnect/reconnect, first packet MUST re-initialize baseline without emitting delta
        val reconnectPacket = byteArrayOf(0x02, 0x14, 0x00, 0xD0.toByte(), 0x07) // revs = 20, time = 2000
        cscDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(reconnectPacket))
        // Prior cadence was 120.0; reconnected baseline packet must NOT compute new cadence against pre-disconnect state
        assertEquals(120.0, cscDevice.getCadenceSensor().value, 0.001)

        val pwReconnect = byteArrayOf(0x20, 0x00, 0xC8.toByte(), 0x00, 0x14, 0x00, 0xD0.toByte(), 0x07)
        pwrDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(pwReconnect))
        assertEquals(120.0, pwrDevice.getCadenceSensor().value, 0.001)

        // Subsequent post-reconnect packet computes cadence accurately against new baseline (revs = 22, time = 3024 -> 120 rpm)
        val p4 = byteArrayOf(0x02, 0x16, 0x00, 0xD0.toByte(), 0x0B) // revs = 22, time = 3024
        cscDevice.measurementCharacteristicUpdate(mockGatt, createMockCharacteristic(p4))
        assertEquals(120.0, cscDevice.getCadenceSensor().value, 0.001)
    }
}

