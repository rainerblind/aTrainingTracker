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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequentPathsTabsScreen(
    viewModel: FrequentPathsViewModel,
    pagerState: PagerState,
    allListState: LazyListState,
    bikeListState: LazyListState,
    runListState: LazyListState,
    otherListState: LazyListState,
    onClusterClick: (RouteCluster) -> Unit,
    onTuneClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val clusters by viewModel.allClusters.collectAsState()
    
    val tabs = listOf(
        stringResource(R.string.sport_type_tab_all) to null,
        stringResource(R.string.sport_type_tab_bike) to BSportType.BIKE,
        stringResource(R.string.sport_type_tab_run) to BSportType.RUN,
        stringResource(R.string.sport_type_tab_unknown) to BSportType.UNKNOWN
    )

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
                val currentSport = tabs[pageIndex].second
                val listState = when (currentSport) {
                    BSportType.BIKE -> bikeListState
                    BSportType.RUN -> runListState
                    BSportType.UNKNOWN -> otherListState
                    else -> allListState
                }
                
                val filteredClusters = if (currentSport == null) {
                    clusters
                } else {
                    clusters.filter { it.bSportType == currentSport }
                }

                FrequentPathsList(
                    clusters = filteredClusters,
                    viewModel = viewModel,
                    onClusterClick = onClusterClick,
                    scrollState = listState,
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightDp = headerHeightDp,
                    density = density
                )
            }

            // 2. THE COLLAPSING HEADER
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
                                    contentDescription = "Tune Clustering",
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
