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
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
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
    private val routesRepository = com.atrainingtracker.trainingtracker.repositories.RoutesRepository.getInstance(application)
    private val banalRepository = BANALServiceRepository.getInstance(application)
    private val discoveryManager = com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager.getInstance(application)

    val allClusters: StateFlow<List<RouteCluster>> = repository.allClusters
    val currentLocation: StateFlow<LatLng?> = banalRepository.currentLocation

    private val _clusterWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val clusterWorkouts: StateFlow<List<WorkoutData>> = _clusterWorkouts.asStateFlow()

    private val _linkedRoute = MutableStateFlow<com.atrainingtracker.trainingtracker.database.RouteWithPath?>(null)
    val linkedRoute: StateFlow<com.atrainingtracker.trainingtracker.database.RouteWithPath?> = _linkedRoute.asStateFlow()

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
                _linkedRoute.value = routesRepository.getRouteByClusterId(cluster.id)
            }
        } else {
            _clusterWorkouts.value = emptyList()
            _linkedRoute.value = null
            clearPeekSelection()
        }
    }

    // --- PEEK / BOTTOM SHEET STATE (SCRUM-196) ---
    private val _peekedWorkoutDataWithTrack = MutableStateFlow<com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack?>(null)
    val peekedWorkoutDataWithTrack: StateFlow<com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack?> = _peekedWorkoutDataWithTrack.asStateFlow()

    fun selectWorkoutForPeek(id: Long) {
        viewModelScope.launch {
            val workout = _clusterWorkouts.value.find { it.id == id }
            if (workout != null) {
                val trackPoints = repository.getWorkoutTrackPoints(id, com.atrainingtracker.trainingtracker.ui.map.TrackType.BEST)
                val markers = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository.getInstance(getApplication()).getWorkoutMarkers(workout)
                _peekedWorkoutDataWithTrack.value = com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack(workout, trackPoints, markers)
            }
        }
    }

    fun clearPeekSelection() {
        _peekedWorkoutDataWithTrack.value = null
    }

    fun updateClusterIdentity(cluster: RouteCluster, newName: String, newSportId: Long) {
        viewModelScope.launch {
            val updated = cluster.copy(name = newName, probableSportId = newSportId)
            repository.updateCluster(updated)
            // Update selected cluster if it's the one modified
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

    fun addManualCluster(
        name: String,
        sportId: Long,
        start: com.google.android.gms.maps.model.LatLng,
        end: com.google.android.gms.maps.model.LatLng,
        apex: com.google.android.gms.maps.model.LatLng,
        distance: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            RouteClusterEngine.getInstance(getApplication())
                .manuallyCreateCluster(name, sportId, start, end, apex, distance)
            repository.refreshClusters()
        }
    }

    fun updateClusterFingerprint(
        cluster: RouteCluster,
        start: LatLng,
        end: LatLng,
        apex: LatLng
    ) {
        viewModelScope.launch {
            val updated = cluster.copy(
                startLat = start.latitude,
                startLng = start.longitude,
                endLat = end.latitude,
                endLng = end.longitude,
                maxDispLat = apex.latitude,
                maxDispLng = apex.longitude
            )
            repository.updateCluster(updated)
            if (_selectedCluster.value?.id == cluster.id) {
                _selectedCluster.value = updated
            }
        }
    }

    fun getSportName(sportId: Long): String = repository.getSportName(sportId)
    fun getBSportType(sportId: Long): com.atrainingtracker.banalservice.BSportType = repository.getBSportType(sportId)

    fun getLinkedEquipment(sportId: Long): List<String> {
        val sportName = getSportName(sportId)
        return discoveryManager.getEquipmentNamesForSport(sportName).toList()
    }
}
