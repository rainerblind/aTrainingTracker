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

package com.atrainingtracker.trainingtracker.climbs

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricUtils
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying distance normalization of climb path points to base 0.0m
 * and scrubbing synchronization (REQ-UI-338, TST-UI-298).
 */
class ClimbNormalizationTest {

    @Test
    fun testNormalizedPathPoints_shiftsDistancesToBaseZero() {
        val p1 = PathPoint(distance = 27380.0, latLng = LatLng(47.100, 11.200), altitude = 600.0)
        val p2 = PathPoint(distance = 27700.0, latLng = LatLng(47.105, 11.205), altitude = 640.0)
        val p3 = PathPoint(distance = 28102.0, latLng = LatLng(47.110, 11.210), altitude = 680.0)

        val climb = Climb(
            id = 101L,
            name = "Test Route Climb",
            startLat = 47.100,
            startLng = 11.200,
            endLat = 47.110,
            endLng = 11.210,
            distanceMeters = 722.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 11.1,
            maxGradePercent = 14.0,
            category = ClimbCategory.CAT_4,
            pathPoints = listOf(p1, p2, p3)
        )

        val normalized = climb.normalizedPathPoints

        assertEquals("Must contain same number of points", 3, normalized.size)

        // Distances must be shifted relative to the start point (27380.0)
        assertEquals(0.0, normalized[0].distance, 0.001)
        assertEquals(320.0, normalized[1].distance, 0.001)
        assertEquals(722.0, normalized[2].distance, 0.001)

        // Geographical and elevation data must remain unchanged
        assertEquals(p1.latLng, normalized[0].latLng)
        assertEquals(p1.altitude, normalized[0].altitude, 0.001)

        assertEquals(p2.latLng, normalized[1].latLng)
        assertEquals(p2.altitude, normalized[1].altitude, 0.001)

        assertEquals(p3.latLng, normalized[2].latLng)
        assertEquals(p3.altitude, normalized[2].altitude, 0.001)

        // Original climb.pathPoints must remain immutable
        assertEquals(27380.0, climb.pathPoints[0].distance, 0.001)
        assertEquals(28102.0, climb.pathPoints[2].distance, 0.001)
    }

    @Test
    fun testNormalizedPathPoints_emptyListReturnsEmpty() {
        val climb = Climb(
            id = 102L,
            name = "Empty Climb",
            startLat = 47.0,
            startLng = 11.0,
            endLat = 47.0,
            endLng = 11.0,
            distanceMeters = 0.0,
            elevationGainMeters = 0.0,
            avgGradePercent = 0.0,
            maxGradePercent = 0.0,
            category = ClimbCategory.UNCATEGORIZED,
            pathPoints = emptyList()
        )

        assertTrue(climb.normalizedPathPoints.isEmpty())
    }

    @Test
    fun testNormalizedPathPoints_alreadyZeroBaseReturnsSameList() {
        val p1 = PathPoint(distance = 0.0, latLng = LatLng(47.0, 11.0), altitude = 500.0)
        val p2 = PathPoint(distance = 500.0, latLng = LatLng(47.01, 11.01), altitude = 550.0)
        val points = listOf(p1, p2)

        val climb = Climb(
            id = 103L,
            name = "Zero Base Climb",
            startLat = 47.0,
            startLng = 11.0,
            endLat = 47.01,
            endLng = 11.01,
            distanceMeters = 500.0,
            elevationGainMeters = 50.0,
            avgGradePercent = 10.0,
            maxGradePercent = 12.0,
            category = ClimbCategory.CAT_4,
            pathPoints = points
        )

        val normalized = climb.normalizedPathPoints
        assertSame("Must return same list instance if already base 0.0", points, normalized)
    }

    @Test
    fun testScrubbingLookup_findNearestPointResolvesCorrectCoordinates() {
        val p1 = PathPoint(distance = 27380.0, latLng = LatLng(47.100, 11.200), altitude = 600.0)
        val p2 = PathPoint(distance = 27700.0, latLng = LatLng(47.105, 11.205), altitude = 640.0)
        val p3 = PathPoint(distance = 28102.0, latLng = LatLng(47.110, 11.210), altitude = 680.0)

        val climb = Climb(
            id = 104L,
            name = "Scrub Test Climb",
            startLat = 47.100,
            startLng = 11.200,
            endLat = 47.110,
            endLng = 11.210,
            distanceMeters = 722.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 11.1,
            maxGradePercent = 14.0,
            category = ClimbCategory.CAT_4,
            pathPoints = listOf(p1, p2, p3)
        )

        val normalized = climb.normalizedPathPoints

        // Scrubbing at mid-distance (320m) should locate p2 exactly
        val nearest = TelemetryMetricUtils.findNearestPoint(normalized, 320.0)
        assertNotNull("Nearest point must not be null", nearest)
        assertEquals(LatLng(47.105, 11.205), nearest!!.latLng)
        assertEquals(640.0, nearest.altitude, 0.001)

        // Scrubbing near start (10m) should locate p1
        val nearestStart = TelemetryMetricUtils.findNearestPoint(normalized, 10.0)
        assertNotNull(nearestStart)
        assertEquals(LatLng(47.100, 11.200), nearestStart!!.latLng)

        // Scrubbing near summit (700m) should locate p3
        val nearestSummit = TelemetryMetricUtils.findNearestPoint(normalized, 700.0)
        assertNotNull(nearestSummit)
        assertEquals(LatLng(47.110, 11.210), nearestSummit!!.latLng)
    }
}
