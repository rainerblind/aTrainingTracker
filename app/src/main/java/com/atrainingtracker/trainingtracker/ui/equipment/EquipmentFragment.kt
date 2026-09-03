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

package com.atrainingtracker.trainingtracker.ui.equipment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class EquipmentFragment : Fragment() {

    private val viewModel: EquipmentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Read the starting tab from arguments (default to 0 if not found)
        val startingTab = arguments?.getInt("starting_tab") ?: 0

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ATrainingTrackerTheme {
                    EquipmentTabsScreen(
                        viewModel = viewModel,
                        initialTab = startingTab,
                        onNavigateToWorkouts = { stats ->
                            // When a stats block is clicked, navigate to the filtered workout list
                            navigateToFilteredWorkouts(stats)
                        }
                    )
                }
            }
        }
    }


    /**
     * Navigates to the WorkoutSummariesListFragment with the filters
     * defined in the clicked StatsData.
     */
    private fun navigateToFilteredWorkouts(stats: StatsData) {
        val fragment = WorkoutSummariesListFragment.newInstance(
            primaryTitle = stats.primaryTitle,
            secondaryTitle = stats.secondaryTitle,
            sportTypeId = stats.filterSportTypeId,
            equipmentId = stats.filterEquipmentId,
            startS = stats.startTimeS,
            endS = stats.endTimeS
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment) // Ensure this ID matches your Activity's container
            .addToBackStack(null)
            .commit()
    }

    private val syncReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            viewModel.loadEquipment()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(
            com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSynchronizeThread.SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED
        )
        androidx.core.content.ContextCompat.registerReceiver(
            requireContext(),
            syncReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            requireContext().unregisterReceiver(syncReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadEquipment()
    }

    companion object {
        const val TAG = "EquipmentFragment"

        @JvmStatic
        fun newInstance(startingTab: Int = 0): EquipmentFragment {
            return EquipmentFragment().apply {
                arguments = Bundle().apply {
                    putInt("starting_tab", startingTab)
                }
            }
        }
    }
}