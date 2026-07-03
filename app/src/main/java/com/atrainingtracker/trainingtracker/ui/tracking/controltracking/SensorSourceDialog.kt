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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.ui.devices.DeviceStatusRow
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.repositories.DeviceTelemetry
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout

@Composable
fun SensorSourceDialog(
    sensorType: SensorType,
    sourceDevice: DeviceUiData?,
    telemetry: DeviceTelemetry?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Current Value
                val currentValueRaw = telemetry?.allValues?.find { it.sensor == sensorType }?.value 
                    ?: stringResource(id = R.string.NoData)
                val unitRes = MyHelper.getUnitsId(sensorType)
                val unit = if (unitRes != 0 && unitRes != R.string.units_none) stringResource(id = unitRes) else ""
                val currentValue = if (unit.isNotEmpty() && currentValueRaw != stringResource(id = R.string.NoData)) "$currentValueRaw $unit" else currentValueRaw

                MetricItem(
                    label = stringResource(id = R.string.current_value),
                    value = currentValue,
                    iconRes = null,
                    layout = MetricLayout.VERTICAL,
                    isPrimary = true,
                    valueStyle = MaterialTheme.typography.displaySmall
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // 2. Device Information
                if (sourceDevice != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.source_device),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = sourceDevice.deviceTypeIconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(if (sourceDevice.protocol == Protocol.SMARTPHONE) 32.dp else 44.dp)
                                    .padding(if (sourceDevice.protocol == Protocol.ANT_PLUS) 2.dp else 0.dp),
                                tint = if (sourceDevice.protocol == Protocol.SMARTPHONE) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                else 
                                    Color.Unspecified
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = sourceDevice.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                DeviceStatusRow(
                                    device = sourceDevice,
                                    alpha = 0.8f,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            text = sourceDevice.manufacturer,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.source_internal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
