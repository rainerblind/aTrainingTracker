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
 * Unit tests verifying analytical follow-me camera target calculation and
 * forward lookahead projection math (REQ-MAP-046 / TST-MAP-048.1).
 */
class FollowMeCameraTargetTest {

    private val munichLocation = LatLng(48.137154, 11.576124)

    @Test
    fun testZeroPadding_returnsCurrentLocationUnchanged() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 45f,
            zoom = 18f,
            lookaheadPaddingPercent = 0f,
            viewportHeightDp = 600f
        )
        assertEquals(munichLocation.latitude, target.latitude, 0.000001)
        assertEquals(munichLocation.longitude, target.longitude, 0.000001)
    }

    @Test
    fun testZeroViewportHeight_returnsCurrentLocationUnchanged() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 90f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 0f
        )
        assertEquals(munichLocation.latitude, target.latitude, 0.000001)
        assertEquals(munichLocation.longitude, target.longitude, 0.000001)
    }

    @Test
    fun testNorthBearing_offsetsLatitudeNorth() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )
        assertTrue(
            "Target latitude (${target.latitude}) must be north of current latitude (${munichLocation.latitude})",
            target.latitude > munichLocation.latitude
        )
        assertEquals(
            "Target longitude must remain unchanged for pure North heading",
            munichLocation.longitude,
            target.longitude,
            0.0001
        )
    }

    @Test
    fun testEastBearing_offsetsLongitudeEast() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 90f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )
        assertTrue(
            "Target longitude (${target.longitude}) must be east of current longitude (${munichLocation.longitude})",
            target.longitude > munichLocation.longitude
        )
        assertEquals(
            "Target latitude must remain unchanged for pure East heading",
            munichLocation.latitude,
            target.latitude,
            0.0001
        )
    }

    @Test
    fun testSouthBearing_offsetsLatitudeSouth() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 180f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )
        assertTrue(
            "Target latitude (${target.latitude}) must be south of current latitude (${munichLocation.latitude})",
            target.latitude < munichLocation.latitude
        )
        assertEquals(
            "Target longitude must remain unchanged for pure South heading",
            munichLocation.longitude,
            target.longitude,
            0.0001
        )
    }

    @Test
    fun testWestBearing_offsetsLongitudeWest() {
        val target = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 270f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )
        assertTrue(
            "Target longitude (${target.longitude}) must be west of current longitude (${munichLocation.longitude})",
            target.longitude < munichLocation.longitude
        )
        assertEquals(
            "Target latitude must remain unchanged for pure West heading",
            munichLocation.latitude,
            target.latitude,
            0.0001
        )
    }

    @Test
    fun testHigherZoom_reducesGroundOffset() {
        // At zoom 19, ground distance per pixel is half of zoom 18
        val targetZoom17 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 17f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )
        val targetZoom18 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f
        )

        val deltaLat17 = targetZoom17.latitude - munichLocation.latitude
        val deltaLat18 = targetZoom18.latitude - munichLocation.latitude

        assertTrue(
            "Delta at zoom 17 ($deltaLat17) must be roughly double delta at zoom 18 ($deltaLat18)",
            deltaLat17 > deltaLat18 * 1.8 && deltaLat17 < deltaLat18 * 2.2
        )
    }

    @Test
    fun testLookaheadPaddingScaling_increasesProportionally() {
        val target10 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 10f,
            viewportHeightDp = 600f,
            tiltAngle = 0f
        )
        val target30 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f,
            tiltAngle = 0f
        )
        val target50 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 50f,
            viewportHeightDp = 600f,
            tiltAngle = 0f
        )

        val delta10 = target10.latitude - munichLocation.latitude
        val delta30 = target30.latitude - munichLocation.latitude
        val delta50 = target50.latitude - munichLocation.latitude

        assertEquals("Delta at 30% must be 3x delta at 10%", delta10 * 3.0, delta30, 0.000001)
        assertEquals("Delta at 50% must be 5x delta at 10%", delta10 * 5.0, delta50, 0.000001)
    }

    @Test
    fun testTiltForeshorteningCompensation_scalesGroundOffsetAtSteepTilt() {
        val targetTilt0 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f,
            tiltAngle = 0f
        )
        val targetTilt70 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f,
            tiltAngle = 70f
        )

        val delta0 = targetTilt0.latitude - munichLocation.latitude
        val delta70 = targetTilt70.latitude - munichLocation.latitude

        val expectedFactor = 1.0 / Math.cos(Math.toRadians(70.0)) // ~2.9238
        val actualFactor = delta70 / delta0

        assertEquals("70 degree tilt should scale ground offset by ~2.92x to compensate foreshortening", expectedFactor, actualFactor, 0.01)
    }

    @Test
    fun testTiltForeshorteningCompensation_clampsExtremeTiltSafely() {
        val targetExtreme90 = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f,
            tiltAngle = 90f
        )
        val targetNegative = calculateFollowMeCameraTarget(
            currentLocation = munichLocation,
            bearing = 0f,
            zoom = 18f,
            lookaheadPaddingPercent = 30f,
            viewportHeightDp = 600f,
            tiltAngle = -30f
        )

        val deltaExtreme = targetExtreme90.latitude - munichLocation.latitude
        val deltaNegative = targetNegative.latitude - munichLocation.latitude

        assertTrue("Extreme 90 degree tilt should be clamped to valid finite double", !deltaExtreme.isNaN() && !deltaExtreme.isInfinite())
        assertTrue("Negative tilt should be clamped and positive", deltaNegative > 0)
    }
}

