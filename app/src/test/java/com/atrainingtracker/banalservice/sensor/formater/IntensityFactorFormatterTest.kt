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
import org.junit.Before
import org.junit.Test
import java.util.Locale

class IntensityFactorFormatterTest {

    private lateinit var formatter: IntensityFactorFormatter

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        formatter = IntensityFactorFormatter()
    }

    @Test
    fun testFormat_nullValue_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun testFormat_nanValue_returnsDashes() {
        assertEquals("--", formatter.format(Double.NaN))
    }

    @Test
    fun testFormat_infiniteValue_returnsDashes() {
        assertEquals("--", formatter.format(Double.POSITIVE_INFINITY))
        assertEquals("--", formatter.format(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testFormat_negativeValue_returnsDashes() {
        assertEquals("--", formatter.format(-0.01))
        assertEquals("--", formatter.format(-1.0))
    }

    @Test
    fun testFormat_validValues() {
        assertEquals("0.85", formatter.format(0.854))
        assertEquals("1.03", formatter.format(1.028))
        assertEquals("0.70", formatter.format(0.7))
        assertEquals("0.00", formatter.format(0.0))
    }
}
