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

package com.atrainingtracker.trainingtracker.domain.laps

import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying LapSplitAndMoveCalculator logic (TST-UI-449.1 / REQ-UI-449).
 */
class LapSplitAndMoveCalculatorTest {

    private fun createPoint(
        rowId: Long,
        index: Int,
        timeS: Long,
        distM: Double,
        lapNr: Long
    ) = CropSamplePoint(
        rowId = rowId,
        index = index,
        timeStr = "2026-11-05 10:00:$index",
        timeTotalS = timeS,
        timeActiveS = timeS,
        distanceTotalM = distM,
        latLng = LatLng(48.0 + index * 0.001, 11.0 + index * 0.001),
        altitude = 500.0,
        lapNr = lapNr
    )

    @Test
    fun `validateSplit successfully splits single lap workout`() {
        val samples = listOf(
            createPoint(1, 0, 0, 0.0, 1),
            createPoint(2, 1, 100, 500.0, 1),
            createPoint(3, 2, 200, 1000.0, 1),
            createPoint(4, 3, 300, 1500.0, 1)
        )
        val existingLaps = listOf(
            LapData(id = 1L, workoutId = 10L, lapNr = 1L, timeTotalS = 300, distanceTotalM = 1500.0, speedAverageMps = 5.0, name = "Base")
        )

        // Split at index 2 (time: 200s, dist: 1000m)
        // Part 1: [0..1] -> duration: 100s, dist: 500m
        // Part 2: [2..3] -> duration: 100s, dist: 500m
        val result = LapSplitAndMoveCalculator.validateSplit(samples, splitSampleIndex = 2, existingLaps = existingLaps)

        assertTrue(result.isValid)
        assertNotNull(result.previewLapBefore)
        assertNotNull(result.previewLapAfter)

        val before = result.previewLapBefore!!
        val after = result.previewLapAfter!!

        assertEquals(1L, before.lapNr)
        assertEquals(100, before.timeTotalS)
        assertEquals(500.0, before.distanceTotalM, 1e-4)
        assertEquals(5.0, before.speedAverageMps, 1e-4)
        assertEquals("Base", before.name)

        assertEquals(2L, after.lapNr)
        assertEquals(100, after.timeTotalS)
        assertEquals(500.0, after.distanceTotalM, 1e-4)
        assertEquals(5.0, after.speedAverageMps, 1e-4)
    }

    @Test
    fun `validateSplit rejects split at start of lap`() {
        val samples = listOf(
            createPoint(1, 0, 0, 0.0, 1),
            createPoint(2, 1, 100, 500.0, 1),
            createPoint(3, 2, 200, 1000.0, 1)
        )

        // Index 0 is the start of the lap -> cannot split at start
        val result = LapSplitAndMoveCalculator.validateSplit(samples, splitSampleIndex = 0)
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `validateSplit rejects split when index out of bounds`() {
        val samples = listOf(
            createPoint(1, 0, 0, 0.0, 1),
            createPoint(2, 1, 100, 500.0, 1)
        )

        assertFalse(LapSplitAndMoveCalculator.validateSplit(samples, splitSampleIndex = -1).isValid)
        assertFalse(LapSplitAndMoveCalculator.validateSplit(samples, splitSampleIndex = 5).isValid)
        assertFalse(LapSplitAndMoveCalculator.validateSplit(emptyList(), splitSampleIndex = 0).isValid)
    }

    @Test
    fun `validateSplit rejects split when resulting lap would have zero duration and zero distance`() {
        val samples = listOf(
            createPoint(1, 0, 0, 0.0, 1),
            createPoint(2, 1, 0, 0.0, 1), // duplicate time & distance
            createPoint(3, 2, 100, 500.0, 1)
        )

        // Split at index 1 -> Part 1: [0..0] duration: 0, distance: 0 -> phantom lap!
        val result = LapSplitAndMoveCalculator.validateSplit(samples, splitSampleIndex = 1)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("empty", ignoreCase = true) == true)
    }

    @Test
    fun `recalculateLapsAfterSplit preserves contiguous 1 to K numbering and metadata`() {
        val workoutId = 42L
        val existingLaps = listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, name = "Warmup"),
            LapData(id = 2L, workoutId = workoutId, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0, name = "Main")
        )

        // Simulate splitting Lap 1 at sample 1:
        // Sample 0: lapNr 1
        // Sample 1: lapNr 2 (new split part of Lap 1)
        // Sample 2, 3: lapNr 3 (shifted from Lap 2)
        val updatedSamples = listOf(
            createPoint(1, 0, 0, 0.0, 1),
            createPoint(2, 1, 50, 250.0, 1),
            createPoint(3, 2, 60, 300.0, 2),
            createPoint(4, 3, 100, 500.0, 2),
            createPoint(5, 4, 150, 800.0, 3),
            createPoint(6, 5, 300, 1500.0, 3)
        )

        val newLaps = LapSplitAndMoveCalculator.recalculateLapsAfterSplit(workoutId, existingLaps, updatedSamples)

        assertEquals(3, newLaps.size)
        assertEquals(1L, newLaps[0].lapNr)
        assertEquals(2L, newLaps[1].lapNr)
        assertEquals(3L, newLaps[2].lapNr)

        // Lap 1: 0..50s, 250m -> 5.0 m/s
        assertEquals(50, newLaps[0].timeTotalS)
        assertEquals(250.0, newLaps[0].distanceTotalM, 1e-4)

        // Lap 2: 60..100s (40s), 300..500m (200m) -> 5.0 m/s
        assertEquals(40, newLaps[1].timeTotalS)
        assertEquals(200.0, newLaps[1].distanceTotalM, 1e-4)

        // Lap 3: 150..300s (150s), 800..1500m (700m) -> ~4.67 m/s
        assertEquals(150, newLaps[2].timeTotalS)
        assertEquals(700.0, newLaps[2].distanceTotalM, 1e-4)
    }
}
