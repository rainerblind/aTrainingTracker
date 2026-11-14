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

    @JvmStatic
    fun updateState(newState: RadarState) {
        synchronized(lock) {
            sourceStates[newState.source] = newState

            // Arbitrate active stream: highest priority source that is CONNECTED or CONNECTING
            val bestConnected = sourceStates.values
                .filter { it.connectionState == RadarConnectionState.CONNECTED || it.connectionState == RadarConnectionState.CONNECTING }
                .maxByOrNull { it.source.priority }

            if (bestConnected != null) {
                activeSource = bestConnected.source
                _radarState.value = bestConnected
            } else {
                // If no sources are connected, update from the incoming source if it matches active or if active was NONE
                if (activeSource == RadarSource.NONE || activeSource == newState.source) {
                    activeSource = newState.source
                    _radarState.value = newState
                } else {
                    val bestSource = sourceStates.values.maxByOrNull { it.source.priority }
                    if (bestSource != null) {
                        activeSource = bestSource.source
                        _radarState.value = bestSource
                    } else {
                        activeSource = newState.source
                        _radarState.value = newState
                    }
                }
            }
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
