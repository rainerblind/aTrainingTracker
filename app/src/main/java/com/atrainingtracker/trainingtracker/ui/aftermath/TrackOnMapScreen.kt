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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.splits.LapSplitCalculator
import com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.firstOrNull
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSelectionDialog
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
import com.atrainingtracker.trainingtracker.zones.HrvSessionThreshold
import com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.CreateLapOnMapSheet
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapEditBottomSheet
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.MoveLapBoundaryOnMapSheet
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@Composable
fun TrackOnMapScreen(
    workoutData: WorkoutData,
    modifier: Modifier = Modifier,
    tracks: List<MapTrack> = emptyList(),
    availableTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    segments: List<MapSegment> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    markers: List<LocationMarker> = emptyList(),
    climbs: List<Climb> = emptyList(),
    knownLocations: List<KnownLocationItem> = emptyList(),
    enabledTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    onToggleTrackType: (TrackType) -> Unit = {},
    showTechnicalTracks: Boolean = false,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    onClusterClick: ((Long) -> Unit)? = null,
    onEditWorkout: ((Long) -> Unit)? = null,
    onChangeCluster: (() -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    hrZoneDistribution: ZoneDistributionData? = null,
    powerZoneDistribution: ZoneDistributionData? = null,
    gradeZoneDistribution: ZoneDistributionData? = null,
    hrZoneThresholds: HeartRateZoneThresholds? = null,
    powerZoneThresholds: PowerZoneThresholds? = null,
    telemetryPath: List<PathPoint> = emptyList(),
    detailPreferences: WorkoutDetailPreferences? = null,
    tabsConfig: List<WorkoutDetailTabConfig>? = null,
    onSaveTabsConfig: ((List<WorkoutDetailTabConfig>) -> Unit)? = null,
    onUpdateFtp: ((Int) -> Unit)? = null,
    onUpdateHrZones: ((Int, Int) -> Unit)? = null,
    recentSessions: List<HrvSessionThreshold> = emptyList(),
    isSmoothedStream: Boolean = false,
    initialAutoShareSnapshot: Boolean = false,
    onSnapshotComplete: (() -> Unit)? = null,
    multiSensorOverlays: Map<TelemetryMetricType, List<TelemetrySourceSeries>> = emptyMap(),
    activityCarriageTimeline: ActivityCarriageTimeline? = null,
    initialSection: WorkoutDetailSectionType? = null,
    isAftermathLoading: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferenceManager = remember { MyPreferenceManager.getInstance(context.applicationContext) }
    val tuningDataStore = remember { TuningPreferencesDataStore.getInstance(context) }
    val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(initial = TuningConfig())
    val persistedDetailPrefs by preferenceManager.workoutDetailPreferencesFlow.collectAsState(initial = WorkoutDetailPreferences())
    val activeDetailPrefs = detailPreferences ?: persistedDetailPrefs
    val persistedTabsConfig by preferenceManager.workoutDetailTabsOrderFlow.collectAsState(initial = null)
    val isTabsConfigReady = tabsConfig != null || persistedTabsConfig != null
    val activeTabsConfig = tabsConfig ?: persistedTabsConfig ?: WorkoutDetailTabConfig.DEFAULT_TABS
    val persistedHiddenCharts by preferenceManager.hiddenTelemetryChartsFlow.collectAsState(initial = emptySet())
    val workoutMapLayerConfig by preferenceManager.workoutMapLayerConfigFlow.collectAsState(initial = WorkoutMapLayerConfig())
    var showFullscreenMap by rememberSaveable(workoutData.id) { mutableStateOf(false) }
    val handleToggleChartVisibility: (TelemetryChartKind) -> Unit = { chartKind ->
        val nextVisible = chartKind in persistedHiddenCharts
        coroutineScope.launch {
            preferenceManager.setTelemetryChartVisible(chartKind, nextVisible)
        }
    }
    var showTabsConfigSheet by rememberSaveable { mutableStateOf(false) }
    var showClusterSelectionDialog by rememberSaveable { mutableStateOf(false) }
    var clusterCandidates by remember { mutableStateOf<List<Pair<WorkoutCluster, Double>>>(emptyList()) }
    var clusterOverride by remember(workoutData.id, workoutData.clusterId, workoutData.clusterName) {
        mutableStateOf<Pair<Long, String?>?>(null)
    }
    val effectiveWorkoutData = remember(workoutData, clusterOverride) {
        clusterOverride?.let { (cid, cname) ->
            workoutData.copy(clusterId = cid, clusterName = cname)
        } ?: workoutData
    }
    val repository = remember(context) { WorkoutRepository.getInstance(context.applicationContext as Application) }
    val clusterRepository = remember(context) { WorkoutClusterRepository.getInstance(context.applicationContext) }
    val effectiveOnChangeCluster: () -> Unit = onChangeCluster ?: { showClusterSelectionDialog = true }

    LaunchedEffect(showClusterSelectionDialog, effectiveWorkoutData) {
        if (showClusterSelectionDialog) {
            clusterCandidates = withContext(Dispatchers.IO) {
                clusterRepository.getCandidateClustersForWorkout(effectiveWorkoutData)
            }
        }
    }
    var showCreateLapSheet by rememberSaveable { mutableStateOf(false) }
    var showMoveBoundarySheet by rememberSaveable { mutableStateOf(false) }
    var moveBoundaryLapNr by rememberSaveable { mutableLongStateOf(1L) }
    var candidateSplitIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeEditingLap by remember { mutableStateOf<LapData?>(null) }
    val isPlayServiceAvailable = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    val lapStartMarkerTitle = stringResource(R.string.marker_lap_start)
    val lapEndMarkerTitle = stringResource(R.string.marker_lap_end)

    // PERFORMANCE: Memoize the filtered tracks list
    val filteredTracks = remember(tracks, enabledTrackTypes) {
        tracks.filter { it.type in enabledTrackTypes }
    }

    val hasGpsTrack = remember(tracks) {
        tracks.any { track ->
            track.path.isNotEmpty() && track.latLngs.any { it.latitude != 0.0 || it.longitude != 0.0 }
        }
    }

    var selectedLapNr by rememberSaveable { mutableStateOf<Long?>(null) }

    val formatters = LocalMetricFormatter.current
    val splitChartData = remember(workoutData.laps, workoutData.bSportType, formatters) {
        LapSplitCalculator.calculateSplitData(
            laps = workoutData.laps,
            bSportType = workoutData.bSportType,
            paceFormatter = { formatters.pace.format_with_units(it) },
            speedFormatter = { formatters.speed.format_with_units(it) }
        )
    }

    val bestTrack = remember(tracks) {
        tracks.find { it.type == TrackType.BEST } ?: tracks.firstOrNull()
    }
    val allPoints = remember(bestTrack) {
        bestTrack?.latLngs ?: emptyList()
    }
    val allDistances = remember(bestTrack) {
        bestTrack?.path?.map { it.distance } ?: emptyList()
    }

    val activeScrubPath = remember(hasGpsTrack, bestTrack, tracks, telemetryPath) {
        if (hasGpsTrack) {
            bestTrack?.path ?: tracks.firstOrNull()?.path
        } else {
            telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }
        }
    }

    val (startDistM, endDistM) = remember(workoutData.laps, selectedLapNr) {
        if (selectedLapNr != null) {
            LapSegmentUtils.calculateLapDistanceRange(workoutData.laps, selectedLapNr!!)
        } else {
            0.0 to 0.0
        }
    }

    val lapSegment = remember(allPoints, allDistances, startDistM, endDistM, selectedLapNr) {
        if (selectedLapNr != null && allPoints.isNotEmpty() && allDistances.isNotEmpty()) {
            LapSegmentUtils.sliceLapSegment(allPoints, allDistances, startDistM, endDistM)
        } else {
            emptyList()
        }
    }

    var hrZoneDisplayMode by rememberSaveable(activeDetailPrefs.zoneCardDisplayMode) { mutableStateOf<ZoneCardDisplayMode?>(null) }
    var powerZoneDisplayMode by rememberSaveable(activeDetailPrefs.zoneCardDisplayMode) { mutableStateOf<ZoneCardDisplayMode?>(null) }
    var gradeZoneDisplayMode by rememberSaveable(activeDetailPrefs.zoneCardDisplayMode) { mutableStateOf<ZoneCardDisplayMode?>(null) }
    val effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode
    val effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode
    val effectiveGradeZoneDisplayMode = gradeZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode

    val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != null } == true)
    val hasAltitudeData = workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != null } == true)

    val enableCpModel = tuningConfig.enableCriticalPowerModel
    val hasTelemetrySeries = remember(activeScrubPath, enableCpModel) {
        hasAnyTelemetryChartSeries(activeScrubPath, enableCpModel)
    }

    val hasZonesData = hrZoneDistribution != null || powerZoneDistribution != null
    val hasGradeDistribution = gradeZoneDistribution != null
    val hasHrvData = remember(activeScrubPath) { TelemetryMetricUtils.hasHrvData(activeScrubPath) }
    val hasLapsData = workoutData.laps.isNotEmpty()
    val hasActivityCarriageBar = activityCarriageTimeline != null && activityCarriageTimeline.segments.isNotEmpty()
    val sectionAvailability = remember(
        workoutData,
        showMap,
        hasGpsTrack,
        hasTracklessAltitude,
        hasAltitudeData,
        hasTelemetrySeries,
        hasZonesData,
        hasGradeDistribution,
        hasHrvData,
        hasLapsData,
        hasActivityCarriageBar
    ) {
        WorkoutDetailSectionAvailability.fromWorkoutState(
            workoutData = workoutData,
            showMap = showMap,
            hasGpsTrack = hasGpsTrack,
            hasTracklessAltitude = hasTracklessAltitude,
            hasAltitudeData = hasAltitudeData,
            hasTelemetrySeries = hasTelemetrySeries,
            hasZonesData = hasZonesData,
            hasLapsData = hasLapsData,
            hasGradeDistribution = hasGradeDistribution,
            hasHrvData = hasHrvData,
            hasActivityCarriageBar = hasActivityCarriageBar
        )
    }

    val visibleTabs = remember(activeTabsConfig, sectionAvailability, activeDetailPrefs) {
        WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs(
            tabs = WorkoutDetailTabConfig.validateConfigs(activeTabsConfig),
            availability = sectionAvailability,
            detailPreferences = activeDetailPrefs
        )
    }

    if (showTabsConfigSheet) {
        WorkoutDetailTabsConfigSheet(
            tabs = activeTabsConfig,
            onTabsChange = { updated ->
                val validated = WorkoutDetailTabConfig.validateConfigs(updated)
                if (onSaveTabsConfig != null) {
                    onSaveTabsConfig(validated)
                } else {
                    coroutineScope.launch { preferenceManager.setWorkoutDetailTabs(validated) }
                }
            },
            onResetDefaults = {
                if (onSaveTabsConfig != null) {
                    onSaveTabsConfig(WorkoutDetailTabConfig.DEFAULT_TABS)
                } else {
                    coroutineScope.launch { preferenceManager.resetWorkoutDetailTabs() }
                }
            },
            onDismiss = { showTabsConfigSheet = false }
        )
    }

    val effectiveOnUpdateFtp: (Int) -> Unit = onUpdateFtp ?: { newFtp ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val settingsDataStore = SettingsDataStore.getInstance(context.applicationContext)
                val thresholds = ZoneCalculationMath.calculateCogganPowerZones(newFtp)
                val cutoffs = listOf(
                    thresholds.z1Max, thresholds.z2Max, thresholds.z3Max,
                    thresholds.z4Max, thresholds.z5Max, thresholds.z6Max
                )
                settingsDataStore.saveCalculatedPowerZones(newFtp, cutoffs)
                AthleteMetricsRepository.getInstance(context.applicationContext).addEntry(
                    metricType = AthleteMetricType.FTP_WATTS,
                    value = newFtp.toDouble()
                )
            } catch (e: Exception) {
                Log.e("TrackOnMapScreen", "Error updating FTP in fallback", e)
            }
        }
    }

    val effectiveOnUpdateHrZones: (Int, Int) -> Unit = onUpdateHrZones ?: { newHrvt1, newHrvt2 ->
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val settingsDataStore = SettingsDataStore.getInstance(context.applicationContext)
                val zoneType = if (workoutData.bSportType == BSportType.RUN) {
                    SettingsDataStore.ZoneType.HR_RUN
                } else {
                    SettingsDataStore.ZoneType.HR_BIKE
                }
                val configuredHrMax = settingsDataStore.hrMaxFlow.firstOrNull() ?: SettingsDataStore.DEFAULT_HR_MAX
                val hrMax = if (configuredHrMax > newHrvt2) configuredHrMax else (newHrvt2 + 10).coerceAtMost(ZoneCalculationMath.MAX_HR_MAX)
                val hrRest = settingsDataStore.hrRestFlow.firstOrNull()
                val thresholds = ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(
                    hrvt1 = newHrvt1,
                    hrvt2 = newHrvt2,
                    hrMax = hrMax,
                    hrRest = hrRest
                )
                val cutoffs = listOf(
                    thresholds.z1Max,
                    thresholds.z2Max,
                    thresholds.z3Max,
                    thresholds.z4Max
                )
                settingsDataStore.saveCalculatedHeartRateZones(
                    zoneType = zoneType,
                    hrMax = hrMax,
                    hrRest = hrRest,
                    method = "HRVT_ANCHORS",
                    cutoffs = cutoffs
                )
            } catch (e: Exception) {
                Log.e("TrackOnMapScreen", "Error updating HR zones in fallback", e)
            }
        }
    }

    WorkoutDetailTabbedContent(
        workoutData = effectiveWorkoutData,
        tabs = visibleTabs,
        availability = sectionAvailability,
        detailPreferences = activeDetailPrefs,
        tuningConfig = tuningConfig,
        activeScrubPath = activeScrubPath,
        hasAltitudeData = hasAltitudeData,
        splitChartData = splitChartData,
        selectedLapNr = selectedLapNr,
        onSelectLapNr = { selectedLapNr = it },
        hrZoneDistribution = hrZoneDistribution,
        powerZoneDistribution = powerZoneDistribution,
        gradeZoneDistribution = gradeZoneDistribution,
        effectiveHrZoneDisplayMode = effectiveHrZoneDisplayMode,
        effectivePowerZoneDisplayMode = effectivePowerZoneDisplayMode,
        effectiveGradeZoneDisplayMode = effectiveGradeZoneDisplayMode,
        onHrZoneDisplayModeChange = { hrZoneDisplayMode = it },
        onPowerZoneDisplayModeChange = { powerZoneDisplayMode = it },
        onGradeZoneDisplayModeChange = { gradeZoneDisplayMode = it },
        hrZoneThresholds = hrZoneThresholds,
        powerZoneThresholds = powerZoneThresholds,
        recentSessions = recentSessions,
        isSmoothedStream = isSmoothedStream,
        multiSensorOverlays = multiSensorOverlays,
        activityCarriageTimeline = activityCarriageTimeline,
        initialSection = initialSection,
        isTabsConfigReady = isTabsConfigReady,
        isSectionDataLoading = isAftermathLoading,
        hiddenCharts = persistedHiddenCharts,
        onToggleChartVisibility = handleToggleChartVisibility,
        onUpdateFtp = effectiveOnUpdateFtp,
        onUpdateHrZones = effectiveOnUpdateHrZones,
        useStatusBarsPadding = useStatusBarsPadding,
        onClusterClick = onClusterClick,
        onEditWorkout = onEditWorkout,
        onChangeCluster = effectiveOnChangeCluster,
        headerActions = headerActions,
        onOpenTabsConfig = { showTabsConfigSheet = true },
        initialAutoShareSnapshot = initialAutoShareSnapshot,
        onSnapshotComplete = onSnapshotComplete,
        mapContent = {
            tracks(filteredTracks)
            contextualPaths(segments)
            contextualPaths(routes)
            if (lapSegment.isNotEmpty()) {
                val lapMarkers = mutableListOf<LocationMarker>()
                lapSegment.firstOrNull()?.let { startPoint ->
                    lapMarkers.add(LocationMarker(position = startPoint, iconResId = R.drawable.control_start, title = lapStartMarkerTitle))
                }
                lapSegment.lastOrNull()?.let { endPoint ->
                    lapMarkers.add(LocationMarker(position = endPoint, iconResId = R.drawable.control_stop, title = lapEndMarkerTitle))
                }
                markers(markers + lapMarkers)
                lapHighlight(lapSegment)
            } else {
                markers(markers)
            }
        },
        mapOverlay = {},
        mapTopEndActions = {
            if (showMap && hasGpsTrack && !initialAutoShareSnapshot) {
                WorkoutMapExpandButton(
                    onClick = { showFullscreenMap = true }
                )
            }
            if (showTechnicalTracks) {
                TrackLayersMenuButton(
                    availableTrackTypes = availableTrackTypes,
                    enabledTrackTypes = enabledTrackTypes,
                    onToggleTrackType = onToggleTrackType
                )
            }
        },
        onMapClick = if (showMap && hasGpsTrack && !initialAutoShareSnapshot) {
            { showFullscreenMap = true }
        } else {
            null
        },
        onSplitLapClick = {
            candidateSplitIndex = null
            showCreateLapSheet = true
        },
        onCreateLapClick = { scrubDist ->
            candidateSplitIndex = activeScrubPath?.indexOfFirst { it.distance >= scrubDist }?.takeIf { it >= 0 }
            showCreateLapSheet = true
        },
        onMoveBoundaryClick = { boundaryLapNr ->
            moveBoundaryLapNr = boundaryLapNr
            showMoveBoundarySheet = true
        },
        onLapClick = { lap ->
            activeEditingLap = lap
            selectedLapNr = lap.lapNr
        },
        modifier = modifier
    )

    if (showFullscreenMap && showMap && hasGpsTrack) {
        FullScreenWorkoutMapDialog(
            bSportType = workoutData.bSportType,
            tracks = tracks,
            availableTrackTypes = availableTrackTypes,
            enabledTrackTypes = enabledTrackTypes,
            onToggleTrackType = onToggleTrackType,
            showTechnicalTracks = showTechnicalTracks,
            segments = segments,
            routes = routes,
            markers = markers,
            knownLocations = knownLocations,
            climbs = climbs,
            lapSegment = lapSegment,
            layerConfig = workoutMapLayerConfig,
            onToggleSpatialLayer = { layer ->
                coroutineScope.launch {
                    preferenceManager.toggleWorkoutMapSpatialLayer(layer)
                }
            },
            onSelectBaseMapType = { baseType ->
                coroutineScope.launch {
                    preferenceManager.setWorkoutMapBaseType(baseType)
                }
            },
            onClose = { showFullscreenMap = false },
            activeScrubPath = activeScrubPath
        )
    }

    if (showClusterSelectionDialog) {
        val targetWorkout = effectiveWorkoutData
        val canCreateNewCluster = clusterRepository.hasSpatialFingerprint(targetWorkout)
        WorkoutClusterSelectionDialog(
            title = stringResource(R.string.cluster_move_workout_title),
            candidates = clusterCandidates,
            onSelect = { target ->
                showClusterSelectionDialog = false
                clusterOverride = target.id to target.name
                coroutineScope.launch {
                    clusterRepository.moveOrAssignWorkoutToCluster(targetWorkout, target.id)
                }
            },
            onDismiss = { showClusterSelectionDialog = false },
            sportNameResolver = { clusterRepository.getSportName(it) },
            bSportTypeResolver = { clusterRepository.getBSportType(it) },
            onCreateNew = if (canCreateNewCluster) {
                {
                    showClusterSelectionDialog = false
                    coroutineScope.launch {
                        val (newId, newName) = clusterRepository.createNewClusterFromWorkout(targetWorkout)
                        if (newId > 0L) {
                            clusterOverride = newId to newName
                        }
                    }
                }
            } else null,
            onUnassign = if (targetWorkout.clusterId > 0) {
                {
                    showClusterSelectionDialog = false
                    clusterOverride = -1L to null
                    coroutineScope.launch {
                        clusterRepository.unassignWorkoutFromCluster(targetWorkout.id)
                    }
                }
            } else null
        )
    }

    if (showCreateLapSheet) {
        CreateLapOnMapSheet(
            workoutId = workoutData.id,
            bSportType = workoutData.bSportType,
            initialSampleIndex = candidateSplitIndex,
            existingLaps = workoutData.laps,
            repository = repository,
            onDismiss = { showCreateLapSheet = false },
            onSplitConfirmed = { splitIndex ->
                coroutineScope.launch {
                    repository.splitLapAtSample(workoutData.id, splitIndex)
                    showCreateLapSheet = false
                }
            }
        )
    }

    if (showMoveBoundarySheet) {
        MoveLapBoundaryOnMapSheet(
            workoutId = workoutData.id,
            boundaryLapNr = moveBoundaryLapNr,
            bSportType = workoutData.bSportType,
            existingLaps = workoutData.laps,
            repository = repository,
            onDismiss = { showMoveBoundarySheet = false },
            onBoundaryMoved = { boundaryLapNr, newIndex ->
                coroutineScope.launch {
                    repository.moveLapBoundary(workoutData.id, boundaryLapNr, newIndex)
                    showMoveBoundarySheet = false
                }
            }
        )
    }

    activeEditingLap?.let { lap ->
        LapEditBottomSheet(
            workoutData = workoutData,
            initialLapNr = lap.lapNr,
            isPlayServiceAvailable = isPlayServiceAvailable,
            onDismissRequest = { activeEditingLap = null },
            onSaveLap = { lapNr, name, description ->
                coroutineScope.launch {
                    repository.updateLapDetails(workoutData.id, lapNr, name, description)
                }
            },
            onMoveBoundaryClick = { boundaryLapNr ->
                activeEditingLap = null
                moveBoundaryLapNr = boundaryLapNr
                showMoveBoundarySheet = true
            },
            onDeleteLapClick = { boundaryLapNr ->
                coroutineScope.launch {
                    repository.deleteLap(workoutData.id, boundaryLapNr)
                    activeEditingLap = null
                }
            }
        )
    }
}

@Composable
private fun getTrackTypeName(type: TrackType): String {
    return when (type) {
        TrackType.BEST -> stringResource(R.string.track_type_best)
        TrackType.GPS -> stringResource(R.string.track_type_gps)
        TrackType.FUSED -> stringResource(R.string.track_type_fused)
        TrackType.ATT_FUSED -> stringResource(R.string.track_type_att_fused)
    }
}

/**
 * Deterministic semantics test tags for [TrackLayersMenuButton] (`REQ-REV-016.2` / `TST-REV-017`).
 */
internal object TrackOnMapTestTags {
    const val EXPAND_FULLSCREEN_BUTTON = "track_expand_fullscreen_button"
    const val LAYERS_BUTTON = "track_layers_button"
    const val LAYERS_MENU = "track_layers_menu"
    fun layerMenuItem(type: TrackType): String = "track_layers_item_${type.name}"
}

/**
 * Subtle floating circular button on the embedded Workout Details map launching full-screen workout map mode
 * (`REQ-MAP-069.1`).
 */
@Composable
internal fun WorkoutMapExpandButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .testTag(TrackOnMapTestTags.EXPAND_FULLSCREEN_BUTTON),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = stringResource(R.string.workout_map_open_fullscreen),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Floating circular Layers action button and [DropdownMenu] toggling technical [TrackType]
 * overlays (`REQ-MAP-012`, `REQ-UI-500.5`, `REQ-REV-016.2`).
 */
@Composable
internal fun TrackLayersMenuButton(
    availableTrackTypes: Set<TrackType>,
    enabledTrackTypes: Set<TrackType>,
    onToggleTrackType: (TrackType) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { showTrackMenu = true },
            modifier = Modifier
                .size(44.dp)
                .testTag(TrackOnMapTestTags.LAYERS_BUTTON),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
            shadowElevation = 6.dp,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.track_layers),
                    modifier = Modifier.size(22.dp),
                    tint = if (enabledTrackTypes.size > 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = TTAlpha.Disabled)
                    }
                )
            }
        }

        DropdownMenu(
            expanded = showTrackMenu,
            onDismissRequest = { showTrackMenu = false },
            modifier = Modifier.testTag(TrackOnMapTestTags.LAYERS_MENU)
        ) {
            TrackType.entries.forEach { type ->
                val isAvailable = type in availableTrackTypes
                DropdownMenuItem(
                    enabled = isAvailable,
                    modifier = Modifier.testTag(TrackOnMapTestTags.layerMenuItem(type)),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.alpha(if (isAvailable) TTAlpha.High else TTAlpha.Disabled)
                        ) {
                            Checkbox(
                                checked = enabledTrackTypes.contains(type),
                                onCheckedChange = null,
                                enabled = isAvailable
                            )
                            Surface(
                                modifier = Modifier.size(12.dp),
                                color = type.color,
                                shape = RoundedCornerShape(2.dp)
                            ) {}
                            Text(
                                text = getTrackTypeName(type),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    onClick = { onToggleTrackType(type) }
                )
            }
        }
    }
}
