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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.SimpleSensorInfo
import com.atrainingtracker.banalservice.ui.devices.devicedata.isWheelSpeedSensor
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.components.stats.StatsData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Computes conflict swap hints for [WheelPositionSelectionDialog] from other currently selected
 * wheel speed sensors on the same bike (`REQ-SEN-035.1`).
 */
internal fun computeWheelPositionConflictHints(
    targetSensorId: Long,
    selectedSensorIds: Set<Long>,
    draftWheelPositions: Map<Long, WheelPosition>,
    allSensors: List<SimpleSensorInfo>
): Map<WheelPosition, String> {
    val hints = mutableMapOf<WheelPosition, String>()
    for (sensorId in selectedSensorIds) {
        if (sensorId == targetSensorId) continue
        val pos = draftWheelPositions[sensorId] ?: continue
        if (pos != WheelPosition.FRONT && pos != WheelPosition.REAR) continue
        val sensorName = allSensors.firstOrNull { it.id == sensorId }?.name
        if (!sensorName.isNullOrBlank() && !hints.containsKey(pos)) {
            hints[pos] = sensorName
        }
    }
    return hints
}

/**
 * Applies a draft wheel mounting position selection for [targetSensorId] on a bike, resolving
 * Front/Rear slot conflicts identically to `EquipmentRepository.setWheelSensorLink()`
 * (`REQ-SEN-035.1`): swaps 2 sensors, moves a single displaced sensor to the free opposite wheel,
 * or demotes the displaced sensor to [WheelPosition.UNSPECIFIED] when both Front and Rear are taken.
 */
internal fun applyDraftWheelPositionSelection(
    targetSensorId: Long,
    targetPosition: WheelPosition,
    selectedSensorIds: Set<Long>,
    draftWheelPositions: Map<Long, WheelPosition>
): Pair<Set<Long>, Map<Long, WheelPosition>> {
    val nextSelected = selectedSensorIds + targetSensorId
    val nextPositions = draftWheelPositions.filterKeys { it in nextSelected }.toMutableMap()

    if (targetPosition == WheelPosition.FRONT || targetPosition == WheelPosition.REAR) {
        val conflictingId = nextPositions.entries.firstOrNull {
            it.key != targetSensorId && it.key in nextSelected && it.value == targetPosition
        }?.key
        if (conflictingId != null) {
            val oldPositionOfTarget = nextPositions[targetSensorId] ?: WheelPosition.UNSPECIFIED
            val targetForConflicting = if (oldPositionOfTarget == WheelPosition.FRONT || oldPositionOfTarget == WheelPosition.REAR) {
                oldPositionOfTarget
            } else {
                val opposite = if (targetPosition == WheelPosition.FRONT) WheelPosition.REAR else WheelPosition.FRONT
                val oppositeTaken = nextPositions.entries.any {
                    it.key != targetSensorId && it.key != conflictingId && it.key in nextSelected && it.value == opposite
                }
                if (oppositeTaken) WheelPosition.UNSPECIFIED else opposite
            }
            nextPositions[conflictingId] = targetForConflicting
        }
    }

    nextPositions[targetSensorId] = targetPosition
    return nextSelected to nextPositions.toMap()
}

/**
 * Formats the collapsed Linked Sensors summary text in [EditEquipmentDialog], appending localized
 * wheel mounting positions for selected wheel speed sensors on bikes (`REQ-SEN-035.2`).
 */
internal fun formatSensorSummaryText(
    allSensors: List<SimpleSensorInfo>,
    selectedIds: Set<Long>,
    wheelPositions: Map<Long, WheelPosition>,
    isBike: Boolean,
    frontLabel: String,
    rearLabel: String,
    unspecifiedLabel: String,
    emptyLabel: String
): String {
    val selected = allSensors.filter { selectedIds.contains(it.id) }
    if (selected.isEmpty()) return emptyLabel
    return selected.joinToString(", ") { sensor ->
        val isWheel = isBike && sensor.deviceType != null && isWheelSpeedSensor(sensor.deviceType)
        if (!isWheel) {
            sensor.name
        } else {
            val posLabel = when (wheelPositions[sensor.id] ?: WheelPosition.UNSPECIFIED) {
                WheelPosition.FRONT -> frontLabel
                WheelPosition.REAR -> rearLabel
                WheelPosition.UNSPECIFIED -> unspecifiedLabel
            }
            "${sensor.name} ($posLabel)"
        }
    }
}

/**
 * Modernized bottom sheet dialog for configuring equipment (bikes, shoes, sensors, sport links)
 * (REQ-UI-149, TST-UI-102, REQ-UI-511, REQ-SEN-035).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEquipmentDialog(
    item: EquipmentItem,
    availableSensors: List<SimpleSensorInfo>,
    availableSportTypes: List<SimpleSportTypeInfo>,
    historyRecords: List<WheelCircumferenceRecord> = emptyList(),
    onResetBaseline: (() -> Unit)? = null,
    onConfirm: (EquipmentItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var frameType by remember(item.id) { mutableStateOf(item.frameType) }
    var selectedSensorIds by remember(item.id) { mutableStateOf(item.linkedDeviceIds.toSet()) }
    var draftWheelPositions by remember(item.id) {
        mutableStateOf(item.linkedDeviceWheelPositions.filterKeys { it in item.linkedDeviceIds })
    }
    var selectedSportTypeIds by remember(item.id) { mutableStateOf(item.linkedSportTypeIds.toSet()) }
    var isRetired by remember(item.id) { mutableStateOf(item.isRetired) }
    var wheelDialogSensor by remember { mutableStateOf<SimpleSensorInfo?>(null) }

    val isBike = item.frameType > 0
    val iconRes = if (isBike) BSportType.BIKE.iconResId else BSportType.RUN.iconResId

    // Multi-select for Sensors (Defensive compatibility filtering & prioritized ordering: REQ-UI-256, REQ-UI-283)
    val compatibleSensors = remember(availableSensors, item.frameType) {
        val sportType = if (isBike) BSportType.BIKE else BSportType.RUN
        val filtered = availableSensors.filter { sensor ->
            if (sensor.deviceType == null) {
                true
            } else if (isBike) {
                DeviceDataRepository.isBikeSensor(sensor.deviceType) || DeviceDataRepository.isSharedSensor(sensor.deviceType)
            } else {
                DeviceDataRepository.isRunSensor(sensor.deviceType) || DeviceDataRepository.isSharedSensor(sensor.deviceType)
            }
        }
        EquipmentSensorOrdering.sortSensors(filtered, sportType)
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(id = R.string.equipment_configure_equipment),
        iconPainter = painterResource(id = iconRes),
        iconTint = Color.Unspecified,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    onConfirm(
                        item.copy(
                            name = name,
                            frameType = frameType,
                            linkedDeviceIds = selectedSensorIds.toList(),
                            linkedSportTypeIds = selectedSportTypeIds.toList(),
                            isRetired = isRetired,
                            linkedDeviceWheelPositions = draftWheelPositions.filterKeys { it in selectedSensorIds }
                        )
                    )
                },
                onCancel = onDismiss,
                saveText = stringResource(R.string.save)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.name)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Only show the spinner if the item is a bike (id 1-4)
            if (isBike) {
                BikeTypeSelector(
                    selectedType = frameType,
                    onTypeSelected = { frameType = it }
                )
            }

            // Multi-select for Sport Types
            MultiSelectSportTypeSpinner(
                allSportTypes = availableSportTypes,
                selectedIds = selectedSportTypeIds,
                onToggleSportType = { id ->
                    selectedSportTypeIds = if (selectedSportTypeIds.contains(id)) {
                        selectedSportTypeIds - id
                    } else {
                        selectedSportTypeIds + id
                    }
                }
            )

            if (compatibleSensors.isNotEmpty()) {
                MultiSelectSensorSpinner(
                    allSensors = compatibleSensors,
                    selectedIds = selectedSensorIds,
                    onToggleSensor = { id ->
                        selectedSensorIds = if (selectedSensorIds.contains(id)) {
                            selectedSensorIds - id
                        } else {
                            selectedSensorIds + id
                        }
                    },
                    wheelPositions = draftWheelPositions,
                    isBike = isBike,
                    onWheelSensorClick = { sensor ->
                        wheelDialogSensor = sensor
                    }
                )
            }

            // Retirement toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.equipment_retired),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isRetired,
                    onCheckedChange = { isRetired = it }
                )
            }

            // Wheel Circumference History Chart for Bikes (REQ-SEN-030.4, ATT-7753)
            if (isBike) {
                WheelCircumferenceHistoryChart(
                    historyRecords = historyRecords,
                    onResetBaseline = { onResetBaseline?.invoke() }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    wheelDialogSensor?.let { sensor ->
        val conflictHints = computeWheelPositionConflictHints(
            targetSensorId = sensor.id,
            selectedSensorIds = selectedSensorIds,
            draftWheelPositions = draftWheelPositions,
            allSensors = compatibleSensors
        )
        WheelPositionSelectionDialog(
            equipmentName = name.ifBlank { item.name },
            sensorName = sensor.name,
            currentPosition = draftWheelPositions[sensor.id] ?: WheelPosition.UNSPECIFIED,
            isLinked = selectedSensorIds.contains(sensor.id),
            conflictHints = conflictHints,
            onSelectPosition = { position ->
                val (nextSelected, nextPositions) = applyDraftWheelPositionSelection(
                    targetSensorId = sensor.id,
                    targetPosition = position,
                    selectedSensorIds = selectedSensorIds,
                    draftWheelPositions = draftWheelPositions
                )
                selectedSensorIds = nextSelected
                draftWheelPositions = nextPositions
                wheelDialogSensor = null
            },
            onUnlink = {
                selectedSensorIds = selectedSensorIds - sensor.id
                draftWheelPositions = draftWheelPositions - sensor.id
                wheelDialogSensor = null
            },
            onDismiss = {
                wheelDialogSensor = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSportTypeSpinner(
    allSportTypes: List<SimpleSportTypeInfo>,
    selectedIds: Set<Long>,
    onToggleSportType: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // UI Label: Show names of selected sport types
    val displayText = allSportTypes
        .filter { selectedIds.contains(it.id) }
        .joinToString(", ") { it.name }
        .ifEmpty { stringResource(R.string.equipment_no_sports_linked) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.equipment_linked_sport_types)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allSportTypes.forEach { sport ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedIds.contains(sport.id), onCheckedChange = null)
                            Text(sport.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                    onClick = { onToggleSportType(sport.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSensorSpinner(
    allSensors: List<SimpleSensorInfo>,
    selectedIds: Set<Long>,
    onToggleSensor: (Long) -> Unit,
    wheelPositions: Map<Long, WheelPosition> = emptyMap(),
    isBike: Boolean = false,
    onWheelSensorClick: ((SimpleSensorInfo) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val frontLabel = stringResource(R.string.equipment_wheel_position_front)
    val rearLabel = stringResource(R.string.equipment_wheel_position_rear)
    val unspecifiedLabel = stringResource(R.string.equipment_wheel_position_unspecified)
    val emptyLabel = stringResource(R.string.equipment_no_sports_linked)

    val displayText = formatSensorSummaryText(
        allSensors = allSensors,
        selectedIds = selectedIds,
        wheelPositions = wheelPositions,
        isBike = isBike,
        frontLabel = frontLabel,
        rearLabel = rearLabel,
        unspecifiedLabel = unspecifiedLabel,
        emptyLabel = emptyLabel
    )

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
                .testTag("edit_equipment_sensor_field")
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allSensors.forEach { sensor ->
                val isSelected = selectedIds.contains(sensor.id)
                val isWheelSensor = isBike && sensor.deviceType != null && isWheelSpeedSensor(sensor.deviceType)
                DropdownMenuItem(
                    modifier = Modifier.testTag("edit_equipment_sensor_row_${sensor.id}"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                            if (isWheelSensor) {
                                val position = wheelPositions[sensor.id] ?: WheelPosition.UNSPECIFIED
                                val posLabel = when (position) {
                                    WheelPosition.FRONT -> frontLabel
                                    WheelPosition.REAR -> rearLabel
                                    WheelPosition.UNSPECIFIED -> unspecifiedLabel
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp, end = 8.dp)
                                ) {
                                    Text(
                                        text = sensor.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = posLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (position == WheelPosition.UNSPECIFIED) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                }
                                if (isSelected && (position == WheelPosition.FRONT || position == WheelPosition.REAR)) {
                                    val badgeRes = if (position == WheelPosition.FRONT) {
                                        R.string.equipment_wheel_position_front_badge
                                    } else {
                                        R.string.equipment_wheel_position_rear_badge
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.testTag("edit_equipment_sensor_badge_${sensor.id}")
                                    ) {
                                        Text(
                                            text = stringResource(badgeRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = sensor.name,
                                    modifier = Modifier.padding(start = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    onClick = {
                        if (isWheelSensor && onWheelSensorClick != null) {
                            expanded = false
                            onWheelSensorClick(sensor)
                        } else {
                            onToggleSensor(sensor.id)
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Edit Bike Wheel Positions Light")
@Composable
private fun PreviewEditEquipmentDialogLight() {
    val mockStats = StatsData(
        primaryTitle = "Specialized Roubaix",
        secondaryTitle = "Gesamt",
        totalWorkouts = 150,
        totalDistanceWithUnits = "4.500 km",
        timeWithUnits = "120:30 h",
        totalAscentWithUnits = "25.000 m"
    )
    val mockBike = EquipmentItem(
        id = 1,
        name = "Specialized Roubaix",
        linkedDeviceIds = listOf(1, 2, 3),
        linkedDeviceNames = "Garmin HRM, Wahoo Front, Sigma Rear",
        linkedSportTypeIds = listOf(1),
        linkedSportTypeNames = "Ride",
        frameType = 3,
        firstUsed = "2023-01-15",
        lastUsed = "2024-03-08",
        statsData = mockStats,
        stravaName = "Specialized Roubaix",
        stravaId = "12345678",
        linkedDeviceWheelPositions = mapOf(
            2L to WheelPosition.FRONT,
            3L to WheelPosition.REAR
        )
    )
    ATrainingTrackerTheme(darkTheme = false) {
        EditEquipmentDialog(
            item = mockBike,
            onDismiss = {},
            onConfirm = {},
            availableSensors = listOf(
                SimpleSensorInfo(1, "Garmin HRM", DeviceType.HRM),
                SimpleSensorInfo(2, "Wahoo Speed", DeviceType.BIKE_SPEED),
                SimpleSensorInfo(3, "Sigma Speed+Cad", DeviceType.BIKE_SPEED_AND_CADENCE),
                SimpleSensorInfo(4, "Stages Power", DeviceType.BIKE_POWER)
            ),
            availableSportTypes = emptyList()
        )
    }
}

@Preview(showBackground = true, name = "Edit Bike Wheel Positions Dark")
@Composable
private fun PreviewEditEquipmentDialogDark() {
    val mockStats = StatsData(
        primaryTitle = "Specialized Roubaix",
        secondaryTitle = "Gesamt",
        totalWorkouts = 150,
        totalDistanceWithUnits = "4.500 km",
        timeWithUnits = "120:30 h",
        totalAscentWithUnits = "25.000 m"
    )
    val mockBike = EquipmentItem(
        id = 1,
        name = "Specialized Roubaix",
        linkedDeviceIds = listOf(1, 2, 3),
        linkedDeviceNames = "Garmin HRM, Wahoo Front, Sigma Rear",
        linkedSportTypeIds = listOf(1),
        linkedSportTypeNames = "Ride",
        frameType = 3,
        firstUsed = "2023-01-15",
        lastUsed = "2024-03-08",
        statsData = mockStats,
        stravaName = "Specialized Roubaix",
        stravaId = "12345678",
        linkedDeviceWheelPositions = mapOf(
            2L to WheelPosition.FRONT,
            3L to WheelPosition.REAR
        )
    )
    ATrainingTrackerTheme(darkTheme = true) {
        EditEquipmentDialog(
            item = mockBike,
            onDismiss = {},
            onConfirm = {},
            availableSensors = listOf(
                SimpleSensorInfo(1, "Garmin HRM", DeviceType.HRM),
                SimpleSensorInfo(2, "Wahoo Speed", DeviceType.BIKE_SPEED),
                SimpleSensorInfo(3, "Sigma Speed+Cad", DeviceType.BIKE_SPEED_AND_CADENCE),
                SimpleSensorInfo(4, "Stages Power", DeviceType.BIKE_POWER)
            ),
            availableSportTypes = emptyList()
        )
    }
}
