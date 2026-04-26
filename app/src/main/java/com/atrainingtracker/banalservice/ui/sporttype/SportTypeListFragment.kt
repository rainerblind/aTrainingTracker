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

package com.atrainingtracker.banalservice.ui.sporttype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragmentClassic
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class SportTypeListFragment : Fragment() {

    private val viewModel: SportTypeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // The Screen now handles its own state for adding/editing/deleting
                    SportTypeScreen(
                        viewModel = viewModel,
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

    companion object {
        val TAG: String = SportTypeListFragment::class.java.name

        @JvmStatic
        fun newInstance(): SportTypeListFragment {
            return SportTypeListFragment()
        }
    }
}