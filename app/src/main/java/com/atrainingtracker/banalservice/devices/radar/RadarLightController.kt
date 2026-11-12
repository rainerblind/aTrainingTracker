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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Central controller coordinating Garmin Varia BLE and ANT+ radar tail light modes (REQ-CON-031, REQ-CON-035).
 *
 * Manages mode selection, StVZO capability filtering, non-blocking GATT/ANT+ command
 * dispatch via [RadarLightDeviceClient.setLightMode], priority smartphone deceleration
 * brake-light triggering with 2.0s post-brake hold, and automated workout lighting
 * lifecycle transitions without duplicate BLE pairing or database storage.
 */
object RadarLightController {

    private const val TAG = "RadarLightController"

    const val POST_BRAKE_HOLD_DURATION_MS = 2000L

    private var activeRadarDevice: RadarLightDeviceClient? = null

    private val _currentMode = MutableStateFlow(RadarLightMode.OFF)
    @get:JvmStatic
    val currentMode: StateFlow<RadarLightMode> = _currentMode.asStateFlow()

    private val _isLightControlAvailable = MutableStateFlow(false)
    @get:JvmStatic
    val isLightControlAvailable: StateFlow<Boolean> = _isLightControlAvailable.asStateFlow()

    private val _isStvzo = MutableStateFlow(false)
    @get:JvmStatic
    val isStvzo: StateFlow<Boolean> = _isStvzo.asStateFlow()

    private val _hasHardwareBrakeSensor = MutableStateFlow(false)
    @get:JvmStatic
    val hasHardwareBrakeSensor: StateFlow<Boolean> = _hasHardwareBrakeSensor.asStateFlow()

    private val _isAutoBypassed = MutableStateFlow(false)
    @get:JvmStatic
    val isAutoBypassed: StateFlow<Boolean> = _isAutoBypassed.asStateFlow()

    var isBrakeActive: Boolean = false
        private set

    private var isWorkoutActive: Boolean = false
    private var appContext: Context? = null

    private var preBrakeMode: RadarLightMode = RadarLightMode.OFF
    private var brakeHoldUntilRealtimeMs: Long = 0L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val brakeHoldRunnable = Runnable {
        synchronized(this@RadarLightController) {
            if (!isBrakeActive) return@Runnable
        }
        checkAndRestorePreBrakeMode(SystemClock.elapsedRealtime())
    }

    /**
     * Binds a connected [RadarLightDeviceClient] peripheral (BLE or ANT+) to the controller.
     */
    @JvmStatic
    @JvmOverloads
    fun registerRadarDevice(
        device: RadarLightDeviceClient,
        lightSupported: Boolean,
        isStvzo: Boolean = false,
        hasHardwareBrakeSensor: Boolean = false
    ) {
        val shouldStartListening: Boolean
        val ctx: Context?
        synchronized(this) {
            activeRadarDevice = device
            _isLightControlAvailable.value = lightSupported
            _isStvzo.value = isStvzo
            _hasHardwareBrakeSensor.value = hasHardwareBrakeSensor
            _isAutoBypassed.value = hasHardwareBrakeSensor
            SmartBrakeLightDetector.isAutoBypassed = hasHardwareBrakeSensor
            shouldStartListening = isWorkoutActive && lightSupported && SmartBrakeLightDetector.isEnabled && !hasHardwareBrakeSensor
            ctx = appContext
        }
        if (hasHardwareBrakeSensor || !lightSupported) {
            SmartBrakeLightDetector.stopListening()
        } else if (shouldStartListening && ctx != null) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = lightSupported))
        Log.i(TAG, "Registered radar device (lightSupported=$lightSupported, isStvzo=$isStvzo, hasHardwareBrake=$hasHardwareBrakeSensor)")
    }

    /**
     * Unbinds a disconnected [RadarLightDeviceClient] peripheral.
     */
    @JvmStatic
    fun unregisterRadarDevice(device: RadarLightDeviceClient) {
        synchronized(this) {
            if (activeRadarDevice === device) {
                activeRadarDevice = null
                _isLightControlAvailable.value = false
                _hasHardwareBrakeSensor.value = false
                _isAutoBypassed.value = false
                SmartBrakeLightDetector.isAutoBypassed = false
                _currentMode.value = RadarLightMode.OFF
                isBrakeActive = false
                try {
                    handler.removeCallbacks(brakeHoldRunnable)
                } catch (_: Exception) {}
            }
        }
        SmartBrakeLightDetector.stopListening()
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF))
        Log.i(TAG, "Unregistered radar device")
    }

    private var cachedAutoLightControl: Boolean = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
    private var cachedLightDefaultMode: RadarLightMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE

    @JvmStatic
    fun updateCachedPreferences(autoControl: Boolean, defaultMode: RadarLightMode) {
        synchronized(this) {
            cachedAutoLightControl = autoControl
            cachedLightDefaultMode = defaultMode
        }
    }

    /**
     * Updates active mode confirmed by GATT telemetry/read (REQ-CON-031.1).
     */
    @JvmStatic
    fun updateHardwareLightMode(mode: RadarLightMode) {
        synchronized(this) {
            _currentMode.value = mode
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(lightMode = mode))
    }

    /**
     * Dispatches a light mode command to the active radar peripheral or simulator.
     *
     * Automatically prunes flashing modes under StVZO regulations to [RadarLightMode.SOLID].
     * Mutates [_currentMode] and [RadarStateRepository] strictly when command dispatch succeeds (REQ-CON-031.2).
     *
     * @param mode Target tail light operating mode.
     * @return `true` if command was dispatched or simulated successfully.
     */
    @JvmStatic
    fun setLightMode(mode: RadarLightMode): Boolean {
        val targetMode = if (_isStvzo.value && !mode.isStvzoCompliant) {
            Log.w(TAG, "Mode $mode is non-StVZO compliant; falling back to SOLID")
            RadarLightMode.SOLID
        } else {
            mode
        }

        val device = synchronized(this) { activeRadarDevice }
        var dispatched = false
        if (device != null && _isLightControlAvailable.value) {
            dispatched = device.setLightMode(targetMode)
        }

        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.getState().isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
            dispatched = true
        }

        if (dispatched) {
            _currentMode.value = targetMode
            val current = RadarStateRepository.getState()
            RadarStateRepository.updateState(current.copy(lightMode = targetMode))
        }

        return dispatched
    }

    /**
     * Returns the list of permitted modes, pruning flashing modes when StVZO hardware is indicated.
     */
    @JvmStatic
    fun getAvailableModes(): List<RadarLightMode> {
        return RadarLightMode.getAvailableModes(_isStvzo.value)
    }

    private fun resolveActiveConfig(context: Context?): Pair<Boolean, RadarLightMode> {
        if (context != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(context).getTuningConfig()
                updateCachedPreferences(cfg.radarAutoLightControl, cfg.radarLightDefaultMode)
                SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                return Pair(cfg.radarAutoLightControl, cfg.radarLightDefaultMode)
            } catch (_: Throwable) {
                // Ignore and use cached
            }
        }
        return synchronized(this) {
            Pair(cachedAutoLightControl, cachedLightDefaultMode)
        }
    }

    /**
     * Workout start hook (REQ-CON-031.4, REQ-CON-035.7): turns on tail light if auto-switch is enabled
     * and activates SmartBrakeLightDetector IMU listening ONLY for cycling workouts when light control is available.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStarted(context: Context? = null, isCycling: Boolean = true) {
        if (!isCycling) {
            Log.d(TAG, "Non-cycling workout started; skipping radar light and brake detection")
            return
        }
        synchronized(this) {
            isWorkoutActive = true
            if (context != null) {
                appContext = context.applicationContext
            }
        }
        val (autoControl, defaultMode) = resolveActiveConfig(context)
        if (autoControl) {
            setLightMode(defaultMode)
        }
        val ctx = context?.applicationContext ?: synchronized(this) { appContext }
        val isLightAvailable = synchronized(this) { _isLightControlAvailable.value || RadarTrafficSimulator.isSimulating }
        if (ctx != null && isLightAvailable && SmartBrakeLightDetector.isEnabled && !SmartBrakeLightDetector.isAutoBypassed) {
            SmartBrakeLightDetector.startListening(ctx)
        }
    }

    /**
     * Workout pause hook: stops IMU listening to conserve battery and clears pending brake holds.
     */
    @JvmStatic
    fun onWorkoutPaused() {
        synchronized(this) {
            isBrakeActive = false
            brakeHoldUntilRealtimeMs = 0L
            try {
                handler.removeCallbacks(brakeHoldRunnable)
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
    }

    /**
     * Workout resume hook: resumes IMU listening and restores default mode if configured.
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutResumed(context: Context? = null, isCycling: Boolean = true) {
        onWorkoutStarted(context, isCycling)
    }

    /**
     * Workout finish/stop hook (REQ-CON-031.4): cancels pending holds, stops IMU listening,
     * and reverts tail light to OFF if auto-switch is enabled.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStopped(context: Context? = null) {
        synchronized(this) {
            isWorkoutActive = false
            isBrakeActive = false
            brakeHoldUntilRealtimeMs = 0L
            preBrakeMode = RadarLightMode.OFF
            try {
                handler.removeCallbacks(brakeHoldRunnable)
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
        SmartBrakeLightDetector.reset()
        val (autoControl, _) = resolveActiveConfig(context)
        if (autoControl) {
            setLightMode(RadarLightMode.OFF)
            val device = synchronized(this) { activeRadarDevice }
            device?.disconnectLight()
        }
        synchronized(this) {
            appContext = null
        }
    }

    /**
     * Triggers priority deceleration brake light or schedules 2.0s post-brake hold (REQ-CON-035.3, REQ-CON-035.4).
     *
     * Overrides SmartRadarLightEngine rate-limiting for immediate safety alerting.
     * When [active] is true, dispatches brake mode (clamped to SOLID if StVZO or dark/night environment).
     * When [active] is false, holds brake mode for 2.0s before restoring pre-brake mode.
     */
    @JvmStatic
    @JvmOverloads
    fun triggerBrakeLight(
        active: Boolean,
        brakeMode: RadarLightMode = RadarLightMode.DAY_FLASH,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        synchronized(this) {
            if (_hasHardwareBrakeSensor.value) {
                Log.d(TAG, "Hardware brake sensor present; software trigger bypassed")
                return false
            }

            if (active) {
                try {
                    handler.removeCallbacks(brakeHoldRunnable)
                } catch (_: Exception) {}

                if (!isBrakeActive) {
                    preBrakeMode = _currentMode.value
                    isBrakeActive = true
                }
                brakeHoldUntilRealtimeMs = nowMs + POST_BRAKE_HOLD_DURATION_MS

                // Anti-glare and StVZO clamping invariants:
                // 1. StVZO units are permanently clamped to SOLID (REQ-CON-035.4).
                // 2. Non-StVZO units ridden in dark/tunnel/night environments are clamped to SOLID (25 lm)
                //    and MUST NEVER emit DAY_FLASH (100 lm) to avoid dazzling following road users (Finding 4).
                val isDarkEnvironment = SmartRadarLightEngine.isTunnelActive ||
                        (SmartRadarLightEngine.currentSolarProfile == SolarElevationCalculator.SolarProfile.DUSK_NIGHT) ||
                        (preBrakeMode == RadarLightMode.NIGHT_FLASH)

                val targetBrakeMode = if (_isStvzo.value || isDarkEnvironment) {
                    RadarLightMode.SOLID
                } else {
                    brakeMode
                }

                return setLightMode(targetBrakeMode)
            } else {
                if (isBrakeActive) {
                    brakeHoldUntilRealtimeMs = nowMs + POST_BRAKE_HOLD_DURATION_MS
                    try {
                        handler.removeCallbacks(brakeHoldRunnable)
                        handler.postDelayed(brakeHoldRunnable, POST_BRAKE_HOLD_DURATION_MS)
                    } catch (_: Exception) {
                        // Test environment fallback
                    }
                }
                return true
            }
        }
    }

    /**
     * Evaluates whether post-brake hold window has expired and restores pre-brake operating mode (REQ-CON-035.3).
     */
    @JvmStatic
    @JvmOverloads
    fun checkAndRestorePreBrakeMode(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(this) {
            if (!isBrakeActive) return false
            if (nowMs >= brakeHoldUntilRealtimeMs) {
                isBrakeActive = false
                val restored = setLightMode(preBrakeMode)
                Log.i(TAG, "Post-brake 2.0s hold expired; restored pre-brake mode: $preBrakeMode")
                if (SmartRadarLightEngine.isAutomationEnabled) {
                    SmartRadarLightEngine.evaluateAndDispatch(nowMs)
                }
                return restored
            }
            return false
        }
    }

    /**
     * Checks if a device model string indicates a Garmin Varia RearVue 820 unit with onboard hardware brake sensor (REQ-CON-035.5).
     */
    @JvmStatic
    fun isRearVue820Model(model: String?): Boolean {
        if (model == null) return false
        val upper = model.uppercase(Locale.US)
        return upper.contains("010-03180") ||
               upper.contains("REARVUE 820") ||
               upper.contains("REARVUE820")
    }

    /**
     * Resets controller state for unit test isolation.
     */
    @JvmStatic
    fun reset() {
        synchronized(this) {
            activeRadarDevice = null
            isWorkoutActive = false
            appContext = null
            _isLightControlAvailable.value = false
            _isStvzo.value = false
            _hasHardwareBrakeSensor.value = false
            _isAutoBypassed.value = false
            _currentMode.value = RadarLightMode.OFF
            isBrakeActive = false
            preBrakeMode = RadarLightMode.OFF
            brakeHoldUntilRealtimeMs = 0L
            cachedAutoLightControl = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
            cachedLightDefaultMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
            try {
                handler.removeCallbacks(brakeHoldRunnable)
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
        SmartBrakeLightDetector.reset()
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF))
    }
}
