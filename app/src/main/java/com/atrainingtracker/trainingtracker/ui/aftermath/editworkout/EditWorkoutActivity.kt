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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class EditWorkoutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve parameters
        val workoutId = intent.getLongExtra(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, -1L)
        val showDetails = intent.getBooleanExtra(EXTRA_SHOW_DETAILS, false)
        val showExtrema = intent.getBooleanExtra(EXTRA_SHOW_EXTREMA, false)

        if (workoutId == -1L) {
            Toast.makeText(this, "Error: Invalid Workout ID.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Factory and ViewModel
        val factory = EditWorkoutViewModelFactory(application, workoutId)
        val viewModel = ViewModelProvider(this, factory)[EditWorkoutViewModel::class.java]

        setContent {
            ATrainingTrackerTheme {
                EditWorkoutScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_DETAILS = "com.atrainingtracker.trainingtracker.SHOW_DETAILS"
        const val EXTRA_SHOW_EXTREMA = "com.atrainingtracker.trainingtracker.SHOW_EXTREMA"
    }
}