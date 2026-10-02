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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test (TST-UI-199.2) verifying that [TrackOnMapScreen]
 * consumes [WorkoutDetailPreferences] to dynamically gate all 8 aftermath sections
 * in full alignment with REQ-UI-240 and ATT-2030.
 */
class TrackOnMapScreenDetailPreferencesContractTest {

    private fun findTrackOnMapScreenFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("TrackOnMapScreen.kt not found in candidates: $candidates")
    }

    @Test
    fun testTrackOnMapScreen_consumesWorkoutDetailPreferences() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        // 1. Parameter declaration
        assertTrue(
            "TrackOnMapScreen must declare detailPreferences parameter defaulting to null (REQ-UI-240)",
            content.contains("detailPreferences: WorkoutDetailPreferences? = null")
        )

        // 2. DataStore Flow observation
        assertTrue(
            "TrackOnMapScreen must observe workoutDetailPreferencesFlow from MyPreferenceManager",
            content.contains("workoutDetailPreferencesFlow.collectAsState(initial = WorkoutDetailPreferences())")
        )

        // 3. Fallback to activeDetailPrefs
        assertTrue(
            "TrackOnMapScreen must resolve activeDetailPrefs with fallback to persisted prefs",
            content.contains("val activeDetailPrefs = detailPreferences ?: persistedDetailPrefs")
        )

        // 4. Map & Elevation Profile Gating
        assertTrue(
            "TrackOnMapScreen must condition showMap on activeDetailPrefs.showMap",
            content.contains("showMap = showMap && hasGpsTrack && activeDetailPrefs.showMap")
        )
        assertTrue(
            "TrackOnMapScreen must condition showElevationProfile on activeDetailPrefs.showElevationProfile",
            content.contains("showElevationProfile = hasGpsTrack && activeDetailPrefs.showElevationProfile")
        )

        // 5. Telemetry Charts Gating
        assertTrue(
            "TrackOnMapScreen must pass showTelemetryCharts to MapDetailLayout",
            content.contains("showTelemetryCharts = activeDetailPrefs.showTelemetryCharts")
        )

        // 6. Analytics & Metadata Content Gating
        assertTrue(
            "TrackOnMapScreen must gate Zone Analysis on activeDetailPrefs.showZoneAnalysis",
            content.contains("if (activeDetailPrefs.showZoneAnalysis)")
        )
        assertTrue(
            "TrackOnMapScreen must gate Laps Split visualizer on activeDetailPrefs.showLaps",
            content.contains("if (activeDetailPrefs.showLaps)")
        )
        assertTrue(
            "TrackOnMapScreen must gate WorkoutDescription on activeDetailPrefs.showDescription",
            content.contains("if (activeDetailPrefs.showDescription)") &&
                    content.contains("WorkoutDescription(")
        )
        assertTrue(
            "TrackOnMapScreen must gate WorkoutExtrema on activeDetailPrefs.showExtrema",
            content.contains("if (activeDetailPrefs.showExtrema") &&
                    content.contains("WorkoutExtrema(")
        )
        assertTrue(
            "TrackOnMapScreen must gate StravaActivitySection on activeDetailPrefs.showStrava",
            content.contains("if (activeDetailPrefs.showStrava") &&
                    content.contains("StravaActivitySection(")
        )
    }
}
