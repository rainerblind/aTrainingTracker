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

package com.atrainingtracker.trainingtracker.ui.map

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated unit test suite verifying ElevationProfile bounds sanitization,
 * plausibility protection, and aesthetic minimum span (REQ-UI-126, TST-UI-079, ATT-508).
 */
class ElevationProfileBoundsTest {

    private fun createPoint(distance: Double, altitude: Double): PathPoint {
        return PathPoint(distance = distance, altitude = altitude, latLng = LatLng(0.0, 0.0))
    }

    /**
     * Verifies that when corrupted extrema overrides (-63 m, +60 m) are passed for a flat coastal route (0-2 m),
     * the outlier overrides are clamped to the stream envelope, and a 20 m aesthetic vertical span
     * is enforced centered around the route midpoint (1.0 m), preventing horizontal line compression.
     */
    @Test
    fun testFlatCoastalProfileWithCorruptedOverrides_ClampsToStreamAndEnforcesMinSpan() {
        val pathPoints = listOf(
            createPoint(0.0, 0.0),
            createPoint(250.0, 1.0),
            createPoint(500.0, 2.0),
            createPoint(750.0, 1.5),
            createPoint(1000.0, 0.5)
        )

        // Historical corrupted overrides from pre-ATT-499 GPS pollution
        val bounds = calculateElevationBounds(
            pathPoints = pathPoints,
            minAltitudeOverride = -63.0,
            maxAltitudeOverride = 60.0,
            outlierToleranceMeters = 15.0,
            minSpanMeters = 20.0
        )

        // Clamped stream min = 0.0, stream max = 2.0 -> mid = 1.0 -> expanded min = -9.0, max = +11.0, range = 20.0
        assertEquals("Minimum altitude must be expanded around route midpoint", -9.0, bounds.min, 0.001)
        assertEquals("Maximum altitude must be expanded around route midpoint", 11.0, bounds.max, 0.001)
        assertEquals("Range must equal the aesthetic minimum span", 20.0, bounds.range, 0.001)
    }

    /**
     * Verifies that for a mountain route with true elevation changes, legitimate overrides
     * matching the stream bounds within tolerance are fully preserved without artificial clipping.
     */
    @Test
    fun testMountainProfileWithValidOverrides_PreservesDynamicRange() {
        val pathPoints = listOf(
            createPoint(0.0, 500.0),
            createPoint(2000.0, 850.0),
            createPoint(5000.0, 1200.0)
        )

        // Legitimate overrides closely aligned with stream
        val bounds = calculateElevationBounds(
            pathPoints = pathPoints,
            minAltitudeOverride = 495.0,
            maxAltitudeOverride = 1205.0,
            outlierToleranceMeters = 15.0,
            minSpanMeters = 20.0
        )

        assertEquals("Valid min override within tolerance must be preserved", 495.0, bounds.min, 0.001)
        assertEquals("Valid max override within tolerance must be preserved", 1205.0, bounds.max, 0.001)
        assertEquals("Range must reflect full mountain climb span", 710.0, bounds.range, 0.001)
    }

    /**
     * Verifies that a flat route without overrides enforces the minimum span centered around the route.
     */
    @Test
    fun testFlatRouteWithoutOverrides_EnforcesMinSpan() {
        val pathPoints = listOf(
            createPoint(0.0, 10.0),
            createPoint(500.0, 11.0)
        )

        val bounds = calculateElevationBounds(
            pathPoints = pathPoints,
            minAltitudeOverride = null,
            maxAltitudeOverride = null,
            minSpanMeters = 20.0
        )

        // mid = 10.5 -> min = 0.5, max = 20.5, range = 20.0
        assertEquals(0.5, bounds.min, 0.001)
        assertEquals(20.5, bounds.max, 0.001)
        assertEquals(20.0, bounds.range, 0.001)
    }

    /**
     * Verifies safe fallback when path points are empty.
     */
    @Test
    fun testEmptyPathPoints_FallsBackGracefully() {
        val bounds = calculateElevationBounds(
            pathPoints = emptyList(),
            minAltitudeOverride = 50.0,
            maxAltitudeOverride = 100.0
        )

        assertEquals(50.0, bounds.min, 0.001)
        assertEquals(100.0, bounds.max, 0.001)
        assertEquals(50.0, bounds.range, 0.001)
    }
}
