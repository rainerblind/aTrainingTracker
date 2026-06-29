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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData

@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel,
    filterSpec: DeviceFilterSpec,
    searchingFor: String?,
    onDeviceSelected: (Long) -> Unit,
    onDeleteDevice: (DeviceUiData) -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.getFilteredDevices(filterSpec).observeAsState(emptyList())

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Searching Header (Only for Available tab)
        if (filterSpec.deviceType != DeviceType.ALL && filterSpec.filterType == DeviceFilterType.AVAILABLE) {
            SearchingHeader(
                protocolName = stringResource(UIHelper.getNameId(filterSpec.protocol)),
                deviceTypeName = stringResource(UIHelper.getNameId(filterSpec.deviceType)),
                isSearching = searchingFor != null
            )
        }

        // 2. The Device List
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_workouts_available), // TODO: Use better "No devices" string
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceItem(
                        device = device,
                        onPairClick = { viewModel.onPairedChanged(device.id, !device.isPaired) },
                        onItemClick = { onDeviceSelected(device.id) },
                        onLongClick = { onDeleteDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchingHeader(
    protocolName: String,
    deviceTypeName: String,
    isSearching: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Text(
                text = stringResource(
                    R.string.devices_searchingForDevice,
                    protocolName,
                    deviceTypeName
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
