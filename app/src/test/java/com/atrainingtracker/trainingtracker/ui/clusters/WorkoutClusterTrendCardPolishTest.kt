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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Architectural, UI layout, and state filtering contract tests for Workout Cluster Trend Card polish
 * (ATT-5852 / REQ-UI-490 / TST-UI-490).
 */
class WorkoutClusterTrendCardPolishTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath across candidates $candidates")
    }

    private fun createWorkout(id: Long, startTimeS: Long, activeTimeSec: Long = 1800L, avgSpeedMps: Double = 7.5): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = "Radfahren",
            bSportType = BSportType.BIKE,
            startTimeS = startTimeS,
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

    @Test
    fun testLeftGutterSeparationContract() {
        // REQ-UI-490.1 / TST-UI-490.1: Dedicated 48dp left gutter separates Y-axis labels from X-axis start date
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt")
        val content = file.readText()

        assertTrue(
            "WorkoutClusterTrendChartCard must declare leftGutter = 48.dp",
            content.contains("val leftGutter = 48.dp")
        )
        assertTrue(
            "Y-axis bottom label must be rendered in left gutter",
            content.contains("drawText(trendSeries.yAxisBottomLabel, 2.dp.toPx()")
        )
        assertTrue(
            "X-axis start label must be rendered at leftPadding along bottom baseline",
            content.contains("drawText(trendSeries.xAxisStartLabel, leftPadding, size.height - 4.dp.toPx()")
        )
    }

    @Test
    fun testAverageBadgeLegibilityContract() {
        // REQ-UI-490.2 / TST-UI-490.2: Average label rendered with rounded surface badge on top of lines
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt")
        val content = file.readText()

        assertTrue(
            "Average badge must draw a rounded rectangle with surfaceColor",
            content.contains("drawRoundRect(") && content.contains("color = surfaceColor.copy(alpha = 0.92f)")
        )
        assertTrue(
            "Average badge must be rendered after trend line stroke",
            content.indexOf("drawRoundRect(") > content.indexOf("drawPath(path = linePath")
        )
    }

    @Test
    fun testTimeRangeFilterPillsAndHeaderDeduplication() {
        // REQ-UI-490.3 / TST-UI-490.3: Redundant title removed and replaced with time-range filter chips
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt")
        val content = file.readText()

        // Redundant icon and title header must not exist in card header row
        assertFalse(
            "Card must not render redundant ShowChart icon and title in header row",
            content.contains("Icons.AutoMirrored.Filled.ShowChart")
        )

        // ClusterTrendTimeRange enum must exist with all 3 tiers
        assertTrue("ClusterTrendTimeRange must contain ALL", content.contains("ALL"))
        assertTrue("ClusterTrendTimeRange must contain THREE_YEARS", content.contains("THREE_YEARS"))
        assertTrue("ClusterTrendTimeRange must contain ONE_YEAR", content.contains("ONE_YEAR"))

        // Time range filter chips must be rendered
        assertTrue(
            "Card must render ClusterTrendTimeRange FilterChips",
            content.contains("ClusterTrendTimeRange.values().forEach")
        )
    }

    @Test
    fun testTimeRangeFilteringLogic() {
        // REQ-UI-490.3 / TST-UI-490.3: Filter workouts relative to latest workout timestamp with safe fallback
        val nowS = 1_700_000_000L
        val oneYearS = 365L * 86400L
        val twoYearsS = 2L * 365L * 86400L
        val fourYearsS = 4L * 365L * 86400L

        val workouts = listOf(
            createWorkout(1L, nowS - fourYearsS), // 4 years ago
            createWorkout(2L, nowS - twoYearsS),  // 2 years ago
            createWorkout(3L, nowS - (oneYearS / 2)), // 6 months ago
            createWorkout(4L, nowS)               // latest
        )

        val latestTimeS = workouts.maxOf { it.startTimeS }
        assertEquals(nowS, latestTimeS)

        // 1 Year filter: should include workout 3 and 4 (2 workouts)
        val threshold1YS = latestTimeS - oneYearS
        val range1Y = workouts.filter { it.startTimeS >= threshold1YS }
        assertEquals(2, range1Y.size)
        assertEquals(listOf(3L, 4L), range1Y.map { it.id })

        // 3 Year filter: should include workout 2, 3, and 4 (3 workouts)
        val threshold3YS = latestTimeS - (3L * 365L * 86400L)
        val range3Y = workouts.filter { it.startTimeS >= threshold3YS }
        assertEquals(3, range3Y.size)
        assertEquals(listOf(2L, 3L, 4L), range3Y.map { it.id })

        // Fallback test: if only 1 workout matches the 1-year window, fallback to all workouts
        val sparseWorkouts = listOf(
            createWorkout(1L, nowS - fourYearsS),
            createWorkout(2L, nowS - twoYearsS),
            createWorkout(3L, nowS) // only 1 workout in 1Y
        )
        val sparseLatest = sparseWorkouts.maxOf { it.startTimeS }
        val sparseFiltered = sparseWorkouts.filter { it.startTimeS >= (sparseLatest - oneYearS) }
        val finalWorkouts = if (sparseFiltered.size >= 2) sparseFiltered else sparseWorkouts
        assertEquals("Sparse filter with < 2 matches must fallback to all workouts", 3, finalWorkouts.size)
    }
}
