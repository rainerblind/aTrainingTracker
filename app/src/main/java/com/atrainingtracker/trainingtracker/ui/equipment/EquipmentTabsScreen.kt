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

package com.atrainingtracker.trainingtracker.ui.equipment


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.components.stats.RichStatsSheet
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsSummaryBlock
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentTabsScreen(
    viewModel: EquipmentViewModel,
    initialTab: Int,
    onNavigateToWorkouts: (StatsData) -> Unit
) {
    val bikes by viewModel.bikes.collectAsState()
    val shoes by viewModel.shoes.collectAsState()

    // State to track which item is being edited or having stats viewed
    var itemToConfigure by remember { mutableStateOf<EquipmentItem?>(null) }
    var itemToDelete by remember { mutableStateOf<EquipmentItem?>(null) }

    // State for the Stats Sheet
    var statsToShow by remember { mutableStateOf<Pair<String, List<StatsData>>?>(null) }

    // State for creating new equipment
    var isAddingNew by remember { mutableStateOf(false) }

    val tabs = listOf(stringResource(R.string.equipment_type_bike), stringResource(R.string.equipment_type_shoe))
    val pagerState = rememberPagerState(
        initialPage = initialTab,
        pageCount = { tabs.size }
    )
    val scope = rememberCoroutineScope()

    // --- Header Animation Logic (Matches SegmentsTabsScreen) ---
    val density = LocalDensity.current
    val appBarMaxHeightPx = with(density) { 130.dp.roundToPx() }
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.nestedScroll(connection)) {

            // 1. THE CONTENT (Pager)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentList = if (page == 0) bikes else shoes

                EquipmentList(
                    items = currentList,
                    emptyMessage = if (page == 0) stringResource(R.string.equipment_no_bikes)
                    else stringResource(R.string.equipment_no_shoes),
                    onConfigClick = { itemToConfigure = it },
                    onStatsClick = { item ->
                        val periods = viewModel.getDetailedStats(item.name, item.id, item.firstUsed)
                        val allStats = listOf(item.statsData) + periods
                        statsToShow = Pair(item.name, allStats)
                    },
                    onDelete = { itemToDelete = it },
                    appBarOffsetPx = connection.appBarOffset,
                    headerHeightPx = appBarMaxHeightPx.toFloat()
                )
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.equipment_management_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) }
                            )
                        }
                    }
                }
            }

            // 3. THE FLOATING ACTION BUTTON
            // Since we aren't using Scaffold, we align it manually to the bottom-right
            FloatingActionButton(
                onClick = { isAddingNew = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding() // Ensure it stays above nav bar
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.text_new))
            }
        }
    }

    // --- Dialog/Sheet Triggers ---

    // ADD NEW EQUIPMENT
    if (isAddingNew) {
        val isBikeTab = pagerState.currentPage == 0
        val availableSensors = if (isBikeTab) viewModel.bikeSensors else viewModel.runSensors
        val availableSportTypes = if (isBikeTab) viewModel.bikeSportTypes else viewModel.runSportTypes

        // Create a blank template
        val newItem = EquipmentItem(
            id = -1, // Database will generate real ID
            name = "",
            frameType = if (isBikeTab) 3 else 0, // Default to Road (3) for bikes, 0 for shoes
            linkedDeviceIds = emptyList(),
            linkedDeviceNames = "",
            linkedSportTypeIds = emptyList(),
            linkedSportTypeNames = "",
            stravaName = null,
            stravaId = null,
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

        EditEquipmentDialog(
            item = newItem,
            availableSensors = availableSensors,
            availableSportTypes = availableSportTypes,
            onDismiss = { isAddingNew = false },
            onConfirm = { addedItem ->
                // Call add instead of update
                viewModel.addEquipment(
                    addedItem.name,
                    addedItem.frameType,
                    addedItem.linkedDeviceIds,
                    addedItem.linkedSportTypeIds)
                isAddingNew = false
            }
        )
    }

    // Show Configuration Dialog if an item is selected
    itemToConfigure?.let { item ->
        // Determine which sensor list to use
        val availableSensors = if (item.frameType > 0) {
            viewModel.bikeSensors
        } else {
            viewModel.runSensors
        }
        val availableSportTypes = if (item.frameType > 0) viewModel.bikeSportTypes else viewModel.runSportTypes

        EditEquipmentDialog(
            item = item,
            availableSensors = availableSensors,
            availableSportTypes = availableSportTypes,
            onDismiss = { itemToConfigure = null },
            onConfirm = { updated ->
                viewModel.updateEquipment(updated)
                itemToConfigure = null
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.really_delete_format, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEquipment(item)
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.Cancel)) }
            }
        )
    }

    // Show Stats Sheet
    statsToShow?.let { (name, data) ->
        RichStatsSheet(
            title = name,
            periodStats = data,
            onDismiss = { statsToShow = null },
            onStatsClick = { onNavigateToWorkouts(it) }
        )
    }
}

@Composable
fun EquipmentList(
    items: List<EquipmentItem>,
    emptyMessage: String,
    onConfigClick: (EquipmentItem) -> Unit,
    onStatsClick: (EquipmentItem) -> Unit,
    onDelete: (EquipmentItem) -> Unit,
    appBarOffsetPx: Int,
    headerHeightPx: Float
) {
    val density = LocalDensity.current
    val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }

    if (items.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding + 16.dp),
            contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.headlineSmall)
        }
    } else {
        val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // Calculation: The initial header height (px) + the current offset (px)
                // convert the final result to Dp.
                top = with(density) { (headerHeightPx + appBarOffsetPx).toDp() + 16.dp },
                bottom = bottomPadding + 16.dp,
                start = 4.dp,
                end = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                EquipmentItem(
                    item = item,
                    onConfigClick = onConfigClick,
                    onStatsClick = onStatsClick,
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EquipmentItem(
    item: EquipmentItem,
    onConfigClick: (EquipmentItem) -> Unit,
    onStatsClick: (EquipmentItem) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    // Using your corrected frameType mapping
    val frameDesc = when (item.frameType) {
        1 -> "MTB"
        2 -> "Cross"
        3 -> "Road"
        4 -> "Time Trial"
        else -> ""
    }

    Box {
        MappableListItem(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            onClick = { onConfigClick(item) },
            onLongClick = { showMenu = true }
        ) {
            // ZONE 1: CONFIGURATION (Top part)
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )

                    if (frameDesc.isNotEmpty()) {
                        Text(
                            text = frameDesc,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Strava Line (with Original Logo)
                if (!item.stravaName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StravaOriginalLogo()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.stravaName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- Linked Sport Types Line ---
                if (item.linkedSportTypeNames.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.equipment_sport_types)} ${item.linkedSportTypeNames}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sensors Line
                if (item.linkedDeviceNames.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${stringResource(R.string.SensorTypes)} ${item.linkedDeviceNames}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // ZONE 2: STATS (Bottom part)
            if (item.statsData.totalWorkouts > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStatsClick(item) }
                        .padding(horizontal = 16.dp)
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
                                    label = stringResource(R.string.stats_first_use),
                                    date = it
                                )
                            }
                            item.lastUsed?.let {
                                UsageItem(
                                    label = stringResource(R.string.stats_last_use),
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

        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            expanded = showMenu,
            onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
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

@Composable
fun StravaOriginalLogo() {
    // Increased height to 18.dp to match the increased bodySmall/bodyMedium text scale
    Image(
        painter = painterResource(id = R.drawable.logo_square_strava),
        contentDescription = "Strava",
        modifier = Modifier.height(12.dp),
        contentScale = ContentScale.Fit
    )
}

// Helper to create mock stats for previews
private fun mockStats(
    title: String,
    dist: String,
    workouts: Int,
    timeWithUnits: String,
    ascent: String
) = StatsData(
    primaryTitle = "Foo",
    secondaryTitle = title,
    totalWorkouts = workouts,
    totalDistanceWithUnits = dist,
    timeWithUnits = timeWithUnits,
    totalAscentWithUnits = ascent
)

@Preview(showBackground = true, name = "Equipment Card - Linked Strava")
@Composable
fun PreviewEquipmentCardLinked() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EquipmentItem(
                item = EquipmentItem(
                    id = 1,
                    name = "Specialized Epic MTB",
                    linkedDeviceIds = listOf(1, 2),
                    linkedDeviceNames = "Garmin HRM, Wahoo Speed",
                    linkedSportTypeIds = listOf(1, 2),
                    linkedSportTypeNames = "Ride",
                    frameType = 1, // MTB
                    stravaName = "My Epic",
                    stravaId = "b12345",
                    firstUsed = "2024-10-11",
                    lastUsed = "2024-10-12",
                    statsData = mockStats(
                        title = "All Time Stats",
                        dist = "1250 km",
                        workouts = 42,
                        timeWithUnits = "42:34:00",
                        ascent = "15400 m"
                    )
                ),
                onConfigClick = { },
                onStatsClick = { },
                onDelete = { }
            )
        }
    }
}

@Preview(showBackground = true, name = "Equipment Card - Simple Shoe")
@Composable
fun PreviewEquipmentCardSimple() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EquipmentItem(
                item = EquipmentItem(
                    id = 2,
                    name = "Asics Gel-Nimbus",
                    linkedDeviceIds = listOf(1, 2),
                    linkedDeviceNames = "",
                    linkedSportTypeIds = listOf(1, 2),
                    linkedSportTypeNames = "",
                    frameType = 0,
                    stravaName = null,
                    stravaId = null,
                    firstUsed = "2024-10-11",
                    lastUsed = "2024-10-12",
                    statsData = mockStats(
                        title = "All Time Stats",
                        dist = "450 km",
                        workouts = 38,
                        timeWithUnits = "12:34:00",
                        ascent = "1200 m"
                    )
                ),
                onConfigClick = { },
                onStatsClick = { },
                onDelete = { }
            )
        }
    }
}

@Preview(showBackground = true, name = "Equipment Card - Empty")
@Composable
fun PreviewEquipmentCardEmpty() {
    ATrainingTrackerTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EquipmentItem(
                item = EquipmentItem(
                    id = 3,
                    name = "New Road Bike",
                    linkedDeviceIds = listOf(3),
                    linkedDeviceNames = "Cycplus Speed",
                    linkedSportTypeIds = listOf(1, 2),
                    linkedSportTypeNames = "Ride",
                    frameType = 3, // Road
                    stravaName = null,
                    stravaId = null,
                    firstUsed = null,
                    lastUsed = null,
                    statsData = mockStats(
                        title = "All Time Stats",
                        dist = "0 km",
                        workouts = 0,
                        timeWithUnits = "00:00:00",
                        ascent = "0 m"
                    )
                ),
                onConfigClick = { },
                onStatsClick = { },
                onDelete = { }
            )
        }
    }
}