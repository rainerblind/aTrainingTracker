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
    fun testDefaultZoom_isElevenPointTwo() {
        assertEquals(11.2f, KnownLocationZoomMath.DEFAULT_THUMBNAIL_ZOOM, 0.0001f)
        assertEquals(9.7f, KnownLocationZoomMath.MIN_ZOOM, 0.0001f)
        assertEquals(11.7f, KnownLocationZoomMath.MAX_ZOOM, 0.0001f)
        assertEquals(0.35f, KnownLocationZoomMath.TARGET_GEOFENCE_DIAMETER_RATIO, 0.0001f)
    }

    @Test
    fun testSmallRadii_clampedToMaxZoom() {
        val latMunich = 48.137

        // Radii 50m, 100m, and 200m must clamp to MAX_ZOOM (11.7f) to ensure rich neighborhood context (REQ-UI-470.1)
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

        // 500m radius: unclamped raw zoom is ~11.51f
        val zoom500 = KnownLocationZoomMath.calculateThumbnailZoom(500, latMunich)
        assertTrue("Zoom for 500m should be between 11.3f and 11.7f, was $zoom500", zoom500 in 11.3f..11.7f)

        // 1000m radius: unclamped raw zoom is ~10.51f
        val zoom1000 = KnownLocationZoomMath.calculateThumbnailZoom(1000, latMunich)
        assertTrue("Zoom for 1000m should be between 10.4f and 10.7f, was $zoom1000", zoom1000 in 10.4f..10.7f)

        // Monotonic progression: larger radius must yield smaller zoom (zoomed out further)
        assertTrue("500m zoom must be greater than 1000m zoom", zoom500 > zoom1000)
    }

    @Test
    fun testLargeRadii_clampedToMinZoom() {
        val latMunich = 48.137

        // 1500m radius: unclamped raw zoom is ~9.93f
        val zoom1500 = KnownLocationZoomMath.calculateThumbnailZoom(1500, latMunich)
        assertTrue("Zoom for 1500m should be near 9.9f", zoom1500 in 9.8f..10.1f)

        // 2000m radius: clamped to MIN_ZOOM (9.7f)
        val zoom2000 = KnownLocationZoomMath.calculateThumbnailZoom(2000, latMunich)
        assertEquals(KnownLocationZoomMath.MIN_ZOOM, zoom2000, 0.0001f)

        // 5000m radius: clamped to MIN_ZOOM (9.7f)
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
