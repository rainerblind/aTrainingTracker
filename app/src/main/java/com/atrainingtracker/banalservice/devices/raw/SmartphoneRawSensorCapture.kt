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

package com.atrainingtracker.banalservice.devices.raw

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.core.content.ContextCompat

/**
 * Self-contained raw smartphone sensor capture listener owned by [SmartphoneRawTelemetryRecorder]
 * (REQ-EXP-045.1, ATT-9348).
 *
 * Registers directly against Android [SensorManager] on a dedicated background [HandlerThread]
 * whenever an opt-in raw recording session starts/resumes, and unregisters cleanly on stop.
 * Decouples raw IMU (`ACC`, `GYRO`, `ROTVEC`), barometer (`BARO`), and step-detector (`STEP`)
 * capture from `SpeedAndLocationDevice_ATTFused` so recording works on default Google Fused /
 * GPS / indoor configurations and when toggled on post-startup without duplicate rows.
 */
internal class SmartphoneRawSensorCapture(
    private val context: Context,
    private val onEvent: (SensorEvent, Long) -> Unit = { event, epochMs ->
        SmartphoneRawTelemetryRecorder.recordSensorEvent(event, epochMs)
    },
    private val sensorManagerProvider: (Context) -> SensorManager? = { ctx ->
        ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    },
    private val useHandlerThread: Boolean = true
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var handlerThread: HandlerThread? = null
    private var registered = false

    @Synchronized
    fun start(): Boolean {
        if (registered) return true
        val sm = try {
            sensorManagerProvider(context)
        } catch (_: Throwable) {
            null
        } ?: return false

        val handler: Handler? = if (useHandlerThread) {
            try {
                HandlerThread("SmartphoneRawSensorCapture", Process.THREAD_PRIORITY_BACKGROUND).let { ht ->
                    ht.start()
                    handlerThread = ht
                    Handler(ht.looper)
                }
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        var anyRegistered = false
        try {
            fun register(sensor: Sensor?, delay: Int) {
                if (sensor == null) return
                val ok = if (handler != null) {
                    sm.registerListener(this, sensor, delay, handler)
                } else {
                    sm.registerListener(this, sensor, delay)
                }
                if (ok) anyRegistered = true
            }

            register(sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
            register(sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
            val rotVec = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            register(rotVec, SensorManager.SENSOR_DELAY_GAME)
            register(sm.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL)

            if (hasActivityRecognitionPermission()) {
                try {
                    register(sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), SensorManager.SENSOR_DELAY_NORMAL)
                } catch (_: SecurityException) {
                    // Platform denied step detector; continue capturing other sensors
                }
            }

            if (!anyRegistered) {
                stop()
                return false
            }

            sensorManager = sm
            registered = true
        } catch (_: Throwable) {
            stop()
            return false
        }
        return true
    }

    @Synchronized
    internal fun hasActiveHandlerThreadForTest(): Boolean = handlerThread != null

    @Synchronized
    fun stop() {
        val sm = sensorManager
        sensorManager = null
        registered = false
        if (sm != null) {
            try {
                sm.unregisterListener(this)
            } catch (_: Throwable) {
            }
        }
        val ht = handlerThread
        handlerThread = null
        if (ht != null) {
            try {
                ht.quitSafely()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor == null) return
        onEvent(event, System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                true
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Throwable) {
            false
        }
    }
}
