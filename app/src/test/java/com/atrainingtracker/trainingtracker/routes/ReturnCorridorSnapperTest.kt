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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ReturnCorridorSnapperTest {

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
        unmockkStatic(Location::class)
    }

    private fun createTestRoute(id: Long, name: String, points: List<PathPoint>): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = "",
            isSelected = false,
            distance = points.lastOrNull()?.distance ?: 0.0,
            elevationGain = 100.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        return RouteWithPath(summary = summary, path = points)
    }

    @Test
    fun snapActiveRoute_forwardProgress_computesRemainingDistanceAndClimb() {
        val p1 = PathPoint(0.0, LatLng(48.0, 9.0), 300.0)
        val p2 = PathPoint(5000.0, LatLng(48.05, 9.0), 400.0) // +100m climb
        val p3 = PathPoint(10000.0, LatLng(48.10, 9.0), 550.0) // +150m climb
        val route = createTestRoute(1L, "Alb Trail", listOf(p1, p2, p3))

        // Athlete is at p2 (5000m along route)
        val snap = ReturnCorridorSnapper.snapActiveRoute(
            currentLat = 48.05,
            currentLng = 9.0,
            route = route,
            isReverseReturn = false
        )

        assertNotNull(snap)
        assertEquals("Alb Trail", snap.destinationName)
        assertFalse(snap.isReverseReturn)
        assertEquals(5000.0, snap.remainingDistanceMeters, 10.0) // 10000 - 5000 = 5000m
        assertEquals(150.0, snap.remainingClimbMeters, 1.0) // from p2 to p3 is 550 - 400 = 150m
    }

    @Test
    fun snapActiveRoute_reverseReturn_computesReverseDistanceAndClimb() {
        val p1 = PathPoint(0.0, LatLng(48.0, 9.0), 300.0)
        val p2 = PathPoint(5000.0, LatLng(48.05, 9.0), 400.0) // +100m climb on forward (descent on reverse)
        val p3 = PathPoint(10000.0, LatLng(48.10, 9.0), 350.0) // -50m on forward (ascent +50m on reverse)
        val route = createTestRoute(1L, "Alb Loop", listOf(p1, p2, p3))

        // Athlete is at p3 (end of route) and reverses back
        val snap = ReturnCorridorSnapper.snapActiveRoute(
            currentLat = 48.10,
            currentLng = 9.0,
            route = route,
            isReverseReturn = true
        )

        assertNotNull(snap)
        assertTrue(snap.isReverseReturn)
        assertEquals(10000.0, snap.remainingDistanceMeters, 10.0)
        // Reverse climb: from p3 (350m) to p2 (400m) is +50m climb. From p2 (400m) to p1 (300m) is downhill.
        assertEquals(50.0, snap.remainingClimbMeters, 1.0)
    }

    @Test
    fun snapCandidateRoute_findsRouteTerminatingAtHome() {
        val home = HomeDestination(
            id = 42L,
            name = "Home Base",
            latLng = LatLng(48.0, 9.0),
            altitude = 300.0
        )

        val p1 = PathPoint(0.0, LatLng(48.2, 9.0), 500.0)
        val p2 = PathPoint(10000.0, LatLng(48.1, 9.0), 400.0)
        val p3 = PathPoint(20000.0, LatLng(48.0001, 9.0001), 305.0) // ends ~15m from home!
        val savedRoute = createTestRoute(99L, "Commute Home", listOf(p1, p2, p3))

        // Athlete is riding near p2
        val snap = ReturnCorridorSnapper.snapToCorridor(
            currentLat = 48.1001,
            currentLng = 9.0,
            currentAltitude = 400.0,
            activeRoute = null,
            homeDestination = home,
            savedRoutes = listOf(savedRoute)
        )

        assertNotNull(snap)
        assertTrue(snap!!.isHomeDestination)
        assertEquals("Home Base", snap.destinationName)
        assertEquals(99L, snap.snappedRouteId)
        assertEquals(10000.0, snap.remainingDistanceMeters, 100.0)
    }

    @Test
    fun snapDirectGeodesic_fallbackToHome_whenNoRoutesMatch() {
        val home = HomeDestination(
            id = 42L,
            name = "Zu Hause",
            latLng = LatLng(48.0, 9.0),
            altitude = 350.0
        )

        val snap = ReturnCorridorSnapper.snapToCorridor(
            currentLat = 48.05,
            currentLng = 9.0,
            currentAltitude = 250.0,
            activeRoute = null,
            homeDestination = home,
            savedRoutes = emptyList()
        )

        assertNotNull(snap)
        assertTrue(snap!!.isHomeDestination)
        assertEquals("Zu Hause", snap.destinationName)
        assertNull(snap.snappedRouteId)
        assertTrue(snap.remainingDistanceMeters > 5000.0)
        assertEquals(100.0, snap.remainingClimbMeters, 0.1) // 350 - 250 = 100m
    }

    @Test
    fun snapToCorridor_emptyReturnsNull_whenNoHomeAndNoRoutes() {
        val snap = ReturnCorridorSnapper.snapToCorridor(
            currentLat = 48.0,
            currentLng = 9.0,
            activeRoute = null,
            homeDestination = null,
            savedRoutes = emptyList()
        )
        assertNull(snap)
    }
}
