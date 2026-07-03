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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout

@Composable
fun SensorSourceDialog(
    sensorType: SensorType,
    sourceDevice: DeviceUiData?,
    allTelemetry: List<DeviceTelemetry>,
    allDevices: List<DeviceUiData>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Grouping Logic
    val potentialTypes = remember(sensorType) { DeviceType.getDeviceTypeList(sensorType) ?: emptySet() }
    val allActiveTelemetries = remember(allTelemetry, sensorType) {
        allTelemetry.filter { telemetry -> telemetry.allValues.any { it.sensor == sensorType } }
    }
    val activeDeviceIds = remember(allActiveTelemetries) { allActiveTelemetries.map { it.deviceId }.toSet() }
    
    val activeBackups = remember(allActiveTelemetries, sourceDevice) {
        allActiveTelemetries.filter { it.deviceId != sourceDevice?.id }
    }
    
    val notConnected = remember(allDevices, activeDeviceIds, potentialTypes) {
        allDevices.filter { it.isPaired && potentialTypes.contains(it.deviceType) && !activeDeviceIds.contains(it.id) }
    }

    val unitRes = MyHelper.getUnitsId(sensorType)
    val unit = if (unitRes != 0 && unitRes != R.string.units_none) stringResource(id = unitRes) else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = sensorType.getIconResId()),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = sensorType.getFullName(context),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Source Device
                if (sourceDevice != null) {
                    val telemetry = allActiveTelemetries.find { it.deviceId == sourceDevice.id }
                    val value = telemetry?.allValues?.find { it.sensor == sensorType }?.value ?: "--"
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.source_device),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DeviceIdentityBlock(
                            device = sourceDevice, 
                            isConnected = true, 
                            valueWithUnit = if (value != "--") "$value $unit" else value
                        )
                        Text(
                            text = sourceDevice.manufacturer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // 2. Active Backups
                if (activeBackups.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.source_active_backups),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        activeBackups.forEach { telemetry ->
                            val device = allDevices.find { it.id == telemetry.deviceId }
                            if (device != null) {
                                val value = telemetry.allValues.find { it.sensor == sensorType }?.value ?: "--"
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    DeviceIdentityBlock(
                                        device = device, 
                                        isConnected = true,
                                        valueWithUnit = if (value != "--") "$value $unit" else value
                                    )
                                    Text(
                                        text = device.manufacturer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Not connected devices
                if (notConnected.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.source_not_connected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        notConnected.forEach { device ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DeviceIdentityBlock(device = device, isConnected = false)
                                Text(
                                    text = device.manufacturer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Internal Fallback if applicable and no external active source
                if (sourceDevice == null && activeBackups.isEmpty() && notConnected.isEmpty()) {
                    val isInternalPossible = remember(sensorType) {
                        potentialTypes.isNotEmpty() && potentialTypes.all { 
                            it == DeviceType.CLOCK || 
                            it == DeviceType.VERTICAL_SPEED_AND_SLOPE || 
                            it.name.startsWith("SPEED_AND_LOCATION") || 
                            it == DeviceType.ALTITUDE_FROM_PRESSURE 
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
                            text = "No source configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Composable
private fun DeviceIdentityBlock(
    device: DeviceUiData, 
    isConnected: Boolean,
    valueWithUnit: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = device.deviceTypeIconRes),
            contentDescription = null,
            modifier = Modifier
                .size(if (device.protocol == Protocol.SMARTPHONE) 32.dp else 44.dp)
                .padding(if (device.protocol == Protocol.ANT_PLUS) 2.dp else 0.dp),
            tint = if (device.protocol == Protocol.SMARTPHONE)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isConnected) com.atrainingtracker.trainingtracker.ui.theme.ConnectionStatusGreen else Color.LightGray,
                        tonalElevation = 2.dp
                    ) {}
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
                alpha = 0.8f,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
