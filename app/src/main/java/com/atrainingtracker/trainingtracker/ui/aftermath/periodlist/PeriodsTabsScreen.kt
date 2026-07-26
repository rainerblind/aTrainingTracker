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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.util.Log
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
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
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit,
    migrationStatus: MigrationStatus? = null
) {

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val appBarMaxHeightPx = with(density) { 130.dp.roundToPx() }
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.nestedScroll(connection)) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val periods = groupedPeriods[pageIndex]
                val scrollState = listStates[pageIndex]

                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(with(density) { (appBarMaxHeightPx + connection.appBarOffset).toDp() }))

                    // --- ATT-346: Migration Progress Feedback ---
                    if (migrationStatus != null) {
                        Surface(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        progress = { migrationStatus.progress },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = migrationStatus.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { migrationStatus.progress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    PeriodBarGraph(
                        periods = periods,
                        currentScrollState = scrollState,
                        onBarClick = { index -> scope.launch { scrollState.animateScrollToItem(index) } },
                        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 8.dp)
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
                        appBarOffsetPx = 0,
                        headerHeightPx = 0f,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

            // THE HEADER
            Surface(
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.workout_periods__periods),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = onToggleHeatmapEnabled) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = if (isHeatmapEnabled) "Disable Heatmap" else "Enable Heatmap",
                                tint = if (isHeatmapEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = TTAlpha.Disabled)
                            )
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
    val maxDuration = remember(periods) { max(1L, periods.maxOfOrNull { it.totalDurationSec } ?: 1L) }
    val graphPeriods = remember(periods) { periods.reversed() }
    val graphScrollState = rememberLazyListState()
    val barWidth = remember(periods) {
        when (periods.firstOrNull()?.periodType) {
            PeriodType.DAY -> 16.dp
            PeriodType.WEEK -> 24.dp
            PeriodType.MONTH -> 48.dp
            PeriodType.YEAR -> 80.dp
            else -> 16.dp
        }
    }
    val firstVisibleIndex by remember { derivedStateOf { currentScrollState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        val graphIndex = graphPeriods.size - 1 - firstVisibleIndex
        if (graphIndex in graphPeriods.indices) { graphScrollState.animateScrollToItem(graphIndex) }
    }
    LazyRow(
        state = graphScrollState, modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(graphPeriods) { index, period ->
            val originalIndex = periods.size - 1 - index
            val heightFraction = period.totalDurationSec.toFloat() / maxDuration
            val isSelected = originalIndex == firstVisibleIndex
            Column(
                modifier = Modifier.width(barWidth).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .fillMaxHeight(heightFraction.coerceAtLeast(0.1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Disabled))
                        .clickable { onBarClick(originalIndex) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.BottomCenter) {
                    val hours = period.totalDurationSec / 3600
                    if (hours > 0) {
                        Text(
                            text = if (barWidth >= 48.dp) "$hours h" else if (barWidth >= 24.dp) "${hours}h" else hours.toString(),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Medium)
                        )
                    }
                }
            }
        }
    }
}
