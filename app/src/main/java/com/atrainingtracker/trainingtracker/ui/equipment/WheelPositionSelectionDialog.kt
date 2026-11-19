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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Selection dialog for configuring wheel sensor mounting position (Front, Rear) (REQ-SEN-029, REQ-SEN-031, REQ-SEN-032).
 */
@Composable
fun WheelPositionSelectionDialog(
    equipmentName: String,
    sensorName: String,
    currentPosition: WheelPosition,
    isLinked: Boolean,
    onSelectPosition: (WheelPosition) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    conflictHints: Map<WheelPosition, String> = emptyMap()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("wheel_position_dialog"),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.equipment_wheel_position_title),
                    style = MaterialTheme.typography.titleLarge
                )
                val subtitle = listOf(equipmentName, sensorName).filter { it.isNotBlank() }.joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    Triple(WheelPosition.FRONT, R.string.equipment_wheel_position_front, "wheel_position_front"),
                    Triple(WheelPosition.REAR, R.string.equipment_wheel_position_rear, "wheel_position_rear")
                )

                options.forEach { (pos, labelRes, tag) ->
                    val isSelected = isLinked && currentPosition == pos
                    val conflictingSensor = conflictHints[pos]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    onSelectPosition(pos)
                                    onDismiss()
                                },
                                role = Role.RadioButton
                            )
                            .testTag(tag)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectPosition(pos)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (!conflictingSensor.isNullOrBlank()) {
                                Text(
                                    text = stringResource(
                                        R.string.equipment_wheel_position_conflict_swap,
                                        conflictingSensor
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.testTag("${tag}_conflict")
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("wheel_position_cancel")
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        dismissButton = if (isLinked) {
            {
                TextButton(
                    onClick = {
                        onUnlink()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("wheel_position_unlink")
                ) {
                    Text(text = stringResource(R.string.equipment_wheel_position_unlink))
                }
            }
        } else null
    )
}

@Preview(showBackground = true, name = "Wheel Position Dialog Light")
@Composable
private fun WheelPositionSelectionDialogLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        WheelPositionSelectionDialog(
            equipmentName = "Gravel Bike",
            sensorName = "Speed Sensor 2",
            currentPosition = WheelPosition.FRONT,
            isLinked = true,
            conflictHints = mapOf(WheelPosition.REAR to "Wahoo Rear"),
            onSelectPosition = {},
            onUnlink = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, name = "Wheel Position Dialog Dark")
@Composable
private fun WheelPositionSelectionDialogDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        WheelPositionSelectionDialog(
            equipmentName = "Gravel Bike",
            sensorName = "Speed Sensor 2",
            currentPosition = WheelPosition.FRONT,
            isLinked = true,
            conflictHints = mapOf(WheelPosition.REAR to "Wahoo Rear"),
            onSelectPosition = {},
            onUnlink = {},
            onDismiss = {}
        )
    }
}
