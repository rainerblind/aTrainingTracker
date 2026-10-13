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

class TurnCueDetectorTest {

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
        // Path: starts heading North (0 lat to 0.002 lat), turns East (0.002 lat, 0 to 0.002 lng), turns South
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
}
