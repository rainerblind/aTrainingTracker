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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.location.RegionNameResolver
import com.atrainingtracker.trainingtracker.ui.map.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import com.atrainingtracker.util.safeEnumValueOfOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates the UI state for the period detail map (ATT-440, ATT-1151).
 * Matches the robust loading pattern used in Workout Clusters.
 */
data class PeriodMapState(
    val tracks: List<MapTrack> = emptyList(),
    val workoutIdToHeatmapPathMap: Map<Long, List<LatLng>> = emptyMap(),
    val workoutIdToSportMap: Map<Long, BSportType> = emptyMap(),
    val memberMarkers: List<PeriodPeakMarker> = emptyList(),
    val regions: List<SpatialRegion> = emptyList(),
    val selectedRegionId: String? = null,
    val isLoading: Boolean = false,
    val focusEpochMs: Long = 0L
)

/**
 * Manages the UI state and background data aggregation for the Workout PeriodsAnalytical hub.
 *
 * This ViewModel orchestrates the progressive loading of historical summaries and manages
 * the transition between the period list and the detailed spatial analytics view. It features
 * a selection-driven loading algorithm to ensure 100% data visibility on maps.
 *
 * Architectural Role: Presentation layer for historical analytical trends.
 */
class PeriodsViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    private val workoutRepo = WorkoutRepository.getInstance(application)
    private val periodsRepo = PeriodsRepository.getInstance(application)
    private val prefManager = com.atrainingtracker.trainingtracker.MyPreferenceManager(application)

    private val _selectedMetric = MutableStateFlow(PeriodBarMetric.DURATION)
    val selectedMetric: StateFlow<PeriodBarMetric> = _selectedMetric.asStateFlow()

    fun selectMetric(metric: PeriodBarMetric) {
        _selectedMetric.value = metric
    }

    val groups = listOf(
        application.getString(R.string.workout_periods__days),
        application.getString(R.string.workout_periods__weeks),
        application.getString(R.string.workout_periods__months),
        application.getString(R.string.workout_periods__years)
    )

    private val _selectedPeriod = MutableStateFlow<PeriodSummary?>(null)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _mapState = MutableStateFlow(PeriodMapState())
    val mapState = _mapState.asStateFlow()

    private var selectionJob: Job? = null

    val enabledMarkerTypes: StateFlow<Set<PeriodMarkerType>> = prefManager.enabledPeriodMarkerTypesFlow
        .map { strings -> 
            strings.mapNotNull { 
                safeEnumValueOfOrNull<PeriodMarkerType>(it)
            }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = setOf(PeriodMarkerType.ALTITUDE, PeriodMarkerType.DISTANCE)
        )

    fun toggleMarkerTypeEnabled(type: PeriodMarkerType) {
        viewModelScope.launch {
            val isEnabled = enabledMarkerTypes.value.contains(type)
            prefManager.setPeriodMarkerTypeEnabled(type.name, !isEnabled)
        }
    }

    /**
     * Selects a specific activity region by ID or null for all regions (ATT-1151).
     */
    fun selectRegion(regionId: String?) {
        _mapState.update { it.copy(selectedRegionId = regionId, focusEpochMs = System.currentTimeMillis()) }
    }

    /**
     * Triggers the transition to the Period Map view and launches the exhaustive loading job.
     *
     * Implementation: Uses a selection-driven algorithm (standardized with Clusters) that
     * queries the database directly for the range. This ensures that every workout in the
     * period is eventually mapped, bypassing the repository's background rollup state.
     *
     * @param summary The period to analyze.
     */
    fun showPeriodMap(summary: PeriodSummary, initialRegionId: String? = null) {
        selectionJob?.cancel()
        val activeSports = _selectedSports.value
        val effectiveSummary = if (activeSports.isNotEmpty()) {
            filterPeriodSummary(summary, activeSports) ?: summary
        } else {
            summary
        }
        _selectedPeriod.value = effectiveSummary
        _mapState.value = PeriodMapState(isLoading = true, selectedRegionId = initialRegionId)
        
        selectionJob = viewModelScope.launch {
            // ATT-440: Adoption of Cluster loading algorithm
            // 1. Fetch Source of Truth for the range (Guaranteed completeness)
            val workouts = withContext(ioDispatcher) {
                periodsRepo.getWorkoutsForRange(summary.startTimestampS, summary.endTimestampS)
            }
            
            // 2. Background Processing on defaultDispatcher (REQ-PER-013)
            withContext(defaultDispatcher) {
                // Filter workouts by active master sport filter (ATT-4935 / REQ-PER-024.5)
                val filteredWorkouts = if (activeSports.isNotEmpty()) {
                    workouts.filter { it.bSportType in activeSports }
                } else {
                    workouts
                }

                // Partition workouts into geographic activity regions
                val rawRegions = SpatialRegionEngine.detectRegions(filteredWorkouts)
                val detectedRegions = rawRegions.map { region ->
                    val place = RegionNameResolver.resolvePlaceName(getApplication(), region.center, dispatcher = ioDispatcher)
                    if (place != null) {
                        region.copy(placeName = place)
                    } else {
                        region
                    }
                }
                val primaryRegion = detectedRegions.firstOrNull { it.isPrimary } ?: detectedRegions.firstOrNull()
                val activeRegionId = initialRegionId ?: primaryRegion?.id

                // Decode polyline paths once for both heatmap and lightweight vector tracks
                val rawDecodedPathMap = filteredWorkouts.associate { w ->
                    w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
                }.filterValues { it.isNotEmpty() }

                // Enforce detailed map vertex budget (MAX_DETAILED_MAP_VERTICES = 8_000) (REQ-PER-019.2 / ATT-4930)
                val baseTolerance = getPeriodSimplificationToleranceMeters(summary.periodType)
                val heatmapPathMap = simplifyPathMapToBudget(
                    pathMap = rawDecodedPathMap,
                    maxVertices = MAX_DETAILED_MAP_VERTICES,
                    baseTolerance = baseTolerance
                )
                
                val isLargePeriod = filteredWorkouts.size > MAX_PERIOD_VECTOR_TRACKS

                // REQ-PER-012: In high-volume periods (> MAX_PERIOD_VECTOR_TRACKS), suppress
                // instantiating hundreds of vector MapTrack and PeriodPeakMarker objects to prevent OOM.
                val tracks = if (isLargePeriod) {
                    emptyList()
                } else {
                    filteredWorkouts.mapNotNull { w ->
                        val points = heatmapPathMap[w.id] ?: return@mapNotNull null
                        MapTrack(
                            id = w.id,
                            type = TrackType.BEST,
                            bSportType = w.bSportType,
                            path = points.map { PathPoint(0.0, it, 0.0) },
                            isVisible = true,
                            minLat = w.minLat,
                            minLng = w.minLng,
                            maxLat = w.maxLat,
                            maxLng = w.maxLng
                        )
                    }
                }
                
                // Pre-calculate member markers (SCRUM-199 style) only for low-volume periods
                val markers = if (isLargePeriod) {
                    emptyList()
                } else {
                    filteredWorkouts.flatMap { w ->
                        val list = mutableListOf<PeriodPeakMarker>()
                        w.startLatLng?.let { 
                            list.add(PeriodPeakMarker(w.id, it, R.drawable.control_start, "${w.workoutName}: Start", PeriodMarkerType.START)) 
                        }
                        w.endLatLng?.let { 
                            list.add(PeriodPeakMarker(w.id, it, R.drawable.control_stop, "${w.workoutName}: End", PeriodMarkerType.END)) 
                        }
                        w.maxDisplacementLatLng?.let { 
                            list.add(PeriodPeakMarker(w.id, it, R.drawable.ic_distance, "${w.workoutName}: Apex", PeriodMarkerType.DISTANCE)) 
                        }
                        w.maxAltitudeLatLng?.let {
                            list.add(PeriodPeakMarker(w.id, it, R.drawable.ic_altitude, "${w.workoutName}: Max Altitude", PeriodMarkerType.ALTITUDE))
                        }
                        list
                    }
                }

                _mapState.value = PeriodMapState(
                    tracks = tracks,
                    workoutIdToHeatmapPathMap = heatmapPathMap,
                    workoutIdToSportMap = filteredWorkouts.associate { it.id to it.bSportType },
                    memberMarkers = markers,
                    regions = detectedRegions,
                    selectedRegionId = activeRegionId,
                    isLoading = false
                )
            }
        }
    }

    fun dismissPeriodMap() {
        selectionJob?.cancel()
        _selectedPeriod.value = null
        _mapState.value = PeriodMapState()
    }

    // Observe summarized periods and migration status from Repository
    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = periodsRepo.groupedPeriods
    val migrationStatus: StateFlow<MigrationStatus?> = periodsRepo.migrationStatus

    // Multi-Sport Filtering (ATT-4770 / REQ-PER-022)
    private val _selectedSports = MutableStateFlow<Set<BSportType>>(setOf(BSportType.BIKE, BSportType.RUN))
    val selectedSports: StateFlow<Set<BSportType>> = _selectedSports.asStateFlow()

    /**
     * Toggles inclusion of a sport in the Periods filter (ATT-4770, REQ-PER-022.2).
     * Enforces non-empty guard: deselecting the final remaining active sport is ignored.
     */
    fun toggleSportFilter(sport: BSportType) {
        _selectedSports.update { current ->
            if (current.contains(sport)) {
                if (current.size > 1) {
                    current - sport
                } else {
                    current
                }
            } else {
                current + sport
            }
        }
    }

    /**
     * Filters a PeriodSummary to include only the specified sport types (ATT-4770, ATT-4935, REQ-PER-024.5b).
     * Recalculates totalWorkouts, totalDurationSec, totalDistance, and symmetrically filters sportStats,
     * polylines, workoutIdToPolylineMap, workoutIdToAllPolylinesMap, workoutIdToPathMap, workoutIdToSportMap,
     * and extremaMarkers. Returns null if totalWorkouts == 0.
     */
    fun filterPeriodSummary(summary: PeriodSummary, sports: Set<BSportType>): PeriodSummary? {
        val filteredStats = summary.sportStats.filterKeys { it in sports }
        val totalWorkouts = filteredStats.values.sumOf { it.count }
        if (totalWorkouts == 0) return null

        val totalDurationSec = filteredStats.values.sumOf { it.totalDurationSec }
        val totalDistance = filteredStats.values.sumOf { it.totalDistanceMeters }

        // Symmetrically filter all polyline and path collections (REQ-PER-024.5b)
        // Bidirectional fallback ensures full backward compatibility when input contains only one polyline map
        val polylineSource = if (summary.workoutIdToPolylineMap.isNotEmpty()) {
            summary.workoutIdToPolylineMap
        } else {
            summary.workoutIdToAllPolylinesMap
        }

        val allPolylineSource = if (summary.workoutIdToAllPolylinesMap.isNotEmpty()) {
            summary.workoutIdToAllPolylinesMap
        } else {
            summary.workoutIdToPolylineMap
        }

        val filteredPolylineMap = if (polylineSource.isNotEmpty() && summary.workoutIdToSportMap.isNotEmpty()) {
            polylineSource.filterKeys { id -> summary.workoutIdToSportMap[id] in sports }
        } else {
            polylineSource
        }

        val filteredAllPolylinesMap = if (allPolylineSource.isNotEmpty() && summary.workoutIdToSportMap.isNotEmpty()) {
            allPolylineSource.filterKeys { id -> summary.workoutIdToSportMap[id] in sports }
        } else {
            allPolylineSource
        }

        val filteredPathMap = if (summary.workoutIdToPathMap.isNotEmpty() && summary.workoutIdToSportMap.isNotEmpty()) {
            summary.workoutIdToPathMap.filterKeys { id -> summary.workoutIdToSportMap[id] in sports }
        } else {
            summary.workoutIdToPathMap
        }

        val filteredPolylines = if (filteredAllPolylinesMap.isNotEmpty()) {
            filteredAllPolylinesMap.values.toList()
        } else if (filteredPolylineMap.isNotEmpty()) {
            filteredPolylineMap.values.toList()
        } else {
            if (summary.sportStats.keys.any { it in sports }) summary.polylines else emptyList()
        }

        val filteredSportMap = if (summary.workoutIdToSportMap.isNotEmpty()) {
            summary.workoutIdToSportMap.filterValues { it in sports }
        } else {
            summary.workoutIdToSportMap
        }

        val filteredMarkers = if (summary.workoutIdToSportMap.isNotEmpty()) {
            summary.extremaMarkers.filter { marker ->
                summary.workoutIdToSportMap[marker.workoutId] in sports
            }
        } else {
            summary.extremaMarkers
        }

        val filteredContributions = summary.yearlySportContributions.filterKeys { it in sports }

        return summary.copy(
            totalWorkouts = totalWorkouts,
            totalDurationSec = totalDurationSec,
            totalDistance = totalDistance,
            sportStats = filteredStats,
            polylines = filteredPolylines,
            workoutIdToPolylineMap = filteredPolylineMap,
            workoutIdToAllPolylinesMap = filteredAllPolylinesMap,
            workoutIdToPathMap = filteredPathMap,
            workoutIdToSportMap = filteredSportMap,
            extremaMarkers = filteredMarkers,
            yearlySportContributions = filteredContributions
        )
    }

    val filteredGroupedPeriods: StateFlow<List<List<PeriodSummary>>> = combine(
        groupedPeriods,
        selectedSports
    ) { groups, sports ->
        groups.map { periodList ->
            periodList.mapNotNull { filterPeriodSummary(it, sports) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    
    @Deprecated("Use migrationStatus instead", ReplaceWith("migrationStatus.value?.phases?.lastOrNull()?.progress"))
    val migrationProgress: StateFlow<Float?> = periodsRepo.migrationStatus
        .map { it?.phases?.lastOrNull()?.progress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadPeriods() {
        viewModelScope.launch {
            workoutRepo.loadAllWorkouts()
            periodsRepo.checkIntegrityAndSync()
        }
    }

    private var peekJob: Job? = null
    @androidx.annotation.VisibleForTesting
    internal val currentPeekJob: Job? get() = peekJob

    private val _peekedWorkoutDataWithTrack = MutableStateFlow<WorkoutDataWithTrack?>(null)
    val peekedWorkoutDataWithTrack = _peekedWorkoutDataWithTrack.asStateFlow()

    fun selectWorkoutForPeek(id: Long) {
        peekJob?.cancel()
        peekJob = viewModelScope.launch {
            val workout = workoutRepo.allWorkouts.value.find { it.id == id }
            if (workout != null) {
                _peekedWorkoutDataWithTrack.value = WorkoutDataWithTrack(
                    workoutData = workout,
                    trackPoints = workoutRepo.getWorkoutTrackPoints(id, TrackType.BEST)
                )
            }
        }
    }

    fun clearPeekSelection() {
        peekJob?.cancel()
        _peekedWorkoutDataWithTrack.value = null
    }
}
