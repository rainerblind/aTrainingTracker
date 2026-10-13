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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold

/**
 * Material 3 modal bottom sheet for multi-dimensional sensor filtering.
 *
 * Utilizes [FilterBottomSheetScaffold] for design consistency across the application.
 * Allows filtering by wireless protocol (All, ANT+, Bluetooth LE), sensor category
 * (HRM, Power, Speed, Cadence, Run Speed, Environment), and battery level.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceFilterBottomSheet(
    criteria: DeviceFilterCriteria,
    onApplyCriteria: (DeviceFilterCriteria) -> Unit,
    onClearAll: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var localProtocol by remember(criteria.protocol) { mutableStateOf(criteria.protocol) }
    var localDeviceTypes by remember(criteria.deviceTypes) { mutableStateOf(criteria.deviceTypes) }
    var localBatteryFilter by remember(criteria.batteryFilter) { mutableStateOf(criteria.batteryFilter) }

    val sensorCategories = remember {
        listOf(
            DeviceType.HRM,
            DeviceType.BIKE_POWER,
            DeviceType.BIKE_SPEED,
            DeviceType.BIKE_CADENCE,
            DeviceType.BIKE_SPEED_AND_CADENCE,
            DeviceType.RUN_SPEED,
            DeviceType.ENVIRONMENT
        )
    }

    FilterBottomSheetScaffold(
        title = stringResource(R.string.filter_sensors_title),
        onDismissRequest = onDismissRequest,
        onClearAll = {
            localProtocol = null
            localDeviceTypes = emptySet()
            localBatteryFilter = BatteryFilter.ALL
            onClearAll()
        },
        onApply = {
            val updated = criteria.copy(
                protocol = localProtocol,
                deviceTypes = localDeviceTypes,
                batteryFilter = localBatteryFilter
            )
            onApplyCriteria(updated)
            onDismissRequest()
        }
    ) {
        // --- 1. PROTOCOL SECTION ---
        Text(
            text = stringResource(R.string.filter_section_protocol),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isAllSelected = localProtocol == null || localProtocol == Protocol.ALL
            FilterChip(
                selected = isAllSelected,
                onClick = { localProtocol = null },
                label = { Text(stringResource(R.string.filter_protocol_all)) }
            )
            FilterChip(
                selected = localProtocol == Protocol.ANT_PLUS,
                onClick = {
                    localProtocol = if (localProtocol == Protocol.ANT_PLUS) null else Protocol.ANT_PLUS
                },
                label = { Text(stringResource(R.string.protocol_ant_plus)) }
            )
            FilterChip(
                selected = localProtocol == Protocol.BLUETOOTH_LE,
                onClick = {
                    localProtocol = if (localProtocol == Protocol.BLUETOOTH_LE) null else Protocol.BLUETOOTH_LE
                },
                label = { Text(stringResource(R.string.protocol_bluetooth)) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 2. SENSOR CATEGORY SECTION ---
        Text(
            text = stringResource(R.string.filter_section_category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sensorCategories.forEach { type ->
                val isSelected = localDeviceTypes.contains(type)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        localDeviceTypes = if (isSelected) {
                            localDeviceTypes - type
                        } else {
                            localDeviceTypes + type
                        }
                    },
                    label = { Text(stringResource(UIHelper.getNameId(type))) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. BATTERY LEVEL SECTION ---
        Text(
            text = stringResource(R.string.filter_section_battery),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = localBatteryFilter == BatteryFilter.ALL,
                onClick = { localBatteryFilter = BatteryFilter.ALL },
                label = { Text(stringResource(R.string.filter_battery_all)) }
            )
            FilterChip(
                selected = localBatteryFilter == BatteryFilter.CRITICAL_ONLY,
                onClick = {
                    localBatteryFilter = if (localBatteryFilter == BatteryFilter.CRITICAL_ONLY) {
                        BatteryFilter.ALL
                    } else {
                        BatteryFilter.CRITICAL_ONLY
                    }
                },
                label = { Text(stringResource(R.string.filter_battery_critical)) }
            )
            FilterChip(
                selected = localBatteryFilter == BatteryFilter.NORMAL,
                onClick = {
                    localBatteryFilter = if (localBatteryFilter == BatteryFilter.NORMAL) {
                        BatteryFilter.ALL
                    } else {
                        BatteryFilter.NORMAL
                    }
                },
                label = { Text(stringResource(R.string.filter_battery_normal)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
