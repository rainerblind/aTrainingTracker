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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData

@Composable
fun EditDeviceDialog(
    deviceId: Long,
    onDismiss: () -> Unit,
    viewModel: EditDeviceViewModel = viewModel()
) {
    val snapshot by viewModel.deviceSnapshot.collectAsStateWithLifecycle()
    val liveData by viewModel.deviceLiveData.collectAsStateWithLifecycle(initialValue = null)

    // Initialize data only once
    LaunchedEffect(deviceId) {
        viewModel.loadInitialDeviceData(deviceId)
    }

    snapshot?.let { data ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = data.deviceTypeIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.edit_device))
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Manufacturer
                    ReadOnlyField(
                        label = stringResource(R.string.devices_manufacturerText),
                        value = data.manufacturer
                    )
                     // Device name
                    OutlinedTextField(
                        value = data.deviceName,
                        onValueChange = { viewModel.onDeviceNameChanged(it) },
                        label = { Text(stringResource(R.string.devices_deviceNameText)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Equipment Linking
                    EquipmentSection(data, viewModel)

                    // 3. Specialized Calibration
                    CalibrationSection(data, viewModel)

                    // 4. Power Meter Features
                    if (data.deviceType == DeviceType.BIKE_POWER) {
                        PowerMeterSection(data, viewModel)
                    }

                    // 5. Live Data Preview
                    liveData?.let { live ->
                        LivePreviewSection(live)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveChanges()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.OK))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EquipmentSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(data.onEquipmentResId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = if (data.linkedEquipment.isEmpty()) {
                    stringResource(R.string.devices_equipment_none)
                } else {
                    data.linkedEquipment.joinToString(", ")
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
        }
    }

    if (showDialog) {
        val selectedItems = remember { data.linkedEquipment.toMutableStateList() }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(data.onEquipmentResId)) },
            text = {
                Column {
                    data.availableEquipment.forEach { equipment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedItems.contains(equipment),
                                onCheckedChange = { checked ->
                                    if (checked) selectedItems.add(equipment)
                                    else selectedItems.remove(equipment)
                                }
                            )
                            Text(equipment)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEquipmentChanged(selectedItems.toList())
                    showDialog = false
                }) {
                    Text(stringResource(R.string.OK))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun CalibrationSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    when (data.deviceType) {
        DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE -> {
            WheelCircumferenceSelector(data, viewModel)
        }
        DeviceType.RUN_SPEED -> {
            RunCalibrationFactorSelector(data, viewModel)
        }
        DeviceType.BIKE_POWER -> {
            // Power meters might support wheel data
            if (data.powerFeatures?.wheelRevolutionDataSupported == true ||
                data.powerFeatures?.wheelSpeedDataSupported == true ||
                data.powerFeatures?.wheelDistanceDataSupported == true) {
                WheelCircumferenceSelector(data, viewModel)
            }
        }
        else -> {}
    }
}

@Composable
private fun WheelCircumferenceSelector(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showCorrectDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.devices_wheel_circumference),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = (data.wheelCircumference ?: 0).toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { valInt -> viewModel.onWheelCircumferenceChanged(valInt) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(painter = painterResource(id = R.drawable.ic_baseline_more_vert_24), contentDescription = null)
                        }
                    }
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    viewModel.wheelSizeNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text("$name (${viewModel.wheelSizeValues[index]} mm)") },
                            onClick = {
                                viewModel.getWheelCircumferenceForPosition(index)?.let {
                                    viewModel.onWheelCircumferenceChanged(it)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(onClick = { showCorrectDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = stringResource(R.string.edit_calibration_factor),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showCorrectDialog) {
        CorrectCalibrationDialog(
            title = stringResource(R.string.devices_correct_calibration_factor_title_bike),
            explanation = stringResource(R.string.devices_correct_calibration_explanation_bike),
            fieldName = stringResource(R.string.devices_wheel_circumference),
            originalFactor = (data.wheelCircumference ?: 2096).toDouble(),
            roundToInt = true,
            onFactorCalculated = { viewModel.onWheelCircumferenceChanged(it.toInt()) },
            onDismiss = { showCorrectDialog = false }
        )
    }
}

@Composable
private fun RunCalibrationFactorSelector(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showCorrectDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.devices_calibration_factor),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            val formattedValue = remember(data.calibrationFactor) {
                String.format(java.util.Locale.getDefault(), "%.4f", data.calibrationFactor ?: 1.0)
            }
            OutlinedTextField(
                value = formattedValue,
                onValueChange = { 
                    it.toDoubleOrNull()?.let { valDbl -> viewModel.onCalibrationFactorChanged(valDbl) }
                },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(onClick = { showCorrectDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = stringResource(R.string.edit_calibration_factor),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showCorrectDialog) {
        CorrectCalibrationDialog(
            title = stringResource(R.string.devices_correct_calibration_factor_title_run),
            explanation = stringResource(R.string.devices_correct_calibration_explanation_run),
            fieldName = stringResource(R.string.devices_calibration_factor),
            originalFactor = data.calibrationFactor ?: 1.0,
            roundToInt = false,
            onFactorCalculated = { viewModel.onCalibrationFactorChanged(it) },
            onDismiss = { showCorrectDialog = false }
        )
    }
}

@Composable
private fun CorrectCalibrationDialog(
    title: String,
    explanation: String,
    fieldName: String,
    originalFactor: Double,
    roundToInt: Boolean,
    onFactorCalculated: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var measuredDist by remember { mutableStateOf("10.0") }
    var correctDist by remember { mutableStateOf("10.0") }
    
    val newFactor = remember(measuredDist, correctDist) {
        val m = measuredDist.toDoubleOrNull() ?: 1.0
        val c = correctDist.toDoubleOrNull() ?: 1.0
        if (m > 0) originalFactor * (c / m) else originalFactor
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = measuredDist,
                    onValueChange = { measuredDist = it },
                    label = { Text(stringResource(R.string.devices_measuredDistanceText)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = correctDist,
                    onValueChange = { correctDist = it },
                    label = { Text(stringResource(R.string.devices_correctDistanceText)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ReadOnlyField(
                    label = "New $fieldName",
                    value = if (roundToInt) newFactor.toInt().toString() else String.format("%.4f", newFactor)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onFactorCalculated(newFactor)
                onDismiss()
            }) {
                Text(stringResource(R.string.OK))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun PowerMeterSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    data.powerFeatures?.let { features ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.bike_power__features),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            // List of supported features
            viewModel.getPowerFeaturesForDisplay(features).forEach { feature ->
                Text(
                    text = feature.name,
                    style = if (feature.isDeemphasized) 
                        MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic)
                    else 
                        MaterialTheme.typography.bodyMedium,
                    color = if (feature.isDeemphasized) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
            }

            // Bluetooth specific corrections
            if (data.protocol == Protocol.BLUETOOTH_LE && features.pedalPowerBalanceSupported) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.bike_power__pedal_power_balance_correction),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = features.doublePowerBalanceValues,
                        onCheckedChange = { viewModel.onDoublePowerBalanceValuesChanged(it) }
                    )
                    Text(stringResource(R.string.bike_power__double_power_balance_values))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = features.invertPowerBalanceValues,
                        onCheckedChange = { viewModel.onInvertPowerBalanceValuesChanged(it) }
                    )
                    Text(stringResource(R.string.bike_power__invert_power_balance_values))
                }
            }
        }
    }
}

@Composable
private fun LivePreviewSection(data: DeviceUiData) {
    if (data.isConnected) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.devices_live_sensor_data_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_device_available),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = data.mainValue ?: "--",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            data.allValues?.forEach { valStr ->
                Text(
                    text = valStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
