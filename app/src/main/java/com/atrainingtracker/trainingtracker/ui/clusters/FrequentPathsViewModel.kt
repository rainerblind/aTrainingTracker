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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.RouteCluster
import com.atrainingtracker.trainingtracker.database.RouteClusterEngine
import com.atrainingtracker.trainingtracker.database.RouteClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrequentPathsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteClusterRepository.getInstance(application)

    val allClusters: StateFlow<List<RouteCluster>> = repository.allClusters

    private val _clusterWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val clusterWorkouts: StateFlow<List<WorkoutData>> = _clusterWorkouts.asStateFlow()

    private val _selectedCluster = MutableStateFlow<RouteCluster?>(null)
    val selectedCluster: StateFlow<RouteCluster?> = _selectedCluster.asStateFlow()

    private val _isRecalculating = MutableStateFlow(false)
    val isRecalculating: StateFlow<Boolean> = _isRecalculating.asStateFlow()

    private val _recalculationFinished = MutableSharedFlow<Unit>()
    val recalculationFinished: SharedFlow<Unit> = _recalculationFinished.asSharedFlow()

    // Tuning Parameters State
    var endpointTolerance by mutableStateOf(TrainingApplication.getClusterTolEndpoints())
    var apexTolerance by mutableStateOf(TrainingApplication.getClusterTolApex())
    var distanceTolerance by mutableStateOf(TrainingApplication.getClusterTolDistance())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshClusters()
        }
    }

    fun recalculateClusters() {
        viewModelScope.launch {
            _isRecalculating.value = true
            
            // Save current parameters to SP first
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplication())
            prefs.edit()
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS, endpointTolerance)
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_APEX, apexTolerance)
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_DISTANCE, distanceTolerance)
                .apply()

            withContext(Dispatchers.IO) {
                com.atrainingtracker.trainingtracker.database.RouteClusterEngine.Companion.getInstance(getApplication())
                    .recalculateHistory(getApplication())
                repository.refreshClusters()
            }
            _isRecalculating.value = false
            _recalculationFinished.emit(Unit)
        }
    }

    fun selectCluster(cluster: RouteCluster?) {
        _selectedCluster.value = cluster
        if (cluster != null) {
            viewModelScope.launch {
                _clusterWorkouts.value = repository.getWorkoutsForCluster(cluster.id)
            }
        } else {
            _clusterWorkouts.value = emptyList()
        }
    }

    fun renameCluster(cluster: RouteCluster, newName: String) {
        viewModelScope.launch {
            val updated = cluster.copy(name = newName)
            repository.updateCluster(updated)
            // Update selected cluster if it's the one renamed
            if (_selectedCluster.value?.id == cluster.id) {
                _selectedCluster.value = updated
            }
        }
    }

    fun getCandidateClustersForWorkout(workout: WorkoutData): List<Pair<RouteCluster, Double>> {
        val start = workout.startLatLng ?: return emptyList()
        val end = workout.endLatLng ?: return emptyList()
        val apex = workout.maxDisplacementLatLng ?: return emptyList()
        
        return RouteClusterEngine.getInstance(getApplication())
            .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName)
    }

    fun moveWorkout(workout: WorkoutData, newClusterId: Long) {
        val currentClusterId = _selectedCluster.value?.id ?: return
        if (currentClusterId == newClusterId) return

        viewModelScope.launch(Dispatchers.IO) {
            RouteClusterEngine.getInstance(getApplication())
                .moveWorkoutToCluster(getApplication(), workout.id, currentClusterId, newClusterId)
            
            // Refresh state
            repository.refreshClusters()
            _selectedCluster.value?.let { current ->
                _clusterWorkouts.value = repository.getWorkoutsForCluster(current.id)
            }
        }
    }
}
