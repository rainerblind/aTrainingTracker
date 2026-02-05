package com.atrainingtracker.trainingtracker.ui.aftermath

/**
 * Represents the state of the workout deletion process.
 */
sealed class DeletionProgress {
    // Represents the idle state where no deletion is happening.
    object Idle : DeletionProgress()

    // Represents the state where deletion is in progress for a specific workout.
    data class InProgress(val workoutName: String, val workoutId: Long) : DeletionProgress()
}