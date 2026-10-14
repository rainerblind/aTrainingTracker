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

package com.atrainingtracker.trainingtracker.domain.crop

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropWorkoutMathTest {

    @Test
    fun zeroAnchorAndShiftCumulative_anchorsStartRowToZeroAndClampsEpsilon() {
        val original = listOf(10.0, 15.0, 15.0 + 1e-7, 20.0, 25.0)
        val startIndex = 1 // baseline is 15.0

        val shifted = CropWorkoutMath.zeroAnchorAndShiftCumulative(original, startIndex)

        assertEquals(5, shifted.size)
        assertEquals(0.0, shifted[0], 0.0) // before start -> 0.0
        assertEquals(0.0, shifted[1], 0.0) // start row anchored to 0.0
        assertEquals(0.0, shifted[2], 0.0) // difference is 1e-7 (< 1e-6) clamped to 0.0
        assertEquals(5.0, shifted[3], 1e-5)
        assertEquals(10.0, shifted[4], 1e-5)
    }

    @Test
    fun zeroAnchorAndShiftCumulativeLong_anchorsAndPreventsNegative() {
        val original = listOf(100L, 200L, 250L, 300L)
        val shifted = CropWorkoutMath.zeroAnchorAndShiftCumulativeLong(original, startIndex = 1)

        assertEquals(4, shifted.size)
        assertEquals(0L, shifted[0])
        assertEquals(0L, shifted[1])
        assertEquals(50L, shifted[2])
        assertEquals(100L, shifted[3])
    }

    @Test
    fun recalculateLineDistance_computesHaversineFromNewStart() {
        val coords = listOf(
            LatLng(48.0, 11.0),
            LatLng(48.0, 11.0), // start point at index 1
            LatLng(48.01, 11.0), // ~1.11 km north
            LatLng(48.02, 11.0)  // ~2.22 km north
        )

        val distances = CropWorkoutMath.recalculateLineDistance(coords, startIndex = 1)

        assertEquals(4, distances.size)
        assertEquals(0.0, distances[0], 0.0)
        assertEquals(0.0, distances[1], 0.0) // anchored start point
        assertTrue(distances[2] > 1100.0 && distances[2] < 1120.0)
        assertTrue(distances[3] > 2200.0 && distances[3] < 2240.0)
    }

    @Test
    fun recalculateLineDistance_indoorSessionWithNullCoords_returnsAllZerosSafely() {
        val nullCoords: List<LatLng?> = listOf(null, null, null, null)
        val distances = CropWorkoutMath.recalculateLineDistance(nullCoords, startIndex = 1)

        assertEquals(4, distances.size)
        distances.forEach { assertEquals(0.0, it, 0.0) }
    }

    @Test
    fun recalculateExtrema_sanitizesNonFiniteAndDerivesAccurateExtrema() {
        val values = listOf(
            null,
            Double.NaN,
            120.0,
            Double.POSITIVE_INFINITY,
            150.0,
            140.0,
            Double.NEGATIVE_INFINITY,
            180.0
        )

        val result = CropWorkoutMath.recalculateExtrema(values)

        assertTrue(result.hasData)
        assertEquals(120.0, result.min, 1e-5)
        assertEquals(180.0, result.max, 1e-5)
        assertEquals(147.5, result.avg, 1e-5)
        assertEquals(120.0, result.start, 1e-5)
        assertEquals(180.0, result.end, 1e-5)
    }

    @Test
    fun recalculateExtrema_emptyOrDisconnectedStream_returnsZerosWithHasDataFalse() {
        val emptyValues: List<Double?> = listOf(null, Double.NaN, null)
        val result = CropWorkoutMath.recalculateExtrema(emptyValues)

        assertFalse(result.hasData)
        assertEquals(0.0, result.min, 0.0)
        assertEquals(0.0, result.max, 0.0)
        assertEquals(0.0, result.avg, 0.0)
        assertEquals(0.0, result.start, 0.0)
        assertEquals(0.0, result.end, 0.0)
    }

    @Test
    fun isCropValid_validatesBoundariesAndDuration() {
        // Valid case
        assertTrue(
            CropWorkoutMath.isCropValid(
                startIndex = 0,
                endIndex = 10,
                totalPoints = 20,
                durationSec = 10.0,
                minIndexStep = 1
            )
        )

        // Invalid: start >= end
        assertFalse(
            CropWorkoutMath.isCropValid(
                startIndex = 5,
                endIndex = 5,
                totalPoints = 20,
                durationSec = 10.0,
                minIndexStep = 1
            )
        )

        // Invalid: duration < 1.0s
        assertFalse(
            CropWorkoutMath.isCropValid(
                startIndex = 0,
                endIndex = 5,
                totalPoints = 20,
                durationSec = 0.5,
                minIndexStep = 1
            )
        )

        // Degenerate boundary case: exactly 2 points with duration >= 1.0s
        assertTrue(
            CropWorkoutMath.isCropValid(
                startIndex = 0,
                endIndex = 1,
                totalPoints = 2,
                durationSec = 1.0,
                minIndexStep = 1
            )
        )

        // Invalid: fewer than 2 total points
        assertFalse(
            CropWorkoutMath.isCropValid(
                startIndex = 0,
                endIndex = 0,
                totalPoints = 1,
                durationSec = 1.0,
                minIndexStep = 1
            )
        )
    }

    @Test
    fun calculateAverageSpeed_guardsAgainstZeroActiveTimeAndNonFinite() {
        assertEquals(5.0, CropWorkoutMath.calculateAverageSpeed(100.0, 20), 1e-5)
        assertEquals(0.0, CropWorkoutMath.calculateAverageSpeed(100.0, 0), 0.0)
        assertEquals(0.0, CropWorkoutMath.calculateAverageSpeed(Double.NaN, 20), 0.0)
        assertEquals(0.0, CropWorkoutMath.calculateAverageSpeed(-10.0, 20), 0.0)
    }

    @Test
    fun calculateRequiredStorageBytes_respects50MbFloorAndMultiplier() {
        // Small DB (1 MB): floor 50 MB applies
        assertEquals(50L * 1024L * 1024L, CropWorkoutMath.calculateRequiredStorageBytes(1024L * 1024L))

        // Large DB (20 MB): 5 * 20 MB = 100 MB applies
        assertEquals(100L * 1024L * 1024L, CropWorkoutMath.calculateRequiredStorageBytes(20L * 1024L * 1024L))
    }
}
