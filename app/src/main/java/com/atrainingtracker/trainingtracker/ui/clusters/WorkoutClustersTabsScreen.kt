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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutClustersTabsScreen(
    viewModel: WorkoutClustersViewModel,
    pagerState: PagerState,
    allListState: LazyListState,
    bikeListState: LazyListState,
    runListState: LazyListState,
    otherListState: LazyListState,
    unclusteredListState: LazyListState,
    onClusterClick: (WorkoutCluster) -> Unit,
    onWorkoutClick: (WorkoutData) -> Unit,
    onHitCountClick: (WorkoutCluster) -> Unit,
    onTuneClick: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteRequest: (WorkoutCluster) -> Unit,
    migrationStatus: com.atrainingtracker.trainingtracker.ui.util.MigrationStatus? = null
) {
    val clusters by viewModel.allClusters.collectAsState()
    val unclusteredWorkouts by viewModel.unclusteredWorkouts.collectAsState()
    
    val tabs = listOf(
        stringResource(R.string.sport_type_tab_all) to null,
        stringResource(R.string.sport_type_tab_bike) to BSportType.BIKE,
        stringResource(R.string.sport_type_tab_run) to BSportType.RUN,
        stringResource(R.string.sport_type_tab_unknown) to BSportType.UNKNOWN,
        stringResource(R.string.unclustered) to null // We'll handle this by index
    )

    val unclusteredIndex = tabs.size - 1

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Header height (SCRUM-212: Synchronized with Routes/Segments at 135dp)
    val headerHeightDp = 135.dp
    val headerHeightPx = with(density) { headerHeightDp.roundToPx() }

    val connection = remember(headerHeightPx) {
        CollapsingAppBarNestedScrollConnection(headerHeightPx)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.nestedScroll(connection)) {
            // 1. THE CONTENT (Pager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val isUnclusteredTab = pageIndex == unclusteredIndex
                val currentSport = tabs[pageIndex].second
                val listState = when {
                    isUnclusteredTab -> unclusteredListState
                    currentSport == BSportType.BIKE -> bikeListState
                    currentSport == BSportType.RUN -> runListState
                    currentSport == BSportType.UNKNOWN -> otherListState
                    else -> allListState
                }
                
                if (isUnclusteredTab) {
                    UnclusteredWorkoutsList(
                        workouts = unclusteredWorkouts,
                        viewModel = viewModel,
                        onWorkoutClick = onWorkoutClick,
                        scrollState = listState,
                        appBarOffsetPx = connection.appBarOffset,
                        headerHeightDp = headerHeightDp,
                        density = density,
                        emptyMessage = stringResource(R.string.no_unclustered_workouts)
                    )
                } else {
                    val filteredClusters = if (currentSport == null) {
                        clusters
                    } else {
                        clusters.filter { it.bSportType == currentSport }
                    }

                    WorkoutClustersList(
                        clusters = filteredClusters,
                        viewModel = viewModel,
                        onClusterClick = onClusterClick,
                        onDeleteRequest = onDeleteRequest,
                        onHitCountClick = onHitCountClick,
                        scrollState = listState,
                        appBarOffsetPx = connection.appBarOffset,
                        headerHeightDp = headerHeightDp,
                        density = density,
                        emptyMessage = if (currentSport == null) stringResource(R.string.absolutely_no_clusters_available)
                                       else stringResource(R.string.no_clusters_available, tabs[pageIndex].first)
                    )
                }
            }

            // 2. THE MIGRATION PROGRESS (ATT-361)
            migrationStatus?.let { status ->
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = headerHeightDp + 16.dp) // Below the header
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }

            // 3. THE COLLAPSING HEADER
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.my_locations),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            IconButton(onClick = onTuneClick) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings_24),
                                    contentDescription = stringResource(R.string.cluster_tuning_content_desc),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(text = tab.first) }
                            )
                        }
                    }
                }
            }

            // 3. THE FLOATING ACTION BUTTON
            // Synchronized look & feel (SCRUM-208): Using default M3 colors to match SportTypes/Equipment
            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.text_new))
            }
        }
    }
}
