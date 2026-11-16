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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import kotlinx.coroutines.launch

data class PoolLengthPreset(
    val labelRes: Int,
    val lengthMeters: Double
)

val POOL_LENGTH_PRESETS = listOf(
    PoolLengthPreset(R.string.tuning_swim_pool_preset_25m, 25.0),
    PoolLengthPreset(R.string.tuning_swim_pool_preset_50m, 50.0),
    PoolLengthPreset(R.string.tuning_swim_pool_preset_25yd, 22.86)
)

/**
 * Material 3 Card rendered in Aftermath Workout Details for swimming sessions.
 * Allows post-workout pool length switching and +/- length adjustments (REQ-SET-111.3, ATT-8001).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwimWorkoutAdjustCard(
    workoutId: Long,
    currentPoolLengthM: Double,
    currentLengthCount: Int,
    onAdjust: suspend (poolLengthM: Double, lengthCount: Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    var selectedPoolLength by remember(currentPoolLengthM) { mutableDoubleStateOf(currentPoolLengthM) }
    var selectedLengths by remember(currentLengthCount) { mutableIntStateOf(Math.max(1, currentLengthCount)) }
    var isRecalculating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun triggerRecalculate(newLengthM: Double, newCount: Int) {
        if (isRecalculating) return
        selectedPoolLength = newLengthM
        selectedLengths = Math.max(1, newCount)
        isRecalculating = true
        scope.launch {
            try {
                onAdjust(newLengthM, Math.max(1, newCount))
            } finally {
                isRecalculating = false
            }
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("swim_workout_adjust_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title + Recalculating Spinner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pool,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.swim_adjust_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isRecalculating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("swim_adjust_progress_indicator"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.swim_adjust_recalculating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Pool Length Preset Selector
            Text(
                text = stringResource(R.string.swim_adjust_pool_length_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("swim_adjust_pool_length_selector")
            ) {
                POOL_LENGTH_PRESETS.forEachIndexed { index, preset ->
                    val isSelected = Math.abs(selectedPoolLength - preset.lengthMeters) < 0.2
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = POOL_LENGTH_PRESETS.size),
                        onClick = {
                            if (!isSelected && !isRecalculating) {
                                triggerRecalculate(preset.lengthMeters, selectedLengths)
                            }
                        },
                        selected = isSelected,
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(preset.labelRes),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            // Lengths Adjuster (+1 / -1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.swim_adjust_lengths_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (selectedLengths > 1 && !isRecalculating) {
                                triggerRecalculate(selectedPoolLength, selectedLengths - 1)
                            }
                        },
                        enabled = selectedLengths > 1 && !isRecalculating,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("swim_adjust_decrement_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "-",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "$selectedLengths",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("swim_adjust_lengths_value")
                    )

                    FilledTonalIconButton(
                        onClick = {
                            if (!isRecalculating) {
                                triggerRecalculate(selectedPoolLength, selectedLengths + 1)
                            }
                        },
                        enabled = !isRecalculating,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("swim_adjust_increment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "SwimWorkoutAdjustCard - Light", showBackground = true)
@Composable
private fun SwimWorkoutAdjustCardPreviewLight() {
    MaterialTheme {
        SwimWorkoutAdjustCard(
            workoutId = 1L,
            currentPoolLengthM = 25.0,
            currentLengthCount = 40,
            onAdjust = { _, _ -> true },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "SwimWorkoutAdjustCard - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SwimWorkoutAdjustCardPreviewDark() {
    MaterialTheme {
        SwimWorkoutAdjustCard(
            workoutId = 1L,
            currentPoolLengthM = 50.0,
            currentLengthCount = 20,
            onAdjust = { _, _ -> true },
            modifier = Modifier.padding(16.dp)
        )
    }
}
