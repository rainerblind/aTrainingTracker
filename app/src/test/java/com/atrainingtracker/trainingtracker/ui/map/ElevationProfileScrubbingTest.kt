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
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test for ElevationProfile point resolution during scrubbing and graceful sensor fallback (REQ-UI-201, TST-UI-155.4).
 */
class ElevationProfileScrubbingTest {

    private val trackPoints = listOf(
        PathPoint(
            distance = 0.0,
            altitude = 100.0,
            latLng = LatLng(48.0, 11.0),
            timeSec = 0L,
            hr = 120,
            power = 150,
            speedMps = 5.0,
            slope = 0.0
        ),
        PathPoint(
            distance = 1000.0,
            altitude = 120.0,
            latLng = LatLng(48.01, 11.01),
            timeSec = 120L,
            hr = 140,
            power = 200,
            speedMps = 8.3,
            slope = 2.0
        ),
        PathPoint(
            distance = 2000.0,
            altitude = 150.0,
            latLng = LatLng(48.02, 11.02),
            timeSec = 240L,
            hr = null, // sensor dropped out
            power = null,
            speedMps = 8.5,
            slope = 3.0
        ),
        PathPoint(
            distance = 3000.0,
            altitude = 130.0,
            latLng = LatLng(48.03, 11.03),
            timeSec = 360L,
            hr = 155,
            power = 220,
            speedMps = null,
            slope = null
        )
    )

    @Test
    fun testResolvePointByDistance() {
        // Scrub at 500m -> nearest preceding point is index 0 (0m)
        val idx1 = trackPoints.indexOfLast { it.distance <= 500.0 }.coerceAtLeast(0)
        assertEquals(0, idx1)
        assertEquals(0.0, trackPoints[idx1].distance, 0.001)

        // Scrub at 1500m -> nearest preceding point is index 1 (1000m)
        val idx2 = trackPoints.indexOfLast { it.distance <= 1500.0 }.coerceAtLeast(0)
        assertEquals(1, idx2)
        assertEquals(1000.0, trackPoints[idx2].distance, 0.001)
        assertEquals(140, trackPoints[idx2].hr)
        assertEquals(200, trackPoints[idx2].power)

        // Scrub at 2500m -> index 2 (2000m) with dropped sensors
        val idx3 = trackPoints.indexOfLast { it.distance <= 2500.0 }.coerceAtLeast(0)
        assertEquals(2, idx3)
        assertNull(trackPoints[idx3].hr)
        assertNull(trackPoints[idx3].power)
        assertEquals(8.5, trackPoints[idx3].speedMps!!, 0.001)
    }

    @Test
    fun testResolvePointByTime() {
        // Scrub at 180s -> nearest preceding point is index 1 (120s)
        val idx1 = trackPoints.indexOfLast { it.timeSec.toDouble() <= 180.0 }.coerceAtLeast(0)
        assertEquals(1, idx1)
        assertEquals(120L, trackPoints[idx1].timeSec)

        // Scrub at 300s -> nearest preceding point is index 2 (240s)
        val idx2 = trackPoints.indexOfLast { it.timeSec.toDouble() <= 300.0 }.coerceAtLeast(0)
        assertEquals(2, idx2)
        assertEquals(240L, trackPoints[idx2].timeSec)
    }

    @Test
    fun testTelemetryGracefulNullHandling() {
        val legacyPoint = PathPoint(
            distance = 500.0,
            altitude = 110.0,
            latLng = LatLng(48.0, 11.0)
        )

        // Missing sensors should gracefully be null, not cause exceptions
        assertNull(legacyPoint.hr)
        assertNull(legacyPoint.power)
        assertNull(legacyPoint.speedMps)
        assertNull(legacyPoint.slope)
        assertEquals(0L, legacyPoint.timeSec)
    }
}
