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

package com.atrainingtracker.trainingtracker.geo

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.ClimbDeduplicator
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.routes.ForkRouteMatcher
import com.atrainingtracker.trainingtracker.routes.GeoUtils
import com.atrainingtracker.trainingtracker.routes.ReturnCorridorSnapper
import com.atrainingtracker.trainingtracker.routes.RouteAutoDetector
import com.atrainingtracker.trainingtracker.routes.RouteSegmentMatcher
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.routes.TcxCourseParser
import com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationEngine
import com.atrainingtracker.trainingtracker.routes.WaypointDistanceCalculator
import com.atrainingtracker.trainingtracker.routes.WaypointType
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Comprehensive verification suite for consolidated [GeoMath] polyline projection,
 * equirectangular cos(lat) isotropy across all consumers, single canonical [PolylineProjection],
 * and unified route elevation-gain hysteresis (REQ-GEO-003, TST-GEO-003, ATT-8133).
 */
class GeoMathTest {

    private fun offsetPoint(origin: LatLng, northMeters: Double, eastMeters: Double): LatLng {
        val metersPerDegLat = Math.toRadians(1.0) * GeoUtils.EARTH_RADIUS_METERS
        val metersPerDegLng = metersPerDegLat * cos(Math.toRadians(origin.latitude))
        return LatLng(
            origin.latitude + northMeters / metersPerDegLat,
            origin.longitude + eastMeters / metersPerDegLng
        )
    }

    private fun buildRoute(id: Long, start: LatLng, end: LatLng): RouteWithPath {
        val dist = GeoUtils.haversineDistanceMeters(
            start.latitude, start.longitude,
            end.latitude, end.longitude
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = start, altitude = 300.0),
            PathPoint(distance = dist, latLng = end, altitude = 310.0)
        )
        return RouteWithPath(
            summary = RouteSummary(
                id = id,
                externalId = "ext_$id",
                name = "Route $id",
                description = "",
                isSelected = true,
                distance = dist,
                elevationGain = 10.0,
                bSportType = BSportType.BIKE,
                source = RouteSource.LOCAL_GPX
            ),
            path = path
        )
    }

    @Test
    fun testSingleCanonicalPolylineProjectionExists_TST_GEO_003() {
        val duplicatePresent = try {
            Class.forName("com.atrainingtracker.trainingtracker.routes.PolylineProjection")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertTrue(
            "Duplicate routes.PolylineProjection must be removed in favor of geo.PolylineProjection",
            !duplicatePresent
        )

        val origin = LatLng(50.0, 9.0)
        val route = buildRoute(1L, origin, offsetPoint(origin, northMeters = 0.0, eastMeters = 1000.0))
        val proj: PolylineProjection = ForkRouteMatcher.projectOntoPolyline(
            offsetPoint(origin, northMeters = 25.0, eastMeters = 500.0),
            route.path
        )
        assertEquals(500.0, proj.alongTrackDistance, 1.5)
        assertEquals(25.0, proj.crossTrackDistanceMeters, 0.5)
    }

    @Test
    fun testCosLatIsotropyAt50North_eastWestNorthSouthAndDiagonalWithin0_5Percent_TST_GEO_003() {
        val origin = LatLng(50.0, 9.0)
        val offsetMeters = 80.0

        // 1. East-West segment (1000 m East) with 80 m North lateral offset at midpoint
        val ewEnd = offsetPoint(origin, northMeters = 0.0, eastMeters = 1000.0)
        val ewQuery = offsetPoint(origin, northMeters = offsetMeters, eastMeters = 500.0)
        val ewRoute = buildRoute(10L, origin, ewEnd)

        // 2. North-South segment (1000 m North) with 80 m East lateral offset at midpoint
        val nsEnd = offsetPoint(origin, northMeters = 1000.0, eastMeters = 0.0)
        val nsQuery = offsetPoint(origin, northMeters = 500.0, eastMeters = offsetMeters)
        val nsRoute = buildRoute(11L, origin, nsEnd)

        // 3. 45-degree NE diagonal segment (1000 m along 45 deg) with 80 m perpendicular NW offset
        val cos45 = cos(Math.toRadians(45.0))
        val sin45 = sin(Math.toRadians(45.0))
        val diagEnd = offsetPoint(origin, northMeters = 1000.0 * cos45, eastMeters = 1000.0 * sin45)
        val diagQuery = offsetPoint(
            origin,
            northMeters = 500.0 * cos45 + offsetMeters * sin45,
            eastMeters = 500.0 * sin45 - offsetMeters * cos45
        )
        val diagRoute = buildRoute(12L, origin, diagEnd)

        // Assert every consolidated projector agrees to < 0.5% across E-W, N-S, and 45 deg diagonal
        fun assertIsotropic(name: String, dEw: Double, dNs: Double, dDiag: Double) {
            val ewNsRelDiff = abs(dEw - dNs) / offsetMeters
            val ewDiagRelDiff = abs(dEw - dDiag) / offsetMeters
            assertTrue("$name E-W ($dEw) vs N-S ($dNs) exceeded 0.5%", ewNsRelDiff < 0.005)
            assertTrue("$name E-W ($dEw) vs Diag ($dDiag) exceeded 0.5%", ewDiagRelDiff < 0.005)
            assertEquals("$name E-W distance", offsetMeters, dEw, 0.5)
            assertEquals("$name N-S distance", offsetMeters, dNs, 0.5)
            assertEquals("$name Diag distance", offsetMeters, dDiag, 0.5)
        }

        // GeoMath
        assertIsotropic(
            "GeoMath",
            GeoMath.distanceToSegmentMeters(ewQuery, origin, ewEnd),
            GeoMath.distanceToSegmentMeters(nsQuery, origin, nsEnd),
            GeoMath.distanceToSegmentMeters(diagQuery, origin, diagEnd)
        )

        // RouteAutoDetector (previously had ~33% distortion on diagonal/non-cardinal segments!)
        assertIsotropic(
            "RouteAutoDetector",
            RouteAutoDetector.distanceToSegmentMeters(ewQuery.latitude, ewQuery.longitude, origin, ewEnd).toDouble(),
            RouteAutoDetector.distanceToSegmentMeters(nsQuery.latitude, nsQuery.longitude, origin, nsEnd).toDouble(),
            RouteAutoDetector.distanceToSegmentMeters(diagQuery.latitude, diagQuery.longitude, origin, diagEnd).toDouble()
        )

        // ClimbDeduplicator
        assertIsotropic(
            "ClimbDeduplicator",
            ClimbDeduplicator.distanceToSegmentMeters(ewQuery, origin, ewEnd),
            ClimbDeduplicator.distanceToSegmentMeters(nsQuery, origin, nsEnd),
            ClimbDeduplicator.distanceToSegmentMeters(diagQuery, origin, diagEnd)
        )

        // PolylineProgressTracker
        assertIsotropic(
            "PolylineProgressTracker",
            PolylineProgressTracker.projectLocation(ewRoute.path, ewQuery)!!.crossTrackDistanceMeters,
            PolylineProgressTracker.projectLocation(nsRoute.path, nsQuery)!!.crossTrackDistanceMeters,
            PolylineProgressTracker.projectLocation(diagRoute.path, diagQuery)!!.crossTrackDistanceMeters
        )

        // ForkRouteMatcher
        assertIsotropic(
            "ForkRouteMatcher",
            ForkRouteMatcher.projectOntoPolyline(ewQuery, ewRoute.path).crossTrackDistanceMeters,
            ForkRouteMatcher.projectOntoPolyline(nsQuery, nsRoute.path).crossTrackDistanceMeters,
            ForkRouteMatcher.projectOntoPolyline(diagQuery, diagRoute.path).crossTrackDistanceMeters
        )

        // ReturnCorridorSnapper
        assertIsotropic(
            "ReturnCorridorSnapper",
            ReturnCorridorSnapper.snapActiveRoute(ewQuery.latitude, ewQuery.longitude, ewRoute, false).crossTrackDistanceMeters,
            ReturnCorridorSnapper.snapActiveRoute(nsQuery.latitude, nsQuery.longitude, nsRoute, false).crossTrackDistanceMeters,
            ReturnCorridorSnapper.snapActiveRoute(diagQuery.latitude, diagQuery.longitude, diagRoute, false).crossTrackDistanceMeters
        )

        // TurnByTurnNavigationEngine
        val tbtEw = TurnByTurnNavigationEngine().apply { setActiveRoute(ewRoute) }.onLocationChanged(ewQuery)
        val tbtNs = TurnByTurnNavigationEngine().apply { setActiveRoute(nsRoute) }.onLocationChanged(nsQuery)
        val tbtDiag = TurnByTurnNavigationEngine().apply { setActiveRoute(diagRoute) }.onLocationChanged(diagQuery)
        assertIsotropic(
            "TurnByTurnNavigationEngine",
            tbtEw.crossTrackDistanceMeters,
            tbtNs.crossTrackDistanceMeters,
            tbtDiag.crossTrackDistanceMeters
        )

        // WaypointDistanceCalculator along-track midpoint parity on diagonal at 50° N
        val diagWpt = RouteWaypoint(
            latLng = diagQuery,
            altitude = 305.0,
            name = "Mid",
            description = "",
            type = WaypointType.GENERIC
        )
        val projectedWpts = WaypointDistanceCalculator.projectWaypoints(listOf(diagWpt), diagRoute.path)
        assertEquals(500.0, projectedWpts.first().distanceFromStart, 2.0)

        // RouteSegmentMatcher diagonal corridor match at 50° N
        val segStart = offsetPoint(origin, northMeters = 200.0 * cos45 + 15.0 * sin45, eastMeters = 200.0 * sin45 - 15.0 * cos45)
        val segEnd = offsetPoint(origin, northMeters = 700.0 * cos45 + 15.0 * sin45, eastMeters = 700.0 * sin45 - 15.0 * cos45)
        val segSummary = SegmentSummary(
            stravaId = 1L, name = "Diag Seg", bSportType = BSportType.BIKE,
            climbCategory_raw = 0, climbCategory = "", prTime_raw = 60, prTime = "01:00",
            city = "", distance = "500 m", distance_raw = 500.0,
            averageGrade_raw = 1.0, averageGrade = "1%", maxGrade = "2%",
            elevationGain_raw = 5.0, elevationGain = "5 m",
            elevationMin = "300 m", elevationMax = "305 m", map_polyline = ""
        )
        val seg = SegmentWithPath(
            segSummary,
            listOf(PathPoint(0.0, segStart, 300.0), PathPoint(500.0, segEnd, 305.0))
        )
        val matched = RouteSegmentMatcher.matchSegmentsPure(diagRoute.path, listOf(seg), BSportType.BIKE)
        assertEquals(1, matched.size)
        assertEquals(200.0, matched.first().startDistanceMeters, 2.0)
        assertEquals(700.0, matched.first().endDistanceMeters, 2.0)
    }

    @Test
    fun testHighLatitude65NorthDiagonalAccuracy_TST_GEO_003() {
        val origin = LatLng(65.0, 25.0)
        val offsetMeters = 100.0
        val cos45 = cos(Math.toRadians(45.0))
        val sin45 = sin(Math.toRadians(45.0))
        val end = offsetPoint(origin, northMeters = 1000.0 * cos45, eastMeters = 1000.0 * sin45)
        val query = offsetPoint(
            origin,
            northMeters = 500.0 * cos45 + offsetMeters * sin45,
            eastMeters = 500.0 * sin45 - offsetMeters * cos45
        )

        val proj = GeoMath.projectPointOntoSegment(query, origin, end)
        assertEquals(0.5, proj.t, 0.005)
        assertEquals(offsetMeters, proj.crossTrackDistanceMeters, 0.5)

        val detectorDist = RouteAutoDetector.distanceToSegmentMeters(query.latitude, query.longitude, origin, end).toDouble()
        assertEquals(offsetMeters, detectorDist, 0.5)
    }

    @Test
    fun testEdgeCases_emptySingleDegenerateAndNearPolar_TST_GEO_003() {
        assertNull(GeoMath.projectOntoPolyline(emptyList(), LatLng(50.0, 9.0)))

        val single = listOf(PathPoint(distance = 0.0, latLng = LatLng(50.0, 9.0), altitude = 250.0, slope = 3.0))
        val singleProj = GeoMath.projectOntoPolyline(single, LatLng(50.001, 9.0))
        assertNotNull(singleProj)
        assertEquals(0, singleProj!!.segmentIndex)
        assertEquals(0.0, singleProj.t, 1e-9)
        assertEquals(250.0, singleProj.interpolatedAltitude!!, 1e-6)

        // Degenerate zero-length segment
        val same = LatLng(50.0, 9.0)
        val degen = GeoMath.projectPointOntoSegment(LatLng(50.001, 9.0), same, same)
        assertEquals(0.0, degen.t, 1e-9)
        assertTrue(degen.crossTrackDistanceMeters.isFinite() && degen.crossTrackDistanceMeters > 100.0)

        // Near-polar guard
        val polar = GeoMath.projectPointOntoSegment(
            LatLng(89.9999, 10.0),
            LatLng(89.9998, 0.0),
            LatLng(89.9998, 90.0)
        )
        assertTrue(polar.t.isFinite() && polar.crossTrackDistanceMeters.isFinite())
    }

    @Test
    fun testNonFiniteInputs_neverPoisonSegmentPolylineOrElevationGain_TST_GEO_003() {
        val origin = LatLng(50.0, 9.0)
        val end = offsetPoint(origin, northMeters = 0.0, eastMeters = 1000.0)
        val validQuery = offsetPoint(origin, northMeters = 50.0, eastMeters = 500.0)

        // 1. Non-finite coordinates in projectPointOntoSegment never yield NaN
        val nanSeg = GeoMath.projectPointOntoSegment(
            Double.NaN, 9.0, origin.latitude, origin.longitude, end.latitude, end.longitude
        )
        assertEquals(Double.POSITIVE_INFINITY, nanSeg.crossTrackDistanceMeters, 0.0)
        assertEquals(0.0, nanSeg.t, 0.0)

        // 2. Non-finite query coordinate in projectOntoPolyline returns null
        val validPath = listOf(
            PathPoint(distance = 0.0, latLng = origin, altitude = 300.0),
            PathPoint(distance = 1000.0, latLng = end, altitude = 310.0)
        )
        assertNull(GeoMath.projectOntoPolyline(validPath, Double.NaN, 9.0))
        assertNull(GeoMath.projectOntoPolyline(validPath, 50.0, Double.POSITIVE_INFINITY))

        // 3. Corrupted NaN coordinate on segment 0 must be skipped so valid segment 1 still wins
        val poisonedPath = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(Double.NaN, 9.0), altitude = 300.0),
            PathPoint(distance = 0.0, latLng = origin, altitude = 300.0),
            PathPoint(distance = 1000.0, latLng = end, altitude = 310.0)
        )
        val recovered = GeoMath.projectOntoPolyline(poisonedPath, validQuery)
        assertNotNull("Valid segment 1 must win when segment 0 has NaN coordinates", recovered)
        assertEquals(1, recovered!!.segmentIndex)
        assertEquals(50.0, recovered.crossTrackDistanceMeters, 0.5)
        assertEquals(500.0, recovered.alongTrackDistance, 1.5)

        // 4. All-corrupted polyline returns null cleanly
        val allBadPath = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(Double.NaN, 9.0), altitude = 300.0),
            PathPoint(distance = 100.0, latLng = LatLng(50.0, Double.POSITIVE_INFINITY), altitude = 305.0)
        )
        assertNull(GeoMath.projectOntoPolyline(allBadPath, validQuery))

        // 5. Non-finite hysteresisMeters falls back to default 2.0 m (suppressing 1.5 m noise, keeping 10 m climb)
        val noisyAlt = listOf(100.0, 101.5, 100.2, 110.0)
        assertEquals(10.0, GeoMath.calculateElevationGain(noisyAlt, Double.NaN), 1e-6)
        assertEquals(10.0, GeoMath.calculateElevationGain(noisyAlt, Double.POSITIVE_INFINITY), 1e-6)
        assertEquals(10.0, GeoMath.calculateElevationGain(noisyAlt, Double.NEGATIVE_INFINITY), 1e-6)
        // Negative finite hysteresis clamps to 0.0 m (raw positive sum: +1.5 + +9.8 = 11.3 m)
        assertEquals(11.3, GeoMath.calculateElevationGain(noisyAlt, -5.0), 1e-6)
    }

    @Test
    fun testUnifiedElevationGainHysteresis_andTcxGpxParity_TST_GEO_003() {
        // Noise below 2.0 m hysteresis produces 0.0 m gain
        val jitterOnly = listOf(100.0, 101.5, 100.2, 101.9, 100.1, 101.0, null, Double.NaN, Double.POSITIVE_INFINITY)
        assertEquals(0.0, GeoMath.calculateElevationGain(jitterOnly), 1e-6)

        // Real climbs accumulate cleanly across 2.0 m hysteresis
        val profile = listOf(100.0, 101.0, 103.0, 106.0, 105.0, 102.0, 110.0)
        // 100 -> 103 (+3, last=103) -> 106 (+3, last=106) -> 102 (-4, last=102) -> 110 (+8, last=110) => 14.0 m
        assertEquals(14.0, GeoMath.calculateElevationGain(profile), 1e-6)

        // Verify TcxCourseParser uses the exact same GeoMath hysteresis (suppressing 1.5 m GPS jitter)
        val noisyTcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
                <Courses>
                    <Course>
                        <Name>Noisy Ridge</Name>
                        <Track>
                            <Trackpoint>
                                <Position><LatitudeDegrees>50.000</LatitudeDegrees><LongitudeDegrees>9.000</LongitudeDegrees></Position>
                                <AltitudeMeters>100.0</AltitudeMeters><DistanceMeters>0.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Position><LatitudeDegrees>50.001</LatitudeDegrees><LongitudeDegrees>9.000</LongitudeDegrees></Position>
                                <AltitudeMeters>101.5</AltitudeMeters><DistanceMeters>111.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Position><LatitudeDegrees>50.002</LatitudeDegrees><LongitudeDegrees>9.000</LongitudeDegrees></Position>
                                <AltitudeMeters>100.2</AltitudeMeters><DistanceMeters>222.0</DistanceMeters>
                            </Trackpoint>
                            <Trackpoint>
                                <Position><LatitudeDegrees>50.003</LatitudeDegrees><LongitudeDegrees>9.000</LongitudeDegrees></Position>
                                <AltitudeMeters>110.0</AltitudeMeters><DistanceMeters>333.0</DistanceMeters>
                            </Trackpoint>
                        </Track>
                    </Course>
                </Courses>
            </TrainingCenterDatabase>
        """.trimIndent()
        val parsed = TcxCourseParser.parse(noisyTcx).getOrThrow()
        assertEquals(
            GeoMath.calculatePathElevationGain(parsed.pathPoints),
            parsed.summary.elevationGain,
            1e-6
        )
        assertEquals(10.0, parsed.summary.elevationGain, 1e-6)
    }
}
