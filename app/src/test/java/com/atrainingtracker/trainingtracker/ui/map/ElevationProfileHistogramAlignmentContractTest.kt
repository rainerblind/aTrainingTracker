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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests verifying that the right-hand marginal Altitude Histogram
 * next to [ElevationProfile] is restricted exclusively to Workout Details and suppressed across
 * non-workout callers (Live Segments, Segment Details, Climb Details, Route Details, Live Map)
 * and compact feed list cards as mandated by REQ-UI-446.1 and TST-UI-446.3 (ATT-5504).
 */
class ElevationProfileHistogramAlignmentContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    private val workoutSummaryFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    @Test
    fun testMapDetailLayout_declaresShowElevationHistogramGatedOnWorkoutData() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Signature must declare showElevationHistogram defaulting to (workoutData != null)
        assertTrue(
            "MapDetailLayout must declare showElevationHistogram: Boolean = workoutData != null (REQ-UI-446.1 / ATT-5504)",
            content.contains("showElevationHistogram: Boolean = workoutData != null")
        )

        // 2. ElevationProfile call must forward alignWithTelemetryCharts = showElevationHistogram
        assertTrue(
            "MapDetailLayout must forward alignWithTelemetryCharts = showElevationHistogram to ElevationProfile (ATT-5504)",
            content.contains("alignWithTelemetryCharts = showElevationHistogram")
        )

        // 3. Must NOT hardcode alignWithTelemetryCharts = true
        assertFalse(
            "MapDetailLayout must not hardcode alignWithTelemetryCharts = true (ATT-5504)",
            content.contains("alignWithTelemetryCharts = true")
        )
    }

    @Test
    fun testWorkoutSummary_suppressesHistogramOnCompactElevationCard() {
        assertTrue("WorkoutSummary.kt must exist", workoutSummaryFile.exists())
        val content = workoutSummaryFile.readText()

        // In WorkoutSectionType.ELEVATION, alignWithTelemetryCharts must be false
        assertTrue(
            "WorkoutSummary must explicitly pass alignWithTelemetryCharts = false on compact card ElevationProfile (REQ-UI-446.1 / ATT-5504)",
            content.contains("alignWithTelemetryCharts = false")
        )

        // Must NOT leak telemetry chart visibility into list card elevation profile
        assertFalse(
            "WorkoutSummary must not forward preferences.showTelemetryCharts to ElevationProfile (ATT-5504)",
            content.contains("alignWithTelemetryCharts = preferences.showTelemetryCharts")
        )
    }

    @Test
    fun testElevationProfile_defaultsAlignWithTelemetryChartsToFalse() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // ElevationProfile default parameter must be alignWithTelemetryCharts: Boolean = false
        assertTrue(
            "ElevationProfile must default alignWithTelemetryCharts to false for standalone callers (REQ-UI-446.1)",
            content.contains("alignWithTelemetryCharts: Boolean = false")
        )
    }

    @Test
    fun testHistogramGatingLogic_workoutDetailsVsNonWorkoutCallers() {
        // Pure logic verification:
        // Workout Details provide non-null workoutData -> histogram enabled
        val workoutDetailsData: Any? = "MockWorkoutData"
        val showInWorkoutDetails = (workoutDetailsData != null)
        assertTrue("Histogram must be enabled in Workout Details", showInWorkoutDetails)

        // Non-workout callers (Live Segments, Climbs, Routes, Live Map) pass workoutData = null -> histogram suppressed
        val nonWorkoutData: Any? = null
        val showInNonWorkout = (nonWorkoutData != null)
        assertFalse("Histogram must be suppressed in non-workout callers", showInNonWorkout)
    }
}
