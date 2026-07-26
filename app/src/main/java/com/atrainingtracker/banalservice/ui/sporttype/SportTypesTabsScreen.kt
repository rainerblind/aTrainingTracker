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

package com.atrainingtracker.banalservice.ui.sporttype

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder
import com.atrainingtracker.trainingtracker.ui.components.DeleteConfirmationDialog
import com.atrainingtracker.trainingtracker.ui.components.stats.RichStatsSheet
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsSummaryBlock
import com.atrainingtracker.trainingtracker.ui.theme.LayoutConstants
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportTypesTabsScreen(
    viewModel: SportTypeViewModel,
    onNavigateToWorkouts: (StatsData) -> Unit
) {
    val sportTypes by viewModel.sportTypes.collectAsStateWithLifecycle()

    // Define our tabs mapping to BSportType
    val tabs = listOf(
        stringResource(R.string.sport_type_tab_all) to null,
        stringResource(R.string.sport_type_tab_bike) to BSportType.BIKE,
        stringResource(R.string.sport_type_tab_run) to BSportType.RUN,
        stringResource(R.string.sport_type_tab_unknown) to BSportType.UNKNOWN
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    var itemToEdit by remember { mutableStateOf<SportTypeItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SportTypeItem?>(null) }

    var statsToShow by remember { mutableStateOf<Pair<String, List<StatsData>>?>(null) }

    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val appBarMaxHeightPx = with(density) { (statusBarHeight + LayoutConstants.COMPACT_HEADER_CONTENT_HEIGHT).roundToPx() }
    
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.nestedScroll(connection)) {

            // 1. THE CONTENT (Pager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val targetBSportType = tabs[pageIndex].second

                // Filter the list based on the tab's BSportType
                val filteredList = if (targetBSportType == null) {
                    sportTypes
                } else {
                    sportTypes.filter { it.bSportType == targetBSportType }
                }

                val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val topPadding = with(density) { (appBarMaxHeightPx.toFloat() + connection.appBarOffset).toDp() + 16.dp }

                if (filteredList.isEmpty()) {
                    EmptyStatePlaceholder(
                        modifier = Modifier.padding(top = topPadding),
                        message = stringResource(R.string.no_sport_types_available)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),

                        contentPadding = PaddingValues(
                            // Calculation: The initial header height (px) + the current offset (px)
                            // convert the final result to Dp.
                            top = topPadding,
                            bottom = bottomPadding + 16.dp,
                            start = 4.dp,
                            end = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Matching Equipment spacing
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            SportTypeCard(
                                item = item,
                                onConfigClick = { itemToEdit = item },
                                onStatsClick = { item ->
                                    // 1. Fetch detailed periods from ViewModel
                                    val periods = viewModel.getDetailedStats(item.name, item.id, item.firstUsed)
                                    // 2. Combine with the "Total" stats already in the item
                                    val allStats = listOf(item.statsData) + periods
                                    // 3. Show the sheet
                                    statsToShow = Pair(item.name, allStats)
                                },
                                onDelete = { itemToDelete = item }
                            )
                        }
                    }
                }
            }

            // 2. THE COLLAPSING HEADER (Matches Segments Style)
            Surface(
                modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstants.HEADER_TITLE_ROW_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.sport_types),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = { Text(text = tab.first) }
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding(),

                onClick = {
                    // Get the BSportType associated with the currently visible tab
                    val currentTabType = tabs[pagerState.currentPage].second

                    // If on the "All" tab (null), default to BIKE
                    // otherwise use the specific tab's type.
                    val initialBSportType = currentTabType ?: BSportType.BIKE

                    // Create blank template for new item
                    itemToEdit = SportTypeItem(
                        id = -1,
                        name = "",
                        bSportType = initialBSportType,
                        minSpeed = when (initialBSportType) {
                            BSportType.BIKE -> TrainingApplication.getMaxRunSpeed_mps()
                            BSportType.RUN -> TrainingApplication.getMaxWalkSpeed_mps()
                            else -> 0.0
                        },
                        maxSpeed = when (initialBSportType) {
                            BSportType.BIKE -> TrainingApplication.getMaxBikeSpeed_mps()
                            BSportType.RUN -> TrainingApplication.getMaxRunSpeed_mps()
                            else -> TrainingApplication.getMaxWalkSpeed_mps()
                        },
                        stravaName = when (initialBSportType) {
                            BSportType.BIKE -> "Ride"
                            BSportType.RUN -> "Run"
                            else -> "Workout"
                        },
                        tcxName = when (initialBSportType) {
                            BSportType.BIKE -> "Biking"
                            BSportType.RUN -> "Running"
                            else -> "Other"
                        },
                        gcName = when (initialBSportType) {
                            BSportType.BIKE -> "bike"
                            BSportType.RUN -> "run"
                            else -> "walk"
                        },
                        linkedEquipmentIds = emptyList(),
                        linkedEquipmentNames = "",
                        isEditable = true,
                        firstUsed = null,
                        lastUsed = null,
                        statsData = StatsData(
                            primaryTitle = "",
                            secondaryTitle = "",
                            totalWorkouts = 0,
                            totalDistanceWithUnits = "0",
                            timeWithUnits = "0",
                            totalAscentWithUnits = "0"
                        )
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.text_new))
            }


        }
    }

    // --- Rich Stats Sheet ---
    statsToShow?.let { (name, data) ->
        RichStatsSheet(
            title = name,
            periodStats = data,
            onDismiss = { statsToShow = null },
            onStatsClick = { stats ->
                // Trigger navigation via Activity or ViewModel event
                onNavigateToWorkouts(stats)
            }
        )
    }

    // Edit Dialog
    itemToEdit?.let { item ->
        EditSportTypeDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedItem ->
                viewModel.saveSportType(updatedItem)
                itemToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete),
            message = stringResource(R.string.really_delete_format, item.name),
            onConfirm = { viewModel.deleteSportType(item.id) },
            onDismiss = { itemToDelete = null }
        )
    }
}

@Composable
fun SportTypeCard(
    item: SportTypeItem,
    onConfigClick: (SportTypeItem) -> Unit,
    onStatsClick: (SportTypeItem) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val speedUnit = stringResource(MyHelper.getSpeedUnitNameId())

    var showMenu by remember { mutableStateOf(false) }

    Box {
        MappableListItem(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            onClick = { onConfigClick(item) },
            onLongClick = {
                if (item.isEditable) {
                    showMenu = true
                }
            }
        ) {
            // Upper part: The equipment itself
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
            ) {
                // HEADER ZONE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val icon = remember(item.bSportType) {
                        androidx.core.content.ContextCompat.getDrawable(context, item.bSportType.iconResId)
                    }

                    icon?.let {
                        Image(
                            painter = rememberDrawablePainter(it),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = stringResource(
                                R.string.average_speed_range_format,
                                MyHelper.mps2userUnit(item.minSpeed),
                                MyHelper.mps2userUnit(item.maxSpeed),
                                speedUnit
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!item.isEditable) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (item.linkedEquipmentNames.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val equipmentIcon = when (item.bSportType) {
                            BSportType.BIKE -> painterResource(R.drawable.ic_equipment_bike)
                            BSportType.RUN -> painterResource(R.drawable.ic_equipment_shoe)
                            else -> null
                        }

                        if (equipmentIcon != null) {
                            Icon(
                                painter = equipmentIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.linkedEquipmentNames,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MAPPING ZONE
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Strava Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.logo_square_strava),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.mapping_format_strava, item.stravaName ?: stringResource(R.string.no_upload)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // File Formats Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.sport_type_file_mapping_format,
                                stringResource(R.string.mapping_format_tcx, item.tcxName),
                                stringResource(R.string.mapping_format_gc, item.gcName)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- THE STATS BLOCK
            if (item.statsData.totalWorkouts > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStatsClick(item) }
                        .padding(horizontal = 12.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Usage Timeline Row (First/Last Activity)
                    if (!item.firstUsed.isNullOrBlank() || !item.lastUsed.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            item.firstUsed?.let {
                                UsageItem(
                                    label = stringResource(R.string.stats_first_activity),
                                    date = it
                                )
                            }
                            item.lastUsed?.let {
                                UsageItem(
                                    label = stringResource(R.string.stats_last_activity),
                                    date = it,
                                    alignEnd = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    StatsSummaryBlock(
                        stats = item.statsData,
                        onStatsClick = { onStatsClick(item) }
                        )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Context Menu for deletion (Pinned to Top-Start to cover the header area)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.delete)
                        )
                    },
                    enabled = item.isEditable,
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun UsageItem(label: String, date: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        // Value on Top
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium, // Match importance of stats
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Label Below
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}


