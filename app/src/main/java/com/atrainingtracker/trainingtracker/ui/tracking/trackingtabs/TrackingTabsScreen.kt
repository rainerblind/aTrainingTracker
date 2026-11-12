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

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.atrainingtracker.util.safeUnregisterReceiver
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.CockpitStatus
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.NonRecordingStatusBanner
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridStatusResolver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.zIndex
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
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.theme.getBatterySnackbarColors
import com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.batterysaver.BatterySaverController
import com.atrainingtracker.trainingtracker.batterysaver.TelemetrySnapshot
import com.atrainingtracker.trainingtracker.batterysaver.DisplayBrightnessMode
import com.atrainingtracker.trainingtracker.batterysaver.calculateZoneIndex
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentStatus
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.CockpitThemeMode
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitDarkTheme
import com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.CopyTrackingTabDialog
import com.atrainingtracker.trainingtracker.ui.theme.resolveEffectiveCockpitThemeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator
import com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlay
import com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarLightQuickSwitchSheet
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
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingJourneySheet
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "TrackingTabsScreen"
private val DEBUG = BuildConfig.DEBUG

@Composable
fun TrackingTabsScreen(
    trackingTabsViewModel: TrackingTabsViewModel,
    onNavigateToHrvTest: () -> Unit = {},
    onNavigateToMorningHrv: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

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

    val batterySaverController = remember(activity) { BatterySaverController(activity = activity) }
    LaunchedEffect(brightnessMode, customBrightness) {
        batterySaverController.setMode(brightnessMode, customBrightness)
    }
    DisposableEffect(batterySaverController) {
        onDispose {
            batterySaverController.release()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val viewSensorsLabel = stringResource(R.string.sensor_battery_action_view_sensors)
    LaunchedEffect(Unit) {
        WorkoutNavigationEvents.lowBatteryAlert.collect { alert ->
            if (alert != null) {
                if (alert.severity == WorkoutNavigationEvents.BatteryAlertSeverity.CRITICAL) {
                    val currentVisuals = snackbarHostState.currentSnackbarData?.visuals as? WorkoutNavigationEvents.BatterySnackbarVisuals
                    if (currentVisuals != null && currentVisuals.severity == WorkoutNavigationEvents.BatteryAlertSeverity.WARNING) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }
                val visuals = WorkoutNavigationEvents.BatterySnackbarVisuals(
                    message = alert.message,
                    actionLabel = viewSensorsLabel,
                    duration = SnackbarDuration.Long,
                    severity = alert.severity
                )
                val result = snackbarHostState.showSnackbar(visuals)
                if (result == SnackbarResult.ActionPerformed) {
                    (activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)
                }
                WorkoutNavigationEvents.consumeLowBatteryAlert()
            }
        }
    }

    val trackingViews by trackingTabsViewModel.trackingViews.collectAsState(initial = emptyList())
    val trackingMode by trackingTabsViewModel.trackingMode.observeAsState(TrackingMode.READY)
    val screenMode by trackingTabsViewModel.screenMode.collectAsState()

    var isAutoPaused by remember { mutableStateOf(TrainingApplication.isAutoPaused()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                isAutoPaused = TrainingApplication.isAutoPaused()
            }
        }
        val filter = IntentFilter(TrainingApplication.TRACKING_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.safeUnregisterReceiver(receiver, "TrackingTabsScreen")
        }
    }
    val cockpitStatus = remember(screenMode, trackingMode, isAutoPaused) {
        SensorGridStatusResolver.resolveCockpitStatus(screenMode, trackingMode, isAutoPaused)
    }
    val onBannerAction: (CockpitStatus) -> Unit = { status ->
        val intent = Intent(status.intentAction).apply {
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }

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
                val application = activity?.application ?: (context.applicationContext as Application)
                return ControlTrackingViewModel(application) as T
            }
        }
    )
    // Collect the states required by ControlTrackingScreen
    val searchingFor by controlViewModel.searchingForDevice.collectAsState()
    val devices by controlViewModel.remoteDevices.collectAsState()
    val activeSensors by controlViewModel.activeSensors.collectAsState()
    val bSportType by controlViewModel.bSportType.collectAsState()
    val hasPairedRemoteDevices by controlViewModel.hasPairedRemoteDevices.collectAsState()
    val hasPairedBluetoothSensors by controlViewModel.hasPairedBluetoothSensors.collectAsState()
    val isMorningCheckVisible by controlViewModel.isMorningHrvCheckVisible.collectAsState()
    val locationCalibrationStatus by trackingTabsViewModel.locationCalibrationStatus.collectAsState()

    // Subscription Downgrade Quota Reconciliation Check (REQ-SET-084.5)
    LaunchedEffect(Unit) {
        try {
            com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManager.getInstance(context).checkDowngradeReconciliation()
        } catch (e: Throwable) {
            // Defensive guard for testing environments
        }
    }

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
                return RouteSelectorViewModel(
                    routesRepository = routesRepo,
                    tuningPreferencesDataStore = tuningDataStore,
                    banalServiceRepository = controlViewModel.banalServiceRepository
                ) as T
            }
        }
    )
    val routeSelectorUiState by routeSelectorViewModel.uiState.collectAsState()
    var showRouteSelectorSheet by remember { mutableStateOf(false) }
    var showSportingJourneySheet by remember { mutableStateOf(false) }

    val returnNavRepo = remember { ReturnNavigationRepository.getInstance(context) }
    val returnNavState by returnNavRepo.navigationState.collectAsState()
    val radarState by RadarStateRepository.radarState.collectAsState()
    var showRadarLightQuickSwitch by remember { mutableStateOf(false) }

    val currentLatLng by controlViewModel.banalServiceRepository.currentLocation.collectAsState()
    LaunchedEffect(currentLatLng) {
        currentLatLng?.let { latLng ->
            val location = Location("GPS").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                controlViewModel.banalServiceRepository.currentBearing.value?.let {
                    bearing = it.toFloat()
                }
                controlViewModel.banalServiceRepository.currentSpeed.value?.let {
                    speed = it.toFloat()
                }
            }
            routeSelectorViewModel.onLocationChanged(location)
        }
    }

    LaunchedEffect(bSportType) {
        routeSelectorViewModel.setActiveSport(bSportType)
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
            val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
            val z2 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
            val z3 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
            val z4 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
            calculateZoneIndex(hrValue, listOf(z1, z2, z3, z4))
        } else null

        val powerZone = if (isCycling && powerValue != null && powerValue > 0) {
            val zoneType = SettingsDataStore.ZoneType.PWR_BIKE
            val z1 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
            val z2 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
            val z3 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
            val z4 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
            val z5 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 5)
            val z6 = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 6)
            calculateZoneIndex(powerValue, listOf(z1, z2, z3, z4, z5, z6))
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
    var previousScreenMode by remember { androidx.compose.runtime.mutableStateOf(screenMode) }
    var previousActivityType by remember { androidx.compose.runtime.mutableStateOf(activityType) }

    // Calculate the initial page for the NEW state (REQ-UI-435, TST-UI-435, ATT-4838)
    val initialPage = remember(activityType, trackingViews.size, screenMode) {
        val resolved = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = previousScreenMode,
            newMode = screenMode,
            lastKnownPage = lastKnownPage,
            tabCount = trackingViews.size,
            activityTypeChanged = activityType != previousActivityType
        )
        previousScreenMode = screenMode
        previousActivityType = activityType
        lastKnownPage = resolved
        resolved
    }

    // Recreate the PagerState using the preserved initialPage
    val pagerState = key(activityType, trackingViews.size, screenMode) {
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { pageCount }
        )
    }
    // Update the tracker whenever the pager settles on a new page
    LaunchedEffect(pagerState.currentPage) {
        lastKnownPage = pagerState.currentPage
        trackingTabsViewModel.onPageSelected(pagerState.currentPage)
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

    val copySourceTabId by trackingTabsViewModel.copySourceTabId.collectAsState()


    val currentPagerState by rememberUpdatedState(pagerState)
    val currentScreenMode by rememberUpdatedState(screenMode)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // NAVIGATION COLLECTION (necessary, when deleting tabs or tracking starts)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            Log.i(TAG, "LaunchedEffect(lifecycleOwner) started collecting navigationEvent")
            trackingTabsViewModel.navigationEvent.collect { tabNavigationEvent ->
                Log.i(TAG, "navigationEvent received: $tabNavigationEvent, currentScreenMode=$currentScreenMode, pageCount=${currentPagerState.pageCount}, currentPage=${currentPagerState.currentPage}")
                when (tabNavigationEvent) {
                    is TabNavigationEvent.NavigateTo -> {
                        navigateToPagerTarget(
                            tabIndex = tabNavigationEvent.index,
                            screenMode = currentScreenMode,
                            currentPageCount = { currentPagerState.pageCount },
                            pageCountFlow = androidx.compose.runtime.snapshotFlow { currentPagerState.pageCount },
                            onUpdateLastKnownPage = { lastKnownPage = it },
                            animateScrollToPage = { currentPagerState.animateScrollToPage(it) },
                            scrollToPage = { currentPagerState.scrollToPage(it) }
                        )
                    }
                    is TabNavigationEvent.EditDevice -> {
                        if (activity != null && activity.canShowDialogFragments()) {
                            val editDeviceDialog = EditDeviceFragmentFactory.create(
                                deviceId = tabNavigationEvent.deviceId,
                                deviceType = com.atrainingtracker.banalservice.devices.DeviceType.ALL
                            )
                            editDeviceDialog.show(
                                activity.supportFragmentManager,
                                "EditDeviceDialog"
                            )
                        } else {
                            Log.w(TAG, "Skipping EditDeviceDialog: host activity unavailable or state already saved")
                        }
                    }
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                trackingTabsViewModel.onResume()
                controlViewModel.refreshMorningCheckStatus()
                cockpitThemeMode = TrainingApplication.getCockpitThemeMode()
                brightnessMode = TrainingApplication.getDisplayBrightnessMode()
                customBrightness = TrainingApplication.getCustomDisplayBrightness()
                batterySaverController.setMode(brightnessMode, customBrightness)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity, isSystemDark) {
        onDispose {
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.window?.let { window ->
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isSystemDark
                    insetsController.isAppearanceLightNavigationBars = !isSystemDark
                }
            }
        }
    }

    // Hardware Volume Button Cockpit Tab Cycling (REQ-TRK-054.2, REQ-TRK-054.3, ATT-5905)
    val mainActivity = activity as? MainActivityWithNavigation
    DisposableEffect(mainActivity, screenMode, pageCount) {
        if (mainActivity != null && screenMode == ScreenMode.TRACKING && pageCount > 0) {
            mainActivity.hardwareKeyInterceptor = { event ->
                if (TrainingApplication.volumeKeysSwitchCockpitTabs() &&
                    (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                ) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val forward = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        val target = TrackingTabsPageResolver.resolveNextPage(
                            currentPage = pagerState.targetPage,
                            pageCount = pageCount,
                            forward = forward
                        )
                        scope.launch {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                    true // Consume both ACTION_DOWN and ACTION_UP to suppress Android's audio volume slider HUD
                } else {
                    false
                }
            }
        }
        onDispose {
            if (mainActivity?.hardwareKeyInterceptor != null) {
                mainActivity.hardwareKeyInterceptor = null
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

            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isLandscapeTracking = isLandscape && screenMode == ScreenMode.TRACKING
            val isControlTab = screenMode == ScreenMode.TRACKING && pagerState.currentPage == 0

            var showFloatingTabRow by remember { mutableStateOf(false) }
            var topTapTrigger by remember { mutableStateOf(0L) }

            LaunchedEffect(pagerState.isScrollInProgress, pagerState.currentPage, topTapTrigger) {
                if (isLandscapeTracking) {
                    if (isControlTab || pagerState.isScrollInProgress) {
                        showFloatingTabRow = true
                    } else {
                        showFloatingTabRow = true
                        delay(2500)
                        showFloatingTabRow = false
                    }
                }
            }

            val isFloatingTabRowVisible = isControlTab || showFloatingTabRow

            val tabRowContent: @Composable () -> Unit = {
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

            Column {

                if (isLandscapeTracking) {
                    // Landscape ScreenMode.TRACKING: compact status bar strip with centered sensor icons and zero permanent tab row (REQ-UI-434.1, REQ-UI-434.2)
                    val activeSensors by trackingTabsViewModel.activeSensors.collectAsState()
                    val sensorSourceMapping by trackingTabsViewModel.sensorSourceMapping.collectAsState()
                    val allTelemetry by trackingTabsViewModel.allTelemetry.collectAsState()
                    val allDevices by trackingTabsViewModel.allDevices.collectAsState()
                    val currentAltitude by trackingTabsViewModel.currentAltitude.collectAsState()

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { topTapTrigger = System.currentTimeMillis() }
                    ) {
                        SensorStatus(
                            activeSensors = activeSensors,
                            sourceMapping = sensorSourceMapping,
                            allTelemetry = allTelemetry,
                            allDevices = allDevices,
                            currentAltitude = currentAltitude,
                            matchedLocation = locationCalibrationStatus,
                            onDeviceClick = { trackingTabsViewModel.onEditDevice(it) },
                            onCalibrateAltitude = { alt, name, save ->
                                trackingTabsViewModel.calibrateAltitude(alt, name, save)
                            },
                            onMenuClick = {
                                (activity as? MainActivityWithNavigation)?.openDrawer()
                            },
                            onSupporterClick = {
                                showSportingJourneySheet = true
                            },
                            isCompactLandscape = true
                        )
                    }
                } else {
                    // Portrait Mode or Non-Tracking Modes: preserved statusBarsPadding and stacked headers (REQ-UI-434.3)
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
                                    val currentAltitude by trackingTabsViewModel.currentAltitude.collectAsState()

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
                                            currentAltitude = currentAltitude,
                                            matchedLocation = locationCalibrationStatus,
                                            onDeviceClick = { trackingTabsViewModel.onEditDevice(it) },
                                            onCalibrateAltitude = { alt, name, save ->
                                                trackingTabsViewModel.calibrateAltitude(alt, name, save)
                                            },
                                            onMenuClick = {
                                                (activity as? MainActivityWithNavigation)?.openDrawer()
                                            },
                                            onSupporterClick = {
                                                showSportingJourneySheet = true
                                            }
                                        )
                                    }
                                }

                                ScreenMode.CONFIGURATION -> {
                                    // -- The full configuration screen for editing the name of the tab, checkboxes for lap button, map, and live segments, as well as the add/delete buttons
                                    if (currentViewInfo != null) {
                                        val sportName = stringResource(id = activityType.titleId)
                                        TrackingTabConfigHeader(
                                            viewInfo = currentViewInfo,
                                            tabIndex = pagerState.currentPage,
                                            tabCount = trackingViews.size,
                                            sportName = sportName,
                                            onUpdateTabName = { id, name -> trackingTabsViewModel.onUpdateTabName(id, name) },
                                            onAddTabRelative = { id, after -> trackingTabsViewModel.onAddTabRelative(id, after) },
                                            onDeleteTab = { id -> trackingTabsViewModel.onDeleteTab(id) },
                                            onCopyTab = { id -> trackingTabsViewModel.onOpenCopyTabDialog(id) },
                                            onMoveTab = { id, moveLeft -> trackingTabsViewModel.onMoveTab(id, moveLeft) },
                                            onToggleMode = { trackingTabsViewModel.toggleScreenMode() }
                                        )
                                    }
                                }

                                ScreenMode.PREVIEW -> {
                                    if (currentViewInfo != null) {
                                        val sportName = stringResource(id = activityType.titleId)
                                        TrackingTabPreviewHeader(
                                            viewInfo = currentViewInfo,
                                            onToggleMode = { trackingTabsViewModel.toggleScreenMode() },
                                            onExitConfig = { trackingTabsViewModel.exitConfiguration() },
                                            sportName = sportName
                                        )
                                    }
                                }
                            }

                            // TAB ROW
                            tabRowContent()
                        }
                    }
                }

                // First-time Stop Tracking Coachmark state & auto-acknowledgement (REQ-UI-367 / ATT-3387)
                val hasSeenStopTrackingHint: Boolean? by tuningDataStore.hasSeenStopTrackingHintFlow.collectAsState(initial = null)
                val hasRenderedCoachmarkInSession by trackingTabsViewModel.hasRenderedCoachmarkInSession.collectAsState()
                val isCoachmarkDismissPending by trackingTabsViewModel.isCoachmarkDismissPending.collectAsState()

                val showStopTrackingCoachmark = TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                    trackingMode = trackingMode,
                    currentPage = pagerState.currentPage,
                    screenMode = screenMode,
                    hasSeenHint = hasSeenStopTrackingHint,
                    isDismissPending = isCoachmarkDismissPending
                )

                LaunchedEffect(
                    pagerState.settledPage,
                    pagerState.isScrollInProgress,
                    showStopTrackingCoachmark
                ) {
                    if (showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress) {
                        trackingTabsViewModel.markCoachmarkRenderedInSession()
                    }
                }

                LaunchedEffect(
                    pagerState.currentPage,
                    pagerState.settledPage,
                    pagerState.isScrollInProgress,
                    trackingMode,
                    hasSeenStopTrackingHint,
                    hasRenderedCoachmarkInSession,
                    isCoachmarkDismissPending
                ) {
                    if (TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                            trackingMode = trackingMode,
                            currentPage = pagerState.currentPage,
                            settledPage = pagerState.settledPage,
                            isScrollInProgress = pagerState.isScrollInProgress,
                            hasSeenHint = hasSeenStopTrackingHint,
                            hasRenderedCoachmarkInSession = hasRenderedCoachmarkInSession,
                            isDismissPending = isCoachmarkDismissPending
                        )
                    ) {
                        trackingTabsViewModel.markCoachmarkDismissPending()
                        tuningDataStore.setHasSeenStopTrackingHint(true)
                    }
                }

                // The Main content hosted inside a constant-weight Box so coachmark overlay never shifts or resizes HorizontalPager
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            LaunchedEffect(lifecycleOwner) {
                                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    controlViewModel.navigationEvent.collect { navigation ->
                                        when (navigation) {
                                            is ControlNavigation.ToEditDevice -> {
                                                if (activity != null && activity.canShowDialogFragments()) {
                                                    val editDeviceDialog =
                                                        EditDeviceFragmentFactory.create(
                                                            deviceId = navigation.deviceId,
                                                            deviceType = navigation.deviceType
                                                        )
                                                    editDeviceDialog.show(
                                                        activity.supportFragmentManager,
                                                        "EditDeviceDialog"
                                                    )
                                                } else {
                                                    Log.w(TAG, "Skipping EditDeviceDialog from ControlTab: host activity unavailable or state already saved")
                                                }
                                            }

                                            is ControlNavigation.ShowRecoveryDialog -> {
                                                val recoveryHandler = activity as? StartOrResumeInterface
                                                if (recoveryHandler != null) {
                                                    if (activity.canShowDialogFragments()) {
                                                        recoveryHandler.showStartOrResumeDialog()
                                                    } else {
                                                        Log.w(TAG, "Skipping recovery dialog via handler: host activity state already saved")
                                                    }
                                                } else if (
                                                    activity != null &&
                                                    activity.canShowDialogFragments() &&
                                                    activity.supportFragmentManager.findFragmentByTag(StartOrResumeDialog.TAG) == null
                                                ) {
                                                    StartOrResumeDialog().show(
                                                        activity.supportFragmentManager,
                                                        StartOrResumeDialog.TAG
                                                    )
                                                } else {
                                                    Log.w(TAG, "Skipping StartOrResumeDialog: host activity unavailable or state already saved")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            ControlTrackingScreen(
                                trackingMode = trackingMode,
                                searchingFor = searchingFor,
                                devices = devices,
                                currentSport = bSportType,
                                onSearch = { controlViewModel.onSearchClicked() },
                                onDeviceClick = { controlViewModel.onDeviceClicked(it) },
                                onSportSelected = { controlViewModel.setSport(it) },
                                onStart = { controlViewModel.onStartTracking() },
                                onPause = { controlViewModel.onPauseTracking() },
                                onResume = { controlViewModel.onResumeTracking() },
                                onStop = { controlViewModel.onStopTracking() },
                                showResearchButton = hasPairedRemoteDevices,
                                hasPairedBluetoothSensors = hasPairedBluetoothSensors,
                                locationCalibrationStatus = locationCalibrationStatus,
                                onPairSensor = { (activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors) },
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
                                    if (isMorningCheckVisible) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = onNavigateToMorningHrv,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("morning_hrv_check_start_button"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = TTColor.HrvHeartRateRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.hrv_morning_check_start_button))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onNavigateToHrvTest,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hrv_test_cockpit_launcher"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = TTColor.HrvHeartRateRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.hrv_test_title))
                                    }
                                }
                            )
                        } else {
                            val viewIndex =
                                if (screenMode == ScreenMode.TRACKING) page - 1 else page

                            // Safely get the viewInfo
                            val viewInfo = trackingViews.getOrNull(viewIndex)

                            if (viewInfo != null) {
                                TrackingTabGridContent(
                                    tabViewId = viewInfo.tabViewId,
                                    screenMode = screenMode,
                                    isTabActive = pagerState.currentPage == page,
                                    onRingBikeBell = { trackingTabsViewModel.triggerBellRing(isLongPress = false) }
                                )
                            } else {
                                // Optional: Show a placeholder or empty box while loading
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }

                    if (isLandscapeTracking) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isFloatingTabRowVisible,
                            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { -it },
                            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                                shadowElevation = 4.dp
                            ) {
                                tabRowContent()
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showStopTrackingCoachmark,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(2f),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        StopTrackingCoachmarkBanner(
                            onShowControlTab = {
                                trackingTabsViewModel.markCoachmarkDismissPending()
                                scope.launch {
                                    tuningDataStore.setHasSeenStopTrackingHint(true)
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            onDismiss = {
                                trackingTabsViewModel.markCoachmarkDismissPending()
                                scope.launch {
                                    tuningDataStore.setHasSeenStopTrackingHint(true)
                                }
                            }
                        )
                    }

                    // Stationary Floating Non-Recording Status Banner across tabs (REQ-TRK-045.1 / ATT-5283)
                    if (screenMode == ScreenMode.TRACKING && pagerState.currentPage > 0 && cockpitStatus != null) {
                        NonRecordingStatusBanner(
                            status = cockpitStatus,
                            onAction = { onBannerAction(cockpitStatus) },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(1.5f)
                                .padding(top = 8.dp)
                        )
                    }

                    // Live Rearview Radar Vertical Edge Overlay (REQ-TRK-062 / ATT-5085)
                    val isRadarActive = screenMode == ScreenMode.TRACKING && (
                        radarState.connectionState != RadarConnectionState.DISCONNECTED ||
                        RadarTrafficSimulator.isSimulating
                    )
                    if (isRadarActive) {
                        RadarEdgeOverlay(
                            radarState = radarState,
                            edgeSide = tuningConfig.radarEdgeSide,
                            clearMode = tuningConfig.radarClearMode,
                            trafficOrientation = tuningConfig.radarTrafficOrientation,
                            onBadgeClick = {
                                showRadarLightQuickSwitch = true
                            },
                            onReconnectClick = {
                                context.sendBroadcast(
                                    Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES).apply {
                                        `package` = context.packageName
                                    }
                                )
                            },
                            modifier = Modifier
                                .align(
                                    if (tuningConfig.radarEdgeSide == RadarEdgeSide.LEFT) {
                                        Alignment.CenterStart
                                    } else {
                                        Alignment.CenterEnd
                                    }
                                )
                                .fillMaxHeight()
                                .zIndex(1.2f)
                        )
                    }

                    if (showRadarLightQuickSwitch) {
                        RadarLightQuickSwitchSheet(
                            currentMode = radarState.lightMode,
                            availableModes = RadarLightController.getAvailableModes(),
                            isStvzo = RadarLightController.isStvzo.collectAsState().value,
                            isLightControlSupported = radarState.isLightControlSupported,
                            snoozeState = radarState.audioSnoozeState,
                            onModeSelected = { mode ->
                                RadarLightController.setManualLightMode(mode)
                                showRadarLightQuickSwitch = false
                            },
                            onSnoozeSelected = { mode ->
                                RadarAlertManager.snooze(mode)
                            },
                            onResumeAudio = {
                                RadarAlertManager.resumeAudio()
                            },
                            onDismiss = { showRadarLightQuickSwitch = false }
                        )
                    }
                }
            }

            // --- Conditionally show the Lap Button and Bike Bell Button (suppressed in CONFIGURATION mode per REQ-UI-295)
            val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
            val shouldShowBikeBell = currentViewInfo?.showBikeBell == true && screenMode != ScreenMode.CONFIGURATION
            if (shouldShowLapButton || shouldShowBikeBell) {
                val isLandscapeWithMap = isLandscape && currentViewInfo?.showMap == true
                val buttonAlignment = if (isLandscapeWithMap) Alignment.BottomStart else Alignment.BottomCenter
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding() // Respect system nav bar
                        .padding(
                            start = if (isLandscapeWithMap) 16.dp else 0.dp,
                            bottom = 8.dp
                        ), // Space from bottom of screen
                    contentAlignment = buttonAlignment
                ) {
                    if (shouldShowLapButton && shouldShowBikeBell) {
                        Row(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LapButton(
                                modifier = Modifier.wrapContentSize(),
                                trackingMode = trackingMode,
                                onClick = { trackingTabsViewModel.onLapButtonClick() }
                            )
                            BikeBellButton(
                                modifier = Modifier.wrapContentSize(),
                                trackingMode = trackingMode,
                                onSingleRing = { trackingTabsViewModel.triggerBellRing(isLongPress = false) },
                                onContinuousRingStart = { trackingTabsViewModel.triggerBellRoll() },
                                onContinuousRingStop = { trackingTabsViewModel.stopBellRoll() }
                            )
                        }
                    } else if (shouldShowLapButton) {
                        LapButton(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(horizontal = 16.dp),
                            trackingMode = trackingMode,
                            onClick = { trackingTabsViewModel.onLapButtonClick() }
                        )
                    } else {
                        BikeBellButton(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(horizontal = 16.dp),
                            trackingMode = trackingMode,
                            onSingleRing = { trackingTabsViewModel.triggerBellRing(isLongPress = false) },
                            onContinuousRingStart = { trackingTabsViewModel.triggerBellRoll() },
                            onContinuousRingStop = { trackingTabsViewModel.stopBellRoll() }
                        )
                    }
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

            // Modal Bottom Sheet for Sporting Journey / Trophy Vault (REQ-SPT-001 / ATT-7022)
            if (showSportingJourneySheet) {
                SportingJourneySheet(
                    onDismissRequest = { showSportingJourneySheet = false }
                )
            }

            // Modal Bottom Sheet for Copy Tracking Tab (REQ-UI-439 / ATT-5078)
            copySourceTabId?.let { sourceId ->
                val currentActivityType by trackingTabsViewModel.activityType.collectAsState()
                CopyTrackingTabDialog(
                    sourceActivityType = currentActivityType,
                    onConfirm = { targetTypes ->
                        trackingTabsViewModel.onConfirmCopyTab(sourceId, targetTypes)
                    },
                    onDismiss = {
                        trackingTabsViewModel.onDismissCopyTabDialog()
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (shouldShowLapButton || shouldShowBikeBell) 64.dp else 8.dp)
            ) { data ->
                val visuals = data.visuals as? WorkoutNavigationEvents.BatterySnackbarVisuals
                if (visuals != null) {
                    val colors = getBatterySnackbarColors(visuals.severity)
                    Snackbar(
                        snackbarData = data,
                        containerColor = colors.containerColor,
                        contentColor = colors.contentColor,
                        actionColor = colors.actionColor
                    )
                } else {
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionColor = MaterialTheme.colorScheme.primary
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

/**
 * Unwraps nested [ContextWrapper] instances to locate the hosting [AppCompatActivity],
 * or returns `null` if the [Context] is not backed by an [AppCompatActivity].
 *
 * Ref: REQ-UI-410 / ATT-4000
 */
internal tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Returns `true` when the receiver [AppCompatActivity] is non-null, active (neither finishing
 * nor destroyed), and its [androidx.fragment.app.FragmentManager] has not yet saved state,
 * making it safe to commit [androidx.fragment.app.DialogFragment.show] transactions.
 *
 * Ref: REQ-UI-410 / ATT-4000
 */
internal fun AppCompatActivity?.canShowDialogFragments(): Boolean {
    return this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved
}

/**
 * Executes a programmatic tab navigation event against the Cockpit pager without cancelling
 * the caller coroutine if `pageCount` fails to grow past the target index within [timeoutMs].
 *
 * Only updates `lastKnownPage` via [onUpdateLastKnownPage] after confirming the resolved
 * pager target index is within `0 until currentPageCount()`.
 *
 * Ref: REQ-UI-410 / ATT-4000
 *
 * @return `true` if the target page was in bounds and scroll was invoked, `false` otherwise.
 */
internal suspend fun navigateToPagerTarget(
    tabIndex: Int,
    screenMode: ScreenMode,
    currentPageCount: () -> Int,
    pageCountFlow: Flow<Int>,
    onUpdateLastKnownPage: (Int) -> Unit,
    animateScrollToPage: suspend (Int) -> Unit,
    scrollToPage: suspend (Int) -> Unit,
    timeoutMs: Long = 2000L
): Boolean {
    // Calculate offset: if TRACKING mode, page 0 is Control, so add 1
    val offset = if (screenMode == ScreenMode.TRACKING) 1 else 0
    val target = tabIndex + offset
    Log.i(TAG, "Navigating to target=$target (offset=$offset)")

    if (target < 0) {
        Log.w(TAG, "Cannot scroll to negative target $target")
        return false
    }

    // If pager hasn't loaded enough pages yet, wait up to timeoutMs for pageCount > target
    if (currentPageCount() <= target) {
        val readyCount = withTimeoutOrNull(timeoutMs) {
            pageCountFlow.first { count -> count > target }
        }
        if (readyCount == null) {
            Log.w(TAG, "Timed out after ${timeoutMs}ms waiting for pageCount > $target (current=${currentPageCount()})")
        }
    }

    val finalPageCount = currentPageCount()
    if (target in 0 until finalPageCount) {
        onUpdateLastKnownPage(target)
        try {
            animateScrollToPage(target)
            Log.i(TAG, "Successfully animated to page $target")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "animateScrollToPage cancelled by user gesture or concurrent scroll; skipping fallback", e)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "animateScrollToPage failed: page $target not ready yet", e)
            try {
                scrollToPage(target)
                Log.i(TAG, "Fallback scrollToPage succeeded for page $target")
            } catch (e2: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Fallback scrollToPage cancelled", e2)
            } catch (e2: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Fallback scrollToPage also failed", e2)
            }
        }
        return true
    } else {
        Log.e(TAG, "Cannot scroll to target $target: pageCount is $finalPageCount")
        return false
    }
}