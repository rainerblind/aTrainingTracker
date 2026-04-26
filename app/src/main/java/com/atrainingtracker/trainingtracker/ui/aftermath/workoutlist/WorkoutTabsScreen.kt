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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
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
    onMapClick: (Long) -> Unit
) {
    val tabs = listOf(
        stringResource(R.string.workout_summaries_tab_all),
        stringResource(R.string.workout_summaries_tab_bike),
        stringResource(R.string.workout_summaries_tab_run),
        stringResource(R.string.workout_summaries_tab_other)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 1. Calculate the total height of the Header (Status Bar + Heading + Trab Row)
    val appBarMaxHeightPx = with(density) { 125.dp.roundToPx() }

    // 2. Initialize the Connection from the article
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    // This is the root container (Baby Blue background)
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        // 3. The NestedScroll modifier is placed on the parent Box
        Box(Modifier.nestedScroll(connection)) {

            // THE CONTENT (Full screen)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
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
                    // Use a Spacer or contentPadding that reacts to the offset
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightPx = appBarMaxHeightPx.toFloat()
                )
            }

            // THE HEADER (Layered on top)
            Surface(
                modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // --- THE HEADING ---
                    Text(
                        text = stringResource(R.string.tab_workouts),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
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
    appBarOffsetPx: Int,
    headerHeightPx: Float
) {
    val density = LocalDensity.current
    val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            // Calculation: The initial header height (px) + the current offset (px)
            // convert the final result to Dp.
            top = with(density) { (headerHeightPx + appBarOffsetPx).toDp() },
            bottom = bottomPadding + 16.dp,
            start = 8.dp,
            end = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = workouts,
            key = { it.id }
        ) { workout ->
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
                    onMapClick = { onMapClick(workout.id) }
                )
            }
        }
    }
}