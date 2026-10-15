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

package com.atrainingtracker.trainingtracker.ui.aftermath.crop

import com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath
import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests verifying Crop UI state management, validation invariants,
 * range scrubbing calculations, and error state transitions (REQ-UI-368, TST-UI-331).
 */
class CropWorkoutScreenContractTest {

    private val samplePoints = listOf(
        CropSamplePoint(1, 0, "2026-10-14 10:00:00", 0, 0, 0.0, null, 100.0, 1),
        CropSamplePoint(2, 1, "2026-10-14 10:00:10", 10, 10, 50.0, null, 101.0, 1),
        CropSamplePoint(3, 2, "2026-10-14 10:00:20", 20, 20, 100.0, null, 102.0, 1),
        CropSamplePoint(4, 3, "2026-10-14 10:00:30", 30, 30, 150.0, null, 103.0, 1),
        CropSamplePoint(5, 4, "2026-10-14 10:00:40", 40, 40, 200.0, null, 104.0, 1),
        CropSamplePoint(6, 5, "2026-10-14 10:00:50", 50, 50, 250.0, null, 105.0, 1)
    )

    @Test
    fun testInitialUiStateCalculation() {
        val total = samplePoints.size
        val origDist = samplePoints.last().distanceTotalM - samplePoints.first().distanceTotalM
        val origDur = samplePoints.last().timeTotalS - samplePoints.first().timeTotalS
        val avgSpeed = CropWorkoutMath.calculateAverageSpeed(origDist, origDur.toInt())

        val state = CropWorkoutUiState(
            isLoading = false,
            workoutName = "Morning Run",
            totalPoints = total,
            startIndex = 0,
            endIndex = total - 1,
            originalDistanceM = origDist,
            originalDurationS = origDur,
            croppedDistanceM = origDist,
            croppedDurationS = origDur,
            croppedAvgSpeedMps = avgSpeed,
            points = samplePoints,
            isValid = CropWorkoutMath.isCropValid(0, total - 1, total, origDur.toDouble())
        )

        assertFalse(state.isLoading)
        assertFalse(state.isCropping)
        assertTrue(state.isValid)
        assertEquals(6, state.totalPoints)
        assertEquals(250.0, state.croppedDistanceM, 1e-4)
        assertEquals(50L, state.croppedDurationS)
        assertEquals(5.0, state.croppedAvgSpeedMps, 1e-4)
    }

    @Test
    fun testRangeUpdateValidatesAndRecomputesMetrics() {
        val total = samplePoints.size
        val start = 1
        val end = 4
        val startPt = samplePoints[start]
        val endPt = samplePoints[end]

        val croppedDist = endPt.distanceTotalM - startPt.distanceTotalM
        val croppedDur = endPt.timeTotalS - startPt.timeTotalS
        val avgSpeed = CropWorkoutMath.calculateAverageSpeed(croppedDist, croppedDur.toInt())
        val valid = CropWorkoutMath.isCropValid(start, end, total, croppedDur.toDouble())

        assertTrue(valid)
        assertEquals(150.0, croppedDist, 1e-4) // 200 - 50
        assertEquals(30L, croppedDur) // 40 - 10
        assertEquals(5.0, avgSpeed, 1e-4)
    }

    @Test
    fun testInvalidZeroDurationOrSinglePointCropRejected() {
        val total = samplePoints.size
        // Single point (start == end)
        assertFalse(CropWorkoutMath.isCropValid(2, 2, total, 0.0))
        // Inverted range
        assertFalse(CropWorkoutMath.isCropValid(4, 2, total, 10.0))
        // Out of bounds
        assertFalse(CropWorkoutMath.isCropValid(-1, 3, total, 10.0))
        assertFalse(CropWorkoutMath.isCropValid(2, 10, total, 10.0))
    }
}
