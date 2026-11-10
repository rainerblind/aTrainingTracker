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

package com.atrainingtracker.banalservice.sensor.formater

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [DfaA1Formatter] conforming to REQ-TRK-061.2 and TST-TRK-061.2.
 */
class DfaA1FormatterTest {

    private val formatter = DfaA1Formatter()

    @Test
    fun testFormat_validValues_formatsWithTwoDecimals() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("0.75", formatter.format(0.75))
            assertEquals("1.00", formatter.format(1.0))
            assertEquals("0.50", formatter.format(0.501))
            assertEquals("0.82", formatter.format(0.824))
            assertEquals("0.83", formatter.format(0.826))
            assertEquals("1.25", formatter.format(1.25))
            assertEquals("0.00", formatter.format(0.0))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testFormat_nullValue_returnsBlankDashes() {
        assertEquals("--.--", formatter.format(null))
    }

    @Test
    fun testFormat_negativeValue_returnsBlankDashes() {
        assertEquals("--.--", formatter.format(-0.1))
        assertEquals("--.--", formatter.format(-1.0))
    }

    @Test
    fun testFormat_nanOrInfinite_returnsBlankDashes() {
        assertEquals("--.--", formatter.format(Double.NaN))
        assertEquals("--.--", formatter.format(Double.POSITIVE_INFINITY))
        assertEquals("--.--", formatter.format(Double.NEGATIVE_INFINITY))
    }
}
