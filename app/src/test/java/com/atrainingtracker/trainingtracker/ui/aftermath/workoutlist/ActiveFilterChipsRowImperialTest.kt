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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterFormattingUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying [ActiveFilterChipsRow] label generation under Imperial and Metric units
 * pursuant to REQ-UI-508.2 / TST-UI-508.2.
 */
class ActiveFilterChipsRowImperialTest {

    @Test
    fun testActiveFilterChipsRow_distanceFormatting_imperialMode() {
        // Given 10 miles active filter (AC-1)
        val criteriaTenMiles = WorkoutFilterCriteria(
            minDistanceMeters = 10.0 * BANALService.METER_PER_MILE
        )
        assertTrue(criteriaTenMiles.minDistanceMeters != null)
        val labelMin = FilterFormattingUtils.formatDistanceFilterString(
            minMeters = criteriaTenMiles.minDistanceMeters,
            maxMeters = null,
            isImperial = true
        )
        assertEquals("> 10 mi", labelMin)

        // Given 10 to 25 miles active filter
        val labelInterval = FilterFormattingUtils.formatDistanceFilterString(
            minMeters = 10.0 * BANALService.METER_PER_MILE,
            maxMeters = 25.0 * BANALService.METER_PER_MILE,
            isImperial = true
        )
        assertEquals("10 - 25 mi", labelInterval)
    }

    @Test
    fun testActiveFilterChipsRow_elevationFormatting_imperialMode() {
        // Given 1,000 feet active filter (AC-2)
        val criteriaThousandFeet = WorkoutFilterCriteria(
            minElevationGainMeters = 1000.0 * BANALService.METER_PER_FOOT
        )
        assertTrue(criteriaThousandFeet.minElevationGainMeters != null)
        val labelMin = FilterFormattingUtils.formatElevationFilterString(
            minMeters = criteriaThousandFeet.minElevationGainMeters,
            maxMeters = null,
            isImperial = true
        )
        assertEquals("> 1000 ft", labelMin)

        // Given 500 to 1,000 feet active filter
        val labelInterval = FilterFormattingUtils.formatElevationFilterString(
            minMeters = 500.0 * BANALService.METER_PER_FOOT,
            maxMeters = 1000.0 * BANALService.METER_PER_FOOT,
            isImperial = true
        )
        assertEquals("500 - 1000 ft", labelInterval)
    }

    @Test
    fun testActiveFilterChipsRow_formatting_metricMode() {
        // Given 10 to 25 km active filter
        val labelDist = FilterFormattingUtils.formatDistanceFilterString(
            minMeters = 10000.0,
            maxMeters = 25000.0,
            isImperial = false
        )
        assertEquals("10 - 25 km", labelDist)

        // Given 100 to 250 m elevation filter
        val labelElev = FilterFormattingUtils.formatElevationFilterString(
            minMeters = 100.0,
            maxMeters = 250.0,
            isImperial = false
        )
        assertEquals("100 - 250 m", labelElev)
    }
}
