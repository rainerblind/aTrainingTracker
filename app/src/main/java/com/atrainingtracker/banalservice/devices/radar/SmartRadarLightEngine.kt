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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Environmental & traffic-adaptive smart tail light mode automation engine (REQ-CON-034, ATT-5104).
 *
 * Coordinates:
 * 1. Sensor Gating & Pocket/Screen-off Protection: rejects 0 lx ambient readings when screen is OFF
 *    or proximity is NEAR, falling back to astronomical solar elevation ([SolarElevationCalculator]).
 * 2. Rapid Tunnel Detection: sustained illuminance < 50 lx for >= 1.5s triggers tunnel mode within 2s;
 *    10s recovery hysteresis (> 300 lx) before returning to daytime mode.
 * 3. 3-Stage Threat Escalation Matrix with strict anti-glare cap at night (never Day Flash at night).
 * 4. Command Rate-Limiting: enforces >= 1.5s between mode command dispatches with 3.5s cooldown hysteresis.
 * 5. Lifecycle Management: registers [Sensor.TYPE_LIGHT] and [Sensor.TYPE_PROXIMITY] during active cycling
 *    workouts when automation is enabled, cleanly unregistering on pause/stop to protect battery life.
 */
object SmartRadarLightEngine : SensorEventListener {

    private const val TAG = "SmartRadarLightEngine"

    private fun safeElapsedRealtime(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    const val RATE_LIMIT_INTERVAL_MS = 1500L
    const val COOLDOWN_HYSTERESIS_MS = 3500L
    const val TUNNEL_ENTRY_DARK_THRESHOLD_LX = 50.0f
    const val TUNNEL_ENTRY_HYSTERESIS_MS = 1500L
    const val TUNNEL_EXIT_BRIGHT_THRESHOLD_LX = 300.0f
    const val TUNNEL_EXIT_HYSTERESIS_MS = 10000L

    private val engineLock = Any()
    private var listenEpoch: Long = 0L

    @Volatile
    private var isAutomationEnabledField: Boolean = false

    var isAutomationEnabled: Boolean
        get() = isAutomationEnabledField
        set(value) {
            setAutomationEnabledInternal(value, evaluate = true)
        }

    @Volatile
    var isTunnelActive: Boolean = false
        private set

    @Volatile
    var currentSolarProfile: SolarElevationCalculator.SolarProfile = SolarElevationCalculator.SolarProfile.DAY
        private set

    @Volatile
    var currentThreatStage: Int = 0
        private set

    @Volatile
    var isManualOverridden: Boolean = false
        private set

    @Volatile
    var lastDispatchedMode: RadarLightMode? = null
        private set

    @Volatile
    var coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default

    @Volatile
    var isProximityNear: Boolean = false
        private set

    @Volatile
    var isListening: Boolean = false
        private set

    @Volatile
    var isWorkoutActive: Boolean = false
        private set

    @Volatile
    var isWorkoutPaused: Boolean = false
        private set

    private var sensorManager: SensorManager? = null
    private var powerManager: PowerManager? = null
    private var observationScope: CoroutineScope? = null
    private var appContext: Context? = null

    private var lastDispatchRealtimeMs: Long = 0L
    private var threatCooldownUntilRealtimeMs: Long = 0L
    private var tunnelDarkSinceRealtimeMs: Long = 0L
    private var tunnelBrightSinceRealtimeMs: Long = 0L
    private var pendingModeToDispatch: RadarLightMode? = null

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val rateLimitFlushRunnable = Runnable {
        flushPendingDispatch()
    }

    private fun cancelPendingRateLimit() {
        synchronized(engineLock) {
            pendingModeToDispatch = null
        }
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {
            // Headless test fallback
        }
    }

    private fun setAutomationEnabledInternal(enabled: Boolean, evaluate: Boolean) {
        val ctxToStart: Context?
        synchronized(engineLock) {
            isAutomationEnabledField = enabled
            ctxToStart = if (enabled && isWorkoutActive && !isWorkoutPaused) appContext else null
        }
        if (enabled) {
            if (ctxToStart != null) {
                startListening(ctxToStart)
            }
            if (evaluate) {
                evaluateAndDispatchInternal(safeElapsedRealtime(), forceImmediate = false)
            }
        } else {
            cancelPendingRateLimit()
            stopListening()
        }
    }

    /**
     * Clears manual override prior to enabling automation from an external preference update (`REQ-CON-059.3`).
     */
    internal fun clearManualOverrideForExternalAuto() {
        isManualOverridden = false
    }

    /**
     * Ambient light sensor callback with hardware pocket/screen-off gating (REQ-CON-034.1, REQ-CON-034.3).
     *
     * @param lux Ambient illuminance reading.
     * @param isScreenInteractive true if screen is active/on.
     * @param isProximityNear true if phone is occluded in a pocket/bag.
     * @param nowMs Current elapsed realtime in ms (overridable for unit testing).
     */
    @JvmStatic
    @JvmOverloads
    fun onAmbientLightChanged(
        lux: Float,
        isScreenInteractive: Boolean,
        isProximityNear: Boolean,
        nowMs: Long = safeElapsedRealtime()
    ) {
        if (!isScreenInteractive || isProximityNear) {
            // Pocket / Screen-Off Gating: 0 lx reading is ignored; relying on solar profile (REQ-CON-034.1)
            tunnelDarkSinceRealtimeMs = 0L
            return
        }

        // Screen is active and unoccluded: valid environmental illuminance
        if (lux < TUNNEL_ENTRY_DARK_THRESHOLD_LX) {
            if (tunnelDarkSinceRealtimeMs == 0L) {
                tunnelDarkSinceRealtimeMs = nowMs
            }
            if (nowMs - tunnelDarkSinceRealtimeMs >= TUNNEL_ENTRY_HYSTERESIS_MS && !isTunnelActive) {
                isTunnelActive = true
                tunnelBrightSinceRealtimeMs = 0L
                Log.i(TAG, "Fast tunnel entry detected (< 50 lx sustained); activating tunnel profile")
                evaluateAndDispatch(nowMs)
            }
        } else if (lux > TUNNEL_EXIT_BRIGHT_THRESHOLD_LX) {
            tunnelDarkSinceRealtimeMs = 0L
            if (isTunnelActive) {
                if (tunnelBrightSinceRealtimeMs == 0L) {
                    tunnelBrightSinceRealtimeMs = nowMs
                }
                if (nowMs - tunnelBrightSinceRealtimeMs >= TUNNEL_EXIT_HYSTERESIS_MS) {
                    isTunnelActive = false
                    tunnelBrightSinceRealtimeMs = 0L
                    Log.i(TAG, "Tunnel exit detected (> 300 lx sustained); returning to daytime profile")
                    evaluateAndDispatch(nowMs)
                }
            }
        } else {
            // Neutral deadband (50..300 lx): reset hysteresis timers
            tunnelDarkSinceRealtimeMs = 0L
            tunnelBrightSinceRealtimeMs = 0L
        }
    }

    /**
     * Location and timestamp update for astronomical solar elevation calculation (REQ-CON-034.2).
     */
    @JvmStatic
    @JvmOverloads
    fun onLocationAndTimeChanged(
        latitudeDeg: Double,
        longitudeDeg: Double,
        epochTimeMs: Long,
        nowMs: Long = safeElapsedRealtime()
    ) {
        val newProfile = SolarElevationCalculator.getSolarProfile(latitudeDeg, longitudeDeg, epochTimeMs)
        if (newProfile != currentSolarProfile) {
            currentSolarProfile = newProfile
            Log.i(TAG, "Solar profile updated to: $newProfile")
            evaluateAndDispatch(nowMs)
        }
    }

    private fun resolveLiveThreatStage(
        state: RadarThreatState,
        nowMs: Long,
        mutate: Boolean
    ): Int {
        val rawStage = when {
            state.overallThreatLevel == ThreatLevel.FAST_APPROACHING ||
                state.targets.any { it.threatLevel == ThreatLevel.FAST_APPROACHING } -> 2

            state.overallThreatLevel == ThreatLevel.APPROACHING ||
                state.targets.any { it.threatLevel == ThreatLevel.APPROACHING } -> 1

            else -> 0
        }

        return synchronized(engineLock) {
            val resolved = if (rawStage > 0) {
                if (mutate) {
                    threatCooldownUntilRealtimeMs = nowMs + COOLDOWN_HYSTERESIS_MS
                }
                rawStage
            } else {
                // Hold previous elevated state until cooldown expires
                if (nowMs < threatCooldownUntilRealtimeMs) 1 else 0
            }
            if (mutate) {
                currentThreatStage = resolved
            }
            resolved
        }
    }

    /**
     * Synchronizes [currentThreatStage] from live [RadarStateRepository.threatState] (`REQ-CON-059.1`).
     */
    internal fun syncThreatStageFromRepository(nowMs: Long = safeElapsedRealtime()) {
        resolveLiveThreatStage(RadarStateRepository.threatState.value, nowMs, mutate = true)
    }

    /**
     * Computes a side-effect-free preview of the automatic target [RadarLightMode] using live threat state (`REQ-CON-059.2`).
     */
    internal fun previewTargetMode(nowMs: Long = safeElapsedRealtime()): RadarLightMode {
        val liveStage = resolveLiveThreatStage(RadarStateRepository.threatState.value, nowMs, mutate = false)
        return computeTargetMode(isTunnelActive, currentSolarProfile, liveStage)
    }

    /**
     * Orthogonal threat-state callback for 3-stage escalation (REQ-CON-034.4, REQ-CON-034.5, REQ-CON-056.3).
     * Never triggered by audio snooze countdowns or tail-light state changes.
     */
    @JvmStatic
    @JvmOverloads
    fun onThreatStateChanged(
        state: RadarThreatState,
        nowMs: Long = safeElapsedRealtime()
    ) {
        resolveLiveThreatStage(state, nowMs, mutate = true)
        evaluateAndDispatch(nowMs)
    }

    /**
     * Radar target and threat state callback for 3-stage escalation (REQ-CON-034.4, REQ-CON-034.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onRadarStateChanged(
        state: RadarState,
        nowMs: Long = safeElapsedRealtime()
    ) {
        onThreatStateChanged(
            RadarThreatState(
                connectionState = state.connectionState,
                overallThreatLevel = state.overallThreatLevel,
                targets = state.targets,
                lastUpdateRealtimeMs = state.lastUpdateRealtimeMs,
                source = state.source
            ),
            nowMs
        )
    }

    /**
     * Computes the target [RadarLightMode] from the environmental and threat matrix.
     */
    @JvmStatic
    fun computeTargetMode(
        isTunnel: Boolean,
        solarProfile: SolarElevationCalculator.SolarProfile,
        threatStage: Int
    ): RadarLightMode {
        val isDarkEnvironment = isTunnel || (solarProfile == SolarElevationCalculator.SolarProfile.DUSK_NIGHT)

        return if (isDarkEnvironment) {
            // Night / Dusk / Tunnel Profile: STRICT ANTI-GLARE CAP (Never DAY_FLASH at night!)
            when (threatStage) {
                2 -> RadarLightMode.NIGHT_FLASH // 40 lm
                1 -> RadarLightMode.SOLID       // 25 lm
                else -> RadarLightMode.PELOTON  // 8 lm non-dazzling low steady
            }
        } else {
            // Daytime Profile
            when (threatStage) {
                2 -> RadarLightMode.DAY_FLASH   // 100 lm high-visibility flash
                1 -> RadarLightMode.SOLID       // 25 lm steady
                else -> RadarLightMode.OFF      // Baseline off / radar-only
            }
        }
    }

    /**
     * Evaluates current state and dispatches mode command subject to rate-limiting.
     */
    fun evaluateAndDispatch(nowMs: Long = safeElapsedRealtime()) {
        evaluateAndDispatchInternal(nowMs, forceImmediate = false)
    }

    /**
     * Core automatic light evaluation and dispatch routine (`REQ-CON-034`, `REQ-CON-059.1`/`.2`).
     *
     * When [forceImmediate] is `true` (explicit user switch to Automatik or post-brake hold expiry),
     * bypasses the 1.5s autonomous rate-limit window and ignores stale [lastDispatchedMode] drift,
     * while strictly respecting active brake preemption.
     */
    internal fun evaluateAndDispatchInternal(
        nowMs: Long = safeElapsedRealtime(),
        forceImmediate: Boolean = false
    ): Boolean {
        if (!isAutomationEnabledField || isManualOverridden || RadarLightController.isBrakeActive) return false

        val targetMode = computeTargetMode(isTunnelActive, currentSolarProfile, currentThreatStage)
        val effectiveTarget = RadarLightController.resolveStvzoMode(targetMode)
        if (!forceImmediate && targetMode == lastDispatchedMode && effectiveTarget == RadarLightController.currentMode.value) {
            return false
        }

        val shouldDispatchNow: Boolean
        val delayMs: Long
        synchronized(engineLock) {
            val elapsedSinceLastDispatch = nowMs - lastDispatchRealtimeMs
            if (forceImmediate || elapsedSinceLastDispatch >= RATE_LIMIT_INTERVAL_MS) {
                pendingModeToDispatch = null
                shouldDispatchNow = true
                delayMs = 0L
            } else {
                // Queue pending dispatch at the expiration of the rate-limit window
                pendingModeToDispatch = targetMode
                shouldDispatchNow = false
                delayMs = RATE_LIMIT_INTERVAL_MS - elapsedSinceLastDispatch
            }
        }

        return if (shouldDispatchNow) {
            try {
                handler.removeCallbacks(rateLimitFlushRunnable)
            } catch (_: Exception) {}
            dispatchMode(targetMode, nowMs)
        } else {
            try {
                handler.removeCallbacks(rateLimitFlushRunnable)
                handler.postDelayed(rateLimitFlushRunnable, delayMs)
            } catch (_: Exception) {
                // Headless test fallback
            }
            false
        }
    }

    private fun dispatchMode(mode: RadarLightMode, nowMs: Long): Boolean {
        val committed = RadarLightController.setAutomationLightMode(mode)
        synchronized(engineLock) {
            if (committed) {
                lastDispatchedMode = mode
                lastDispatchRealtimeMs = nowMs
                pendingModeToDispatch = null
            } else {
                lastDispatchedMode = null
            }
        }
        return committed
    }

    @VisibleForTesting
    internal fun flushPendingDispatch(nowMs: Long = safeElapsedRealtime()): Boolean {
        val hasPending = synchronized(engineLock) {
            val had = pendingModeToDispatch != null
            pendingModeToDispatch = null
            had
        }
        if (!hasPending) return false
        if (!isAutomationEnabledField || isManualOverridden || RadarLightController.isBrakeActive) return false
        return evaluateAndDispatchInternal(nowMs, forceImmediate = false)
    }

    @VisibleForTesting
    internal fun hasPendingDispatch(): Boolean = synchronized(engineLock) {
        pendingModeToDispatch != null
    }

    /**
     * Registers a manual user override from the Cockpit sheet, temporarily suspending automation.
     */
    @JvmStatic
    fun onManualOverride(mode: RadarLightMode) {
        synchronized(engineLock) {
            isManualOverridden = true
            lastDispatchedMode = mode
            pendingModeToDispatch = null
        }
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {}
        Log.i(TAG, "Manual override active: $mode (smart light automation suspended)")
    }

    /**
     * Resumes smart automation following a manual override (`REQ-CON-059.1`).
     * Syncs live threat state, enables automation without duplicate evaluation, and dispatches immediately.
     */
    @JvmStatic
    fun resumeAutomation() {
        val nowMs = safeElapsedRealtime()
        cancelPendingRateLimit()
        syncThreatStageFromRepository(nowMs)
        isManualOverridden = false
        setAutomationEnabledInternal(enabled = true, evaluate = false)
        Log.i(TAG, "Smart light automation resumed")
        evaluateAndDispatchInternal(nowMs, forceImmediate = true)
    }

    /**
     * Starts smart tail light automation and observers for active workout session (REQ-CON-034, REQ-CON-059.1/4).
     * Uses pure leaf [engineLock] (never held across coroutine launches or [SensorManager] calls).
     */
    @JvmStatic
    fun start(context: Context) {
        val appCtx = context.applicationContext ?: context
        val newScope: CoroutineScope?
        val shouldListen: Boolean
        synchronized(engineLock) {
            appContext = appCtx
            isWorkoutActive = true
            isWorkoutPaused = false
            newScope = if (observationScope == null) {
                CoroutineScope(SupervisorJob() + coroutineDispatcher).also { observationScope = it }
            } else {
                null
            }
            shouldListen = isAutomationEnabledField
        }

        if (newScope != null) {
            newScope.launch {
                RadarStateRepository.threatState.collectLatest { state ->
                    onThreatStateChanged(state)
                }
            }

            newScope.launch {
                try {
                    if (TuningPreferencesDataStore.hasInitializedInstance()) {
                        TuningPreferencesDataStore.getInstance(appCtx).radarSmartLightAutomationFlow.collectLatest { enabled ->
                            if (!RadarLightController.shouldIgnoreStaleDataStoreAutoMode(enabled)) {
                                if (enabled) {
                                    clearManualOverrideForExternalAuto()
                                }
                                isAutomationEnabled = enabled
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    runCatching { Log.w(TAG, "Error collecting radarSmartLightAutomationFlow", e) }
                }
            }
        }

        if (shouldListen) {
            startListening(appCtx)
        }
    }

    /**
     * Registers ambient light (Sensor.TYPE_LIGHT) and proximity (Sensor.TYPE_PROXIMITY) listeners.
     * Executes all [SensorManager] operations strictly outside [engineLock] with [listenEpoch] race protection.
     */
    @JvmStatic
    fun startListening(
        context: Context,
        customSensorManager: SensorManager? = null,
        customPowerManager: PowerManager? = null
    ) {
        val expectedEpoch = synchronized(engineLock) {
            if (isListening) return
            ++listenEpoch
        }
        val sm = customSensorManager ?: (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager) ?: return
        val pm = customPowerManager ?: (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)

        val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximitySensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (lightSensor != null) {
            sm.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (proximitySensor != null) {
            sm.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val committed = synchronized(engineLock) {
            if (listenEpoch == expectedEpoch && !isListening) {
                sensorManager = sm
                powerManager = pm
                isListening = true
                true
            } else {
                false
            }
        }
        if (!committed) {
            try {
                sm.unregisterListener(this)
            } catch (_: Exception) {}
            return
        }
        Log.i(TAG, "SmartRadarLightEngine registered sensor listeners (light=${lightSensor != null}, prox=${proximitySensor != null})")
    }

    /**
     * Unregisters sensor listeners to prevent battery drain when paused or stopped.
     * Executes [SensorManager.unregisterListener] strictly outside [engineLock].
     */
    @JvmStatic
    fun stopListening() {
        val smToUnregister = synchronized(engineLock) {
            listenEpoch++
            val sm = sensorManager
            val wasListening = isListening
            sensorManager = null
            powerManager = null
            isListening = false
            if (wasListening) sm else null
        } ?: return
        try {
            smToUnregister.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering light/proximity sensor listeners", e)
        }
        Log.i(TAG, "SmartRadarLightEngine unregistered sensor listeners")
    }

    /**
     * Marks workout as paused, cancelling queued rate-limit dispatches and unregistering ambient/proximity
     * sensors so calling [resumeAutomation] while paused does not re-register hardware listeners (`REQ-CON-059.1`).
     */
    @JvmStatic
    fun onWorkoutPaused() {
        synchronized(engineLock) {
            isWorkoutPaused = true
            pendingModeToDispatch = null
        }
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {}
        stopListening()
    }

    /**
     * Stops automation, unregisters sensors, clears cached dispatch state, and terminates observers (`REQ-CON-059.2`).
     * Never dispatches mode commands during teardown.
     */
    @JvmStatic
    fun stop() {
        val scopeToCancel: CoroutineScope?
        synchronized(engineLock) {
            isWorkoutActive = false
            isWorkoutPaused = false
            isAutomationEnabledField = false
            lastDispatchedMode = null
            pendingModeToDispatch = null
            scopeToCancel = observationScope
            observationScope = null
            appContext = null
        }
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {}
        stopListening()
        scopeToCancel?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values.getOrNull(0) ?: return
                isProximityNear = distance < event.sensor.maximumRange
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values.getOrNull(0) ?: return
                val isInteractive = powerManager?.isInteractive ?: true
                onAmbientLightChanged(lux, isInteractive, isProximityNear)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Resets engine state for unit testing isolation.
     */
    @JvmStatic
    fun reset() {
        stop()
        synchronized(engineLock) {
            coroutineDispatcher = Dispatchers.Default
            isAutomationEnabledField = false
            isTunnelActive = false
            currentSolarProfile = SolarElevationCalculator.SolarProfile.DAY
            currentThreatStage = 0
            isManualOverridden = false
            isProximityNear = false
            isWorkoutPaused = false
            lastDispatchedMode = null
            lastDispatchRealtimeMs = 0L
            threatCooldownUntilRealtimeMs = 0L
            tunnelDarkSinceRealtimeMs = 0L
            tunnelBrightSinceRealtimeMs = 0L
            pendingModeToDispatch = null
        }
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {}
    }
}
