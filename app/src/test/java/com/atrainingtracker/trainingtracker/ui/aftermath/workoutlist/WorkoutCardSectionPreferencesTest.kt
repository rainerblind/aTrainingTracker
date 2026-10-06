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

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [WorkoutCardSectionPreferences] data contract, default states,
 * and immutability invariants under REQ-UI-210 and TST-UI-164.
 */
class WorkoutCardSectionPreferencesTest {

    @Test
    fun defaultPreferences_haveExpectedValues() {
        val prefs = WorkoutCardSectionPreferences()

        // High-density standard card defaults (REQ-UI-285)
        assertTrue("Description should be enabled by default", prefs.showDescription)
        assertTrue("Extrema should be enabled by default", prefs.showExtrema)
        assertFalse("Laps should be disabled by default for list cards", prefs.showLaps)
        assertTrue("Strava activity should be enabled by default", prefs.showStrava)
        assertTrue("Map preview should be enabled by default", prefs.showMapPreview)
        // Heavy analytical sections (including elevation profile) default to false for 60/120fps LazyColumn scroll performance (REQ-UI-240)
        assertFalse("Elevation profile should be disabled by default for list cards", prefs.showElevationProfile)
        assertFalse("Telemetry charts should be disabled by default", prefs.showTelemetryCharts)
        assertTrue("Zone analysis should be enabled by default", prefs.showZoneAnalysis)
        assertTrue("Export status should be enabled by default", prefs.showExportStatus)
    }

    @Test
    fun copyPreferences_modifiesOnlySpecifiedFields() {
        val original = WorkoutCardSectionPreferences()
        val modified = original.copy(
            showMapPreview = false,
            showTelemetryCharts = true,
            showLaps = true
        )

        assertTrue(modified.showDescription)
        assertTrue(modified.showExtrema)
        assertTrue(modified.showLaps)
        assertTrue(modified.showStrava)
        assertFalse(modified.showMapPreview)
        assertFalse(modified.showElevationProfile)
        assertTrue(modified.showTelemetryCharts)
        assertTrue(modified.showZoneAnalysis)
        assertTrue(modified.showExportStatus)
    }

    @Test
    fun customAllDisabledConfiguration_maintainsFidelity() {
        val allDisabled = WorkoutCardSectionPreferences(
            showDescription = false,
            showExtrema = false,
            showLaps = false,
            showStrava = false,
            showMapPreview = false,
            showElevationProfile = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false
        )

        assertFalse(allDisabled.showDescription)
        assertFalse(allDisabled.showExtrema)
        assertFalse(allDisabled.showLaps)
        assertFalse(allDisabled.showStrava)
        assertFalse(allDisabled.showMapPreview)
        assertFalse(allDisabled.showElevationProfile)
        assertFalse(allDisabled.showTelemetryCharts)
        assertFalse(allDisabled.showZoneAnalysis)
    }

    @Test
    fun equalityAndHashCode_workConsistently() {
        val prefs1 = WorkoutCardSectionPreferences(showTelemetryCharts = true)
        val prefs2 = WorkoutCardSectionPreferences(showTelemetryCharts = true)
        val prefs3 = WorkoutCardSectionPreferences(showTelemetryCharts = false)

        assertEquals(prefs1, prefs2)
        assertEquals(prefs1.hashCode(), prefs2.hashCode())
        assertFalse(prefs1 == prefs3)
    }
}
