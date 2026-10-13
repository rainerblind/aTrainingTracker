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

import android.location.Location
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Unit tests verifying waypoint projection and cumulative distance calculation along a route
 * (REQ-MAP-026, TST-MAP-028 Group 3).
 */
class WaypointDistanceCalculatorTest {

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCalculateWaypointDistance_projectsAccuratelyOntoStraightPolyline() {
        // Straight horizontal route from (48.0, 11.0) to (48.0, 11.02), total distance ~1487m
        val p1 = PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0)
        val p2 = PathPoint(distance = 1487.0, latLng = LatLng(48.0, 11.02), altitude = 0.0)
        val path = listOf(p1, p2)

        // Waypoint halfway along segment, slightly offset northward by ~100m
        val waypoint = RouteWaypoint(
            latLng = LatLng(48.001, 11.01),
            name = "Halfway Bench",
            type = WaypointType.POI_BENCH
        )

        val projected = WaypointDistanceCalculator.projectWaypoints(listOf(waypoint), path)

        assertEquals(1, projected.size)
        // Midpoint of 1487.0m is ~743.5m; check within 20m tolerance
        assertEquals(743.5, projected[0].distanceFromStart, 20.0)
    }

    @Test
    fun testCalculateWaypointDistance_withMultiplePoints_sortsByAscendingDistance() {
        val p1 = PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0)
        val p2 = PathPoint(distance = 1000.0, latLng = LatLng(48.0, 11.01), altitude = 0.0)
        val p3 = PathPoint(distance = 2000.0, latLng = LatLng(48.0, 11.02), altitude = 0.0)
        val path = listOf(p1, p2, p3)

        // Three waypoints out of order: at ~1500m, ~200m, and ~800m
        val wFar = RouteWaypoint(latLng = LatLng(48.0, 11.015), name = "Far")
        val wNear = RouteWaypoint(latLng = LatLng(48.0, 11.002), name = "Near")
        val wMid = RouteWaypoint(latLng = LatLng(48.0, 11.008), name = "Mid")

        val projected = WaypointDistanceCalculator.projectWaypoints(listOf(wFar, wNear, wMid), path)

        assertEquals(3, projected.size)
        assertEquals("Near", projected[0].name)
        assertEquals("Mid", projected[1].name)
        assertEquals("Far", projected[2].name)
        assertTrue(projected[0].distanceFromStart < projected[1].distanceFromStart)
        assertTrue(projected[1].distanceFromStart < projected[2].distanceFromStart)
    }

    @Test
    fun testCalculateWaypointDistance_withEmptyPathOrWaypoints_handlesGracefully() {
        val waypoint = RouteWaypoint(latLng = LatLng(48.0, 11.0), name = "Solo")

        // Empty waypoints
        val emptyWpts = WaypointDistanceCalculator.projectWaypoints(emptyList(), listOf(PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0)))
        assertTrue(emptyWpts.isEmpty())

        // Empty path
        val emptyPath = WaypointDistanceCalculator.projectWaypoints(listOf(waypoint), emptyList())
        assertEquals(1, emptyPath.size)

        // Single path point
        val singlePoint = WaypointDistanceCalculator.projectWaypoints(listOf(waypoint), listOf(PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0)))
        assertEquals(1, singlePoint.size)
        assertEquals(0.0, singlePoint[0].distanceFromStart, 0.001)
    }
}
