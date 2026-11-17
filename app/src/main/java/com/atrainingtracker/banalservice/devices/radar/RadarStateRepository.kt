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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Reactive state repository providing high-frequency (4–8 Hz) radar telemetry stream (REQ-CON-028.4, REQ-CON-041).
 *
 * Implements deterministic multi-source arbitration across concurrent BLE, ANT+, and simulation streams:
 * BLE (3) > ANT_PLUS (2) > SIMULATOR (1) > NONE (0).
 */
object RadarStateRepository {

    private val lock = Any()
    private val sourceStates = ConcurrentHashMap<RadarSource, RadarState>()

    @Volatile
    private var activeSource: RadarSource = RadarSource.NONE

    private val _radarState = MutableStateFlow(RadarState())
    val radarState: StateFlow<RadarState> = _radarState.asStateFlow()

    /**
     * Atomically applies an application-level control state transform (e.g. `isAutoMode`, `lightMode`,
     * `audioSnoozeState`) under [lock] and synchronizes `isAutoMode` and `audioSnoozeState` across
     * all tracked [sourceStates] entries so multi-source failover cannot revert user overrides.
     */
    @JvmStatic
    fun updateControlState(transform: (RadarState) -> RadarState) {
        synchronized(lock) {
            val updated = transform(_radarState.value)
            _radarState.value = updated
            if (updated.source != RadarSource.NONE) {
                sourceStates[updated.source] = updated
            }
            syncControlFieldsAcrossSourcesLocked(updated)
        }
    }

    /**
     * Atomically builds and publishes a telemetry/lifecycle [RadarState] under [lock] from the
     * latest repository snapshot, eliminating TOCTOU races with concurrent [updateControlState] calls.
     */
    @JvmStatic
    fun updateTelemetryState(buildState: (RadarState) -> RadarState): RadarState {
        synchronized(lock) {
            val newState = buildState(_radarState.value)
            updateStateLocked(newState)
            return newState
        }
    }

    /**
     * Atomically evaluates a conditional telemetry/lifecycle [RadarState] under [lock].
     * Returns `null` without mutating repository state when [buildState] returns `null`.
     */
    @JvmStatic
    fun updateTelemetryStateIf(buildState: (RadarState) -> RadarState?): RadarState? {
        synchronized(lock) {
            val newState = buildState(_radarState.value) ?: return null
            updateStateLocked(newState)
            return newState
        }
    }

    @JvmStatic
    fun updateState(newState: RadarState) {
        synchronized(lock) {
            updateStateLocked(newState)
        }
    }

    private fun updateStateLocked(newState: RadarState) {
        val previousState = _radarState.value
        val previousActiveSource = activeSource
        sourceStates[newState.source] = newState

        // Two-level arbitration: CONNECTED sources strictly take precedence over CONNECTING sources (REQ-CON-041.2).
        // An active CONNECTED stream (e.g. ANT+) must never be preempted by a CONNECTING source (e.g. BLE scanning).
        val bestActive = sourceStates.values
            .filter { it.connectionState == RadarConnectionState.CONNECTED }
            .maxByOrNull { it.source.priority }
            ?: sourceStates.values
                .filter { it.connectionState == RadarConnectionState.CONNECTING }
                .maxByOrNull { it.source.priority }

        val resolvedBest = if (bestActive != null) {
            preserveControlFieldsAcrossSourcesLocked(bestActive, previousState, previousActiveSource)
        } else if (previousActiveSource == RadarSource.NONE || previousActiveSource == newState.source) {
            preserveControlFieldsAcrossSourcesLocked(newState, previousState, previousActiveSource)
        } else {
            val bestSource = sourceStates.values.maxByOrNull { it.source.priority }
            if (bestSource != null) {
                preserveControlFieldsAcrossSourcesLocked(bestSource, previousState, previousActiveSource)
            } else {
                preserveControlFieldsAcrossSourcesLocked(newState, previousState, previousActiveSource)
            }
        }

        activeSource = resolvedBest.source
        sourceStates[resolvedBest.source] = resolvedBest
        _radarState.value = resolvedBest
        syncControlFieldsAcrossSourcesLocked(resolvedBest)
    }

    private fun syncControlFieldsAcrossSourcesLocked(authoritative: RadarState) {
        for ((source, existing) in sourceStates) {
            if (source != authoritative.source) {
                sourceStates[source] = existing.copy(
                    isAutoMode = authoritative.isAutoMode,
                    audioSnoozeState = authoritative.audioSnoozeState
                )
            }
        }
    }

    private fun preserveControlFieldsAcrossSourcesLocked(
        candidate: RadarState,
        previousState: RadarState,
        previousActiveSource: RadarSource
    ): RadarState {
        val crossesSourceOrActiveOverride =
            previousActiveSource != RadarSource.NONE || candidate.source != previousState.source
        val preservedIsAutoMode = if (!previousState.isAutoMode && candidate.isAutoMode && crossesSourceOrActiveOverride) {
            false
        } else {
            candidate.isAutoMode
        }
        val preservedSnooze = if (previousState.audioSnoozeState.isSnoozed &&
            !candidate.audioSnoozeState.isSnoozed &&
            crossesSourceOrActiveOverride
        ) {
            previousState.audioSnoozeState
        } else {
            candidate.audioSnoozeState
        }
        val preservedLightMode = if (previousActiveSource == RadarSource.NONE &&
            candidate.source != previousState.source &&
            previousState.lightMode != RadarLightMode.OFF &&
            candidate.lightMode == RadarLightMode.OFF
        ) {
            previousState.lightMode
        } else {
            candidate.lightMode
        }
        val preservedLightSupported = candidate.isLightControlSupported ||
            (previousActiveSource == RadarSource.NONE &&
                candidate.source != previousState.source &&
                previousState.isLightControlSupported)

        return if (preservedIsAutoMode == candidate.isAutoMode &&
            preservedSnooze == candidate.audioSnoozeState &&
            preservedLightMode == candidate.lightMode &&
            preservedLightSupported == candidate.isLightControlSupported
        ) {
            candidate
        } else {
            candidate.copy(
                isAutoMode = preservedIsAutoMode,
                audioSnoozeState = preservedSnooze,
                lightMode = preservedLightMode,
                isLightControlSupported = preservedLightSupported
            )
        }
    }

    @JvmStatic
    fun getState(): RadarState {
        return _radarState.value
    }

    @JvmStatic
    fun getState(source: RadarSource): RadarState {
        return sourceStates[source] ?: RadarState(source = source)
    }

    @JvmStatic
    fun getActiveSource(): RadarSource {
        return activeSource
    }

    @JvmStatic
    fun reset() {
        synchronized(lock) {
            sourceStates.clear()
            activeSource = RadarSource.NONE
            _radarState.value = RadarState()
        }
    }
}
