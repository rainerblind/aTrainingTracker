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
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Central controller coordinating Garmin Varia BLE and ANT+ radar tail light modes (REQ-CON-031, REQ-CON-035).
 *
 * Manages mode selection, StVZO capability filtering, non-blocking GATT/ANT+ command
 * dispatch via [RadarLightDeviceClient.setLightMode], priority smartphone deceleration
 * brake-light triggering with 2.0s post-brake hold, and automated workout lighting
 * lifecycle transitions without duplicate BLE pairing or database storage.
 */
data class RadarLightSelection(
    val isAuto: Boolean,
    val mode: RadarLightMode
)

object RadarLightController {

    private const val TAG = "RadarLightController"

    const val POST_BRAKE_HOLD_DURATION_MS = 2000L
    const val DEBOUNCE_DELAY_MS = 400L

    private var activeRadarDevice: RadarLightDeviceClient? = null

    private val _currentMode = MutableStateFlow(RadarLightMode.OFF)
    @get:JvmStatic
    val currentMode: StateFlow<RadarLightMode> = _currentMode.asStateFlow()

    private val _isAutoMode = MutableStateFlow(true)
    @get:JvmStatic
    val isAutoMode: StateFlow<Boolean> = _isAutoMode.asStateFlow()

    private val _preferredMode = MutableStateFlow(TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE)
    @get:JvmStatic
    val preferredMode: StateFlow<RadarLightMode> = _preferredMode.asStateFlow()

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
    private var isCyclingSession: Boolean = false
    private var appContext: Context? = null

    private var preBrakeMode: RadarLightMode = RadarLightMode.OFF
    private var brakeHoldUntilRealtimeMs: Long = 0L

    private var pendingTargetMode: RadarLightMode? = null

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val brakeHoldRunnable = Runnable {
        synchronized(this@RadarLightController) {
            if (!isBrakeActive) return@Runnable
        }
        checkAndRestorePreBrakeMode(SystemClock.elapsedRealtime())
    }

    private val debouncedDispatchRunnable = Runnable {
        flushDebouncedDispatch()
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
            shouldStartListening = isWorkoutActive && isCyclingSession && lightSupported && SmartBrakeLightDetector.isEnabled && !hasHardwareBrakeSensor
            ctx = appContext
        }
        if (hasHardwareBrakeSensor || !lightSupported) {
            SmartBrakeLightDetector.stopListening()
        } else if (shouldStartListening && ctx != null) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        RadarStateRepository.updateControlState {
            it.copy(isLightControlSupported = lightSupported, isAutoMode = _isAutoMode.value)
        }
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
                _isStvzo.value = false
                _hasHardwareBrakeSensor.value = false
                _isAutoBypassed.value = false
                SmartBrakeLightDetector.isAutoBypassed = false
                _currentMode.value = RadarLightMode.OFF
                isBrakeActive = false
                pendingTargetMode = null
                try {
                    handler.removeCallbacks(brakeHoldRunnable)
                    handler.removeCallbacks(debouncedDispatchRunnable)
                } catch (_: Exception) {}
            }
        }
        SmartBrakeLightDetector.stopListening()
        RadarStateRepository.updateControlState {
            it.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF, isAutoMode = _isAutoMode.value)
        }
        Log.i(TAG, "Unregistered radar device")
    }

    private var cachedAutoMode: Boolean = TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE
    private var cachedAutoLightControl: Boolean = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
    private var cachedLightDefaultMode: RadarLightMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
    private var cachedControlMode: RadarLightControlMode = TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE
    private var hasLoadedInitialPreferences: Boolean = false
    private var hasUserModifiedSelection: Boolean = false

    @JvmStatic
    fun updateCachedPreferences(autoMode: Boolean, defaultMode: RadarLightMode) {
        synchronized(this) {
            cachedAutoMode = autoMode
            cachedLightDefaultMode = defaultMode
            cachedAutoLightControl = !autoMode
            cachedControlMode = if (autoMode) RadarLightControlMode.SMART_AUTOMATION else RadarLightControlMode.FIXED_WORKOUT_MODE
            hasLoadedInitialPreferences = true
        }
        _isAutoMode.value = autoMode
        _preferredMode.value = defaultMode
        RadarStateRepository.updateControlState { it.copy(isAutoMode = autoMode) }
    }

    @JvmStatic
    @Deprecated("Use updateCachedPreferences(autoMode: Boolean, defaultMode: RadarLightMode)")
    fun updateCachedPreferences(controlMode: RadarLightControlMode, defaultMode: RadarLightMode) {
        synchronized(this) {
            cachedControlMode = controlMode
            cachedLightDefaultMode = defaultMode
            when (controlMode) {
                RadarLightControlMode.SMART_AUTOMATION -> {
                    cachedAutoMode = true
                }
                RadarLightControlMode.FIXED_WORKOUT_MODE -> {
                    cachedAutoMode = false
                }
                RadarLightControlMode.MANUAL -> {
                    cachedAutoMode = false
                    cachedLightDefaultMode = RadarLightMode.OFF
                }
            }
            cachedAutoLightControl = (controlMode == RadarLightControlMode.FIXED_WORKOUT_MODE)
            hasLoadedInitialPreferences = true
        }
        val isAuto = (controlMode == RadarLightControlMode.SMART_AUTOMATION)
        val resolvedDefault = synchronized(this) { cachedLightDefaultMode }
        _isAutoMode.value = isAuto
        _preferredMode.value = resolvedDefault
        RadarStateRepository.updateControlState { it.copy(isAutoMode = isAuto) }
    }

    /**
     * Hydrates persisted [isAutoMode] and [preferredMode] from [TuningPreferencesDataStore] on cold start
     * prior to workout start (`REQ-CON-048.2`, `ATT-8450`). Safe to call from plain-JVM tests.
     */
    @JvmStatic
    @JvmOverloads
    fun hydrateFromPreferences(context: Context? = null): Boolean {
        synchronized(this) {
            if (hasLoadedInitialPreferences) return true
        }
        val ctx = context?.applicationContext ?: context ?: synchronized(this) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull()
        if (ctx != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(ctx).getTuningConfig()
                applyHydratedPreferences(cfg.radarLightAutoMode, cfg.radarLightDefaultMode, cfg.radarBrakeLightEnabled)
                return true
            } catch (_: Throwable) {
                // Ignore uninitialized or mock context failures
            }
        }
        return false
    }

    /**
     * Suspends to read the initial persisted DataStore preferences for Compose/UI entry points (`REQ-CON-048.2`, `ATT-8450`).
     * Never overwrites an explicit in-session user selection made before disk load finishes.
     */
    suspend fun hydrateFromPreferencesAsync(context: Context) {
        hydrateFromPreferences(context)
        val appCtx = runCatching { context.applicationContext ?: context }.getOrNull() ?: return
        val filesDir = runCatching { appCtx.filesDir }.getOrNull() ?: return
        if (filesDir.javaClass != java.io.File::class.java) return
        try {
            val cfg = TuningPreferencesDataStore.getInstance(appCtx).tuningConfigFlow.first()
            applyHydratedPreferences(cfg.radarLightAutoMode, cfg.radarLightDefaultMode, cfg.radarBrakeLightEnabled)
        } catch (_: Throwable) {
            // Ignore in isolated/unattached contexts
        }
    }

    private fun applyHydratedPreferences(autoMode: Boolean, defaultMode: RadarLightMode, brakeLightEnabled: Boolean) {
        val applied = synchronized(this) {
            if (hasUserModifiedSelection) return
            cachedAutoMode = autoMode
            cachedLightDefaultMode = defaultMode
            cachedAutoLightControl = !autoMode
            cachedControlMode = if (autoMode) RadarLightControlMode.SMART_AUTOMATION else RadarLightControlMode.FIXED_WORKOUT_MODE
            hasLoadedInitialPreferences = true
            true
        }
        if (applied) {
            SmartBrakeLightDetector.isEnabled = brakeLightEnabled
            SmartRadarLightEngine.isAutomationEnabled = autoMode
            _isAutoMode.value = autoMode
            _preferredMode.value = defaultMode
            RadarStateRepository.updateControlState { it.copy(isAutoMode = autoMode) }
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
        RadarStateRepository.updateControlState { it.copy(lightMode = mode, isAutoMode = _isAutoMode.value) }
    }

    /**
     * Reconciles optimistic UI state when a peripheral GATT characteristic write fails (REQ-CON-031.2, ATT-7737).
     * Reverts [_currentMode] and [RadarStateRepository] to [confirmedFallbackMode] (or [RadarLightMode.OFF] if unknown)
     * when the displayed mode still matches the rejected [failedMode].
     */
    @JvmStatic
    fun onHardwareLightModeWriteFailed(failedMode: RadarLightMode, confirmedFallbackMode: RadarLightMode?) {
        val fallback = confirmedFallbackMode ?: RadarLightMode.OFF
        val shouldRevert = synchronized(this) {
            if (_currentMode.value == failedMode) {
                _currentMode.value = fallback
                true
            } else {
                false
            }
        }
        if (shouldRevert) {
            RadarStateRepository.updateControlState { it.copy(lightMode = fallback, isAutoMode = _isAutoMode.value) }
        }
        Log.w(TAG, "Hardware light mode write rejected for $failedMode; reconciled state to $fallback (reverted=$shouldRevert)")
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
            RadarStateRepository.updateControlState { it.copy(lightMode = targetMode, isAutoMode = _isAutoMode.value) }
        }

        return dispatched
    }

    /**
     * User-initiated selection of Smart Auto mode (REQ-CON-031.3, REQ-CON-034).
     * Resumes smart automation via [SmartRadarLightEngine.resumeAutomation].
     */
    @JvmStatic
    fun setAutoMode() {
        synchronized(this) {
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
            pendingTargetMode = null
            _isAutoMode.value = true
            hasUserModifiedSelection = true
        }
        updateCachedPreferences(autoMode = true, defaultMode = synchronized(this) { cachedLightDefaultMode })
        SmartRadarLightEngine.resumeAutomation()
        RadarStateRepository.updateControlState { it.copy(isAutoMode = true) }
        persistSelectionAsync(isAuto = true, mode = null)
        Log.i(TAG, "User selected Automatik mode")
    }

    /**
     * User-initiated mode selection from Cockpit sheet (REQ-CON-031.3, REQ-CON-034).
     * Suspends smart light automation via [SmartRadarLightEngine.onManualOverride] and dispatches command.
     */
    @JvmStatic
    fun setManualLightMode(mode: RadarLightMode): Boolean {
        synchronized(this) {
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
            pendingTargetMode = null
            _isAutoMode.value = false
            hasUserModifiedSelection = true
        }
        val targetMode = if (_isStvzo.value && !mode.isStvzoCompliant) RadarLightMode.SOLID else mode
        updateCachedPreferences(autoMode = false, defaultMode = targetMode)
        SmartRadarLightEngine.onManualOverride(targetMode)
        val result = setLightMode(targetMode)
        RadarStateRepository.updateControlState { it.copy(isAutoMode = false, lightMode = _currentMode.value) }
        persistSelectionAsync(isAuto = false, mode = targetMode)
        return result
    }

    /**
     * Cycles through available radar tail light modes in ascending intensity order with ~400 ms hardware command debouncing (REQ-CON-031.8, REQ-CON-047.3).
     *
     * Non-StVZO sequence: AUTO -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH -> AUTO.
     * StVZO sequence: AUTO -> OFF -> SOLID -> AUTO.
     *
     * Updates UI-observable target state immediately for zero-latency visual feedback, while coalescing
     * outbound hardware dispatch over [DEBOUNCE_DELAY_MS] ms.
     *
     * @return The updated [RadarLightSelection].
     */
    @JvmStatic
    fun cycleNextMode(): RadarLightSelection {
        val availableModes = RadarLightMode.getAvailableModes(_isStvzo.value)
        val currentIsAuto = _isAutoMode.value
        val currentExplicit = pendingTargetMode ?: _currentMode.value

        if (currentIsAuto) {
            return applyDebouncedModeStep(availableModes.first())
        } else {
            val currentIndex = availableModes.indexOf(currentExplicit)
            val nextMode: RadarLightMode? = if (currentIndex in 0 until availableModes.lastIndex) {
                availableModes[currentIndex + 1]
            } else if (currentIndex < 0) {
                availableModes.first()
            } else {
                null // back to AUTO
            }

            return if (nextMode == null) {
                setAutoMode()
                RadarLightSelection(isAuto = true, mode = _currentMode.value)
            } else {
                applyDebouncedModeStep(nextMode)
            }
        }
    }

    private fun applyDebouncedModeStep(targetMode: RadarLightMode): RadarLightSelection {
        synchronized(this) {
            _isAutoMode.value = false
            pendingTargetMode = targetMode
            _currentMode.value = targetMode
            hasUserModifiedSelection = true
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
                handler.postDelayed(debouncedDispatchRunnable, DEBOUNCE_DELAY_MS)
            } catch (_: Exception) {}
        }
        updateCachedPreferences(autoMode = false, defaultMode = targetMode)
        SmartRadarLightEngine.onManualOverride(targetMode)
        RadarStateRepository.updateControlState { it.copy(isAutoMode = false, lightMode = targetMode) }
        persistSelectionAsync(isAuto = false, mode = targetMode)
        return RadarLightSelection(isAuto = false, mode = targetMode)
    }

    private fun flushDebouncedDispatch() {
        val target = synchronized(this) {
            val t = pendingTargetMode
            pendingTargetMode = null
            t
        } ?: return
        val targetMode = if (_isStvzo.value && !target.isStvzoCompliant) {
            RadarLightMode.SOLID
        } else {
            target
        }
        val device = synchronized(this) { activeRadarDevice }
        if (device != null && _isLightControlAvailable.value) {
            device.setLightMode(targetMode)
        }
        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.getState().isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
        }
    }

    @VisibleForTesting
    fun flushDebounce() {
        flushDebouncedDispatch()
    }

    private fun persistSelectionAsync(isAuto: Boolean, mode: RadarLightMode?) {
        val ctx = synchronized(this) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull()
        if (ctx != null) {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    TuningPreferencesDataStore.getInstance(ctx).updateRadarLightSelection(isAuto, mode)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Returns the list of permitted modes, pruning flashing modes when StVZO hardware is indicated.
     */
    @JvmStatic
    fun getAvailableModes(): List<RadarLightMode> {
        return RadarLightMode.getAvailableModes(_isStvzo.value)
    }

    private data class ActiveLightConfig(
        val isAutoMode: Boolean,
        val defaultMode: RadarLightMode,
        val controlMode: RadarLightControlMode = if (isAutoMode) RadarLightControlMode.SMART_AUTOMATION else RadarLightControlMode.FIXED_WORKOUT_MODE
    )

    private fun resolveActiveConfig(context: Context?): ActiveLightConfig {
        val ctx = context ?: synchronized(this) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull()
        if (ctx != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(ctx).getTuningConfig()
                SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                synchronized(this) {
                    if (!hasLoadedInitialPreferences) {
                        cachedAutoMode = cfg.radarLightAutoMode
                        cachedLightDefaultMode = cfg.radarLightDefaultMode
                        cachedAutoLightControl = !cfg.radarLightAutoMode
                        cachedControlMode = if (cfg.radarLightAutoMode) RadarLightControlMode.SMART_AUTOMATION else RadarLightControlMode.FIXED_WORKOUT_MODE
                        hasLoadedInitialPreferences = true
                        _isAutoMode.value = cfg.radarLightAutoMode
                        _preferredMode.value = cfg.radarLightDefaultMode
                    }
                }
                SmartRadarLightEngine.isAutomationEnabled = synchronized(this) { cachedAutoMode }
                return synchronized(this) {
                    ActiveLightConfig(cachedAutoMode, cachedLightDefaultMode, cachedControlMode)
                }
            } catch (_: Throwable) {
                // Ignore and use cached
            }
        }
        return synchronized(this) {
            ActiveLightConfig(cachedAutoMode, cachedLightDefaultMode, cachedControlMode)
        }
    }

    /**
     * Workout start hook (REQ-CON-031.4, REQ-CON-034, REQ-CON-035.7): turns on tail light if auto-switch is enabled
     * and activates SmartBrakeLightDetector and SmartRadarLightEngine ONLY for cycling workouts when light control is available.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStarted(context: Context? = null, isCycling: Boolean = true) {
        synchronized(this) {
            isCyclingSession = isCycling
            if (!isCycling) {
                Log.d(TAG, "Non-cycling workout started; skipping radar light and brake detection")
                return
            }
            isWorkoutActive = true
            if (context != null) {
                appContext = context.applicationContext
            }
        }
        val config = resolveActiveConfig(context)
        if (config.controlMode == RadarLightControlMode.MANUAL || (!config.isAutoMode && config.defaultMode == RadarLightMode.OFF)) {
            Log.d(TAG, "Radar light control is MANUAL/OFF; skipping automatic mode dispatch")
        } else if (config.isAutoMode) {
            _isAutoMode.value = true
            RadarStateRepository.updateControlState { it.copy(isAutoMode = true) }
        } else {
            _isAutoMode.value = false
            setLightMode(config.defaultMode)
            SmartRadarLightEngine.onManualOverride(config.defaultMode)
            RadarStateRepository.updateControlState { it.copy(isAutoMode = false, lightMode = config.defaultMode) }
        }
        val ctx = context?.applicationContext ?: synchronized(this) { appContext }
        val isLightAvailable = synchronized(this) { _isLightControlAvailable.value || RadarTrafficSimulator.isSimulating }
        if (ctx != null && isLightAvailable && SmartBrakeLightDetector.isEnabled && !SmartBrakeLightDetector.isAutoBypassed) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        if (ctx != null && isCycling && config.isAutoMode) {
            SmartRadarLightEngine.start(ctx)
        }
    }

    /**
     * Workout pause hook: stops IMU and ambient light listening to conserve battery, clears pending brake holds,
     * and reverts from active brake light to pre-brake mode (REQ-CON-035.7).
     * Mode restoration executes outside synchronized(this) to prevent deadlock and re-entrancy.
     */
    @JvmStatic
    fun onWorkoutPaused() {
        val modeToRestore = synchronized(this) {
            isWorkoutActive = false
            val mode = if (isBrakeActive) preBrakeMode else null
            isBrakeActive = false
            brakeHoldUntilRealtimeMs = 0L
            try {
                handler.removeCallbacks(brakeHoldRunnable)
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
            mode
        }
        modeToRestore?.let { setLightMode(it) }
        SmartBrakeLightDetector.stopListening()
        SmartRadarLightEngine.stopListening()
    }

    /**
     * Workout resume hook: resumes IMU and ambient light listening and preserves current active light mode.
     * Does NOT stomp active mode (e.g. PELOTON 8 lm or night modes) back to defaultMode unless currently OFF.
     * Checks [isCyclingSession] so non-cycling workouts never activate IMU listeners or tail lights upon unpause (REQ-CON-035.7).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutResumed(context: Context? = null, isCycling: Boolean? = null) {
        val cycling = isCycling ?: synchronized(this) { isCyclingSession }
        if (!cycling) {
            Log.d(TAG, "Non-cycling workout resumed; skipping radar light and brake detection")
            return
        }
        synchronized(this) {
            isWorkoutActive = true
            isCyclingSession = true
            if (context != null) {
                appContext = context.applicationContext
            }
        }
        val config = resolveActiveConfig(context)
        if (!config.isAutoMode) {
            if (_currentMode.value == RadarLightMode.OFF && config.defaultMode != RadarLightMode.OFF) {
                setLightMode(config.defaultMode)
            }
        }
        val ctx = context?.applicationContext ?: synchronized(this) { appContext }
        val isLightAvailable = synchronized(this) { _isLightControlAvailable.value || RadarTrafficSimulator.isSimulating }
        if (ctx != null && isLightAvailable && SmartBrakeLightDetector.isEnabled && !SmartBrakeLightDetector.isAutoBypassed) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        if (ctx != null && cycling && config.isAutoMode) {
            SmartRadarLightEngine.start(ctx)
        }
    }

    /**
     * Workout finish/stop hook (REQ-CON-031.4, REQ-CON-034): cancels pending holds, stops sensor listening,
     * and reverts tail light to OFF if auto-switch is enabled.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStopped(context: Context? = null) {
        synchronized(this) {
            isWorkoutActive = false
            isCyclingSession = false
            isBrakeActive = false
            brakeHoldUntilRealtimeMs = 0L
            preBrakeMode = RadarLightMode.OFF
            try {
                handler.removeCallbacks(brakeHoldRunnable)
                handler.removeCallbacks(debouncedDispatchRunnable)
                pendingTargetMode = null
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
        SmartBrakeLightDetector.resetMotionState()
        SmartRadarLightEngine.stop()
        val config = resolveActiveConfig(context)
        if (config.controlMode != RadarLightControlMode.MANUAL) {
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
            isCyclingSession = false
            appContext = null
            _isLightControlAvailable.value = false
            _isStvzo.value = false
            _hasHardwareBrakeSensor.value = false
            _isAutoBypassed.value = false
            _isAutoMode.value = true
            _preferredMode.value = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
            _currentMode.value = RadarLightMode.OFF
            isBrakeActive = false
            preBrakeMode = RadarLightMode.OFF
            brakeHoldUntilRealtimeMs = 0L
            pendingTargetMode = null
            cachedAutoMode = TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE
            cachedAutoLightControl = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
            cachedLightDefaultMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
            hasLoadedInitialPreferences = false
            hasUserModifiedSelection = false
            try {
                handler.removeCallbacks(brakeHoldRunnable)
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
        SmartBrakeLightDetector.reset()
        SmartRadarLightEngine.reset()
        RadarStateRepository.updateControlState {
            it.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF, isAutoMode = true)
        }
    }
}
