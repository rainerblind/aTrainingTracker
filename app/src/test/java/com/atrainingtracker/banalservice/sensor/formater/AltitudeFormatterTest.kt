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

import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AltitudeFormatter] (REQ-ARC-007, TST-ARC-007).
 */
class AltitudeFormatterTest {

    private val formatter = AltitudeFormatter()

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
        assertEquals("--", formatter.format_with_units(null))
        assertEquals("--", formatter.format_3(null))
    }

    @Test
    fun format_metric_formatsCorrectly() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        assertEquals("500", formatter.format(500.0))
        val format3 = formatter.format_3(500.123)
        assertTrue(format3 == "500.123" || format3 == "500,123")
        val withUnits = formatter.format_with_units(500.0)
        assertTrue(withUnits.contains("500") && withUnits.endsWith("m"))
    }

    @Test
    fun format_imperial_formatsCorrectly() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val withUnits = formatter.format_with_units(500.0)
        assertTrue(withUnits.endsWith("ft"))
    }
}
