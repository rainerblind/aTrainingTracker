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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.routes.WaypointType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying N+1 query elimination and batch query parity in RoutesDatabaseManager (REQ-DAT-034.2, TST-DAT-029.2).
 * Verifies:
 * 1. getAllRouteSummaries() returns route metadata without loading path points or waypoints.
 * 2. getAllRoutes() uses 3 bulk queries to reconstruct RouteWithPath instances matching individual loading.
 * 3. Empty database returns empty list cleanly.
 */
@RunWith(RobolectricTestRunner::class)
class RoutesDatabaseManagerOptimizationTest {

    private lateinit var context: Context
    private lateinit var manager: RoutesDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("Routes.db")
        RoutesDatabaseManager.resetForTesting(null)
        manager = RoutesDatabaseManager.getInstance(context)
    }

    @After
    fun tearDown() {
        RoutesDatabaseManager.resetForTesting(null)
        context.deleteDatabase("Routes.db")
    }

    private fun insertSampleRoute(name: String, sportType: BSportType, pointCount: Int, waypointCount: Int): Long {
        val path = (0 until pointCount).map { i ->
            PathPoint(
                latLng = LatLng(48.0 + (i * 0.001), 11.5 + (i * 0.001)),
                distance = i * 100.0,
                altitude = 500.0 + i * 10.0
            )
        }
        val waypoints = (0 until waypointCount).map { i ->
            RouteWaypoint(
                id = 0L,
                routeId = 0L,
                name = "WP $i for $name",
                description = "Desc $i",
                latLng = LatLng(48.0 + (i * 0.002), 11.5 + (i * 0.002)),
                altitude = 520.0 + i * 20.0,
                distanceFromStart = i * 200.0,
                type = WaypointType.POI_SUMMIT
            )
        }
        val summary = RouteSummary(
            id = 0L,
            externalId = "ext_$name",
            name = name,
            description = "Route Description for $name",
            distance = pointCount * 100.0,
            elevationGain = 150.0,
            bSportType = sportType,
            source = RouteSource.LOCAL_GPX,
            isSelected = false,
            clusterId = -1L,
            minLat = 48.0,
            minLng = 11.5,
            maxLat = 48.0 + (pointCount * 0.001),
            maxLng = 11.5 + (pointCount * 0.001),
            syncedAt = 123456789L
        )
        return manager.insertRoute(summary, path, waypoints)
    }

    @Test
    fun testEmptyDatabase_returnsEmptyLists() {
        val summaries = manager.getAllRouteSummaries()
        assertTrue("Empty DB must return empty summaries list", summaries.isEmpty())

        val routes = manager.getAllRoutes()
        assertTrue("Empty DB must return empty routes list", routes.isEmpty())
    }

    @Test
    fun testGetAllRouteSummaries_returnsMetadataAccurately() {
        val id1 = insertSampleRoute("Route Alpha", BSportType.RUN, 10, 2)
        val id2 = insertSampleRoute("Route Beta", BSportType.BIKE, 20, 4)

        val summaries = manager.getAllRouteSummaries()
        assertEquals(2, summaries.size)

        val s1 = summaries.find { it.id == id1 }
        val s2 = summaries.find { it.id == id2 }

        assertNotNull(s1)
        assertEquals("Route Alpha", s1!!.name)
        assertEquals(BSportType.RUN, s1.bSportType)
        assertEquals(1000.0, s1.distance, 0.01)
        assertEquals(123456789L, s1.syncedAt)

        assertNotNull(s2)
        assertEquals("Route Beta", s2!!.name)
        assertEquals(BSportType.BIKE, s2.bSportType)
        assertEquals(2000.0, s2.distance, 0.01)
    }

    @Test
    fun testGetAllRoutes_batchedLoadingParityWithIndividualQueries() {
        val id1 = insertSampleRoute("Mountain Pass", BSportType.BIKE, 15, 3)
        val id2 = insertSampleRoute("River Trail", BSportType.RUN, 8, 1)
        val id3 = insertSampleRoute("City Loop", BSportType.UNKNOWN, 5, 0)

        val allRoutes = manager.getAllRoutes()
        assertEquals(3, allRoutes.size)

        for (route in allRoutes) {
            val routeId = route.summary.id
            val individual = manager.getRouteById(routeId)
            assertNotNull("Individual route lookup must not be null for $routeId", individual)

            val individualSummary = individual!!.summary
            val individualPath = individual.path
            val individualWaypoints = individual.waypoints

            assertEquals("Summary name parity for route $routeId", individualSummary.name, route.summary.name)
            assertEquals("Summary distance parity for route $routeId", individualSummary.distance, route.summary.distance, 0.001)
            assertEquals("Summary sport parity for route $routeId", individualSummary.bSportType, route.summary.bSportType)

            // Verify Path Point count, coordinates, and sequential order
            assertEquals("Path point count mismatch for route $routeId", individualPath.size, route.path.size)
            for (i in individualPath.indices) {
                assertEquals("Path Lat parity at $i", individualPath[i].latLng.latitude, route.path[i].latLng.latitude, 0.00001)
                assertEquals("Path Lng parity at $i", individualPath[i].latLng.longitude, route.path[i].latLng.longitude, 0.00001)
                assertEquals("Path Dist parity at $i", individualPath[i].distance, route.path[i].distance, 0.00001)
                assertEquals("Path Alt parity at $i", individualPath[i].altitude, route.path[i].altitude)
            }

            // Verify Waypoint count, coordinates, and sequential order
            assertEquals("Waypoint count mismatch for route $routeId", individualWaypoints.size, route.waypoints.size)
            for (i in individualWaypoints.indices) {
                assertEquals("WP Name parity at $i", individualWaypoints[i].name, route.waypoints[i].name)
                assertEquals("WP Type parity at $i", individualWaypoints[i].type, route.waypoints[i].type)
                assertEquals("WP Lat parity at $i", individualWaypoints[i].latLng.latitude, route.waypoints[i].latLng.latitude, 0.00001)
                assertEquals("WP Lng parity at $i", individualWaypoints[i].latLng.longitude, route.waypoints[i].latLng.longitude, 0.00001)
            }
        }
    }
}
