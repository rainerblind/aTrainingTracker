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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkRouteMatcherTest {

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
    fun projectOntoPolyline_calculatesAccurateDistanceAndCrossTrack() {
        val path = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5100, 9.0000, 1111.0)
        )
        // Point exactly on segment halfway
        val midPos = LatLng(48.5050, 9.0000)
        val projOn = ForkRouteMatcher.projectOntoPolyline(midPos, path)
        assertEquals(0.0, projOn.crossTrackDistanceMeters, 1.0)
        assertEquals(555.5, projOn.distanceAlongRouteMeters, 2.0)

        // Point offset by ~100m East
        val offsetPos = LatLng(48.5050, 9.00135)
        val projOff = ForkRouteMatcher.projectOntoPolyline(offsetPos, path)
        assertTrue(projOff.crossTrackDistanceMeters in 90.0..110.0)
    }

    @Test
    fun findCandidates_sharedDepartureCorridor_returnsMatchingRoutes() {
        // Two routes sharing first 1000m going North from 48.5000, 9.0000
        val sharedPath = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5050, 9.0000, 555.0),
            pt(48.5100, 9.0000, 1111.0)
        )
        val route1Path = sharedPath + listOf(pt(48.5150, 9.0050, 1700.0))
        val route2Path = sharedPath + listOf(pt(48.5150, 8.9950, 1700.0))

        val route1 = RouteWithPath(dummySummary(1L, "Waldenbuch"), route1Path)
        val route2 = RouteWithPath(dummySummary(2L, "Dettenhausen"), route2Path)

        // Athlete at 600m along shared corridor (48.5054, 9.0000)
        val athletePos = LatLng(48.5054, 9.0000)
        val candidates = ForkRouteMatcher.findCandidateRoutes(listOf(route1, route2), athletePos)

        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.summary.id == 1L })
        assertTrue(candidates.any { it.summary.id == 2L })
    }

    @Test
    fun findCandidates_divergedOrReverseRoute_filtersOutRoute() {
        // Route 3 goes South instead of North
        val southRoutePath = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.4900, 9.0000, 1111.0)
        )
        val route3 = RouteWithPath(dummySummary(3L, "South Route"), southRoutePath)

        val athletePos = LatLng(48.5054, 9.0000)
        val candidates = ForkRouteMatcher.findCandidateRoutes(listOf(route3), athletePos)

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun findCandidates_insufficientSharedPrefix_returnsEmpty() {
        // Route only shares 100m
        val shortRoutePath = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5010, 9.0000, 111.0),
            pt(48.5020, 9.0100, 850.0)
        )
        val routeShort = RouteWithPath(dummySummary(4L, "Short Shared"), shortRoutePath)

        val athletePos = LatLng(48.5010, 9.0000) // only ~111m from start
        val candidates = ForkRouteMatcher.findCandidateRoutes(listOf(routeShort), athletePos)

        assertTrue(candidates.isEmpty())
    }

    /**
     * TST-MAP-040.1: Spatial bounding box rejection (O(1)).
     */
    @Test
    fun findCandidates_spatialBoundingBoxRejection_discardsDistantRouteInO1() {
        // Distant route in Stuttgart (latitude 48.7758, longitude 9.1829), > 30km away
        val distantPath = listOf(
            pt(48.7758, 9.1829, 0.0),
            pt(48.7800, 9.1850, 500.0)
        )
        val distantRoute = RouteWithPath(
            dummySummary(5L, "Stuttgart Route").copy(
                minLat = 48.7758, maxLat = 48.7800,
                minLng = 9.1829, maxLng = 9.1850
            ),
            distantPath
        )

        // Athlete in Tübingen (48.5216, 9.0576)
        val athletePos = LatLng(48.5216, 9.0576)
        val candidates = ForkRouteMatcher.findCandidateRoutes(listOf(distantRoute), athletePos)

        assertTrue(candidates.isEmpty())

        // Test isPointWithinBoundingBox helper directly
        val withinBounds = ForkRouteMatcher.isPointWithinBoundingBox(
            point = athletePos,
            minLat = 48.5200, maxLat = 48.5230,
            minLng = 9.0550, maxLng = 9.0600,
            marginMeters = 100.0
        )
        assertTrue(withinBounds)

        val outsideBounds = ForkRouteMatcher.isPointWithinBoundingBox(
            point = athletePos,
            minLat = 48.7758, maxLat = 48.7800,
            minLng = 9.1829, maxLng = 9.1850,
            marginMeters = 100.0
        )
        org.junit.Assert.assertFalse(outsideBounds)
    }

    /**
     * TST-MAP-040.2: Sport-type pre-filtering.
     */
    @Test
    fun findCandidates_sportTypePreFiltering_filtersIncompatibleSport() {
        val sharedPath = listOf(
            pt(48.5000, 9.0000, 0.0),
            pt(48.5050, 9.0000, 555.0),
            pt(48.5100, 9.0000, 1111.0)
        )
        val bikeRoute = RouteWithPath(
            dummySummary(10L, "Bike Corridor").copy(bSportType = BSportType.BIKE),
            sharedPath
        )
        val runRoute = RouteWithPath(
            dummySummary(11L, "Run Corridor").copy(bSportType = BSportType.RUN),
            sharedPath
        )

        val athletePos = LatLng(48.5054, 9.0000)

        // Cycling workout: only bike route matches
        val cyclingCandidates = ForkRouteMatcher.findCandidateRoutes(
            allRoutes = listOf(bikeRoute, runRoute),
            currentPos = athletePos,
            activeSportType = BSportType.BIKE
        )
        assertEquals(1, cyclingCandidates.size)
        assertEquals(10L, cyclingCandidates.first().summary.id)

        // Running workout: only run route matches
        val runningCandidates = ForkRouteMatcher.findCandidateRoutes(
            allRoutes = listOf(bikeRoute, runRoute),
            currentPos = athletePos,
            activeSportType = BSportType.RUN
        )
        assertEquals(1, runningCandidates.size)
        assertEquals(11L, runningCandidates.first().summary.id)

        // No active sport specified (null): both matching routes qualify
        val allCandidates = ForkRouteMatcher.findCandidateRoutes(
            allRoutes = listOf(bikeRoute, runRoute),
            currentPos = athletePos,
            activeSportType = null
        )
        assertEquals(2, allCandidates.size)
    }
}
