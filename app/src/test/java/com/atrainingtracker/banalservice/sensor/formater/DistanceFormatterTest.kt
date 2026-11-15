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
 * Unit tests for [DistanceFormatter] (REQ-ARC-007, TST-ARC-007).
 */
class DistanceFormatterTest {

    private val formatter = DistanceFormatter()

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
    fun format_metric_distanceRanges() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)

        // Basic format (km, 2 decimals)
        val basic = formatter.format(5000.0)
        assertTrue(basic == "5.00" || basic == "5,00")

        // format_3 (km, 3 decimals)
        val format3 = formatter.format_3(5123.0)
        assertTrue(format3 == "5.123" || format3 == "5,123")

        // format_with_units < 1000m -> meters
        assertEquals("500 m", formatter.format_with_units(500))

        // 1km .. 100km -> 2 decimals km
        val km2 = formatter.format_with_units(5000)
        assertTrue(km2 == "5.00 km" || km2 == "5,00 km")

        // 100km .. 1000km -> 1 decimal km
        val km1 = formatter.format_with_units(150_000)
        assertTrue(km1 == "150.0 km" || km1 == "150,0 km")

        // >= 1000km -> 0 decimals km
        val km0 = formatter.format_with_units(2_000_000)
        assertTrue(km0.endsWith("km"))
    }

    @Test
    fun format_imperial_distanceRanges() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)

        val ft = formatter.format_with_units(500.0)
        assertTrue(ft.endsWith("ft"))

        val mile = formatter.format_with_units(5000.0)
        assertTrue(mile.endsWith("mile"))
    }
}
