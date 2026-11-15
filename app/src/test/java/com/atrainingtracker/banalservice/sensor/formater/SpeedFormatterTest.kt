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
 * Unit tests for [SpeedFormatter] (REQ-ARC-007, TST-ARC-007).
 */
class SpeedFormatterTest {

    private val formatter = SpeedFormatter()

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun format_metric_formatsSpeedCorrectly() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        // 10 m/s = 36 km/h
        val result = formatter.format(10.0)
        assertTrue(result == "36.0" || result == "36,0")
        val withUnits = formatter.format_with_units(10.0)
        assertTrue(withUnits.endsWith("km/h"))
    }

    @Test
    fun format_imperial_formatsSpeedWithUnits() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val withUnits = formatter.format_with_units(10.0)
        assertTrue(withUnits.endsWith("mile/h"))
    }
}
