package com.atrainingtracker.banalservice.ui.sporttype

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.ui.components.stats.RichStatsSheet
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsSummaryBlock

@Composable
fun SportTypeScreen(
    viewModel: SportTypeViewModel
) {
    val sportTypes by viewModel.sportTypes.collectAsStateWithLifecycle()
    var itemToEdit by remember { mutableStateOf<SportTypeItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SportTypeItem?>(null) }

    var statsToShow by remember { mutableStateOf<Pair<String, List<StatsData>>?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Create blank template for new item
                itemToEdit = SportTypeItem(
                    id = -1,
                    name = "",
                    bSportType = BSportType.BIKE,
                    minSpeed = 4.2,
                    maxSpeed = 10.0,
                    stravaName = "Ride",
                    tcxName = "Biking",
                    gcName = "bike",
                    linkedEquipmentIds = emptyList(),
                    linkedEquipmentNames = "",
                    isEditable = true,
                    firstUsed = null,
                    lastUsed = null,
                    statsData = StatsData(
                        title = "",
                        totalWorkouts = 0,
                        totalDistanceWithUnits = "0",
                        timeWithUnits = "0",
                        totalAscentWithUnits = "0"
                    )
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.text_new))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp) // Matching Equipment spacing
        ) {
            items(sportTypes, key = { it.id }) { item ->
                SportTypeCard(
                    item = item,
                    onConfigClick = { itemToEdit = item },
                    onStatsClick = { item ->
                        // 1. Fetch detailed periods from ViewModel
                        val periods = viewModel.getDetailedStats(item.id, item.firstUsed)
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

    // --- Rich Stats Sheet ---
    statsToShow?.let { (name, data) ->
        RichStatsSheet(
            title = name,
            periodStats = data,
            onDismiss = { statsToShow = null }
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
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.really_delete_workout_name_scheme, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSportType(item.id)
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.Cancel)) }
            }
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
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Upper part: The equipment itself
            Column(modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onConfigClick(item) },
                    onLongClick = { showMenu = true }
                )
                .padding(16.dp)
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
                            text = stringResource(R.string.mapping_format_strava, item.stravaName),
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

                    if (item.linkedEquipmentNames.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                }
            }

            // --- THE STATS BLOCK
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
                        stats = item.statsData
                        )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // The Menu itself (anchored to the Card via the Box)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.delete),
                        color = if (item.isEditable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
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
                        contentDescription = null,
                        tint = if (item.isEditable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                }
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


