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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RouteSegmentMatcher] geodesic spatial corridor and directional traversal engine
 * (REQ-UI-302, TST-UI-262, ATT-2583).
 */
class RouteSegmentMatcherTest {

    // 1 degree longitude at 48.0 degrees lat is approx 74470 meters
    private val metersPerLngDegree = 74470.0

    // Construct a straight 10-kilometer route heading East (0m to 10000m, step 100m)
    private val sampleRoute: List<PathPoint> = (0..100).map { i ->
        val distMeters = i * 100.0
        val lng = 11.0 + (distMeters / metersPerLngDegree)
        PathPoint(
            distance = distMeters,
            latLng = LatLng(48.0, lng),
            altitude = 500.0
        )
    }

    private fun createSegment(
        id: Long,
        name: String,
        sportType: BSportType,
        points: List<PathPoint>,
        distanceMeters: Double,
        prTime: String = "05:00"
    ): SegmentWithPath {
        val summary = SegmentSummary(
            stravaId = id,
            name = name,
            bSportType = sportType,
            climbCategory_raw = 0,
            climbCategory = "",
            prTime_raw = 300,
            prTime = prTime,
            city = "Munich",
            distance = "${(distanceMeters / 1000.0)} km",
            distance_raw = distanceMeters,
            averageGrade_raw = 2.5,
            averageGrade = "2.5%",
            maxGrade = "4.0%",
            elevationGain_raw = 50.0,
            elevationGain = "50 m",
            elevationMin = "500 m",
            elevationMax = "550 m",
            map_polyline = ""
        )
        return SegmentWithPath(summary, points)
    }

    @Test
    fun testMatchSegments_forwardAlignedSegment_matchesSuccessfully() = runBlocking {
        // Segment along km 2 (index 20) to km 5 (index 50)
        val segPoints = (20..50).map { i ->
            val dist = (i - 20) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val candidate = createSegment(101L, "Sprint 2-5km", BSportType.BIKE, segPoints, 3000.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertEquals("Should match 1 forward-aligned segment", 1, matched.size)
        val result = matched[0]
        assertEquals(101L, result.segment.summary.stravaId)
        assertEquals(2000.0, result.startDistanceMeters, 1.0)
        assertEquals(5000.0, result.endDistanceMeters, 1.0)
        assertEquals(20, result.startPathIndex)
        assertEquals(50, result.endPathIndex)
    }

    @Test
    fun testMatchSegments_reverseDirectionSegment_isRejected() = runBlocking {
        // Segment running in reverse: from km 5 down to km 2
        val segPoints = (50 downTo 20).map { i ->
            val dist = (50 - i) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val candidate = createSegment(102L, "Reverse Segment", BSportType.BIKE, segPoints, 3000.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertTrue("Reverse traversal segment MUST be rejected", matched.isEmpty())
    }

    @Test
    fun testMatchSegments_perpendicularCrossingSegment_isRejected() = runBlocking {
        // Crossing corridor segment: running North to South, crossing at km 5
        val crossLng = 11.0 + ((50 * 100.0) / metersPerLngDegree)
        val segPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.01, crossLng), altitude = 500.0), // ~1.1 km North
            PathPoint(distance = 2200.0, latLng = LatLng(47.99, crossLng), altitude = 500.0) // ~1.1 km South
        )
        val candidate = createSegment(103L, "Perpendicular Cross", BSportType.BIKE, segPoints, 2200.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertTrue("Perpendicular crossing segment MUST be rejected", matched.isEmpty())
    }

    @Test
    fun testMatchSegments_opposingSportType_isRejected() = runBlocking {
        // Segment matching spatial geometry but registered as RUN on a BIKE route
        val segPoints = (20..50).map { i ->
            val dist = (i - 20) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val candidate = createSegment(104L, "Running Segment", BSportType.RUN, segPoints, 3000.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertTrue("Conflicting sport type MUST be rejected", matched.isEmpty())
    }

    @Test
    fun testMatchSegments_divergingFinish_isRejected() = runBlocking {
        // Segment starts at km 2 (on corridor) but finish point diverges 500m North
        val segPoints = listOf(
            PathPoint(distance = 0.0, latLng = sampleRoute[20].latLng, altitude = 500.0),
            PathPoint(distance = 3000.0, latLng = LatLng(48.005, sampleRoute[50].latLng.longitude), altitude = 500.0) // ~555m North
        )
        val candidate = createSegment(105L, "Diverging Finish", BSportType.BIKE, segPoints, 3000.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertTrue("Segment with diverging terminal point MUST be rejected", matched.isEmpty())
    }

    @Test
    fun testMatchSegments_horseshoeDivergingMidpoint_isRejected() = runBlocking {
        // Segment with start at km 2, finish at km 5, but middle point loops 500m away
        val segPoints = listOf(
            PathPoint(distance = 0.0, latLng = sampleRoute[20].latLng, altitude = 500.0),
            PathPoint(distance = 1500.0, latLng = LatLng(48.005, sampleRoute[35].latLng.longitude), altitude = 500.0), // 550m North
            PathPoint(distance = 3000.0, latLng = sampleRoute[50].latLng, altitude = 500.0)
        )
        val candidate = createSegment(106L, "Horseshoe Divergence", BSportType.BIKE, segPoints, 3000.0)

        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(candidate), BSportType.BIKE)

        assertTrue("Segment with diverging midpoint MUST be rejected", matched.isEmpty())
    }

    @Test
    fun testMatchSegments_multipleSegments_sortedAscendingByStartDistance() = runBlocking {
        // Segment 1 from km 6 to km 8
        val seg1Points = (60..80).map { i ->
            val dist = (i - 60) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val seg1 = createSegment(201L, "Later Segment", BSportType.BIKE, seg1Points, 2000.0)

        // Segment 2 from km 1 to km 3
        val seg2Points = (10..30).map { i ->
            val dist = (i - 10) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val seg2 = createSegment(202L, "Earlier Segment", BSportType.BIKE, seg2Points, 2000.0)

        // Pass in reverse order [seg1, seg2]
        val matched = RouteSegmentMatcher.matchSegments(sampleRoute, listOf(seg1, seg2), BSportType.BIKE)

        assertEquals(2, matched.size)
        assertEquals("Earlier segment must be first", 202L, matched[0].segment.summary.stravaId)
        assertEquals(1000.0, matched[0].startDistanceMeters, 1.0)
        assertEquals("Later segment must be second", 201L, matched[1].segment.summary.stravaId)
        assertEquals(6000.0, matched[1].startDistanceMeters, 1.0)
    }

    private fun createRoute(
        id: Long,
        name: String,
        sportType: BSportType,
        path: List<PathPoint>
    ): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = name,
            description = "Test description",
            isSelected = false,
            distance = path.lastOrNull()?.distance ?: 0.0,
            elevationGain = 100.0,
            bSportType = sportType,
            source = RouteSource.LOCAL_GPX
        )
        return RouteWithPath(summary = summary, path = path)
    }

    @Test
    fun testFindRoutesContainingSegment_forwardRouteMatches_returnsSegmentMatchedRoute() = runBlocking {
        // Segment from km 2 to km 4 along sampleRoute
        val segPoints = (20..40).map { i ->
            val dist = (i - 20) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val segment = createSegment(301L, "Target Segment", BSportType.BIKE, segPoints, 2000.0)

        val candidateRoute = createRoute(1001L, "Tour de Alps", BSportType.BIKE, sampleRoute)
        val nonMatchingRoute = createRoute(1002L, "Running Route", BSportType.RUN, sampleRoute)

        val matches = RouteSegmentMatcher.findRoutesContainingSegment(
            segment = segment,
            candidateRoutes = listOf(candidateRoute, nonMatchingRoute)
        )

        assertEquals(1, matches.size)
        assertEquals(1001L, matches[0].route.summary.id)
        assertEquals("Tour de Alps", matches[0].route.summary.name)
        assertEquals(2000.0, matches[0].startDistanceMeters, 1.0)
        assertEquals(4000.0, matches[0].endDistanceMeters, 1.0)
    }

    @Test
    fun testFindRoutesContainingSegment_reverseDirectionRoute_returnsEmpty() = runBlocking {
        // Reverse direction route (Westward: 10000m to 0m)
        val reverseRoutePath = sampleRoute.reversed().mapIndexed { index, pt ->
            PathPoint(distance = index * 100.0, latLng = pt.latLng, altitude = pt.altitude)
        }
        val reverseRoute = createRoute(1003L, "Reverse Route", BSportType.BIKE, reverseRoutePath)

        // Segment heading East from km 2 to km 4
        val segPoints = (20..40).map { i ->
            val dist = (i - 20) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val segment = createSegment(302L, "Eastbound Segment", BSportType.BIKE, segPoints, 2000.0)

        val matches = RouteSegmentMatcher.findRoutesContainingSegment(
            segment = segment,
            candidateRoutes = listOf(reverseRoute)
        )

        assertTrue("Reverse traversal route MUST not match", matches.isEmpty())
    }

    @Test
    fun testFindRoutesContainingSegment_emptyOrSinglePoint_returnsEmpty() = runBlocking {
        val singlePointSegment = createSegment(
            303L,
            "Single Point",
            BSportType.BIKE,
            listOf(sampleRoute.first()),
            0.0
        )
        val candidateRoute = createRoute(1004L, "Sample Route", BSportType.BIKE, sampleRoute)

        val matches = RouteSegmentMatcher.findRoutesContainingSegment(
            segment = singlePointSegment,
            candidateRoutes = listOf(candidateRoute)
        )

        assertTrue("Single-point segment must return empty list", matches.isEmpty())

        val emptyMatches = RouteSegmentMatcher.findRoutesContainingSegment(
            segment = singlePointSegment,
            candidateRoutes = emptyList()
        )
        assertTrue("Empty candidate routes must return empty list", emptyMatches.isEmpty())
    }

    @Test
    fun testMatchSegments_sparseRouteVertices_matchesSegmentBetweenVertices() = runBlocking {
        // Route with vertices spaced 60m apart
        val sparseRoute = (0..20).map { i ->
            val distMeters = i * 60.0
            val lng = 11.0 + (distMeters / metersPerLngDegree)
            PathPoint(distance = distMeters, latLng = LatLng(48.0, lng), altitude = 500.0)
        }

        // Segment starting at 210m (midpoint between 180m and 240m vertices; vertex distance = 30m > 25m)
        // and ending at 510m (midpoint between 480m and 540m vertices; vertex distance = 30m > 25m)
        val segPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0 + (210.0 / metersPerLngDegree)), altitude = 500.0),
            PathPoint(distance = 150.0, latLng = LatLng(48.0, 11.0 + (360.0 / metersPerLngDegree)), altitude = 500.0),
            PathPoint(distance = 300.0, latLng = LatLng(48.0, 11.0 + (510.0 / metersPerLngDegree)), altitude = 500.0)
        )
        val candidate = createSegment(401L, "Sparse Segment", BSportType.BIKE, segPoints, 300.0)

        val matched = RouteSegmentMatcher.matchSegments(sparseRoute, listOf(candidate), BSportType.BIKE)

        assertEquals("Should successfully match segment falling between sparse route vertices", 1, matched.size)
        val result = matched[0]
        assertEquals(401L, result.segment.summary.stravaId)
        assertEquals(210.0, result.startDistanceMeters, 1.0)
        assertEquals(510.0, result.endDistanceMeters, 1.0)
    }

    @Test
    fun testMatchSegments_outAndBackRoute_matchesForwardOutboundLeg() = runBlocking {
        // Out-and-back route: 0 to 5000m East, then 5000m to 10000m West
        val outbound = (0..50).map { i ->
            val dist = i * 100.0
            val lng = 11.0 + (dist / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val returnLeg = (1..50).map { i ->
            val dist = 5000.0 + i * 100.0
            val lng = 11.0 + ((5000.0 - i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val outAndBackPath = outbound + returnLeg

        // Segment heading East from 1000m to 3000m
        val segPoints = (10..30).map { i ->
            val dist = (i - 10) * 100.0
            val lng = 11.0 + ((i * 100.0) / metersPerLngDegree)
            PathPoint(distance = dist, latLng = LatLng(48.0, lng), altitude = 500.0)
        }
        val candidate = createSegment(402L, "Outbound Segment", BSportType.BIKE, segPoints, 2000.0)

        val matched = RouteSegmentMatcher.matchSegments(outAndBackPath, listOf(candidate), BSportType.BIKE)

        assertEquals("Out-and-back route should match the forward outbound traversal", 1, matched.size)
        val result = matched[0]
        assertEquals(402L, result.segment.summary.stravaId)
        assertEquals(1000.0, result.startDistanceMeters, 1.0)
        assertEquals(3000.0, result.endDistanceMeters, 1.0)
    }
}

