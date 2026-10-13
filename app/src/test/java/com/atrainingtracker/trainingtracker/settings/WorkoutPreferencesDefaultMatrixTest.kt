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

package com.atrainingtracker.trainingtracker.settings

import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying default section visibility matrices across summary cards and details (REQ-UI-285, TST-UI-245).
 */
class WorkoutPreferencesDefaultMatrixTest {

    @Test
    fun testWorkoutCardSectionPreferences_defaultVisibility() {
        val cardPrefs = WorkoutCardSectionPreferences()

        // Visible in list card
        assertTrue("Description should be visible in list by default", cardPrefs.showDescription)
        assertTrue("Extrema should be visible in list by default", cardPrefs.showExtrema)
        assertTrue("Map preview should be visible in list by default", cardPrefs.showMapPreview)
        assertTrue("Zone analysis should be visible in list by default", cardPrefs.showZoneAnalysis)
        assertTrue("Export status should be visible in list by default", cardPrefs.showExportStatus)
        assertTrue("Strava should be visible in list by default", cardPrefs.showStrava)

        // Hidden in list card to prevent bloat and maintain smooth performance
        assertFalse("Elevation profile should be hidden in list by default", cardPrefs.showElevationProfile)
        assertFalse("Telemetry charts should be hidden in list by default", cardPrefs.showTelemetryCharts)
        assertFalse("Laps table should be hidden in list by default", cardPrefs.showLaps)

        assertEquals(LapDisplayMode.VISUALIZER_ONLY, cardPrefs.lapDisplayMode)
    }

    @Test
    fun testWorkoutDetailPreferences_defaultVisibility() {
        val detailPrefs = WorkoutDetailPreferences()

        // All 9 sections are active by default in full details screen
        assertTrue("Description should be visible in details", detailPrefs.showDescription)
        assertTrue("Extrema should be visible in details", detailPrefs.showExtrema)
        assertTrue("Map should be visible in details", detailPrefs.showMap)
        assertTrue("Elevation profile should be visible in details", detailPrefs.showElevationProfile)
        assertTrue("Telemetry charts should be visible in details", detailPrefs.showTelemetryCharts)
        assertTrue("Zone analysis should be visible in details", detailPrefs.showZoneAnalysis)
        assertTrue("Laps should be visible in details", detailPrefs.showLaps)
        assertTrue("Export status should be visible in details", detailPrefs.showExportStatus)
        assertTrue("Strava should be visible in details", detailPrefs.showStrava)
    }
}
