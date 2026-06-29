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
 * along with this program.  See the file LICENSE for more details.
 */

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicelist.*
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialog
import kotlinx.coroutines.launch

@Composable
fun DevicesTabbedScreen(
    tabViewModel: DevicesTabbedViewModel,
    listViewModel: DeviceListViewModel = viewModel(),
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val uiState by tabViewModel.uiState.observeAsState()
    val searchingFor by tabViewModel.searchingFor.collectAsState()
    
    val protocol = tabViewModel.protocol
    var showDeleteConfirmFor by remember { mutableStateOf<DeviceUiData?>(null) }
    var editingDeviceId by remember { mutableStateOf<Long?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.AwaitingDeviceTypeSelection -> {
                DeviceTypeSelectionDialog(
                    protocol = protocol,
                    onSelected = { tabViewModel.onDeviceTypeSelected(it) },
                    onDismiss = { /* Handle back if needed */ }
                )
            }
            is UiState.DisplayingTabs -> {
                val deviceType = state.deviceType
                val tabs = remember(protocol, deviceType) {
                    listOf(
                        DeviceFilterSpec(DeviceFilterType.AVAILABLE, protocol, deviceType),
                        DeviceFilterSpec(DeviceFilterType.PAIRED, protocol, deviceType),
                        DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, protocol, deviceType)
                    )
                }
                
                val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
                val scope = rememberCoroutineScope()

                Column {
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        tabs.forEachIndexed { index, spec ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = {
                                    Text(getTabTitle(spec))
                                }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        DeviceListScreen(
                            viewModel = listViewModel,
                            filterSpec = tabs[page],
                            searchingFor = if (page == 0) searchingFor else null,
                            onDeviceSelected = { editingDeviceId = it },
                            onDeleteDevice = { showDeleteConfirmFor = it }
                        )
                    }
                }
                
                // Keep searching active while displaying tabs
                DisposableEffect(Unit) {
                    tabViewModel.startSearching()
                    onDispose { tabViewModel.stopSearching() }
                }
            }
            else -> {}
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmFor?.let { device ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmFor = null },
            title = { Text(stringResource(R.string.devices_dialog_delete_device_title)) },
            text = { Text(stringResource(R.string.devices_dialog_delete_device_message, device.deviceName)) },
            confirmButton = {
                TextButton(onClick = {
                    listViewModel.deleteDevice(device.id)
                    showDeleteConfirmFor = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmFor = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit Device Dialog
    val idToEdit = editingDeviceId
    if (idToEdit != null) {
        EditDeviceDialog(
            deviceId = idToEdit,
            onDismiss = { editingDeviceId = null }
        )
    }
}

@Composable
private fun getTabTitle(spec: DeviceFilterSpec): String {
    return if (spec.deviceType == DeviceType.ALL) {
        when (spec.filterType) {
            DeviceFilterType.AVAILABLE -> stringResource(R.string.devices_all_available_devices)
            DeviceFilterType.PAIRED -> stringResource(R.string.devices_all_paired_devices)
            DeviceFilterType.ALL_KNOWN -> stringResource(R.string.devices_all_known_devices)
        }
    } else {
        val deviceTypeName = stringResource(UIHelper.getNameId(spec.deviceType))
        when (spec.filterType) {
            DeviceFilterType.AVAILABLE -> stringResource(R.string.devices_available_devices_format, deviceTypeName)
            DeviceFilterType.PAIRED -> stringResource(R.string.devices_paired_devices_format, deviceTypeName)
            DeviceFilterType.ALL_KNOWN -> stringResource(R.string.devices_known_devices_format, deviceTypeName)
        }
    }
}

@Composable
private fun DeviceTypeSelectionDialog(
    protocol: Protocol,
    onSelected: (DeviceType) -> Unit,
    onDismiss: () -> Unit
) {
    val deviceTypeList = remember(protocol) { DeviceType.getRemoteDeviceTypes(protocol).toList() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_device_type)) },
        text = {
            Column {
                deviceTypeList.forEach { type ->
                    ListItem(
                        headlineContent = { Text(stringResource(UIHelper.getNameId(type))) },
                        modifier = Modifier.clickable { onSelected(type) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(DeviceType.ALL) }) {
                Text(stringResource(R.string.devices_all))
            }
        }
    )
}
