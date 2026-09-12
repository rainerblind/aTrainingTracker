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

import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LapSegmentUtils].
 * Verifies zero-IO polyline slicing, cumulative distance intervals, and camera bounds calculation.
 */
class LapSegmentUtilsTest {

    @Test
    fun `calculateLapDistanceRange returns correct cumulative intervals`() {
        val laps = listOf(
            LapData(workoutId = 1L, lapNr = 1L, timeTotalS = 60, distanceTotalM = 500.0, speedAverageMps = 8.33),
            LapData(workoutId = 1L, lapNr = 2L, timeTotalS = 120, distanceTotalM = 1000.0, speedAverageMps = 8.33),
            LapData(workoutId = 1L, lapNr = 3L, timeTotalS = 90, distanceTotalM = 800.0, speedAverageMps = 8.88)
        )

        val range1 = LapSegmentUtils.calculateLapDistanceRange(laps, 1L)
        assertEquals(0.0, range1.first, 0.001)
        assertEquals(500.0, range1.second, 0.001)

        val range2 = LapSegmentUtils.calculateLapDistanceRange(laps, 2L)
        assertEquals(500.0, range2.first, 0.001)
        assertEquals(1500.0, range2.second, 0.001)

        val range3 = LapSegmentUtils.calculateLapDistanceRange(laps, 3L)
        assertEquals(1500.0, range3.first, 0.001)
        assertEquals(2300.0, range3.second, 0.001)

        val unknownRange = LapSegmentUtils.calculateLapDistanceRange(laps, 99L)
        assertEquals(0.0, unknownRange.first, 0.001)
        assertEquals(0.0, unknownRange.second, 0.001)
    }

    @Test
    fun `sliceLapSegment extracts segment with boundary anchors`() {
        val points = (0 until 10).map { LatLng(48.0 + it * 0.001, 11.0 + it * 0.001) }
        val dists = (0 until 10).map { it * 100.0 }

        // Slicing [250.0, 650.0] should pick indices 3..6 with anchors 2 and 7
        val segment = LapSegmentUtils.sliceLapSegment(points, dists, 250.0, 650.0)

        assertEquals(6, segment.size)
        assertEquals(points[2], segment.first())
        assertEquals(points[7], segment.last())
    }

    @Test
    fun `sliceLapSegment handles empty inputs and invalid ranges gracefully`() {
        val points = listOf(LatLng(48.0, 11.0), LatLng(48.1, 11.1))
        val dists = listOf(0.0, 100.0)

        assertTrue(LapSegmentUtils.sliceLapSegment(emptyList(), dists, 0.0, 100.0).isEmpty())
        assertTrue(LapSegmentUtils.sliceLapSegment(points, emptyList(), 0.0, 100.0).isEmpty())
        assertTrue(LapSegmentUtils.sliceLapSegment(points, dists, 150.0, 50.0).isEmpty())
        assertTrue(LapSegmentUtils.sliceLapSegment(points, dists, 500.0, 600.0).isEmpty())
    }

    @Test
    fun `calculateLapBounds builds valid enclosing bounds`() {
        val points = listOf(
            LatLng(48.10, 11.50),
            LatLng(48.15, 11.55),
            LatLng(48.08, 11.48)
        )

        val bounds = LapSegmentUtils.calculateLapBounds(points)
        assertNotNull(bounds)
        assertEquals(48.08, bounds!!.southwest.latitude, 0.001)
        assertEquals(11.48, bounds.southwest.longitude, 0.001)
        assertEquals(48.15, bounds.northeast.latitude, 0.001)
        assertEquals(11.55, bounds.northeast.longitude, 0.001)
    }

    @Test
    fun `calculateLapBounds expands zero-area bounds for single coordinate`() {
        val points = listOf(LatLng(48.10, 11.50))
        val bounds = LapSegmentUtils.calculateLapBounds(points)

        assertNotNull(bounds)
        assertTrue(bounds!!.southwest.latitude < bounds.northeast.latitude)
        assertTrue(bounds.southwest.longitude < bounds.northeast.longitude)
    }

    @Test
    fun `calculateLapBounds returns null for empty list`() {
        assertNull(LapSegmentUtils.calculateLapBounds(emptyList()))
    }
}
