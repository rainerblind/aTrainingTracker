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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.launch

/**
 * The main container for the Aftermath section, organizing summaries into tabs.
 */
@Composable
fun WorkoutTabsScreen(
    workouts: List<WorkoutData>,
    isLoading: Boolean,  // TODO: show a CircularProgressIndicator here
    isPlayServiceAvailable: Boolean,
    onMenuClick: (WorkoutData) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        stringResource(R.string.workout_summaries_tab_all),
        stringResource(R.string.workout_summaries_tab_bike),
        stringResource(R.string.workout_summaries_tab_run),
        stringResource(R.string.workout_summaries_tab_other)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Bar
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            // containerColor = MaterialTheme.colorScheme.surface,
            // contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(text = title) }
                )
            }
        }

        // Tab Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            when (pageIndex) {
                0 -> WorkoutList(workouts = workouts, isPlayServiceAvailable = isPlayServiceAvailable, onMenuClick = onMenuClick)
                1 -> WorkoutList(workouts = workouts.filter { it.sportData.bSportType == BSportType.BIKE }, isPlayServiceAvailable = isPlayServiceAvailable,  onMenuClick = onMenuClick)
                2 -> WorkoutList(workouts = workouts.filter { it.sportData.bSportType == BSportType.RUN }, isPlayServiceAvailable = isPlayServiceAvailable, onMenuClick = onMenuClick)
                3 -> WorkoutList(workouts = workouts.filter { it.sportData.bSportType == BSportType.UNKNOWN }, isPlayServiceAvailable = isPlayServiceAvailable, onMenuClick = onMenuClick)
            }
        }
    }
}

/**
 * The scrollable list of WorkoutSummaries.
 * This can be used independently or inside the Tab Pager.
 */
@Composable
fun WorkoutList(
    workouts: List<WorkoutData>,
    isPlayServiceAvailable: Boolean,
    onMenuClick: (WorkoutData) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = workouts,
            key = { it.id } // Use workout ID as key for stable animations
        ) { workout ->
            WorkoutSummary(
                workoutData = workout,
                onMenuClick = { onMenuClick(workout) },
                isPlayServiceAvailable = isPlayServiceAvailable,
                onMapClick = { }, // TODO: move forward / upwards later on.
                modifier = modifier
            )
            HorizontalDivider(
                thickness = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}