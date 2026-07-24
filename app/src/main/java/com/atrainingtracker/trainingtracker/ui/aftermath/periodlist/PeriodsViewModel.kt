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
    val selectedPeriod = _selectedPeriod.asStateFlow()

    val isHeatmapEnabled: StateFlow<Boolean> = prefManager.isHeatmapEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val enabledMarkerTypes: StateFlow<Set<PeriodMarkerType>> = prefManager.enabledPeriodMarkerTypesFlow
        .map { strings -> 
            strings.mapNotNull { 
                try { PeriodMarkerType.valueOf(it) } catch(e: Exception) { null } 
            }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = setOf(PeriodMarkerType.ALTITUDE, PeriodMarkerType.DISTANCE)
        )

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

        // Lazy-loading of full heatmap polylines
        viewModelScope.launch {
            val workoutIdList = workoutRepo.allWorkouts.value.filter { w ->
                w.startTimeS >= summary.startTimestampS && w.startTimeS <= summary.endTimestampS
            }.map { it.id }

            val richPaths = workoutIdList.associateWith { id ->
                val fullPath = workoutRepo.getWorkoutTrackPoints(id, TrackType.BEST)
                
                // PERFORMANCE: Simplify for map
                if (fullPath.size > 800) {
                    val latLngs = fullPath.map { it.latLng }
                    var tolerance = 1.0
                    var simplified = PolyUtil.simplify(latLngs, tolerance)
                    var iterations = 0
                    while (simplified.size > 1000 && iterations < 5) {
                        tolerance *= 2.0
                        simplified = PolyUtil.simplify(latLngs, tolerance)
                        iterations++
                    }
                    simplified
                } else {
                    fullPath.map { it.latLng }
                }
            }
            
            val current = _selectedPeriod.value
            if (current != null && current.startTimestampS == summary.startTimestampS && 
                current.periodType == summary.periodType) {
                _selectedPeriod.value = current.copy(workoutIdToPathMap = richPaths)
            }
        }
    }

    fun dismissPeriodMap() {
        _selectedPeriod.value = null
    }

    // Observe summarized periods and migration progress from Repository
    val groupedPeriods: StateFlow<List<List<PeriodSummary>>> = periodsRepo.groupedPeriods
    val migrationProgress: StateFlow<Float?> = periodsRepo.migrationProgress

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
