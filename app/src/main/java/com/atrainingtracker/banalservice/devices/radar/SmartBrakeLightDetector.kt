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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 *    longitudinal deceleration a <= -2.0 m/s^2 is sustained for >= 250 ms at riding speeds v >= 2.5 m/s
 *    and speed derivative dv/dt <= 0.
 * 4. Auto-Bypass Gating: Automatically disabled when the connected radar peripheral possesses
 *    hardware-level brake sensing (Garmin Varia RearVue 820).
 * 5. Lifecycle Management: Registers [SensorEventListener] during active tracking and unregisters on
 *    pause, stop, or disconnect to protect battery life.
 */
object SmartBrakeLightDetector : SensorEventListener {

    private const val TAG = "SmartBrakeLightDetector"

    const val DECELERATION_THRESHOLD_MPS2 = -2.0f
    const val MIN_DECELERATION_DURATION_MS = 250L
    const val MIN_SPEED_MPS = 2.5f // ~9.0 km/h
    const val VERTICAL_SHOCK_THRESHOLD_MPS2 = 4.0f

    @Volatile
    var isEnabled: Boolean = true
        set(value) {
            synchronized(this) {
                field = value
                if (!value && isListening) {
                    stopListening()
                }
            }
        }

    @Volatile
    var isAutoBypassed: Boolean = false
        set(value) {
            synchronized(this) {
                field = value
                if (value && isListening) {
                    stopListening()
                }
            }
        }

    @Volatile
    var isListening: Boolean = false
        private set

    @Volatile
    var isBraking: Boolean = false
        private set

    @Volatile
    var currentSpeedMps: Float = 0f
        private set

    @Volatile
    var currentDvDt: Float = 0f
        private set

    @Volatile
    var lastForwardDeceleration: Float = 0f
        private set

    @Volatile
    var lastVerticalShock: Float = 0f
        private set

    @Volatile
    private var gravityX = 0f

    @Volatile
    private var gravityY = 0f

    @Volatile
    private var gravityZ = 9.81f

    @Volatile
    private var lastSpeedMps = 0f

    @Volatile
    private var lastSpeedTimestampMs = 0L

    @Volatile
    private var decelerationStartRealtimeMs = 0L

    private var sensorManager: SensorManager? = null

    /**
     * Android [SensorEventListener] callback.
     */
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                if (event.values.size >= 3) {
                    onGravityChanged(event.values[0], event.values[1], event.values[2])
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (event.values.size >= 3) {
                    onLinearAccelerationChanged(event.values[0], event.values[1], event.values[2])
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Registers IMU listeners with Android [SensorManager] during active cycling workout.
     */
    @JvmStatic
    @JvmOverloads
    fun startListening(context: Context, customSensorManager: SensorManager? = null) {
        synchronized(this) {
            if (isListening || !isEnabled || isAutoBypassed) return
            val sm = customSensorManager ?: (context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager) ?: return
            val linAcc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            if (linAcc != null) {
                sm.registerListener(this, linAcc, SensorManager.SENSOR_DELAY_GAME)
            }
            if (gravity != null) {
                sm.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
            }
            sensorManager = sm
            isListening = true
            Log.i(TAG, "SensorManager listeners registered for SmartBrakeLightDetector")
        }
    }

    /**
     * Unregisters IMU listeners from Android [SensorManager] to prevent battery drain.
     */
    @JvmStatic
    fun stopListening() {
        synchronized(this) {
            if (!isListening) return
            try {
                sensorManager?.unregisterListener(this)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering sensor listeners", e)
            }
            sensorManager = null
            isListening = false
            decelerationStartRealtimeMs = 0L
            if (isBraking) {
                endBraking(SystemClock.elapsedRealtime())
            }
            Log.i(TAG, "SensorManager listeners unregistered for SmartBrakeLightDetector")
        }
    }

    /**
     * Updates orientation/gravity vector from [android.hardware.Sensor.TYPE_GRAVITY].
     */
    @JvmStatic
    fun onGravityChanged(gx: Float, gy: Float, gz: Float) {
        synchronized(this) {
            gravityX = gx
            gravityY = gy
            gravityZ = gz
        }
    }

    /**
     * Updates current riding speed from GPS or wheel sensor telemetry and calculates dv/dt (REQ-CON-035.1).
     */
    @JvmStatic
    @JvmOverloads
    fun onSpeedChanged(
        speedMps: Float,
        nowMs: Long = SystemClock.elapsedRealtime()
    ) {
        synchronized(this) {
            if (lastSpeedTimestampMs > 0L) {
                val dt = (nowMs - lastSpeedTimestampMs) / 1000f
                if (dt > 0.05f) {
                    currentDvDt = (speedMps - lastSpeedMps) / dt
                    lastSpeedMps = speedMps
                    lastSpeedTimestampMs = nowMs
                } else if (speedMps != lastSpeedMps) {
                    currentDvDt = (speedMps - lastSpeedMps) / 0.1f
                    lastSpeedMps = speedMps
                    lastSpeedTimestampMs = nowMs
                }
            } else {
                lastSpeedMps = speedMps
                lastSpeedTimestampMs = nowMs
                currentDvDt = 0f
            }
            currentSpeedMps = speedMps
        }
    }

    /**
     * Overrides current dv/dt value directly for deterministic testing.
     */
    @JvmStatic
    fun setDvDtForTesting(dvDt: Float) {
        synchronized(this) {
            currentDvDt = dvDt
        }
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
        synchronized(this) {
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

            // 3. Minimum speed and speed derivative check (REQ-CON-035.1)
            // Riding speed must be >= 2.5 m/s (~9.0 km/h) and speed derivative dv/dt <= 0 (not accelerating)
            if (currentSpeedMps < MIN_SPEED_MPS || currentDvDt > 0.05f) {
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
        synchronized(this) {
            stopListening()
            isEnabled = true
            isAutoBypassed = false
            isBraking = false
            currentSpeedMps = 0f
            currentDvDt = 0f
            lastSpeedMps = 0f
            lastSpeedTimestampMs = 0L
            lastForwardDeceleration = 0f
            lastVerticalShock = 0f
            gravityX = 0f
            gravityY = 0f
            gravityZ = 9.81f
            decelerationStartRealtimeMs = 0L
        }
    }
}
