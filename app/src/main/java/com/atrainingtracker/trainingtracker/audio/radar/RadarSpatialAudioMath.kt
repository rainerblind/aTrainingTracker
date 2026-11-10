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
import kotlin.math.abs

/**
 * Pure mathematical kinematics utilities for 3D spatial radar audio panning and speed-adaptive pitch modulation (REQ-TRK-065).
 */
object RadarSpatialAudioMath {

    const val MIN_SPEED_MPS: Float = 3.0f
    const val MAX_SPEED_MPS: Float = 30.0f
    const val MIN_PITCH: Float = 0.95f
    const val MAX_PITCH: Float = 1.35f

    const val IN_LANE_THRESHOLD_METERS: Float = 1.0f
    const val PASSING_LANE_THRESHOLD_METERS: Float = 1.5f
    const val ATTENUATED_GAIN: Float = 0.20f

    /**
     * Computes left and right stereo gains [Pair(leftGain, rightGain)] from vehicle lateral offset (REQ-TRK-065.1).
     *
     * Invariants:
     * - If spatial audio is disabled, or [route] is [RadarAudioRoute.FORCE_SPEAKER], or [lateralOffsetMeters] is null,
     *   returns centered stereo [Pair(1.0f, 1.0f)] to preserve maximum phone speaker volume (TST-TRK-065.4).
     * - When in-lane (|x| < 1.0m), returns centered stereo [Pair(1.0f, 1.0f)].
     * - When in passing lane (|x| >= 1.5m):
     *   - In Right-Hand Traffic (RHT), vehicle overtaking on left (x <= -1.5m) pans to left ear: [Pair(1.0f, 0.20f)].
     *   - In Left-Hand Traffic (LHT), vehicle overtaking on right (x >= 1.5m) pans to right ear: [Pair(0.20f, 1.0f)].
     *   - Opposite passing side pans to the opposite ear.
     * - Smooth linear attenuation between 1.0m and 1.5m.
     */
    fun computeStereoGains(
        lateralOffsetMeters: Float?,
        trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND,
        isSpatialAudioEnabled: Boolean = true,
        route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT
    ): Pair<Float, Float> {
        if (!isSpatialAudioEnabled || route == RadarAudioRoute.FORCE_SPEAKER || lateralOffsetMeters == null) {
            return Pair(1.0f, 1.0f)
        }

        val absOffset = abs(lateralOffsetMeters)
        if (absOffset < IN_LANE_THRESHOLD_METERS) {
            return Pair(1.0f, 1.0f)
        }

        val fraction = ((absOffset - IN_LANE_THRESHOLD_METERS) / (PASSING_LANE_THRESHOLD_METERS - IN_LANE_THRESHOLD_METERS))
            .coerceIn(0.0f, 1.0f)
        val oppositeGain = 1.0f - fraction * (1.0f - ATTENUATED_GAIN)

        return if (lateralOffsetMeters < 0f) {
            // Target is laterally on the left
            Pair(1.0f, oppositeGain)
        } else {
            // Target is laterally on the right
            Pair(oppositeGain, 1.0f)
        }
    }

    /**
     * Linearly scales vehicle closing speed across [3.0 .. 30.0 m/s] to playback pitch multiplier [0.95x .. 1.35x] (REQ-TRK-065.2).
     */
    fun computeSpeedPitch(
        approachSpeedMps: Float?,
        isSpeedPitchEnabled: Boolean = true
    ): Float {
        if (!isSpeedPitchEnabled || approachSpeedMps == null) {
            return 1.0f
        }

        val clampedSpeed = approachSpeedMps.coerceIn(MIN_SPEED_MPS, MAX_SPEED_MPS)
        val fraction = (clampedSpeed - MIN_SPEED_MPS) / (MAX_SPEED_MPS - MIN_SPEED_MPS)
        return MIN_PITCH + fraction * (MAX_PITCH - MIN_PITCH)
    }

    /**
     * Determines whether a lateral lane position change represents transitioning from in-lane to safe passing lane (REQ-TRK-065.3).
     */
    fun isLaneChangeToSafePassing(
        previousOffsetMeters: Float?,
        currentOffsetMeters: Float?,
        trafficOrientation: RadarTrafficOrientation = RadarTrafficOrientation.RIGHT_HAND
    ): Boolean {
        if (previousOffsetMeters == null || currentOffsetMeters == null) return false
        val wasInLane = abs(previousOffsetMeters) < IN_LANE_THRESHOLD_METERS
        val isNowPassing = when (trafficOrientation) {
            RadarTrafficOrientation.RIGHT_HAND -> currentOffsetMeters <= -PASSING_LANE_THRESHOLD_METERS
            RadarTrafficOrientation.LEFT_HAND -> currentOffsetMeters >= PASSING_LANE_THRESHOLD_METERS
        }
        return wasInLane && isNowPassing
    }
}
