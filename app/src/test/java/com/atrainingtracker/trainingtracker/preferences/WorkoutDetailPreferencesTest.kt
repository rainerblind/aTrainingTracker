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

package com.atrainingtracker.trainingtracker.preferences

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [WorkoutDetailPreferences] data contract, default states,
 * and immutability invariants under REQ-UI-240 and TST-UI-199.1.
 */
class WorkoutDetailPreferencesTest {

    @Test
    fun defaultPreferences_haveAllSectionsEnabled() {
        val prefs = WorkoutDetailPreferences()

        // Detail view defaults all analytical and metadata sections to ON
        assertTrue("Description should be enabled by default in details", prefs.showDescription)
        assertTrue("Extrema should be enabled by default in details", prefs.showExtrema)
        assertTrue("Laps should be enabled by default in details", prefs.showLaps)
        assertTrue("Strava activity should be enabled by default in details", prefs.showStrava)
        assertTrue("Map should be enabled by default in details", prefs.showMap)
        assertTrue("Elevation profile should be enabled by default in details", prefs.showElevationProfile)
        assertTrue("Telemetry charts should be enabled by default in details", prefs.showTelemetryCharts)
        assertTrue("Zone analysis should be enabled by default in details", prefs.showZoneAnalysis)
        assertTrue("Export status should be enabled by default in details", prefs.showExportStatus)
    }

    @Test
    fun listCardPreferences_defaultsElevationAndChartsToFalse() {
        val listPrefs = WorkoutCardSectionPreferences()

        // Contrast verification: list cards default heavy charts and laps to false (REQ-UI-285)
        assertTrue("Description should be enabled in list", listPrefs.showDescription)
        assertTrue("Extrema should be enabled in list", listPrefs.showExtrema)
        assertFalse("Laps should be disabled by default in list", listPrefs.showLaps)
        assertTrue("Strava should be enabled in list", listPrefs.showStrava)
        assertTrue("Map preview should be enabled in list", listPrefs.showMapPreview)
        assertFalse("Elevation profile should be disabled by default in list", listPrefs.showElevationProfile)
        assertFalse("Telemetry charts should be disabled by default in list", listPrefs.showTelemetryCharts)
        assertTrue("Zone analysis should be enabled by default in list", listPrefs.showZoneAnalysis)
        assertTrue("Export status should be enabled by default in list", listPrefs.showExportStatus)
    }

    @Test
    fun copyPreferences_modifiesOnlySpecifiedFields() {
        val original = WorkoutDetailPreferences()
        val modified = original.copy(
            showMap = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false
        )

        assertTrue(modified.showDescription)
        assertTrue(modified.showExtrema)
        assertTrue(modified.showLaps)
        assertTrue(modified.showStrava)
        assertFalse(modified.showMap)
        assertTrue(modified.showElevationProfile)
        assertFalse(modified.showTelemetryCharts)
        assertFalse(modified.showZoneAnalysis)
    }

    @Test
    fun equalityAndHashCode_workAsExpected() {
        val prefs1 = WorkoutDetailPreferences(showMap = false)
        val prefs2 = WorkoutDetailPreferences(showMap = false)
        val prefs3 = WorkoutDetailPreferences(showMap = true)

        assertEquals("Equal objects must have equal values", prefs1, prefs2)
        assertEquals("Equal objects must have equal hashCodes", prefs1.hashCode(), prefs2.hashCode())
        assertFalse("Different objects must not be equal", prefs1 == prefs3)
    }
}
