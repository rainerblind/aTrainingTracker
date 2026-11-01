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

/**
 * Unit tests for live track polyline decimation (REQ-TRK-042.2, TST-TRK-042.2, ATT-4863).
 */
class LiveTrackDecimationTest {

    @Test
    fun testShortTrackUnderThreshold_remainsUntouched() {
        val shortPath = (0 until 250).map { i ->
            LatLng(48.0 + (i * 0.0001), 11.0)
        }
        val result = decimateLiveTrack(shortPath)
        assertEquals(shortPath.size, result.size)
        assertEquals(shortPath, result)
    }

    @Test
    fun testExactlyAtThreshold_remainsUntouched() {
        val thresholdPath = (0 until 300).map { i ->
            LatLng(48.0 + (i * 0.0001), 11.0)
        }
        val result = decimateLiveTrack(thresholdPath)
        assertEquals(300, result.size)
        assertEquals(thresholdPath, result)
    }

    @Test
    fun testLongCollinearTrack_simplifiesHeadAndPreservesTail() {
        // 500 collinear points: 300 in head, 200 in tail
        val path = (0 until 500).map { i ->
            LatLng(48.0 + (i * 0.0001), 11.0)
        }
        val result = decimateLiveTrack(path)

        // The head has 300 collinear points, simplified at 3.0m should reduce to start and end (2 points)
        // Tail has 200 points preserved exactly
        assertTrue(result.size < path.size)
        val tailExpected = path.takeLast(200)
        val tailActual = result.takeLast(200)
        assertEquals(tailExpected, tailActual)

        // Head should be reduced significantly (start of track preserved)
        assertEquals(path.first().latitude, result.first().latitude, 1e-6)
        assertEquals(path.first().longitude, result.first().longitude, 1e-6)
    }

    @Test
    fun testEmptyAndSinglePointTrack() {
        val emptyPath = emptyList<LatLng>()
        assertEquals(emptyPath, decimateLiveTrack(emptyPath))

        val singlePoint = listOf(LatLng(48.0, 11.0))
        assertEquals(singlePoint, decimateLiveTrack(singlePoint))
    }
}
