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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitChartData
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitVisualizerCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCard
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatus
import com.atrainingtracker.trainingtracker.ui.components.strava.StravaActivitySection
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.WorkoutDescription
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetails
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.WorkoutExtrema
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.*
import kotlinx.coroutines.launch

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
    effectiveHrZoneDisplayMode: ZoneCardDisplayMode,
    effectivePowerZoneDisplayMode: ZoneCardDisplayMode,
    onHrZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onPowerZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    useStatusBarsPadding: Boolean,
    onClusterClick: ((Long) -> Unit)?,
    onEditWorkout: ((Long) -> Unit)?,
    headerActions: @Composable RowScope.() -> Unit,
    onOpenTabsConfig: () -> Unit,
    mapContent: MapContentScope.() -> Unit,
    mapOverlay: @Composable BoxScope.() -> Unit,
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

    // Hit-test & edge-gutter state for gesture arbitration between Map/Chart canvases and lower-pane cards
    var isTouchingInteractiveCanvas by remember { mutableStateOf(true) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
                .pointerInput(density) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val firstChange = event.changes.firstOrNull()
                            if (firstChange != null) {
                                lastTouchXDp = with(density) { firstChange.position.x.toDp().value }
                            }
                        }
                    }
                }
        ) {
            // 1. Persistent Workout Header with "Configure Tabs" Action
            WorkoutHeader(
                modifier = Modifier.fillMaxWidth(),
                data = workoutData.headerData,
                menuEnabled = false,
                canDelete = false,
                onClicked = onEditWorkout?.let { edit -> { edit(workoutData.id) } },
                onExport = {},
                onSaveAsRoute = {},
                onDeleteRequest = {},
                onClusterClick = onClusterClick,
                onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } },
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

            // 3. HorizontalPager hosting Mode A / Mode B / Mode C per tab
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !activeTabRequiresGestureIsolation,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                            isTouchingInteractiveCanvas = true
                                        }
                                    }
                                }
                        ) {
                            MapDetailLayout(
                                bSportType = workoutData.bSportType,
                                zoomFocus = MapZoomFocus.FIT_PRIMARY,
                                activeScrubPath = activeScrubPath,
                                minAltitudeOverride = workoutData.minAltitude,
                                maxAltitudeOverride = workoutData.maxAltitude,
                                useStatusBarsPadding = false,
                                showMap = true,
                                showElevationProfile = false,
                                showTelemetryCharts = false,
                                hrZoneDistribution = hrZoneDistribution,
                                powerZoneDistribution = powerZoneDistribution,
                                hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                header = {},
                                mapContent = mapContent,
                                overlay = mapOverlay,
                                metadataContent = null,
                                analyticsContent = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP -> {
                        // Mode B: Map pinned at top + SplitPaneDivider + single lower scrollable pane
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                            isTouchingInteractiveCanvas = true
                                        }
                                    }
                                }
                        ) {
                            MapDetailLayout(
                                bSportType = workoutData.bSportType,
                                zoomFocus = MapZoomFocus.FIT_PRIMARY,
                                activeScrubPath = activeScrubPath,
                                minAltitudeOverride = workoutData.minAltitude,
                                maxAltitudeOverride = workoutData.maxAltitude,
                                useStatusBarsPadding = false,
                                showMap = true,
                                showElevationProfile = tabHasElevation,
                                showTelemetryCharts = tabHasCharts,
                                hrZoneDistribution = hrZoneDistribution,
                                powerZoneDistribution = powerZoneDistribution,
                                hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                header = {},
                                mapContent = mapContent,
                                overlay = mapOverlay,
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
                                                splitChartData = splitChartData,
                                                selectedLapNr = selectedLapNr,
                                                onSelectLapNr = onSelectLapNr,
                                                hrZoneDistribution = hrZoneDistribution,
                                                powerZoneDistribution = powerZoneDistribution,
                                                effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                                effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                                onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                                onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL -> {
                        // Mode A: No Map in tab -> Single unified vertical scroll container
                        if (tabHasElevation || tabHasCharts) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                awaitPointerEvent(PointerEventPass.Initial)
                                                isTouchingInteractiveCanvas = true
                                            }
                                        }
                                    }
                            ) {
                                MapDetailLayout(
                                    bSportType = workoutData.bSportType,
                                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                                    activeScrubPath = activeScrubPath,
                                    minAltitudeOverride = workoutData.minAltitude,
                                    maxAltitudeOverride = workoutData.maxAltitude,
                                    useStatusBarsPadding = false,
                                    showMap = false,
                                    showElevationProfile = tabHasElevation,
                                    showTelemetryCharts = tabHasCharts,
                                    hrZoneDistribution = hrZoneDistribution,
                                    powerZoneDistribution = powerZoneDistribution,
                                    hrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                    powerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                    header = {},
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
                                                    splitChartData = splitChartData,
                                                    selectedLapNr = selectedLapNr,
                                                    onSelectLapNr = onSelectLapNr,
                                                    hrZoneDistribution = hrZoneDistribution,
                                                    powerZoneDistribution = powerZoneDistribution,
                                                    effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                                    effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                                    onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                                    onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
                                    splitChartData = splitChartData,
                                    selectedLapNr = selectedLapNr,
                                    onSelectLapNr = onSelectLapNr,
                                    hrZoneDistribution = hrZoneDistribution,
                                    powerZoneDistribution = powerZoneDistribution,
                                    effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
                                    effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
                                    onHrZoneDisplayModeChange = onHrZoneDisplayModeChange,
                                    onPowerZoneDisplayModeChange = onPowerZoneDisplayModeChange
                                )
                            }
                        }
                    }
                }
            }
        }
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
    splitChartData: LapSplitChartData?,
    selectedLapNr: Long?,
    onSelectLapNr: (Long?) -> Unit,
    hrZoneDistribution: ZoneDistributionData?,
    powerZoneDistribution: ZoneDistributionData?,
    effectiveHrZoneDisplayMode: ZoneCardDisplayMode,
    effectivePowerZoneDisplayMode: ZoneCardDisplayMode,
    onHrZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit,
    onPowerZoneDisplayModeChange: (ZoneCardDisplayMode) -> Unit
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
                    modifier = Modifier.fillMaxWidth()
                )
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
            }
            WorkoutDetailSectionType.LAPS -> {
                splitChartData?.let { splits ->
                    LapSplitVisualizerCard(
                        splitData = splits,
                        selectedLapNr = selectedLapNr,
                        onLapClick = { tappedLapNr ->
                            onSelectLapNr(if (selectedLapNr == tappedLapNr) null else tappedLapNr)
                        },
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
            WorkoutDetailSectionType.MAP,
            WorkoutDetailSectionType.ELEVATION,
            WorkoutDetailSectionType.CHARTS -> {
                // Handled directly by MapDetailLayout
            }
        }
    }
}
