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

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Pure mathematical unit tests for [ElevationSmoothingMath] verifying quantization staircase
 * elimination, dynamic boundary weight renormalization, crest preservation, and linear-time complexity.
 * (TST-UI-257 / REQ-UI-297)
 */
class ElevationSmoothingMathTest {

    private fun createDummyPoint(distance: Double, altitude: Double): PathPoint {
        return PathPoint(
            distance = distance,
            latLng = LatLng(48.7, 9.1),
            altitude = altitude,
            timeSec = (distance / 3.0).toLong()
        )
    }

    @Test
    fun testStaircaseElimination_onQuantizedSteps() {
        // Construct a synthetic 500m track sampled every 2m
        // Raw altitude steps up by 0.4m every 20m (average grade: 0.4 / 20 * 100 = 2.0%)
        val points = mutableListOf<PathPoint>()
        var d = 0.0
        while (d <= 500.0) {
            val stepIndex = (d / 20.0).toInt()
            val rawAlt = 100.0 + stepIndex * 0.4
            points.add(createDummyPoint(d, rawAlt))
            d += 2.0
        }

        val smoothed = ElevationSmoothingMath.smoothAltitudes(points)
        assertEquals(points.size, smoothed.size)

        // 1. Strictly monotonic progression in the interior (eliminating 0% plateaus)
        for (i in 0 until smoothed.size - 1) {
            assertTrue(
                "Smoothed altitude at index $i (${smoothed[i]}) must be <= next (${smoothed[i + 1]})",
                smoothed[i + 1] > smoothed[i]
            )
        }

        // 2. Continuous grade evaluation in the interior (away from boundary cutoffs)
        // With raw data, grade oscillated between 0.0% on plateaus and 20.0% at step boundaries!
        // With distance-weighted Gaussian smoothing, grade should stay tightly around ~2.0%
        val interiorStart = points.indexOfFirst { it.distance >= 63.0 }
        val interiorEnd = points.indexOfLast { it.distance <= 437.0 }

        for (i in interiorStart until interiorEnd) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val grade = ElevationSmoothingMath.calculateGrade(p1.distance, smoothed[i], p2.distance, smoothed[i + 1])
            assertTrue(
                "Grade at distance ${p1.distance}m was $grade%, expected smooth climb between 1.5% and 2.5%",
                grade in 1.5..2.5
            )
        }
    }

    @Test
    fun testDefaultConstantsAndDynamicCutoffScaling() {
        assertEquals("DEFAULT_SIGMA must be 21.0 meters (ATT-2746)", 21.0, ElevationSmoothingMath.DEFAULT_SIGMA, 0.001)
        assertEquals("DEFAULT_CUTOFF must be 63.0 meters (ATT-2746)", 63.0, ElevationSmoothingMath.DEFAULT_CUTOFF, 0.001)

        // Verify custom sigma scales cutoff dynamically
        val points = listOf(
            createDummyPoint(0.0, 100.0),
            createDummyPoint(20.0, 102.0),
            createDummyPoint(40.0, 104.0),
            createDummyPoint(60.0, 106.0)
        )
        val resultCustom = ElevationSmoothingMath.smoothAltitudes(points, sigma = 15.0)
        assertEquals(4, resultCustom.size)
        assertTrue(resultCustom[1] > resultCustom[0])
    }

    @Test
    fun testBoundaryWeightRenormalization_noSaggingOrPeaking() {
        // Track from 0m to 2000m with linear climb from 250m to 400m
        val points = mutableListOf<PathPoint>()
        var d = 0.0
        while (d <= 2000.0) {
            val alt = 250.0 + (d / 2000.0) * 150.0
            points.add(createDummyPoint(d, alt))
            d += 5.0
        }

        val smoothed = ElevationSmoothingMath.smoothAltitudes(points)

        // Start boundary: must not sag towards 0.0
        assertEquals("Start boundary must match initial elevation within 0.1%", 250.0, smoothed[0], 0.25)

        // End boundary: must not sag or peak
        assertEquals("End boundary must match final elevation within 0.1%", 400.0, smoothed.last(), 0.4)
    }

    @Test
    fun testSummitAndSaddlePreservation() {
        // Bell-shaped hill: base at 200m, peak at 300m at distance 500m, base at 200m at distance 1000m
        val points = mutableListOf<PathPoint>()
        var d = 0.0
        while (d <= 1000.0) {
            // Gaussian shaped hill with peak 300m
            val diff = (d - 500.0) / 100.0
            val alt = 200.0 + 100.0 * kotlin.math.exp(-0.5 * diff * diff)
            points.add(createDummyPoint(d, alt))
            d += 5.0
        }

        val smoothed = ElevationSmoothingMath.smoothAltitudes(points)

        // True peak is at 500m (index 100) with altitude 300.0m
        val peakIndex = points.indexOfFirst { it.distance == 500.0 }
        val smoothedPeak = smoothed[peakIndex]

        // Peak must be preserved without severe flattening (sigma=35m over a broad hill should preserve >= 290m)
        assertTrue(
            "Smoothed peak was $smoothedPeak, expected >= 290.0m",
            smoothedPeak >= 290.0
        )

        // Max altitude must still occur within +/- 10m of the true peak
        val maxSmoothedIndex = smoothed.indices.maxByOrNull { smoothed[it] }!!
        val maxSmoothedDist = points[maxSmoothedIndex].distance
        assertEquals(500.0, maxSmoothedDist, 10.0)
    }

    @Test
    fun testDegenerateAndEdgeCaseInputs() {
        // 1. Empty list
        val emptyResult = ElevationSmoothingMath.smoothAltitudes(emptyList())
        assertEquals(0, emptyResult.size)

        // 2. Single point
        val singlePoint = listOf(createDummyPoint(100.0, 345.6))
        val singleResult = ElevationSmoothingMath.smoothAltitudes(singlePoint)
        assertEquals(1, singleResult.size)
        assertEquals(345.6, singleResult[0], 0.001)

        // 3. Duplicate distances
        val duplicatePoints = listOf(
            createDummyPoint(0.0, 100.0),
            createDummyPoint(0.0, 100.0),
            createDummyPoint(0.0, 100.0)
        )
        val duplicateResult = ElevationSmoothingMath.smoothAltitudes(duplicatePoints)
        assertEquals(3, duplicateResult.size)
        assertEquals(100.0, duplicateResult[0], 0.001)
    }

    @Test
    fun testHighDensityBenchmark_10000Points() {
        val points = mutableListOf<PathPoint>()
        for (i in 0 until 10_000) {
            val d = i * 2.5 // 25 km total
            val alt = 200.0 + 50.0 * kotlin.math.sin(d / 1000.0)
            points.add(createDummyPoint(d, alt))
        }

        // Warm up JIT
        ElevationSmoothingMath.smoothAltitudes(points.take(5000))

        val elapsedMs = measureTimeMillis {
            val result = ElevationSmoothingMath.smoothAltitudes(points)
            assertEquals(10_000, result.size)
        }

        assertTrue(
            "Smoothing 10,000 dense points took $elapsedMs ms, expected < 50 ms",
            elapsedMs < 50
        )
    }
}
