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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentsTabsScreen(
    liveSegments: List<LiveSegment>,
    pagerState: PagerState,
    bikeListState: LazyListState,
    runListState: LazyListState,
    isRefreshing: (BSportType) -> Boolean,
    onRefresh: (BSportType) -> Unit,
    onSegmentClick: (Long) -> Unit,
    sortOrder: SegmentSortOrder,
    scrollToTop: Boolean,
    onSortOrderChange: (SegmentSortOrder) -> Unit
) {
    val tabs = listOf(
        Pair(stringResource(R.string.workout_summaries_tab_bike), BSportType.BIKE),
        Pair(stringResource(R.string.workout_summaries_tab_run), BSportType.RUN)
    )

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val appBarMaxHeightPx = with(density) { 135.dp.roundToPx() }
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    Log.i("SegmentTabsScreen", "scrollToTop=$scrollToTop")
    // Reset scroll position to top when sort order changes
    LaunchedEffect(scrollToTop, sortOrder) {
        if (scrollToTop) {
            bikeListState.scrollToItem(0)
            runListState.scrollToItem(0)
        }
    }

    var showSortMenu by remember { mutableStateOf(false) } // Track sort order menu visibility

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.nestedScroll(connection)) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val currentSport = tabs[pageIndex].second
                val listState = if (currentSport == BSportType.BIKE) bikeListState else runListState
                val filteredLiveSegments = liveSegments.filter { it.summary.bSportType == currentSport }

                SegmentList(
                    liveSegments = filteredLiveSegments,
                    scrollState = listState,
                    isRefreshing = isRefreshing(currentSport),
                    onRefresh = { onRefresh(currentSport) },
                    onSegmentClick = onSegmentClick,
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightPx = appBarMaxHeightPx.toFloat()
                )
            }

            // --- HEADER (Same as WorkoutTabsScreen) ---
            Surface(
                modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
                // tonalElevation = 3.dp
            ) {
                Column {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        // Title Row with Sort Icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.segments),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    SegmentSortOrder.entries.forEach { order ->
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
                    }
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
        }
    }
}