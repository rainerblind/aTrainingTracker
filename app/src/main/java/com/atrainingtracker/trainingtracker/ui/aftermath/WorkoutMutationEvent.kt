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

package com.atrainingtracker.trainingtracker.ui.aftermath

/**
 * Authoritative post-commit domain events emitted by [WorkoutRepository.workoutMutationEvents]
 * after persistent workout deletions or finished-workout mutations complete (`REQ-DAT-049.3`).
 *
 * Strictly NEVER emitted on 1-Hz unfinished live-tracking ticks (`REQ-STB-020.4`).
 */
sealed class WorkoutMutationEvent {

    /**
     * Emitted once after a single workout has been purged from SQLite and sidecar storage.
     */
    data class Deleted(
        val workoutId: Long,
        val clusterId: Long = -1L,
        val equipmentId: Long = -1L,
        val sportId: Long = -1L,
        val wasFinished: Boolean = true
    ) : WorkoutMutationEvent()

    /**
     * Emitted once after a bulk deletion cascade commits all removals.
     */
    data class BulkDeleted(
        val workoutIds: List<Long>
    ) : WorkoutMutationEvent()

    /**
     * Emitted after a finished workout is finalized, saved, or cropped in SQLite.
     */
    data class Finalized(
        val workoutId: Long
    ) : WorkoutMutationEvent()
}
