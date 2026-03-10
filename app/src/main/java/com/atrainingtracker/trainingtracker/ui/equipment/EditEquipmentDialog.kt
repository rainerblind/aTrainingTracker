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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData

@Composable
fun EditEquipmentDialog(
    item: EquipmentItem,
    onDismiss: () -> Unit,
    onConfirm: (EquipmentItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var sensors by remember { mutableStateOf(item.sensors) }
    var frameType by remember { mutableIntStateOf(item.frameType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Equipment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sensors,
                    onValueChange = { sensors = it },
                    label = { Text("Sensors (e.g. Garmin HRM)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Comma separated list") }
                )

                // Currently a simple text display, could be a Dropdown later
                Text("Type: ${if (frameType == 1) "MTB" else "Road"}",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(item.copy(name = name, sensors = sensors, frameType = frameType))
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, name = "Edit Bike Preview")
@Composable
fun PreviewEditEquipmentDialog() {
    // Mock data for the preview matching your latest StatsData structure
    val mockStats = StatsData(
        title = "Gesamt",
        totalWorkouts = 150,
        totalDistanceWithUnits = "4.500 km",
        timeWithUnits = "120:30 h",
        totalAscentWithUnits = "25.000 m"
    )

    val mockBike = EquipmentItem(
        id = 1,
        name = "Specialized Roubaix",
        sensors = "Garmin HRM, Speed Sensor",
        frameType = 3, // Road
        firstUsed = "2023-01-15",
        lastUsed = "2024-03-08",
        statsData = mockStats,
        stravaName = "Specialized Roubaix",
        stravaId = "12345678"
    )

    MaterialTheme {
        EditEquipmentDialog(
            item = mockBike,
            onDismiss = {},
            onConfirm = {}
        )
    }
}