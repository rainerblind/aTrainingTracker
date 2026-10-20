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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Mode of sport type assignment during batch route import.
 */
enum class BatchSportMode {
    /** Apply a single selected sport type to all imported routes in batch. */
    ALL,
    /** Step through each imported route sequentially for interactive inspection and editing. */
    INDIVIDUAL
}

/**
 * Modal prompt displayed during batch GPX route import (REQ-MAP-053.6).
 *
 * Allows the athlete to choose between:
 * 1. [BatchSportMode.ALL]: Uniformly assigns the chosen [BSportType] to all imported routes.
 * 2. [BatchSportMode.INDIVIDUAL]: Sequentially reviews and customizes each route before saving.
 *
 * Adheres to Material 3 interaction standards and Design Guidelines Section 1.1 for binary mode toggles.
 * Must be invoked on the Android UI thread.
 *
 * @param routeCount Number of routes selected for batch import.
 * @param onConfirm Callback dispatched with the selected [BatchSportMode] and target [BSportType].
 * @param onDismiss Callback dispatched when the dialog is dismissed or canceled.
 * @param modifier Optional [Modifier] for layout adjustments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportSportTypeDialog(
    routeCount: Int,
    onConfirm: (mode: BatchSportMode, selectedSport: BSportType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(BatchSportMode.ALL) }
    var selectedSport by remember { mutableStateOf(BSportType.BIKE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.batch_import_dialog_title_format, routeCount))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == BatchSportMode.ALL,
                        onClick = { selectedMode = BatchSportMode.ALL },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_import_mode_all),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    SegmentedButton(
                        selected = selectedMode == BatchSportMode.INDIVIDUAL,
                        onClick = { selectedMode = BatchSportMode.INDIVIDUAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_import_mode_individual),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedMode) {
                    BatchSportMode.ALL -> {
                        Text(
                            text = stringResource(R.string.route_sport_type),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN).forEach { sport ->
                                FilterChip(
                                    selected = selectedSport == sport,
                                    onClick = { selectedSport = sport },
                                    label = { Text(stringResource(sport.stringResId)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = sport.iconResId),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    BatchSportMode.INDIVIDUAL -> {
                        Text(
                            text = stringResource(R.string.batch_import_individual_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode, selectedSport) }
            ) {
                Text(
                    text = stringResource(
                        if (selectedMode == BatchSportMode.ALL) {
                            R.string.batch_import_action_import
                        } else {
                            R.string.batch_import_action_continue
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        modifier = modifier
    )
}

@Preview(name = "Batch Import Dialog - Apply to All (Light)", showBackground = true)
@Composable
private fun BatchImportSportTypeDialogAllLightPreview() {
    ATrainingTrackerTheme {
        BatchImportSportTypeDialog(
            routeCount = 5,
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}

@Preview(name = "Batch Import Dialog - Apply to All (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun BatchImportSportTypeDialogAllDarkPreview() {
    ATrainingTrackerTheme {
        BatchImportSportTypeDialog(
            routeCount = 12,
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}
