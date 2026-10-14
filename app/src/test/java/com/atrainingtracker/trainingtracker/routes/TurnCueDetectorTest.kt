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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.measureTimeMillis

class TurnCueDetectorTest {

    private val origin = LatLng(48.0, 11.0)

    private fun offsetLatLng(start: LatLng, deltaNorthMeters: Double, deltaEastMeters: Double): LatLng {
        val deltaLat = deltaNorthMeters / 111139.0
        val deltaLng = deltaEastMeters / (111139.0 * cos(Math.toRadians(start.latitude)))
        return LatLng(start.latitude + deltaLat, start.longitude + deltaLng)
    }

    @Test
    fun testSignedAngleDelta_cardinalDirections() {
        // North (0) to East (90) -> +90 (Right)
        assertEquals(90f, TurnCueDetector.computeSignedAngleDelta(0f, 90f), 0.01f)

        // North (0) to West (270) -> -90 (Left)
        assertEquals(-90f, TurnCueDetector.computeSignedAngleDelta(0f, 270f), 0.01f)

        // East (90) to North (0) -> -90 (Left)
        assertEquals(-90f, TurnCueDetector.computeSignedAngleDelta(90f, 0f), 0.01f)

        // West (270) to North (0) -> +90 (Right)
        assertEquals(90f, TurnCueDetector.computeSignedAngleDelta(270f, 0f), 0.01f)

        // North (0) to South (180) -> 180 (U-Turn)
        assertEquals(180f, TurnCueDetector.computeSignedAngleDelta(0f, 180f), 0.01f)
    }

    @Test
    fun testClassifyTurn_angles() {
        assertEquals(TurnDirection.SLIGHT_RIGHT, TurnCueDetector.classifyTurn(45f))
        assertEquals(TurnDirection.RIGHT, TurnCueDetector.classifyTurn(90f))
        assertEquals(TurnDirection.SHARP_RIGHT, TurnCueDetector.classifyTurn(135f))
        assertEquals(TurnDirection.U_TURN, TurnCueDetector.classifyTurn(170f))

        assertEquals(TurnDirection.SLIGHT_LEFT, TurnCueDetector.classifyTurn(-45f))
        assertEquals(TurnDirection.LEFT, TurnCueDetector.classifyTurn(-90f))
        assertEquals(TurnDirection.SHARP_LEFT, TurnCueDetector.classifyTurn(-135f))
        assertEquals(TurnDirection.U_TURN, TurnCueDetector.classifyTurn(-170f))
    }

    @Test
    fun testDetectGeometricCues_rightAndLeftTurns() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0),
            PathPoint(distance = 55.0, latLng = LatLng(48.0005, 11.0), altitude = 0.0),
            PathPoint(distance = 110.0, latLng = LatLng(48.0010, 11.0), altitude = 0.0),
            PathPoint(distance = 170.0, latLng = LatLng(48.0010, 11.0008), altitude = 0.0),
            PathPoint(distance = 230.0, latLng = LatLng(48.0010, 11.0016), altitude = 0.0),
            PathPoint(distance = 285.0, latLng = LatLng(48.0005, 11.0016), altitude = 0.0),
            PathPoint(distance = 340.0, latLng = LatLng(48.0000, 11.0016), altitude = 0.0)
        )

        val cues = TurnCueDetector.detectGeometricCues(points)
        assertEquals(2, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
        assertEquals(TurnDirection.RIGHT, cues[1].direction)
    }

    @Test
    fun testExtractOrDetectCues_prefersExplicitWaypoints() {
        val summary = RouteSummary(
            id = 1L,
            externalId = "route_1",
            name = "Test Route",
            description = "Test description",
            isSelected = false,
            distance = 1000.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 0.0),
            PathPoint(distance = 1000.0, latLng = LatLng(48.01, 11.0), altitude = 0.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                id = 101L,
                routeId = 1L,
                latLng = LatLng(48.005, 11.0),
                name = "Turn Left onto Trail",
                type = WaypointType.TURN_LEFT,
                distanceFromStart = 500.0
            )
        )
        val route = RouteWithPath(summary, path, waypoints)

        val cues = TurnCueDetector.extractOrDetectCues(route)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.LEFT, cues[0].direction)
        assertEquals(500.0, cues[0].distanceFromStart, 0.01)
        assertEquals(101L, cues[0].id)
    }

    // --- ASPICE SWE.4 Requirements Coverage (TST-MAP-045.1 to TST-MAP-045.18) ---

    @Test
    fun testTST_MAP_045_1_sharp90DegreeIntersectionTurn() {
        // 50m North, 90 degree Right, 50m East (5m steps)
        val pts = mutableListOf<LatLng>()
        for (i in 0..10) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        for (i in 1..10) {
            pts.add(offsetLatLng(origin, 50.0, i * 5.0))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
        assertTrue("Apex within +-5m of corner (50m)", abs(cues[0].distanceFromStart - 50.0) <= 5.0)
    }

    @Test
    fun testTST_MAP_045_2_sweeping90DegreeCurveOver45Meters() {
        // 30m North, 45m curve turning 90 degrees Right (9 segments of 5m, 10 deg each), 30m East
        val pts = mutableListOf<LatLng>()
        var curN = 0.0
        var curE = 0.0
        pts.add(offsetLatLng(origin, curN, curE))

        // 30m straight
        for (i in 1..6) {
            curN += 5.0
            pts.add(offsetLatLng(origin, curN, curE))
        }

        // 45m sweeping curve (heading 0 to 90 degrees in 10 degree increments)
        var heading = 0.0
        for (i in 1..9) {
            heading += 10.0
            val rad = Math.toRadians(heading)
            curN += 5.0 * cos(rad)
            curE += 5.0 * sin(rad)
            pts.add(offsetLatLng(origin, curN, curE))
        }

        // 30m straight East
        for (i in 1..6) {
            curE += 5.0
            pts.add(offsetLatLng(origin, curN, curE))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
        assertTrue("Apex within sweeping curve (30m to 75m)", cues[0].distanceFromStart in 30.0..75.0)
    }

    @Test
    fun testTST_MAP_045_3_windingRoadGentleBendsSuppressed() {
        // 120m road with gentle weaving (< 20 degrees deflection)
        val pts = mutableListOf<LatLng>()
        var curN = 0.0
        var curE = 0.0
        pts.add(offsetLatLng(origin, curN, curE))

        for (i in 1..24) {
            // Alternating 10 degree heading wiggle (total delta 20 deg < 25 deg)
            val heading = if ((i / 4) % 2 == 0) 10.0 else -10.0
            val rad = Math.toRadians(heading)
            curN += 5.0 * cos(rad)
            curE += 5.0 * sin(rad)
            pts.add(offsetLatLng(origin, curN, curE))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue("Gentle bends (< 25 deg) must be suppressed", cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_4_constantCurvatureArcMidpointTieBreaking() {
        // 30m straight, circular arc of 6 segments of 5m (total 30m) turning 15 deg each (90 deg total), 30m straight
        val pts = mutableListOf<LatLng>()
        var curN = 0.0
        var curE = 0.0
        pts.add(offsetLatLng(origin, curN, curE))

        for (i in 1..6) {
            curN += 5.0
            pts.add(offsetLatLng(origin, curN, curE))
        }
        val arcStartDist = 30.0
        var heading = 0.0
        for (i in 1..6) {
            heading += 15.0
            val rad = Math.toRadians(heading)
            curN += 5.0 * cos(rad)
            curE += 5.0 * sin(rad)
            pts.add(offsetLatLng(origin, curN, curE))
        }
        val arcMidDist = 45.0
        for (i in 1..6) {
            curE += 5.0
            pts.add(offsetLatLng(origin, curN, curE))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
        assertTrue("Midpoint tie-breaking centers apex near 45m (+-5m)", abs(cues[0].distanceFromStart - arcMidDist) <= 5.0)
    }

    @Test
    fun testTST_MAP_045_5_hairpinSwitchbackConsolidatedToSingleUTurn() {
        // 40m North, sharp 180 switchback, 40m South
        val pts = mutableListOf<LatLng>()
        for (i in 0..8) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        // Hairpin apex points
        pts.add(offsetLatLng(origin, 42.0, 3.0))
        pts.add(offsetLatLng(origin, 42.0, 6.0))
        for (i in 8 downTo 0) {
            pts.add(offsetLatLng(origin, i * 5.0, 6.0))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.U_TURN, cues[0].direction)
    }

    @Test
    fun testTST_MAP_045_6_highFrequencyGpsJitterFiltered() {
        // 100m straight path with +-3m lateral jitter every 5m
        val pts = mutableListOf<LatLng>()
        for (i in 0..20) {
            val lateralNoise = if (i % 2 == 0) 3.0 else -3.0
            pts.add(offsetLatLng(origin, i * 5.0, lateralNoise))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue("High-frequency +-3m GPS jitter must produce 0 cues", cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_7_shortRouteSafelyReturnsEmptyList() {
        val pts = listOf(
            offsetLatLng(origin, 0.0, 0.0),
            offsetLatLng(origin, 5.0, 0.0),
            offsetLatLng(origin, 10.0, 0.0)
        )
        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue("Short route (< 20m) safely returns empty list", cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_8_fiveThousandPointBenchmarkUnder100Ms() {
        val pts = ArrayList<LatLng>(5000)
        var curN = 0.0
        var curE = 0.0
        for (i in 0 until 5000) {
            curN += 5.0
            pts.add(offsetLatLng(origin, curN, curE))
        }

        val duration = measureTimeMillis {
            val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
            assertTrue(cues.isEmpty())
        }
        assertTrue("5,000 points processed in < 100ms, was $duration ms", duration < 100)
    }

    @Test
    fun testTST_MAP_045_9_chicaneSeparationPreservesBothCues() {
        // Chicane: Left turn at 30m, 15m straight transition, Right turn at 45m
        val pts = mutableListOf<LatLng>()
        for (i in 0..6) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0)) // 30m North
        }
        // Turn Left (heading West)
        pts.add(offsetLatLng(origin, 30.0, -5.0))
        pts.add(offsetLatLng(origin, 30.0, -10.0))
        pts.add(offsetLatLng(origin, 30.0, -15.0)) // 15m West
        // Turn Right (heading North again)
        pts.add(offsetLatLng(origin, 35.0, -15.0))
        pts.add(offsetLatLng(origin, 40.0, -15.0))
        pts.add(offsetLatLng(origin, 45.0, -15.0))
        pts.add(offsetLatLng(origin, 50.0, -15.0))

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(2, cues.size)
        assertEquals(TurnDirection.LEFT, cues[0].direction)
        assertEquals(TurnDirection.RIGHT, cues[1].direction)
    }

    @Test
    fun testTST_MAP_045_10_boundaryConditionAtExactly20Meters() {
        val pts = listOf(
            offsetLatLng(origin, 0.0, 0.0),
            offsetLatLng(origin, 10.0, 0.0),
            offsetLatLng(origin, 20.0, 0.0)
        )
        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue(cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_11_straightRouteContinuousLateralJitterZeroCues() {
        val pts = mutableListOf<LatLng>()
        for (i in 0..60) {
            val noise = if (i % 2 == 0) 2.0 else -2.0
            pts.add(offsetLatLng(origin, i * 3.0, noise))
        }
        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue("Continuous +-2m lateral jitter produces 0 cues", cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_12_zeroLengthDuplicateSegmentsSanitizeCleanly() {
        val pts = mutableListOf<LatLng>()
        for (i in 0..6) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        // 5 duplicate points at corner (30m)
        val corner = offsetLatLng(origin, 30.0, 0.0)
        repeat(5) { pts.add(corner) }

        for (i in 1..6) {
            pts.add(offsetLatLng(origin, 30.0, i * 5.0))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
    }

    @Test
    fun testTST_MAP_045_13_thresholdOscillationSuppressesDuplicateCues() {
        // 30m North, then turn 26.0 deg with slight oscillation (24.9 -> 25.1 deg)
        val pts = mutableListOf<LatLng>()
        for (i in 0..6) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        var rad = Math.toRadians(26.0)
        pts.add(offsetLatLng(origin, 30.0 + 5.0 * cos(rad), 5.0 * sin(rad)))
        pts.add(offsetLatLng(origin, 30.0 + 10.0 * cos(rad), 10.0 * sin(rad)))
        rad = Math.toRadians(24.9)
        pts.add(offsetLatLng(origin, 30.0 + 15.0 * cos(rad), 15.0 * sin(rad)))
        pts.add(offsetLatLng(origin, 30.0 + 20.0 * cos(rad), 20.0 * sin(rad)))
        rad = Math.toRadians(25.1)
        pts.add(offsetLatLng(origin, 30.0 + 25.0 * cos(rad), 25.0 * sin(rad)))
        pts.add(offsetLatLng(origin, 30.0 + 30.0 * cos(rad), 30.0 * sin(rad)))
        pts.add(offsetLatLng(origin, 30.0 + 35.0 * cos(rad), 35.0 * sin(rad)))

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.SLIGHT_RIGHT, cues[0].direction)
    }

    @Test
    fun testTST_MAP_045_14_highFrequencyLateralNoiseZeroCues() {
        val pts = mutableListOf<LatLng>()
        for (i in 0..40) {
            val lateral = if (i % 2 == 0) 1.8 else -1.8
            pts.add(offsetLatLng(origin, i * 4.0, lateral))
        }
        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertTrue(cues.isEmpty())
    }

    @Test
    fun testTST_MAP_045_15_closedLoopRouteStartFinishBoundaryProtection() {
        // Closed loop: 30m North, 30m East, 30m South, 30m West returning to origin
        val pts = mutableListOf<LatLng>()
        for (i in 0..6) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        for (i in 1..6) {
            pts.add(offsetLatLng(origin, 30.0, i * 5.0))
        }
        for (i in 1..6) {
            pts.add(offsetLatLng(origin, 30.0 - i * 5.0, 30.0))
        }
        for (i in 1..6) {
            pts.add(offsetLatLng(origin, 0.0, 30.0 - i * 5.0))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        // Expected: corners at 30m, 60m, 90m. The finish window suppresses phantom cues at 120m (closure)
        assertEquals(3, cues.size)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
        assertEquals(TurnDirection.RIGHT, cues[1].direction)
        assertEquals(TurnDirection.RIGHT, cues[2].direction)
        assertTrue("No cue on finish boundary", cues.all { it.distanceFromStart <= 110.0 })
    }

    @Test
    fun testTST_MAP_045_17_immediateStartOfRouteTurnClampedToZero() {
        // Departure at 0m, 90 degree turn Right at 2m, then 40m East
        val pts = mutableListOf<LatLng>()
        pts.add(offsetLatLng(origin, 0.0, 0.0))
        pts.add(offsetLatLng(origin, 2.0, 0.0))
        for (i in 1..8) {
            pts.add(offsetLatLng(origin, 2.0, i * 5.0))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(0.0, cues[0].distanceFromStart, 0.01)
        assertEquals(TurnDirection.RIGHT, cues[0].direction)
    }

    @Test
    fun testTST_MAP_045_18_thresholdOscillationHysteresisBuffer() {
        // Candidate within 30m of same direction must increase by at least 5 degrees to refine
        val pts = mutableListOf<LatLng>()
        for (i in 0..6) {
            pts.add(offsetLatLng(origin, i * 5.0, 0.0))
        }
        // Turn 30 degrees Right
        var rad = Math.toRadians(30.0)
        for (i in 1..4) {
            pts.add(offsetLatLng(origin, 30.0 + i * 5.0 * cos(rad), i * 5.0 * sin(rad)))
        }
        // Small fluctuation +2 degrees (32 degrees total, < 30 + 5)
        rad = Math.toRadians(32.0)
        for (i in 5..8) {
            pts.add(offsetLatLng(origin, 30.0 + i * 5.0 * cos(rad), i * 5.0 * sin(rad)))
        }

        val cues = TurnCueDetector.detectGeometricCuesFromLatLng(pts)
        assertEquals(1, cues.size)
        assertEquals(TurnDirection.SLIGHT_RIGHT, cues[0].direction)
    }
}
