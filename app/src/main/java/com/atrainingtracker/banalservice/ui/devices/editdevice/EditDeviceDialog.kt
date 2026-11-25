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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.AttFusedAltitudePreferences
import com.atrainingtracker.banalservice.devices.AttFusedPreferences
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinator
import com.atrainingtracker.banalservice.devices.radar.RadarBluetoothBondingHelper
import com.atrainingtracker.banalservice.devices.radar.RadarBondActionResult
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.devicedata.isWheelSpeedSensor
import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.components.radar.RadarLightModeGrid
import com.atrainingtracker.trainingtracker.ui.equipment.WheelPositionSelectionDialog
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
    val persistedIdentity by viewModel.persistedDeviceIdentity.collectAsStateWithLifecycle()
    val liveData by viewModel.deviceLiveData.collectAsStateWithLifecycle(initialValue = null)
    val wheelCircumferenceHistory by viewModel.wheelCircumferenceHistory.collectAsStateWithLifecycle()
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

    val handleDismiss: () -> Unit = {
        viewModel.discardDraft()
        onDismiss()
    }

    snapshot?.let { data ->
        AppModalBottomSheet(
            onDismissRequest = handleDismiss,
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
                    onCancel = handleDismiss,
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
                CalibrationSection(data, wheelCircumferenceHistory, viewModel)

                // 4. Power Meter Features
                if (data.deviceType == DeviceType.BIKE_POWER) {
                    PowerMeterSection(data, viewModel)
                }

                // 5. Specialized Altitude Explanations (ATT-5073, REQ-SEN-026)
                if (data.deviceType == DeviceType.ALTITUDE_FROM_PRESSURE || data.deviceType == DeviceType.ALTITUDE_FROM_POSITION) {
                    AltitudeExplanationSection(data.deviceType)
                }

                // 6. ATT Odometry Fusion Configuration & Guards (ATT-7751, REQ-SEN-028, REQ-UI-2799.2)
                if (data.deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED) {
                    AttFusedConfigSection(viewModel = viewModel)
                }

                // 7. ATT Altitude & Slope Fusion Configuration & Guards (ATT-6279, REQ-FIL-016, REQ-UI-2799.2)
                if (data.deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED) {
                    AttFusedAltitudeConfigSection(viewModel = viewModel)
                }

                // 7b. Post-Workout ERTS Smoothing Toggle for ATT Fused Sensors (ATT-8113, REQ-FIL-017.5, REQ-UI-2799.2)
                if (shouldShowPostWorkoutErtsToggle(data.deviceType)) {
                    PostWorkoutErtsSmoothingSection(viewModel = viewModel)
                }

                // 8. Radar V2 Bonding Hint & Tail Light Mode Control (ATT-7540, ATT-8450, ATT-8125, ATT-8731, REQ-CON-031.5, REQ-CON-048, REQ-CON-061.3, REQ-UI-2799.2)
                if (data.deviceType == DeviceType.BIKE_RADAR) {
                    if (data.protocol == Protocol.BLUETOOTH_LE) {
                        val bondingContext = LocalContext.current
                        val bondingStatus by remember { RadarStateRepository.observeV2BondingStatus() }
                            .collectAsStateWithLifecycle(initialValue = RadarStateRepository.getV2BondingStatus())
                        val radarIdentityId = (persistedIdentity ?: data).id
                        val radarMac = remember(radarIdentityId) {
                            runCatching { viewModel.getBluetoothMacAddress(radarIdentityId) }.getOrNull()
                        }
                        var bondActionResult by remember(radarIdentityId, bondingStatus.isRequired) {
                            mutableStateOf<RadarBondActionResult?>(null)
                        }
                        if (shouldShowRadarV2BondingHint(data.deviceType, data.protocol, bondingStatus.isRequired, bondingStatus.deviceAddress, radarMac)) {
                            RadarV2BondingHintCard(
                                actionResult = bondActionResult,
                                onPairClick = {
                                    bondActionResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
                                        context = bondingContext,
                                        macAddress = bondingStatus.deviceAddress ?: radarMac
                                    )
                                }
                            )
                        }
                    }
                    RadarLightControlSection(
                        isConnected = isConnected,
                        deviceName = currentDevice.deviceName,
                        viewModel = viewModel
                    )
                }

                // 9. Wheel Sensor Guidance (ATT-7752, REQ-SEN-029.4)
                if (shouldShowWheelSensorGuidance(data.deviceType)) {
                    WheelSensorGuidanceCard(onShowGuidance = { showWheelGuidanceDialog = true })
                }

                // 10. Polar H10 & Verity Sense Internal Backup Recording (ATT-7762, ATT-7923, ATT-8949, REQ-SET-104, REQ-SET-108, REQ-CON-049.4)
                if (data.deviceType == DeviceType.HRM && data.protocol == Protocol.BLUETOOTH_LE) {
                    PolarOfflineDeviceSection(
                        data = data,
                        persistedIdentity = persistedIdentity ?: data,
                        viewModel = viewModel
                    )
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

@Composable
private fun EquipmentSection(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    EquipmentSectionContent(
        data = data,
        onEquipmentChanged = viewModel::onEquipmentChanged
    )
}

/**
 * Formats the equipment summary field text, appending localized [WheelPosition] labels
 * for wheel speed sensors (`REQ-SEN-031.1`) while preserving plain names for other sensors.
 */
internal fun formatEquipmentSummaryText(
    linkedEquipment: List<String>,
    linkedWheelPositions: Map<String, WheelPosition>,
    isWheelSensor: Boolean,
    frontLabel: String,
    rearLabel: String,
    unspecifiedLabel: String,
    noneLabel: String
): String {
    if (linkedEquipment.isEmpty()) return noneLabel
    if (!isWheelSensor) return linkedEquipment.joinToString(", ")
    return linkedEquipment.joinToString(", ") { bike ->
        val label = when (linkedWheelPositions[bike] ?: WheelPosition.UNSPECIFIED) {
            WheelPosition.FRONT -> frontLabel
            WheelPosition.REAR -> rearLabel
            WheelPosition.UNSPECIFIED -> unspecifiedLabel
        }
        "$bike ($label)"
    }
}

@Composable
private fun wheelPositionLabel(position: WheelPosition): String = when (position) {
    WheelPosition.FRONT -> stringResource(R.string.equipment_wheel_position_front)
    WheelPosition.REAR -> stringResource(R.string.equipment_wheel_position_rear)
    WheelPosition.UNSPECIFIED -> stringResource(R.string.equipment_wheel_position_unspecified)
}

/**
 * Stateless Compose section for equipment summary and bike-linking bottom sheet (`REQ-SEN-031`).
 * For [DeviceType.BIKE_SPEED] and [DeviceType.BIKE_SPEED_AND_CADENCE], displays per-bike
 * [WheelPosition] and opens [WheelPositionSelectionDialog] to stage draft positions until Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentSectionContent(
    data: DeviceUiData,
    onEquipmentChanged: (List<String>, Map<String, WheelPosition>) -> Unit,
    initialShowSheet: Boolean = false
) {
    var showDialog by remember(initialShowSheet) { mutableStateOf(initialShowSheet) }
    val isWheelSensor = isWheelSpeedSensor(data.deviceType)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val displayText = formatEquipmentSummaryText(
            linkedEquipment = data.linkedEquipment,
            linkedWheelPositions = data.linkedWheelPositions,
            isWheelSensor = isWheelSensor,
            frontLabel = stringResource(R.string.equipment_wheel_position_front),
            rearLabel = stringResource(R.string.equipment_wheel_position_rear),
            unspecifiedLabel = stringResource(R.string.equipment_wheel_position_unspecified),
            noneLabel = stringResource(R.string.devices_equipment_none)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("equipment_summary_trigger")
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("equipment_summary_field")
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
        val selectedItems = remember(showDialog) { data.linkedEquipment.toMutableStateList() }
        val draftPositions = remember(showDialog) {
            mutableStateMapOf<String, WheelPosition>().apply {
                putAll(data.linkedWheelPositions)
            }
        }
        var wheelDialogBike by remember { mutableStateOf<String?>(null) }

        AppModalBottomSheet(
            onDismissRequest = {
                wheelDialogBike = null
                showDialog = false
            },
            title = stringResource(data.onEquipmentResId),
            actions = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        wheelDialogBike = null
                        showDialog = false
                    },
                    modifier = Modifier.testTag("equipment_sheet_cancel")
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onEquipmentChanged(selectedItems.toList(), draftPositions.toMap())
                        wheelDialogBike = null
                        showDialog = false
                    },
                    modifier = Modifier.testTag("equipment_sheet_ok")
                ) {
                    Text(stringResource(R.string.OK))
                }
            }
        ) {
            EquipmentSheetListContent(
                availableEquipment = data.availableEquipment,
                selectedItems = selectedItems,
                draftPositions = draftPositions,
                isWheelSensor = isWheelSensor,
                onSimpleToggle = { equipment ->
                    if (selectedItems.contains(equipment)) selectedItems.remove(equipment)
                    else selectedItems.add(equipment)
                },
                onWheelBikeClick = { equipment ->
                    wheelDialogBike = equipment
                }
            )
        }

        wheelDialogBike?.let { bike ->
            WheelPositionSelectionDialog(
                equipmentName = bike,
                sensorName = data.deviceName,
                currentPosition = draftPositions[bike] ?: WheelPosition.UNSPECIFIED,
                isLinked = selectedItems.contains(bike),
                conflictHints = data.wheelPositionConflicts[bike].orEmpty(),
                onSelectPosition = { position ->
                    if (!selectedItems.contains(bike)) {
                        selectedItems.add(bike)
                    }
                    draftPositions[bike] = position
                    wheelDialogBike = null
                },
                onUnlink = {
                    selectedItems.remove(bike)
                    draftPositions.remove(bike)
                    wheelDialogBike = null
                },
                onDismiss = {
                    wheelDialogBike = null
                }
            )
        }
    }
}

/**
 * Renders the selectable equipment rows inside the Equipment bottom sheet (`REQ-SEN-031`).
 * Wheel speed sensors display mounting position and prompt via [WheelPositionSelectionDialog];
 * non-wheel sensors preserve simple checkbox toggling.
 */
@Composable
internal fun EquipmentSheetListContent(
    availableEquipment: List<String>,
    selectedItems: List<String>,
    draftPositions: Map<String, WheelPosition>,
    isWheelSensor: Boolean,
    onSimpleToggle: (String) -> Unit,
    onWheelBikeClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableEquipment.filter { it.isNotBlank() }.forEach { equipment ->
            val isLinked = selectedItems.contains(equipment)
            if (isWheelSensor) {
                val position = draftPositions[equipment] ?: WheelPosition.UNSPECIFIED
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("equipment_sheet_row_$equipment")
                        .clickable { onWheelBikeClick(equipment) }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isLinked,
                        onCheckedChange = null
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 8.dp)
                    ) {
                        Text(
                            text = equipment,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLinked) {
                            Text(
                                text = wheelPositionLabel(position),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (position == WheelPosition.UNSPECIFIED) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                    if (isLinked && (position == WheelPosition.FRONT || position == WheelPosition.REAR)) {
                        val badgeRes = if (position == WheelPosition.FRONT) {
                            R.string.equipment_wheel_position_front_badge
                        } else {
                            R.string.equipment_wheel_position_rear_badge
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
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
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("equipment_sheet_row_$equipment")
                        .clickable { onSimpleToggle(equipment) }
                ) {
                    Checkbox(
                        checked = isLinked,
                        onCheckedChange = null
                    )
                    Text(
                        text = equipment,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CalibrationSection(
    data: DeviceUiData,
    wheelCircumferenceHistory: List<WheelCircumferenceRecord>,
    viewModel: EditDeviceViewModel
) {
    when (data.deviceType) {
        DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE -> {
            WheelCircumferenceSection(
                wheelCircumferenceMm = data.wheelCircumference,
                baselineCircumferenceMeters = data.baselineCircumference,
                wheelSizeNames = viewModel.wheelSizeNames,
                wheelSizeValues = viewModel.wheelSizeValues,
                historyRecords = wheelCircumferenceHistory,
                onWheelCircumferenceChanged = viewModel::onWheelCircumferenceChanged,
                onResetBaseline = viewModel::resetTirePressureBaseline
            )
        }
        DeviceType.RUN_SPEED -> {
            RunCalibrationFactorSelector(data, viewModel)
        }
        DeviceType.BIKE_POWER -> {
            // Power meters might support wheel data
            if (data.powerFeatures?.wheelRevolutionDataSupported == true ||
                data.powerFeatures?.wheelSpeedDataSupported == true ||
                data.powerFeatures?.wheelDistanceDataSupported == true) {
                WheelCircumferenceSection(
                    wheelCircumferenceMm = data.wheelCircumference,
                    baselineCircumferenceMeters = data.baselineCircumference,
                    wheelSizeNames = viewModel.wheelSizeNames,
                    wheelSizeValues = viewModel.wheelSizeValues,
                    historyRecords = wheelCircumferenceHistory,
                    onWheelCircumferenceChanged = viewModel::onWheelCircumferenceChanged,
                    onResetBaseline = viewModel::resetTirePressureBaseline
                )
            }
        }
        else -> {}
    }
}

@Composable
private fun RunCalibrationFactorSelector(data: DeviceUiData, viewModel: EditDeviceViewModel) {
    RunCalibrationFactorContent(
        calibrationFactor = data.calibrationFactor,
        onCalibrationFactorChanged = viewModel::onCalibrationFactorChanged
    )
}

/**
 * Parses a decimal string accepting both dot (`.`) and comma (`,`) decimal separators
 * regardless of active device locale (`REQ-UI-2799.2`, `ATT-8125`).
 */
internal fun parseLocaleDecimal(input: String): Double? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val parsed = normalized.toDoubleOrNull() ?: return null
    return parsed.takeIf { it.isFinite() }
}

/**
 * Formats a calibration factor with 4 decimal places for the active [locale] (`REQ-UI-2799.2`).
 */
internal fun formatCalibrationFactor(
    value: Double?,
    locale: java.util.Locale = java.util.Locale.getDefault()
): String = String.format(locale, "%.4f", value ?: 1.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunCalibrationFactorContent(
    calibrationFactor: Double?,
    onCalibrationFactorChanged: (Double) -> Unit
) {
    var showCorrectDialog by remember { mutableStateOf(false) }
    var textBuffer by rememberSaveable {
        mutableStateOf(formatCalibrationFactor(calibrationFactor))
    }

    LaunchedEffect(calibrationFactor) {
        val target = calibrationFactor ?: 1.0
        val parsedCurrent = parseLocaleDecimal(textBuffer)
        if (parsedCurrent == null || kotlin.math.abs(parsedCurrent - target) > 1e-9) {
            textBuffer = formatCalibrationFactor(target)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.devices_calibration_factor),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textBuffer,
                onValueChange = { input ->
                    textBuffer = input
                    parseLocaleDecimal(input)?.takeIf { it > 0.0 }?.let { valDbl ->
                        onCalibrationFactorChanged(valDbl)
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("run_calibration_factor_input")
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
            originalFactor = calibrationFactor ?: 1.0,
            roundToInt = false,
            onFactorCalculated = onCalibrationFactorChanged,
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
    var measuredDist by remember { mutableStateOf(String.format(java.util.Locale.getDefault(), "%.1f", 10.0)) }
    var correctDist by remember { mutableStateOf(String.format(java.util.Locale.getDefault(), "%.1f", 10.0)) }

    val newFactor = remember(measuredDist, correctDist) {
        val m = parseLocaleDecimal(measuredDist) ?: 1.0
        val c = parseLocaleDecimal(correctDist) ?: 1.0
        if (m > 0.0) originalFactor * (c / m) else originalFactor
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
                value = if (roundToInt) newFactor.toInt().toString() else formatCalibrationFactor(newFactor)
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
 * (REQ-SEN-028.3, REQ-SEN-028.4, REQ-UI-2799.2, ATT-7751, ATT-8125).
 * Toggle changes are staged in [EditDeviceViewModel] draft state and persisted strictly on Save.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun AttFusedConfigSection(viewModel: EditDeviceViewModel) {
    val context = LocalContext.current
    val draftState by viewModel.attFusedDraft.collectAsStateWithLifecycle()
    val effectiveDraft = draftState ?: remember {
        AttFusedDraftConfig(
            gnssEnabled = AttFusedPreferences.isGnssEnabled(context),
            wheelEnabled = AttFusedPreferences.isWheelEnabled(context),
            gyroEnabled = AttFusedPreferences.isGyroEnabled(context),
            autoCalibEnabled = AttFusedPreferences.isAutoCalibEnabled(context)
        )
    }

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

        // Guarded Toggles Section (draft-staged until Save)
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
                    checked = effectiveDraft.gnssEnabled,
                    enabled = gnssAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_gnss_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedConfigChanged { it.copy(gnssEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_wheel),
                    checked = effectiveDraft.wheelEnabled,
                    enabled = wheelAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_wheel_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedConfigChanged { it.copy(wheelEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_gyro),
                    checked = effectiveDraft.gyroEnabled,
                    enabled = gyroAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_gyro_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedConfigChanged { it.copy(gyroEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_toggle_autocalib),
                    checked = effectiveDraft.autoCalibEnabled,
                    enabled = autoCalibAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_toggle_autocalib_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedConfigChanged { it.copy(autoCalibEnabled = enabled) }
                    }
                )
            }
        }
    }
}

/**
 * Educational information card and guarded input toggles for ATT Altitude & Slope Fusion
 * (REQ-FIL-016.5, REQ-UI-2799.2, ATT-6279, ATT-8125).
 * Toggle changes are staged in [EditDeviceViewModel] draft state and persisted strictly on Save.
 */
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun AttFusedAltitudeConfigSection(viewModel: EditDeviceViewModel) {
    val context = LocalContext.current
    val draftState by viewModel.attFusedAltitudeDraft.collectAsStateWithLifecycle()
    val effectiveDraft = draftState ?: remember {
        AttFusedAltitudeDraftConfig(
            barometerEnabled = AttFusedAltitudePreferences.isBarometerEnabled(context),
            demEnabled = AttFusedAltitudePreferences.isDemEnabled(context),
            gpsEnabled = AttFusedAltitudePreferences.isGpsEnabled(context),
            speedEnabled = AttFusedAltitudePreferences.isSpeedEnabled(context)
        )
    }

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

        // Guarded Toggles Section (draft-staged until Save)
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
                    checked = effectiveDraft.barometerEnabled,
                    enabled = baroAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_baro_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedAltitudeConfigChanged { it.copy(barometerEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_dem),
                    checked = effectiveDraft.demEnabled,
                    enabled = demAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_dem_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedAltitudeConfigChanged { it.copy(demEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_gps),
                    checked = effectiveDraft.gpsEnabled,
                    enabled = gpsAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_gps_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedAltitudeConfigChanged { it.copy(gpsEnabled = enabled) }
                    }
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                AttFusedToggleRow(
                    title = stringResource(R.string.device_att_fused_alt_toggle_speed),
                    checked = effectiveDraft.speedEnabled,
                    enabled = speedAvailable,
                    disabledHint = stringResource(R.string.device_att_fused_alt_toggle_speed_hint_disabled),
                    onCheckedChange = { enabled ->
                        viewModel.onAttFusedAltitudeConfigChanged { it.copy(speedEnabled = enabled) }
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
                modifier = Modifier.weight(1f).padding(end = 8.dp),
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
 * Returns `true` exclusively for the two virtual ATT Fused sensors that benefit from
 * post-workout Extended Rauch-Tung-Striebel (`ERTS`) smoothing (`REQ-FIL-017.5`, `ATT-8113`).
 */
internal fun shouldShowPostWorkoutErtsToggle(deviceType: DeviceType): Boolean =
    deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED ||
    deviceType == DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED

/**
 * Stateful section binding the Post-Workout ERTS smoothing switch to [EditDeviceViewModel]
 * draft/persisted state backed by `TuningPreferencesDataStore.KEY_POST_WORKOUT_ERTS_SMOOTHING`
 * (`REQ-FIL-017.5`, `REQ-UI-2799.2`, `ATT-8113`).
 */
@Composable
private fun PostWorkoutErtsSmoothingSection(
    viewModel: EditDeviceViewModel,
    modifier: Modifier = Modifier
) {
    val persistedEnabled by viewModel.postWorkoutErtsPersisted.collectAsStateWithLifecycle()
    val draftEnabled by viewModel.postWorkoutErtsDraft.collectAsStateWithLifecycle()
    val effectiveEnabled = draftEnabled ?: persistedEnabled

    PostWorkoutErtsSmoothingCard(
        checked = effectiveEnabled,
        onCheckedChange = viewModel::onPostWorkoutErtsSmoothingChanged,
        modifier = modifier
    )
}

/**
 * Stateless Material 3 card exposing the global Post-Workout ERTS smoothing toggle inside
 * [EditDeviceDialog] for ATT Fused sensors, matching the shared preference in
 * `SensorsGpsFilterSection` (`REQ-FIL-017.5`, `ATT-8113`).
 */
@Composable
internal fun PostWorkoutErtsSmoothingCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("edit_device_post_workout_erts_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tuning_post_workout_erts_smoothing_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag("edit_device_post_workout_erts_switch")
                )
            }
            Text(
                text = stringResource(R.string.tuning_post_workout_erts_smoothing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Post-Workout ERTS Card Light", showBackground = true)
@Composable
private fun PostWorkoutErtsSmoothingCardLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PostWorkoutErtsSmoothingCard(checked = true, onCheckedChange = {})
        }
    }
}

@Preview(
    name = "Post-Workout ERTS Card Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun PostWorkoutErtsSmoothingCardDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            PostWorkoutErtsSmoothingCard(checked = false, onCheckedChange = {})
        }
    }
}

/**
 * Returns `true` when [deviceName] explicitly identifies a radar-only unit without tail light hardware
 * (such as Garmin Varia RVR315) (`REQ-CON-048.1`, `ATT-8450`).
 */
internal fun isKnownRadarWithoutLight(deviceName: String?): Boolean {
    if (deviceName.isNullOrBlank()) return false
    val upper = deviceName.uppercase(java.util.Locale.US)
    return upper.contains("RVR315") || upper.contains("RVR 315")
}

/**
 * Determines whether [RadarLightControlSection] should be visible for a [DeviceType.BIKE_RADAR] device
 * (`REQ-CON-048.1`, `ATT-8450`).
 */
internal fun shouldShowRadarLightControlSection(
    isConnected: Boolean,
    isLightControlAvailable: Boolean,
    deviceName: String?
): Boolean {
    if (isConnected) return isLightControlAvailable
    return !isKnownRadarWithoutLight(deviceName)
}

/**
 * Tail light mode selector for radar devices (`REQ-CON-031.5`, `REQ-CON-048`, `REQ-UI-2799.2`,
 * `ATT-7540`, `ATT-8450`, `ATT-8125`).
 *
 * Remains visible and interactive offline (unless known radar-only such as RVR315 or connected without
 * light support), hydrating persisted mode preferences on cold start and staging edits inside
 * [EditDeviceViewModel] until Save (discarded on Cancel with zero DataStore/controller mutation).
 */
@Composable
private fun RadarLightControlSection(
    isConnected: Boolean,
    deviceName: String?,
    viewModel: EditDeviceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        RadarLightController.hydrateFromPreferencesAsync(context)
    }

    val isLightControlAvailable by RadarLightController.isLightControlAvailable.collectAsStateWithLifecycle()
    if (!shouldShowRadarLightControlSection(isConnected, isLightControlAvailable, deviceName)) {
        return
    }

    val isAutoMode by RadarLightController.isAutoMode.collectAsStateWithLifecycle()
    val currentMode by RadarLightController.currentMode.collectAsStateWithLifecycle()
    val preferredMode by RadarLightController.preferredMode.collectAsStateWithLifecycle()
    val isStvzo by RadarLightController.isStvzo.collectAsStateWithLifecycle()
    val radarDraft by viewModel.radarLightDraft.collectAsStateWithLifecycle()
    val isOffline = !isConnected
    val baseEffectiveMode = if (isOffline) preferredMode else currentMode
    val displayedIsAutoMode = radarDraft?.isAutoMode ?: isAutoMode
    val displayedEffectiveMode = radarDraft?.mode ?: baseEffectiveMode

    RadarLightControlSectionContent(
        isOffline = isOffline,
        isAutoMode = displayedIsAutoMode,
        effectiveMode = displayedEffectiveMode,
        isStvzo = isStvzo,
        onSelectAuto = { viewModel.onRadarLightSelectionChanged(isAutoMode = true, mode = displayedEffectiveMode) },
        onSelectMode = { mode -> viewModel.onRadarLightSelectionChanged(isAutoMode = false, mode = mode) },
        modifier = modifier
    )
}

/**
 * Stateless UI content for [RadarLightControlSection] (`REQ-CON-048`, `REQ-CON-047.2`, `ATT-8450`, `ATT-8678`).
 */
@Composable
internal fun RadarLightControlSectionContent(
    isOffline: Boolean,
    isAutoMode: Boolean,
    effectiveMode: RadarLightMode,
    isStvzo: Boolean,
    onSelectAuto: () -> Unit,
    onSelectMode: (RadarLightMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableModes = remember(isStvzo) { RadarLightMode.getAvailableModes(isStvzo) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RadarLightControlSection"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.radar_light_sheet_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("RadarLightOfflineHint"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.radar_light_offline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RadarLightModeGrid(
            currentMode = effectiveMode,
            availableModes = availableModes,
            isStvzo = isStvzo,
            isAutoMode = isAutoMode,
            showActiveModeSubtitle = !isOffline,
            onModeSelected = onSelectMode,
            onAutoSelected = onSelectAuto
        )
    }
}

@Preview(showBackground = true, name = "Offline Multi-Mode Light")
@Composable
private fun RadarLightControlSectionOfflineMultiModeLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarLightControlSectionContent(
                isOffline = true,
                isAutoMode = false,
                effectiveMode = RadarLightMode.SOLID,
                isStvzo = false,
                onSelectAuto = {},
                onSelectMode = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Offline Multi-Mode Dark")
@Composable
private fun RadarLightControlSectionOfflineMultiModeDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarLightControlSectionContent(
                isOffline = true,
                isAutoMode = true,
                effectiveMode = RadarLightMode.DAY_FLASH,
                isStvzo = false,
                onSelectAuto = {},
                onSelectMode = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Offline StVZO Light")
@Composable
private fun RadarLightControlSectionOfflineStvzoLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarLightControlSectionContent(
                isOffline = true,
                isAutoMode = false,
                effectiveMode = RadarLightMode.SOLID,
                isStvzo = true,
                onSelectAuto = {},
                onSelectMode = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Offline StVZO Dark")
@Composable
private fun RadarLightControlSectionOfflineStvzoDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
            RadarLightControlSectionContent(
                isOffline = true,
                isAutoMode = true,
                effectiveMode = RadarLightMode.OFF,
                isStvzo = true,
                onSelectAuto = {},
                onSelectMode = {}
            )
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

/**
 * Returns true if [data] identifies a BLE Polar H10 chest strap via hardware DIS/SDK metadata or
 * guarded legacy fallback (`REQ-CON-049.4`). Non-BLE protocols (e.g., ANT+) always return `false`.
 */
@JvmOverloads
fun isPolarH10Device(
    data: DeviceUiData,
    context: android.content.Context? = null,
    macAddress: String? = null
): Boolean {
    if (data.protocol != Protocol.BLUETOOTH_LE) return false
    return PolarOfflineGattCoordinator.isPolarH10Device(
        context = context,
        name = data.deviceName,
        manufacturer = data.manufacturer,
        macAddress = macAddress
    )
}

/**
 * Returns true if [data] identifies a BLE Polar Verity Sense / compatible optical Polar offline HR
 * sensor (`ATT-7923`, `REQ-CON-049.4`). Non-BLE protocols (e.g., ANT+) always return `false`.
 */
@JvmOverloads
fun isPolarVeritySenseDevice(
    data: DeviceUiData,
    context: android.content.Context? = null,
    macAddress: String? = null
): Boolean {
    if (data.protocol != Protocol.BLUETOOTH_LE) return false
    return PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
        context = context,
        name = data.deviceName,
        manufacturer = data.manufacturer,
        macAddress = macAddress
    )
}

@Composable
private fun PolarOfflineDeviceSection(
    data: DeviceUiData,
    persistedIdentity: DeviceUiData,
    viewModel: EditDeviceViewModel
) {
    val polarMac = if (persistedIdentity.protocol == Protocol.BLUETOOTH_LE) {
        remember(persistedIdentity.id) {
            try {
                viewModel.getBluetoothMacAddress(persistedIdentity.id)
            } catch (_: Throwable) {
                null
            }
        }
    } else {
        null
    }
    PolarOfflineDeviceSectionContent(
        data = data,
        persistedIdentity = persistedIdentity,
        polarMac = polarMac,
        onOfflineRecordingChanged = viewModel::onPolarH10OfflineRecordingChanged,
        onLiveImuEnabledChanged = viewModel::onPolarLiveImuEnabledChanged
    )
}

/**
 * Stateless Polar backup & live-IMU toggle section gated on [persistedIdentity] (immutable pre-edit
 * device identity) and hardware capability metadata so live keystroke drafts in the rename field
 * cannot falsely enable or hide Polar options (`REQ-CON-049.4`).
 */
@Composable
internal fun PolarOfflineDeviceSectionContent(
    data: DeviceUiData,
    persistedIdentity: DeviceUiData,
    polarMac: String?,
    onOfflineRecordingChanged: (Boolean) -> Unit,
    onLiveImuEnabledChanged: (Boolean) -> Unit
) {
    val dialogContext = LocalContext.current
    val isH10 = isPolarH10Device(persistedIdentity, dialogContext, polarMac)
    val isVeritySense = !isH10 && isPolarVeritySenseDevice(persistedIdentity, dialogContext, polarMac)
    if (isH10 || isVeritySense) {
        val isAndroid13Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PolarBackupRecordingCard(
                checked = data.polarH10OfflineRecording,
                isVeritySense = isVeritySense,
                isAndroid13Plus = isAndroid13Plus,
                onCheckedChange = onOfflineRecordingChanged
            )
            PolarLiveImuCard(
                checked = data.polarLiveImuEnabled,
                isVeritySense = isVeritySense,
                isAndroid13Plus = isAndroid13Plus,
                onCheckedChange = onLiveImuEnabledChanged
            )
        }
    }
}

/**
 * Stateless Material 3 card exposing the Polar H10 (HR + HRV) or Verity Sense (optical HR-only)
 * internal backup recording toggle (`REQ-SET-104`, `REQ-SET-108`).
 */
@Composable
fun PolarBackupRecordingCard(
    checked: Boolean,
    isVeritySense: Boolean,
    isAndroid13Plus: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val titleRes = if (isVeritySense) {
        R.string.device_polar_verity_backup_recording_title
    } else {
        R.string.device_polar_h10_backup_recording_title
    }
    val descRes = if (isVeritySense) {
        R.string.device_polar_verity_backup_recording_desc
    } else {
        R.string.device_polar_h10_backup_recording_desc
    }
    PolarFeatureToggleCard(
        titleRes = titleRes,
        descRes = descRes,
        disabledHintRes = R.string.device_polar_h10_backup_recording_requires_android_13,
        checked = checked,
        enabled = isAndroid13Plus,
        onCheckedChange = onCheckedChange
    )
}

/**
 * Stateless Material 3 card exposing the Polar H10 (`ACC`) or Verity Sense (`ACC` + `GYRO`)
 * live PMD IMU streaming opt-in toggle (`REQ-SET-112.1`, `ATT-8016`).
 */
@Composable
fun PolarLiveImuCard(
    checked: Boolean,
    isVeritySense: Boolean,
    isAndroid13Plus: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val descRes = if (isVeritySense) {
        R.string.device_polar_verity_live_imu_desc
    } else {
        R.string.device_polar_h10_live_imu_desc
    }
    PolarFeatureToggleCard(
        titleRes = R.string.device_polar_live_imu_title,
        descRes = descRes,
        disabledHintRes = R.string.device_polar_live_imu_requires_android_13,
        checked = checked,
        enabled = isAndroid13Plus,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun PolarFeatureToggleCard(
    titleRes: Int,
    descRes: Int,
    disabledHintRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
                title = stringResource(titleRes),
                checked = checked,
                enabled = enabled,
                disabledHint = stringResource(disabledHintRes),
                onCheckedChange = onCheckedChange
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            )
        }
    }
}

@Preview(name = "Wheel Position Equipment Light", showBackground = true)
@Composable
private fun WheelPositionEquipmentLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        WheelPositionEquipmentPreviewContent()
    }
}

@Preview(
    name = "Wheel Position Equipment Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
private fun WheelPositionEquipmentDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        WheelPositionEquipmentPreviewContent()
    }
}

@Composable
private fun WheelPositionEquipmentPreviewContent() {
    Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EquipmentSectionContent(
                data = previewWheelSensorUiData(),
                onEquipmentChanged = { _, _ -> }
            )
            EquipmentSheetListContent(
                availableEquipment = listOf("Gravel Bike", "Road Bike", "Winter Bike"),
                selectedItems = listOf("Gravel Bike", "Road Bike"),
                draftPositions = mapOf(
                    "Gravel Bike" to WheelPosition.FRONT,
                    "Road Bike" to WheelPosition.REAR
                ),
                isWheelSensor = true,
                onSimpleToggle = {},
                onWheelBikeClick = {}
            )
        }
    }
}

private fun previewWheelSensorUiData(): DeviceUiData = DeviceUiData(
    id = 42L,
    protocol = Protocol.BLUETOOTH_LE,
    deviceType = DeviceType.BIKE_SPEED,
    lastSeen = "Now",
    manufacturer = "Garmin",
    deviceName = "Speed Sensor 2",
    isPaired = true,
    linkedEquipment = listOf("Gravel Bike", "Road Bike"),
    availableEquipment = listOf("Gravel Bike", "Road Bike", "Winter Bike"),
    powerFeaturesFlags = null,
    batteryPercentage = 95,
    linkedSportTypes = listOf("Cycling"),
    deviceTypeIconRes = R.drawable.bt_bike_spd,
    batteryStatusIconRes = R.drawable.ic_battery_full,
    onEquipmentResId = R.string.devices_on_bikes_text,
    wheelCircumference = 2105,
    calibrationFactor = null,
    powerFeatures = null,
    isConnected = false,
    mainValue = null,
    allValues = null,
    linkedWheelPositions = mapOf(
        "Gravel Bike" to WheelPosition.FRONT,
        "Road Bike" to WheelPosition.REAR
    )
)


