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
}
