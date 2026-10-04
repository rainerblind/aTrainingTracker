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
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying automated climb detection, micro-dip tolerance, and category scoring
 * (REQ-MAP-027, TST-MAP-029 Group 1).
 */
class ClimbDetectorTest {

    @Test
    fun detectClimbs_detectsValidClimbAndCalculatesCategory() {
        // Construct a 2000m climb with 120m gain (6% average gradient)
        // Score = 2000 * 6 = 12000 -> CAT_4
        val points = mutableListOf<PathPoint>()
        val startAlt = 200.0
        val totalDist = 2000.0
        val gain = 120.0
        val steps = 20

        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val d = fraction * totalDist
            val alt = startAlt + (fraction * gain)
            points.add(
                PathPoint(
                    distance = d,
                    latLng = LatLng(48.0 + (fraction * 0.01), 11.0 + (fraction * 0.01)),
                    altitude = alt
                )
            )
        }

        val climbs = ClimbDetector.detectClimbs(points)

        assertEquals(1, climbs.size)
        val climb = climbs[0]
        assertEquals(2000.0, climb.distanceMeters, 1.0)
        assertEquals(120.0, climb.elevationGainMeters, 1.0)
        assertEquals(6.0, climb.avgGradePercent, 0.1)
        assertEquals(ClimbCategory.CAT_4, climb.category)
        assertTrue(climb.name.contains("2.0 km"))
        assertEquals(21, climb.pathPoints.size)
    }

    @Test
    fun detectClimbs_ignoresFlatOrDescendingTracks() {
        // Flat track of 5000m at constant 150m altitude
        val flatPoints = (0..50).map { i ->
            PathPoint(
                distance = i * 100.0,
                latLng = LatLng(48.0 + (i * 0.001), 11.0),
                altitude = 150.0
            )
        }
        val flatClimbs = ClimbDetector.detectClimbs(flatPoints)
        assertTrue("Flat track must yield 0 climbs", flatClimbs.isEmpty())

        // Descending track: 2000m dropping from 400m to 200m
        val descPoints = (0..20).map { i ->
            PathPoint(
                distance = i * 100.0,
                latLng = LatLng(48.0 + (i * 0.001), 11.0),
                altitude = 400.0 - (i * 10.0)
            )
        }
        val descClimbs = ClimbDetector.detectClimbs(descPoints)
        assertTrue("Descending track must yield 0 climbs", descClimbs.isEmpty())
    }

    @Test
    fun detectClimbs_toleratesMicroDipsWithinClimb() {
        // 1500m climb with a small 5m dip over 50m in the middle
        val points = mutableListOf<PathPoint>()
        // Part 1: 0 to 600m ascending from 100m to 150m (gain 50m)
        points.add(PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 100.0))
        points.add(PathPoint(distance = 600.0, latLng = LatLng(48.005, 11.0), altitude = 150.0))
        // Small dip: 650m dropping 5m to 145m
        points.add(PathPoint(distance = 650.0, latLng = LatLng(48.006, 11.0), altitude = 145.0))
        // Part 2: resuming ascent to 1500m ending at 210m (net gain 110m)
        points.add(PathPoint(distance = 1000.0, latLng = LatLng(48.009, 11.0), altitude = 175.0))
        points.add(PathPoint(distance = 1500.0, latLng = LatLng(48.014, 11.0), altitude = 210.0))

        val climbs = ClimbDetector.detectClimbs(points)

        // Must retain 1 continuous climb encompassing the entire 1500m ascent
        assertEquals(1, climbs.size)
        assertEquals(1500.0, climbs[0].distanceMeters, 1.0)
        assertEquals(110.0, climbs[0].elevationGainMeters, 1.0)
        assertEquals(5, climbs[0].pathPoints.size)
    }

    @Test
    fun detectClimbs_assignsCorrectClimbCategories() {
        assertEquals(ClimbCategory.HC, ClimbCategory.fromScore(85000.0))
        assertEquals(ClimbCategory.CAT_1, ClimbCategory.fromScore(65000.0))
        assertEquals(ClimbCategory.CAT_2, ClimbCategory.fromScore(35000.0))
        assertEquals(ClimbCategory.CAT_3, ClimbCategory.fromScore(18000.0))
        assertEquals(ClimbCategory.CAT_4, ClimbCategory.fromScore(9000.0))
        assertEquals(ClimbCategory.UNCATEGORIZED, ClimbCategory.fromScore(5000.0))
    }
}
