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
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Central controller coordinating Garmin Varia BLE and ANT+ radar tail light modes (REQ-CON-031, REQ-CON-035, REQ-CON-056).
 *
 * Architectural Invariants (`REQ-CON-056.2`, `REQ-CON-056.4`):
 * - Zero stale local preference caches: reads live [TuningConfig] directly from [TuningPreferencesDataStore].
 * - Single long-lived [persistenceScope] for async selection persistence (no per-tap scope allocations).
 * - Deadlock-free lock hierarchy: [controllerLock] is ALWAYS released prior to invoking [SmartBrakeLightDetector],
 *   [RadarLightDeviceClient], [SmartRadarLightEngine], or [RadarStateRepository] ($E(L_A \to L_B) = \emptyset$).
 */
data class RadarLightSelection(
    val isAuto: Boolean,
    val mode: RadarLightMode
)

object RadarLightController {

    private const val TAG = "RadarLightController"

    const val POST_BRAKE_HOLD_DURATION_MS = 2000L
    const val DEBOUNCE_DELAY_MS = 400L

    private val controllerLock = Any()

    @Volatile
    private var persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val selectionGeneration = AtomicLong(0L)

    private var activeRadarDevice: RadarLightDeviceClient? = null

    private val _currentMode = MutableStateFlow(RadarLightMode.OFF)
    @get:JvmStatic
    val currentMode: StateFlow<RadarLightMode> = _currentMode.asStateFlow()

    private val _controlMode = MutableStateFlow(TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE)

    private val _isAutoMode = MutableStateFlow(TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE)
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

    @Volatile
    var isBrakeActive: Boolean = false
        private set

    private var isWorkoutActive: Boolean = false
    private var isCyclingSession: Boolean = false
    private var appContext: Context? = null

    private var preBrakeMode: RadarLightMode = RadarLightMode.OFF
    private var brakeHoldUntilRealtimeMs: Long = 0L
    private var brakeEpoch: Long = 0L

    private var pendingTargetMode: RadarLightMode? = null

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val brakeHoldRunnable = Runnable {
        val active = synchronized(controllerLock) { isBrakeActive }
        if (!active) return@Runnable
        checkAndRestorePreBrakeMode(safeElapsedRealtime())
    }

    private val debouncedDispatchRunnable = Runnable {
        flushDebouncedDispatch()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun safeElapsedRealtime(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.nanoTime() / 1_000_000L
    }

    /**
     * Binds a connected [RadarLightDeviceClient] peripheral (BLE or ANT+) to the controller.
     * Invokes [SmartBrakeLightDetector] and [RadarStateRepository] strictly outside [controllerLock].
     */
    @JvmStatic
    @JvmOverloads
    fun registerRadarDevice(
        device: RadarLightDeviceClient,
        lightSupported: Boolean,
        isStvzo: Boolean = false,
        hasHardwareBrakeSensor: Boolean = false
    ) {
        val workoutCycling: Boolean
        val ctx: Context?
        synchronized(controllerLock) {
            activeRadarDevice = device
            _isLightControlAvailable.value = lightSupported
            _isStvzo.value = isStvzo
            _hasHardwareBrakeSensor.value = hasHardwareBrakeSensor
            _isAutoBypassed.value = hasHardwareBrakeSensor
            workoutCycling = isWorkoutActive && isCyclingSession
            ctx = appContext
        }
        SmartBrakeLightDetector.isAutoBypassed = hasHardwareBrakeSensor
        if (hasHardwareBrakeSensor || !lightSupported) {
            SmartBrakeLightDetector.stopListening()
        } else if (workoutCycling && SmartBrakeLightDetector.isEnabled && ctx != null) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        RadarStateRepository.updateLightState {
            it.copy(isLightControlSupported = lightSupported, isAutoMode = _isAutoMode.value)
        }
        Log.i(TAG, "Registered radar device (lightSupported=$lightSupported, isStvzo=$isStvzo, hasHardwareBrake=$hasHardwareBrakeSensor)")
    }

    /**
     * Unbinds a disconnected [RadarLightDeviceClient] peripheral.
     * Invokes [SmartBrakeLightDetector] and [RadarStateRepository] strictly outside [controllerLock].
     */
    @JvmStatic
    fun unregisterRadarDevice(device: RadarLightDeviceClient) {
        val wasActive: Boolean
        synchronized(controllerLock) {
            wasActive = (activeRadarDevice === device)
            if (wasActive) {
                activeRadarDevice = null
                _isLightControlAvailable.value = false
                _isStvzo.value = false
                _hasHardwareBrakeSensor.value = false
                _isAutoBypassed.value = false
                _currentMode.value = RadarLightMode.OFF
                isBrakeActive = false
                brakeEpoch++
                pendingTargetMode = null
                try {
                    handler.removeCallbacks(brakeHoldRunnable)
                    handler.removeCallbacks(debouncedDispatchRunnable)
                } catch (_: Exception) {}
            }
        }
        if (wasActive) {
            SmartBrakeLightDetector.isAutoBypassed = false
        }
        SmartBrakeLightDetector.stopListening()
        RadarStateRepository.updateLightState {
            it.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF, isAutoMode = _isAutoMode.value)
        }
        Log.i(TAG, "Unregistered radar device")
    }

    /**
     * Live DataStore propagation hook invoked by [TuningPreferencesDataStore] on every committed config change
     * (REQ-CON-056.2). Eliminates stale local preference caches.
     */
    @JvmStatic
    fun onPreferencesUpdated(config: TuningConfig) {
        val controlMode = config.radarLightControlMode
        val isAuto = config.radarLightAutoMode
        val preferred = if (controlMode == RadarLightControlMode.MANUAL) {
            RadarLightMode.OFF
        } else {
            config.radarLightDefaultMode
        }
        _controlMode.value = controlMode
        _isAutoMode.value = isAuto
        _preferredMode.value = preferred
        SmartRadarLightEngine.isAutomationEnabled = isAuto
        RadarStateRepository.updateLightState { it.copy(isAutoMode = isAuto) }
    }

    /**
     * Hydrates persisted [isAutoMode] and [preferredMode] from [TuningPreferencesDataStore] on cold start
     * prior to workout start (`REQ-CON-048.2`, `ATT-8450`). Safe to call from plain-JVM tests.
     */
    @JvmStatic
    @JvmOverloads
    fun hydrateFromPreferences(context: Context? = null): Boolean {
        val ctx = context?.applicationContext ?: context ?: synchronized(controllerLock) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull()
        if (ctx != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(ctx).getTuningConfig()
                SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                onPreferencesUpdated(cfg)
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
        val startGen = selectionGeneration.get()
        hydrateFromPreferences(context)
        val appCtx = runCatching { context.applicationContext ?: context }.getOrNull() ?: return
        val filesDir = runCatching { appCtx.filesDir }.getOrNull() ?: return
        if (filesDir.javaClass != java.io.File::class.java) return
        try {
            val cfg = TuningPreferencesDataStore.getInstance(appCtx).tuningConfigFlow.first()
            if (selectionGeneration.get() == startGen) {
                SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                onPreferencesUpdated(cfg)
            }
        } catch (_: Throwable) {
            // Ignore in isolated/unattached contexts
        }
    }

    /**
     * Updates active mode confirmed by GATT telemetry/read (REQ-CON-031.1).
     */
    @JvmStatic
    fun updateHardwareLightMode(mode: RadarLightMode) {
        synchronized(controllerLock) {
            _currentMode.value = mode
        }
        RadarStateRepository.updateLightState { it.copy(lightMode = mode, isAutoMode = _isAutoMode.value) }
    }

    /**
     * Reconciles optimistic UI state when a peripheral GATT characteristic write fails (REQ-CON-031.2, ATT-7737).
     */
    @JvmStatic
    fun onHardwareLightModeWriteFailed(failedMode: RadarLightMode, confirmedFallbackMode: RadarLightMode?) {
        val fallback = confirmedFallbackMode ?: RadarLightMode.OFF
        val shouldRevert = synchronized(controllerLock) {
            if (_currentMode.value == failedMode) {
                _currentMode.value = fallback
                true
            } else {
                false
            }
        }
        if (shouldRevert) {
            RadarStateRepository.updateLightState { it.copy(lightMode = fallback, isAutoMode = _isAutoMode.value) }
        }
        Log.w(TAG, "Hardware light mode write rejected for $failedMode; reconciled state to $fallback (reverted=$shouldRevert)")
    }

    /**
     * Dispatches a light mode command to the active radar peripheral or simulator.
     * Automatically prunes flashing modes under StVZO regulations to [RadarLightMode.SOLID].
     * Mutates [_currentMode] and [RadarStateRepository.lightState] strictly when command dispatch succeeds (REQ-CON-031.2).
     */
    @JvmStatic
    fun setLightMode(mode: RadarLightMode): Boolean {
        val targetMode = if (_isStvzo.value && !mode.isStvzoCompliant) {
            Log.w(TAG, "Mode $mode is non-StVZO compliant; falling back to SOLID")
            RadarLightMode.SOLID
        } else {
            mode
        }

        val device = synchronized(controllerLock) { activeRadarDevice }
        var dispatched = false
        if (device != null && _isLightControlAvailable.value) {
            dispatched = device.setLightMode(targetMode)
        }

        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.lightState.value.isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
            dispatched = true
        }

        if (dispatched) {
            _currentMode.value = targetMode
            RadarStateRepository.updateLightState { it.copy(lightMode = targetMode, isAutoMode = _isAutoMode.value) }
        }

        return dispatched
    }

    /**
     * User-initiated selection of Smart Auto mode (REQ-CON-031.3, REQ-CON-034).
     */
    @JvmStatic
    fun setAutoMode() {
        selectionGeneration.incrementAndGet()
        synchronized(controllerLock) {
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
            pendingTargetMode = null
            _controlMode.value = RadarLightControlMode.SMART_AUTOMATION
            _isAutoMode.value = true
        }
        SmartRadarLightEngine.isAutomationEnabled = true
        SmartRadarLightEngine.resumeAutomation()
        RadarStateRepository.updateLightState { it.copy(isAutoMode = true) }
        persistSelectionAsync(isAuto = true, mode = null)
        Log.i(TAG, "User selected Automatik mode")
    }

    /**
     * User-initiated mode selection from Cockpit sheet (REQ-CON-031.3, REQ-CON-034).
     */
    @JvmStatic
    fun setManualLightMode(mode: RadarLightMode): Boolean {
        selectionGeneration.incrementAndGet()
        val targetMode = if (_isStvzo.value && !mode.isStvzoCompliant) RadarLightMode.SOLID else mode
        synchronized(controllerLock) {
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
            pendingTargetMode = null
            _controlMode.value = RadarLightControlMode.FIXED_WORKOUT_MODE
            _isAutoMode.value = false
            _preferredMode.value = targetMode
        }
        SmartRadarLightEngine.isAutomationEnabled = false
        SmartRadarLightEngine.onManualOverride(targetMode)
        val result = setLightMode(targetMode)
        RadarStateRepository.updateLightState { it.copy(isAutoMode = false, lightMode = _currentMode.value) }
        persistSelectionAsync(isAuto = false, mode = targetMode)
        return result
    }

    /**
     * Cycles through available radar tail light modes in ascending intensity order with ~400 ms hardware command debouncing.
     */
    @JvmStatic
    fun cycleNextMode(): RadarLightSelection {
        val availableModes = RadarLightMode.getAvailableModes(_isStvzo.value)
        val currentIsAuto = _isAutoMode.value
        val currentExplicit = synchronized(controllerLock) { pendingTargetMode } ?: _currentMode.value

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
        selectionGeneration.incrementAndGet()
        synchronized(controllerLock) {
            _controlMode.value = RadarLightControlMode.FIXED_WORKOUT_MODE
            _isAutoMode.value = false
            _preferredMode.value = targetMode
            pendingTargetMode = targetMode
            _currentMode.value = targetMode
            try {
                handler.removeCallbacks(debouncedDispatchRunnable)
                handler.postDelayed(debouncedDispatchRunnable, DEBOUNCE_DELAY_MS)
            } catch (_: Exception) {}
        }
        SmartRadarLightEngine.isAutomationEnabled = false
        SmartRadarLightEngine.onManualOverride(targetMode)
        RadarStateRepository.updateLightState { it.copy(isAutoMode = false, lightMode = targetMode) }
        persistSelectionAsync(isAuto = false, mode = targetMode)
        return RadarLightSelection(isAuto = false, mode = targetMode)
    }

    private fun flushDebouncedDispatch() {
        val target = synchronized(controllerLock) {
            val t = pendingTargetMode
            pendingTargetMode = null
            t
        } ?: return
        val targetMode = if (_isStvzo.value && !target.isStvzoCompliant) {
            RadarLightMode.SOLID
        } else {
            target
        }
        val device = synchronized(controllerLock) { activeRadarDevice }
        if (device != null && _isLightControlAvailable.value) {
            device.setLightMode(targetMode)
        }
        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.lightState.value.isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
        }
    }

    @VisibleForTesting
    fun flushDebounce() {
        flushDebouncedDispatch()
    }

    private fun persistSelectionAsync(isAuto: Boolean, mode: RadarLightMode?) {
        val ctx = synchronized(controllerLock) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull() ?: return
        if (!TuningPreferencesDataStore.hasInitializedInstance()) {
            val filesDir = runCatching { ctx.filesDir }.getOrNull() ?: return
            if (filesDir.javaClass != java.io.File::class.java || filesDir.path.isEmpty()) return
        }
        persistenceScope.launch {
            try {
                TuningPreferencesDataStore.getInstance(ctx).updateRadarLightSelection(isAuto, mode)
            } catch (_: Throwable) {
                // Ignore isolated/mocked context failures
            }
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
        val controlMode: RadarLightControlMode,
        val defaultMode: RadarLightMode
    ) {
        val isAutoMode: Boolean get() = controlMode == RadarLightControlMode.SMART_AUTOMATION
    }

    private fun resolveActiveConfig(context: Context?): ActiveLightConfig {
        val ctx = context ?: synchronized(controllerLock) { appContext } ?: runCatching {
            com.atrainingtracker.trainingtracker.TrainingApplication.getAppContext()
        }.getOrNull()
        if (ctx != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(ctx).getTuningConfig()
                SmartBrakeLightDetector.isEnabled = cfg.radarBrakeLightEnabled
                val resolvedDefault = if (cfg.radarLightControlMode == RadarLightControlMode.MANUAL) {
                    RadarLightMode.OFF
                } else {
                    cfg.radarLightDefaultMode
                }
                _controlMode.value = cfg.radarLightControlMode
                _isAutoMode.value = cfg.radarLightAutoMode
                _preferredMode.value = resolvedDefault
                SmartRadarLightEngine.isAutomationEnabled = cfg.radarLightAutoMode
                return ActiveLightConfig(cfg.radarLightControlMode, resolvedDefault)
            } catch (_: Throwable) {
                // Fallback to headless state flows in isolated JVM tests
            }
        }
        val control = _controlMode.value
        val preferred = _preferredMode.value
        SmartRadarLightEngine.isAutomationEnabled = (control == RadarLightControlMode.SMART_AUTOMATION)
        return ActiveLightConfig(control, preferred)
    }

    /**
     * Workout start hook (REQ-CON-031.4, REQ-CON-034, REQ-CON-035.7).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStarted(context: Context? = null, isCycling: Boolean = true) {
        synchronized(controllerLock) {
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
            RadarStateRepository.updateLightState { it.copy(isAutoMode = true) }
        } else {
            _isAutoMode.value = false
            setLightMode(config.defaultMode)
            SmartRadarLightEngine.onManualOverride(config.defaultMode)
            RadarStateRepository.updateLightState { it.copy(isAutoMode = false, lightMode = config.defaultMode) }
        }
        val ctx = context?.applicationContext ?: synchronized(controllerLock) { appContext }
        val isLightAvailable = _isLightControlAvailable.value || RadarTrafficSimulator.isSimulating
        if (ctx != null && isLightAvailable && SmartBrakeLightDetector.isEnabled && !SmartBrakeLightDetector.isAutoBypassed) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        if (ctx != null && isCycling && config.isAutoMode) {
            SmartRadarLightEngine.start(ctx)
        }
    }

    /**
     * Workout pause hook: stops IMU and ambient light listening, clears pending brake holds,
     * and reverts from active brake light to pre-brake mode outside [controllerLock].
     */
    @JvmStatic
    fun onWorkoutPaused() {
        val modeToRestore = synchronized(controllerLock) {
            isWorkoutActive = false
            val mode = if (isBrakeActive) preBrakeMode else null
            isBrakeActive = false
            brakeEpoch++
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
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutResumed(context: Context? = null, isCycling: Boolean? = null) {
        val cycling = isCycling ?: synchronized(controllerLock) { isCyclingSession }
        if (!cycling) {
            Log.d(TAG, "Non-cycling workout resumed; skipping radar light and brake detection")
            return
        }
        synchronized(controllerLock) {
            isWorkoutActive = true
            isCyclingSession = true
            if (context != null) {
                appContext = context.applicationContext
            }
        }
        val config = resolveActiveConfig(context)
        if (!config.isAutoMode && config.controlMode != RadarLightControlMode.MANUAL) {
            if (_currentMode.value == RadarLightMode.OFF && config.defaultMode != RadarLightMode.OFF) {
                setLightMode(config.defaultMode)
            }
        }
        val ctx = context?.applicationContext ?: synchronized(controllerLock) { appContext }
        val isLightAvailable = _isLightControlAvailable.value || RadarTrafficSimulator.isSimulating
        if (ctx != null && isLightAvailable && SmartBrakeLightDetector.isEnabled && !SmartBrakeLightDetector.isAutoBypassed) {
            SmartBrakeLightDetector.startListening(ctx)
        }
        if (ctx != null && cycling && config.isAutoMode) {
            SmartRadarLightEngine.start(ctx)
        }
    }

    /**
     * Workout finish/stop hook: cancels pending holds, stops sensor listening,
     * and reverts tail light to OFF unless control mode is [RadarLightControlMode.MANUAL].
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStopped(context: Context? = null) {
        synchronized(controllerLock) {
            isWorkoutActive = false
            isCyclingSession = false
            isBrakeActive = false
            brakeEpoch++
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
            val device = synchronized(controllerLock) { activeRadarDevice }
            device?.disconnectLight()
        }
        synchronized(controllerLock) {
            appContext = null
        }
    }

    /**
     * Triggers priority deceleration brake light or schedules 2.0s post-brake hold (REQ-CON-035.3, REQ-CON-035.4, REQ-CON-056.4).
     *
     * Dispatches [setLightMode] strictly OUTSIDE [controllerLock] with a monotonic [brakeEpoch] guard
     * so concurrent [onWorkoutPaused]/[onWorkoutStopped] transitions cannot be overwritten.
     */
    @JvmStatic
    @JvmOverloads
    fun triggerBrakeLight(
        active: Boolean,
        brakeMode: RadarLightMode = RadarLightMode.DAY_FLASH,
        nowMs: Long = safeElapsedRealtime()
    ): Boolean {
        val targetBrakeMode: RadarLightMode
        val expectedEpoch: Long
        synchronized(controllerLock) {
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
                expectedEpoch = ++brakeEpoch

                val isDarkEnvironment = SmartRadarLightEngine.isTunnelActive ||
                    (SmartRadarLightEngine.currentSolarProfile == SolarElevationCalculator.SolarProfile.DUSK_NIGHT) ||
                    (preBrakeMode == RadarLightMode.NIGHT_FLASH)

                targetBrakeMode = if (_isStvzo.value || isDarkEnvironment) {
                    RadarLightMode.SOLID
                } else {
                    brakeMode
                }
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

        // Dispatch strictly outside controllerLock, verifying no concurrent pause/stop/unregister invalidated this brake epoch
        val stillValid = synchronized(controllerLock) { isBrakeActive && brakeEpoch == expectedEpoch }
        if (!stillValid) return false
        return setLightMode(targetBrakeMode)
    }

    /**
     * Evaluates whether post-brake hold window has expired and restores pre-brake operating mode (REQ-CON-035.3, REQ-CON-056.4).
     * Dispatches mode restoration and [SmartRadarLightEngine.evaluateAndDispatch] strictly outside [controllerLock].
     */
    @JvmStatic
    @JvmOverloads
    fun checkAndRestorePreBrakeMode(nowMs: Long = safeElapsedRealtime()): Boolean {
        val modeToRestore: RadarLightMode = synchronized(controllerLock) {
            if (!isBrakeActive) return false
            if (nowMs < brakeHoldUntilRealtimeMs) return false
            isBrakeActive = false
            brakeEpoch++
            preBrakeMode
        }
        val restored = setLightMode(modeToRestore)
        Log.i(TAG, "Post-brake 2.0s hold expired; restored pre-brake mode: $modeToRestore")
        if (SmartRadarLightEngine.isAutomationEnabled) {
            SmartRadarLightEngine.evaluateAndDispatch(nowMs)
        }
        return restored
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
        synchronized(controllerLock) {
            activeRadarDevice = null
            isWorkoutActive = false
            isCyclingSession = false
            appContext = null
            _isLightControlAvailable.value = false
            _isStvzo.value = false
            _hasHardwareBrakeSensor.value = false
            _isAutoBypassed.value = false
            _controlMode.value = TuningPreferencesDefaults.RADAR_LIGHT_CONTROL_MODE
            _isAutoMode.value = TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE
            _preferredMode.value = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
            _currentMode.value = RadarLightMode.OFF
            isBrakeActive = false
            preBrakeMode = RadarLightMode.OFF
            brakeHoldUntilRealtimeMs = 0L
            brakeEpoch++
            pendingTargetMode = null
            selectionGeneration.set(0L)
            try {
                handler.removeCallbacks(brakeHoldRunnable)
                handler.removeCallbacks(debouncedDispatchRunnable)
            } catch (_: Exception) {}
        }
        SmartBrakeLightDetector.stopListening()
        SmartBrakeLightDetector.reset()
        SmartRadarLightEngine.reset()
        RadarStateRepository.updateLightState {
            RadarLightState(
                lightMode = RadarLightMode.OFF,
                isLightControlSupported = false,
                isAutoMode = TuningPreferencesDefaults.RADAR_LIGHT_AUTO_MODE
            )
        }
    }
}
