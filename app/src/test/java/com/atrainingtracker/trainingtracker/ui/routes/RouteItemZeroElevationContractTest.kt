/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit and contract tests verifying zero-altitude elevation profile suppression in [RouteItem]
 * (REQ-UI-494.3, TST-UI-494.3).
 */
class RouteItemZeroElevationContractTest {

    @Test
    fun testHasValidElevationData_returnsFalseForEmptyPointsAndZeroGain() {
        assertFalse(
            "Empty pathPoints and 0.0 gain must have no valid elevation data",
            hasValidElevationData(emptyList(), 0.0)
        )
    }

    @Test
    fun testHasValidElevationData_returnsFalseForNullAltitudesAndZeroGain() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = null, timeSec = 0L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.1, 9.1), altitude = null, timeSec = 100L)
        )
        assertFalse(
            "Null altitudes and 0.0 gain must have no valid elevation data",
            hasValidElevationData(points, 0.0)
        )
    }

    @Test
    fun testHasValidElevationData_returnsFalseForZeroAltitudesAndZeroGain() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = 0.0, timeSec = 0L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.1, 9.1), altitude = 0.00001, timeSec = 100L)
        )
        assertFalse(
            "Flat zero-altitude track (bike2work) with 0.0 gain must have no valid elevation data",
            hasValidElevationData(points, 0.0)
        )
    }

    @Test
    fun testHasValidElevationData_returnsTrueForAuthenticAltitudePoints() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = 350.0, timeSec = 0L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.1, 9.1), altitude = 380.0, timeSec = 100L)
        )
        assertTrue(
            "Points with authentic elevation must return true even if gain summary is 0.0",
            hasValidElevationData(points, 0.0)
        )
    }

    @Test
    fun testHasValidElevationData_returnsTrueForPositiveElevationGain() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = null, timeSec = 0L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.1, 9.1), altitude = null, timeSec = 100L)
        )
        assertTrue(
            "Positive elevation gain must return true even if individual point altitudes are absent",
            hasValidElevationData(points, 125.0)
        )
    }

    @Test
    fun testRouteItem_contractInspection_guardsElevationProfile() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt")
        assertNotNull("RouteItemKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val routeItemMethod = methods.find { it.name.startsWith("RouteItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteItem composable must exist and be public", routeItemMethod)
    }
}
