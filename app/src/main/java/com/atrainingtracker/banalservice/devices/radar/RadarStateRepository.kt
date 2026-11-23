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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.util.concurrent.ConcurrentHashMap

/**
 * Reactive state repository exposing three orthogonal radar state streams (REQ-CON-028.4, REQ-CON-041, REQ-CON-056.3):
 * - [threatState]: 4–8 Hz vehicle targets and multi-source connection arbitration (`BLE > ANT_PLUS > SIMULATOR`, `CONNECTED > CONNECTING`, `sourceStates <= 4`).
 * - [lightState]: Tail-light mode, capability, and auto-mode flag (updated atomically via [MutableStateFlow.update]).
 * - [snoozeState]: Audio snooze mode and monotonic expiration deadline (updated atomically via [MutableStateFlow.update]).
 *
 * UI/ViewModel layers combine these streams on demand via [observeCombinedState] without coupling
 * background engines (`SmartRadarLightEngine`, `RadarAlertManager`) to unrelated domains.
 */
object RadarStateRepository {

    private val threatLock = Any()
    private val sourceStates = ConcurrentHashMap<RadarSource, RadarThreatState>()

    @Volatile
    private var activeSource: RadarSource = RadarSource.NONE

    private val _threatState = MutableStateFlow(RadarThreatState())
    val threatState: StateFlow<RadarThreatState> = _threatState.asStateFlow()

    private val _lightState = MutableStateFlow(RadarLightState())
    val lightState: StateFlow<RadarLightState> = _lightState.asStateFlow()

    private val _snoozeState = MutableStateFlow(RadarAudioSnoozeState.INACTIVE)
    val snoozeState: StateFlow<RadarAudioSnoozeState> = _snoozeState.asStateFlow()

    /**
     * Cold combined UI stream merging the three orthogonal slices on demand (REQ-CON-056.3).
     */
    fun observeCombinedState(): Flow<RadarState> = combine(
        threatState,
        lightState,
        snoozeState
    ) { threat, light, snooze ->
        combineSnapshot(threat, light, snooze)
    }

    /**
     * Atomically updates [lightState] via lock-free CAS [MutableStateFlow.updateAndGet] (REQ-CON-056.3).
     */
    @JvmStatic
    fun updateLightState(transform: (RadarLightState) -> RadarLightState): RadarLightState {
        return _lightState.updateAndGet(transform)
    }

    /**
     * Atomically updates [snoozeState] via lock-free CAS [MutableStateFlow.updateAndGet] (REQ-CON-056.3).
     */
    @JvmStatic
    fun updateSnoozeState(transform: (RadarAudioSnoozeState) -> RadarAudioSnoozeState): RadarAudioSnoozeState {
        return _snoozeState.updateAndGet(transform)
    }

    /**
     * Atomically publishes a [RadarThreatState] and performs two-level multi-source arbitration (`REQ-CON-041.2`).
     */
    @JvmStatic
    fun updateThreatState(newState: RadarThreatState): RadarThreatState {
        synchronized(threatLock) {
            return updateThreatLocked(newState)
        }
    }

    /**
     * Atomically builds and publishes a [RadarThreatState] from the latest active threat snapshot.
     */
    @JvmStatic
    fun updateThreatState(buildState: (RadarThreatState) -> RadarThreatState): RadarThreatState {
        synchronized(threatLock) {
            val newState = buildState(_threatState.value)
            return updateThreatLocked(newState)
        }
    }

    /**
     * Atomically evaluates a conditional [RadarThreatState] update, returning `null` if skipped.
     */
    @JvmStatic
    fun updateThreatStateIf(buildState: (RadarThreatState) -> RadarThreatState?): RadarThreatState? {
        synchronized(threatLock) {
            val newState = buildState(_threatState.value) ?: return null
            return updateThreatLocked(newState)
        }
    }

    /**
     * Compatibility control-state updater routing light and snooze changes to their orthogonal flows.
     */
    @JvmStatic
    fun updateControlState(transform: (RadarState) -> RadarState) {
        val before = getState()
        val after = transform(before)
        if (after.lightMode != before.lightMode ||
            after.isLightControlSupported != before.isLightControlSupported ||
            after.isAutoMode != before.isAutoMode
        ) {
            _lightState.update { current ->
                current.copy(
                    lightMode = if (after.lightMode != before.lightMode) after.lightMode else current.lightMode,
                    isLightControlSupported = if (after.isLightControlSupported != before.isLightControlSupported) after.isLightControlSupported else current.isLightControlSupported,
                    isAutoMode = if (after.isAutoMode != before.isAutoMode) after.isAutoMode else current.isAutoMode
                )
            }
        }
        if (after.audioSnoozeState != before.audioSnoozeState) {
            _snoozeState.update { after.audioSnoozeState }
        }
    }

    /**
     * Compatibility telemetry updater routing threat/connection fields strictly to [threatState].
     */
    @JvmStatic
    fun updateTelemetryState(buildState: (RadarState) -> RadarState): RadarState {
        synchronized(threatLock) {
            val built = buildState(getState())
            val resolved = updateThreatLocked(built.toThreatState())
            return combineSnapshot(resolved, _lightState.value, _snoozeState.value)
        }
    }

    /**
     * Compatibility conditional telemetry updater routing threat/connection fields strictly to [threatState].
     */
    @JvmStatic
    fun updateTelemetryStateIf(buildState: (RadarState) -> RadarState?): RadarState? {
        synchronized(threatLock) {
            val built = buildState(getState()) ?: return null
            val resolved = updateThreatLocked(built.toThreatState())
            return combineSnapshot(resolved, _lightState.value, _snoozeState.value)
        }
    }

    /**
     * Compatibility state publisher routing threat/connection fields to [threatState] and forwarding
     * non-default light/snooze fields to their orthogonal flows without clobbering active slices
     * when failover callers pass default light/snooze values.
     */
    @JvmStatic
    fun updateState(newState: RadarState) {
        synchronized(threatLock) {
            updateThreatLocked(newState.toThreatState())
        }
        if (newState.lightMode != RadarLightMode.OFF ||
            newState.isLightControlSupported ||
            !newState.isAutoMode
        ) {
            _lightState.update {
                RadarLightState(
                    lightMode = newState.lightMode,
                    isLightControlSupported = newState.isLightControlSupported,
                    isAutoMode = newState.isAutoMode
                )
            }
        }
        if (newState.audioSnoozeState != RadarAudioSnoozeState.INACTIVE) {
            _snoozeState.update { newState.audioSnoozeState }
        }
    }

    private fun updateThreatLocked(newState: RadarThreatState): RadarThreatState {
        val previousActiveSource = activeSource
        sourceStates[newState.source] = newState

        // Two-level arbitration: CONNECTED sources strictly take precedence over CONNECTING sources (REQ-CON-041.2).
        val bestActive = sourceStates.values
            .filter { it.connectionState == RadarConnectionState.CONNECTED }
            .maxByOrNull { it.source.priority }
            ?: sourceStates.values
                .filter { it.connectionState == RadarConnectionState.CONNECTING }
                .maxByOrNull { it.source.priority }

        val resolvedBest = when {
            bestActive != null -> bestActive
            previousActiveSource == RadarSource.NONE || previousActiveSource == newState.source -> newState
            else -> sourceStates.values.maxByOrNull { it.source.priority } ?: newState
        }

        activeSource = resolvedBest.source
        sourceStates[resolvedBest.source] = resolvedBest
        _threatState.update { resolvedBest }
        return resolvedBest
    }

    @JvmStatic
    fun getState(): RadarState {
        return combineSnapshot(_threatState.value, _lightState.value, _snoozeState.value)
    }

    @JvmStatic
    fun getState(source: RadarSource): RadarState {
        val threat = sourceStates[source] ?: return RadarState(source = source)
        return combineSnapshot(threat, _lightState.value, _snoozeState.value)
    }

    @JvmStatic
    fun getThreatState(source: RadarSource): RadarThreatState {
        return sourceStates[source] ?: RadarThreatState(source = source)
    }

    @JvmStatic
    fun getActiveSource(): RadarSource {
        return activeSource
    }

    @JvmStatic
    fun reset() {
        synchronized(threatLock) {
            sourceStates.clear()
            activeSource = RadarSource.NONE
            _threatState.update { RadarThreatState() }
        }
        _lightState.update { RadarLightState() }
        _snoozeState.update { RadarAudioSnoozeState.INACTIVE }
    }

    private fun RadarState.toThreatState(): RadarThreatState = RadarThreatState(
        connectionState = connectionState,
        overallThreatLevel = overallThreatLevel,
        targets = targets,
        lastUpdateRealtimeMs = lastUpdateRealtimeMs,
        source = source
    )

    private fun combineSnapshot(
        threat: RadarThreatState,
        light: RadarLightState,
        snooze: RadarAudioSnoozeState
    ): RadarState = RadarState(
        connectionState = threat.connectionState,
        overallThreatLevel = threat.overallThreatLevel,
        targets = threat.targets,
        lastUpdateRealtimeMs = threat.lastUpdateRealtimeMs,
        lightMode = light.lightMode,
        isLightControlSupported = light.isLightControlSupported,
        audioSnoozeState = snooze,
        source = threat.source,
        isAutoMode = light.isAutoMode
    )
}
