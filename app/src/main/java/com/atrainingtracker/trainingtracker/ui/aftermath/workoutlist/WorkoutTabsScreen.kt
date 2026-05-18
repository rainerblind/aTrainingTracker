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

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    pagerState: PagerState,
    allListState: LazyListState,
    bikeListState: LazyListState,
    runListState: LazyListState,
    otherListState: LazyListState,
    isPlayServiceAvailable: Boolean,
    onExportWorkoutTo: (Long, FileFormat) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    onEditWorkout: (Long) -> Unit,
    onMapClick: (WorkoutData) -> Unit,
    sortOrder: WorkoutSortOrder,
    onSortOrderChange: (WorkoutSortOrder) -> Unit,
    scrollToTop: Boolean,
    isCompactView: Boolean,
    onToggleCompactView: () -> Unit
) {
    val tabs = listOf(
        stringResource(R.string.workout_summaries_tab_all),
        stringResource(R.string.workout_summaries_tab_bike),
        stringResource(R.string.workout_summaries_tab_run),
        stringResource(R.string.workout_summaries_tab_other)
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Log.i("WorkoutTabsScreen", "Before LauncedEffect (scrollToTop: $scrollToTop)")
    LaunchedEffect(scrollToTop, sortOrder) {
        Log.i("WorkoutTabsScreen", "scrollToTop: $scrollToTop")

        if (scrollToTop) {
            allListState.scrollToItem(0)
            bikeListState.scrollToItem(0)
            runListState.scrollToItem(0)
            otherListState.scrollToItem(0)
        }
    }

    // 1. Calculate the total height of the Header (Status Bar + Heading + Trab Row)
    val appBarMaxHeightPx = with(density) { 135.dp.roundToPx() }

    // 2. Initialize the Connection from the article
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    // This is the root container
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
                val scrollState = when (pageIndex) {
                    1 -> bikeListState
                    2 -> runListState
                    3 -> otherListState
                    else -> allListState
                }

                WorkoutList(
                    scrollState = scrollState,
                    workouts = filteredWorkouts,
                    isPlayServiceAvailable = isPlayServiceAvailable,
                    onExportWorkout = onExportWorkoutTo,
                    onDeleteConfirmed = onDeleteConfirmed,
                    onEditWorkout = onEditWorkout,
                    onMapClick = onMapClick,
                    isCompactView = isCompactView,
                    // Use a Spacer or contentPadding that reacts to the offset
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightPx = appBarMaxHeightPx.toFloat()
                )
            }

            // THE HEADER (Layered on top)
            Surface(
                modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
                // tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // --- Heading Row with Sort Button ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.tab_workouts),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Toggle View Mode Button
                        IconButton(onClick = onToggleCompactView ) {
                            Icon(
                                imageVector = if (isCompactView) Icons.Default.ViewStream else Icons.Default.ViewHeadline,
                                contentDescription = "Switch View Mode",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Sort Dropdown
                        var showSortMenu by remember {
                            mutableStateOf(false)
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Sort,
                                    contentDescription = stringResource(R.string.sort),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            DropdownMenu(
                                containerColor = MaterialTheme.colorScheme.surface,
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                WorkoutSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(order.labelResId))
                                        },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (sortOrder == order) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
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
