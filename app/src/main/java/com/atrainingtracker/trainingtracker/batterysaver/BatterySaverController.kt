/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.batterysaver

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BatterySaverController(
    private val activity: Activity? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    val stateMachine: BatterySaverStateMachine = BatterySaverStateMachine(),
    private val brightnessApplier: ((Float) -> Unit)? = null
) : SensorEventListener {

    var brightnessMode: DisplayBrightnessMode = DisplayBrightnessMode.SYSTEM
        private set

    var customBrightness: Float = 0.30f
        private set

    val isEnabled: Boolean
        get() = brightnessMode != DisplayBrightnessMode.SYSTEM

    var isWakeupActive: Boolean = false
        private set

    var currentAppliedBrightness: Float = 1.0f
        private set

    private var lastSnapshot: TelemetrySnapshot = TelemetrySnapshot()
    private var wakeupTimerJob: Job? = null
    private var hysteresisJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isListeningToProximity = false

    init {
        activity?.let {
            sensorManager = it.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        }
    }

    fun setMode(mode: DisplayBrightnessMode, custom: Float = customBrightness) {
        brightnessMode = mode
        customBrightness = custom.coerceIn(DimmingLevel.SAFETY_FLOOR, 1.0f)

        when (mode) {
            DisplayBrightnessMode.SYSTEM -> {
                stopProximityListening()
                cancelWakeupTimer()
                hysteresisJob?.cancel()
                hysteresisJob = null
                applyBrightness(1.0f)
            }
            DisplayBrightnessMode.AUTO -> {
                startProximityListening()
                onWakeupEvent()
            }
            DisplayBrightnessMode.CUSTOM -> {
                stopProximityListening()
                cancelWakeupTimer()
                hysteresisJob?.cancel()
                hysteresisJob = null
                applyBrightness(customBrightness)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        setMode(if (enabled) DisplayBrightnessMode.AUTO else DisplayBrightnessMode.SYSTEM)
    }

    fun onWakeupEvent() {
        if (brightnessMode != DisplayBrightnessMode.AUTO) return

        cancelWakeupTimer()
        hysteresisJob?.cancel()
        hysteresisJob = null
        isWakeupActive = true
        applyBrightness(1.0f)

        wakeupTimerJob = scope.launch {
            delay(DimmingLevel.WAKEUP_DURATION_MS)
            isWakeupActive = false
            val targetLevel = stateMachine.evaluateRawLevel(lastSnapshot)
            stateMachine.forceLevel(targetLevel)
            applyBrightness(targetLevel.brightness)
        }
    }

    fun updateTelemetry(snapshot: TelemetrySnapshot, currentTimeMs: Long = System.currentTimeMillis()) {
        lastSnapshot = snapshot
        if (brightnessMode != DisplayBrightnessMode.AUTO) return

        val raw = stateMachine.evaluateRawLevel(snapshot)
        if (raw.brightness > stateMachine.currentLevel.brightness) {
            // Immediate upward transition (e.g. hill climb or high effort)
            hysteresisJob?.cancel()
            hysteresisJob = null
            stateMachine.forceLevel(raw)
            if (!isWakeupActive) {
                applyBrightness(raw.brightness)
            }
        } else if (raw.brightness < stateMachine.currentLevel.brightness) {
            // Downward transition with 3-second damping hysteresis
            if (hysteresisJob == null || !hysteresisJob!!.isActive) {
                hysteresisJob = scope.launch {
                    delay(DimmingLevel.DOWNWARD_HYSTERESIS_MS)
                    stateMachine.forceLevel(raw)
                    if (!isWakeupActive) {
                        applyBrightness(raw.brightness)
                    }
                }
            }
        } else {
            hysteresisJob?.cancel()
            hysteresisJob = null
        }
    }

    private fun cancelWakeupTimer() {
        wakeupTimerJob?.cancel()
        wakeupTimerJob = null
        isWakeupActive = false
    }

    internal fun applyBrightness(brightness: Float) {
        val target = if (brightness >= 1.0f) {
            1.0f
        } else {
            brightness.coerceIn(DimmingLevel.SAFETY_FLOOR, 1.0f)
        }
        currentAppliedBrightness = target

        if (brightnessApplier != null) {
            brightnessApplier.invoke(target)
            return
        }

        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = if (target >= 1.0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                target
            }
            window.attributes = lp
        }
    }

    fun startProximityListening() {
        if (isListeningToProximity || sensorManager == null || proximitySensor == null) return
        sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        isListeningToProximity = true
    }

    fun stopProximityListening() {
        if (!isListeningToProximity) return
        sensorManager?.unregisterListener(this)
        isListeningToProximity = false
    }

    fun release() {
        stopProximityListening()
        cancelWakeupTimer()
        hysteresisJob?.cancel()
        hysteresisJob = null
        applyBrightness(1.0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || brightnessMode != DisplayBrightnessMode.AUTO) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values.getOrNull(0) ?: return
            val maxRange = proximitySensor?.maximumRange ?: 5.0f
            // Near condition detected (hand hovering or waving over the sensor)
            if (distance < maxRange) {
                onWakeupEvent()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
