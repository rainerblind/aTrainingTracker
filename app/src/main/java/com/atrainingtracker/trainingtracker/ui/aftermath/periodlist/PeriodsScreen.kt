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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutScreen
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModel
import com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.EditWorkoutViewModelFactory
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a single-day [PeriodSummary] used to route day-cell clicks through the pure Compose
 * workout-list navigation callback without legacy Fragment transactions (`REQ-UI-2799.1`, `ATT-8125`).
 */
internal fun buildDayPeriodSummary(
    date: LocalDate,
    sport: BSportType,
    activity: DailySportActivity,
    locale: Locale = Locale.getDefault(),
    zoneId: ZoneId = ZoneId.systemDefault()
): PeriodSummary {
    val dayStartS = date.atStartOfDay(zoneId).toEpochSecond()
    val dayEndS = date.plusDays(1).atStartOfDay(zoneId).toEpochSecond() - 1L
    val dateTitle = date.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", locale))
    return PeriodSummary(
        periodLabel = dateTitle,
        periodDateRange = dateTitle,
        periodType = PeriodType.DAY,
        startTimestampS = dayStartS,
        endTimestampS = dayEndS,
        totalWorkouts = activity.count,
        totalDurationSec = activity.durationSec,
        totalDistance = activity.distanceMeters,
        sportStats = mapOf(
            sport to SportStats(
                count = activity.count,
                totalDurationSec = activity.durationSec,
                totalDistanceMeters = activity.distanceMeters,
                totalAscentMeters = activity.ascentMeters,
                detailedSportStats = emptyMap(),
                longestWorkout = null
            )
        ),
        sortKey = date.toString()
    )
}

/**
 * Top-level Composable for Periods summaries, map view, and drill-downs (REQ-UI-159, REQ-UI-2799).
 */
@Composable
fun PeriodsScreen(
    viewModel: PeriodsViewModel = viewModel(),
    onStartWorkoutList: ((PeriodSummary, BSportType?, Long?) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trackOnMapViewModel: TrackOnMapAftermathViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]

    val isPlayAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    LaunchedEffect(Unit) {
        viewModel.loadPeriods()
    }

    val groupedPeriods by viewModel.groupedPeriods.collectAsStateWithLifecycle()
    val filteredGroupedPeriods by viewModel.filteredGroupedPeriods.collectAsStateWithLifecycle()
    val selectedSports by viewModel.selectedSports.collectAsStateWithLifecycle()
    val migrationStatus by viewModel.migrationStatus.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val enabledMarkerTypes by viewModel.enabledMarkerTypes.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val groups = viewModel.groups

    val peekedWorkoutDataWithTrack by viewModel.peekedWorkoutDataWithTrack.collectAsStateWithLifecycle()

    var editedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }

    val pagerState = rememberPagerState(
        pageCount = { groups.size },
        initialPage = 1
    )
    val listStates = List(groups.size) { rememberLazyListState() }

    val handleStartWorkoutList: (PeriodSummary, BSportType?, Long?) -> Unit = { periodSummary, bSportType, scrollToWorkoutId ->
        onStartWorkoutList?.invoke(periodSummary, bSportType, scrollToWorkoutId)
    }

    val handleDayClick: (PeriodSummary, BSportType, LocalDate, DailySportActivity?) -> Unit = { _, sport, date, activity ->
        if (activity != null && activity.count > 0) {
            val daySummary = buildDayPeriodSummary(
                date = date,
                sport = sport,
                activity = activity,
                locale = currentLocale
            )
            handleStartWorkoutList(daySummary, sport, activity.workoutIds.firstOrNull())
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
                onSelectRegion = { regionId -> viewModel.selectRegion(regionId) },
                trackOnMapViewModel = trackOnMapViewModel
            )
        } else {
            PeriodsTabsScreen(
                groupedPeriods = filteredGroupedPeriods,
                pagerState = pagerState,
                listStates = listStates,
                onHeaderClick = { summary -> handleStartWorkoutList(summary, null, null) },
                onMapClick = { summary, regionId -> viewModel.showPeriodMap(summary, regionId) },
                onSportClick = { summary, bSportType -> handleStartWorkoutList(summary, bSportType, null) },
                onLongestWorkoutClick = { summary, bSportType, workoutId ->
                    handleStartWorkoutList(summary, bSportType, workoutId)
                },
                isPlayServiceAvailable = isPlayAvailable,
                tabs = groups,
                migrationStatus = migrationStatus,
                selectedSports = selectedSports,
                onToggleSportFilter = { viewModel.toggleSportFilter(it) },
                selectedMetric = selectedMetric,
                onSelectMetric = { viewModel.selectMetric(it) },
                onDayCellClick = handleDayClick,
                onMenuClick = onMenuClick
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
