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
 * Represents the state of the workout deletion process.
 */
sealed class DeletionProgress {
    // Represents the idle state where no deletion is happening.
    object Idle : DeletionProgress()

    // Represents the state where deletion is in progress for a specific workout (single workout deletion or legacy).
    data class InProgress(val workoutName: String, val workoutId: Long) : DeletionProgress()

    // Represents granular bulk deletion progress.
    data class Deleting(
        val current: Int,
        val total: Int,
        val workoutName: String,
        val workoutId: Long
    ) : DeletionProgress() {
        val progress: Float
            get() = if (total > 0) current.toFloat() / total.toFloat() else 0f
    }

    // Represents downstream analytical cache resynchronization (Periods & Clusters).
    object Resyncing : DeletionProgress()
}