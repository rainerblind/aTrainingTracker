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

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.tracking.LapSummaryDialog
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlNavigation
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreen
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatus
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingTabGridContent
import kotlinx.coroutines.launch

private const val TAG = "TrackingTabsScreen"
private val DEBUG = BuildConfig.DEBUG

@Composable
fun TrackingTabsScreen(
    trackingTabsViewModel: TrackingTabsViewModel
) {
    val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity

    val trackingViews by trackingTabsViewModel.trackingViews.collectAsState(initial = emptyList())
    val trackingMode by trackingTabsViewModel.trackingMode.observeAsState(TrackingMode.READY)
    val screenMode by trackingTabsViewModel.screenMode.collectAsState()

    if (trackingViews.isEmpty() && screenMode != ScreenMode.TRACKING) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading tabs...")
        }
        return // Stop execution here to prevent Pager from crashing
    }

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
    val pageCount by remember(trackingViews, screenMode) {
        derivedStateOf {
            if (screenMode == ScreenMode.TRACKING) trackingViews.size + 1 else trackingViews.size
        }
    }
    // Keep track of the current page as a simple variable to survive the PagerState recreation
    var lastKnownPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Calculate the initial page for the NEW state
    val initialPage = remember(trackingViews.size, screenMode) {
        // Use the tracked 'lastKnownPage' instead of the pagerState reference
        lastKnownPage.coerceIn(
            0,
            (if (screenMode == ScreenMode.TRACKING) trackingViews.size else trackingViews.size - 1).coerceAtLeast(0)
        )
    }

    // Recreate the PagerState using the preserved initialPage
    val pagerState = key(trackingViews.size, screenMode) {
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount }
        )
    }
    // Update the tracker whenever the pager settles on a new page
    LaunchedEffect(pagerState.currentPage) {
        lastKnownPage = pagerState.currentPage
    }

    val scope = rememberCoroutineScope()

    // BACK NAVIGATION HANDLER: when in CONFIGURATION mode, exit to TRACKING mode
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        trackingTabsViewModel.toggleScreenMode() // Exit config mode on back press
    }

    // -- Show Lap Summary Dialog
    val lapEvent by trackingTabsViewModel.lapEvent.observeAsState()
    lapEvent?.let { event ->
        LapSummaryDialog(
            lapNr = event.lapNumber,
            lapTime = event.lapTime,
            lapDistance = event.lapDistance,
            lapSpeed = event.lapSpeed,
            onDismissRequest = {
                trackingTabsViewModel.clearLapEvent()
            }
        )
    }


    // NAVIGATION COLLECTION (necessary, when deleting tabs)
    LaunchedEffect(Unit) {
        trackingTabsViewModel.navigationEvent.collect { tabNavigationEvent ->
            when (tabNavigationEvent) {
                is TabNavigationEvent.NavigateTo -> {

                    // Calculate offset: if TRACKING mode, page 0 is Control, so add 1
                    val offset = if (screenMode == ScreenMode.TRACKING) 1 else 0
                    val target = tabNavigationEvent.index + offset

                    // Animate to the requested page
                    scope.launch {
                        try {
                            lastKnownPage = target
                            pagerState.animateScrollToPage(target)
                        }
                        catch (e: Exception) {
                            Log.e(TAG, "Navigation failed: page $target not ready yet", e)
                        }
                    }
                }
            }
        }
    }

    // Move to first tab when tracking is started
    val navigateTrigger by trackingTabsViewModel.navigateToTrackingTab.observeAsState()
    LaunchedEffect(navigateTrigger) {
        if (navigateTrigger != null) {
            if (screenMode == ScreenMode.TRACKING) {
                pagerState.scrollToPage(1)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Get the current view info
            val currentViewInfo = if (screenMode != ScreenMode.TRACKING) {
                trackingViews.getOrNull(pagerState.currentPage)
            } else if (pagerState.currentPage > 0) {
                trackingViews.getOrNull(pagerState.currentPage - 1)
            } else null

            Column {

                // 1. DYNAMIC HEADER (Config Mode or Tab Title)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding() // Pushes the header below the status bar
                    ) {

                        // TAB HEADER
                        when (screenMode) {
                            ScreenMode.TRACKING -> {
                                // Show the available Sensors
                                Surface(
                                    modifier = Modifier.padding(4.dp),
                                    color = Color.Transparent
                                ) {
                                    SensorStatus(activeSensors = activeSensors)
                                }
                            }

                            ScreenMode.CONFIGURATION -> {
                                // -- The full configuration screen for editing the name of the tab, checkboxes for lap button, map, and live segments, as well as the add/delete buttons
                                if (currentViewInfo != null) {
                                    TrackingTabConfigHeader(
                                        viewInfo = currentViewInfo,
                                        onUpdateTabName = { id, name -> trackingTabsViewModel.onUpdateTabName(id, name) },
                                        onAddTabRelative = { id, after -> trackingTabsViewModel.onAddTabRelative(id, after) },
                                        onDeleteTab = { id -> trackingTabsViewModel.onDeleteTab(id) },
                                        onUpdateShowMap = { id, show -> trackingTabsViewModel.onUpdateShowMap(id, show) },
                                        onUpdateShowLiveSegments = { id, show -> trackingTabsViewModel.onUpdateShowLiveSegments(id, show)},
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
                            containerColor = MaterialTheme.colorScheme.surface,
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
                                val targetPage =
                                    if (screenMode == ScreenMode.TRACKING) index + 1 else index
                                Tab(
                                    selected = pagerState.currentPage == targetPage,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                targetPage
                                            )
                                        }
                                    },
                                    text = { Text(view.name) }
                                )
                            }
                        }
                    }
                }

                // The Main content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(), // do not draw under the navigation bar
                    userScrollEnabled = true,
                    beyondViewportPageCount = if (screenMode == ScreenMode.TRACKING) trackingViews.size + 1 else trackingViews.size  // keep them all
                ) { page ->
                    if (screenMode == ScreenMode.TRACKING && page == 0) {

                        // --- CONTROL TAB (Page 0) ---
                        LaunchedEffect(Unit) {
                            controlViewModel.navigationEvent.collect { navigation ->
                                when (navigation) {
                                    is ControlNavigation.ToPairing -> {
                                        (context as? MainActivityWithNavigation)?.startPairing(
                                            navigation.protocol
                                        )
                                    }

                                    is ControlNavigation.ToEditDevice -> {
                                        val editDeviceDialog =
                                            EditDeviceFragmentFactory.create(
                                                deviceId = navigation.deviceId,
                                                deviceType = navigation.deviceType
                                            )
                                        editDeviceDialog.show(
                                            context.supportFragmentManager,
                                            "EditDeviceDialog"
                                        )
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
                            onPairingClicked = { controlViewModel.onPairingClicked(it) },
                        )
                    } else {
                        val viewIndex =
                            if (screenMode == ScreenMode.TRACKING) page - 1 else page

                        // Safely get the viewInfo
                        val viewInfo = trackingViews.getOrNull(viewIndex)

                        if (viewInfo != null) {
                            TrackingTabGridContent(
                                viewInfo.tabViewId,
                                screenMode,
                            )
                        } else {
                            // Optional: Show a placeholder or empty box while loading
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // --- Conditionally show the Lap Button
            val shouldShowLapButton = currentViewInfo?.showLapButton == true
            if (shouldShowLapButton) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding() // Respect system nav bar
                        .padding(bottom = 8.dp), // Space from bottom of screen
                    contentAlignment = Alignment.BottomCenter
                ) {
                    LapButton(
                        modifier = Modifier
                            .wrapContentSize() // Don't fill width anymore
                            .padding(horizontal = 16.dp),
                        trackingMode = trackingMode,
                        onClick = { trackingTabsViewModel.onLapButtonClick() }
                    )
                }
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