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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch
import kotlin.math.max


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodsTabsScreen(
    groupedPeriods: List<List<PeriodSummary>>,
    tabs: List<String>,
    pagerState: PagerState,
    listStates: List<LazyListState>,
    isPlayServiceAvailable: Boolean,
    isHeatmapEnabled: Boolean,
    onToggleHeatmapEnabled: () -> Unit,
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit
) {

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 1. Calculate the total height of the Header (Status Bar + Heading + Tab Row)
    // Reverted to original height since graph is moving out of collapsing area
    val appBarMaxHeightPx = with(density) { 130.dp.roundToPx() }

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
                val periods = groupedPeriods[pageIndex]
                val scrollState = listStates[pageIndex]

                Column(modifier = Modifier.fillMaxSize()) {
                    // Spacer for the collapsing header
                    Spacer(modifier = Modifier.height(with(density) { (appBarMaxHeightPx + connection.appBarOffset).toDp() }))

                    // THE GRAPH (Static below tabs)
                    PeriodBarGraph(
                        periods = periods,
                        currentScrollState = scrollState,
                        onBarClick = { index ->
                            scope.launch {
                                scrollState.animateScrollToItem(index)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    PeriodList(
                        periods = periods,
                        scrollState = scrollState,
                        isPlayServiceAvailable = isPlayServiceAvailable,
                        isHeatmapEnabled = isHeatmapEnabled,
                        onHeaderClick = onHeaderClick,
                        onMapClick = onMapClick,
                        onSportClick = onSportClick,
                        onLongestWorkoutClick = onLongestWorkoutClick,
                        // PeriodList now only needs to handle the rest of the offset
                        appBarOffsetPx = 0,
                        headerHeightPx = 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            // THE HEADER (Layered on top)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
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
                            text = stringResource(R.string.workout_periods__periods),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onToggleHeatmapEnabled) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = if (isHeatmapEnabled) "Disable Heatmap" else "Enable Heatmap",
                                    tint = if (isHeatmapEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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

@Composable
fun PeriodBarGraph(
    periods: List<PeriodSummary>,
    currentScrollState: LazyListState,
    onBarClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (periods.isEmpty()) return

    val maxDuration = remember(periods) {
        max(1L, periods.maxOfOrNull { it.totalDurationSec } ?: 1L)
    }

    val graphPeriods = remember(periods) { periods.reversed() }
    val graphScrollState = rememberLazyListState()
    
    // Determine bar width based on PeriodType (using the first period as reference)
    val barWidth = remember(periods) {
        when (periods.firstOrNull()?.periodType) {
            PeriodType.DAY -> 16.dp
            PeriodType.WEEK -> 24.dp
            PeriodType.MONTH -> 48.dp
            PeriodType.YEAR -> 80.dp
            else -> 16.dp
        }
    }

    // Identify which period is currently most visible in the list to highlight its bar
    val firstVisibleIndex by remember {
        derivedStateOf { currentScrollState.firstVisibleItemIndex }
    }

    // Synchronize graph scroll with list scroll
    LaunchedEffect(firstVisibleIndex) {
        val graphIndex = graphPeriods.size - 1 - firstVisibleIndex
        if (graphIndex in graphPeriods.indices) {
            graphScrollState.animateScrollToItem(graphIndex)
        }
    }

    LazyRow(
        state = graphScrollState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(graphPeriods) { index, period ->
            val originalIndex = periods.size - 1 - index
            val heightFraction = period.totalDurationSec.toFloat() / maxDuration
            val isSelected = originalIndex == firstVisibleIndex

            Column(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // The Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight(heightFraction.coerceAtLeast(0.1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clickable { onBarClick(originalIndex) }
                )

                // The Number below the bar container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val hours = period.totalDurationSec / 3600
                    if (hours > 0) {
                        Text(
                            text = if (barWidth >= 48.dp) "$hours h" else if (barWidth >= 24.dp) "${hours}h" else hours.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
