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
    fun testCalculateTargetZoom_highSpeed40Kmh_scalesLinearly() {
        // 40 km/h = 40 / 3.6 m/s -> slope = (20 - 18) / 20 = 0.1 -> zoom = 20 - 0.1 * 40 = 16.0f
        val speedMps = 40.0f / 3.6f
        val zoom = calculateFollowMeTargetZoom(
            speedMps = speedMps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f
        )
        assertEquals(16.0f, zoom, 0.001f)
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
    fun testCalculateTargetZoom_clampedToMinAndMax() {
        // Extreme high speed (120 km/h = 33.33 m/s) -> 20 - 0.1 * 120 = 8.0f -> clamped to minZoom 12.0f
        val extremeSpeedMps = 120.0f / 3.6f
        val clampedMin = calculateFollowMeTargetZoom(
            speedMps = extremeSpeedMps,
            baseZoom = 20.0f,
            speedZoomEnabled = true,
            cruisingZoom = 18.0f,
            minZoom = 12.0f,
            maxZoom = 21.0f
        )
        assertEquals(12.0f, clampedMin, 0.001f)

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
