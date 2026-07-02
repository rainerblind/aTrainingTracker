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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.components.EmptyStatePlaceholder

@Composable
fun DeviceListScreen(
    viewModel: DeviceListViewModel,
    filterSpec: DeviceFilterSpec,
    isSearchingForNewDevices: Boolean,
    onDeviceSelected: (Long) -> Unit,
    onDeleteDevice: (DeviceUiData) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LazyListState = rememberLazyListState(),
    appBarOffsetPx: Int = 0,
    headerHeightPx: Float = 0f
) {
    val devices by viewModel.getFilteredDevices(filterSpec).collectAsState(initial = emptyList())
    val density = LocalDensity.current
    val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
    
    // Dynamically calculate bottom padding to clear the system navigation bar
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding() // Ensure entire content area stays above system navigation
    ) {
        // Prominent Searching Header - Shown always when searching for NEW devices in the Available tab
        val isSearching = filterSpec.filterType == DeviceFilterType.CONNECTED && isSearchingForNewDevices
        if (isSearching) {
            SearchingHeader(
                protocolName = stringResource(UIHelper.getNameId(filterSpec.protocol)),
                deviceTypeName = stringResource(UIHelper.getNameId(filterSpec.deviceType)),
                modifier = Modifier.padding(top = topPadding, start = 8.dp, end = 8.dp)
            )
        }

        if (devices.isEmpty()) {
            val emptyMessage = when (filterSpec.filterType) {
                DeviceFilterType.CONNECTED -> stringResource(R.string.devices_no_devices_available)
                DeviceFilterType.PAIRED -> stringResource(R.string.devices_no_paired_devices)
                DeviceFilterType.ALL_KNOWN -> stringResource(R.string.devices_no_known_devices)
            }
            val placeholderPadding = if (isSearching) 8.dp else topPadding
            EmptyStatePlaceholder(
                modifier = Modifier
                    .padding(top = placeholderPadding)
                    .navigationBarsPadding()
                    .weight(1f),
                icon = Icons.Default.Devices,
                message = emptyMessage,
                hint = "" // Information is now prominently in the header
            )
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (isSearching) 8.dp else topPadding + 8.dp,
                    bottom = navigationBarBottom + 16.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically // Centering ensures indicator is safe from edge clipping
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))

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
