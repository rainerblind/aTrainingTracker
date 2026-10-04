/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.routes

import android.location.Location
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.*

class TurnByTurnNavigationEngineTest {

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

    private fun createLocation(lat: Double, lng: Double): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lng
        return loc
    }

    @Test
    fun testNavigationLifecycle_approachingTurnNowAndPassed() {
        val summary = RouteSummary(
            id = 10L,
            externalId = "route_10",
            name = "Alpine Pass",
            description = "Test alpine route",
            isSelected = false,
            distance = 1000.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        // Straight 1000m path north
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.000, 11.000), altitude = 0.0),
            PathPoint(distance = 555.0, latLng = LatLng(47.005, 11.000), altitude = 0.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.009, 11.000), altitude = 0.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 1L,
                routeId = 10L,
                latLng = LatLng(47.005, 11.000),
                type = WaypointType.TURN_RIGHT,
                distanceFromStart = 555.0,
                name = "Turn Right onto Pass Road"
            )
        )
        val route = RouteWithPath(summary, path, waypoints)

        val engine = TurnByTurnNavigationEngine()
        engine.setActiveRoute(route)

        val config = TuningConfig(
            turnCueCountdownDistanceMeters = 150f,
            offRouteCorridorThresholdMeters = 50f
        )

        // 1. Initial position at 200m: Not approaching (555 - 200 = 355m > 150m)
        val loc1 = createLocation(47.0018, 11.000)
        val state1 = engine.onLocationChanged(loc1, config)
        assertNotNull(state1.upcomingCue)
        assertEquals(TurnDirection.RIGHT, state1.upcomingCue?.direction)
        assertFalse("Should not be approaching at > 150m", state1.isApproaching)
        assertFalse("Should not be turn now at > 25m", state1.isTurnNow)
        assertFalse("Should be on route", state1.isOffRoute)

        // 2. Approaching at 450m: (555 - 450 = 105m <= 150m)
        val loc2 = createLocation(47.00405, 11.000)
        val state2 = engine.onLocationChanged(loc2, config)
        assertTrue("Should be approaching at 105m", state2.isApproaching)
        assertFalse("Should not be turn now at 105m", state2.isTurnNow)

        // 3. Turn now at 540m: (555 - 540 = 15m <= 25m)
        val loc3 = createLocation(47.00486, 11.000)
        val state3 = engine.onLocationChanged(loc3, config)
        assertTrue("Should be approaching", state3.isApproaching)
        assertTrue("Should be turn now at 15m", state3.isTurnNow)

        // 4. Passed at 585m (> 555 + 20m)
        val loc4 = createLocation(47.0053, 11.000)
        val state4 = engine.onLocationChanged(loc4, config)
        assertNull("Upcoming cue should be null after passing all cues", state4.upcomingCue)
        assertEquals(1, state4.passedCuesCount)
    }

    @Test
    fun testOffRouteCorridorEvaluation_3FixDebounceAndRecovery() {
        val summary = RouteSummary(
            id = 20L,
            externalId = "route_20",
            name = "Corridor Test",
            description = "Test corridor route",
            isSelected = false,
            distance = 1110.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.000, 11.000), altitude = 0.0),
            PathPoint(distance = 1110.0, latLng = LatLng(47.010, 11.000), altitude = 0.0)
        )
        val route = RouteWithPath(summary, path, emptyList())

        val engine = TurnByTurnNavigationEngine()
        engine.setActiveRoute(route)

        val config = TuningConfig(offRouteCorridorThresholdMeters = 50f)

        // Deviate ~80m east (lng = 11.001 at 47 deg latitude is ~75m)
        val offLoc = createLocation(47.005, 11.0011)

        // Fix 1: cross-track > 50m, but debounced (needs 3 consecutive fixes)
        val s1 = engine.onLocationChanged(offLoc, config)
        assertFalse("Should not trigger off route on 1st fix", s1.isOffRoute)

        // Fix 2:
        val s2 = engine.onLocationChanged(offLoc, config)
        assertFalse("Should not trigger off route on 2nd fix", s2.isOffRoute)

        // Fix 3: Triggers off-route
        val s3 = engine.onLocationChanged(offLoc, config)
        assertTrue("Should trigger off route on 3rd consecutive fix", s3.isOffRoute)

        // Recovery: Move back on route (cross-track <= 30m)
        val onLoc = createLocation(47.005, 11.0001)
        val s4 = engine.onLocationChanged(onLoc, config)
        assertFalse("Should recover when back on route", s4.isOffRoute)
    }
}
