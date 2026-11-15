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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.AttFusedAltitudePreferences
import com.atrainingtracker.banalservice.devices.AttFusedPreferences
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.equipment.WheelSensorGuidanceDialog
import com.atrainingtracker.trainingtracker.ui.theme.TTColor

/**
 * Modernized bottom sheet dialog for editing device settings, calibration, and equipment links (REQ-UI-149, TST-UI-102).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceDialog(
    deviceId: Long,
    onDismiss: () -> Unit,
    viewModel: EditDeviceViewModel = viewModel()
) {
    val snapshot by viewModel.deviceSnapshot.collectAsStateWithLifecycle()
    val liveData by viewModel.deviceLiveData.collectAsStateWithLifecycle(initialValue = null)
    val deviceNotFound by viewModel.deviceNotFound.collectAsStateWithLifecycle()
    var showWheelGuidanceDialog by remember { mutableStateOf(false) }

    // Initialize data only once
    LaunchedEffect(deviceId) {
        viewModel.loadInitialDeviceData(deviceId)
    }

    LaunchedEffect(deviceNotFound) {
        if (deviceNotFound) {
            onDismiss()
        }
    }

    if (showWheelGuidanceDialog) {
        WheelSensorGuidanceDialog(onDismiss = { showWheelGuidanceDialog = false })
    }

    snapshot?.let { data ->
        AppModalBottomSheet(
            onDismissRequest = onDismiss,
            title = data.deviceName,
            iconPainter = painterResource(id = data.deviceTypeIconRes),
            iconTint = if (data.protocol == Protocol.SMARTPHONE)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            else
                Color.Unspecified,
            actions = {
                AppDialogActions.SaveCancel(
                    onSave = {
                        viewModel.saveChanges()
                        onDismiss()
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
                val currentDevice = liveData ?: data
                val isConnected = currentDevice.isConnected

                // Technical Status LED & Protocol details (Synchronized - REQ-UI-492.4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = CircleShape,
                            color = if (isConnected) TTColor.ConnectionStatusGreen else Color.LightGray,
                            tonalElevation = 2.dp
                        ) {}
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DeviceStatusRow(
                        device = currentDevice,
                        alpha = TTAlpha.Medium,
                        textStyle = MaterialTheme.typography.bodySmall,
                        isConnected = isConnected
                    )
                }

                if (currentDevice.manufacturer.isNotBlank()) {
                    Text(
                        text = currentDevice.manufacturer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Device name editor
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.devices_deviceNameText),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = data.deviceName,
                        onValueChange = { viewModel.onDeviceNameChanged(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Equipment Linking
                EquipmentSection(data, viewModel)

                // 3. Specialized Calibration
                CalibrationSection(data, viewModel)

                // 4. Power Meter Features
                if (data.deviceType == DeviceType.BIKE_POWER) {
                    PowerMeterSection(data, viewModel)
                }

                // 5. Specialized Altitude Explanations (ATT-5073, REQ-SEN-026)
                if (data.deviceType == DeviceType.ALTITUDE_FROM_PRESSURE || data.deviceType == DeviceType.ALTITUDE_FROM_POSITION) {
                    AltitudeExplanationSection(data.deviceType)
                }

                // 6. ATT Odometry Fusion Configuration & Guards (ATT-7751, REQ-SEN-028)
                if (data.deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED) {
                    AttFusedConfigSection()
                }

                // 7. ATT Altitude & Slope Fusion Configuration & Guards (ATT-6279, REQ-FIL-016)
                if (data.deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED) {
                    AttFusedAltitudeConfigSection()
                }

                // 8. Radar Tail Light Mode Control (ATT-7540, REQ-CON-031.5)
                if (data.deviceType == DeviceType.BIKE_RADAR) {
                    RadarLightControlSection(isConnected = isConnected)
                }

                // 9. Wheel Sensor Guidance (ATT-7752, REQ-SEN-029.4)
                if (shouldShowWheelSensorGuidance(data.deviceType)) {
                    WheelSensorGuidanceCard(onShowGuidance = { showWheelGuidanceDialog = true })
                }

                // 10. Polar H10 Internal Backup Recording (ATT-7762, REQ-SET-104)
                if (data.deviceType == DeviceType.HRM && isPolarH10Device(data)) {
                    PolarH10Section(data, viewModel)
                }

                // 11. Live Data Preview
                liveData?.let { live ->
                    LivePreviewSection(live)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val displayText = if (data.linkedEquipment.isEmpty()) {
            stringResource(R.string.devices_equipment_none)
        } else {
            data.linkedEquipment.joinToString(", ")
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
        ) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(data.onEquipmentResId)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Predicted Sport Types (Reactive technical mapping)
        if (data.linkedSportTypes.isNotEmpty()) {
            Text(
                text = "→ ${data.linkedSportTypes.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }

    if (showDialog) {
        val selectedItems = remember { data.linkedEquipment.toMutableStateList() }
        AppModalBottomSheet(
            onDismissRequest = { showDialog = false },
            title = stringResource(data.onEquipmentResId),
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = {
                    viewModel.onEquipmentChanged(selectedItems.toList())
                    showDialog = false
                }) {
                    Text(stringResource(R.string.OK))
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                data.availableEquipment.filter { it.isNotBlank() }.forEach { equipment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedItems.contains(equipment)) selectedItems.remove(equipment)
                                else selectedItems.add(equipment)
                            }
                    ) {
                        Checkbox(
                            checked = selectedItems.contains(equipment),
                            onCheckedChange = null
                        )
                        Text(
                            text = equipment,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelCircumferenceSelector(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showCorrectDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.devices_wheel_circumference),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = (data.wheelCircumference ?: 0).toString(),
                    onValueChange = { 
                        it.toIntOrNull()?.let { valInt -> viewModel.onWheelCircumferenceChanged(valInt) }
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    viewModel.wheelSizeNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text("$name (${viewModel.wheelSizeValues[index]} mm)") },
                            onClick = {
                                viewModel.getWheelCircumferenceForPosition(index)?.let {
                                    viewModel.onWheelCircumferenceChanged(it)
                                }
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
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

        val baselineMm = data.baselineCircumference?.let { (it * 1000).toInt() }
        if (baselineMm != null && baselineMm > 0) {
            Text(
                text = "Baseline: $baselineMm mm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = { viewModel.resetTirePressureBaseline() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.devices_reset_tire_pressure_baseline),
                style = MaterialTheme.typography.labelMedium
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunCalibrationFactorSelector(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    var showCorrectDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.devices_calibration_factor),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
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

@OptIn(ExperimentalMaterial3Api::class)
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

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = {
                onFactorCalculated(newFactor)
                onDismiss()
            }) {
                Text(stringResource(R.string.OK))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PowerMeterSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    data.powerFeatures?.let { features ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.bike_power__features),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

@Composable
private fun LivePreviewSection(data: DeviceUiData) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.devices_live_sensor_data_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (data.isConnected) {
            Text(
                text = data.mainValue ?: "--",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            data.allValues?.forEach { valStr ->
                Text(
                    text = valStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = stringResource(R.string.devices_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Educational information card detailing the operational principles and trade-offs (pros & cons)
 * of specialized altitude devices (ALTITUDE_FROM_PRESSURE and ALTITUDE_FROM_POSITION) (REQ-SEN-026.3, ATT-5073).
 */
@Composable
private fun AltitudeExplanationSection(deviceType: DeviceType) {
    val (howItWorksRes, prosConsRes) = when (deviceType) {
        DeviceType.ALTITUDE_FROM_PRESSURE -> Pair(
            R.string.device_altitude_pressure_how_it_works,
            R.string.device_altitude_pressure_pros_cons
        )
        DeviceType.ALTITUDE_FROM_POSITION -> Pair(
            R.string.device_altitude_position_how_it_works,
            R.string.device_altitude_position_pros_cons
        )
        else -> return
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.device_altitude_explanation_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.device_altitude_how_it_works_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(howItWorksRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.device_altitude_pros_cons_header),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(prosConsRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Educational information card and guarded input toggles for ATT Odometry Fusion
 * (REQ-SEN-028.3, REQ-SEN-028.4, ATT-7751).
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun AttFusedConfigSection() {
    val context = LocalContext.current
    var gnssChecked by remember { mutableStateOf(AttFusedPreferences.isGnssEnabled(context)) }
    var wheelChecked by remember { mutableStateOf(AttFusedPreferences.isWheelEnabled(context)) }
    var gyroChecked by remember { mutableStateOf(AttFusedPreferences.isGyroEnabled(context)) }
    var autoCalibChecked by remember { mutableStateOf(AttFusedPreferences.isAutoCalibEnabled(context)) }

    val gnssAvailable = remember { AttFusedPreferences.isGnssAvailable(context) }
    val wheelAvailable = remember { AttFusedPreferences.isWheelAvailable(context) }
    val gyroAvailable = remember { AttFusedPreferences.isGyroAvailable(context) }
    val autoCalibAvailable = remember { AttFusedPreferences.isAutoCalibAvailable(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Educational Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_altitude_explanation_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.device_altitude_how_it_works_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_att_fused_how_it_works),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.device_altitude_pros_cons_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_att_fused_pros_cons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Guarded Toggles Section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_gnss),
                    checked = gnssChecked,
                    enabled = gnssAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_gnss_hint_disabled),
                    onCheckedChange = {
                        gnssChecked = it
                        AttFusedPreferences.setGnssEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_wheel),
                    checked = wheelChecked,
                    enabled = wheelAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_wheel_hint_disabled),
                    onCheckedChange = {
                        wheelChecked = it
                        AttFusedPreferences.setWheelEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_gyro),
                    checked = gyroChecked,
                    enabled = gyroAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_gyro_hint_disabled),
                    onCheckedChange = {
                        gyroChecked = it
                        AttFusedPreferences.setGyroEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_autocalib),
                    checked = autoCalibChecked,
                    enabled = autoCalibAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_autocalib_hint_disabled),
                    onCheckedChange = {
                        autoCalibChecked = it
                        AttFusedPreferences.setAutoCalibEnabled(context, it)
                    }
                )
            }
        }
    }
}

/**
 * Educational information card and guarded input toggles for ATT Altitude & Slope Fusion
 * (REQ-FIL-016.5, ATT-6279).
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun AttFusedAltitudeConfigSection() {
    val context = LocalContext.current
    var baroChecked by remember { mutableStateOf(AttFusedAltitudePreferences.isBarometerEnabled(context)) }
    var demChecked by remember { mutableStateOf(AttFusedAltitudePreferences.isDemEnabled(context)) }
    var gpsChecked by remember { mutableStateOf(AttFusedAltitudePreferences.isGpsEnabled(context)) }
    var speedChecked by remember { mutableStateOf(AttFusedAltitudePreferences.isSpeedEnabled(context)) }

    val baroAvailable = remember { AttFusedAltitudePreferences.isBarometerAvailable(context) }
    val demAvailable = remember { AttFusedAltitudePreferences.isDemAvailable(context) }
    val gpsAvailable = remember { AttFusedAltitudePreferences.isGpsAvailable(context) }
    val speedAvailable = remember { AttFusedAltitudePreferences.isSpeedAvailable(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Educational Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_altitude_explanation_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.device_altitude_how_it_works_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_att_fused_alt_how_it_works),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.device_altitude_pros_cons_header),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.device_att_fused_alt_pros_cons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Guarded Toggles Section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.devices_live_sensor_data_header),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_baro),
                    checked = baroChecked,
                    enabled = baroAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_baro_hint_disabled),
                    onCheckedChange = {
                        baroChecked = it
                        AttFusedAltitudePreferences.setBarometerEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_dem),
                    checked = demChecked,
                    enabled = demAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_dem_hint_disabled),
                    onCheckedChange = {
                        demChecked = it
                        AttFusedAltitudePreferences.setDemEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_gps),
                    checked = gpsChecked,
                    enabled = gpsAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_gps_hint_disabled),
                    onCheckedChange = {
                        gpsChecked = it
                        AttFusedAltitudePreferences.setGpsEnabled(context, it)
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_speed),
                    checked = speedChecked,
                    enabled = speedAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_speed_hint_disabled),
                    onCheckedChange = {
                        speedChecked = it
                        AttFusedAltitudePreferences.setSpeedEnabled(context, it)
                    }
                )
            }
        }
    }
}

@Composable
private fun AttFusedToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    disabledHint: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Switch(
                checked = checked && enabled,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
        if (!enabled) {
            Text(
                text = disabledHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Live tail light mode selector for connected radar devices supporting light control (REQ-CON-031.5, ATT-7540).
 *
 * Mode selections are dispatched immediately via [RadarLightController.setManualLightMode] and do not
 * modify [EditDeviceViewModel] draft state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun RadarLightControlSection(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val isLightControlAvailable by RadarLightController.isLightControlAvailable.collectAsStateWithLifecycle()
    if (!isConnected || !isLightControlAvailable) {
        return
    }

    val currentMode by RadarLightController.currentMode.collectAsStateWithLifecycle()
    val isStvzo by RadarLightController.isStvzo.collectAsStateWithLifecycle()
    val availableModes = remember(isStvzo) { RadarLightMode.getAvailableModes(isStvzo) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.radar_light_sheet_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isStvzo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TTColor.RadarDisconnectedAmber.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.radar_light_stvzo_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                availableModes.forEachIndexed { index, mode ->
                    val isSelected = mode == currentMode
                    SegmentedButton(
                        selected = isSelected,
                        onClick = { RadarLightController.setManualLightMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = availableModes.size),
                        icon = {},
                        modifier = Modifier.testTag("RadarLightMode_${mode.name}")
                    ) {
                        Text(
                            text = stringResource(mode.titleResId),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            availableModes.forEach { mode ->
                val isSelected = mode == currentMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { RadarLightController.setManualLightMode(mode) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("RadarLightMode_${mode.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotColor = when (mode) {
                        RadarLightMode.OFF -> MaterialTheme.colorScheme.outline
                        RadarLightMode.SOLID -> TTColor.RadarThreatRed
                        RadarLightMode.DAY_FLASH -> TTColor.RadarThreatAmber
                        RadarLightMode.NIGHT_FLASH -> TTColor.RadarThreatAmber.copy(alpha = 0.7f)
                        RadarLightMode.PELOTON -> TTColor.RadarThreatRed.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(mode.titleResId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    RadioButton(
                        selected = isSelected,
                        onClick = { RadarLightController.setManualLightMode(mode) }
                    )
                }
            }
        }
    }
}

/**
 * In-app educational guidance card for wheel speed sensors and fused odometry (REQ-SEN-029.4, ATT-7752).
 */
@Composable
internal fun WheelSensorGuidanceCard(
    onShowGuidance: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onShowGuidance,
        modifier = modifier
            .fillMaxWidth()
            .testTag("edit_device_wheel_guidance_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.equipment_guidance_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onShowGuidance,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("edit_device_wheel_guidance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.equipment_guidance_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(R.string.equipment_guidance_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Determines whether the wheel sensor guidance entry point should be displayed for the given [deviceType]
 * (REQ-SEN-029.4, ATT-7752).
 */
fun shouldShowWheelSensorGuidance(deviceType: DeviceType): Boolean =
    deviceType == DeviceType.BIKE_SPEED ||
    deviceType == DeviceType.BIKE_SPEED_AND_CADENCE ||
    deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED

fun isPolarH10Device(data: DeviceUiData): Boolean {
    val name = data.deviceName.lowercase(java.util.Locale.ROOT)
    val manufacturer = data.manufacturer.lowercase(java.util.Locale.ROOT)
    return name.contains("polar h10") || (name.contains("h10") && manufacturer.contains("polar")) || (data.deviceType == DeviceType.HRM && name.startsWith("polar h10", ignoreCase = true))
}

@Composable
private fun PolarH10Section(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    val isAndroid13Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttFusedToggleRow(
                title = stringResource(R.string.device_polar_h10_backup_recording_title),
                checked = data.polarH10OfflineRecording,
                enabled = isAndroid13Plus,
                disabledHint = stringResource(R.string.device_polar_h10_backup_recording_requires_android_13),
                onCheckedChange = { viewModel.onPolarH10OfflineRecordingChanged(it) }
            )
            Text(
                text = stringResource(R.string.device_polar_h10_backup_recording_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            )
        }
    }
}


