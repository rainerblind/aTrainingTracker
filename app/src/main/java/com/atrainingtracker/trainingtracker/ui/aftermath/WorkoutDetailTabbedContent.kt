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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.res.Configuration
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoEntry
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoPreviewBottomSheet
import com.atrainingtracker.trainingtracker.ui.aftermath.photos.WorkoutPhotoStrip
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitChartData
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizerCard
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvAlpha1ScatterCard
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.GradeZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatus
import com.atrainingtracker.trainingtracker.ui.components.strava.StravaActivitySection
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetails
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricUtils
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLaps
import com.atrainingtracker.trainingtracker.ui.map.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Multi-tab Workout Details container (`REQ-UI-408` / `ATT-3920`).
 *
 * Hosts:
 * 1. Persistent top [WorkoutHeader] with a compact `"Configure Tabs"` [IconButton] (`REQ-UI-408.5`).
 * 2. Material 3 [PrimaryScrollableTabRow] displaying all non-empty tabs filtered by [WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs].
 * 3. [HorizontalPager] with region-aware & edge-gutter gesture arbitration ([WorkoutDetailTabConfig.requiresHorizontalGestureIsolation]).
 * 4. Conflict-free per-tab layout modes (`REQ-UI-408.3`):
 *    - [WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL]
 *    - [WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP]
 *    - [WorkoutDetailTabLayoutMode.MODE_C_FULL_MAP]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailTabbedContent(
    workoutData: WorkoutData,
    tabs: List<WorkoutDetailTabConfig>,
    availability: WorkoutDetailSectionAvailability,
    detailPreferences: WorkoutDetailPreferences,
    tuningConfig: TuningConfig,
    activeScrubPath: List<PathPoint>?,
    hasAltitudeData: Boolean,
    splitChartData: LapSplitChartData?,
    selectedLapNr: Long?,
    onSelectLapNr: (Long?) -> Unit,
    hrZoneDistribution: ZoneDistributionData?,
    powerZoneDistribution: ZoneDistributionData?,
    gradeZoneDistribution: ZoneDistributionData? = null,
    effectiveHrZoneDisplayMode: ZoneCardDisplayMode,
    effectivePowerZoneDisplayMode: ZoneCardDisplayMode,
    effectiveGradeZoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS,
    onHrZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onPowerZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onGradeZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit = {},
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    useStatusBarsPadding: Boolean,
    onClusterClick: ((Long) -> Unit)?,
    onEditWorkout: ((Long) -> Unit)?,
    headerActions: @Composable RowScope.() -> Unit,
    onOpenTabsConfig: () -> Unit,
    mapContent: MapContentScope.() -> Unit,
    mapOverlay: @Composable BoxScope.() -> Unit,
    mapTopEndActions: (@Composable ColumnScope.() -> Unit)? = null,
    onSplitLapClick: (() -> Unit)? = null,
    onCreateLapClick: ((Double) -> Unit)? = null,
    onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null,
    onLapClick: ((LapData) -> Unit)? = null,
    initialAutoShareSnapshot: Boolean = false,
    onSnapshotComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleTabs = remember(tabs, availability, detailPreferences) {
        WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs(
            tabs = tabs,
            availability = availability,
            detailPreferences = detailPreferences
        )
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { visibleTabs.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember(context) { WorkoutRepository.getInstance(context.applicationContext as Application) }
    var photosRefreshKey by remember { mutableIntStateOf(0) }
    val workoutPhotos by produceState<List<WorkoutPhotoEntry>>(
        initialValue = emptyList(),
        key1 = workoutData.id,
        key2 = photosRefreshKey
    ) {
        value = withContext(Dispatchers.IO) {
            repository.getWorkoutPhotos(workoutData.id)
        }
    }
    var activePreviewPhoto by remember { mutableStateOf<WorkoutPhotoEntry?>(null) }
    var activeSharePhoto by remember { mutableStateOf<WorkoutPhotoEntry?>(null) }
    var showShareModeSheet by rememberSaveable { mutableStateOf(false) }

    var triggerMapSnapshot by remember { mutableStateOf(false) }
    var pendingAutoShare by remember(workoutData.id, initialAutoShareSnapshot) {
        mutableStateOf(initialAutoShareSnapshot)
    }
    var originPageIndex by rememberSaveable(workoutData.id) { mutableStateOf<Int?>(null) }

    val mapTabIndex = remember(visibleTabs) {
        val idx = visibleTabs.indexOfFirst { WorkoutDetailSectionType.MAP in it.sections }
        if (idx >= 0) idx else visibleTabs.indexOfFirst {
            WorkoutDetailSectionType.ELEVATION in it.sections || WorkoutDetailSectionType.CHARTS in it.sections
        }
    }

    LaunchedEffect(pendingAutoShare, mapTabIndex) {
        if (pendingAutoShare && mapTabIndex >= 0) {
            if (pagerState.currentPage != mapTabIndex) {
                pagerState.scrollToPage(mapTabIndex)
            }
            kotlinx.coroutines.delay(350L)
            pendingAutoShare = false
            triggerMapSnapshot = true
        }
    }

    val handleSnapshotComplete = {
        triggerMapSnapshot = false
        coroutineScope.launch {
            originPageIndex?.let { targetPage ->
                if (targetPage in 0 until visibleTabs.size) {
                    pagerState.scrollToPage(targetPage)
                }
                originPageIndex = null
            }
            onSnapshotComplete?.invoke()
        }
    }

    val snapshotHeader: @Composable () -> Unit = {
        WorkoutHeader(
            modifier = Modifier.fillMaxWidth(),
            data = workoutData.headerData,
            menuEnabled = false,
            canDelete = false,
            onClicked = null,
            onExport = {},
            onSaveAsRoute = {},
            onDeleteRequest = {},
            onClusterClick = null,
            onEditWorkout = null,
            onShareWorkout = null
        )
    }

    // Hit-test & edge-gutter state for gesture arbitration between Map/Chart canvases and lower-pane cards
    var isTouchingInteractiveCanvas by remember { mutableStateOf(false) }
    var lastTouchXDp by remember { mutableStateOf<Float?>(null) }

    val currentPageIndex = pagerState.currentPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
    val activeTab = visibleTabs[currentPageIndex]

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val density = LocalDensity.current
        val screenWidthDp = maxWidth.value

        val activeTabRequiresGestureIsolation = remember(
            activeTab,
            availability,
            detailPreferences,
            isTouchingInteractiveCanvas,
            lastTouchXDp,
            screenWidthDp
        ) {
            activeTab.requiresHorizontalGestureIsolation(
                availability = availability,
                detailPreferences = detailPreferences,
                isTouchingInteractiveCanvas = isTouchingInteractiveCanvas,
                touchXDp = lastTouchXDp,
                screenWidthDp = screenWidthDp
            )
        }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val connection = remember { CollapsingAppBarNestedScrollConnection(0) }
        var headerHeightPx by remember { mutableIntStateOf(0) }
        var totalTopChromeHeightPx by remember { mutableIntStateOf(0) }

        LaunchedEffect(isLandscape, totalTopChromeHeightPx, headerHeightPx) {
            val maxCollapse = if (isLandscape) totalTopChromeHeightPx else headerHeightPx
            if (maxCollapse > 0) {
                connection.appBarMaxHeight = maxCollapse
            }
        }

        val currentTopPaddingPx = (totalTopChromeHeightPx + connection.appBarOffset).coerceAtLeast(0)
        val currentTopPaddingDp = with(density) { currentTopPaddingPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
                .nestedScroll(connection)
                .pointerInput(density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        lastTouchXDp = with(density) { down.position.x.toDp().value }
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        lastTouchXDp = null
                        isTouchingInteractiveCanvas = false
                    }
                }
        ) {
            // 3. HorizontalPager hosting Mode A / Mode B / Mode C per tab
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !activeTabRequiresGestureIsolation,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = currentTopPaddingDp)
            ) { pageIndex ->
                val tab = visibleTabs[pageIndex]
                val effectiveMode = tab.effectiveLayoutMode(availability, detailPreferences)
                val activeSections = remember(tab, availability, detailPreferences) {
                    tab.normalizedSections.filter { availability.isSectionActive(it, detailPreferences) }
                }
                val tabHasElevation = WorkoutDetailSectionType.ELEVATION in activeSections && hasAltitudeData
                val tabHasCharts = WorkoutDetailSectionType.CHARTS in activeSections
                val nonMapNonGraphSections = remember(activeSections) {
                    activeSections.filter {
                        it != WorkoutDetailSectionType.MAP &&
                            it != WorkoutDetailSectionType.ELEVATION &&
                            it != WorkoutDetailSectionType.CHARTS
                    }
                }

                when (effectiveMode) {
                    WorkoutDetailTabLayoutMode.MODE_C_FULL_MAP -> {
                        // Mode C: Full-height Map (100% of tab viewport, zero SplitPaneDivider)
                        MapDetailLayout(
                            bSportType = workoutData.bSportType,
                            zoomFocus = MapZoomFocus.FIT_PRIMARY,
                            activeScrubPath = activeScrubPath,
                            minAltitudeOverride = workoutData.minAltitude,
                            maxAltitudeOverride = workoutData.maxAltitude,
                            useStatusBarsPadding = false,
                            isBottomSheet = false,
                            showHeader = false,
                            showMap = true,
                            showElevationProfile = false,
                            showTelemetryCharts = false,
                            hrZoneDistribution = hrZoneDistribution,
                            powerZoneDistribution = powerZoneDistribution,
                            hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                            powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                            hrZoneThresholds = hrZoneThresholds,
                            powerZoneThresholds = powerZoneThresholds,
                            workoutData = workoutData,
                            onCreateLapClick = onCreateLapClick,
                            onInteractiveCanvasTouch = { isTouchingInteractiveCanvas = it },
                            header = snapshotHeader,
                            mapContent = mapContent,
                            overlay = mapOverlay,
                            topEndActions = mapTopEndActions,
                            metadataContent = null,
                            analyticsContent = null,
                            externalSnapshotTrigger = (pageIndex == mapTabIndex) && triggerMapSnapshot,
                            onSnapshotComplete = { handleSnapshotComplete() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP -> {
                        // Mode B: Map pinned at top + SplitPaneDivider + single lower scrollable pane
                        MapDetailLayout(
                            bSportType = workoutData.bSportType,
                            zoomFocus = MapZoomFocus.FIT_PRIMARY,
                            activeScrubPath = activeScrubPath,
                            minAltitudeOverride = workoutData.minAltitude,
                            maxAltitudeOverride = workoutData.maxAltitude,
                            useStatusBarsPadding = false,
                            isBottomSheet = false,
                            showHeader = false,
                            showMap = true,
                            showElevationProfile = tabHasElevation,
                            showTelemetryCharts = tabHasCharts,
                            hrZoneDistribution = hrZoneDistribution,
                            powerZoneDistribution = powerZoneDistribution,
                            hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                            powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                            hrZoneThresholds = hrZoneThresholds,
                            powerZoneThresholds = powerZoneThresholds,
                            workoutData = workoutData,
                            onCreateLapClick = onCreateLapClick,
                            onInteractiveCanvasTouch = { isTouchingInteractiveCanvas = it },
                            header = snapshotHeader,
                            externalSnapshotTrigger = (pageIndex == mapTabIndex) && triggerMapSnapshot,
                            onSnapshotComplete = { handleSnapshotComplete() },
                            mapContent = mapContent,
                            overlay = mapOverlay,
                            topEndActions = mapTopEndActions,
                            metadataContent = null,
                            analyticsContent = if (nonMapNonGraphSections.isNotEmpty()) {
                                {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        awaitPointerEvent(PointerEventPass.Initial)
                                                        isTouchingInteractiveCanvas = false
                                                    }
                                                }
                                            }
                                    ) {
                                        RenderWorkoutDetailCardSections(
                                            sections = nonMapNonGraphSections,
                                            workoutData = workoutData,
                                            tuningConfig = tuningConfig,
                                            detailPreferences = detailPreferences,
                                            splitChartData = splitChartData,
                                            selectedLapNr = selectedLapNr,
                                            onSelectLapNr = onSelectLapNr,
                                            hrZoneDistribution = hrZoneDistribution,
                                            powerZoneDistribution = powerZoneDistribution,
                                            gradeZoneDistribution = gradeZoneDistribution,
                                            effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                            effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                            effectiveGradeZoneDisplayMode = effectiveGradeZoneDisplayMode,
                                            onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                            onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange,
                                            onGradeZoneDisplayModeChange = onGradeZoneDisplayModeChange,
                                            activeScrubPath = activeScrubPath,
                                            photos = workoutPhotos,
                                            onPhotoClick = { activePreviewPhoto = it },
                                            onSplitLapClick = onSplitLapClick,
                                            onMoveBoundaryClick = onMoveBoundaryClick,
                                            onLapClick = onLapClick,
                                            onAddPhotos = { uris ->
                                                coroutineScope.launch {
                                                    uris.forEach { uri ->
                                                        repository.addWorkoutPhoto(workoutData.id, uri, null)
                                                    }
                                                    photosRefreshKey++
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL -> {
                        // Mode A: No Map in tab -> Single unified vertical scroll container
                        if (tabHasElevation || tabHasCharts) {
                            MapDetailLayout(
                                bSportType = workoutData.bSportType,
                                zoomFocus = MapZoomFocus.FIT_PRIMARY,
                                activeScrubPath = activeScrubPath,
                                minAltitudeOverride = workoutData.minAltitude,
                                maxAltitudeOverride = workoutData.maxAltitude,
                                useStatusBarsPadding = false,
                                isBottomSheet = false,
                                showHeader = false,
                                showMap = false,
                                showElevationProfile = tabHasElevation,
                                showTelemetryCharts = tabHasCharts,
                                hrZoneDistribution = hrZoneDistribution,
                                powerZoneDistribution = powerZoneDistribution,
                                hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                hrZoneThresholds = hrZoneThresholds,
                                powerZoneThresholds = powerZoneThresholds,
                                workoutData = workoutData,
                                onCreateLapClick = onCreateLapClick,
                                onInteractiveCanvasTouch = { isTouchingInteractiveCanvas = it },
                                header = snapshotHeader,
                                externalSnapshotTrigger = (pageIndex == mapTabIndex) && triggerMapSnapshot,
                                onSnapshotComplete = { handleSnapshotComplete() },
                                metadataContent = null,
                                analyticsContent = if (nonMapNonGraphSections.isNotEmpty()) {
                                    {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            awaitPointerEvent(PointerEventPass.Initial)
                                                            isTouchingInteractiveCanvas = false
                                                        }
                                                    }
                                                }
                                        ) {
                                            RenderWorkoutDetailCardSections(
                                                sections = nonMapNonGraphSections,
                                                workoutData = workoutData,
                                                tuningConfig = tuningConfig,
                                                detailPreferences = detailPreferences,
                                                splitChartData = splitChartData,
                                                selectedLapNr = selectedLapNr,
                                                onSelectLapNr = onSelectLapNr,
                                                hrZoneDistribution = hrZoneDistribution,
                                                powerZoneDistribution = powerZoneDistribution,
                                                gradeZoneDistribution = gradeZoneDistribution,
                                                effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                                effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                                effectiveGradeZoneDisplayMode = effectiveGradeZoneDisplayMode,
                                                onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                                onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange,
                                                onGradeZoneDisplayModeChange = onGradeZoneDisplayModeChange,
                                                activeScrubPath = activeScrubPath,
                                                photos = workoutPhotos,
                                                onPhotoClick = { activePreviewPhoto = it },
                                                onSplitLapClick = onSplitLapClick,
                                                onMoveBoundaryClick = onMoveBoundaryClick,
                                                onLapClick = onLapClick,
                                                onAddPhotos = { uris ->
                                                    coroutineScope.launch {
                                                        uris.forEach { uri ->
                                                            repository.addWorkoutPhoto(workoutData.id, uri, null)
                                                        }
                                                        photosRefreshKey++
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent(PointerEventPass.Initial)
                                                isTouchingInteractiveCanvas = false
                                            }
                                        }
                                    }
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RenderWorkoutDetailCardSections(
                                    sections = nonMapNonGraphSections,
                                    workoutData = workoutData,
                                    tuningConfig = tuningConfig,
                                    detailPreferences = detailPreferences,
                                    splitChartData = splitChartData,
                                    selectedLapNr = selectedLapNr,
                                    onSelectLapNr = onSelectLapNr,
                                    hrZoneDistribution = hrZoneDistribution,
                                    powerZoneDistribution = powerZoneDistribution,
                                    gradeZoneDistribution = gradeZoneDistribution,
                                    effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                    effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                    effectiveGradeZoneDisplayMode = effectiveGradeZoneDisplayMode,
                                    onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                    onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange,
                                    onGradeZoneDisplayModeChange = onGradeZoneDisplayModeChange,
                                    activeScrubPath = activeScrubPath,
                                    photos = workoutPhotos,
                                    onPhotoClick = { activePreviewPhoto = it },
                                    onSplitLapClick = onSplitLapClick,
                                    onMoveBoundaryClick = onMoveBoundaryClick,
                                    onLapClick = onLapClick,
                                    onAddPhotos = { uris ->
                                        coroutineScope.launch {
                                            uris.forEach { uri ->
                                                repository.addWorkoutPhoto(workoutData.id, uri, null)
                                            }
                                            photosRefreshKey++
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Top Chrome (WorkoutHeader + Tab Row)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .offset { IntOffset(0, connection.appBarOffset) }
                    .background(MaterialTheme.colorScheme.surface)
                    .onGloballyPositioned { coordinates ->
                        val measured = coordinates.size.height
                        if (measured != totalTopChromeHeightPx) {
                            totalTopChromeHeightPx = measured
                            val maxCollapse = if (isLandscape) measured else headerHeightPx
                            if (maxCollapse > 0) {
                                connection.appBarMaxHeight = maxCollapse
                            }
                        }
                    }
            ) {
                // 1. Persistent Workout Header with "Configure Tabs" Action
                WorkoutHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val measured = coordinates.size.height
                            if (measured != headerHeightPx) {
                                headerHeightPx = measured
                                val maxCollapse = if (isLandscape) totalTopChromeHeightPx else measured
                                if (maxCollapse > 0) {
                                    connection.appBarMaxHeight = maxCollapse
                                }
                            }
                        },
                    data = workoutData.headerData,
                    menuEnabled = false,
                    canDelete = false,
                    onClicked = null,
                    onExport = {},
                    onSaveAsRoute = {},
                    onDeleteRequest = {},
                    onClusterClick = onClusterClick,
                    onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } },
                    onShareWorkout = { showShareModeSheet = true },
                    onSyncHealthConnect = if (HealthConnectManager.isAvailable(context)) {
                        { HealthConnectManager.syncWorkoutAsync(context, workoutData, force = true) }
                    } else null,
                    actions = {
                        headerActions()
                        IconButton(
                            onClick = onOpenTabsConfig,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.workout_detail_tabs_config_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                // 1b. Diagnostic Warning Banner (REQ-TRK-053.4)
                if (!workoutData.recordingErrors.isNullOrBlank()) {
                    var showDetailDiagnosticSheet by remember { mutableStateOf(false) }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDetailDiagnosticSheet = true }
                            .testTag(com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticTestTags.BANNER)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.workout_diagnostic_banner_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.workout_diagnostic_banner_action),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showDetailDiagnosticSheet) {
                        val coroutineScope = rememberCoroutineScope()
                        val repository = remember(context) { WorkoutRepository.getInstance(context.applicationContext as Application) }
                        com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticBottomSheet(
                            recordingErrorsJson = workoutData.recordingErrors!!,
                            workoutName = workoutData.workoutName,
                            sportName = workoutData.sportName,
                            onClearWarning = {
                                coroutineScope.launch {
                                    repository.clearRecordingErrors(workoutData.id)
                                }
                                showDetailDiagnosticSheet = false
                            },
                            onDismiss = { showDetailDiagnosticSheet = false }
                        )
                    }
                }

                // 2. Material 3 Tab Bar (shown when > 1 visible tab)
                if (visibleTabs.size > 1) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = currentPageIndex,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visibleTabs.forEachIndexed { index, tab ->
                            val selected = index == currentPageIndex
                            Tab(
                                selected = selected,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = resolveWorkoutDetailTabTitle(tab),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                } else {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }

    activePreviewPhoto?.let { photo ->
        WorkoutPhotoPreviewBottomSheet(
            photo = photo,
            onDismissRequest = { activePreviewPhoto = null },
            onDeletePhoto = { photoToDelete ->
                coroutineScope.launch {
                    repository.deleteWorkoutPhoto(photoToDelete.id)
                    photosRefreshKey++
                    activePreviewPhoto = null
                }
            },
            onShareOverlay = { photoToShare ->
                activeSharePhoto = photoToShare
                activePreviewPhoto = null
            }
        )
    }

    activeSharePhoto?.let { photo ->
        WorkoutShareBottomSheet(
            workoutData = workoutData,
            onDismissRequest = { activeSharePhoto = null },
            onSelectMapSnapshot = { activeSharePhoto = null },
            initialPhotoUri = photo.uri,
            attachedPhotos = workoutPhotos.map { it.uri }
        )
    }

    if (showShareModeSheet) {
        WorkoutShareBottomSheet(
            workoutData = workoutData,
            onDismissRequest = { showShareModeSheet = false },
            onSelectMapSnapshot = {
                showShareModeSheet = false
                if (mapTabIndex >= 0) {
                    originPageIndex = pagerState.currentPage
                    pendingAutoShare = true
                }
            },
            attachedPhotos = workoutPhotos.map { it.uri }
        )
    }
}

/**
 * Renders the non-map, non-graph card sections in their configured order (`REQ-UI-408.1` / `REQ-UI-408.3`).
 */
@Composable
private fun RenderWorkoutDetailCardSections(
    sections: List<WorkoutDetailSectionType>,
    workoutData: WorkoutData,
    tuningConfig: TuningConfig,
    detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences(),
    splitChartData: LapSplitChartData?,
    selectedLapNr: Long?,
    onSelectLapNr: (Long?) -> Unit,
    hrZoneDistribution: ZoneDistributionData?,
    powerZoneDistribution: ZoneDistributionData?,
    gradeZoneDistribution: ZoneDistributionData? = null,
    effectiveHrZoneDisplayMode: ZoneCardDisplayMode,
    effectivePowerZoneDisplayMode: ZoneCardDisplayMode,
    effectiveGradeZoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS,
    onHrZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onPowerZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onGradeZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit = {},
    activeScrubPath: List<com.atrainingtracker.trainingtracker.ui.map.PathPoint>? = null,
    photos: List<WorkoutPhotoEntry> = emptyList(),
    onPhotoClick: ((WorkoutPhotoEntry) -> Unit)? = null,
    onAddPhotos: ((List<android.net.Uri>) -> Unit)? = null,
    onSplitLapClick: (() -> Unit)? = null,
    onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null,
    onLapClick: ((LapData) -> Unit)? = null
) {
    sections.forEach { section ->
        when (section) {
            WorkoutDetailSectionType.DETAILS -> {
                WorkoutDetails(
                    data = workoutData.detailsData,
                    enableCogganPowerMetrics = tuningConfig.enableCogganPowerMetrics,
                    showCogganPowerInWorkoutSummary = tuningConfig.showCogganPowerInWorkoutSummary,
                    enableAerobicEfficiencyModel = tuningConfig.enableAerobicEfficiencyModel,
                    showAerobicEfficiencyInWorkoutSummary = tuningConfig.showAerobicEfficiencyInWorkoutSummary,
                    enableCriticalPowerModel = tuningConfig.enableCriticalPowerModel,
                    showCriticalPowerInWorkoutSummary = tuningConfig.showCriticalPowerInWorkoutSummary,
                    enableCardioPowerDynamics = tuningConfig.enableCardioPowerDynamics,
                    showCardioPowerInWorkoutSummary = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tuningConfig.enableCardioPowerDynamics && workoutData.cardioPowerDynamics != null) {
                    CardioPowerDynamicsCard(
                        record = workoutData.cardioPowerDynamics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.DESCRIPTION -> {
                WorkoutDescription(
                    data = workoutData.descriptionData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            WorkoutDetailSectionType.EXTREMA -> {
                if (workoutData.extremaData.dataRows.isNotEmpty()) {
                    WorkoutExtrema(
                        data = workoutData.extremaData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.ZONES -> {
                hrZoneDistribution?.let { distribution ->
                    HeartRateZoneDistributionCard(
                        distribution = distribution,
                        displayMode = effectiveHrZoneDisplayMode,
                        onDisplayModeChange = onHrZoneDisplayModeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                powerZoneDistribution?.let { distribution ->
                    PowerZoneDistributionCard(
                        distribution = distribution,
                        displayMode = effectivePowerZoneDisplayMode,
                        onDisplayModeChange = onPowerZoneDisplayModeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (TelemetryMetricUtils.hasHrvData(activeScrubPath)) {
                    HrvAlpha1ScatterCard(
                        points = activeScrubPath ?: emptyList(),
                        dfaA1ThresholdT1 = tuningConfig.dfaA1ThresholdT1,
                        dfaA1ThresholdT2 = tuningConfig.dfaA1ThresholdT2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HrvThresholdSummaryCard(
                        points = activeScrubPath ?: emptyList(),
                        dfaA1ThresholdT1 = tuningConfig.dfaA1ThresholdT1,
                        dfaA1ThresholdT2 = tuningConfig.dfaA1ThresholdT2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.GRADE_DISTRIBUTION -> {
                gradeZoneDistribution?.let { distribution ->
                    GradeZoneDistributionCard(
                        distribution = distribution,
                        displayMode = effectiveGradeZoneDisplayMode,
                        onDisplayModeChange = onGradeZoneDisplayModeChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.LAPS -> {
                if (workoutData.laps.isNotEmpty()) {
                    WorkoutLaps(
                        laps = workoutData.laps,
                        bSportType = workoutData.bSportType,
                        lapDisplayMode = if (workoutData.laps.size > 1) LapDisplayMode.VISUALIZER_ONLY else LapDisplayMode.TABLE_ONLY,
                        onLapClick = { lap ->
                            onSelectLapNr(if (selectedLapNr == lap.lapNr) null else lap.lapNr)
                            onLapClick?.invoke(lap)
                        },
                        onSplitLapClick = onSplitLapClick,
                        onMoveBoundaryClick = onMoveBoundaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.STRAVA -> {
                if (!workoutData.stravaActivityData.isNullOrBlank()) {
                    StravaActivitySection(
                        rawActivityJson = workoutData.stravaActivityData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            WorkoutDetailSectionType.EXPORT_STATUS -> {
                ExportStatus(
                    exportStatuses = workoutData.exportStatuses
                )
            }
            WorkoutDetailSectionType.PHOTOS -> {
                if (onPhotoClick != null && onAddPhotos != null) {
                    val startMs = workoutData.startTimeS * 1000L
                    val endMs = (workoutData.startTimeS + workoutData.totalTimeSec) * 1000L
                    WorkoutPhotoStrip(
                        photos = photos,
                        onPhotoClick = onPhotoClick,
                        onAddPhotos = onAddPhotos,
                        showAddButton = detailPreferences.showPhotoAddButton,
                        thumbnailSize = detailPreferences.photoThumbnailSize,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                }
            }
            WorkoutDetailSectionType.MAP,
            WorkoutDetailSectionType.ELEVATION,
            WorkoutDetailSectionType.CHARTS -> {
                // Handled directly by MapDetailLayout
            }
        }
    }
}
