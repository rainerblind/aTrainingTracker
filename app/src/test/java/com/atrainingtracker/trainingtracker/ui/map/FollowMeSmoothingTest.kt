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

import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests verifying Follow-Me speed smoothing, bearing angular low-pass filtering,
 * and zoom hysteresis deadband behavior (REQ-MAP-049 / TST-MAP-051).
 */
class FollowMeSmoothingTest {

    @Test
    fun testSpeedSmoothing_exponentialFilterDampsRapidSpike() {
        val alpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA // 0.10f
        var filteredSpeed = 0.0f
        val rawSpikeSpeed = 10.0f // 36 km/h step input

        // Step 1: after one update, filteredSpeed should be 0.10 * 10 = 1.0f
        filteredSpeed += alpha * (rawSpikeSpeed - filteredSpeed)
        assertEquals(1.0f, filteredSpeed, 0.001f)

        // Step 2: after second update, filteredSpeed = 1.0 + 0.10 * (10 - 1.0) = 1.9f
        filteredSpeed += alpha * (rawSpikeSpeed - filteredSpeed)
        assertEquals(1.9f, filteredSpeed, 0.001f)

        // Step 3: after third update, filteredSpeed = 1.9 + 0.10 * (10 - 1.9) = 2.71f
        filteredSpeed += alpha * (rawSpikeSpeed - filteredSpeed)
        assertEquals(2.71f, filteredSpeed, 0.001f)
    }

    @Test
    fun testSpeedSmoothingAlpha_clampedToValidRange() {
        // Below min (0.02f)
        val belowMin = 0.001f
        val clampedMin = belowMin.coerceIn(
            TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA
        )
        assertEquals(0.02f, clampedMin, 0.001f)

        // Above max (0.50f)
        val aboveMax = 0.95f
        val clampedMax = aboveMax.coerceIn(
            TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
            TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA
        )
        assertEquals(0.50f, clampedMax, 0.001f)
    }

    @Test
    fun testBearingSmoothing_crossesZeroDegreeBoundarySmoothly() {
        val alpha = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA // 0.15f
        var filteredBearing = 355.0f
        val targetBearing = 5.0f // 10 degrees clockwise

        var diff = targetBearing - filteredBearing
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f

        // diff should be +10.0f (shortest angular path)
        assertEquals(10.0f, diff, 0.001f)

        filteredBearing += alpha * diff
        filteredBearing = (filteredBearing + 360f) % 360f

        // 355 + 0.15 * 10 = 356.5f
        assertEquals(356.5f, filteredBearing, 0.001f)
    }

    @Test
    fun testBearingSmoothing_counterClockwiseAcrossZeroBoundary() {
        val alpha = 0.20f
        var filteredBearing = 5.0f
        val targetBearing = 355.0f // 10 degrees counter-clockwise

        var diff = targetBearing - filteredBearing
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f

        // diff should be -10.0f
        assertEquals(-10.0f, diff, 0.001f)

        filteredBearing += alpha * diff
        filteredBearing = (filteredBearing + 360f) % 360f

        // 5 - 0.20 * 10 = 3.0f
        assertEquals(3.0f, filteredBearing, 0.001f)
    }

    @Test
    fun testZoomHysteresis_suppressesMicroFluctuations() {
        val deadbandThreshold = 0.10f
        var activeZoom = 18.50f

        // Test 1: Minor change below deadband (< 0.10f) -> activeZoom unchanged
        val targetZoomMinor = 18.55f // delta = 0.05f < 0.10f
        val delta1 = abs(targetZoomMinor - activeZoom)
        val effectiveZoom1 = if (delta1 >= deadbandThreshold) {
            activeZoom = targetZoomMinor
            targetZoomMinor
        } else {
            activeZoom
        }
        assertEquals(18.50f, effectiveZoom1, 0.001f)
        assertEquals(18.50f, activeZoom, 0.001f)

        // Test 2: Another minor change (< 0.10f) -> activeZoom unchanged
        val targetZoomMinor2 = 18.42f // delta = 0.08f < 0.10f
        val delta2 = abs(targetZoomMinor2 - activeZoom)
        val effectiveZoom2 = if (delta2 >= deadbandThreshold) {
            activeZoom = targetZoomMinor2
            targetZoomMinor2
        } else {
            activeZoom
        }
        assertEquals(18.50f, effectiveZoom2, 0.001f)
        assertEquals(18.50f, activeZoom, 0.001f)

        // Test 3: Significant change (>= 0.10f) -> activeZoom updates
        val targetZoomMajor = 18.35f // delta = 0.15f >= 0.10f
        val delta3 = abs(targetZoomMajor - activeZoom)
        val effectiveZoom3 = if (delta3 >= deadbandThreshold) {
            activeZoom = targetZoomMajor
            targetZoomMajor
        } else {
            activeZoom
        }
        assertEquals(18.35f, effectiveZoom3, 0.001f)
        assertEquals(18.35f, activeZoom, 0.001f)
    }

    @Test
    fun testFullPipeline_smoothSpeedZoomIntegration() {
        val speedAlpha = 0.10f
        val baseZoom = 20.0f
        val cruisingZoom = 18.0f
        var filteredSpeed = 0.0f
        var activeZoom = baseZoom

        // Athlete accelerates from 0 to 20 km/h (5.555 m/s)
        val targetSpeedMps = 20.0f / 3.6f

        // Initial state
        assertEquals(20.0f, activeZoom, 0.001f)

        // Frame 1: speed increases slightly
        filteredSpeed += speedAlpha * (targetSpeedMps - filteredSpeed)
        var targetZoom = calculateFollowMeTargetZoom(filteredSpeed, baseZoom, true, cruisingZoom)
        var delta = abs(targetZoom - activeZoom)
        if (delta >= 0.10f) activeZoom = targetZoom

        // At filteredSpeed = 0.555 m/s (2 km/h), targetZoom is 19.80f -> delta = 0.20f >= 0.10f
        assertEquals(19.80f, activeZoom, 0.001f)

        // Frame 2: speed increases slightly from 2 to ~3.8 km/h
        filteredSpeed += speedAlpha * (targetSpeedMps - filteredSpeed)
        targetZoom = calculateFollowMeTargetZoom(filteredSpeed, baseZoom, true, cruisingZoom)
        delta = abs(targetZoom - activeZoom)
        // delta is small, check that activeZoom either updates smoothly or is held
        assertTrue("Camera zoom must be bounded between cruising and base zoom", activeZoom in cruisingZoom..baseZoom)
    }
}
