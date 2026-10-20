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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying pure math, sorting, inversion, and hit detection for WorkoutClusterTrendMath (REQ-SET-078 / TST-SET-078).
 */
class WorkoutClusterTrendMathTest {

    private fun createWorkout(
        id: Long,
        startTimeS: Long,
        activeTimeSec: Long,
        avgSpeedMps: Double,
        sportType: BSportType = BSportType.BIKE
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = if (sportType == BSportType.RUN) "Laufen" else "Radfahren",
            bSportType = sportType,
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
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testChronologicalSortingAndCleansing() {
        val w1 = createWorkout(id = 1L, startTimeS = 3000L, activeTimeSec = 1800L, avgSpeedMps = 7.5)
        val w2 = createWorkout(id = 2L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 6.8)
        val w3 = createWorkout(id = 3L, startTimeS = 2000L, activeTimeSec = 1900L, avgSpeedMps = 7.1)
        val wCorruptDuration = createWorkout(id = 4L, startTimeS = 500L, activeTimeSec = 0L, avgSpeedMps = 7.0)
        val wCorruptSpeed = createWorkout(id = 5L, startTimeS = 2500L, activeTimeSec = 1850L, avgSpeedMps = 0.0)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2, w3, wCorruptDuration, wCorruptSpeed),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )

        assertEquals(3, series.points.size)
        assertEquals(2L, series.points[0].workoutId)
        assertEquals(3L, series.points[1].workoutId)
        assertEquals(1L, series.points[2].workoutId)

        assertEquals(0.0f, series.points[0].normalizedX, 0.001f)
        assertEquals(0.5f, series.points[1].normalizedX, 0.001f)
        assertEquals(1.0f, series.points[2].normalizedX, 0.001f)
    }

    @Test
    fun testDurationModeInversionFasterIsHigher() {
        // w1: 1800s (fastest, PR), w2: 2400s (slowest), w3: 2100s (middle)
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 1800L, avgSpeedMps = 7.0)
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 2400L, avgSpeedMps = 5.0)
        val w3 = createWorkout(id = 3L, startTimeS = 3000L, activeTimeSec = 2100L, avgSpeedMps = 6.0)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2, w3),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )

        // Faster (w1 = 1800s) must have normalizedY = 1.0f
        assertEquals(1.0f, series.points[0].normalizedY, 0.001f)
        assertTrue(series.points[0].isPr)

        // Slowest (w2 = 2400s) must have normalizedY = 0.0f
        assertEquals(0.0f, series.points[1].normalizedY, 0.001f)
        assertFalse(series.points[1].isPr)

        // Middle (w3 = 2100s) must have normalizedY = 0.5f
        assertEquals(0.5f, series.points[2].normalizedY, 0.001f)
        assertFalse(series.points[2].isPr)

        assertEquals(1L, series.prWorkoutId)
    }

    @Test
    fun testRunningPaceModeInversionFasterPaceIsHigher() {
        // Running sport: BSportType.RUN -> metricMode SPEED_OR_PACE renders Pace
        // w1: 3.0 m/s (slow pace = 333 s/km), w2: 4.0 m/s (fast pace = 250 s/km)
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 3.0, sportType = BSportType.RUN)
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 1500L, avgSpeedMps = 4.0, sportType = BSportType.RUN)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.RUN,
            metricMode = ClusterTrendMetricMode.SPEED_OR_PACE
        )

        assertTrue(series.isPace)
        // Faster pace (w2) must have higher normalizedY
        assertTrue(series.points[1].normalizedY > series.points[0].normalizedY)
        assertEquals(0.0f, series.points[0].normalizedY, 0.001f)
        assertEquals(1.0f, series.points[1].normalizedY, 0.001f)
    }

    @Test
    fun testCyclingSpeedModeStandardHigherSpeedIsHigher() {
        // Cycling sport: BSportType.BIKE -> metricMode SPEED_OR_PACE renders Speed
        // w1: 5.0 m/s (18 km/h), w2: 8.0 m/s (28.8 km/h)
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 5.0, sportType = BSportType.BIKE)
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 1500L, avgSpeedMps = 8.0, sportType = BSportType.BIKE)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.SPEED_OR_PACE
        )

        assertFalse(series.isPace)
        // Higher speed (w2) must have higher normalizedY
        assertTrue(series.points[1].normalizedY > series.points[0].normalizedY)
        assertEquals(0.0f, series.points[0].normalizedY, 0.001f)
        assertEquals(1.0f, series.points[1].normalizedY, 0.001f)
    }

    @Test
    fun testBoundaryResilienceZeroAndOneWorkout() {
        val emptySeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = emptyList(),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )
        assertTrue(emptySeries.isEmpty)
        assertNull(emptySeries.prWorkoutId)

        val singleWorkout = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 1800L, avgSpeedMps = 7.0)
        val singleSeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(singleWorkout),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )
        assertTrue(singleSeries.isEmpty)
        assertNull(singleSeries.prWorkoutId)
    }

    @Test
    fun testBoundaryResilienceIdenticalDurations() {
        // Two workouts with exact same duration
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 1800L, avgSpeedMps = 7.0)
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 1800L, avgSpeedMps = 7.0)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )

        assertEquals(2, series.points.size)
        // Clamped safely to 0.5f without NaN
        assertEquals(0.5f, series.points[0].normalizedY, 0.001f)
        assertEquals(0.5f, series.points[1].normalizedY, 0.001f)
        assertEquals(0.5f, series.avgNormalizedY, 0.001f)
    }

    @Test
    fun testNearestPointHitTesting() {
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2400L, avgSpeedMps = 5.0)
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 1800L, avgSpeedMps = 8.0)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )

        val widthPx = 200f
        val heightPx = 100f
        val maxDistPx = 30f

        // Point 1: normalizedX = 0f, normalizedY = 0f (canvas coords: x = 0, y = 100)
        // Point 2: normalizedX = 1f, normalizedY = 1f (canvas coords: x = 200, y = 0)

        // Touch near Point 1 (x=10, y=95) -> dist ~11px (< 30px)
        val hit1 = WorkoutClusterTrendMath.findNearestPoint(series.points, 10f, 95f, widthPx, heightPx, maxDistPx)
        assertNotNull(hit1)
        assertEquals(1L, hit1?.workoutId)

        // Touch near Point 2 (x=195, y=5) -> dist ~7px (< 30px)
        val hit2 = WorkoutClusterTrendMath.findNearestPoint(series.points, 195f, 5f, widthPx, heightPx, maxDistPx)
        assertNotNull(hit2)
        assertEquals(2L, hit2?.workoutId)

        // Touch in the middle (x=100, y=50) -> far from both points (> 70px)
        val hitNone = WorkoutClusterTrendMath.findNearestPoint(series.points, 100f, 50f, widthPx, heightPx, maxDistPx)
        assertNull(hitNone)
    }

    @Test
    fun testTukeyIqrOutlierFencingAndResilientYScaling() {
        // TST-SET-078.7: 7 normal sessions (2000s..2300s) + 1 extreme outlier (6952s, ~115 min)
        val normalWorkouts = listOf(
            createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 7.0),
            createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 2050L, avgSpeedMps = 7.0),
            createWorkout(id = 3L, startTimeS = 3000L, activeTimeSec = 2100L, avgSpeedMps = 7.0),
            createWorkout(id = 4L, startTimeS = 4000L, activeTimeSec = 2150L, avgSpeedMps = 7.0),
            createWorkout(id = 5L, startTimeS = 5000L, activeTimeSec = 2200L, avgSpeedMps = 7.0),
            createWorkout(id = 6L, startTimeS = 6000L, activeTimeSec = 2250L, avgSpeedMps = 7.0),
            createWorkout(id = 7L, startTimeS = 7000L, activeTimeSec = 2300L, avgSpeedMps = 7.0)
        )
        val outlierWorkout = createWorkout(id = 8L, startTimeS = 8000L, activeTimeSec = 6952L, avgSpeedMps = 2.0)

        val series = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = normalWorkouts + outlierWorkout,
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )

        assertEquals(8, series.points.size)

        // Normal workouts (indices 0..6) must NOT be clamped outliers
        for (i in 0..6) {
            assertFalse("Workout at index $i should not be clamped outlier", series.points[i].isClampedOutlier)
        }

        // Outlier workout (index 7) must be marked as clamped outlier and clamped to 0.0f
        val outlierPoint = series.points[7]
        assertTrue("Workout 8 (6952s) must be flagged as clamped outlier", outlierPoint.isClampedOutlier)
        assertEquals(0.0f, outlierPoint.normalizedY, 0.001f)

        // Fastest workout (2000s) must have normalizedY = 1.0f
        assertEquals(1.0f, series.points[0].normalizedY, 0.001f)

        // Workout 7 (2300s) should have normalizedY around (2525 - 2300)/525 ~ 0.428f (not squished to >0.9f)
        assertTrue("Normal workout 7 must expand across dynamic range", series.points[6].normalizedY < 0.5f)

        // Small dataset fallback: N = 3 (< 4) must NOT trigger outlier fencing
        val smallList = listOf(
            createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 7.0),
            createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 2100L, avgSpeedMps = 7.0),
            createWorkout(id = 3L, startTimeS = 3000L, activeTimeSec = 6952L, avgSpeedMps = 2.0)
        )
        val smallSeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = smallList,
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )
        assertEquals(3, smallSeries.points.size)
        assertFalse(smallSeries.points[0].isClampedOutlier)
        assertFalse(smallSeries.points[1].isClampedOutlier)
        assertFalse(smallSeries.points[2].isClampedOutlier)
    }

    @Test
    fun testXAxisAndYAxisScaleBoundsCalculationAndFormatting() {
        // TST-SET-078.8: Valid workouts spanning known dates and performance metrics
        val w1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 5.0, sportType = BSportType.BIKE)
            .copy(formattedDate = "12.05.")
        val w2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 2500L, avgSpeedMps = 8.0, sportType = BSportType.BIKE)
            .copy(formattedDate = "19.10.")

        // Duration mode
        val durationSeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.DURATION
        )
        assertEquals("12.05.", durationSeries.xAxisStartLabel)
        assertEquals("19.10.", durationSeries.xAxisEndLabel)
        // Fastest duration = 2000s (0:33:20), Slowest duration = 2500s (0:41:40)
        assertEquals("0:33:20", durationSeries.yAxisTopLabel)
        assertEquals("0:41:40", durationSeries.yAxisBottomLabel)

        // Speed mode (Cycling)
        val speedSeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(w1, w2),
            clusterSportType = BSportType.BIKE,
            metricMode = ClusterTrendMetricMode.SPEED_OR_PACE
        )
        assertFalse(speedSeries.isPace)
        // Fastest speed = 8.0 m/s (top), Slowest speed = 5.0 m/s (bottom)
        assertTrue(speedSeries.yAxisTopLabel.contains("km/h") || speedSeries.yAxisTopLabel.contains("mph"))
        assertTrue(speedSeries.yAxisBottomLabel.contains("km/h") || speedSeries.yAxisBottomLabel.contains("mph"))

        // Pace mode (Running)
        val wRun1 = createWorkout(id = 1L, startTimeS = 1000L, activeTimeSec = 2000L, avgSpeedMps = 3.0, sportType = BSportType.RUN)
            .copy(formattedDate = "01.06.")
        val wRun2 = createWorkout(id = 2L, startTimeS = 2000L, activeTimeSec = 1500L, avgSpeedMps = 4.0, sportType = BSportType.RUN)
            .copy(formattedDate = "15.08.")

        val paceSeries = WorkoutClusterTrendMath.buildTrendSeries(
            workouts = listOf(wRun1, wRun2),
            clusterSportType = BSportType.RUN,
            metricMode = ClusterTrendMetricMode.SPEED_OR_PACE
        )
        assertTrue(paceSeries.isPace)
        assertEquals("01.06.", paceSeries.xAxisStartLabel)
        assertEquals("15.08.", paceSeries.xAxisEndLabel)
        // Fastest pace = 4.0 m/s at top; Slowest pace = 3.0 m/s at bottom
        assertTrue(paceSeries.yAxisTopLabel.contains("/km") || paceSeries.yAxisTopLabel.contains("/mi"))
        assertTrue(paceSeries.yAxisBottomLabel.contains("/km") || paceSeries.yAxisBottomLabel.contains("/mi"))
    }
}
