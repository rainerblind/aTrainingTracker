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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HrvDurabilityCalculator] verifying REQ-PER-038 and TST-PER-046.
 */
class HrvDurabilityCalculatorTest {

    private val dummyLatLng = LatLng(48.0, 11.0)

    private fun createPoint(
        timeSec: Long,
        power: Int? = 200,
        speedMps: Double? = 8.0,
        alpha1: Double? = 0.85,
        artifactRate: Double? = 1.0
    ): PathPoint {
        return PathPoint(
            distance = 0.0,
            latLng = dummyLatLng,
            altitude = 500.0,
            timeSec = timeSec,
            hr = 140,
            power = power,
            speedMps = speedMps,
            hrvDfaA1 = alpha1,
            hrvArtifactRate = artifactRate
        )
    }

    @Test
    fun testEmptyOrNonHrvPointsReturnNoHrvData() {
        val resultEmpty = HrvDurabilityCalculator.calculateDurability(emptyList())
        assertEquals(HrvDurabilityStatus.NO_HRV_DATA, resultEmpty.status)
        assertNull(resultEmpty.durabilityDriftPct)

        val nonHrvPoints = (0 until 100).map { i ->
            createPoint(timeSec = i * 5L, alpha1 = null, artifactRate = null)
        }
        val resultNonHrv = HrvDurabilityCalculator.calculateDurability(nonHrvPoints)
        assertEquals(HrvDurabilityStatus.NO_HRV_DATA, resultNonHrv.status)
        assertNull(resultNonHrv.durabilityDriftPct)
    }

    @Test
    fun testShortWorkoutYieldsInsufficientDuration() {
        // 45-minute workout (2700 s)
        val points = (0..2700 step 5).map { t ->
            createPoint(timeSec = t.toLong(), power = 200, alpha1 = 0.85)
        }
        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.INSUFFICIENT_DURATION, result.status)
        assertNull(result.durabilityDriftPct)
    }

    @Test
    fun testFreshBaselineAndLateSegmentWindowSelectionAndDrift() {
        // 90-minute workout (5400 s)
        // Fresh baseline (15–45 min): alpha1 = 0.80, power = 200W
        // Late segment (>60 min / 3600–5400 s): alpha1 = 0.64 (-20%), power = 200W
        val points = (0..5400 step 5).map { t ->
            val a1 = if (t > 3600) 0.64 else 0.80
            createPoint(timeSec = t.toLong(), power = 200, alpha1 = a1)
        }

        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.VALID, result.status)
        assertNotNull(result.freshBaselineMean)
        assertNotNull(result.lateMean)
        assertNotNull(result.durabilityDriftPct)

        assertEquals(0.80, result.freshBaselineMean!!, 0.001)
        assertEquals(0.64, result.lateMean!!, 0.001)
        // Drift = (0.64 - 0.80) / 0.80 * 100% = -20.0%
        assertEquals(-20.0, result.durabilityDriftPct!!, 0.01)
        assertEquals(200.0, result.referenceIntensity!!, 0.001)
        assertTrue(result.isPowerBased)
    }

    @Test
    fun testIsoIntensityBandFilteringAndNormalization() {
        // Reference power = 200 W (median of fresh baseline).
        // +/- 10% band is [180 W, 220 W].
        // Out-of-band samples (e.g. 150 W and 260 W) must be excluded.
        // Sample at 210 W has raw alpha1 = 0.80 * (200/210) -> normalized alpha1 = raw * (210/200) = 0.80.
        val points = mutableListOf<PathPoint>()
        // Warmup (0–15 min)
        for (t in 0 until 900 step 5) {
            points.add(createPoint(timeSec = t.toLong(), power = 150, alpha1 = 0.90))
        }
        // Fresh baseline (15–45 min): alternating 190 W, 200 W, 210 W -> median 200 W
        for (t in 900..2700 step 5) {
            val p = when ((t / 5) % 3) {
                0 -> 190
                1 -> 200
                else -> 210
            }
            // For power p, alpha1 decreases with higher workload: a1 = 0.80 * (200.0 / p) so normalized alpha1 is exactly 0.80
            val a1 = 0.80 * (200.0 / p)
            points.add(createPoint(timeSec = t.toLong(), power = p, alpha1 = a1))
        }
        // Transition (45–60 min)
        for (t in 2705..3600 step 5) {
            points.add(createPoint(timeSec = t.toLong(), power = 250, alpha1 = 0.70))
        }
        // Late segment (60–90 min): alternating 190 W, 200 W, 210 W with drift (norm alpha1 = 0.72 -> -10% drift)
        for (t in 3605..5400 step 5) {
            val p = when ((t / 5) % 3) {
                0 -> 190
                1 -> 200
                else -> 210
            }
            val a1 = 0.72 * (200.0 / p)
            points.add(createPoint(timeSec = t.toLong(), power = p, alpha1 = a1))
        }

        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.VALID, result.status)
        assertEquals(200.0, result.referenceIntensity!!, 0.001)
        assertEquals(0.80, result.freshBaselineMean!!, 0.01)
        assertEquals(0.72, result.lateMean!!, 0.01)
        assertEquals(-10.0, result.durabilityDriftPct!!, 0.05)
    }

    @Test
    fun testArtifactRateGatingExcludesNoisySamples() {
        // All late segment samples have artifact rate > 5.0%, so matched data < 600s
        val points = (0..5400 step 5).map { t ->
            val art = if (t > 3600) 8.0 else 1.0
            createPoint(timeSec = t.toLong(), power = 200, alpha1 = 0.80, artifactRate = art)
        }
        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.INSUFFICIENT_MATCHED_DATA, result.status)
        assertNull(result.durabilityDriftPct)
    }

    @Test
    fun testInsufficientMatchedDataInLateSegmentYieldsGuard() {
        // Late segment has only 300s of matched data (e.g. rider climbed out of zone)
        val points = (0..5400 step 5).map { t ->
            val p = if (t in 3605..3900) 200 else if (t > 3900) 350 else 200
            createPoint(timeSec = t.toLong(), power = p, alpha1 = 0.80)
        }
        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.INSUFFICIENT_MATCHED_DATA, result.status)
        assertNull(result.durabilityDriftPct)
    }

    @Test
    fun testSliceTrajectoryGeneration() {
        // 120-minute workout (7200 s)
        val points = (0..7200 step 5).map { t ->
            createPoint(timeSec = t.toLong(), power = 200, alpha1 = 0.80)
        }
        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.VALID, result.status)
        assertTrue(result.slices.isNotEmpty())

        // Check slices: 15–30, 30–45, 45–60, 60–75, 75–90, 90–105, 105–120
        assertEquals(15, result.slices[0].startMinute)
        assertEquals(30, result.slices[0].endMinute)
        assertNotNull(result.slices[0].meanNormalizedAlpha1)
        assertEquals(0.80, result.slices[0].meanNormalizedAlpha1!!, 0.001)

        val lastSlice = result.slices.last()
        assertEquals(105, lastSlice.startMinute)
        assertEquals(120, lastSlice.endMinute)
    }

    @Test
    fun testSpeedBasedDurabilityForRunningWorkouts() {
        // Running workout without power telemetry
        val points = (0..5400 step 5).map { t ->
            val a1 = if (t > 3600) 0.70 else 0.80
            createPoint(timeSec = t.toLong(), power = null, speedMps = 3.5, alpha1 = a1)
        }
        val result = HrvDurabilityCalculator.calculateDurability(points)
        assertEquals(HrvDurabilityStatus.VALID, result.status)
        assertFalse(result.isPowerBased)
        assertEquals(3.5, result.referenceIntensity!!, 0.001)
        assertEquals(0.80, result.freshBaselineMean!!, 0.001)
        assertEquals(0.70, result.lateMean!!, 0.001)
        assertEquals(-12.5, result.durabilityDriftPct!!, 0.01)
    }
}
