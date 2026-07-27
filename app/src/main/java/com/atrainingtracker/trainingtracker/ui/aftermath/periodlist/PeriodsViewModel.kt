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
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val _periodPaths = MutableStateFlow<Map<Long, List<com.google.android.gms.maps.model.LatLng>>>(emptyMap())

    val isHeatmapEnabled: StateFlow<Boolean> = prefManager.isHeatmapEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

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

    /**
     * ATT-440: Reactive pipeline that provides the selected period summary enriched with
     * simplified path data that "grows" as background loading progresses.
     */
    val selectedPeriod: StateFlow<PeriodSummary?> = combine(
        _selectedPeriod,
        _periodPaths
    ) { selected, paths ->
        selected?.copy(workoutIdToPathMap = paths)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // --- REACTIVE PATH AGGREGATION (ATT-440) ---
        viewModelScope.launch {
            combine(_selectedPeriod, workoutRepo.allWorkouts) { selected, allWorkouts ->
                selected to allWorkouts
            }.collectLatest { (selected, allWorkouts) ->
                if (selected == null) {
                    _periodPaths.value = emptyMap()
                    return@collectLatest
                }

                val workoutsInPeriod = allWorkouts.filter { w ->
                    w.startTimeS >= selected.startTimestampS && w.startTimeS <= selected.endTimestampS
                }

                val currentPaths = _periodPaths.value.toMutableMap()
                var changed = false

                workoutsInPeriod.forEach { workout ->
                    if (!currentPaths.containsKey(workout.id)) {
                        // PERFORMANCE: Fetch and simplify in background
                        val fullPath = workoutRepo.getWorkoutTrackPoints(workout.id, TrackType.BEST)
                        val simplified = if (fullPath.size > 800) {
                            val latLngs = fullPath.map { it.latLng }
                            var tolerance = 1.0
                            var simplifiedPath = PolyUtil.simplify(latLngs, tolerance)
                            var iterations = 0
                            while (simplifiedPath.size > 1000 && iterations < 5) {
                                tolerance *= 2.0
                                simplifiedPath = PolyUtil.simplify(latLngs, tolerance)
                                iterations++
                            }
                            simplifiedPath
                        } else {
                            fullPath.map { it.latLng }
                        }
                        currentPaths[workout.id] = simplified
                        changed = true
                    }
                }

                if (changed) {
                    _periodPaths.value = currentPaths
                }
            }
        }
    }

    fun toggleHeatmapEnabled() {
        viewModelScope.launch {
            prefManager.setHeatmapEnabled(!isHeatmapEnabled.value)
        }
    }

    fun toggleMarkerTypeEnabled(type: PeriodMarkerType) {
        viewModelScope.launch {
            val isEnabled = enabledMarkerTypes.value.contains(type)
            prefManager.setPeriodMarkerTypeEnabled(type.name, !isEnabled)
        }
    }

    fun showPeriodMap(summary: PeriodSummary) {
        _selectedPeriod.value = summary
    }

    fun dismissPeriodMap() {
        _selectedPeriod.value = null
        _periodPaths.value = emptyMap()
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
