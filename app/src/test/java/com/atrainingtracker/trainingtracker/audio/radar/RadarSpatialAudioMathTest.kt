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

package com.atrainingtracker.trainingtracker.audio.radar

import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [RadarSpatialAudioMath] (TST-TRK-065.1, TST-TRK-065.2, TST-TRK-065.4).
 */
class RadarSpatialAudioMathTest {

    @Test
    fun computeStereoGains_whenDisabledOrNullOrSpeaker_returnsCenteredStereo() {
        // Disabled
        val disabledGains = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = -2.0f,
            isSpatialAudioEnabled = false
        )
        assertEquals(1.0f, disabledGains.first, 0.001f)
        assertEquals(1.0f, disabledGains.second, 0.001f)

        // Force Phone Speaker fallback invariant (REQ-TRK-065.1, TST-TRK-065.4)
        val speakerGains = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = -2.5f,
            route = RadarAudioRoute.FORCE_SPEAKER
        )
        assertEquals(1.0f, speakerGains.first, 0.001f)
        assertEquals(1.0f, speakerGains.second, 0.001f)

        // Null offset (e.g. BLE V1 or ANT+ radar)
        val nullGains = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = null
        )
        assertEquals(1.0f, nullGains.first, 0.001f)
        assertEquals(1.0f, nullGains.second, 0.001f)
    }

    @Test
    fun computeStereoGains_whenInLane_returnsCenteredStereo() {
        // Center of lane
        val center = RadarSpatialAudioMath.computeStereoGains(0.0f)
        assertEquals(1.0f, center.first, 0.001f)
        assertEquals(1.0f, center.second, 0.001f)

        // Slightly left in lane (|x| < 1.0m)
        val slightlyLeft = RadarSpatialAudioMath.computeStereoGains(-0.8f)
        assertEquals(1.0f, slightlyLeft.first, 0.001f)
        assertEquals(1.0f, slightlyLeft.second, 0.001f)

        // Slightly right in lane (|x| < 1.0m)
        val slightlyRight = RadarSpatialAudioMath.computeStereoGains(0.9f)
        assertEquals(1.0f, slightlyRight.first, 0.001f)
        assertEquals(1.0f, slightlyRight.second, 0.001f)
    }

    @Test
    fun computeStereoGains_whenPassingLane_pansToOvertakingEar() {
        // RHT vehicle overtaking on left (x <= -1.5m)
        val rhtLeft = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = -2.0f,
            trafficOrientation = RadarTrafficOrientation.RIGHT_HAND
        )
        assertEquals(1.0f, rhtLeft.first, 0.001f)
        assertEquals(0.20f, rhtLeft.second, 0.001f)

        // LHT vehicle overtaking on right (x >= 1.5m)
        val lhtRight = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = 2.0f,
            trafficOrientation = RadarTrafficOrientation.LEFT_HAND
        )
        assertEquals(0.20f, lhtRight.first, 0.001f)
        assertEquals(1.0f, lhtRight.second, 0.001f)
    }

    @Test
    fun computeStereoGains_linearInterpolation_betweenThresholds() {
        // Intermediate offset x = -1.25m (midpoint between 1.0m and 1.5m)
        val mid = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters = -1.25f,
            trafficOrientation = RadarTrafficOrientation.RIGHT_HAND
        )
        assertEquals(1.0f, mid.first, 0.001f)
        // 1.0 - 0.5 * (1.0 - 0.20) = 0.60
        assertEquals(0.60f, mid.second, 0.001f)
    }

    @Test
    fun computeSpeedPitch_linearScaleAndClamping() {
        // Disabled returns 1.0f
        assertEquals(1.0f, RadarSpatialAudioMath.computeSpeedPitch(20.0f, false), 0.001f)
        // Null returns 1.0f
        assertEquals(1.0f, RadarSpatialAudioMath.computeSpeedPitch(null, true), 0.001f)

        // Minimum speed clamp (<= 3.0 m/s -> 0.95x)
        assertEquals(0.95f, RadarSpatialAudioMath.computeSpeedPitch(1.0f, true), 0.001f)
        assertEquals(0.95f, RadarSpatialAudioMath.computeSpeedPitch(3.0f, true), 0.001f)

        // Maximum speed clamp (>= 30.0 m/s -> 1.35x)
        assertEquals(1.35f, RadarSpatialAudioMath.computeSpeedPitch(30.0f, true), 0.001f)
        assertEquals(1.35f, RadarSpatialAudioMath.computeSpeedPitch(40.0f, true), 0.001f)

        // Midpoint speed (16.5 m/s -> 1.15x)
        assertEquals(1.15f, RadarSpatialAudioMath.computeSpeedPitch(16.5f, true), 0.001f)
    }

    @Test
    fun isLaneChangeToSafePassing_detectsTransitionsCorrectly() {
        // Null checks
        assertFalse(RadarSpatialAudioMath.isLaneChangeToSafePassing(null, -2.0f))
        assertFalse(RadarSpatialAudioMath.isLaneChangeToSafePassing(0.0f, null))

        // In-lane to safe passing in RHT (x moves from 0.0m to -1.8m)
        assertTrue(RadarSpatialAudioMath.isLaneChangeToSafePassing(0.0f, -1.8f, RadarTrafficOrientation.RIGHT_HAND))

        // In-lane to safe passing in LHT (x moves from 0.0m to 1.8m)
        assertTrue(RadarSpatialAudioMath.isLaneChangeToSafePassing(0.0f, 1.8f, RadarTrafficOrientation.LEFT_HAND))

        // RHT vehicle moving to right side shoulder is not safe passing in RHT
        assertFalse(RadarSpatialAudioMath.isLaneChangeToSafePassing(0.0f, 1.8f, RadarTrafficOrientation.RIGHT_HAND))

        // Staying in-lane
        assertFalse(RadarSpatialAudioMath.isLaneChangeToSafePassing(0.0f, 0.5f, RadarTrafficOrientation.RIGHT_HAND))

        // Already passing
        assertFalse(RadarSpatialAudioMath.isLaneChangeToSafePassing(-2.0f, -2.2f, RadarTrafficOrientation.RIGHT_HAND))
    }
}
