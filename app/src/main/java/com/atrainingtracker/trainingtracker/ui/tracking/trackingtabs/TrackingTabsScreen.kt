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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.preference.PreferenceManager
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceFragmentFactory
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
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

    val isSystemDark = isSystemInDarkTheme()
    var cockpitThemeMode by remember { mutableStateOf(TrainingApplication.getCockpitThemeMode()) }
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TrainingApplication.SP_COCKPIT_THEME_MODE) {
                cockpitThemeMode = TrainingApplication.getCockpitThemeMode()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val isCockpitDark = resolveEffectiveCockpitDarkTheme(cockpitThemeMode, isSystemDark)

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
    val selectingProtocol by controlViewModel.selectingProtocol.collectAsState()


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

    // BACK NAVIGATION HANDLER: CONFIG -> PREVIEW (ATT-245)
    // PREVIEW -> FINISH is handled by the Activity
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        trackingTabsViewModel.handleBackPressToPreview()
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


    val currentPagerState by rememberUpdatedState(pagerState)
    val currentScreenMode by rememberUpdatedState(screenMode)

    // NAVIGATION COLLECTION (necessary, when deleting tabs or tracking starts)
    LaunchedEffect(Unit) {
        Log.i(TAG, "LaunchedEffect(Unit) started collecting navigationEvent")
        trackingTabsViewModel.navigationEvent.collect { tabNavigationEvent ->
            Log.i(TAG, "navigationEvent received: $tabNavigationEvent, currentScreenMode=$currentScreenMode, pageCount=${currentPagerState.pageCount}, currentPage=${currentPagerState.currentPage}")
            when (tabNavigationEvent) {
                is TabNavigationEvent.NavigateTo -> {
                    // Calculate offset: if TRACKING mode, page 0 is Control, so add 1
                    val offset = if (currentScreenMode == ScreenMode.TRACKING) 1 else 0
                    val target = tabNavigationEvent.index + offset
                    Log.i(TAG, "Navigating to target=$target (offset=$offset)")

                    lastKnownPage = target

                    // If pager hasn't loaded enough pages yet, wait for pageCount to be ready
                    if (currentPagerState.pageCount <= target) {
                        try {
                            kotlinx.coroutines.withTimeout(2000) {
                                androidx.compose.runtime.snapshotFlow { currentPagerState.pageCount }
                                    .collect { count ->
                                        if (count > target) {
                                            throw kotlinx.coroutines.CancellationException("PageCountReady")
                                        }
                                    }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            if (e.message != "PageCountReady") throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Timed out waiting for pageCount > $target", e)
                        }
                    }

                    if (target in 0 until currentPagerState.pageCount) {
                        try {
                            currentPagerState.animateScrollToPage(target)
                            Log.i(TAG, "Successfully animated to page $target")
                        } catch (e: Exception) {
                            Log.e(TAG, "animateScrollToPage failed: page $target not ready yet", e)
                            try {
                                currentPagerState.scrollToPage(target)
                                Log.i(TAG, "Fallback scrollToPage succeeded for page $target")
                            } catch (e2: Exception) {
                                Log.e(TAG, "Fallback scrollToPage also failed", e2)
                            }
                        }
                    } else {
                        Log.e(TAG, "Cannot scroll to target $target: pageCount is ${currentPagerState.pageCount}")
                    }
                }
                is TabNavigationEvent.EditDevice -> {
                    val editDeviceDialog = EditDeviceFragmentFactory.create(
                        deviceId = tabNavigationEvent.deviceId,
                        deviceType = com.atrainingtracker.banalservice.devices.DeviceType.ALL
                    )
                    editDeviceDialog.show(
                        context.supportFragmentManager,
                        "EditDeviceDialog"
                    )
                }
            }
        }
    }


    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                trackingTabsViewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding() // Pushes the header below the status bar
                    ) {

                        // TAB HEADER
                        when (screenMode) {
                            ScreenMode.TRACKING -> {
                                // Collect source info
                                val activeSensors by trackingTabsViewModel.activeSensors.collectAsState()
                                val sensorSourceMapping by trackingTabsViewModel.sensorSourceMapping.collectAsState()
                                val allTelemetry by trackingTabsViewModel.allTelemetry.collectAsState()
                                val allDevices by trackingTabsViewModel.allDevices.collectAsState()

                                // Show the available Sensors
                                Surface(
                                    modifier = Modifier.padding(4.dp),
                                    color = Color.Transparent
                                ) {
                                    SensorStatus(
                                        activeSensors = activeSensors,
                                        sourceMapping = sensorSourceMapping,
                                        allTelemetry = allTelemetry,
                                        allDevices = allDevices,
                                        onDeviceClick = { trackingTabsViewModel.onEditDevice(it) },
                                        onMenuClick = {
                                            (context as? MainActivityWithNavigation)?.openDrawer()
                                        }
                                    )
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
                                        onUpdateShowElevationProfile = { id, show -> trackingTabsViewModel.onUpdateShowElevationProfile(id, show) },
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                            navigation.protocol,
                                            navigation.deviceType
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
                            selectingProtocol = selectingProtocol,
                            onDeviceTypeSelected = { controlViewModel.onDeviceTypeSelected(it) },
                            onCancelDeviceTypeSelection = { controlViewModel.onCancelDeviceTypeSelection() }
                        )
                    } else {
                        val viewIndex =
                            if (screenMode == ScreenMode.TRACKING) page - 1 else page

                        // Safely get the viewInfo
                        val viewInfo = trackingViews.getOrNull(viewIndex)

                        if (viewInfo != null) {
                            ATrainingTrackerTheme(darkTheme = isCockpitDark) {
                                TrackingTabGridContent(
                                    viewInfo.tabViewId,
                                    screenMode,
                                )
                            }
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
                    ATrainingTrackerTheme(darkTheme = isCockpitDark) {
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
}

@Composable
fun getControlTabTitle(mode: TrackingMode): String {
    return when (mode) {
        TrackingMode.PAUSED -> stringResource(R.string.Paused)
        TrackingMode.TRACKING -> stringResource(R.string.Tracking)
        else -> stringResource(R.string.tab_start)
    }
}