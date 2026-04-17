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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatus
import kotlinx.coroutines.launch

@Composable
fun TrackingTabsScreen(
    trackingTabsViewModel: TrackingTabsViewModel
) {
    val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity

    val trackingViews by trackingTabsViewModel.trackingViews.collectAsState(initial = emptyList())
    val trackingMode by trackingTabsViewModel.trackingMode.observeAsState(TrackingMode.READY)
    val screenMode by trackingTabsViewModel.screenMode.collectAsState()

    // -- view models
    val mapViewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context.application)
    )

    // ViewModel for the control tracking tab
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


    // Page count: Control Tab + Sensor Tabs
    val pageCount = if (screenMode != ScreenMode.TRACKING) trackingViews.size else trackingViews.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    // BACK NAVIGATION HANDLER: when in CONFIGURATION mode, exit to TRACKING mode
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        trackingTabsViewModel.toggleScreenMode() // Exit config mode on back press
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. DYNAMIC HEADER (Config Mode or Tab Title)
        val currentViewInfo = if (screenMode != ScreenMode.TRACKING) {
            trackingViews.getOrNull(pagerState.currentPage)
        } else if (pagerState.currentPage > 0) {
            trackingViews.getOrNull(pagerState.currentPage - 1)
        } else null

        Column(modifier = Modifier.fillMaxSize()) {

            // TAB HEADER
            when (screenMode) {
                ScreenMode.TRACKING -> {
                    // Show the available Sensors
                    Surface(modifier = Modifier.padding(4.dp)) {
                        SensorStatus(activeSensors = activeSensors)
                    }
                }
                ScreenMode.CONFIGURATION -> {
                    // -- The full configuration screen for editing the name of the tab, checkboxes for lap botton, map, and live segments, as well as the add/delete buttons
                    if (currentViewInfo != null) {
                        TrackingTabConfigHeader(
                            viewInfo = currentViewInfo,
                            onUpdateTabName = { id, name -> trackingTabsViewModel.onUpdateTabName(id, name) },
                            onAddTabRelative = { id, after -> trackingTabsViewModel.onAddTabRelative(id, after) },
                            onDeleteTab = { id -> trackingTabsViewModel.onDeleteTab(id) },
                            onUpdateShowMap = { id, show -> trackingTabsViewModel.onUpdateShowMap(id, show) },
                            onUpdateShowLiveSegments = { id, show -> trackingTabsViewModel.onUpdateShowLiveSegments(id, show) },
                            onUpdateShowLapButton = { id, show -> trackingTabsViewModel.onUpdateShowLapButton(id, show) },
                            onToggleMode = { trackingTabsViewModel.toggleScreenMode() }
                        )
                    }
                }
                ScreenMode.PREVIEW -> {
                    if (currentViewInfo != null) {
                        TrackingTabPreviewHeader(
                            viewInfo = currentViewInfo,
                            onToggleMode = { trackingTabsViewModel.toggleScreenMode() },
                        )
                    }
                }
            }

            // TAB ROW
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp,
                divider = {}
            ) {
                if (screenMode == ScreenMode.TRACKING) {
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
                    val targetPage = if (screenMode == ScreenMode.TRACKING) index + 1 else index
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
                beyondViewportPageCount = if (screenMode == ScreenMode.TRACKING) trackingViews.size + 1 else trackingViews.size  // keep them all
            ) { page ->
                if (screenMode == ScreenMode.TRACKING && page == 0) {

                    // --- CONTROL TAB (Page 0) ---
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
                    )
                }
                else {
                    val viewIndex = if (screenMode == ScreenMode.TRACKING) page - 1 else page
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