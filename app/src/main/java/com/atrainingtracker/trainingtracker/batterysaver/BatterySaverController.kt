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

    var isEnabled: Boolean = false
        private set

    var isWakeupActive: Boolean = false
        private set

    var currentAppliedBrightness: Float = 1.0f
        private set

    private var wakeupTimerJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isListeningToProximity = false

    init {
        activity?.let {
            sensorManager = it.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled

        if (enabled) {
            startProximityListening()
            // Start with full illumination for initial awareness
            onWakeupEvent()
        } else {
            stopProximityListening()
            cancelWakeupTimer()
            applyBrightness(1.0f)
        }
    }

    fun onWakeupEvent() {
        if (!isEnabled) return

        cancelWakeupTimer()
        isWakeupActive = true
        applyBrightness(1.0f)

        wakeupTimerJob = scope.launch {
            delay(DimmingLevel.WAKEUP_DURATION_MS)
            isWakeupActive = false
            applyBrightness(stateMachine.currentLevel.brightness)
        }
    }

    fun updateTelemetry(snapshot: TelemetrySnapshot, currentTimeMs: Long = System.currentTimeMillis()) {
        val level = stateMachine.update(snapshot, currentTimeMs)
        if (isEnabled && !isWakeupActive) {
            applyBrightness(level.brightness)
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
        applyBrightness(1.0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isEnabled) return
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
