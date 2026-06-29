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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.components.MappableListItem

@Composable
fun DeviceItem(
    device: DeviceUiData,
    onPairClick: () -> Unit,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MappableListItem(
        modifier = modifier,
        onClick = onItemClick,
        onLongClick = onLongClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Identity Row: Icon + Name/Value + Manufacturer
                Row(verticalAlignment = Alignment.Top) {
                    // Device Type Icon
                    Icon(
                        painter = painterResource(id = device.deviceTypeIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = Color.Unspecified
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Row 1: Name (left) and Value (right)
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = device.deviceName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            if (device.isConnected) {
                                Text(
                                    text = device.mainValue ?: "--",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_device_not_available),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }

                        // Row 2: Manufacturer
                        Text(
                            text = device.manufacturer,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Row 3: Status Line (Availability and Battery) - Starts below icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // 1. Availability Icon
                    val statusIcon = if (device.isConnected) R.drawable.ic_device_available else R.drawable.ic_device_not_available
                    Icon(
                        painter = painterResource(id = statusIcon),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Unspecified // Original Color
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // 2. Status Text
                    Text(
                        text = if (device.isConnected) {
                            stringResource(R.string.devices_available)
                        } else {
                            "${stringResource(R.string.devices_lastSeenText)} ${device.lastSeen?.split(" ")?.firstOrNull() ?: ""}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    // 3. Battery Info
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        painter = painterResource(id = device.batteryStatusIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified // Original Color
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${device.batteryPercentage}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Row 4: Linked Equipment
                if (device.linkedEquipment.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.devices_on_short_format,
                            device.linkedEquipment.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Control (Bottom Right): Pairing Switch
            Switch(
                checked = device.isPaired,
                onCheckedChange = { onPairClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .scale(0.8f) 
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDeviceItem() {
    val mockDevice = DeviceUiData(
        id = 1L,
        protocol = Protocol.ANT_PLUS,
        deviceType = DeviceType.HRM,
        lastSeen = "2024-05-21 10:00:00",
        manufacturer = "Garmin",
        deviceName = "HRM-Pro 12345",
        isPaired = true,
        linkedEquipment = listOf("Road Bike", "Treadmill"),
        availableEquipment = emptyList(),
        powerFeaturesFlags = null,
        batteryPercentage = 85,
        deviceTypeIconRes = R.drawable.hr,
        batteryStatusIconRes = R.drawable.stat_sys_battery_80,
        onEquipmentResId = R.string.devices_on_equipment_text,
        wheelCircumference = null,
        calibrationFactor = null,
        powerFeatures = null,
        isConnected = true,
        mainValue = "145",
        allValues = listOf("145 bpm")
    )

    MaterialTheme {
        DeviceItem(
            device = mockDevice,
            onPairClick = {},
            onItemClick = {},
            onLongClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDeviceItem2() {
    val mockDevice = DeviceUiData(
        id = 1L,
        protocol = Protocol.ANT_PLUS,
        deviceType = DeviceType.HRM,
        lastSeen = "2024-05-21 10:00:00",
        manufacturer = "Garmin",
        deviceName = "HRM-Pro 12345",
        isPaired = false,
        linkedEquipment = listOf("Road Bike", "Treadmill"),
        availableEquipment = emptyList(),
        powerFeaturesFlags = null,
        batteryPercentage = 12,
        deviceTypeIconRes = R.drawable.hr,
        batteryStatusIconRes = R.drawable.stat_sys_battery_80,
        onEquipmentResId = R.string.devices_on_equipment_text,
        wheelCircumference = null,
        calibrationFactor = null,
        powerFeatures = null,
        isConnected = false,
        mainValue = "145",
        allValues = listOf("145 bpm")
    )

    MaterialTheme {
        DeviceItem(
            device = mockDevice,
            onPairClick = {},
            onItemClick = {},
            onLongClick = {}
        )
    }
}
