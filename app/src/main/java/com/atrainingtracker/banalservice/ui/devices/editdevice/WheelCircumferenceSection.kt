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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.equipment.WheelCircumferenceHistoryChart
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlin.math.roundToInt

/**
 * Pure stateless wheel-circumference configuration and longitudinal history section for bike
 * wheel-speed sensors inside [EditDeviceDialog] (REQ-SEN-033.1, REQ-SEN-033.3, ATT-8422).
 *
 * Preserves the tire-size preset dropdown and initial mm input for newly paired sensors while
 * communicating automatic GNSS calibration during rides and embedding [WheelCircumferenceHistoryChart]
 * with a single consolidated baseline reset action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WheelCircumferenceSection(
    wheelCircumferenceMm: Int?,
    baselineCircumferenceMeters: Double?,
    wheelSizeNames: List<String>,
    wheelSizeValues: List<String>,
    historyRecords: List<WheelCircumferenceRecord>,
    onWheelCircumferenceChanged: (Int) -> Unit,
    onResetBaseline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wheel_circumference_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.devices_wheel_circumference),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = (wheelCircumferenceMm ?: 0).toString(),
                onValueChange = { input ->
                    input.toIntOrNull()?.let { valueInt -> onWheelCircumferenceChanged(valueInt) }
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .testTag("wheel_circumference_input"),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                wheelSizeNames.forEachIndexed { index, name ->
                    val mmText = wheelSizeValues.getOrNull(index) ?: return@forEachIndexed
                    DropdownMenuItem(
                        text = { Text("$name ($mmText mm)") },
                        onClick = {
                            mmText.toIntOrNull()?.let { onWheelCircumferenceChanged(it) }
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.devices_wheel_circumference_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("wheel_circumference_auto_hint")
        )

        val baselineMm = baselineCircumferenceMeters?.let { (it * 1000.0).roundToInt() }
        if (baselineMm != null && baselineMm > 0) {
            Text(
                text = stringResource(R.string.devices_wheel_baseline_format, baselineMm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("wheel_circumference_baseline_label")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        WheelCircumferenceHistoryChart(
            historyRecords = historyRecords,
            onResetBaseline = onResetBaseline,
            emptyStateText = stringResource(R.string.devices_wheel_history_empty)
        )
    }
}

@Preview(showBackground = true, name = "Wheel Circumference Section - Light")
@Composable
private fun PreviewWheelCircumferenceSectionLight() {
    ATrainingTrackerTheme(darkTheme = false) {
        WheelCircumferenceSection(
            wheelCircumferenceMm = 2136,
            baselineCircumferenceMeters = 2.136,
            wheelSizeNames = listOf("700 x 25", "700 x 28", "700 x 32"),
            wheelSizeValues = listOf("2105", "2136", "2155"),
            historyRecords = listOf(
                WheelCircumferenceRecord(1, 1, 1000L, WheelPosition.FRONT, 2136),
                WheelCircumferenceRecord(2, 1, 2000L, WheelPosition.FRONT, 2132),
                WheelCircumferenceRecord(3, 1, 3000L, WheelPosition.FRONT, 2128)
            ),
            onWheelCircumferenceChanged = {},
            onResetBaseline = {}
        )
    }
}
