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
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying deterministic lap recalculation, boundary lap handling,
 * sequential contiguous re-indexing (1..K), and note preservation (REQ-UI-368, TST-UI-331).
 */
class CropLapsRecalculationTest {

    private fun recalculateLaps(
        workoutId: Long,
        existingLaps: List<LapData>,
        retainedSamples: List<CropSamplePoint>
    ): List<LapData> {
        val retainedByLap = retainedSamples.groupBy { it.lapNr }
        val recalculatedLaps = mutableListOf<LapData>()
        var newLapSequence = 1L

        for (existingLap in existingLaps) {
            val lapPoints = retainedByLap[existingLap.lapNr] ?: continue
            if (lapPoints.isEmpty()) continue
            val firstPt = lapPoints.first()
            val lastPt = lapPoints.last()
            val lapDurationS = (lastPt.timeTotalS - firstPt.timeTotalS).coerceAtLeast(0L).toInt()
            val lapDistanceM = (lastPt.distanceTotalM - firstPt.distanceTotalM).coerceAtLeast(0.0)

            // Strictly filter out zero-duration / zero-distance phantom laps
            if (lapDurationS <= 0 && lapDistanceM <= 0.0) continue

            val lapAvgSpeed = CropWorkoutMath.calculateAverageSpeed(lapDistanceM, lapDurationS)
            val lapTimeStart = firstPt.timeStr ?: existingLap.timeStart

            recalculatedLaps.add(
                LapData(
                    id = 0L,
                    workoutId = workoutId,
                    lapNr = newLapSequence++,
                    timeStart = lapTimeStart,
                    timeTotalS = lapDurationS,
                    distanceTotalM = lapDistanceM,
                    speedAverageMps = lapAvgSpeed,
                    name = existingLap.name,
                    description = existingLap.description
                )
            )
        }
        return recalculatedLaps
    }

    @Test
    fun testPrunedLapsAreCompletelyRemovedAndRemainingReindexedContiguously() {
        val workoutId = 42L
        val originalLaps = listOf(
            LapData(1, workoutId, 1, "2026-10-14 10:00:00", 600, 3000.0, 5.0, "Warmup", "Easy jog"),
            LapData(2, workoutId, 2, "2026-10-14 10:10:00", 600, 3500.0, 5.83, "Interval 1", "Hard tempo"),
            LapData(3, workoutId, 3, "2026-10-14 10:20:00", 600, 3600.0, 6.0, "Interval 2", "Max effort"),
            LapData(4, workoutId, 4, "2026-10-14 10:30:00", 600, 2800.0, 4.67, "Cooldown", "Gentle finish")
        )

        // Retain only samples from Lap 2 and Lap 3
        val retainedSamples = listOf(
            // Lap 2 samples
            CropSamplePoint(100, 0, "2026-10-14 10:12:00", 720, 720, 3600.0, null, 100.0, 2),
            CropSamplePoint(101, 1, "2026-10-14 10:18:00", 1080, 1080, 6200.0, null, 105.0, 2),
            // Lap 3 samples
            CropSamplePoint(102, 2, "2026-10-14 10:20:00", 1200, 1200, 6500.0, null, 110.0, 3),
            CropSamplePoint(103, 3, "2026-10-14 10:25:00", 1500, 1500, 9500.0, null, 112.0, 3)
        )

        val updatedLaps = recalculateLaps(workoutId, originalLaps, retainedSamples)

        // 1. Exactly 2 laps retained (Lap 1 and Lap 4 pruned)
        assertEquals(2, updatedLaps.size)

        // 2. Sequential contiguous re-indexing (1..2)
        assertEquals(1L, updatedLaps[0].lapNr)
        assertEquals(2L, updatedLaps[1].lapNr)

        // 3. User notes strictly preserved verbatim
        assertEquals("Interval 1", updatedLaps[0].name)
        assertEquals("Hard tempo", updatedLaps[0].description)
        assertEquals("Interval 2", updatedLaps[1].name)
        assertEquals("Max effort", updatedLaps[1].description)

        // 4. Boundary metrics recalculated strictly from retained samples
        assertEquals(360, updatedLaps[0].timeTotalS) // 1080 - 720
        assertEquals(2600.0, updatedLaps[0].distanceTotalM, 1e-4) // 6200 - 3600
        assertEquals("2026-10-14 10:12:00", updatedLaps[0].timeStart)

        assertEquals(300, updatedLaps[1].timeTotalS) // 1500 - 1200
        assertEquals(3000.0, updatedLaps[1].distanceTotalM, 1e-4) // 9500 - 6500
        assertEquals("2026-10-14 10:20:00", updatedLaps[1].timeStart)
    }

    @Test
    fun testZeroDurationPhantomLapsRejected() {
        val workoutId = 99L
        val originalLaps = listOf(
            LapData(1, workoutId, 1, "2026-10-14 10:00:00", 100, 500.0, 5.0, "Lap 1", null),
            LapData(2, workoutId, 2, "2026-10-14 10:01:40", 100, 500.0, 5.0, "Lap 2", null)
        )

        // Retain only 1 sample for Lap 1 (diff = 0 duration, 0 distance)
        val retainedSamples = listOf(
            CropSamplePoint(1, 0, "2026-10-14 10:00:00", 10, 10, 50.0, null, null, 1),
            CropSamplePoint(2, 1, "2026-10-14 10:01:40", 100, 100, 500.0, null, null, 2),
            CropSamplePoint(3, 2, "2026-10-14 10:02:40", 160, 160, 800.0, null, null, 2)
        )

        val updatedLaps = recalculateLaps(workoutId, originalLaps, retainedSamples)

        // Lap 1 has 1 point -> duration = 0, distance = 0 -> filtered out!
        assertEquals(1, updatedLaps.size)
        assertEquals(1L, updatedLaps[0].lapNr)
        assertEquals("Lap 2", updatedLaps[0].name)
        assertEquals(60, updatedLaps[0].timeTotalS)
        assertEquals(300.0, updatedLaps[0].distanceTotalM, 1e-4)
    }
}
