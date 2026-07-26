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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch
import kotlin.collections.sort

@Composable
fun RouteTabbedScreen(
    routesWithPath: List<RouteWithPath>,
    pagerState: PagerState,
    allSportsListState: LazyListState,
    bikeListState: LazyListState,
    runListState: LazyListState,
    otherListState: LazyListState,
    onMapClick: (Long) -> Unit,
    onHeaderClick: (Long) -> Unit,
    onToggleSelection: (Long, Boolean) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    onImportClick: () -> Unit,
    onSyncStravaClick: () -> Unit,
    isSyncing: Boolean,
    sortOrder: RouteSortOrder,
    onSortOrderChange: (RouteSortOrder) -> Unit,
    scrollToTop: Boolean,
    isLocationAvailable: Boolean,
) {
    // Define our tabs mapping to BSportType
    val tabs = listOf(
        stringResource(R.string.sport_type_tab_all) to null,
        stringResource(R.string.sport_type_tab_bike) to BSportType.BIKE,
        stringResource(R.string.sport_type_tab_run) to BSportType.RUN,
        stringResource(R.string.sport_type_tab_unknown) to BSportType.UNKNOWN
    )

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val appBarMaxHeightPx = with(density) { (statusBarHeight + LayoutConstants.COMPACT_HEADER_CONTENT_HEIGHT).roundToPx() }

    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    LaunchedEffect(scrollToTop, sortOrder) {
        if (scrollToTop) {
            allSportsListState.scrollToItem(0)
            bikeListState.scrollToItem(0)
            runListState.scrollToItem(0)
            otherListState.scrollToItem(0)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.nestedScroll(connection)) {

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
                    else -> allSportsListState
                }
                val filteredRoutesWithPath = if (currentSport == null) {
                    routesWithPath
                }
                else {
                    routesWithPath.filter { it.summary.bSportType == currentSport }
                }

                RouteList(
                    routes = filteredRoutesWithPath,
                    bSportType = currentSport,
                    scrollState = listState,
                    onMapClick = onMapClick,
                    onHeaderClick = onHeaderClick,
                    onToggleSelection = onToggleSelection,
                    onDeleteConfirmed = onDeleteConfirmed,
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightPx = appBarMaxHeightPx.toFloat(),
                )
            }

            // Identify which routes are in the current tab
            val currentTabSport = tabs[pagerState.currentPage].second
            val routesInCurrentTab = if (currentTabSport == null) {
                routesWithPath
            } else {
                routesWithPath.filter { it.summary.bSportType == currentTabSport }
            }

            // Check if all visible routes are currently selected
            val isAllSelected = routesInCurrentTab.isNotEmpty() &&
                    routesInCurrentTab.all { it.summary.isSelected }

            // --- HEADER (Same as WorkoutTabsScreen) ---
            Surface(
                modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
                // tonalElevation = 3.dp
            ) {
                Column {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        // Title Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.routes),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // --- SELECT ALL SWITCH ---
                                if (routesInCurrentTab.isNotEmpty()) {
                                    Switch(
                                        modifier = Modifier
                                            .scale(0.75f) // Make it smaller
                                            .padding(end = 8.dp),
                                        checked = isAllSelected,
                                        onCheckedChange = { checked ->
                                            routesInCurrentTab.forEach {
                                                onToggleSelection(it.summary.id, checked)
                                            }
                                        },
                                        thumbContent = if (isAllSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(2.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }

                                // --- IMPORT DROPDOWN ---
                                var showImportMenu by remember { mutableStateOf(false) }

                                Box {
                                    IconButton(onClick = { showImportMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = stringResource(R.string.route_import),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showImportMenu,
                                        onDismissRequest = { showImportMenu = false },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        // GPX Import
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.GPX)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            },
                                            onClick = {
                                                showImportMenu = false
                                                onImportClick()
                                            }
                                        )

                                        // Strava Sync
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.Strava)) },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.logo_square_strava),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = Color.Unspecified
                                                )
                                            },
                                            enabled = !isSyncing,
                                            onClick = {
                                                showImportMenu = false
                                                onSyncStravaClick()
                                            }
                                        )
                                    }
                                }

                                // --- SYNC PROGRESS (shown separately if active) ---
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                // --- SORT BUTTON ---
                                var showSortMenu by remember { mutableStateOf(false) }

                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = stringResource(R.string.sort),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    DropdownMenu(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        RouteSortOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(text = stringResource(order.labelResId),
                                                        color = if (order == RouteSortOrder.DISTANCE_TO_USER && !isLocationAvailable) {
                                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                        }
                                                        else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    onSortOrderChange(order)
                                                    showSortMenu = false
                                                },
                                                leadingIcon = {
                                                    if (sortOrder == order) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = if (order == RouteSortOrder.DISTANCE_TO_USER && !isLocationAvailable) {
                                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                            }
                                                            else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )
                                                    }
                                                },
                                                enabled = !(order == RouteSortOrder.DISTANCE_TO_USER && !isLocationAvailable)
                                            )
                                        }
                                    }
                                }

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
        }
    }
}