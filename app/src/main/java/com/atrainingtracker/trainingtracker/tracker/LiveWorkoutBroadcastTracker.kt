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

package com.atrainingtracker.trainingtracker.tracker

/**
 * Functional callback invoked when a discrete workout lifecycle transition should notify
 * internal repository listeners via {@link TrackerService#WORKOUT_UPDATED_INTENT}.
 */
fun interface WorkoutUpdatedDispatcher {
    fun dispatch(workoutId: Long)
}

/**
 * Coordinates discrete {@link TrackerService#WORKOUT_UPDATED_INTENT} local broadcasts across live
 * workout lifecycle transitions ({@code REQ-STB-020.1}, {@code REQ-STB-020.2}).
 *
 * Eliminates 1-Hz active and paused steady-state broadcast storms while still dispatching single
 * updates when a workout starts/resumes, transitions between {@code TRACKING} and {@code PAUSED},
 * persists a live lap split, or updates sport metadata.
 */
class LiveWorkoutBroadcastTracker(
    private val dispatcher: WorkoutUpdatedDispatcher,
    private val liveCardStateStore: LiveWorkoutCardStateStore
) {
    constructor(dispatcher: WorkoutUpdatedDispatcher) : this(
        dispatcher = dispatcher,
        liveCardStateStore = LiveWorkoutCardStateStore.Default
    )
    private val lock = Any()
    private var trackedWorkoutId: Long = -1L
    private var lastPausedState: Boolean? = null

    /**
     * Records a session start or resume initialization and emits one discrete broadcast when
     * {@code workoutId > 0}. Seeds {@code isPaused} so subsequent steady-state ticks stay silent.
     */
    fun onSessionInitialized(workoutId: Long, isPaused: Boolean) {
        if (workoutId <= 0L) return
        synchronized(lock) {
            trackedWorkoutId = workoutId
            lastPausedState = isPaused
        }
        liveCardStateStore.onSessionStarted(workoutId, isPaused)
        dispatcher.dispatch(workoutId)
    }

    /**
     * Evaluates a 1-Hz sampling tick and emits a single broadcast strictly on a
     * {@code TRACKING <-> PAUSED} transition for {@code workoutId}. Steady-state active ticks,
     * steady-state paused ticks, and the initial baseline observation emit zero broadcasts.
     *
     * @return {@code true} if a pause/resume edge broadcast was dispatched.
     */
    fun onPauseStateTick(workoutId: Long, isPaused: Boolean): Boolean {
        if (workoutId <= 0L) return false
        val shouldDispatch = synchronized(lock) {
            if (trackedWorkoutId != workoutId || lastPausedState == null) {
                trackedWorkoutId = workoutId
                lastPausedState = isPaused
                false
            } else if (lastPausedState != isPaused) {
                lastPausedState = isPaused
                true
            } else {
                false
            }
        }
        liveCardStateStore.onPauseStateChanged(workoutId, isPaused)
        if (shouldDispatch) {
            dispatcher.dispatch(workoutId)
        }
        return shouldDispatch
    }

    /**
     * Dispatches a single broadcast for a discrete non-tick lifecycle event (e.g., live lap save
     * outside {@code endWorkout} or sport update) while preserving the tracked pause state.
     */
    fun onDiscreteEdge(workoutId: Long) {
        if (workoutId <= 0L) return
        dispatcher.dispatch(workoutId)
    }

    /**
     * Clears tracked session state when tracking stops or tears down.
     */
    fun reset() {
        synchronized(lock) {
            trackedWorkoutId = -1L
            lastPausedState = null
        }
        liveCardStateStore.clear()
    }
}
