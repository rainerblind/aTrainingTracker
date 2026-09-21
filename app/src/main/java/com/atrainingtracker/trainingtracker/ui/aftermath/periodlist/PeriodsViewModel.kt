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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.ui.map.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates the UI state for the period detail map (ATT-440, ATT-1151).
 * Matches the robust loading pattern used in Workout Clusters.
 */
data class PeriodMapState(
    val tracks: List<MapTrack> = emptyList(),
    val workoutIdToHeatmapPathMap: Map<Long, List<LatLng>> = emptyMap(),
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
class PeriodsViewModel(application: Application) : AndroidViewModel(application) {

    private val workoutRepo = WorkoutRepository.getInstance(application)
    private val periodsRepo = PeriodsRepository.getInstance(application)
    private val prefManager = com.atrainingtracker.trainingtracker.MyPreferenceManager(application)

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
                try { PeriodMarkerType.valueOf(it) } catch(_: Exception) { null } 
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
    fun showPeriodMap(summary: PeriodSummary) {
        selectionJob?.cancel()
        _selectedPeriod.value = summary
        
        selectionJob = viewModelScope.launch {
            _mapState.value = PeriodMapState(isLoading = true)
            
            // ATT-440: Adoption of Cluster loading algorithm
            // 1. Fetch Source of Truth for the range (Guaranteed completeness)
            val workouts = withContext(Dispatchers.IO) {
                periodsRepo.getWorkoutsForRange(summary.startTimestampS, summary.endTimestampS)
            }
            
            // 2. Background Processing on Dispatchers.Default (REQ-PER-013)
            withContext(Dispatchers.Default) {
                // Partition workouts into geographic activity regions
                val detectedRegions = SpatialRegionEngine.detectRegions(workouts)
                val primaryRegion = detectedRegions.firstOrNull { it.isPrimary } ?: detectedRegions.firstOrNull()

                // Decode polyline paths once for both heatmap and lightweight vector tracks
                val heatmapPathMap = workouts.associate { w ->
                    w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
                }.filterValues { it.isNotEmpty() }
                
                val isLargePeriod = workouts.size > MAX_PERIOD_VECTOR_TRACKS

                // REQ-PER-012: In high-volume periods (> MAX_PERIOD_VECTOR_TRACKS), suppress
                // instantiating hundreds of vector MapTrack and PeriodPeakMarker objects to prevent OOM.
                val tracks = if (isLargePeriod) {
                    emptyList()
                } else {
                    workouts.mapNotNull { w ->
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
                    workouts.flatMap { w ->
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
                    memberMarkers = markers,
                    regions = detectedRegions,
                    selectedRegionId = primaryRegion?.id,
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
    
    @Deprecated("Use migrationStatus instead", ReplaceWith("migrationStatus.value?.phases?.lastOrNull()?.progress"))
    val migrationProgress: StateFlow<Float?> = periodsRepo.migrationStatus
        .map { it?.phases?.lastOrNull()?.progress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadPeriods() {
        viewModelScope.launch {
            workoutRepo.loadAllWorkouts()
        }
    }

    private val _peekedWorkoutDataWithTrack = MutableStateFlow<WorkoutDataWithTrack?>(null)
    val peekedWorkoutDataWithTrack = _peekedWorkoutDataWithTrack.asStateFlow()

    fun selectWorkoutForPeek(id: Long) {
        viewModelScope.launch {
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
        _peekedWorkoutDataWithTrack.value = null
    }
}
