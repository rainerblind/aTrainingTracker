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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

/**
 * Bottom popup presented when an athlete returns to the base version with > 2 paired sensors (REQ-SET-084.5, REQ-UI-149, ATT-5661).
 * Guides athlete to select up to 2 active sensors to keep, non-destructively unpairing excess devices while
 * preserving calibration and equipment data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepTwoSensorsBottomSheet(
    currentlyPairedDevices: List<DeviceUiData>,
    defaultKeepDeviceIds: Set<Long>,
    onConfirmSelection: (selectedIds: Set<Long>) -> Unit,
    onNavigateToSupporter: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(currentlyPairedDevices) {
        mutableStateOf(defaultKeepDeviceIds)
    }

    AppModalBottomSheet(
        title = stringResource(R.string.keep_two_sensors_title),
        onDismissRequest = onDismiss,
        icon = Icons.Default.Devices,
        actions = {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onNavigateToSupporter()
                },
                modifier = Modifier.weight(1.4f)
            ) {
                Text(text = stringResource(R.string.keep_two_sensors_action_unlock))
            }
            Button(
                onClick = {
                    onConfirmSelection(selectedIds)
                },
                enabled = selectedIds.size in 1..2,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.keep_two_sensors_action_confirm))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.keep_two_sensors_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(R.string.keep_two_sensors_selection_header, selectedIds.size, 2),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        currentlyPairedDevices.forEach { device ->
            val isSelected = device.id in selectedIds
            val isDefault = device.id in defaultKeepDeviceIds

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedIds = if (isSelected) {
                            selectedIds - device.id
                        } else {
                            if (selectedIds.size < 2) {
                                selectedIds + device.id
                            } else {
                                selectedIds
                            }
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            selectedIds = if (checked) {
                                if (selectedIds.size < 2) selectedIds + device.id else selectedIds
                            } else {
                                selectedIds - device.id
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isDefault) {
                            Text(
                                text = stringResource(R.string.supporter_recommended_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KeepTwoSensorsBottomSheetPreview() {
    val sampleDevices = listOf(
        DeviceUiData(
            id = 1L,
            protocol = Protocol.SMARTPHONE,
            deviceType = DeviceType.SPEED_AND_LOCATION_GPS,
            lastSeen = null,
            manufacturer = "Internal",
            deviceName = "Internal GPS",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 100,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = true,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        ),
        DeviceUiData(
            id = 2L,
            protocol = Protocol.SMARTPHONE,
            deviceType = DeviceType.ALTITUDE_FROM_PRESSURE,
            lastSeen = null,
            manufacturer = "Internal",
            deviceName = "Barometer",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 100,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = true,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        ),
        DeviceUiData(
            id = 3L,
            protocol = Protocol.BLUETOOTH_LE,
            deviceType = DeviceType.HRM,
            lastSeen = null,
            manufacturer = "Polar",
            deviceName = "Polar H10",
            isPaired = true,
            linkedEquipment = emptyList(),
            availableEquipment = emptyList(),
            powerFeaturesFlags = null,
            batteryPercentage = 85,
            linkedSportTypes = emptyList(),
            deviceTypeIconRes = 0,
            batteryStatusIconRes = 0,
            onEquipmentResId = 0,
            wheelCircumference = null,
            calibrationFactor = null,
            powerFeatures = null,
            isConnected = false,
            mainValue = null,
            allValues = null,
            isNewlyDiscovered = false
        )
    )

    ATrainingTrackerTheme {
        KeepTwoSensorsBottomSheet(
            currentlyPairedDevices = sampleDevices,
            defaultKeepDeviceIds = setOf(1L, 2L),
            onConfirmSelection = {},
            onNavigateToSupporter = {},
            onDismiss = {}
        )
    }
}
