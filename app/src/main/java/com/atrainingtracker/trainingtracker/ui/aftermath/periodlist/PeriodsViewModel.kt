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
 * Encapsulates the UI state for the period detail map (ATT-440).
 * Matches the robust loading pattern used in Workout Clusters.
 */
data class PeriodMapState(
    val tracks: List<MapTrack> = emptyList(),
    val workoutIdToHeatmapPathMap: Map<Long, List<LatLng>> = emptyMap(),
    val memberMarkers: List<PeriodPeakMarker> = emptyList(),
    val isLoading: Boolean = false,
)

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
            
            // 2. Background Processing
            withContext(Dispatchers.Default) {
                val tracks = workouts.map { it.toMapTrack().copy(isVisible = true) }
                val heatmapPathMap = workouts.associate { w ->
                    w.id to if (w.mapPolyline.isNotEmpty()) PolyUtil.decode(w.mapPolyline) else emptyList()
                }.filterValues { it.isNotEmpty() }
                
                // Pre-calculate member markers (SCRUM-199 style)
                val markers = workouts.flatMap { w ->
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

                _mapState.value = PeriodMapState(
                    tracks = tracks,
                    workoutIdToHeatmapPathMap = heatmapPathMap,
                    memberMarkers = markers,
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
