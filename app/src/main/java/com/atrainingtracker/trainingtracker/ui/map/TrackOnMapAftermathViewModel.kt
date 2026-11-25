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

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageTimeline
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.util.safeEnumValueOfOrNull
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.util.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.zones.HrvSessionThreshold
import com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepository
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AftermathMapUIState(
    val tracks: List<MapTrack> = emptyList(),
    val availableTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
    val segments: List<MapSegment> = emptyList(),
    val routes: List<MapRoute> = emptyList(),
    val markers: List<LocationMarker> = emptyList(),
    val climbs: List<Climb> = emptyList(),
    val knownLocations: List<KnownLocationItem> = emptyList(),
    val bSportType: BSportType = BSportType.UNKNOWN,
    val zoomFocus: MapZoomFocus = MapZoomFocus.FIT_PRIMARY,
    val hrZoneDistribution: ZoneDistributionData? = null,
    val powerZoneDistribution: ZoneDistributionData? = null,
    val gradeZoneDistribution: ZoneDistributionData? = null,
    val telemetryPath: List<PathPoint> = emptyList(),
    val multiSensorOverlays: Map<TelemetryMetricType, List<TelemetrySourceSeries>> = emptyMap(),
    val hrThresholds: HeartRateZoneThresholds? = null,
    val powerThresholds: PowerZoneThresholds? = null,
    val recentSessions: List<HrvSessionThreshold> = emptyList(),
    val isSmoothedHrvStream: Boolean = false,
    val loadedWorkoutId: Long? = null,
    val activityCarriageTimeline: ActivityCarriageTimeline? = null,
    val isLoading: Boolean = false
)

class TrackOnMapAftermathViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val climbsFlowProvider: (() -> StateFlow<List<Climb>>?)? = null,
    private val knownLocationsFlowProvider: (() -> StateFlow<List<KnownLocationItem>>?)? = null
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AftermathMapUIState())
    val uiState = _uiState.asStateFlow()

    // TODO: Move to WorkoutRepository.

    private val workoutRepository = WorkoutRepository.getInstance(application)
    private val segmentsRepository = SegmentsRepository.getInstance(application)
    private val routesRepository = RoutesRepository.getInstance(application)
    private val clusterRepository by lazy { WorkoutClusterRepository.getInstance(application) }
    private val liveClimbsFlow: StateFlow<List<Climb>>? by lazy {
        climbsFlowProvider?.invoke()
            ?: if (Build.VERSION.SDK_INT == 0) null
            else runCatching { LiveClimbsRepository.getInstance(application).allStoredClimbs }.getOrNull()
    }
    private val knownLocationsFlow: StateFlow<List<KnownLocationItem>>? by lazy {
        knownLocationsFlowProvider?.invoke()
            ?: if (Build.VERSION.SDK_INT == 0) null
            else runCatching { KnownLocationsRepository.getInstance(application).locationsFlow }.getOrNull()
    }
    private val prefManager = com.atrainingtracker.trainingtracker.MyPreferenceManager.getInstance(application)

    init {
        // Reactively observe stored climbs & favorite locations so cold-start SQLite hydration
        // propagates immediately into AftermathMapUIState without one-shot empty-list races (REQ-MAP-069.3).
        liveClimbsFlow?.let { flow ->
            viewModelScope.launch {
                flow.collect { storedClimbs ->
                    _uiState.update { it.copy(climbs = storedClimbs) }
                }
            }
        }
        knownLocationsFlow?.let { flow ->
            viewModelScope.launch {
                flow.collect { favoriteLocations ->
                    _uiState.update { it.copy(knownLocations = favoriteLocations) }
                }
            }
        }
    }

    private val _clusterCandidates = MutableStateFlow<List<Pair<WorkoutCluster, Double>>>(emptyList())
    /** Candidate route clusters for the active cluster-change dialog (`REQ-UI-2786.3`). */
    val clusterCandidates: StateFlow<List<Pair<WorkoutCluster, Double>>> = _clusterCandidates.asStateFlow()


    val enabledTrackTypes: StateFlow<Set<TrackType>> = prefManager.enabledTrackTypesFlow
        .map { strings ->
            strings.mapNotNull {
                safeEnumValueOfOrNull<TrackType>(it)
            }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = setOf(TrackType.BEST)
        )

    fun toggleTrackTypeEnabled(type: TrackType) {
        viewModelScope.launch {
            val isEnabled = enabledTrackTypes.value.contains(type)
            prefManager.setTrackTypeEnabled(type.name, !isEnabled)
        }
    }

    private var loadJob: Job? = null
    @androidx.annotation.VisibleForTesting
    internal val currentLoadJob: Job? get() = loadJob

    fun loadAftermathData(workoutData: WorkoutData) {
        loadJob?.cancel()
        val workoutId = workoutData.id
        val bSportType = workoutData.bSportType
        val isSameWorkout = (_uiState.value.loadedWorkoutId == workoutId)

        // --- PHASE 1: Immediate Reset or In-Place Preservation (REQ-UI-427.2) ---
        // If switching to a different workout, clear previous state to prevent "ghost" data.
        // If reloading the same workout (e.g. lap split/move, export broadcasts), retain tracks
        // to prevent visibleTabs collapse and HorizontalPager tab jumping (Rule 73).
        if (!isSameWorkout) {
            _uiState.update { current ->
                AftermathMapUIState(
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    bSportType = bSportType,
                    tracks = emptyList(),
                    markers = emptyList(),
                    climbs = liveClimbsFlow?.value ?: current.climbs,
                    knownLocations = knownLocationsFlow?.value ?: current.knownLocations,
                    loadedWorkoutId = workoutId,
                    isLoading = true
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    bSportType = bSportType,
                    loadedWorkoutId = workoutId,
                    isLoading = true
                )
            }
        }

        loadJob = viewModelScope.launch(ioDispatcher) {
            try {
            // --- PHASE 2: Fast Track (From WorkoutData Polyline & Streams) ---
            // Decodes the thinned data already present in workoutData for instant UI feedback.
            // When updating the same workout and tracks are already present, bypass fast-track polyline overwrite
            if (workoutData.mapPolyline.isNotEmpty() && (!isSameWorkout || _uiState.value.tracks.isEmpty())) {
                val latLngs = PolyUtil.decode(workoutData.mapPolyline)

                // Decode elevation streams
                val alts = if (workoutData.encodedAltitudes.isNotEmpty()) {
                    NumericalEncodingUtils.decodeDoubles(workoutData.encodedAltitudes)
                } else emptyList()

                val dists = if (workoutData.encodedDistances.isNotEmpty()) {
                    NumericalEncodingUtils.decodeDoubles(workoutData.encodedDistances)
                } else emptyList()

                // Map points directly since sampling is identical
                val fastPath = latLngs.mapIndexed { index, latLng ->
                    PathPoint(
                        distance = dists.getOrElse(index) { 0.0 },
                        latLng = latLng,
                        altitude = alts.getOrNull(index)
                    )
                }

                val fastTrack = MapTrack(
                    id = workoutId,
                    type = TrackType.BEST,
                    bSportType = bSportType,
                    path = fastPath
                )

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(tracks = listOf(fastTrack)) }
                }
            }

            // --- PHASE 3: Calculate Extrema (Markers) ---
            val markerList = workoutRepository.getWorkoutMarkers(workoutData)

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(markers = markerList) }
            }

            // --- PHASE 4: High-Resolution Tracks (From Samples DB) ---
            // Load full fidelity data and simplify aggressively for map performance
            // ATT-4003 / REQ-DAT-033: Consolidate multi-pass scans into single projected query
            val telemetry = try {
                workoutRepository.loadWorkoutDetailTelemetry(workoutId, bSportType)
            } catch (_: Throwable) {
                null
            }

            val fullTracks = TrackType.entries.mapNotNull { type ->
                val fullPath = telemetry?.tracks?.get(type) ?: workoutRepository.getWorkoutTrackPoints(workoutId, type)
                if (fullPath.isNotEmpty()) {
                    // PERFORMANCE: Cap tracks at ~800 points for smooth UI interaction
                    val simplifiedPath = if (fullPath.size > 800) {
                        val latLngs = fullPath.map { it.latLng }
                        
                        var tolerance = if (type == TrackType.BEST) 1.0 else 3.0
                        var simplifiedLatLngs = PolyUtil.simplify(latLngs, tolerance)
                        
                        // Iteratively increase tolerance if path is still too large
                        var iterations = 0
                        while (simplifiedLatLngs.size > 1000 && iterations < 6) {
                            tolerance *= 2.0
                            simplifiedLatLngs = PolyUtil.simplify(latLngs, tolerance)
                            iterations++
                        }
                        
                        // Reconstruction of PathPoints
                        val result = ArrayList<PathPoint>(simplifiedLatLngs.size)
                        var originalIdx = 0
                        for (target in simplifiedLatLngs) {
                            while (originalIdx < fullPath.size && fullPath[originalIdx].latLng != target) {
                                originalIdx++
                            }
                            if (originalIdx < fullPath.size) {
                                result.add(fullPath[originalIdx])
                                originalIdx++
                            }
                        }
                        result
                    } else fullPath

                    MapTrack(
                        id = type.ordinal.toLong(),
                        type = type,
                        bSportType = bSportType,
                        path = simplifiedPath
                    )
                } else null
            }

            val hasValidSampleTrack = fullTracks.any { it.path.size >= 2 }
            if (fullTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        val tracksToSet = if (hasValidSampleTrack || current.tracks.isEmpty()) {
                            fullTracks
                        } else {
                            current.tracks
                        }
                        current.copy(
                            tracks = tracksToSet,
                            availableTrackTypes = fullTracks.map { it.type }.toSet(),
                            multiSensorOverlays = telemetry?.multiSensorOverlays ?: emptyMap(),
                            activityCarriageTimeline = telemetry?.activityCarriageTimeline
                        )
                    }
                }
            } else {
                val (telemetryPoints, tracklessOverlays) = try {
                    workoutRepository.loadTracklessWorkoutTelemetry(workoutId)
                } catch (_: Throwable) {
                    workoutRepository.getWorkoutTelemetryPoints(workoutId) to emptyMap()
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            telemetryPath = telemetryPoints,
                            multiSensorOverlays = tracklessOverlays,
                            activityCarriageTimeline = telemetry?.activityCarriageTimeline
                        )
                    }
                }
            }

            // --- PHASE 5: Segments (All for spatial context) ---
            val allSegments = segmentsRepository.allSegmentsWithPath.value
            val mapSegments = allSegments
                .map { segment ->
                    MapSegment(
                        stravaId = segment.summary.stravaId,
                        name = segment.summary.name,
                        path = segment.path,
                        bSportType = segment.summary.bSportType,
                        showStartAndFinishText = false,
                    )
                }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(segments = mapSegments) }
            }

            // --- PHASE 6: Routes, Climbs & Favorite Locations (All for spatial context, REQ-MAP-069.3) ---
            val allRoutes = routesRepository.allRoutes.value
            val mapRoutes = allRoutes
                .map { it.toMapRoute() }
            val storedClimbs = liveClimbsFlow?.value
            val favoriteLocations = knownLocationsFlow?.value

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        routes = mapRoutes,
                        climbs = storedClimbs ?: current.climbs,
                        knownLocations = favoriteLocations ?: current.knownLocations
                    )
                }
            }

            // --- PHASE 7: Zone Analytics (Heart Rate, Power & Grade Zone Distribution & Thresholds) ---
            val hrDistribution = telemetry?.hrZoneDistribution ?: workoutRepository.getHeartRateZoneDistribution(workoutId, bSportType)
            val powerDistribution = telemetry?.powerZoneDistribution ?: workoutRepository.getPowerZoneDistribution(workoutId)
            val gradeDistribution = telemetry?.gradeZoneDistribution ?: workoutRepository.getGradeZoneDistribution(workoutId)
            val hrThresholds = telemetry?.hrThresholds ?: TelemetryZoneMath.loadHeartRateThresholds(getApplication(), bSportType)
            val powerThresholds = telemetry?.powerThresholds ?: TelemetryZoneMath.loadPowerThresholds(getApplication())
            val recentHrvSessions = workoutRepository.getRecentHrvSessionThresholds(workoutId)
            val isSmoothedHrv = workoutRepository.isWorkoutHrvSmoothed(workoutId)
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        hrZoneDistribution = hrDistribution,
                        powerZoneDistribution = powerDistribution,
                        gradeZoneDistribution = gradeDistribution,
                        hrThresholds = hrThresholds,
                        powerThresholds = powerThresholds,
                        recentSessions = recentHrvSessions,
                        isSmoothedHrvStream = isSmoothedHrv,
                        isLoading = false
                    )
                }
            }

            // --- PHASE 8: Lazy Cardio-Power Dynamics Resolution (REQ-REV-009 / ATT-6722) ---
            if (workoutData.cardioPowerDynamics == null && workoutData.activeTimeSec >= PowerHrDynamicsCalculator.MIN_QUALIFYING_DURATION_SEC) {
                workoutRepository.getOrComputeCardioPowerDynamics(workoutId)
            }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                withContext(Dispatchers.Main) {
                    if (_uiState.value.loadedWorkoutId == workoutId) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
            }
        }
    }

    /**
     * Unidirectionally updates the athlete's cycling Functional Threshold Power (FTP), optional LT1 power
     * threshold, and optionally recalculated Coggan power zones in SettingsDataStore and AthleteMetrics.db
     * (`REQ-PER-037.4`, `REQ-SET-135.4`).
     */
    fun updateFtp(
        newFtp: Int,
        lt1PowerWatts: Int? = null,
        recalculateZones: Boolean = true
    ) {
        viewModelScope.launch(ioDispatcher) {
            val settingsDataStore = SettingsDataStore.getInstance(getApplication())
            val metricsRepo = AthleteMetricsRepository.getInstance(getApplication())
            val thresholds = if (recalculateZones) {
                ZoneCalculationMath.calculateCogganPowerZones(newFtp).also { t ->
                    val cutoffs = listOf(t.z1Max, t.z2Max, t.z3Max, t.z4Max, t.z5Max, t.z6Max)
                    settingsDataStore.saveCalculatedPowerZones(newFtp, cutoffs)
                }
            } else null

            metricsRepo.addEntry(
                metricType = AthleteMetricType.FTP_WATTS,
                value = newFtp.toDouble()
            )
            if (lt1PowerWatts != null && AthleteMetricType.LT1_PWR_BIKE_WATTS.isValidMetricValue(lt1PowerWatts.toDouble())) {
                metricsRepo.addEntry(
                    metricType = AthleteMetricType.LT1_PWR_BIKE_WATTS,
                    value = lt1PowerWatts.toDouble()
                )
            }
            if (thresholds != null) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(powerThresholds = thresholds)
                }
            }
        }
    }

    /**
     * Persists accepted HRVT1 (`LT1_HR_*`) and HRVT2 (`LTHR_*`) thresholds into [AthleteMetricsRepository]
     * (syncing [SettingsDataStore]) and optionally recalculates threshold-anchored Heart Rate training zones
     * (`REQ-PER-037.7`, `REQ-SET-135.4`).
     */
    fun updateHrZones(
        newHrvt1: Int,
        newHrvt2: Int,
        recalculateZones: Boolean = true
    ) {
        viewModelScope.launch(ioDispatcher) {
            val settingsDataStore = SettingsDataStore.getInstance(getApplication())
            val metricsRepo = AthleteMetricsRepository.getInstance(getApplication())
            val isRun = _uiState.value.bSportType == BSportType.RUN
            val zoneType = if (isRun) SettingsDataStore.ZoneType.HR_RUN else SettingsDataStore.ZoneType.HR_BIKE
            val lt1Type = if (isRun) AthleteMetricType.LT1_HR_RUN_BPM else AthleteMetricType.LT1_HR_BIKE_BPM
            val lthrType = if (isRun) AthleteMetricType.LTHR_RUN_BPM else AthleteMetricType.LTHR_BIKE_BPM

            if (lt1Type.isValidMetricValue(newHrvt1.toDouble())) {
                metricsRepo.addEntry(metricType = lt1Type, value = newHrvt1.toDouble())
            }
            if (lthrType.isValidMetricValue(newHrvt2.toDouble())) {
                metricsRepo.addEntry(metricType = lthrType, value = newHrvt2.toDouble())
            }

            if (recalculateZones) {
                val hrMaxType = if (isRun) AthleteMetricType.HR_MAX_RUN_BPM else AthleteMetricType.HR_MAX_BIKE_BPM
                val sportHrMax = if (isRun) {
                    settingsDataStore.hrMaxRunFlow.firstOrNull()
                } else {
                    settingsDataStore.hrMaxBikeFlow.firstOrNull()
                }
                val configuredHrMax = sportHrMax
                    ?: settingsDataStore.hrMaxFlow.firstOrNull()
                    ?: SettingsDataStore.DEFAULT_HR_MAX
                val hrMax = if (configuredHrMax > newHrvt2) {
                    configuredHrMax
                } else {
                    (newHrvt2 + 10).coerceAtMost(ZoneCalculationMath.MAX_HR_MAX)
                }
                val hrRest = settingsDataStore.hrRestFlow.firstOrNull()
                if ((sportHrMax == null || hrMax > configuredHrMax) && hrMaxType.isValidMetricValue(hrMax.toDouble())) {
                    metricsRepo.addEntry(metricType = hrMaxType, value = hrMax.toDouble())
                }

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
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(hrThresholds = thresholds)
                }
            }
        }
    }

    /**
     * Loads candidate route clusters for [workout] on [ioDispatcher], including non-GPS fallback (`REQ-UI-2786.3`).
     */
    fun loadCandidateClustersForWorkout(workout: WorkoutData) {
        viewModelScope.launch(ioDispatcher) {
            _clusterCandidates.value = clusterRepository.getCandidateClustersForWorkout(workout)
        }
    }

    /**
     * Moves or assigns [workout] to [targetClusterId] and updates repository state (`REQ-UI-2786.3`).
     */
    fun moveOrAssignWorkoutToCluster(workout: WorkoutData, targetClusterId: Long) {
        viewModelScope.launch(ioDispatcher) {
            clusterRepository.moveOrAssignWorkoutToCluster(workout, targetClusterId)
        }
    }

    /**
     * Creates a new favorite route cluster from [workout]'s spatial fingerprint (`REQ-UI-2786.3`).
     */
    fun createNewClusterFromWorkout(workout: WorkoutData) {
        viewModelScope.launch(ioDispatcher) {
            clusterRepository.createNewClusterFromWorkout(workout)
        }
    }

    /**
     * Unassigns [workoutId] from its current favorite route cluster (`REQ-UI-2786.3`).
     */
    fun unassignWorkoutFromCluster(workoutId: Long) {
        viewModelScope.launch(ioDispatcher) {
            clusterRepository.unassignWorkoutFromCluster(workoutId)
        }
    }

    /** Resolves localized sport display name for [sportId] (`REQ-UI-2786.3`). */
    fun getSportName(sportId: Long): String = clusterRepository.getSportName(sportId)

    /** Resolves [BSportType] for [sportId] (`REQ-UI-2786.3`). */
    fun getBSportType(sportId: Long): BSportType = clusterRepository.getBSportType(sportId)

}
