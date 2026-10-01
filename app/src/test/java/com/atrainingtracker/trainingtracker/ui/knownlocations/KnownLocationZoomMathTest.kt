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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [KnownLocationZoomMath] verifying calibrated thumbnail zoom calculations,
 * boundary clamping, and latitude scaling (REQ-UI-224, TST-UI-178.1).
 */
class KnownLocationZoomMathTest {

    @Test
    fun testDefaultZoom_isTwelve() {
        assertEquals(12.0f, KnownLocationZoomMath.DEFAULT_THUMBNAIL_ZOOM, 0.0001f)
        assertEquals(10.5f, KnownLocationZoomMath.MIN_ZOOM, 0.0001f)
        assertEquals(12.5f, KnownLocationZoomMath.MAX_ZOOM, 0.0001f)
        assertEquals(0.35f, KnownLocationZoomMath.TARGET_GEOFENCE_DIAMETER_RATIO, 0.0001f)
    }

    @Test
    fun testSmallRadii_clampedToMaxZoom() {
        val latMunich = 48.137

        // Radii 50m, 100m, and 200m must clamp to MAX_ZOOM (12.5f) to ensure rich neighborhood context (>= 1.4km)
        val zoom50 = KnownLocationZoomMath.calculateThumbnailZoom(50, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom50, 0.0001f)

        val zoom100 = KnownLocationZoomMath.calculateThumbnailZoom(100, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom100, 0.0001f)

        val zoom200 = KnownLocationZoomMath.calculateThumbnailZoom(200, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom200, 0.0001f)
    }

    @Test
    fun testMediumRadii_scalesProportionally() {
        val latMunich = 48.137

        // 300m radius: unclamped raw zoom is ~12.26f
        val zoom300 = KnownLocationZoomMath.calculateThumbnailZoom(300, latMunich)
        assertTrue("Zoom for 300m should be between 12.0f and 12.4f, was $zoom300", zoom300 in 12.0f..12.4f)

        // 500m radius: unclamped raw zoom is ~11.52f
        val zoom500 = KnownLocationZoomMath.calculateThumbnailZoom(500, latMunich)
        assertTrue("Zoom for 500m should be between 11.3f and 11.7f, was $zoom500", zoom500 in 11.3f..11.7f)

        // Monotonic progression: larger radius must yield smaller zoom (zoomed out further)
        assertTrue("300m zoom must be greater than 500m zoom", zoom300 > zoom500)
    }

    @Test
    fun testLargeRadii_clampedToMinZoom() {
        val latMunich = 48.137

        // 1000m radius: unclamped raw zoom is ~10.52f
        val zoom1000 = KnownLocationZoomMath.calculateThumbnailZoom(1000, latMunich)
        assertTrue("Zoom for 1000m should be near MIN_ZOOM", zoom1000 in 10.5f..10.6f)

        // 1500m radius: clamped to MIN_ZOOM (10.5f)
        val zoom1500 = KnownLocationZoomMath.calculateThumbnailZoom(1500, latMunich)
        assertEquals(KnownLocationZoomMath.MIN_ZOOM, zoom1500, 0.0001f)

        // 5000m radius: clamped to MIN_ZOOM (10.5f)
        val zoom5000 = KnownLocationZoomMath.calculateThumbnailZoom(5000, latMunich)
        assertEquals(KnownLocationZoomMath.MIN_ZOOM, zoom5000, 0.0001f)
    }

    @Test
    fun testLatitudeVariations_producesStableZoom() {
        val radius = 300

        val zoomEquator = KnownLocationZoomMath.calculateThumbnailZoom(radius, 0.0)
        val zoomMidLat = KnownLocationZoomMath.calculateThumbnailZoom(radius, 48.0)
        val zoomHighLat = KnownLocationZoomMath.calculateThumbnailZoom(radius, 70.0)

        // All should be validly clamped between MIN_ZOOM and MAX_ZOOM
        assertTrue(zoomEquator in KnownLocationZoomMath.MIN_ZOOM..KnownLocationZoomMath.MAX_ZOOM)
        assertTrue(zoomMidLat in KnownLocationZoomMath.MIN_ZOOM..KnownLocationZoomMath.MAX_ZOOM)
        assertTrue(zoomHighLat in KnownLocationZoomMath.MIN_ZOOM..KnownLocationZoomMath.MAX_ZOOM)
    }

    @Test
    fun testDegenerateInputs_clampsGracefully() {
        // Negative radius
        val zoomNegative = KnownLocationZoomMath.calculateThumbnailZoom(-100, 48.0)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoomNegative, 0.0001f)

        // Zero radius
        val zoomZero = KnownLocationZoomMath.calculateThumbnailZoom(0, 48.0)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoomZero, 0.0001f)

        // Extreme polar latitudes (should not produce NaN or Infinity)
        val zoomNorthPole = KnownLocationZoomMath.calculateThumbnailZoom(200, 90.0)
        assertTrue(zoomNorthPole in KnownLocationZoomMath.MIN_ZOOM..KnownLocationZoomMath.MAX_ZOOM)

        val zoomSouthPole = KnownLocationZoomMath.calculateThumbnailZoom(200, -90.0)
        assertTrue(zoomSouthPole in KnownLocationZoomMath.MIN_ZOOM..KnownLocationZoomMath.MAX_ZOOM)
    }
}
