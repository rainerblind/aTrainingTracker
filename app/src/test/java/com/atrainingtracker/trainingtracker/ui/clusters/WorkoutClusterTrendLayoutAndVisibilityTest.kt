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

package com.atrainingtracker.trainingtracker.ui.clusters

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Layout, boundary visibility, and modularity contract tests for Workout Cluster Trend Card (REQ-SET-078 / TST-SET-078).
 */
class WorkoutClusterTrendLayoutAndVisibilityTest {

    private fun createWorkout(id: Long, activeTimeSec: Long, avgSpeedMps: Double): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = "Radfahren",
            bSportType = BSportType.BIKE,
            startTimeS = 1000L * id,
            formattedDate = "2026-10-19",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = avgSpeedMps * activeTimeSec,
            maxDisplacement = 0.0,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec,
            avgSpeedMps = avgSpeedMps,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath across candidates $candidates")
    }

    @Test
    fun testTrendSeriesEmptyForLessThanTwoWorkouts() {
        val zeroWorkouts = emptyList<WorkoutData>()
        val oneWorkout = listOf(createWorkout(1L, 1800L, 7.5))
        val oneValidOneCorrupt = listOf(
            createWorkout(1L, 1800L, 7.5),
            createWorkout(2L, 0L, 7.5) // non-positive duration
        )

        val series0 = WorkoutClusterTrendMath.buildTrendSeries(zeroWorkouts, BSportType.BIKE, ClusterTrendMetricMode.DURATION)
        val series1 = WorkoutClusterTrendMath.buildTrendSeries(oneWorkout, BSportType.BIKE, ClusterTrendMetricMode.DURATION)
        val seriesCorrupt = WorkoutClusterTrendMath.buildTrendSeries(oneValidOneCorrupt, BSportType.BIKE, ClusterTrendMetricMode.DURATION)

        assertTrue("Empty list must produce empty series", series0.isEmpty)
        assertTrue("Single workout list must produce empty series", series1.isEmpty)
        assertTrue("Single valid workout with corrupt second must produce empty series", seriesCorrupt.isEmpty)
    }

    @Test
    fun testWorkoutClusterTrendChartCardHiddenContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt")
        val content = file.readText()

        // Must guard against N < 2 inside composable
        assertTrue(
            "WorkoutClusterTrendChartCard must return early when points.size < 2",
            content.contains("if (trendSeries.points.size < 2)") && content.contains("return")
        )
    }

    @Test
    fun testWorkoutClusterDetailDashboardVisibilityGuardAndDivider() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt")
        val content = file.readText()

        // Must contain validTrendWorkoutsCount check before composing card
        assertTrue(
            "WorkoutClusterDetailDashboard must guard WorkoutClusterTrendChartCard with valid count >= 2",
            content.contains("validTrendWorkoutsCount >= 2")
        )

        // Must render HorizontalDivider for visual cadence
        assertTrue(
            "WorkoutClusterDetailDashboard must render HorizontalDivider after trend card",
            content.contains("HorizontalDivider(")
        )
    }

    @Test
    fun testModularityAndLineCount() {
        val mathFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMath.kt")
        val chartFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt")

        val mathLines = mathFile.readLines().size
        val chartLines = chartFile.readLines().size

        assertTrue("WorkoutClusterTrendMath.kt ($mathLines lines) must be strictly < 500 lines", mathLines < 500)
        assertTrue("WorkoutClusterTrendChartCard.kt ($chartLines lines) must be strictly < 500 lines", chartLines < 500)
    }
}
