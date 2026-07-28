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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlin.getValue

class PeriodsFragment : Fragment() {

    // Initialize the existing ViewModel
    private val viewModel: PeriodsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Tell the ViewModel to ensure all data is loaded from the DB
        viewModel.loadPeriods()


        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // 1. Observe the periods list and migration status from ViewModel
                    val groupedPeriods by viewModel.groupedPeriods.collectAsStateWithLifecycle()
                    val migrationStatus by viewModel.migrationStatus.collectAsStateWithLifecycle()
                    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
                    val isHeatmapEnabled by viewModel.isHeatmapEnabled.collectAsStateWithLifecycle()
                    val enabledMarkerTypes by viewModel.enabledMarkerTypes.collectAsStateWithLifecycle()
                    val groups = viewModel.groups

                    val peekedWorkoutDataWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsStateWithLifecycle()

                    // 1. HOIST SCROLL STATES
                    // These will live as long as the Fragment's View is alive
                    val pagerState = rememberPagerState(
                        pageCount = { groups.size },
                        initialPage = 1) // Set the initial page to the weeks.
                    val listStates = List(groups.size) { rememberLazyListState() }

                    if (selectedPeriod != null) {
                        val mapState by viewModel.mapState.collectAsStateWithLifecycle()
                        PeriodMapScreen(
                            summary = selectedPeriod!!,
                            mapState = mapState,
                            isHeatmapEnabled = isHeatmapEnabled,
                            onToggleHeatmapEnabled = { viewModel.toggleHeatmapEnabled() },
                            enabledMarkerTypes = enabledMarkerTypes,
                            onToggleMarkerType = { viewModel.toggleMarkerTypeEnabled(it) },
                            onWorkoutClick = { id -> viewModel.selectWorkoutForPeek(id) },
                            peekedWorkoutDataWithTrack = peekedWorkoutDataWithTrack,
                            clearPeekSelection = { viewModel.clearPeekSelection() },
                            onBack = { viewModel.dismissPeriodMap() }
                        )
                    }
                    else {
                        PeriodsTabsScreen(
                            groupedPeriods = groupedPeriods,
                            pagerState = pagerState,
                            listStates = listStates,
                            onHeaderClick = { summary -> startWorkoutSummaryList(summary) },
                            onMapClick = { summary -> viewModel.showPeriodMap(summary) },
                            onSportClick = { summary, bSportType -> startWorkoutSummaryList(summary, bSportType) },
                            onLongestWorkoutClick = { summary, bSportType, workoutId -> 
                                startWorkoutSummaryList(summary, bSportType, workoutId) 
                            },
                            isPlayServiceAvailable = isPlayAvailable,
                            tabs = groups,
                            isHeatmapEnabled = isHeatmapEnabled,
                            onToggleHeatmapEnabled = { viewModel.toggleHeatmapEnabled() },
                            migrationStatus = migrationStatus
                        )
                    }
                }
            }
        }
    }

    fun startWorkoutSummaryList(
        periodSummary: PeriodSummary, 
        bSportType: BSportType? = null,
        scrollToWorkoutId: Long? = null
    ) {
        // calc the secondary title
        // Either "Runs (3 workouts)" or "3 Running, 5 Cycling"
        val secondaryTitle = if (bSportType != null) {
            // Case 1: Specific sport clicked
            val count = periodSummary.sportStats[bSportType]?.count ?: 0
            val workoutsCountString = resources.getQuantityString(
                R.plurals.workout_periods__workouts,
                count,
                count
            )
            "${getString(bSportType.stringResId)} ($workoutsCountString)"
        } else {
            // Case 2: Header clicked (No specific sport)
            // Creates a string like: "3 Running, 2 Cycling"
            periodSummary.sportStats.entries
                .filter { it.value.count > 0 } // Only show sports that have workouts
                .joinToString(", ") { (sport, stats) ->
                    "${stats.count} ${getString(sport.stringResId)}"
                }
        }

        val fragment = WorkoutSummariesListFragment.newInstance(
            primaryTitle = periodSummary.periodLabel,
            secondaryTitle = secondaryTitle,
            bSportType = bSportType,
            startS = periodSummary.startTimestampS,
            endS = periodSummary.endTimestampS,
            scrollToWorkoutId = scrollToWorkoutId
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }


    // Companion object for Java compatibility
    companion object {
        @JvmField
        val TAG: String = "PeriodsFragment"

        @JvmStatic
        fun newInstance(): PeriodsFragment {
            return PeriodsFragment()
        }
    }
}