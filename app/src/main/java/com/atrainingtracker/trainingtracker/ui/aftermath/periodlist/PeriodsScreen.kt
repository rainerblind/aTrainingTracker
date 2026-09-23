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

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelFactory
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Top-level Composable for Periods summaries, map view, and drill-downs (REQ-UI-159).
 */
@Composable
fun PeriodsScreen(
    viewModel: PeriodsViewModel = viewModel(),
    onStartWorkoutList: ((PeriodSummary, BSportType?, Long?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isPlayAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    LaunchedEffect(Unit) {
        viewModel.loadPeriods()
    }

    val groupedPeriods by viewModel.groupedPeriods.collectAsStateWithLifecycle()
    val migrationStatus by viewModel.migrationStatus.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val enabledMarkerTypes by viewModel.enabledMarkerTypes.collectAsStateWithLifecycle()
    val groups = viewModel.groups

    val peekedWorkoutDataWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsStateWithLifecycle()

    var editedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }

    val pagerState = rememberPagerState(
        pageCount = { groups.size },
        initialPage = 1
    )
    val listStates = List(groups.size) { rememberLazyListState() }

    val handleStartWorkoutList: (PeriodSummary, BSportType?, Long?) -> Unit = onStartWorkoutList ?: { periodSummary, bSportType, scrollToWorkoutId ->
        val secondaryTitle = if (bSportType != null) {
            val count = periodSummary.sportStats[bSportType]?.count ?: 0
            val workoutsCountString = context.resources.getQuantityString(
                R.plurals.workout_periods__workouts,
                count,
                count
            )
            "${context.getString(bSportType.stringResId)} ($workoutsCountString)"
        } else {
            periodSummary.sportStats.entries
                .filter { it.value.count > 0 }
                .joinToString(", ") { (sport, stats) ->
                    "${stats.count} ${context.getString(sport.stringResId)}"
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

        (context as? FragmentActivity)?.supportFragmentManager?.let { fm ->
            // Use Android content view ID if available
            val containerId = android.R.id.content
            fm.beginTransaction()
                .add(containerId, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    Box(modifier = modifier) {
        if (selectedPeriod != null) {
            val mapState by viewModel.mapState.collectAsStateWithLifecycle()
            PeriodMapScreen(
                summary = selectedPeriod!!,
                mapState = mapState,
                enabledMarkerTypes = enabledMarkerTypes,
                onToggleMarkerType = { viewModel.toggleMarkerTypeEnabled(it) },
                onWorkoutClick = { id -> viewModel.selectWorkoutForPeek(id) },
                peekedWorkoutDataWithTrack = peekedWorkoutDataWithTrack,
                clearPeekSelection = { viewModel.clearPeekSelection() },
                onBack = { viewModel.dismissPeriodMap() },
                onEditWorkout = { id -> editedWorkoutId = id },
                onSelectRegion = { regionId -> viewModel.selectRegion(regionId) }
            )
        } else {
            PeriodsTabsScreen(
                groupedPeriods = groupedPeriods,
                pagerState = pagerState,
                listStates = listStates,
                onHeaderClick = { summary -> handleStartWorkoutList(summary, null, null) },
                onMapClick = { summary -> viewModel.showPeriodMap(summary) },
                onSportClick = { summary, bSportType -> handleStartWorkoutList(summary, bSportType, null) },
                onLongestWorkoutClick = { summary, bSportType, workoutId ->
                    handleStartWorkoutList(summary, bSportType, workoutId)
                },
                isPlayServiceAvailable = isPlayAvailable,
                tabs = groups,
                migrationStatus = migrationStatus
            )
        }
    }

    if (editedWorkoutId != null) {
        val app = context.applicationContext as Application
        val editViewModel: EditWorkoutViewModel = viewModel(
            key = "edit_workout_${editedWorkoutId}",
            factory = EditWorkoutViewModelFactory(app, editedWorkoutId!!)
        )

        EditWorkoutScreen(
            viewModel = editViewModel,
            onBack = {
                val id = editedWorkoutId
                editedWorkoutId = null
                viewModel.loadPeriods()
                if (id != null && selectedPeriod != null) {
                    viewModel.selectWorkoutForPeek(id)
                }
            }
        )
    }
}
