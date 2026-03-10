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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.R

@Composable
fun EditEquipmentDialog(
    item: EquipmentItem,
    availableSensors: List<DevicesDatabaseManager.SimpleSensorInfo>,
    onDismiss: () -> Unit,
    onConfirm: (EquipmentItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var frameType by remember { mutableStateOf(item.frameType) }
    var selectedSensorIds by remember { mutableStateOf(item.linkedDeviceIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.equipment_configure_equipment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,        onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Only show the spinner if the item is a bike (id 1-4)
                if (item.frameType > 0) {
                    BikeTypeSelector(
                        selectedType = frameType,
                        onTypeSelected = { frameType = it }
                    )
                }

                MultiSelectSensorSpinner(
                    allSensors = availableSensors,
                    selectedIds = selectedSensorIds,
                    onToggleSensor = { id ->
                        selectedSensorIds = if (selectedSensorIds.contains(id)) {
                            selectedSensorIds - id
                        } else {
                            selectedSensorIds + id
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(item.copy(
                        name = name,
                        frameType = frameType,
                        linkedDeviceIds = selectedSensorIds.toList()
                    ))                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSensorSpinner(
    allSensors: List<DevicesDatabaseManager.SimpleSensorInfo>,
    selectedIds: Set<Long>,
    onToggleSensor: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // UI Label: Show names of selected sensors
    val displayText = allSensors
        .filter { selectedIds.contains(it.id) }
        .joinToString(", ") { it.name }
        .ifEmpty { stringResource(R.string.equipment_no_sensors_linked) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.equipment_linked_sensors)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allSensors.forEach { sensor ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedIds.contains(sensor.id), onCheckedChange = null)
                            Text(sensor.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                    onClick = { onToggleSensor(sensor.id) }
                )
            }
        }
    }
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
        linkedDeviceIds = listOf(1, 2),
        linkedDeviceNames = "Garmin HRM, Speed Sensor",
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
            onConfirm = {},
            availableSensors = listOf(
                DevicesDatabaseManager.SimpleSensorInfo(1, "Garmin HRM"),
                DevicesDatabaseManager.SimpleSensorInfo(2, "Wahoo Speed"),
                DevicesDatabaseManager.SimpleSensorInfo(3, "Stages Power"),
                DevicesDatabaseManager.SimpleSensorInfo(4, "Polar H10")
            )
        )
    }
}