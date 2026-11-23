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
import org.junit.Test

/**
 * Unit tests for [PaceFormatter] (REQ-ARC-007, TST-ARC-007).
 */
class PaceFormatterTest {

    private val formatter = PaceFormatter()

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    @Test
    fun format_null_returnsDashes() {
        assertEquals("--", formatter.format(null))
    }

    @Test
    fun format_exceedingMaxPace_returnsTildes() {
        assertEquals("~~", formatter.format(15.0))
    }

    @Test
    fun format_metric_formatsMinutesAndSeconds() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        // pace = 0.3 s/m -> 300 s/km = 5.0 min/km -> 5:00
        assertEquals("5:00", formatter.format(0.3))
        assertEquals("5:00 min/km", formatter.format_with_units(0.3))
    }

    @Test
    fun format_imperial_formatsMinutesAndSeconds() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        // pace_spm format_with_units includes min/mile
        val formatted = formatter.format_with_units(0.3)
        assert(formatted.endsWith("min/mile"))
    }

    @Test
    fun formatPaceMinutes_negativeAndExceedingMaxPace_matchFormatParity() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        assertEquals("--", formatter.formatPaceMinutes(null, MyUnits.METRIC))
        assertEquals("--", formatter.formatPaceMinutes(-1.5, MyUnits.METRIC))
        assertEquals("-- min/km", formatter.formatPaceMinutesWithUnits(-1.5, MyUnits.METRIC))
        assertEquals("--", formatter.formatPaceMinutes(Double.NaN, MyUnits.METRIC))

        // BANALService.MAX_PACE = 10.0 s/m -> 166.666... min/km; strictly greater returns "~~"
        assertEquals("~~", formatter.formatPaceMinutes(166.7, MyUnits.METRIC))
        assertEquals("~~ min/km", formatter.formatPaceMinutesWithUnits(166.7, MyUnits.METRIC))
        assertEquals("5:30 min/km", formatter.formatPaceMinutesWithUnits(5.5, MyUnits.METRIC))

        // Imperial MAX_PACE = 10.0 * 1609.344 / 60 = 268.224 min/mile
        assertEquals("200:00 min/mile", formatter.formatPaceMinutesWithUnits(200.0, MyUnits.IMPERIAL))
        assertEquals("~~", formatter.formatPaceMinutes(268.3, MyUnits.IMPERIAL))
        assertEquals("~~ min/mile", formatter.formatPaceMinutesWithUnits(268.3, MyUnits.IMPERIAL))
    }
}
