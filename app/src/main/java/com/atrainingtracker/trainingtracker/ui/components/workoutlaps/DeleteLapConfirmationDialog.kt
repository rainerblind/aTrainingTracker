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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter

/**
 * Direction indicator for merging an interior lap (Option A per REQ-UI-454.1).
 */
enum class MergeDirection {
    PREVIOUS,
    NEXT
}

/**
 * Confirmation dialog for deleting a lap boundary and merging adjacent laps (REQ-UI-454.1 / REQ-UI-454.2).
 *
 * Implements Option A boundary direction selection:
 * - First lap (k == 1): merges Lap 1 with Lap 2.
 * - Last lap (k == N): merges Lap N-1 with Lap N.
 * - Interior laps (1 < k < N): Material 3 SingleChoiceSegmentedButtonRow allows toggling
 *   between merging with the previous lap (k-1 + k) and the next lap (k + k+1).
 *
 * Previews the combined metrics (duration, distance, speed/pace) before user confirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteLapConfirmationDialog(
    currentLap: LapData,
    allLaps: List<LapData>,
    isRunningSport: Boolean,
    onConfirm: (boundaryLapNr: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalLaps = allLaps.size
    val currentLapNr = currentLap.lapNr
    val isFirstLap = (currentLapNr == 1L)
    val isLastLap = (currentLapNr == totalLaps.toLong())
    val isInteriorLap = (!isFirstLap && !isLastLap)

    var mergeDirection by remember(currentLapNr) {
        mutableStateOf(if (isFirstLap) MergeDirection.NEXT else MergeDirection.PREVIOUS)
    }

    val removedBoundaryLapNr: Long = when {
        isFirstLap -> 1L
        isLastLap -> (totalLaps - 1).toLong().coerceAtLeast(1L)
        else -> if (mergeDirection == MergeDirection.PREVIOUS) currentLapNr - 1L else currentLapNr
    }

    val lapA = allLaps.find { it.lapNr == removedBoundaryLapNr }
    val lapB = allLaps.find { it.lapNr == removedBoundaryLapNr + 1L }
    val previewLap = if (lapA != null && lapB != null) {
        LapSplitAndMoveCalculator.calculateMergePreview(lapA, lapB)
    } else null

    val formatters = LocalMetricFormatter.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.delete_lap_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.delete_lap_warning_description,
                        removedBoundaryLapNr,
                        removedBoundaryLapNr + 1L
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option A Direction Selector (only shown for interior laps 1 < k < N)
                if (isInteriorLap) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.delete_lap_direction_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = mergeDirection == MergeDirection.PREVIOUS,
                                onClick = { mergeDirection = MergeDirection.PREVIOUS },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.delete_lap_merge_with_previous,
                                        currentLapNr - 1L,
                                        currentLapNr
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            SegmentedButton(
                                selected = mergeDirection == MergeDirection.NEXT,
                                onClick = { mergeDirection = MergeDirection.NEXT },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.delete_lap_merge_with_next,
                                        currentLapNr,
                                        currentLapNr + 1L
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Merged Lap Live Metric Preview
                if (previewLap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.delete_lap_preview_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (!previewLap.name.isNullOrBlank()) {
                                Text(
                                    text = previewLap.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                MetricItem(
                                    iconRes = R.drawable.ic_time_active,
                                    label = stringResource(R.string.time_active),
                                    value = formatters.time.format(previewLap.timeTotalS.toLong()),
                                    layout = MetricLayout.VERTICAL,
                                    iconSize = 18.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricItem(
                                    iconRes = R.drawable.ic_distance,
                                    label = stringResource(R.string.distance),
                                    value = formatters.distance.format_with_units(previewLap.distanceTotalM),
                                    layout = MetricLayout.VERTICAL,
                                    iconSize = 18.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                val speedPaceFormatted = if (isRunningSport) {
                                    formatters.pace.format_with_units(previewLap.speedAverageMps)
                                } else {
                                    formatters.speed.format_with_units(previewLap.speedAverageMps)
                                }
                                MetricItem(
                                    iconRes = R.drawable.ic_speed,
                                    label = if (isRunningSport) stringResource(R.string.pace) else stringResource(R.string.speed),
                                    value = speedPaceFormatted,
                                    layout = MetricLayout.VERTICAL,
                                    iconSize = 18.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(removedBoundaryLapNr)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.delete_lap_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
