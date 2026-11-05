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

package com.atrainingtracker.trainingtracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.ZoneSnapshotEntry
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import java.text.DateFormat
import java.util.Date

/**
 * Dialog displaying timestamped historical zone snapshots for a given [zoneType], allowing the
 * athlete to view history, add backdated snapshots, edit cutoffs, and delete entries (REQ-SET-082.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneHistoryDialog(
    zoneType: SettingsDataStore.ZoneType,
    history: List<ZoneSnapshotEntry>,
    onDismiss: () -> Unit,
    onSaveSnapshot: (Long, List<Int>) -> Unit,
    onUpdateSnapshot: (Long, Long, List<Int>) -> Unit,
    onDeleteSnapshot: (Long) -> Unit
) {
    var editingEntry by remember { mutableStateOf<ZoneSnapshotEntry?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<ZoneSnapshotEntry?>(null) }

    val zoneTypeName = when (zoneType) {
        SettingsDataStore.ZoneType.HR_RUN -> stringResource(R.string.tab_hr_run)
        SettingsDataStore.ZoneType.HR_BIKE -> stringResource(R.string.tab_hr_bike)
        SettingsDataStore.ZoneType.PWR_BIKE -> stringResource(R.string.tab_pwr_bike)
    }

    AppModalBottomSheet(
        title = "${stringResource(R.string.zone_history_title)}: $zoneTypeName",
        onDismissRequest = onDismiss,
        icon = Icons.Default.CalendarToday,
        scrollable = false,
        actions = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        Button(
            onClick = { isAddingNew = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.zone_history_add_snapshot))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.zone_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { snapshot ->
                    ZoneSnapshotCard(
                        snapshot = snapshot,
                        onEdit = { editingEntry = snapshot },
                        onDelete = { entryToDelete = snapshot }
                    )
                }
            }
        }
    }

    if (isAddingNew) {
        val initialBoundaries = history.firstOrNull()?.boundaries
            ?: (1..zoneType.boundaryCount).map { 100 + it * 20 }
        ZoneSnapshotEditDialog(
            zoneType = zoneType,
            initialTimestampMs = System.currentTimeMillis(),
            initialBoundaries = initialBoundaries,
            isNew = true,
            onDismiss = { isAddingNew = false },
            onConfirm = { timestampMs, boundaries ->
                onSaveSnapshot(timestampMs, boundaries)
                isAddingNew = false
            }
        )
    }

    editingEntry?.let { entry ->
        ZoneSnapshotEditDialog(
            zoneType = zoneType,
            initialTimestampMs = entry.timestampMs,
            initialBoundaries = entry.boundaries,
            isNew = false,
            onDismiss = { editingEntry = null },
            onConfirm = { timestampMs, boundaries ->
                onUpdateSnapshot(entry.id, timestampMs, boundaries)
                editingEntry = null
            }
        )
    }

    entryToDelete?.let { entry ->
        val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.timestampMs))
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.zone_history_delete_title)) },
            text = { Text(stringResource(R.string.zone_history_delete_message, dateStr)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSnapshot(entry.id)
                        entryToDelete = null
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ZoneSnapshotCard(
    snapshot: ZoneSnapshotEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(snapshot.timestampMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snapshot.timestampMs))
    }
    val unit = if (snapshot.zoneType == SettingsDataStore.ZoneType.PWR_BIKE) "W" else "bpm"

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val boundsSummary = snapshot.boundaries.mapIndexed { idx, v -> "Z${idx + 1}: $v" }.joinToString(" | ") + " $unit"
                Text(
                    text = boundsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.zone_history_edit_snapshot),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneSnapshotEditDialog(
    zoneType: SettingsDataStore.ZoneType,
    initialTimestampMs: Long,
    initialBoundaries: List<Int>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, List<Int>) -> Unit
) {
    var selectedTimestampMs by remember { mutableLongStateOf(initialTimestampMs) }
    var showDatePicker by remember { mutableStateOf(false) }

    val boundaryInputs = remember {
        mutableStateListOf<String>().apply {
            addAll(initialBoundaries.map { it.toString() })
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val zoneLabels = listOf(
        R.string.zone_1_label,
        R.string.zone_2_label,
        R.string.zone_3_label,
        R.string.zone_4_label,
        R.string.zone_5_label,
        R.string.zone_6_label
    )

    val dateFormatted = remember(selectedTimestampMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(selectedTimestampMs))
    }

    AppModalBottomSheet(
        title = stringResource(
            if (isNew) R.string.zone_history_add_snapshot else R.string.zone_history_edit_snapshot
        ),
        onDismissRequest = onDismiss,
        icon = Icons.Default.Edit,
        scrollable = true,
        actions = {
            val errorMonotonicTemplate = stringResource(R.string.zone_history_error_monotonic)
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = {
                    val parsed = boundaryInputs.mapNotNull { it.toIntOrNull() }
                    if (parsed.size != zoneType.boundaryCount) {
                        errorMessage = "Please enter all ${zoneType.boundaryCount} thresholds"
                        return@Button
                    }
                    var monotonic = parsed.first() > 0
                    for (i in 0 until parsed.size - 1) {
                        if (parsed[i] >= parsed[i + 1]) {
                            monotonic = false
                            break
                        }
                    }
                    if (!monotonic) {
                        errorMessage = String.format(errorMonotonicTemplate, parsed.joinToString(" < "))
                        return@Button
                    }
                    onConfirm(selectedTimestampMs, parsed)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.zone_history_save_snapshot))
            }
        }
    ) {
        // Date Selector
        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.zone_history_date_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Threshold Inputs
        boundaryInputs.forEachIndexed { index, valueStr ->
            val labelRes = zoneLabels.getOrElse(index) { R.string.zone_1_label }
            OutlinedTextField(
                value = valueStr,
                onValueChange = { newVal ->
                    boundaryInputs[index] = newVal.filter { it.isDigit() }
                    errorMessage = null
                },
                label = { Text(stringResource(labelRes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                singleLine = true
            )
        }

        errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedTimestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedTimestampMs = it }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.OK))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Preview(name = "Zone History Dialog Light", showBackground = true)
@Composable
private fun ZoneHistoryDialogLightPreview() {
    ATrainingTrackerTheme {
        ZoneHistoryDialog(
            zoneType = SettingsDataStore.ZoneType.HR_RUN,
            history = listOf(
                ZoneSnapshotEntry(
                    id = 1L,
                    timestampMs = 1735689600000L,
                    zoneType = SettingsDataStore.ZoneType.HR_RUN,
                    boundaries = listOf(132, 145, 158, 171)
                ),
                ZoneSnapshotEntry(
                    id = 2L,
                    timestampMs = 1704067200000L,
                    zoneType = SettingsDataStore.ZoneType.HR_RUN,
                    boundaries = listOf(128, 140, 154, 168)
                )
            ),
            onDismiss = {},
            onSaveSnapshot = { _, _ -> },
            onUpdateSnapshot = { _, _, _ -> },
            onDeleteSnapshot = {}
        )
    }
}

@Preview(name = "Zone History Dialog Dark", showBackground = true)
@Composable
private fun ZoneHistoryDialogDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        ZoneHistoryDialog(
            zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
            history = listOf(
                ZoneSnapshotEntry(
                    id = 1L,
                    timestampMs = 1735689600000L,
                    zoneType = SettingsDataStore.ZoneType.PWR_BIKE,
                    boundaries = listOf(149, 203, 243, 284, 324, 405)
                )
            ),
            onDismiss = {},
            onSaveSnapshot = { _, _ -> },
            onUpdateSnapshot = { _, _, _ -> },
            onDeleteSnapshot = {}
        )
    }
}
