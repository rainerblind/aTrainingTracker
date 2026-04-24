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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class WorkoutSummariesTabbedFragment : Fragment() {

    // Initialize the existing ViewModel
    private val viewModel: WorkoutSummariesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Tell the ViewModel to ensure all data is loaded from the DB
        viewModel.loadWorkouts()

        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // 1. Observe the workouts list from ViewModel
                    val workouts by viewModel.workouts.observeAsState(initial = emptyList())

                    // 2. Observe the loading state
                    val isLoading by viewModel.isLoading.observeAsState(initial = false)

                    // 3. Render the Tabbed UI
                    WorkoutTabsScreen(
                        workouts = workouts,
                        isLoading = isLoading,
                        onMenuClick = { workoutData ->
                            // Here you can handle the menu click,
                            // e.g., opening a popup or calling viewModel logic
                            // viewModel.onWorkoutAction(workoutData.headerData.id)
                        },
                        onEditWorkout = { workoutId ->
                            TrainingApplication.startEditWorkoutActivity(workoutId, false)
                        },
                        onMapClick = { workoutId ->
                            TrainingApplication.startTrackOnMapAftermathActivity(activity, workoutId)
                        },
                        isPlayServiceAvailable = isPlayAvailable,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle deletion events or other one-time events from the ViewModel
        viewModel.confirmDeleteWorkoutEvent.observe(viewLifecycleOwner) { workoutId ->
            // Trigger your existing Delete Dialog logic here if needed
        }
    }


    // Companion object for Java compatibility
    companion object {
        @JvmField
        val TAG: String = "WorkoutSummariesTabbedFragment"

        @JvmStatic
        fun newInstance(): WorkoutSummariesTabbedFragment {
            return WorkoutSummariesTabbedFragment()
        }
    }
}