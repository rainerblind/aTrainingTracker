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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying cross-domain normalized viewport start progress fraction mathematics,
 * zero-span safety, and multi-chart lockstep pan synchronization between Distance and Time
 * domains as mandated by REQ-UI-232, REQ-UI-234, and TST-UI-190 (ATT-1987).
 */
class MapDetailLayoutCrossDomainPanTest {

    @Test
    fun testFractionToDomain_mapsDistanceAndClampsWithinZoomLimits() {
        val totalDistance = 25000.0 // 25 km
        val zoomScale = 2.0f // max fraction = 0.5

        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(0.0, totalDistance, zoomScale), 0.001)
        assertEquals(6250.0, MapDetailViewportMath.fractionToDomain(0.25, totalDistance, zoomScale), 0.001)
        assertEquals(12500.0, MapDetailViewportMath.fractionToDomain(0.50, totalDistance, zoomScale), 0.001)
        // Values beyond max allowable fraction clamp cleanly to maxStart
        assertEquals(12500.0, MapDetailViewportMath.fractionToDomain(0.75, totalDistance, zoomScale), 0.001)
        assertEquals(12500.0, MapDetailViewportMath.fractionToDomain(1.0, totalDistance, zoomScale), 0.001)
    }

    @Test
    fun testFractionToDomain_mapsTimeAndClampsWithinZoomLimits() {
        val totalTimeSec = 3600.0 // 1 hour
        val zoomScale = 3.0f // max fraction = 1.0 - 1/3 = 0.6666... max start = 2400s

        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(0.0, totalTimeSec, zoomScale), 0.001)
        assertEquals(900.0, MapDetailViewportMath.fractionToDomain(0.25, totalTimeSec, zoomScale), 0.001)
        assertEquals(1800.0, MapDetailViewportMath.fractionToDomain(0.50, totalTimeSec, zoomScale), 0.001)
        assertEquals(2400.0, MapDetailViewportMath.fractionToDomain(0.80, totalTimeSec, zoomScale), 0.001)
    }

    @Test
    fun testDomainToFraction_mapsDistanceToNormalizedFraction() {
        val totalDistance = 25000.0
        val zoomScale = 2.0f // max fraction = 0.5

        assertEquals(0.0, MapDetailViewportMath.domainToFraction(0.0, totalDistance, zoomScale), 0.001)
        assertEquals(0.25, MapDetailViewportMath.domainToFraction(6250.0, totalDistance, zoomScale), 0.001)
        assertEquals(0.50, MapDetailViewportMath.domainToFraction(12500.0, totalDistance, zoomScale), 0.001)
        // Values past maxStart clamp to maxFraction
        assertEquals(0.50, MapDetailViewportMath.domainToFraction(20000.0, totalDistance, zoomScale), 0.001)
    }

    @Test
    fun testCrossDomainSynchronizedPanning_elevationDragUpdatesTelemetryInLockstep() {
        val totalDistance = 20000.0 // 20 km (Elevation Profile Domain)
        val totalTimeSec = 4000.0 // 4,000 s (Telemetry Graphs Domain)
        val zoomScale = 2.0f

        // Initial state: fraction 0.0
        var fraction = 0.0
        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(fraction, totalDistance, zoomScale), 0.001)
        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(fraction, totalTimeSec, zoomScale), 0.001)

        // Athlete drags ElevationProfile horizontally to start at 5,000m
        val elevationNewStart = 5000.0
        fraction = MapDetailViewportMath.domainToFraction(elevationNewStart, totalDistance, zoomScale)
        assertEquals(0.25, fraction, 0.001)

        // Projected to Telemetry domain: should start at 1,000s (25% of 4,000s)
        val telemetryNewStart = MapDetailViewportMath.fractionToDomain(fraction, totalTimeSec, zoomScale)
        assertEquals(1000.0, telemetryNewStart, 0.001)
    }

    @Test
    fun testCrossDomainSynchronizedPanning_telemetryDragUpdatesElevationInLockstep() {
        val totalDistance = 18000.0 // 18 km (Elevation Profile Domain)
        val totalTimeSec = 3600.0 // 1 hour (Telemetry Graphs Domain)
        val zoomScale = 4.0f // max fraction = 0.75

        // Athlete drags TelemetryMetricGraph horizontally to start at 900s (25% of activity)
        val telemetryNewStart = 900.0
        val fraction = MapDetailViewportMath.domainToFraction(telemetryNewStart, totalTimeSec, zoomScale)
        assertEquals(0.25, fraction, 0.001)

        // Projected to Elevation domain: should start at 4,500m (25% of 18,000m)
        val elevationNewStart = MapDetailViewportMath.fractionToDomain(fraction, totalDistance, zoomScale)
        assertEquals(4500.0, elevationNewStart, 0.001)
    }

    @Test
    fun testZeroAndNegativeSpanRobustness() {
        // Zero span
        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(0.5, 0.0, 2.0f), 0.0001)
        assertEquals(0.0, MapDetailViewportMath.domainToFraction(100.0, 0.0, 2.0f), 0.0001)

        // Negative span
        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(0.5, -100.0, 2.0f), 0.0001)
        assertEquals(0.0, MapDetailViewportMath.domainToFraction(100.0, -100.0, 2.0f), 0.0001)

        // Sub-1.0 zoom factor robustness
        assertEquals(0.0, MapDetailViewportMath.fractionToDomain(0.5, 1000.0, 0.5f), 0.0001)
        assertEquals(0.0, MapDetailViewportMath.domainToFraction(100.0, 1000.0, 0.5f), 0.0001)
    }
}
