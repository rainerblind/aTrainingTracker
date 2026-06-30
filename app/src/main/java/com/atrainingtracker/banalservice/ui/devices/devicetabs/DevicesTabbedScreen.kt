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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicelist.*
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialog
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
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

    val density = LocalDensity.current
    val appBarMaxHeightPx = with(density) { 135.dp.roundToPx() }
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }

    val availableListState = rememberLazyListState()
    val pairedListState = rememberLazyListState()
    val allKnownListState = rememberLazyListState()

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
                        DeviceFilterSpec(DeviceFilterType.CONNECTED, protocol, deviceType),
                        DeviceFilterSpec(DeviceFilterType.PAIRED, protocol, deviceType),
                        DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, protocol, deviceType)
                    )
                }
                
                val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
                val scope = rememberCoroutineScope()

                Box(Modifier.nestedScroll(connection)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        val scrollState = when (page) {
                            0 -> availableListState
                            1 -> pairedListState
                            else -> allKnownListState
                        }
                        DeviceListScreen(
                            viewModel = listViewModel,
                            filterSpec = tabs[page],
                            searchingFor = if (page == 0) searchingFor else null,
                            onDeviceSelected = { editingDeviceId = it },
                            onDeleteDevice = { showDeleteConfirmFor = it },
                            scrollState = scrollState,
                            appBarOffsetPx = connection.appBarOffset,
                            headerHeightPx = appBarMaxHeightPx.toFloat()
                        )
                    }

                    // --- HEADER ---
                    Surface(
                        modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column {
                            Column(modifier = Modifier.statusBarsPadding()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val title = if (deviceType == DeviceType.ALL) {
                                        stringResource(R.string.devices_all_sensors)
                                    } else {
                                        "${stringResource(UIHelper.getNameId(protocol))} ${stringResource(UIHelper.getNameId(deviceType))}"
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            PrimaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                divider = {}
                            ) {
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
                        }
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
            },
            containerColor = MaterialTheme.colorScheme.surface
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
    return when (spec.filterType) {
        DeviceFilterType.CONNECTED -> stringResource(R.string.devices_tab_available)
        DeviceFilterType.PAIRED -> stringResource(R.string.devices_tab_paired)
        DeviceFilterType.ALL_KNOWN -> stringResource(R.string.devices_tab_known)
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                deviceTypeList.forEach { type ->
                    ListItem(
                        headlineContent = { 
                            Text(
                                text = stringResource(UIHelper.getNameId(type)),
                                style = MaterialTheme.typography.bodyLarge
                            ) 
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(id = com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId(type, protocol)),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Unspecified
                            )
                        },
                        modifier = Modifier.clickable { onSelected(type) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(DeviceType.ALL) }) {
                Text(stringResource(R.string.devices_all))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
