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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.launch

/**
 * The main container for the Aftermath section, organizing summaries into tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTabsScreen(
    workouts: List<WorkoutData>,
    isLoading: Boolean,
    isPlayServiceAvailable: Boolean,
    onExportWorkoutTo: (Long, FileFormat) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    onEditWorkout: (Long) -> Unit,
    onMapClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Define the scroll behavior
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val tabs = listOf(
        stringResource(R.string.workout_summaries_tab_all),
        stringResource(R.string.workout_summaries_tab_bike),
        stringResource(R.string.workout_summaries_tab_run),
        stringResource(R.string.workout_summaries_tab_other)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            // 2. Attach the scroll connection to the Scaffold
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        topBar = {
            // 3. The Surface provides the Baby Blue background for the Status Bar
            Surface(
                modifier = Modifier.graphicsLayer {
                    // This moves the tabs up/down
                    translationY = scrollBehavior.state.heightOffset
                },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(text = title) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // 4. CRITICAL: Link the Pager to the scroll connection
                // so the list movement triggers the TabRow translation
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            userScrollEnabled = true,
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            val filteredWorkouts = when (pageIndex) {
                1 -> workouts.filter { it.bSportType == BSportType.BIKE }
                2 -> workouts.filter { it.bSportType == BSportType.RUN }
                3 -> workouts.filter { it.bSportType == BSportType.UNKNOWN }
                else -> workouts
            }

            WorkoutList(
                workouts = filteredWorkouts,
                isPlayServiceAvailable = isPlayServiceAvailable,
                onExportWorkout = onExportWorkoutTo,
                onDeleteConfirmed = onDeleteConfirmed,
                onEditWorkout = onEditWorkout,
                onMapClick = onMapClick,
                // Pass the padding but we will handle the "moving up" inside
                contentPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The scrollable list of WorkoutSummaries.
 * This can be used independently or inside the Tab Pager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutList(
    workouts: List<WorkoutData>,
    isPlayServiceAvailable: Boolean,
    onExportWorkout: (Long, FileFormat) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    onEditWorkout: (Long) -> Unit,
    onMapClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =  PaddingValues(0.dp),
    scrollBehavior: TopAppBarScrollBehavior
) {
    val density = LocalDensity.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            // 2. Convert heightOffset (px) to Dp before adding to top padding
            top = (contentPadding.calculateTopPadding() + with(density) {
                scrollBehavior.state.heightOffset.toDp()
            }).coerceAtLeast(0.dp),

            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 8.dp,
            end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = workouts,
            key = { it.id }
        ) { workout ->
            // ... ElevatedCard logic remains the same ...
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                WorkoutSummary(
                    workoutData = workout,
                    isPlayServiceAvailable = isPlayServiceAvailable,
                    onExport = { fileFormat -> onExportWorkout(workout.id, fileFormat) },
                    onDeleteConfirmed = { onDeleteConfirmed(workout.id) },
                    onEditWorkout = { onEditWorkout(workout.id) },
                    onMapClick = { onMapClick(workout.id) },
                    modifier = modifier
                )
            }
        }
    }
}