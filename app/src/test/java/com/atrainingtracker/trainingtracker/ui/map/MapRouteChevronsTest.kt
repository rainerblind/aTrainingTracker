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
 * Unit tests verifying directional chevron geometry and bearing calculations (REQ-MAP-023 / TST-MAP-025 / ATT-1841).
 */
class MapRouteChevronsTest {

    @Test
    fun testCalculateBearing_cardinalDirections() {
        val origin = LatLng(0.0, 0.0)
        val north = LatLng(1.0, 0.0)
        val east = LatLng(0.0, 1.0)
        val south = LatLng(-1.0, 0.0)
        val west = LatLng(0.0, -1.0)

        // North: 0 deg
        val bearingNorth = calculateBearing(origin, north)
        assertEquals(0.0, bearingNorth, 0.1)

        // East: 90 deg
        val bearingEast = calculateBearing(origin, east)
        assertEquals(90.0, bearingEast, 0.1)

        // South: 180 deg
        val bearingSouth = calculateBearing(origin, south)
        assertEquals(180.0, bearingSouth, 0.1)

        // West: 270 deg
        val bearingWest = calculateBearing(origin, west)
        assertEquals(270.0, bearingWest, 0.1)
    }

    @Test
    fun testChevronWindowing_safelySamplesPolylinePoints() {
        val points = (0..50).map { i ->
            PathPoint(
                distance = i * 10.0,
                latLng = LatLng(48.0 + (i * 0.001), 11.0 + (i * 0.001)),
                altitude = 500.0 + i
            )
        }

        val step = 10
        val sampledPairs = points.windowed(2, step)
        assertTrue(sampledPairs.isNotEmpty())

        sampledPairs.forEach { pair ->
            assertEquals(2, pair.size)
            val p1 = pair[0].latLng
            val p2 = pair[1].latLng
            val bearing = calculateBearing(p1, p2).toFloat()
            assertTrue(bearing in 0.0f..360.0f)
        }
    }
}
