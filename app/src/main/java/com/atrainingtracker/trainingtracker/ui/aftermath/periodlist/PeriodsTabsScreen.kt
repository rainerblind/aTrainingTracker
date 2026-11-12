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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
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
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit,
    migrationStatus: MigrationStatus? = null,
    selectedSports: Set<BSportType> = setOf(BSportType.BIKE, BSportType.RUN),
    onToggleSportFilter: (BSportType) -> Unit = {},
    selectedMetric: PeriodBarMetric = PeriodBarMetric.DURATION,
    onSelectMetric: (PeriodBarMetric) -> Unit = {},
    onDayCellClick: ((PeriodSummary, BSportType, java.time.LocalDate, DailySportActivity?) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    PeriodsTabsScreen(
        groupedPeriods = groupedPeriods,
        tabs = tabs,
        pagerState = pagerState,
        listStates = listStates,
        isPlayServiceAvailable = isPlayServiceAvailable,
        onHeaderClick = onHeaderClick,
        onMapClick = { s, _ -> onMapClick(s) },
        onSportClick = onSportClick,
        onLongestWorkoutClick = onLongestWorkoutClick,
        migrationStatus = migrationStatus,
        selectedSports = selectedSports,
        onToggleSportFilter = onToggleSportFilter,
        selectedMetric = selectedMetric,
        onSelectMetric = onSelectMetric,
        onDayCellClick = onDayCellClick,
        onMenuClick = onMenuClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodsTabsScreen(
    groupedPeriods: List<List<PeriodSummary>>,
    tabs: List<String>,
    pagerState: PagerState,
    listStates: List<LazyListState>,
    isPlayServiceAvailable: Boolean,
    onHeaderClick: (PeriodSummary) -> Unit,
    onMapClick: (PeriodSummary, String?) -> Unit,
    onSportClick: (PeriodSummary, BSportType) -> Unit,
    onLongestWorkoutClick: (PeriodSummary, BSportType, Long) -> Unit,
    migrationStatus: MigrationStatus? = null,
    selectedSports: Set<BSportType> = setOf(BSportType.BIKE, BSportType.RUN),
    onToggleSportFilter: (BSportType) -> Unit = {},
    selectedMetric: PeriodBarMetric = PeriodBarMetric.DURATION,
    onSelectMetric: (PeriodBarMetric) -> Unit = {},
    onDayCellClick: ((PeriodSummary, BSportType, java.time.LocalDate, DailySportActivity?) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val appBarMaxHeightPx = with(density) { (statusBarHeight + LayoutConstants.COMPACT_HEADER_CONTENT_HEIGHT).roundToPx() }

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
                val periods = if (pageIndex < groupedPeriods.size) groupedPeriods[pageIndex] else emptyList()
                val scrollState = listStates[pageIndex]

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = with(density) { (appBarMaxHeightPx + connection.appBarOffset).toDp() })
                    ) {
                        // --- ATT-346: Migration Progress Feedback ---
                        if (migrationStatus != null) {
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                tonalElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = migrationStatus.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    migrationStatus.phases.forEachIndexed { index, phase ->
                                        if (index > 0) Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    progress = { phase.progress },
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = stringResource(R.string.migration_phase_label, phase.id, phase.message),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (phase.progress < 1.0f) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { phase.progress },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                color = if (phase.progress >= 1.0f) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }


                        PeriodBarGraph(
                            periods = periods,
                            currentScrollState = scrollState,
                            onBarClick = { index -> scope.launch { scrollState.animateScrollToItem(index) } },
                            selectedMetric = selectedMetric,
                            onMetricSelect = onSelectMetric,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        PeriodList(
                            periods = periods,
                            scrollState = scrollState,
                            isPlayServiceAvailable = isPlayServiceAvailable,
                            onHeaderClick = onHeaderClick,
                            onMapClick = onMapClick,
                            onSportClick = onSportClick,
                            onLongestWorkoutClick = onLongestWorkoutClick,
                            activeMetric = selectedMetric,
                            onDayCellClick = onDayCellClick,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                }
            }

            // THE HEADER
            Surface(
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                            .padding(start = 4.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (onMenuClick != null) {
                                IconButton(onClick = onMenuClick) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.navigation_drawer_open),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.workout_periods__periods),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val canonicalSports = listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN)
                            canonicalSports.forEach { sport ->
                                val isSelected = selectedSports.contains(sport)
                                val tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = TTAlpha.Disabled)
                                }
                                IconButton(
                                    onClick = { onToggleSportFilter(sport) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = sport.iconResId),
                                        contentDescription = stringResource(id = sport.stringResId),
                                        tint = tint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
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

