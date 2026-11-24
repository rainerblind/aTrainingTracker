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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le

import android.bluetooth.BluetoothGattCharacteristic
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Pure-JVM unit tests for [BleDisStringSanitizer] (`REQ-CON-060.1`, `TST-CON-060.1`, `ATT-8996`).
 */
class BleDisStringSanitizerTest {

    @Test
    fun testSanitize_stripsAllNulBytesAndTrimsWhitespace_TST_CON_060_1() {
        assertNull(BleDisStringSanitizer.sanitize(null))
        assertNull(BleDisStringSanitizer.sanitize(""))
        assertNull(BleDisStringSanitizer.sanitize("   \t\r\n  "))
        assertNull(BleDisStringSanitizer.sanitize("\u0000"))
        assertNull(BleDisStringSanitizer.sanitize("\u0000\u0000\u0000"))
        assertNull(BleDisStringSanitizer.sanitize(" \u0000 \t \u0000 "))

        assertEquals("Polar Electro Oy", BleDisStringSanitizer.sanitize("Polar Electro Oy\u0000"))
        assertEquals("Polar Electro Oy", BleDisStringSanitizer.sanitize("\u0000Polar Electro Oy\u0000\u0000"))
        assertEquals("PolarH10", BleDisStringSanitizer.sanitize("Polar\u0000H10\u0000"))
        assertEquals("Sense", BleDisStringSanitizer.sanitize("  Sense \u0000 "))
        assertEquals("Garmin", BleDisStringSanitizer.sanitize("Garmin"))
        assertEquals("Ümlaut 日本語", BleDisStringSanitizer.sanitize(" Ümlaut 日本語\u0000 "))
    }

    @Test
    fun testContainsNul_detectsNulBytesAccurately_TST_CON_060_1() {
        assertFalse(BleDisStringSanitizer.containsNul(null))
        assertFalse(BleDisStringSanitizer.containsNul(""))
        assertFalse(BleDisStringSanitizer.containsNul("Polar Electro Oy"))
        assertTrue(BleDisStringSanitizer.containsNul("\u0000"))
        assertTrue(BleDisStringSanitizer.containsNul("Polar Electro Oy\u0000"))
        assertTrue(BleDisStringSanitizer.containsNul("Polar\u0000H10"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testReadSanitizedString_handlesGetStringValueAndRawValueFallback_TST_CON_060_1() {
        assertNull(BleDisStringSanitizer.readSanitizedString(null))

        val charWithString = mockk<BluetoothGattCharacteristic>()
        every { charWithString.getStringValue(0) } returns "Polar Electro Oy\u0000"
        assertEquals("Polar Electro Oy", BleDisStringSanitizer.readSanitizedString(charWithString))

        val charNulOnly = mockk<BluetoothGattCharacteristic>()
        every { charNulOnly.getStringValue(0) } returns "\u0000\u0000"
        assertNull(BleDisStringSanitizer.readSanitizedString(charNulOnly))

        val charFallbackBytes = mockk<BluetoothGattCharacteristic>()
        every { charFallbackBytes.getStringValue(0) } returns null
        every { charFallbackBytes.value } returns "A1B2C3D4\u0000".toByteArray(StandardCharsets.UTF_8)
        assertEquals("A1B2C3D4", BleDisStringSanitizer.readSanitizedString(charFallbackBytes))

        val charBothNull = mockk<BluetoothGattCharacteristic>()
        every { charBothNull.getStringValue(0) } returns null
        every { charBothNull.value } returns null
        assertNull(BleDisStringSanitizer.readSanitizedString(charBothNull))
    }
}
