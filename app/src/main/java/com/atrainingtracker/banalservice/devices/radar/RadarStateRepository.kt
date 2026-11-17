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

    @JvmStatic
    fun updateState(newState: RadarState) {
        synchronized(lock) {
            val previousState = _radarState.value
            val previousActiveSource = activeSource
            sourceStates[newState.source] = newState

            if (previousActiveSource == RadarSource.NONE || newState.source == previousActiveSource) {
                syncControlFieldsAcrossSourcesLocked(newState)
            }

            // Two-level arbitration: CONNECTED sources strictly take precedence over CONNECTING sources (REQ-CON-041.2).
            // An active CONNECTED stream (e.g. ANT+) must never be preempted by a CONNECTING source (e.g. BLE scanning).
            val bestActive = sourceStates.values
                .filter { it.connectionState == RadarConnectionState.CONNECTED }
                .maxByOrNull { it.source.priority }
                ?: sourceStates.values
                    .filter { it.connectionState == RadarConnectionState.CONNECTING }
                    .maxByOrNull { it.source.priority }

            if (bestActive != null) {
                val resolvedBest = preserveControlFieldsOnFailoverLocked(bestActive, newState, previousState, previousActiveSource)
                activeSource = resolvedBest.source
                _radarState.value = resolvedBest
            } else {
                // If no sources are connected, update from the incoming source if it matches active or if active was NONE
                if (previousActiveSource == RadarSource.NONE || previousActiveSource == newState.source) {
                    activeSource = newState.source
                    _radarState.value = newState
                } else {
                    val bestSource = sourceStates.values.maxByOrNull { it.source.priority }
                    if (bestSource != null) {
                        val resolvedBest = preserveControlFieldsOnFailoverLocked(bestSource, newState, previousState, previousActiveSource)
                        activeSource = resolvedBest.source
                        _radarState.value = resolvedBest
                        syncControlFieldsAcrossSourcesLocked(resolvedBest)
                    } else {
                        activeSource = newState.source
                        _radarState.value = newState
                    }
                }
            }
        }
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

    private fun preserveControlFieldsOnFailoverLocked(
        candidate: RadarState,
        incomingState: RadarState,
        previousState: RadarState,
        previousActiveSource: RadarSource
    ): RadarState {
        if (previousActiveSource == RadarSource.NONE ||
            candidate.source == previousActiveSource ||
            candidate.source == incomingState.source
        ) {
            return candidate
        }
        return candidate.copy(
            isAutoMode = previousState.isAutoMode,
            audioSnoozeState = previousState.audioSnoozeState
        ).also { sourceStates[it.source] = it }
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
