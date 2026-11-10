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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter

/**
 * Pure UI state-driven composable rendering a compact workout summary list card (REQ-UI-506.2).
 *
 * Decouples rendering from formatter calls, string concatenation, and live tracking checks.
 */
@Composable
fun WorkoutSummaryCompact(
    uiState: WorkoutCardUiState,
    onMapClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkFinished: (() -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val hasContextMenu = uiState.hasContextMenu && (uiState.canDelete || (uiState.canMarkFinished && onMarkFinished != null))

    Box {
        MappableListItem(
            modifier = modifier,
            onClick = onMapClick,
            onLongClick = if (hasContextMenu) { { showContextMenu = true } } else null,
            alpha = uiState.contentAlpha
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                // --- ROW 1: Workout Name ---
                Text(
                    text = uiState.workoutName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // --- ROW 2: Icon, Sport, Equipment, Date & Time ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sport Icon
                    Icon(
                        painter = painterResource(id = uiState.sportIconResId),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = Color.Unspecified
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sport Name
                    Text(
                        text = uiState.sportName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Equipment (if present)
                    if (!uiState.equipmentName.isNullOrBlank()) {
                        Text(
                            text = ": ${uiState.equipmentName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Date and Time
                    Text(
                        text = uiState.formattedDateTime,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- ROW 3: Active Time, Distance, Speed/Pace, Ascent ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Active Time
                    CompactMetricItem(
                        label = stringResource(SensorType.TIME_ACTIVE.fullNameId),
                        value = uiState.activeTimeFormatted
                    )

                    // Distance
                    CompactMetricItem(
                        label = stringResource(SensorType.DISTANCE_m.fullNameId),
                        value = uiState.distanceFormatted
                    )

                    // Speed/Pace
                    CompactMetricItem(
                        label = "Ø " + stringResource(SensorType.SPEED_mps.fullNameId),
                        value = uiState.speedPaceFormatted
                    )

                    // Ascent
                    CompactMetricItem(
                        label = stringResource(SensorType.ASCENT.fullNameId),
                        value = uiState.ascentFormatted
                    )
                }
            }
        }

        // Context Menu for deletion (Pinned to Top-Start to cover the header area)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                if (uiState.canMarkFinished && onMarkFinished != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mark_as_finished)) },
                        onClick = {
                            showContextMenu = false
                            onMarkFinished()
                        },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }
                if (uiState.canDelete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showContextMenu = false
                            onDeleteRequest()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible domain model overload of [WorkoutSummaryCompact] (REQ-UI-506.4).
 */
@Composable
fun WorkoutSummaryCompact(
    workoutData: WorkoutData,
    onMapClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkFinished: (() -> Unit)? = null
) {
    val formatters = LocalMetricFormatter.current
    val isActivelyTracked = TrainingApplication.isActivelyTracked(workoutData.id)
    val uiState = remember(workoutData, formatters, isActivelyTracked) {
        WorkoutCardUiState.from(
            workoutData = workoutData,
            formatters = formatters,
            isActivelyTracked = isActivelyTracked
        )
    }

    WorkoutSummaryCompact(
        uiState = uiState,
        onMapClick = onMapClick,
        onDeleteRequest = onDeleteRequest,
        modifier = modifier,
        onMarkFinished = onMarkFinished
    )
}

@Composable
private fun CompactMetricItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, name = "Workout Summary Compact Preview")
@Composable
private fun WorkoutSummaryCompactPreview() {
    val sampleState = WorkoutCardUiState(
        workoutId = 1L,
        workoutName = "Morning Run in the Park",
        sportIconResId = BSportType.RUN.iconResId,
        sportName = "Running",
        equipmentName = "Nike Pegasus",
        formattedDateTime = "10.11.2026  07:30",
        activeTimeFormatted = "45:12 min",
        distanceFormatted = "8.45 km",
        speedPaceFormatted = "5:21 /km",
        ascentFormatted = "120 m",
        isFinished = true,
        isActivelyTracked = false
    )
    WorkoutSummaryCompact(
        uiState = sampleState,
        onMapClick = {},
        onDeleteRequest = {}
    )
}
