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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying RouteAutoDetector behavior (REQ-MAP-024 / TST-MAP-026 / ATT-1835).
 */
class RouteAutoDetectorTest {

    private lateinit var detector: RouteAutoDetector

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        detector = RouteAutoDetector()
    }

    private fun createLocation(lat: Double, lng: Double, bearing: Float = 0f, speed: Float = 5f): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lng
        every { loc.bearing } returns bearing
        every { loc.hasBearing() } returns (bearing != 0f)
        every { loc.speed } returns speed
        return loc
    }

    private fun createSampleRoute(
        id: Long = 1L,
        name: String = "River Path",
        points: List<LatLng> = listOf(
            LatLng(48.137154, 11.576124),
            LatLng(48.138000, 11.577000),
            LatLng(48.139000, 11.578000)
        )
    ): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = "Test description",
            isSelected = false,
            distance = 5000.0,
            elevationGain = 50.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val path = points.mapIndexed { idx, latLng ->
            PathPoint(distance = (idx * 50).toDouble(), latLng = latLng, altitude = 500.0)
        }
        return RouteWithPath(summary = summary, path = path)
    }

    @Test
    fun testDetectsRouteNearStartWithMatchingHeading() {
        val route = createSampleRoute()
        // Close to start point (48.137154, 11.576124), heading northeast (~38 degrees)
        val loc = createLocation(48.137160, 11.576130, bearing = 38f)

        val candidate = detector.evaluate(loc, listOf(route), currentlyActiveRouteId = null)

        assertNotNull("Should detect candidate near start point", candidate)
        assertEquals(route.summary.id, candidate?.summary?.id)
    }

    @Test
    fun testIgnoresRouteWhenFarAway() {
        val route = createSampleRoute()
        // Far away location (> 10 km)
        val loc = createLocation(49.000000, 12.000000, bearing = 38f)

        val candidate = detector.evaluate(loc, listOf(route), currentlyActiveRouteId = null)

        assertNull("Should not detect candidate when user is far away", candidate)
    }

    @Test
    fun testIgnoresActiveRoute() {
        val route = createSampleRoute(id = 42L)
        val loc = createLocation(48.137160, 11.576130, bearing = 38f)

        val candidate = detector.evaluate(loc, listOf(route), currentlyActiveRouteId = 42L)

        assertNull("Should not suggest a route that is already active", candidate)
    }

    @Test
    fun testDismissSuppressionPreventsImmediateReDetection() {
        val route = createSampleRoute(id = 100L)
        val loc = createLocation(48.137160, 11.576130, bearing = 38f)

        // Dismiss the route
        detector.dismissRoute(100L)

        val candidate = detector.evaluate(loc, listOf(route), currentlyActiveRouteId = null)
        assertNull("Dismissed route should be suppressed during cooldown", candidate)
    }

    @Test
    fun testEmptyRouteListReturnsNull() {
        val loc = createLocation(48.137160, 11.576130, bearing = 38f)
        val candidate = detector.evaluate(loc, emptyList<RouteWithPath>(), currentlyActiveRouteId = null)
        assertNull(candidate)
    }
}
