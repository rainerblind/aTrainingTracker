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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.view.WindowCompat
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.batterysaver.BatterySaverController
import com.atrainingtracker.trainingtracker.batterysaver.TelemetrySnapshot
import com.atrainingtracker.trainingtracker.batterysaver.DisplayBrightnessMode
import com.atrainingtracker.trainingtracker.batterysaver.calculateZoneIndex
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitThemeState
import com.atrainingtracker.trainingtracker.ui.tracking.LapSummaryDialog
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlNavigation
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreen
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatus
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingTabGridContent
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.ReturnNavigationRepository
import com.atrainingtracker.trainingtracker.ui.components.RouteSelectionButton
import com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModel
import android.location.Location
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
    var brightnessMode by remember { mutableStateOf(TrainingApplication.getDisplayBrightnessMode()) }
    var customBrightness by remember { mutableStateOf(TrainingApplication.getCustomDisplayBrightness()) }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val prefsListener = remember {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TrainingApplication.SP_COCKPIT_THEME_MODE) {
                cockpitThemeMode = TrainingApplication.getCockpitThemeMode()
            } else if (key == TrainingApplication.SP_DISPLAY_BRIGHTNESS_MODE || key == TrainingApplication.SP_BATTERY_SAVER) {
                brightnessMode = TrainingApplication.getDisplayBrightnessMode()
            } else if (key == TrainingApplication.SP_CUSTOM_DISPLAY_BRIGHTNESS) {
                customBrightness = TrainingApplication.getCustomDisplayBrightness()
            }
        }
    }
    val displaySettingsListener = remember {
        TrainingApplication.OnDisplaySettingsChangeListener {
            cockpitThemeMode = TrainingApplication.getCockpitThemeMode()
            brightnessMode = TrainingApplication.getDisplayBrightnessMode()
            customBrightness = TrainingApplication.getCustomDisplayBrightness()
        }
    }
    DisposableEffect(prefs, prefsListener, displaySettingsListener) {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        TrainingApplication.addDisplaySettingsChangeListener(displaySettingsListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            TrainingApplication.removeDisplaySettingsChangeListener(displaySettingsListener)
        }
    }

    val batterySaverController = remember { BatterySaverController(activity = context) }
    LaunchedEffect(brightnessMode, customBrightness) {
        batterySaverController.setMode(brightnessMode, customBrightness)
    }
    DisposableEffect(batterySaverController) {
        onDispose {
            batterySaverController.release()
        }
    }

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
    val hasPairedRemoteDevices by controlViewModel.hasPairedRemoteDevices.collectAsState()
    val locationCalibrationStatus by trackingTabsViewModel.locationCalibrationStatus.collectAsState()

    // Battery Saver Telemetry & Event Subscriptions
    val tuningDataStore = remember { com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore(context) }
    val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(
        initial = com.atrainingtracker.trainingtracker.settings.TuningConfig()
    )

    // Route Selection and Navigation state for Control Tracking screen (REQ-UI-279 / ATT-2458, REQ-UI-281 / ATT-2460)
    val routesRepo = remember { RoutesRepository.getInstance(context) }
    val routeSelectorViewModel: RouteSelectorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RouteSelectorViewModel(routesRepo, tuningPreferencesDataStore = tuningDataStore) as T
            }
        }
    )
    val routeSelectorUiState by routeSelectorViewModel.uiState.collectAsState()
    var showRouteSelectorSheet by remember { mutableStateOf(false) }

    val returnNavRepo = remember { ReturnNavigationRepository.getInstance(context) }
    val returnNavState by returnNavRepo.navigationState.collectAsState()

    val currentLatLng by controlViewModel.banalServiceRepository.currentLocation.collectAsState()
    LaunchedEffect(currentLatLng) {
        currentLatLng?.let { latLng ->
            val location = Location("GPS").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
            }
            routeSelectorViewModel.onLocationChanged(location)
        }
    }

    LaunchedEffect(tuningConfig) {
        batterySaverController.updateTuningConfig(
            com.atrainingtracker.trainingtracker.batterysaver.BatterySaverTuningConfig(
                fullDimFactor = tuningConfig.fullDimFactor,
                mediumDimFactor = tuningConfig.mediumDimFactor,
                slopeFlatThreshold = tuningConfig.slopeFlatThreshold,
                slopeSteepThreshold = tuningConfig.slopeSteepThreshold,
                wakeupDurationMs = tuningConfig.wakeupDurationSec * 1000L,
                downwardHysteresisMs = tuningConfig.downwardDelaySec * 1000L
            )
        )
    }

    val filteredSensorData by trackingTabsViewModel.allFilteredSensorData.collectAsState()
    val activityType by trackingTabsViewModel.activityType.collectAsState()
    val liveSegments by trackingTabsViewModel.liveSegments.collectAsState()

    LaunchedEffect(filteredSensorData, activityType, brightnessMode, tuningConfig) {
        if (brightnessMode != DisplayBrightnessMode.AUTO) return@LaunchedEffect

        val speedData = filteredSensorData.find { it.sensorType == SensorType.SPEED_mps }
        val speed = (speedData?.value as? Number)?.toDouble() ?: 0.0

        val slopeData = filteredSensorData.find { it.sensorType == SensorType.SLOPE }
        val rawSlope = (slopeData?.value as? Number)?.toFloat()
        val slope = if (speed > tuningConfig.slopeMinSpeedMps && rawSlope != null && !rawSlope.isNaN() && !rawSlope.isInfinite()) rawSlope else 0.0f

        val hrData = filteredSensorData.find { it.sensorType == SensorType.HR }
        val hrValue = (hrData?.value as? Number)?.toDouble()

        val powerData = filteredSensorData.find { it.sensorType == SensorType.POWER }
        val powerValue = (powerData?.value as? Number)?.toDouble()

        val isCycling = activityType.sportType == BSportType.BIKE
        val hrZone = if (hrValue != null && hrValue > 0) {
            val zoneType = if (isCycling) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN
            calculateZoneIndex(context, zoneType, hrValue)
        } else null

        val powerZone = if (isCycling && powerValue != null && powerValue > 0) {
            calculateZoneIndex(context, SettingsDataStore.ZoneType.PWR_BIKE, powerValue)
        } else null

        batterySaverController.updateTelemetry(
            TelemetrySnapshot(
                slopePercent = slope,
                hrZone = hrZone,
                powerZone = powerZone,
                isCycling = isCycling
            )
        )
    }

    LaunchedEffect(trackingMode) {
        batterySaverController.onWakeupEvent()
        val isTracking = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED
        routeSelectorViewModel.setTrackingActive(isTracking)
    }

    var lastActiveSegmentStatus by remember { mutableStateOf<Map<Long, LiveSegmentStatus>>(emptyMap()) }
    LaunchedEffect(liveSegments) {
        val currentStatusMap: Map<Long, LiveSegmentStatus> = liveSegments.associate { 
            it.staticData.summary.stravaId to it.liveData.segmentStatus 
        }
        var hasRelevantTransition = false
        for ((id, status) in currentStatusMap) {
            val previous = lastActiveSegmentStatus[id]
            if (previous != status) {
                if (status == LiveSegmentStatus.APPROACHING ||
                    status == LiveSegmentStatus.ON_SEGMENT ||
                    status == LiveSegmentStatus.FINISHED) {
                    hasRelevantTransition = true
                    break
                }
            }
        }
        lastActiveSegmentStatus = currentStatusMap
        if (hasRelevantTransition) {
            batterySaverController.onWakeupEvent()
        }
    }

    val navRepo = remember { TurnByTurnNavigationRepository.getInstance(context) }
    val navState by navRepo.navigationState.collectAsState()
    var lastTurnApproaching by remember { mutableStateOf(false) }
    var lastOffRoute by remember { mutableStateOf(false) }
    LaunchedEffect(navState.isApproaching, navState.isOffRoute) {
        if ((!lastTurnApproaching && navState.isApproaching) || (!lastOffRoute && navState.isOffRoute)) {
            batterySaverController.onWakeupEvent()
        }
        lastTurnApproaching = navState.isApproaching
        lastOffRoute = navState.isOffRoute
    }


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

    val cockpitThemeState = resolveEffectiveCockpitThemeState(
        screenMode = screenMode,
        currentPage = pagerState.currentPage,
        cockpitThemeMode = cockpitThemeMode,
        isSystemDark = isSystemDark
    )

    val scope = rememberCoroutineScope()

    // BACK NAVIGATION HANDLER: CONFIG -> PREVIEW -> TRACKING (ATT-245 / ATT-1456)
    BackHandler(enabled = screenMode == ScreenMode.CONFIGURATION) {
        trackingTabsViewModel.handleBackPressToPreview()
    }
    BackHandler(enabled = screenMode == ScreenMode.PREVIEW) {
        trackingTabsViewModel.exitConfiguration()
    }

    // -- Show Lap Summary Dialog
    val lapEvent by trackingTabsViewModel.lapEvent.observeAsState()
    LaunchedEffect(lapEvent) {
        if (lapEvent != null) {
            batterySaverController.onWakeupEvent()
        }
    }
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
                cockpitThemeMode = TrainingApplication.getCockpitThemeMode()
                brightnessMode = TrainingApplication.getDisplayBrightnessMode()
                customBrightness = TrainingApplication.getCustomDisplayBrightness()
                batterySaverController.setMode(brightnessMode, customBrightness)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, isSystemDark) {
        onDispose {
            if (!context.isFinishing && !context.isDestroyed) {
                context.window?.let { window ->
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isSystemDark
                    insetsController.isAppearanceLightNavigationBars = !isSystemDark
                }
            }
        }
    }

    ATrainingTrackerTheme(
        darkTheme = cockpitThemeState.darkTheme,
        amoled = cockpitThemeState.amoled
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                batterySaverController.onWakeupEvent()
                            }
                        }
                    }
            ) {

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
                                        onToggleMode = { trackingTabsViewModel.toggleScreenMode() }
                                    )
                                }
                            }

                            ScreenMode.PREVIEW -> {
                                if (currentViewInfo != null) {
                                    TrackingTabPreviewHeader(
                                        viewInfo = currentViewInfo,
                                        onToggleMode = { trackingTabsViewModel.toggleScreenMode() },
                                        onExitConfig = { trackingTabsViewModel.exitConfiguration() }
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
                                val isSelected = pagerState.currentPage == 0
                                Tab(
                                    selected = isSelected,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    text = {
                                        // Dynamic Title for Control Tab (Tracking/Paused/Start)
                                        Text(
                                            text = getControlTabTitle(trackingMode),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                            trackingViews.forEachIndexed { index, view ->
                                val targetPage =
                                    if (screenMode == ScreenMode.TRACKING) index + 1 else index
                                val isSelected = pagerState.currentPage == targetPage
                                Tab(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                targetPage
                                            )
                                        }
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    text = {
                                        Text(
                                            text = view.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
                            onCancelDeviceTypeSelection = { controlViewModel.onCancelDeviceTypeSelection() },
                            showResearchButton = hasPairedRemoteDevices,
                            locationCalibrationStatus = locationCalibrationStatus,
                            bottomContent = {
                                val isDimmed = routeSelectorUiState.activeRoute == null && routeSelectorUiState.routes.isEmpty()
                                val emptySubtitleRes = if (isDimmed) {
                                    routeSelectorUiState.contextEmptyHintRes
                                } else {
                                    R.string.route_action_select_desc
                                }
                                RouteSelectionButton(
                                    activeRoute = routeSelectorUiState.activeRoute,
                                    isDimmed = isDimmed,
                                    emptySubtitleRes = emptySubtitleRes,
                                    returnNavState = returnNavState,
                                    onClick = { showRouteSelectorSheet = true },
                                    onClearRoute = { routeSelectorViewModel.clearRoute() }
                                )
                            }
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

            // --- Conditionally show the Lap Button (suppressed in CONFIGURATION mode per REQ-UI-295)
            val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
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

            // Modal Bottom Sheet for Route Selector (REQ-UI-279, REQ-UI-280, REQ-UI-282 / ATT-2459, ATT-2462)
            if (showRouteSelectorSheet) {
                val isMidRide = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED
                RouteSelectorModalBottomSheet(
                    viewModel = routeSelectorViewModel,
                    onDismiss = { showRouteSelectorSheet = false },
                    isMidRide = isMidRide,
                    onTakeMeHome = {
                        returnNavRepo.startTakeMeHome()
                        showRouteSelectorSheet = false
                    }
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