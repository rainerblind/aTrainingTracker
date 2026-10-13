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

package com.atrainingtracker.trainingtracker.ui.routes

import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying multi-stage proximity ranking and tie-breaking for RouteProximityRanker
 * (REQ-MAP-024 / TST-MAP-026 / ATT-1835).
 */
class RouteProximityRankerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    private fun createRoute(
        id: Long,
        name: String,
        sportType: BSportType = BSportType.BIKE,
        distance: Double = 10000.0,
        syncedAt: Long = 1000L,
        startLat: Double = 48.137,
        startLng: Double = 11.576,
        secondLat: Double = 48.138,
        secondLng: Double = 11.577
    ): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = "Description for $name",
            isSelected = false,
            distance = distance,
            elevationGain = 100.0,
            bSportType = sportType,
            source = RouteSource.LOCAL_GPX,
            syncedAt = syncedAt
        )
        val path = listOf(
            PathPoint(0.0, LatLng(startLat, startLng), 500.0),
            PathPoint(200.0, LatLng(secondLat, secondLng), 505.0)
        )
        return RouteWithPath(summary = summary, path = path)
    }

    @Test
    fun testTier1_proximityGrouping() {
        // User at Munich city center (48.137, 11.576)
        val userLocation = LatLng(48.137, 11.576)

        // Route A starts 50m away (< 250m)
        val routeNear = createRoute(1L, "Near Route", startLat = 48.1372, startLng = 11.5762)
        // Route B starts 2000m away (> 250m)
        val routeFar = createRoute(2L, "Far Route", startLat = 48.155, startLng = 11.576)

        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(routeFar, routeNear),
            currentLocation = userLocation,
            currentBearing = null,
            activeSport = null
        )

        assertEquals("Near route (<250m) should rank before far route (>250m)", 1L, result[0].summary.id)
        assertEquals(2L, result[1].summary.id)
    }

    @Test
    fun testTier2_sportProfileMatching() {
        val homeLocation = LatLng(48.137, 11.576)

        // 4 routes starting at the exact same coordinates (Home Hub)
        val runRoute1 = createRoute(1L, "Morning Run 5k", sportType = BSportType.RUN, startLat = 48.137, startLng = 11.576)
        val runRoute2 = createRoute(2L, "Trail Run 10k", sportType = BSportType.RUN, startLat = 48.137, startLng = 11.576)
        val bikeRoute1 = createRoute(3L, "Road Loop 40k", sportType = BSportType.BIKE, startLat = 48.137, startLng = 11.576)
        val bikeRoute2 = createRoute(4L, "Gravel Ride 60k", sportType = BSportType.BIKE, startLat = 48.137, startLng = 11.576)

        // Active sport is BIKE
        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(runRoute1, runRoute2, bikeRoute1, bikeRoute2),
            currentLocation = homeLocation,
            currentBearing = null,
            activeSport = BSportType.BIKE
        )

        // The first two routes must be bike routes
        assertTrue("First route should be BIKE", result[0].summary.bSportType == BSportType.BIKE)
        assertTrue("Second route should be BIKE", result[1].summary.bSportType == BSportType.BIKE)
        assertTrue("Third route should be RUN", result[2].summary.bSportType == BSportType.RUN)
        assertTrue("Fourth route should be RUN", result[3].summary.bSportType == BSportType.RUN)
    }

    @Test
    fun testTier3_headingMovementAlignment() {
        val homeLocation = LatLng(48.137, 11.576)

        // Route East: start (48.137, 11.576) -> departs East (48.137, 11.585) (~90 deg)
        val routeEast = createRoute(
            1L, "East Loop",
            sportType = BSportType.BIKE,
            startLat = 48.137, startLng = 11.576,
            secondLat = 48.137, secondLng = 11.585
        )

        // Route West: start (48.137, 11.576) -> departs West (48.137, 11.565) (~270 deg)
        val routeWest = createRoute(
            2L, "West Loop",
            sportType = BSportType.BIKE,
            startLat = 48.137, startLng = 11.576,
            secondLat = 48.137, secondLng = 11.565
        )

        // Athlete moves East (bearing = 90 deg)
        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(routeWest, routeEast),
            currentLocation = homeLocation,
            currentBearing = 90f,
            activeSport = BSportType.BIKE
        )

        assertEquals("East Loop should rank first when moving East", 1L, result[0].summary.id)
        assertEquals(2L, result[1].summary.id)
    }

    @Test
    fun testTier4_recencyAndFrequency() {
        val homeLocation = LatLng(48.137, 11.576)

        // Two identical routes in proximity and sport, Route A synced yesterday (higher timestamp), Route B 6 months ago
        val routeRecent = createRoute(1L, "Regular Route", syncedAt = 200000L, startLat = 48.137, startLng = 11.576)
        val routeOld = createRoute(2L, "Old Route", syncedAt = 5000L, startLat = 48.137, startLng = 11.576)

        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(routeOld, routeRecent),
            currentLocation = homeLocation,
            currentBearing = null,
            activeSport = BSportType.BIKE
        )

        assertEquals("More recent route should rank first", 1L, result[0].summary.id)
        assertEquals(2L, result[1].summary.id)
    }

    @Test
    fun testTier5_fallbackSorting() {
        val homeLocation = LatLng(48.137, 11.576)

        // Two identical routes in proximity, sport, heading, recency; one is shorter (5km vs 15km)
        val routeShort = createRoute(1L, "Short Route", distance = 5000.0, syncedAt = 1000L, startLat = 48.137, startLng = 11.576)
        val routeLong = createRoute(2L, "Long Route", distance = 15000.0, syncedAt = 1000L, startLat = 48.137, startLng = 11.576)

        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(routeLong, routeShort),
            currentLocation = homeLocation,
            currentBearing = null,
            activeSport = BSportType.BIKE
        )

        assertEquals("Shorter route should rank first in fallback tier", 1L, result[0].summary.id)
        assertEquals(2L, result[1].summary.id)
    }

    @Test
    fun testNullLocationFallback() {
        val route1 = createRoute(1L, "Route A", syncedAt = 1000L, distance = 5000.0)
        val route2 = createRoute(2L, "Route B", syncedAt = 5000L, distance = 2000.0)

        // Null location: should sort by recency descending (Route B has syncedAt 5000L > 1000L)
        val result = RouteProximityRanker.rankRoutes(
            routes = listOf(route1, route2),
            currentLocation = null,
            currentBearing = null,
            activeSport = null
        )

        assertEquals(2, result.size)
        assertEquals(2L, result[0].summary.id)
        assertEquals(1L, result[1].summary.id)
    }

    @Test
    fun testFilterAndRankRoutes_filtersStrictlyWithinRadius() {
        val userLocation = LatLng(48.137, 11.576)

        // Route A starts ~400m away (lat + 0.0036 is ~400m)
        val routeNear = createRoute(1L, "Route Near", syncedAt = 1000L, startLat = 48.1406, startLng = 11.576)
        // Route B starts ~800m away
        val routeMid = createRoute(2L, "Route Mid", syncedAt = 2000L, startLat = 48.1442, startLng = 11.576)
        // Route C starts ~1500m away (> 1000m)
        val routeFar = createRoute(3L, "Route Far", syncedAt = 3000L, startLat = 48.1505, startLng = 11.576)

        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(routeFar, routeMid, routeNear),
            currentLocation = userLocation,
            radiusMeters = 1000.0f
        )

        assertEquals("Should only include routes within 1000m radius", 2, result.size)
        assertTrue(result.none { it.summary.id == 3L })
    }

    @Test
    fun testFilterAndRankRoutes_sortsByRecencyDescending() {
        val userLocation = LatLng(48.137, 11.576)

        // Route A is closer (~300m) but older syncedAt (1000L)
        val routeOlder = createRoute(1L, "Older Route", syncedAt = 1000L, startLat = 48.1397, startLng = 11.576)
        // Route B is farther (~800m) but newer syncedAt (5000L)
        val routeNewer = createRoute(2L, "Newer Route", syncedAt = 5000L, startLat = 48.1442, startLng = 11.576)

        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(routeOlder, routeNewer),
            currentLocation = userLocation,
            radiusMeters = 1000.0f
        )

        assertEquals(2, result.size)
        assertEquals("Most recently ridden route must be first (Zuletzt gefahren)", 2L, result[0].summary.id)
        assertEquals(1L, result[1].summary.id)
    }

    @Test
    fun testFilterAndRankRoutes_nullLocation_returnsEmptyList() {
        val route = createRoute(1L, "Any Route", syncedAt = 5000L)
        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(route),
            currentLocation = null,
            radiusMeters = 1000.0f
        )
        assertTrue("Null location must return empty list as distance cannot be determined", result.isEmpty())
    }

    @Test
    fun testFilterAndRankRoutes_customRadius() {
        val userLocation = LatLng(48.137, 11.576)
        // Route at ~700m
        val route = createRoute(1L, "Route 700m", startLat = 48.1433, startLng = 11.576)

        val resultSmallRadius = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(route),
            currentLocation = userLocation,
            radiusMeters = 500.0f
        )
        assertTrue("Route at 700m should be excluded for 500m radius", resultSmallRadius.isEmpty())

        val resultLargeRadius = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(route),
            currentLocation = userLocation,
            radiusMeters = 1000.0f
        )
        assertEquals(1, resultLargeRadius.size)
    }

    @Test
    fun testFilterAndRankRoutes_filtersBySportType_bikeActive() {
        val userLocation = LatLng(48.137, 11.576)
        val bikeRoute = createRoute(1L, "Bike Route", sportType = BSportType.BIKE, startLat = 48.138, startLng = 11.576)
        val runRoute = createRoute(2L, "Run Route", sportType = BSportType.RUN, startLat = 48.138, startLng = 11.576)
        val unknownRoute = createRoute(3L, "Generic Route", sportType = BSportType.UNKNOWN, startLat = 48.138, startLng = 11.576)

        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(bikeRoute, runRoute, unknownRoute),
            currentLocation = userLocation,
            radiusMeters = 1000.0f,
            activeSport = BSportType.BIKE
        )

        assertEquals(2, result.size)
        assertTrue("Bike route must be included", result.any { it.summary.id == 1L })
        assertTrue("Untagged/Unknown route must be included as fallback", result.any { it.summary.id == 3L })
        assertTrue("Run route must be excluded when active sport is BIKE", result.none { it.summary.id == 2L })
    }

    @Test
    fun testFilterAndRankRoutes_filtersBySportType_runActive() {
        val userLocation = LatLng(48.137, 11.576)
        val bikeRoute = createRoute(1L, "Bike Route", sportType = BSportType.BIKE, startLat = 48.138, startLng = 11.576)
        val runRoute = createRoute(2L, "Run Route", sportType = BSportType.RUN, startLat = 48.138, startLng = 11.576)
        val unknownRoute = createRoute(3L, "Generic Route", sportType = BSportType.UNKNOWN, startLat = 48.138, startLng = 11.576)

        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(bikeRoute, runRoute, unknownRoute),
            currentLocation = userLocation,
            radiusMeters = 1000.0f,
            activeSport = BSportType.RUN
        )

        assertEquals(2, result.size)
        assertTrue("Run route must be included", result.any { it.summary.id == 2L })
        assertTrue("Untagged/Unknown route must be included as fallback", result.any { it.summary.id == 3L })
        assertTrue("Bike route must be excluded when active sport is RUN", result.none { it.summary.id == 1L })
    }

    @Test
    fun testFilterAndRankRoutes_filtersBySportType_unknownActive_returnsAll() {
        val userLocation = LatLng(48.137, 11.576)
        val bikeRoute = createRoute(1L, "Bike Route", sportType = BSportType.BIKE, startLat = 48.138, startLng = 11.576)
        val runRoute = createRoute(2L, "Run Route", sportType = BSportType.RUN, startLat = 48.138, startLng = 11.576)
        val unknownRoute = createRoute(3L, "Generic Route", sportType = BSportType.UNKNOWN, startLat = 48.138, startLng = 11.576)

        val result = RouteProximityRanker.filterAndRankRoutes(
            routes = listOf(bikeRoute, runRoute, unknownRoute),
            currentLocation = userLocation,
            radiusMeters = 1000.0f,
            activeSport = BSportType.UNKNOWN
        )

        assertEquals("When active sport is UNKNOWN, all routes within radius must be included", 3, result.size)
    }

    @Test
    fun testMatchesSport_invariants() {
        // null active sport allows everything
        assertTrue(RouteProximityRanker.matchesSport(BSportType.BIKE, null))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.RUN, null))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.UNKNOWN, null))

        // UNKNOWN active sport allows everything
        assertTrue(RouteProximityRanker.matchesSport(BSportType.BIKE, BSportType.UNKNOWN))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.RUN, BSportType.UNKNOWN))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.UNKNOWN, BSportType.UNKNOWN))

        // BIKE active sport
        assertTrue(RouteProximityRanker.matchesSport(BSportType.BIKE, BSportType.BIKE))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.UNKNOWN, BSportType.BIKE))
        org.junit.Assert.assertFalse(RouteProximityRanker.matchesSport(BSportType.RUN, BSportType.BIKE))

        // RUN active sport
        assertTrue(RouteProximityRanker.matchesSport(BSportType.RUN, BSportType.RUN))
        assertTrue(RouteProximityRanker.matchesSport(BSportType.UNKNOWN, BSportType.RUN))
        org.junit.Assert.assertFalse(RouteProximityRanker.matchesSport(BSportType.BIKE, BSportType.RUN))
    }
}

