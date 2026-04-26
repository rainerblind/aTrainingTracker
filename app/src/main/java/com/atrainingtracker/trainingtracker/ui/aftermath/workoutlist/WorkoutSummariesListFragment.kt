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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class WorkoutSummariesListFragment : Fragment() {

    // 1. Share the ViewModel with the Activity/Parent as in the Classic version
    private val viewModel: WorkoutSummariesViewModel by activityViewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) Log.i(TAG, "onCreateView()")

        return ComposeView(requireContext()).apply {
            // Dispose the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                ATrainingTrackerTheme {
                    // 2. Retrieve Arguments (same logic as Classic)
                    val bSportType = arguments?.getSerializable(ARG_BSPORT_TYPE) as? BSportType
                    val sportId = arguments?.getLong(ARG_SPORT_ID, -1)?.takeIf { it != -1L }
                    val equipId = arguments?.getLong(ARG_EQUIP_ID, -1)?.takeIf { it != -1L }
                    val startS = arguments?.getLong(ARG_START_S, -1L)?.takeIf { it != -1L }
                    val endS = arguments?.getLong(ARG_END_S, -1L)?.takeIf { it != -1L }

                    // 3. Observe the filtered Flow reactively
                    // We 'remember' the flow so we don't recreate the observer on every recomposition
                    val filteredWorkoutsFlow = remember(bSportType, sportId, equipId, startS, endS) {
                        viewModel.getFilteredWorkouts(
                            bSportType = bSportType,
                            sportTypeId = sportId,
                            equipmentId = equipId,
                            startTimeS = startS,
                            endTimeS = endS
                        )
                    }

                    val workouts by filteredWorkoutsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                    val isPlayAvailable = remember {
                        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS
                    }

                    // 4. Standalone scroll behavior
                    // (If this fragment is used inside the TabbedFragment, it will participate in nested scrolling)
                    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

                    WorkoutList(
                        workouts = workouts,
                        isPlayServiceAvailable = isPlayAvailable,
                        onExportWorkout = { id, format -> /* Call export logic */ },
                        onDeleteConfirmed = { id ->
                            // Reuse the same confirmation logic from the Classic Fragment
                            showDeleteConfirmationDialog(id)
                        },
                        onEditWorkout = { id -> /* Navigate to Edit */ },
                        onMapClick = { id -> /* Navigate to Map */ },
                        // Following the Article's approach:
                        appBarOffsetPx = scrollBehavior.state.heightOffset.toInt(),
                        headerHeightPx = 0f // Individual list fragments usually start at 0 unless they have their own header
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ensure data loading starts
        viewModel.loadWorkoutsIfNeeded()

        // Observe the delete event from VM (if triggered by a separate UI action)
        viewModel.confirmDeleteWorkoutEvent.observe(viewLifecycleOwner) { workoutId ->
            showDeleteConfirmationDialog(workoutId)
        }
    }

    private fun showDeleteConfirmationDialog(workoutId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_workout)
            .setMessage(R.string.really_delete_workout)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setPositiveButton(R.string.delete_workout) { _, _ ->
                viewModel.deleteWorkout(workoutId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val ARG_BSPORT_TYPE = "ARG_BSPORT_TYPE"
        const val ARG_SPORT_ID = "ARG_SPORT_ID"
        const val ARG_EQUIP_ID = "ARG_EQUIP_ID"
        const val ARG_START_S = "ARG_START_S"
        const val ARG_END_S = "ARG_END_S"
        const val TAG = "WorkoutSummariesListFragment"
        val DEBUG = TrainingApplication.getDebug(true)

        // Keep the exact same newInstance signature for compatibility
        fun newInstance(
            bSportType: BSportType? = null,
            sportTypeId: Long? = null,
            equipmentId: Long? = null,
            startS: Long? = null,
            endS: Long? = null
        ) = WorkoutSummariesListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_BSPORT_TYPE, bSportType)
                sportTypeId?.let { putLong(ARG_SPORT_ID, it) }
                equipmentId?.let { putLong(ARG_EQUIP_ID, it) }
                startS?.let { putLong(ARG_START_S, it) }
                endS?.let { putLong(ARG_END_S, it) }
            }
        }
    }
}