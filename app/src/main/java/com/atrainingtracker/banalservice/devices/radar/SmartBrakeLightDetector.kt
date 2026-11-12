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

package com.atrainingtracker.banalservice.devices.radar

import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Smartphone IMU & GPS deceleration brake-light detector (REQ-CON-035, ATT-5121).
 *
 * Coordinates:
 * 1. Attitude Compensation: Fuses [android.hardware.Sensor.TYPE_LINEAR_ACCELERATION] with
 *    [android.hardware.Sensor.TYPE_GRAVITY] to project dynamic motion onto horizontal and vertical planes.
 * 2. Vertical Shock & Pothole Rejection: Rejects vertical spikes (|a_v| > 4.0 m/s^2) that lack
 *    sustained forward deceleration.
 * 3. Forward Deceleration Trigger: Emits a confirmed Brake Event to [RadarLightController] when
 *    longitudinal deceleration a <= -2.0 m/s^2 is sustained for >= 250 ms at riding speeds v >= 2.5 m/s.
 * 4. Auto-Bypass Gating: Automatically disabled when the connected radar peripheral possesses
 *    hardware-level brake sensing (Garmin Varia RearVue 820).
 */
object SmartBrakeLightDetector {

    private const val TAG = "SmartBrakeLightDetector"

    const val DECELERATION_THRESHOLD_MPS2 = -2.0f
    const val MIN_DECELERATION_DURATION_MS = 250L
    const val MIN_SPEED_MPS = 2.5f // ~9.0 km/h
    const val VERTICAL_SHOCK_THRESHOLD_MPS2 = 4.0f

    var isEnabled: Boolean = true
    var isAutoBypassed: Boolean = false

    var isBraking: Boolean = false
        private set

    var currentSpeedMps: Float = 0f
        private set

    var lastForwardDeceleration: Float = 0f
        private set

    var lastVerticalShock: Float = 0f
        private set

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.81f

    private var decelerationStartRealtimeMs = 0L

    /**
     * Updates orientation/gravity vector from [android.hardware.Sensor.TYPE_GRAVITY].
     */
    @JvmStatic
    fun onGravityChanged(gx: Float, gy: Float, gz: Float) {
        gravityX = gx
        gravityY = gy
        gravityZ = gz
    }

    /**
     * Updates current riding speed from GPS or wheel sensor telemetry.
     */
    @JvmStatic
    fun onSpeedChanged(speedMps: Float) {
        currentSpeedMps = speedMps
    }

    /**
     * Primary IMU sensor callback processing linear acceleration [android.hardware.Sensor.TYPE_LINEAR_ACCELERATION].
     *
     * @param ax X-axis linear acceleration (m/s^2)
     * @param ay Y-axis linear acceleration (m/s^2)
     * @param az Z-axis linear acceleration (m/s^2)
     * @param nowMs Current elapsed realtime in ms (overridable for testing)
     */
    @JvmStatic
    @JvmOverloads
    fun onLinearAccelerationChanged(
        ax: Float,
        ay: Float,
        az: Float,
        nowMs: Long = SystemClock.elapsedRealtime()
    ) {
        if (!isEnabled || isAutoBypassed) {
            if (isBraking) {
                endBraking(nowMs)
            }
            return
        }

        // 1. Attitude compensation via normalized gravity unit vector
        val gNorm = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        val (normGx, normGy, normGz) = if (gNorm > 0.001f) {
            Triple(gravityX / gNorm, gravityY / gNorm, gravityZ / gNorm)
        } else {
            Triple(0f, 0f, 1f)
        }

        // Vertical acceleration component along gravity vector: a_v = a_lin . g_norm
        val verticalAcc = ax * normGx + ay * normGy + az * normGz
        lastVerticalShock = abs(verticalAcc)

        // Forward longitudinal deceleration along bike travel axis:
        // In standard phone mount (portrait mode, screen tilted up/forward), forward horizontal axis is projected +Y
        // Horizontal forward unit vector: f = (0, 1, 0) - ((0, 1, 0) . g_norm) * g_norm
        val fDotG = normGy
        val fX = -fDotG * normGx
        val fY = 1f - fDotG * normGy
        val fZ = -fDotG * normGz
        val fNorm = sqrt(fX * fX + fY * fY + fZ * fZ)

        val forwardAcc = if (fNorm > 0.1f) {
            (ax * fX + ay * fY + az * fZ) / fNorm
        } else {
            // Phone mounted flat / horizontal: forward direction defaults to +Y
            ay
        }
        lastForwardDeceleration = forwardAcc

        // 2. Vertical shock & pothole rejection
        // If vertical shock is excessive without sustained forward deceleration, reset accumulator
        if (lastVerticalShock > VERTICAL_SHOCK_THRESHOLD_MPS2 && forwardAcc > DECELERATION_THRESHOLD_MPS2) {
            decelerationStartRealtimeMs = 0L
            if (isBraking) {
                endBraking(nowMs)
            }
            return
        }

        // 3. Minimum speed check
        if (currentSpeedMps < MIN_SPEED_MPS) {
            decelerationStartRealtimeMs = 0L
            if (isBraking) {
                endBraking(nowMs)
            }
            return
        }

        // 4. Deceleration threshold & duration gating
        if (forwardAcc <= DECELERATION_THRESHOLD_MPS2) {
            if (decelerationStartRealtimeMs == 0L) {
                decelerationStartRealtimeMs = nowMs
            }
            val elapsed = nowMs - decelerationStartRealtimeMs
            if (elapsed >= MIN_DECELERATION_DURATION_MS && !isBraking) {
                isBraking = true
                Log.i(TAG, "Deceleration brake event triggered (a=$forwardAcc m/s^2, sustained=${elapsed}ms)")
                RadarLightController.triggerBrakeLight(active = true, nowMs = nowMs)
            }
        } else {
            decelerationStartRealtimeMs = 0L
            if (isBraking) {
                endBraking(nowMs)
            }
        }
    }

    private fun endBraking(nowMs: Long) {
        isBraking = false
        decelerationStartRealtimeMs = 0L
        Log.i(TAG, "Deceleration brake event ended; releasing to post-brake hold window")
        RadarLightController.triggerBrakeLight(active = false, nowMs = nowMs)
    }

    /**
     * Resets detector state for unit test isolation.
     */
    @JvmStatic
    fun reset() {
        isEnabled = true
        isAutoBypassed = false
        isBraking = false
        currentSpeedMps = 0f
        lastForwardDeceleration = 0f
        lastVerticalShock = 0f
        gravityX = 0f
        gravityY = 0f
        gravityZ = 9.81f
        decelerationStartRealtimeMs = 0L
    }
}
