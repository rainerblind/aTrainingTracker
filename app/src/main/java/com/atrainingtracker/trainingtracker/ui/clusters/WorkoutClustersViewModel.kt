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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataWithTrack
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates the UI state for the cluster detail map (ATT-359).
 * Pre-calculating this in the background prevents main-thread jank.
 */
data class ClusterMapState(
    val tracks: List<MapTrack> = emptyList(),
    val heatmapPaths: List<List<LatLng>> = emptyList(),
    val memberMarkers: List<LocationMarker> = emptyList(),
    val isLoading: Boolean = false
)

class WorkoutClustersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutClusterRepository.getInstance(application)
    private val routesRepository = RoutesRepository.getInstance(application)
    private val banalRepository = BANALServiceRepository.getInstance(application)
    private val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(application)

    val allClusters: StateFlow<List<WorkoutCluster>> = repository.allClusters
    val currentLocation: StateFlow<LatLng?> = banalRepository.currentLocation

    private val _clusterWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val clusterWorkouts: StateFlow<List<WorkoutData>> = _clusterWorkouts.asStateFlow()

    private val _unclusteredWorkouts = MutableStateFlow<List<WorkoutData>>(emptyList())
    val unclusteredWorkouts: StateFlow<List<WorkoutData>> = _unclusteredWorkouts.asStateFlow()

    private val _linkedRoute = MutableStateFlow<RouteWithPath?>(null)
    val linkedRoute: StateFlow<RouteWithPath?> = _linkedRoute.asStateFlow()

    private val _mapState = MutableStateFlow(ClusterMapState())
    val mapState: StateFlow<ClusterMapState> = _mapState.asStateFlow()

    private val _selectedCluster = MutableStateFlow<WorkoutCluster?>(null)
    val selectedCluster: StateFlow<WorkoutCluster?> = _selectedCluster.asStateFlow()

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
            _unclusteredWorkouts.value = repository.getUnclusteredWorkouts()
        }
    }

    fun recalculateClusters() {
        viewModelScope.launch {
            _isRecalculating.value = true
            
            // Save current parameters to SP first
            val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())
            prefs.edit()
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS, endpointTolerance)
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_APEX, apexTolerance)
                .putFloat(TrainingApplication.SP_CLUSTER_TOL_DISTANCE, distanceTolerance)
                .apply()

            withContext(Dispatchers.IO) {
                WorkoutClusterEngine.getInstance(getApplication())
                    .recalculateHistory(getApplication())
                repository.refreshClusters()
            }
            _isRecalculating.value = false
            _recalculationFinished.emit(Unit)
        }
    }

    fun selectCluster(cluster: WorkoutCluster?) {
        _selectedCluster.value = cluster
        if (cluster != null) {
            viewModelScope.launch {
                _mapState.update { it.copy(isLoading = true) }
                val workouts = repository.getWorkoutsForCluster(cluster.id)
                _clusterWorkouts.value = workouts
                _linkedRoute.value = routesRepository.getRouteByClusterId(cluster.id)
                
                // --- ATT-359: Background Map Processing ---
                withContext(Dispatchers.Default) {
                    val tracks = workouts.map { it.toMapTrack().copy(isVisible = true) }
                    val heatmapPaths = workouts.mapNotNull { 
                        if (it.mapPolyline.isNotEmpty()) PolyUtil.decode(it.mapPolyline) else null 
                    }
                    
                    // Pre-calculate markers to avoid UI jank (SCRUM-199)
                    val memberAlpha = 0.3f
                    val markers = workouts.flatMap { w ->
                        val list = mutableListOf<LocationMarker>()
                        val onMarkerClick: () -> Boolean = {
                            selectWorkoutForPeek(w.id)
                            true
                        }

                        w.startLatLng?.let { list.add(LocationMarker(it, R.drawable.control_start, alpha = memberAlpha, onClick = onMarkerClick)) }
                        w.endLatLng?.let { list.add(LocationMarker(it, R.drawable.control_stop, alpha = memberAlpha, onClick = onMarkerClick)) }
                        w.maxDisplacementLatLng?.let { list.add(LocationMarker(it, R.drawable.ic_distance, alpha = memberAlpha, onClick = onMarkerClick)) }
                        list
                    }

                    _mapState.value = ClusterMapState(
                        tracks = tracks,
                        heatmapPaths = heatmapPaths,
                        memberMarkers = markers,
                        isLoading = false
                    )
                }
            }
        } else {
            _clusterWorkouts.value = emptyList()
            _linkedRoute.value = null
            _mapState.value = ClusterMapState()
            clearPeekSelection()
        }
    }

    // --- PEEK / BOTTOM SHEET STATE (SCRUM-196) ---
    private val _peekedWorkoutDataWithTrack = MutableStateFlow<WorkoutDataWithTrack?>(null)
    val peekedWorkoutDataWithTrack: StateFlow<WorkoutDataWithTrack?> = _peekedWorkoutDataWithTrack.asStateFlow()

    fun selectWorkoutForPeek(id: Long) {
        viewModelScope.launch {
            val workout = _clusterWorkouts.value.find { it.id == id } 
                ?: _unclusteredWorkouts.value.find { it.id == id }

            if (workout != null) {
                val trackPoints = repository.getWorkoutTrackPoints(id, TrackType.BEST)
                val markers = WorkoutRepository.getInstance(getApplication()).getWorkoutMarkers(workout)
                _peekedWorkoutDataWithTrack.value = WorkoutDataWithTrack(workout, trackPoints, markers)
            }
        }
    }

    fun clearPeekSelection() {
        _peekedWorkoutDataWithTrack.value = null
    }

    fun updateClusterIdentity(cluster: WorkoutCluster, newName: String, newSportId: Long) {
        viewModelScope.launch {
            val updated = cluster.copy(name = newName, probableSportId = newSportId)
            repository.updateCluster(updated)
            // Update selected cluster if it's the one modified
            if (_selectedCluster.value?.id == cluster.id) {
                _selectedCluster.value = updated
            }
        }
    }

    fun deleteCluster(cluster: WorkoutCluster) {
        viewModelScope.launch {
            repository.deleteCluster(cluster.id)
            _selectedCluster.value = null
        }
    }

    fun getCandidateClustersForWorkout(workout: WorkoutData): List<Pair<WorkoutCluster, Double>> {
        val start = workout.startLatLng ?: return emptyList()
        val end = workout.endLatLng ?: return emptyList()
        val apex = workout.maxDisplacementLatLng ?: return emptyList()
        
        return WorkoutClusterEngine.getInstance(getApplication())
            .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName)
    }

    fun moveWorkout(workout: WorkoutData, newClusterId: Long) {
        val currentClusterId = workout.clusterId
        if (currentClusterId == newClusterId) return

        viewModelScope.launch(Dispatchers.IO) {
            WorkoutClusterEngine.getInstance(getApplication())
                .moveWorkoutToCluster(getApplication(), workout.id, currentClusterId, newClusterId)
            
            // Refresh state
            repository.refreshClusters()

            // Update both UI lists immediately
            _selectedCluster.value?.let { current ->
                _clusterWorkouts.value = repository.getWorkoutsForCluster(current.id)
            }
            _unclusteredWorkouts.value = repository.getUnclusteredWorkouts()
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
            WorkoutClusterEngine.getInstance(getApplication())
                .manuallyCreateCluster(name, sportId, start, end, apex, distance)
            repository.refreshClusters()
        }
    }

    fun updateClusterFingerprint(
        cluster: WorkoutCluster,
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
    fun getBSportType(sportId: Long): BSportType = repository.getBSportType(sportId)

    fun getLinkedEquipment(sportId: Long): List<String> {
        val sportName = getSportName(sportId)
        return discoveryManager.getEquipmentNamesForSport(sportName).toList()
    }
}
