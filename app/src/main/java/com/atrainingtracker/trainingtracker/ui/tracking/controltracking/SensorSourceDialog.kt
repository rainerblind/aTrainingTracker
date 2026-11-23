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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.HrmSensorCapabilityHelper
import com.atrainingtracker.banalservice.sensor.HrHealthTier
import com.atrainingtracker.banalservice.sensor.HrModality
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.PrimaryHrSensorArbitrator
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.sensor.formater.AltitudeFormatter
import com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.LocationCalibrationStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSourceDialog(
    sensorType: SensorType,
    sourceDevice: DeviceUiData?,
    allTelemetry: List<DeviceTelemetry>,  // telemetry of all active devices
    allDevices: List<DeviceUiData>,       // all devices found in the database
    currentAltitude: Double? = null,
    matchedLocation: LocationCalibrationStatus? = null,
    onDeviceClick: (Long) -> Unit,
    onCalibrateAltitude: ((referenceAltitudeMeters: Double, locationName: String?, saveAsKnownLocation: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Grouping Logic
    val potentialTypes = remember(sensorType) { DeviceType.getDeviceTypeList(sensorType) ?: emptySet() }
    val allActiveTelemetries = remember(allTelemetry, sensorType) {
        allTelemetry.filter { telemetry -> telemetry.allValues.any { it.sensor == sensorType } }
    }
    val activeDeviceIds = remember(allActiveTelemetries) { allActiveTelemetries.map { it.deviceId }.toSet() }
    
    val activeBackups = remember(allActiveTelemetries, sourceDevice, allDevices, sensorType, context) {
        sortActiveBackupTelemetries(
            telemetries = allActiveTelemetries,
            sensorType = sensorType,
            sourceDeviceId = sourceDevice?.id,
            allDevices = allDevices,
            context = context
        )
    }

    val notConnected = remember(allDevices, activeDeviceIds, potentialTypes, sensorType, context) {
        sortNotConnectedDevices(
            allDevices = allDevices,
            activeDeviceIds = activeDeviceIds,
            potentialTypes = potentialTypes,
            sensorType = sensorType,
            context = context
        )
    }

    val unitRes = MyHelper.getUnitsId(sensorType)
    val unit = if (unitRes != 0 && unitRes != R.string.units_none) stringResource(id = unitRes) else ""

    val dialogTitle = if (sensorType == SensorType.RADAR_THREAT_COUNT) {
        stringResource(id = R.string.device_type_bike_radar).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        }
    } else {
        sensorType.getFullName(context)
    }

    AppModalBottomSheet(
        title = dialogTitle,
        iconPainter = safePainterResource(id = sensorType.getIconResId(), fallback = Icons.Default.Sensors),
        iconTint = MaterialTheme.colorScheme.onSurface,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.Confirm(
                onConfirm = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sensor_source_dialog_content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var hasPrecedingSection = false

            // 0. Manual Altitude Calibration Section (ATT-4845, ATT-5072 / REQ-TRK-047)
            if (sensorType == SensorType.ALTITUDE && onCalibrateAltitude != null) {
                var altitudeInput by remember(currentAltitude, matchedLocation) {
                    val initialAltitude = matchedLocation?.referenceAltitude ?: currentAltitude
                    val initialFormatted = if (initialAltitude != null && java.lang.Double.isFinite(initialAltitude)) {
                        AltitudeFormatter().format(initialAltitude)
                    } else ""
                    mutableStateOf(initialFormatted)
                }
                var locationNameInput by remember { mutableStateOf("") }
                var saveLocation by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.altitude_calibration_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (matchedLocation != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.altitude_calibration_matched_location,
                                    matchedLocation.locationName
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = altitudeInput,
                            onValueChange = { altitudeInput = it },
                            label = { Text(stringResource(id = R.string.altitude_calibration_reference_label)) },
                            trailingIcon = {
                                if (unit.isNotEmpty()) {
                                    Text(
                                        text = unit,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (matchedLocation == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { saveLocation = !saveLocation }
                            ) {
                                Checkbox(
                                    checked = saveLocation,
                                    onCheckedChange = { saveLocation = it }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.altitude_calibration_save_location),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (saveLocation) {
                                OutlinedTextField(
                                    value = locationNameInput,
                                    onValueChange = { locationNameInput = it },
                                    label = { Text(stringResource(id = R.string.altitude_calibration_location_name)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val entered = altitudeInput.toDoubleOrNull()
                                if (entered != null) {
                                    val altitudeMeters = if (TrainingApplication.getUnit() == MyUnits.IMPERIAL) {
                                        entered * BANALService.METER_PER_FOOT
                                    } else {
                                        entered
                                    }
                                    onCalibrateAltitude(
                                        altitudeMeters,
                                        locationNameInput.takeIf { it.isNotBlank() },
                                        saveLocation && matchedLocation == null
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = altitudeInput.toDoubleOrNull() != null,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(id = R.string.altitude_calibration_action))
                        }
                    }
                }
                hasPrecedingSection = true
            }

            // 1. Source Device
            if (sourceDevice != null) {
                if (hasPrecedingSection) {
                    HorizontalDivider(
                        modifier = Modifier.testTag("sensor_source_dialog_divider"),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                hasPrecedingSection = true
                val telemetry = allActiveTelemetries.find { it.deviceId == sourceDevice.id }
                val value = telemetry?.allValues?.find { it.sensor == sensorType }?.value ?: "--"
                val displayValue = if (sensorType == SensorType.RADAR_THREAT_COUNT) null
                    else if (value != "--") "$value $unit" else value
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.source_device),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TTColor.SensorSourceActive
                    )
                    DeviceIdentityBlock(
                        device = sourceDevice, 
                        isConnected = true, 
                        valueWithUnit = displayValue,
                        modifier = Modifier.clickable { 
                            onDeviceClick(sourceDevice.id)
                        }
                    )
                }
            }

            // 2. Active Backups
            if (activeBackups.isNotEmpty()) {
                if (hasPrecedingSection) {
                    HorizontalDivider(
                        modifier = Modifier.testTag("sensor_source_dialog_divider"),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                hasPrecedingSection = true
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.source_active_backups),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TTColor.SensorBackupActive
                    )
                    activeBackups.forEach { telemetry ->
                        val device = allDevices.find { it.id == telemetry.deviceId }
                        if (device != null) {
                            val value = telemetry.allValues.find { it.sensor == sensorType }?.value ?: "--"
                            val displayValue = if (sensorType == SensorType.RADAR_THREAT_COUNT) null
                                else if (value != "--") "$value $unit" else value
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DeviceIdentityBlock(
                                    device = device, 
                                    isConnected = true,
                                    valueWithUnit = displayValue,
                                    modifier = Modifier.clickable {
                                        onDeviceClick(device.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Not connected devices
            if (notConnected.isNotEmpty()) {
                if (hasPrecedingSection) {
                    HorizontalDivider(
                        modifier = Modifier.testTag("sensor_source_dialog_divider"),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                hasPrecedingSection = true
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.source_not_connected),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    notConnected.forEach { device ->
                        DeviceIdentityBlock(
                            device = device, 
                            isConnected = false,
                            modifier = Modifier.clickable {
                                onDeviceClick(device.id)
                            }
                        )
                    }
                }
            }

            // Internal Fallback if applicable and no external active source
            if (sourceDevice == null && activeBackups.isEmpty() && notConnected.isEmpty()) {
                if (hasPrecedingSection) {
                    HorizontalDivider(
                        modifier = Modifier.testTag("sensor_source_dialog_divider"),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                val isInternalPossible = remember(sensorType) {
                    potentialTypes.isNotEmpty() && potentialTypes.all { 
                        it == DeviceType.CLOCK || 
                        it == DeviceType.VERTICAL_SPEED_AND_SLOPE || 
                        it.name.startsWith("SPEED_AND_LOCATION") || 
                        it == DeviceType.ALTITUDE_FROM_PRESSURE || 
                        it == DeviceType.ALTITUDE_FROM_POSITION 
                    }
                }
                if (isInternalPossible) {
                    Text(
                        text = stringResource(id = R.string.source_internal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                     Text(
                        text = stringResource(id = R.string.source_none_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIdentityBlock(
    device: DeviceUiData, 
    isConnected: Boolean,
    valueWithUnit: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors),
            contentDescription = null,
            modifier = Modifier
                .size(if (device.protocol == Protocol.SMARTPHONE) 32.dp else 44.dp)
                .padding(if (device.protocol == Protocol.ANT_PLUS) 2.dp else 0.dp)
                .clip(RoundedCornerShape(4.dp)),
            tint = if (device.protocol == Protocol.SMARTPHONE)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)
            else
                Color.Unspecified
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (isConnected) TTColor.ConnectionStatusGreen else Color.LightGray,
                            tonalElevation = 2.dp
                        ) {}
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (valueWithUnit != null) {
                    Text(
                        text = valueWithUnit,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DeviceStatusRow(
                device = device,
                isConnected = isConnected,
                alpha = TTAlpha.Medium,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Resolves the effective [HrModality] for a persisted [DeviceUiData] entry (`REQ-UI-2804.3`).
 * When persisted [DeviceUiData.btAddress] and [context] are available for a [DeviceType.HRM],
 * re-evaluates [PrimaryHrSensorArbitrator.resolveModality] against live capability metadata;
 * otherwise uses an explicit [DeviceUiData.hrModality] before falling back to protocol/name rules.
 */
internal fun resolveDeviceHrModality(
    device: DeviceUiData,
    context: android.content.Context? = null
): HrModality {
    if (device.btAddress != null && context != null && device.deviceType == DeviceType.HRM) {
        return PrimaryHrSensorArbitrator.resolveModality(
            protocol = device.protocol,
            deviceType = device.deviceType,
            deviceName = device.deviceName,
            manufacturer = device.manufacturer,
            macAddress = device.btAddress,
            context = context
        )
    }
    device.hrModality?.let { return it }
    return PrimaryHrSensorArbitrator.resolveModality(
        protocol = device.protocol,
        deviceType = device.deviceType,
        deviceName = device.deviceName,
        manufacturer = device.manufacturer,
        macAddress = device.btAddress,
        context = context
    )
}

/**
 * Sorts active backup telemetry entries for [SensorSourceDialog] (`REQ-UI-268.5`, `REQ-UI-2804.3`).
 * For [SensorType.HR], orders by `(healthTier [HEALTHY < DEGRADED < STALE_OR_NULL],
 * modalityTier [ECG < ANT+ < Optical PPG < Other], deviceName, deviceId)`.
 * Non-HR sensor types preserve [MySensorManager.getPriorityRank] ordering.
 */
internal fun sortActiveBackupTelemetries(
    telemetries: List<DeviceTelemetry>,
    sensorType: SensorType,
    sourceDeviceId: Long?,
    allDevices: List<DeviceUiData>,
    context: android.content.Context? = null
): List<DeviceTelemetry> {
    val devicesById = allDevices.associateBy { it.id }
    val filtered = telemetries.filter { it.deviceId != sourceDeviceId }
    if (sensorType == SensorType.HR) {
        return filtered.sortedWith(
            compareBy<DeviceTelemetry>(
                { (it.hrHealthTier ?: HrHealthTier.STALE_OR_NULL).rank },
                { telemetry ->
                    val modality = telemetry.hrModality
                        ?: devicesById[telemetry.deviceId]?.let { resolveDeviceHrModality(it, context) }
                        ?: HrModality.OTHER
                    modality.rank
                },
                { devicesById[it.deviceId]?.deviceName ?: "" },
                { it.deviceId }
            )
        )
    }
    return filtered.sortedWith(
        compareBy<DeviceTelemetry>(
            { MySensorManager.getPriorityRank(sensorType, devicesById[it.deviceId]?.deviceType) },
            { devicesById[it.deviceId]?.deviceName ?: "" },
            { it.deviceId }
        )
    )
}

/**
 * Normalizes a persisted [DeviceUiData.manufacturer] display string for hardware capability checks,
 * clearing the localized [R.string.devices_unknown_manufacturer] placeholder so un-reconnected
 * Polar sensors retain legacy name fallback (`REQ-CON-057.3`).
 */
internal fun normalizeManufacturerForCapabilityCheck(
    manufacturer: String,
    context: android.content.Context? = null
): String {
    val trimmed = manufacturer.trim()
    if (trimmed.isEmpty()) return ""
    val unknownLabel = context?.let {
        runCatching { it.getString(R.string.devices_unknown_manufacturer) }.getOrNull()?.trim()
    }
    if (!unknownLabel.isNullOrEmpty() && trimmed.equals(unknownLabel, ignoreCase = true)) {
        return ""
    }
    return trimmed
}

/**
 * Sorts disconnected devices for [SensorSourceDialog] (`REQ-UI-268.5`, `REQ-UI-2804.3`, `REQ-CON-057.3`).
 * For [SensorType.HR], orders by `(modalityTier [ECG < ANT+ < Optical PPG < Other],
 * deviceName, deviceId)` using persisted protocol and capability metadata.
 * For [DeviceType.HRM] candidates on Polar Live-IMU sensor types (`CADENCE`, `STRIDES`,
 * `VERTICAL_OSCILLATION`, `GROUND_CONTACT_TIME`), filters via [HrmSensorCapabilityHelper].
 * Non-HR sensor types preserve [MySensorManager.getPriorityRank] ordering.
 */
internal fun sortNotConnectedDevices(
    allDevices: List<DeviceUiData>,
    activeDeviceIds: Set<Long>,
    potentialTypes: Set<DeviceType>,
    sensorType: SensorType,
    context: android.content.Context? = null
): List<DeviceUiData> {
    val filtered = allDevices.filter { device ->
        potentialTypes.contains(device.deviceType) &&
            !activeDeviceIds.contains(device.id) &&
            (device.deviceType != DeviceType.HRM || HrmSensorCapabilityHelper.isSensorSupported(
                context = context,
                sensorType = sensorType,
                protocol = device.protocol,
                deviceName = device.deviceName,
                manufacturer = normalizeManufacturerForCapabilityCheck(device.manufacturer, context),
                btAddress = device.btAddress
            ))
    }
    if (sensorType == SensorType.HR) {
        return filtered.sortedWith(
            compareBy<DeviceUiData>(
                { resolveDeviceHrModality(it, context).rank },
                { it.deviceName },
                { it.id }
            )
        )
    }
    return filtered.sortedWith(
        compareBy<DeviceUiData>(
            { MySensorManager.getPriorityRank(sensorType, it.deviceType) },
            { it.deviceName },
            { it.id }
        )
    )
}
