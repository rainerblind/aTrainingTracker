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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ZoneNumberParser], verifying locale-aware input parsing,
 * space stripping, decimal truncation, and null-safety.
 *
 * Covers [TST-UI-336.5].
 */
class ZoneNumberParserTest {

    @Test
    fun parseIntOrNull_standardDigits_parsesCorrectly() {
        assertEquals(250, ZoneNumberParser.parseIntOrNull("250"))
        assertEquals(50, ZoneNumberParser.parseIntOrNull("50"))
        assertEquals(600, ZoneNumberParser.parseIntOrNull("600"))
    }

    @Test
    fun parseIntOrNull_whitespaceAndNbsp_cleansAndParses() {
        assertEquals(250, ZoneNumberParser.parseIntOrNull(" 250 "))
        assertEquals(250, ZoneNumberParser.parseIntOrNull("\u00A0250\u00A0"))
        assertEquals(250, ZoneNumberParser.parseIntOrNull("2 5 0"))
    }

    @Test
    fun parseIntOrNull_decimalSeparators_truncatesToInteger() {
        assertEquals(250, ZoneNumberParser.parseIntOrNull("250,5"))
        assertEquals(250, ZoneNumberParser.parseIntOrNull("250.7"))
    }

    @Test
    fun parseIntOrNull_invalidStrings_returnsNullSafely() {
        assertNull(ZoneNumberParser.parseIntOrNull(""))
        assertNull(ZoneNumberParser.parseIntOrNull("   "))
        assertNull(ZoneNumberParser.parseIntOrNull("abc"))
        assertNull(ZoneNumberParser.parseIntOrNull("-"))
        assertNull(ZoneNumberParser.parseIntOrNull("!@#"))
    }
}
