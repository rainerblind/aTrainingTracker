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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Lifecycle-safe, non-composition coordinator for deferred Aftermath Polar offline retry syncs
 * (`REQ-SET-119.1`, `REQ-SET-119.2`, `ATT-8969`).
 *
 * Owns a [SupervisorJob] + [Dispatchers.IO] [CoroutineScope] independent of Jetpack Compose
 * composition lifetimes so recomposition or banner disposal never cancels in-flight PFTP syncs
 * with `ForgottenCoroutineScopeException`, deduplicates concurrent taps per `workoutId`, and emits
 * completion events so Aftermath view models can refresh HR telemetry graphs.
 */
object PolarRetrySyncCoordinator {

    /**
     * Per-workout reactive UI state for the Aftermath retry sync banner.
     */
    data class RetrySyncState(
        val isSyncing: Boolean = false,
        val errorMessage: String? = null
    )

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val inFlightWorkoutIds = ConcurrentHashMap.newKeySet<Long>()

    private val _states = MutableStateFlow<Map<Long, RetrySyncState>>(emptyMap())
    val states: StateFlow<Map<Long, RetrySyncState>> = _states.asStateFlow()

    private val _completedWorkoutEvents = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val completedWorkoutEvents: SharedFlow<Long> = _completedWorkoutEvents.asSharedFlow()

    @VisibleForTesting
    var dispatcher: CoroutineDispatcher = Dispatchers.IO

    @VisibleForTesting
    var syncExecutorOverride: (suspend (Context, Long, String?) -> Boolean)? = null

    /**
     * Returns the current [RetrySyncState] snapshot for [workoutId].
     */
    fun stateFor(workoutId: Long): RetrySyncState = _states.value[workoutId] ?: RetrySyncState()

    /**
     * Launches an offline Polar sync for [workoutId] on the coordinator's non-composition scope.
     *
     * Concurrent invocations while [workoutId] is already syncing are ignored (`null` returned).
     */
    @Suppress("TooGenericExceptionCaught")
    fun startSync(
        context: Context,
        workoutId: Long,
        polarExerciseId: String?,
        failedMessage: String
    ): Job? {
        if (!inFlightWorkoutIds.add(workoutId)) {
            return null
        }

        val appContext = context.applicationContext
        _states.update { current ->
            current + (workoutId to RetrySyncState(isSyncing = true, errorMessage = null))
        }

        return scope.launch(dispatcher) {
            try {
                val success = executePendingSync(appContext, workoutId, polarExerciseId)
                if (success) {
                    _states.update { current -> current - workoutId }
                    _completedWorkoutEvents.tryEmit(workoutId)
                } else {
                    _states.update { current ->
                        current + (workoutId to RetrySyncState(isSyncing = false, errorMessage = failedMessage))
                    }
                }
            } catch (ce: CancellationException) {
                _states.update { current ->
                    current + (workoutId to RetrySyncState(isSyncing = false, errorMessage = null))
                }
                throw ce
            } catch (_: Throwable) {
                _states.update { current ->
                    current + (workoutId to RetrySyncState(isSyncing = false, errorMessage = failedMessage))
                }
            } finally {
                inFlightWorkoutIds.remove(workoutId)
            }
        }
    }

    /**
     * Clears stored UI sync error/progress state for [workoutId] (e.g., on banner dismiss).
     */
    fun clearState(workoutId: Long) {
        _states.update { current -> current - workoutId }
    }

    private suspend fun executePendingSync(
        appContext: Context,
        workoutId: Long,
        polarExerciseId: String?
    ): Boolean {
        syncExecutorOverride?.let { override ->
            return override(appContext, workoutId, polarExerciseId)
        }
        return if (PolarVeritySenseOfflineController.isVerityPendingSync(polarExerciseId)) {
            PolarVeritySenseOfflineController.syncPendingWorkout(appContext, workoutId)
        } else {
            PolarH10OfflineController.syncPendingWorkout(appContext, workoutId)
        }
    }

    /**
     * Cancels active child jobs and clears all test overrides and state maps.
     */
    @VisibleForTesting
    fun resetForTesting() {
        scopeJob.cancelChildren()
        inFlightWorkoutIds.clear()
        _states.value = emptyMap()
        dispatcher = Dispatchers.IO
        syncExecutorOverride = null
    }
}
