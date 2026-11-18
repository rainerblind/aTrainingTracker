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
 * boundary clamping, and latitude scaling (REQ-UI-224, REQ-UI-2785.2, TST-UI-178.1, TST-UI-2785.1).
 */
class KnownLocationZoomMathTest {

    @Test
    fun testDefaultZoom_isElevenPointTwo() {
        assertEquals(11.2f, KnownLocationZoomMath.DEFAULT_THUMBNAIL_ZOOM, 0.0001f)
        assertEquals(120.0, KnownLocationZoomMath.DEFAULT_THUMBNAIL_HEIGHT_DP, 0.0001)
        assertEquals(9.7f, KnownLocationZoomMath.MIN_ZOOM, 0.0001f)
        assertEquals(11.7f, KnownLocationZoomMath.MAX_ZOOM, 0.0001f)
        assertEquals(0.35f, KnownLocationZoomMath.TARGET_GEOFENCE_DIAMETER_RATIO, 0.0001f)
    }

    @Test
    fun testSmallRadii_clampedToMaxZoom() {
        val latMunich = 48.137

        // Radii 50m, 100m, 200m, and 500m must clamp to MAX_ZOOM (11.7f) at 120dp height (REQ-UI-470.1, REQ-UI-2785.2)
        val zoom50 = KnownLocationZoomMath.calculateThumbnailZoom(50, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom50, 0.0001f)

        val zoom100 = KnownLocationZoomMath.calculateThumbnailZoom(100, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom100, 0.0001f)

        val zoom200 = KnownLocationZoomMath.calculateThumbnailZoom(200, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom200, 0.0001f)

        val zoom500 = KnownLocationZoomMath.calculateThumbnailZoom(500, latMunich)
        assertEquals(KnownLocationZoomMath.MAX_ZOOM, zoom500, 0.0001f)
    }

    @Test
    fun testMediumRadii_scalesProportionally() {
        val latMunich = 48.137

        // 1000m radius at 120dp height: unclamped raw zoom is ~11.10f
        val zoom1000 = KnownLocationZoomMath.calculateThumbnailZoom(1000, latMunich)
        assertTrue("Zoom for 1000m should be near 11.10f, was $zoom1000", zoom1000 in 10.95f..11.25f)

        // 1500m radius at 120dp height: unclamped raw zoom is ~10.51f
        val zoom1500 = KnownLocationZoomMath.calculateThumbnailZoom(1500, latMunich)
        assertTrue("Zoom for 1500m should be near 10.51f, was $zoom1500", zoom1500 in 10.35f..10.65f)

        // Monotonic progression: larger radius must yield smaller zoom (zoomed out further)
        assertTrue("1000m zoom must be greater than 1500m zoom", zoom1000 > zoom1500)
    }

    @Test
    fun testLargeRadii_clampedToMinZoom() {
        val latMunich = 48.137

        // 2000m radius at 120dp height: unclamped raw zoom is ~10.10f
        val zoom2000 = KnownLocationZoomMath.calculateThumbnailZoom(2000, latMunich)
        assertTrue("Zoom for 2000m should be near 10.10f, was $zoom2000", zoom2000 in 9.95f..10.25f)

        // >= 2700m radius (e.g. 3000m, 5000m): clamped to MIN_ZOOM (9.7f)
        val zoom3000 = KnownLocationZoomMath.calculateThumbnailZoom(3000, latMunich)
        assertEquals(KnownLocationZoomMath.MIN_ZOOM, zoom3000, 0.0001f)

        val zoom5000 = KnownLocationZoomMath.calculateThumbnailZoom(5000, latMunich)
        assertEquals(KnownLocationZoomMath.MIN_ZOOM, zoom5000, 0.0001f)
    }

    @Test
    fun testHeightCalibration_120dpShiftsUnclampedZoomByPlus0585Over80dp() {
        val latMunich = 48.137
        val zoom80 = KnownLocationZoomMath.calculateThumbnailZoom(1000, latMunich, thumbnailHeightDp = 80.0)
        val zoom120 = KnownLocationZoomMath.calculateThumbnailZoom(1000, latMunich)
        val expectedDelta = (kotlin.math.ln(120.0 / 80.0) / kotlin.math.ln(2.0)).toFloat()
        assertEquals(expectedDelta, zoom120 - zoom80, 0.001f)
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
