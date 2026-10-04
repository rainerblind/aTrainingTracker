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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class RouteDivergenceDetectorTest {

    private fun pt(lat: Double, lng: Double, dist: Double = 0.0) =
        PathPoint(dist, LatLng(lat, lng), 400.0)

    private fun dummySummary(id: Long, name: String, dist: Double = 10000.0, elev: Double = 150.0) = RouteSummary(
        id = id,
        externalId = "ext_$id",
        name = name,
        description = "",
        distance = dist,
        elevationGain = elev,
        bSportType = BSportType.BIKE,
        isSelected = false,
        source = RouteSource.LOCAL_GPX
    )

    @Test
    fun classifyRelativeDirection_mapsAngleRangesAccurately() {
        assertEquals(ForkDirection.LEFT, RouteDivergenceDetector.classifyRelativeDirection(-45.0))
        assertEquals(ForkDirection.LEFT, RouteDivergenceDetector.classifyRelativeDirection(-21.0))
        assertEquals(ForkDirection.STRAIGHT, RouteDivergenceDetector.classifyRelativeDirection(-10.0))
        assertEquals(ForkDirection.STRAIGHT, RouteDivergenceDetector.classifyRelativeDirection(0.0))
        assertEquals(ForkDirection.STRAIGHT, RouteDivergenceDetector.classifyRelativeDirection(15.0))
        assertEquals(ForkDirection.RIGHT, RouteDivergenceDetector.classifyRelativeDirection(25.0))
        assertEquals(ForkDirection.RIGHT, RouteDivergenceDetector.classifyRelativeDirection(60.0))
    }

    @Test
    fun pointAtDistance_interpolatesAccurately() {
        val path = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5100, 9.0000, 1000.0)
        )
        val midPt = RouteDivergenceDetector.pointAtDistance(path, 500.0)
        assertEquals(48.5050, midPt.latitude, 0.0001)
        assertEquals(9.0000, midPt.longitude, 0.0001)
    }

    @Test
    fun detectDivergence_twoSplittingRoutes_findsAccurateCoordinateAndDistance() {
        // Shared corridor heading North for 1000m (48.5000 to 48.5090)
        // Divergence at ~1000m: Route 1 turns Left (Northwest), Route 2 turns Right (Northeast)
        val sharedPoints = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5045, 9.0000, 500.0),
            pt(48.5090, 9.0000, 1000.0)
        )
        val route1Path = sharedPoints + listOf(
            pt(48.5120, 8.9950, 1500.0),
            pt(48.5150, 8.9900, 2000.0)
        )
        val route2Path = sharedPoints + listOf(
            pt(48.5120, 9.0050, 1500.0),
            pt(48.5150, 9.0100, 2000.0)
        )

        val route1 = RouteWithPath(dummySummary(1L, "Left Route", 38000.0, 420.0), route1Path)
        val route2 = RouteWithPath(dummySummary(2L, "Right Route", 65000.0, 680.0), route2Path)

        // Athlete at 800m (approaching fork within 200m)
        val athletePos = LatLng(48.5072, 9.0000)
        val state = RouteDivergenceDetector.detectDivergence(listOf(route1, route2), athletePos)

        assertNotNull(state)
        requireNotNull(state)
        assertTrue(state.isApproaching)
        assertTrue("Distance to fork should be ~200m, was ${state.distanceToForkMeters}",
            state.distanceToForkMeters in 150.0..250.0)

        assertEquals(2, state.branches.size)
        val leftBranch = state.branches.find { it.direction == ForkDirection.LEFT }
        val rightBranch = state.branches.find { it.direction == ForkDirection.RIGHT }

        assertNotNull(leftBranch)
        assertNotNull(rightBranch)
        assertEquals(1L, leftBranch?.routeId)
        assertEquals(2L, rightBranch?.routeId)
    }

    @Test
    fun alertWindow_beyond300Meters_returnsNull() {
        // Fork is at 1000m
        val sharedPoints = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5090, 9.0000, 1000.0)
        )
        val route1 = RouteWithPath(dummySummary(1L, "Left"), sharedPoints + listOf(pt(48.5150, 8.9900, 2000.0)))
        val route2 = RouteWithPath(dummySummary(2L, "Right"), sharedPoints + listOf(pt(48.5150, 9.0100, 2000.0)))

        // Athlete at 400m (600m from fork, beyond 300m window)
        val athletePos = LatLng(48.5036, 9.0000)
        val state = RouteDivergenceDetector.detectDivergence(listOf(route1, route2), athletePos)

        assertNull(state)
    }
}
