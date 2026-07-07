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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
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
    onCheckAntInstallation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by tabViewModel.uiState.observeAsState()
    val isSearchingForNewDevices by tabViewModel.isSearchingForNewDevices.collectAsState()
    
    val protocol = tabViewModel.protocol
    var showDeleteConfirmFor by remember { mutableStateOf<DeviceUiData?>(null) }
    var editingDeviceId by remember { mutableStateOf<Long?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

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
                        verticalAlignment = Alignment.Top,
                        beyondViewportPageCount = 2
                    ) { page ->
                        val scrollState = when (page) {
                            0 -> availableListState
                            1 -> pairedListState
                            else -> allKnownListState
                        }
                        DeviceListScreen(
                            viewModel = listViewModel,
                            filterSpec = tabs[page],
                            isSearchingForNewDevices = if (page == 0) isSearchingForNewDevices else false,
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // 1. Icon (Protocol or Device Type)
                                        val headerIcon = when {
                                            deviceType != DeviceType.ALL -> com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId(deviceType, protocol)
                                            protocol != Protocol.ALL -> protocol.iconId
                                            else -> null
                                        }

                                        if (headerIcon != null) {
                                            val headerIconSize = if (protocol == Protocol.ANT_PLUS) 38.dp else 42.dp
                                            Icon(
                                                painter = painterResource(id = headerIcon),
                                                contentDescription = null,
                                                modifier = Modifier.size(headerIconSize),
                                                tint = Color.Unspecified
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }

                                        // 2. Title Logic
                                        val title = when {
                                            deviceType != DeviceType.ALL -> {
                                                stringResource(R.string.devices_sensors_title)
                                            }
                                            else -> stringResource(R.string.devices_all_sensors)
                                        }

                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Modern Options Menu (Only shown for ANT+ troubleshooting)
                                    if (protocol == Protocol.ANT_PLUS || protocol == Protocol.ALL) {
                                        Box {
                                            IconButton(onClick = { menuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.devices_settings_header),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.check_ANT_installation)) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onCheckAntInstallation()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            PrimaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            else -> {
                // Should not happen if started from Control Screen
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
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
    return when (spec.filterType) {
        DeviceFilterType.CONNECTED -> stringResource(R.string.devices_tab_available)
        DeviceFilterType.PAIRED -> stringResource(R.string.devices_tab_paired)
        DeviceFilterType.ALL_KNOWN -> stringResource(R.string.devices_tab_known)
    }
}
