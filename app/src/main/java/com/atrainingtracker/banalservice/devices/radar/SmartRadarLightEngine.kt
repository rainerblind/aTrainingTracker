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

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

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
 */
object SmartRadarLightEngine {

    private const val TAG = "SmartRadarLightEngine"

    const val RATE_LIMIT_INTERVAL_MS = 1500L
    const val COOLDOWN_HYSTERESIS_MS = 3500L
    const val TUNNEL_ENTRY_DARK_THRESHOLD_LX = 50.0f
    const val TUNNEL_ENTRY_HYSTERESIS_MS = 1500L
    const val TUNNEL_EXIT_BRIGHT_THRESHOLD_LX = 300.0f
    const val TUNNEL_EXIT_HYSTERESIS_MS = 10000L

    var isAutomationEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                evaluateAndDispatch(SystemClock.elapsedRealtime())
            }
        }

    var isTunnelActive: Boolean = false
        private set

    var currentSolarProfile: SolarElevationCalculator.SolarProfile = SolarElevationCalculator.SolarProfile.DAY
        private set

    var currentThreatStage: Int = 0
        private set

    var isManualOverridden: Boolean = false
        private set

    var lastDispatchedMode: RadarLightMode? = null
        private set

    private var lastDispatchRealtimeMs: Long = 0L
    private var threatCooldownUntilRealtimeMs: Long = 0L
    private var tunnelDarkSinceRealtimeMs: Long = 0L
    private var tunnelBrightSinceRealtimeMs: Long = 0L
    private var pendingModeToDispatch: RadarLightMode? = null

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val rateLimitFlushRunnable = Runnable {
        flushPendingDispatch()
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
        nowMs: Long = SystemClock.elapsedRealtime()
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
        nowMs: Long = SystemClock.elapsedRealtime()
    ) {
        val newProfile = SolarElevationCalculator.getSolarProfile(latitudeDeg, longitudeDeg, epochTimeMs)
        if (newProfile != currentSolarProfile) {
            currentSolarProfile = newProfile
            Log.i(TAG, "Solar profile updated to: $newProfile")
            evaluateAndDispatch(nowMs)
        }
    }

    /**
     * Radar target and threat state callback for 3-stage escalation (REQ-CON-034.4, REQ-CON-034.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onRadarStateChanged(
        state: RadarState,
        nowMs: Long = SystemClock.elapsedRealtime()
    ) {
        val rawStage = when {
            state.overallThreatLevel == ThreatLevel.FAST_APPROACHING ||
                    state.targets.any { it.threatLevel == ThreatLevel.FAST_APPROACHING } -> 2

            state.overallThreatLevel == ThreatLevel.APPROACHING ||
                    state.targets.any { it.threatLevel == ThreatLevel.APPROACHING } -> 1

            else -> 0
        }

        if (rawStage > 0) {
            threatCooldownUntilRealtimeMs = nowMs + COOLDOWN_HYSTERESIS_MS
            currentThreatStage = rawStage
        } else {
            // Hold previous elevated state until cooldown expires
            currentThreatStage = if (nowMs < threatCooldownUntilRealtimeMs) {
                1
            } else {
                0
            }
        }

        evaluateAndDispatch(nowMs)
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
    fun evaluateAndDispatch(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (!isAutomationEnabled || isManualOverridden || RadarLightController.isBrakeActive) return

        val targetMode = computeTargetMode(isTunnelActive, currentSolarProfile, currentThreatStage)
        if (targetMode == lastDispatchedMode) return

        val elapsedSinceLastDispatch = nowMs - lastDispatchRealtimeMs
        if (elapsedSinceLastDispatch >= RATE_LIMIT_INTERVAL_MS) {
            dispatchMode(targetMode, nowMs)
        } else {
            // Queue pending dispatch at the expiration of the rate-limit window
            pendingModeToDispatch = targetMode
            val delayMs = RATE_LIMIT_INTERVAL_MS - elapsedSinceLastDispatch
            try {
                handler.removeCallbacks(rateLimitFlushRunnable)
                handler.postDelayed(rateLimitFlushRunnable, delayMs)
            } catch (_: Exception) {
                // Headless test fallback
            }
        }
    }

    private fun dispatchMode(mode: RadarLightMode, nowMs: Long) {
        lastDispatchedMode = mode
        lastDispatchRealtimeMs = nowMs
        pendingModeToDispatch = null
        RadarLightController.setLightMode(mode)
    }

    private fun flushPendingDispatch() {
        val mode = pendingModeToDispatch ?: return
        val nowMs = SystemClock.elapsedRealtime()
        dispatchMode(mode, nowMs)
    }

    /**
     * Registers a manual user override from the Cockpit sheet, temporarily suspending automation.
     */
    @JvmStatic
    fun onManualOverride(mode: RadarLightMode) {
        isManualOverridden = true
        lastDispatchedMode = mode
        Log.i(TAG, "Manual override active: $mode (smart light automation suspended)")
    }

    /**
     * Resumes smart automation following a manual override.
     */
    @JvmStatic
    fun resumeAutomation() {
        isManualOverridden = false
        Log.i(TAG, "Smart light automation resumed")
        evaluateAndDispatch(SystemClock.elapsedRealtime())
    }

    /**
     * Resets engine state for unit testing isolation.
     */
    @JvmStatic
    fun reset() {
        isAutomationEnabled = false
        isTunnelActive = false
        currentSolarProfile = SolarElevationCalculator.SolarProfile.DAY
        currentThreatStage = 0
        isManualOverridden = false
        lastDispatchedMode = null
        lastDispatchRealtimeMs = 0L
        threatCooldownUntilRealtimeMs = 0L
        tunnelDarkSinceRealtimeMs = 0L
        tunnelBrightSinceRealtimeMs = 0L
        pendingModeToDispatch = null
        try {
            handler.removeCallbacks(rateLimitFlushRunnable)
        } catch (_: Exception) {}
    }
}
