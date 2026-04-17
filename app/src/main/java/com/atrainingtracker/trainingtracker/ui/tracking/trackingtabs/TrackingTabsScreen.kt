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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.map.MapViewModel
import com.atrainingtracker.trainingtracker.ui.map.MapViewModelFactory
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlNavigation
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreen
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel
import kotlinx.coroutines.launch

@Composable
fun TrackingTabsScreen(
    trackingTabsViewModel: TrackingTabsViewModel,
    isExplicitMode: Boolean
) {
    val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity

    val trackingViews by trackingTabsViewModel.trackingViews.collectAsState(initial = emptyList())
    val trackingMode by trackingTabsViewModel.trackingMode.observeAsState(TrackingMode.READY)
    val screenMode by trackingTabsViewModel.screenMode.collectAsState()

    val mapViewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.application)
    )

    // Page count: Control Tab + Sensor Tabs
    val pageCount = if (isExplicitMode) trackingViews.size else trackingViews.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    // BACK NAVIGATION HANDLER: when in CONFIGURATION mode, exit to TRACKING mode
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        trackingTabsViewModel.toggleScreenMode() // Exit config mode on back press
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. DYNAMIC HEADER (Config Mode or Tab Title)
        val currentViewInfo = if (isExplicitMode) {
            trackingViews.getOrNull(pagerState.currentPage)
        } else if (pagerState.currentPage > 0) {
            trackingViews.getOrNull(pagerState.currentPage - 1)
        } else null

        Column(modifier = Modifier.fillMaxSize()) {


            // TAB HEADER
            if (currentViewInfo != null) {
                TrackingTabHeader(
                    viewInfo = currentViewInfo,
                    screenMode = screenMode,
                    onUpdateTabName = { id, name -> trackingTabsViewModel.onUpdateTabName(id, name) },
                    onAddTabRelative = { id, after -> trackingTabsViewModel.onAddTabRelative(id, after) },
                    onDeleteTab = { id -> trackingTabsViewModel.onDeleteTab(id) },
                    onUpdateShowMap = { id, show -> trackingTabsViewModel.onUpdateShowMap(id, show) },
                    onUpdateShowLiveSegments = { id, show -> trackingTabsViewModel.onUpdateShowLiveSegments(id, show) },
                    onUpdateShowLapButton = { id, show -> trackingTabsViewModel.onUpdateShowLapButton(id, show) },
                    onToggleMode = { trackingTabsViewModel.toggleScreenMode() }
                )
            }

            // TAB ROW
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                divider = {}
            ) {
                if (!isExplicitMode) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            // Dynamic Title for Control Tab (Tracking/Paused/Start)
                            Text(getControlTabTitle(trackingMode))
                        }
                    )
                }
                trackingViews.forEachIndexed { index, view ->
                    val targetPage = if (isExplicitMode) index else index + 1
                    Tab(
                        selected = pagerState.currentPage == targetPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(targetPage) } },
                        text = { Text(view.name) }
                    )
                }
            }

            // 3. THE HORIZONTAL PAGER
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = true,
                beyondViewportPageCount = 7 // Keep some tabs...
            ) { page ->
                if (!isExplicitMode && page == 0) {

                    // --- CONTROL TAB (Page 0) ---

                    // Initialize the specialized ViewModel for this screen
                    val controlViewModel: ControlTrackingViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ControlTrackingViewModel(context.application) as T
                            }
                        }
                    )

                    // Collect the states required by ControlTrackingScreen
                    val searchingFor by controlViewModel.searchingForDevice.collectAsState()
                    val devices by controlViewModel.remoteDevices.collectAsState()
                    val activeSensors by controlViewModel.activeSensors.collectAsState()
                    val bSportType by controlViewModel.bSportType.collectAsState()
                    val trackingMode by controlViewModel.trackingMode.observeAsState(TrackingMode.READY)

                    // Navigation logic for Pairing/Edit (Moved from Fragment to LaunchedEffect)
                    LaunchedEffect(Unit) {
                        controlViewModel.navigationEvent.collect { navigation ->
                            when (navigation) {
                                is ControlNavigation.ToPairing -> {
                                    (context as? MainActivityWithNavigation)?.startPairing(navigation.protocol)
                                }
                                is ControlNavigation.ToEditDevice -> {
                                    val editDeviceDialog = EditDeviceFragmentFactory.create(
                                        deviceId = navigation.deviceId,
                                        deviceType = navigation.deviceType
                                    )
                                    editDeviceDialog.show(context.supportFragmentManager, "EditDeviceDialog")
                                }
                            }
                        }
                    }

                    ControlTrackingScreen(
                        trackingMode = trackingMode,
                        searchingFor = searchingFor,
                        devices = devices,
                        activeSensors = activeSensors,
                        currentSport = bSportType,
                        isAntSupported = controlViewModel.isAntProperlyInstalled(),
                        isBluetoothSupported = controlViewModel.isBluetoothSupported(),
                        onSearch = { controlViewModel.onSearchClicked() },
                        onDeviceClick = { controlViewModel.onDeviceClicked(it) },
                        onSportSelected = { controlViewModel.setSport(it) },
                        onStart = { controlViewModel.onStartTracking() },
                        onPause = { controlViewModel.onPauseTracking() },
                        onResume = { controlViewModel.onResumeTracking() },
                        onStop = { controlViewModel.onStopTracking() },
                        onPairingClicked = { controlViewModel.onPairingClicked(it) }
                    )                } else {
                    val viewIndex = if (isExplicitMode) page else page - 1
                    val viewInfo = trackingViews[viewIndex]
                    // The Grid Content (including the Elevation Profile logic)
                    TrackingTabGridContent(
                        viewInfo.tabViewId,
                        screenMode,
                        mapViewModel)
                }
            }

            // THE CONDITIONAL LAP BUTTON
            val shouldShowLapButton = currentViewInfo?.showLapButton == true
                    && screenMode == ScreenMode.TRACKING

            if (shouldShowLapButton) {
                LapButton(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 0.dp)
                        .align(Alignment.CenterHorizontally)
                        .wrapContentHeight(),
                    trackingMode = trackingMode,
                    onClick = { trackingTabsViewModel.onLapButtonClick() }
                )
            }
        }
    }
}

@Composable
fun getControlTabTitle(mode: TrackingMode): String {
    return when (mode) {
        TrackingMode.PAUSED -> stringResource(R.string.Paused)
        TrackingMode.TRACKING -> stringResource(R.string.Tracking)
        else -> stringResource(R.string.tab_start)
    }
}