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
import org.junit.Test

/**
 * Unit tests verifying speed-dependent camera zoom curve calculation and tilt angle bounds (REQ-MAP-042 / TST-MAP-044.1 / TST-MAP-044.3).
 */
class FollowMeCameraZoomTest {

    @Test
    fun testCalculateTargetZoom_stationary_returnsBaseZoom() {
        val zoom = calculateFollowMeTargetZoom(
            speedMps = 0.0f,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(20.0f, zoom, 0.001f)
    }

    @Test
    fun testCalculateTargetZoom_cruisingSpeed20Kmh_returnsCruisingZoom() {
        // 20 km/h = 20 / 3.6 m/s
        val speedMps = 20.0f / 3.6f
        val zoom = calculateFollowMeTargetZoom(
            speedMps = speedMps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(18.0f, zoom, 0.001f)
    }

    @Test
    fun testCalculateTargetZoom_moderateSpeed10Kmh_scalesLinearly() {
        // 10 km/h = 10 / 3.6 m/s -> linear slope: 20 - (2/20)*10 = 19.0f
        val speedMps = 10.0f / 3.6f
        val zoom = calculateFollowMeTargetZoom(
            speedMps = speedMps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(19.0f, zoom, 0.001f)
    }

    @Test
    fun testCalculateTargetZoom_highSpeed_scalesLogarithmically() {
        // 30 km/h: 18.0 - log2(30/20) = 18.0 - 0.58496 = 17.415f
        val speed30Mps = 30.0f / 3.6f
        val zoom30 = calculateFollowMeTargetZoom(
            speedMps = speed30Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(17.415f, zoom30, 0.01f)

        // 40 km/h: 18.0 - log2(40/20) = 18.0 - 1.0 = 17.00f
        val speed40Mps = 40.0f / 3.6f
        val zoom40 = calculateFollowMeTargetZoom(
            speedMps = speed40Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(17.00f, zoom40, 0.001f)

        // 60 km/h: 18.0 - log2(60/20) = 18.0 - 1.58496 = 16.415f
        val speed60Mps = 60.0f / 3.6f
        val zoom60 = calculateFollowMeTargetZoom(
            speedMps = speed60Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(16.415f, zoom60, 0.01f)

        // 80 km/h: 18.0 - log2(80/20) = 18.0 - 2.0 = 16.00f
        val speed80Mps = 80.0f / 3.6f
        val zoom80 = calculateFollowMeTargetZoom(
            speedMps = speed80Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(16.00f, zoom80, 0.001f)
    }

    @Test
    fun testCalculateTargetZoom_clampedAtMinZoom16() {
        // 100 km/h and 120 km/h exceed 80 km/h -> clamped at default minZoom = 16.0f
        val speed100Mps = 100.0f / 3.6f
        val zoom100 = calculateFollowMeTargetZoom(
            speedMps = speed100Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(16.0f, zoom100, 0.001f)

        val speed120Mps = 120.0f / 3.6f
        val zoom120 = calculateFollowMeTargetZoom(
            speedMps = speed120Mps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(16.0f, zoom120, 0.001f)
    }

    @Test
    fun testCalculateTargetZoom_speedZoomDisabled_returnsConstantBaseZoom() {
        val speeds = listOf(0.0f, 5.0f, 10.0f, 25.0f) // m/s
        for (speed in speeds) {
            val zoom = calculateFollowMeTargetZoom(
                speedMps = speed,
                baseZoom = 19.5f,
                speedZoomEnabled = false,
                cruisingZoom = 17.5f
            )
            assertEquals("Zoom must remain locked to baseZoom when speedZoomEnabled is false", 19.5f, zoom, 0.001f)
        }
    }

    @Test
    fun testCalculateTargetZoom_negativeSpeed_coercedToZero() {
        // Negative speed -> coerced to 0 km/h -> baseZoom 20.0f
        val negativeSpeed = calculateFollowMeTargetZoom(
            speedMps = -5.0f,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(20.0f, negativeSpeed, 0.001f)
    }

    @Test
    fun testFollowMeCameraTilt_respectsConfiguredAngleAndClamps() {
        val belowMin = (-10.0f).coerceIn(
            TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE,
            TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE
        )
        val aboveMax = 85.0f.coerceIn(
            TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE,
            TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE
        )
        val nominal = 45.0f.coerceIn(
            TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_TILT_ANGLE,
            TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_TILT_ANGLE
        )

        assertEquals(0.0f, belowMin, 0.001f)
        assertEquals(70.0f, aboveMax, 0.001f)
        assertEquals(45.0f, nominal, 0.001f)
    }
}
