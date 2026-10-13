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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.trainingtracker.ui.common.filters.RemovableFilterChip

/**
 * Horizontal scrolling row displaying removable chips for each active sensor filter dimension.
 *
 * Enables single-tap dismissal of active protocol, category, and battery filters directly
 * below the sensor tabs.
 */
@Composable
fun ActiveDeviceFilterChipsRow(
    criteria: DeviceFilterCriteria,
    onRemoveProtocol: () -> Unit,
    onRemoveDeviceType: (DeviceType) -> Unit,
    onRemoveBatteryFilter: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (criteria.isEmpty) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Protocol Chip
        if (criteria.protocol != null && criteria.protocol != Protocol.ALL) {
            item("protocol") {
                RemovableFilterChip(
                    label = stringResource(UIHelper.getNameId(criteria.protocol)),
                    onRemove = onRemoveProtocol
                )
            }
        }

        // 2. Category / DeviceType Chips
        items(
            items = criteria.deviceTypes.toList(),
            key = { "deviceType_${it.name}" }
        ) { deviceType ->
            RemovableFilterChip(
                label = stringResource(UIHelper.getNameId(deviceType)),
                onRemove = { onRemoveDeviceType(deviceType) }
            )
        }

        // 3. Battery Level Chip
        if (criteria.batteryFilter != BatteryFilter.ALL) {
            item("battery") {
                val labelRes = when (criteria.batteryFilter) {
                    BatteryFilter.CRITICAL_ONLY -> R.string.filter_battery_critical_chip
                    BatteryFilter.NORMAL -> R.string.filter_battery_normal_chip
                    BatteryFilter.ALL -> R.string.filter_battery_all
                }
                RemovableFilterChip(
                    label = stringResource(labelRes),
                    onRemove = onRemoveBatteryFilter
                )
            }
        }

        // 4. Clear All Button
        if (criteria.activeFilterCount > 1) {
            item("clearAll") {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.filter_clear_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
