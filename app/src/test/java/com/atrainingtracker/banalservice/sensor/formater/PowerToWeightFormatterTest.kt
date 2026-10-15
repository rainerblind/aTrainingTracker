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
 * Unit tests verifying power-to-weight ratio (W/kg) formatting conforming to REQ-TRK-019 and TST-TRK-011.
 */
class PowerToWeightFormatterTest {

    private val formatter = PowerToWeightFormatter()

    @Test
    fun testNull_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun testNaN_returnsDashes() {
        assertEquals("--", formatter.format(Double.NaN))
    }

    @Test
    fun testInfinite_returnsDashes() {
        assertEquals("--", formatter.format(Double.POSITIVE_INFINITY))
        assertEquals("--", formatter.format(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testNegative_returnsDashes() {
        assertEquals("--", formatter.format(-0.1))
        assertEquals("--", formatter.format(-5.0))
    }

    @Test
    fun testValidValues_formatsToOneDecimalPlace() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("4.0", formatter.format(4.0))
            assertEquals("3.8", formatter.format(3.849))
            assertEquals("0.0", formatter.format(0.0))
            assertEquals("5.3", formatter.format(5.26))
            assertEquals("4.0 W/kg", formatter.format_with_units(4.0))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
