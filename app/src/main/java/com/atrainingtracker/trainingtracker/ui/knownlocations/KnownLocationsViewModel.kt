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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified UI State for Known Start Locations management (REQ-UI-165, REQ-UI-180, REQ-UI-186).
 */
data class KnownLocationsUiState(
    val locations: List<KnownLocationItem> = emptyList(),
    val filteredLocations: List<KnownLocationItem> = emptyList(),
    val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap(),
    val startsByLocationId: Map<Long, Int> = emptyMap(),
    val sortOrder: KnownLocationSortOrder = KnownLocationSortOrder.STARTS,
    val isLocationAvailable: Boolean = false,
    val userLocation: LatLng? = null,
    val searchQuery: String = "",
    val isMetric: Boolean = true,
    val isLoading: Boolean = false,
    val selectedLocationForEdit: KnownLocationItem? = null,
    val showMapInEditDialog: Boolean = false
)

/**
 * ViewModel orchestrating Known Start Locations screen state, search filtering,
 * route cluster association, and editing operations (REQ-UI-165, REQ-UI-180, REQ-UI-186).
 */
class KnownLocationsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: KnownLocationsRepository = KnownLocationsRepository.getInstance(application),
    initialIsMetric: Boolean? = null,
    banalServiceRepository: BANALServiceRepository? = null,
    private val clusterRepository: WorkoutClusterRepository = WorkoutClusterRepository.getInstance(application),
    workoutRepository: WorkoutRepository? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    private val banalRepo: BANALServiceRepository? = banalServiceRepository ?: try {
        BANALServiceRepository.getInstance(application)
    } catch (_: Exception) {
        null
    }

    private val workoutRepo: WorkoutRepository? = workoutRepository ?: try {
        WorkoutRepository.getInstance(application)
    } catch (_: Exception) {
        null
    }

    private val isMetricSetting: Boolean = initialIsMetric ?: try {
        TrainingApplication.getUnit() == MyUnits.METRIC
    } catch (_: Exception) {
        true
    }

    private val _uiState = MutableStateFlow(
        KnownLocationsUiState(
            isMetric = isMetricSetting,
            isLoading = true
        )
    )
    val uiState: StateFlow<KnownLocationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.locationsFlow.collect { items ->
                val currentWorkouts = workoutRepo?.allWorkouts?.value ?: emptyList()
                val currentClusters = clusterRepository.allClusters.value
                val (startsMapping, mapping) = withContext(defaultDispatcher) {
                    Pair(
                        groupStartsByLocation(items, currentWorkouts),
                        groupClustersByLocation(items, currentClusters)
                    )
                }
                _uiState.update { state ->
                    val sorted = applySort(items, state.sortOrder, state.userLocation, startsMapping)
                    val filtered = applyFilter(sorted, state.searchQuery)
                    state.copy(
                        locations = sorted,
                        filteredLocations = filtered,
                        clustersByLocationId = mapping,
                        startsByLocationId = startsMapping,
                        isLoading = false,
                        selectedLocationForEdit = items.find { it.id == state.selectedLocationForEdit?.id }
                    )
                }
            }
        }

        viewModelScope.launch {
            clusterRepository.allClusters.collect { clusters ->
                _uiState.update { state ->
                    val mapping = groupClustersByLocation(state.locations, clusters)
                    state.copy(clustersByLocationId = mapping)
                }
            }
        }

        workoutRepo?.allWorkouts?.let { workoutsFlow ->
            viewModelScope.launch {
                workoutsFlow.collect { workouts ->
                    val currentLocations = repository.locationsFlow.value
                    if (currentLocations.isEmpty()) {
                        _uiState.update { it.copy(startsByLocationId = emptyMap()) }
                        return@collect
                    }
                    val startsMapping = withContext(defaultDispatcher) {
                        groupStartsByLocation(currentLocations, workouts)
                    }
                    _uiState.update { state ->
                        val sorted = if (state.sortOrder == KnownLocationSortOrder.STARTS) {
                            applySort(currentLocations, state.sortOrder, state.userLocation, startsMapping)
                        } else {
                            state.locations
                        }
                        val filtered = applyFilter(sorted, state.searchQuery)
                        state.copy(
                            startsByLocationId = startsMapping,
                            locations = sorted,
                            filteredLocations = filtered
                        )
                    }
                }
            }
        }

        banalRepo?.currentLocation?.let { locFlow ->
            viewModelScope.launch {
                locFlow.collect { loc ->
                    _uiState.update { state ->
                        val locationAvailable = loc != null
                        val sorted = if (state.sortOrder == KnownLocationSortOrder.DISTANCE_TO_USER) {
                            applySort(state.locations, state.sortOrder, loc, state.startsByLocationId)
                        } else {
                            state.locations
                        }
                        state.copy(
                            userLocation = loc,
                            isLocationAvailable = locationAvailable,
                            locations = sorted,
                            filteredLocations = applyFilter(sorted, state.searchQuery)
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.healLegacyNames()
        }
    }

    /**
     * Sets the active sort order for known locations.
     */
    fun setSortOrder(order: KnownLocationSortOrder) {
        _uiState.update { state ->
            val sorted = applySort(state.locations, order, state.userLocation, state.startsByLocationId)
            val filtered = applyFilter(sorted, state.searchQuery)
            state.copy(
                sortOrder = order,
                locations = sorted,
                filteredLocations = filtered
            )
        }
    }

    /**
     * Updates active text search query and recomputes filtered locations.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.locations, query)
            state.copy(
                searchQuery = query,
                filteredLocations = filtered
            )
        }
    }

    /**
     * Opens modal edit dialog for the specified location item.
     */
    fun openEditDialog(location: KnownLocationItem, showMap: Boolean = false) {
        _uiState.update {
            it.copy(
                selectedLocationForEdit = location,
                showMapInEditDialog = showMap
            )
        }
    }

    /**
     * Dismisses modal edit dialog.
     */
    fun dismissEditDialog() {
        _uiState.update { it.copy(selectedLocationForEdit = null) }
    }

    /**
     * Commits edited location changes to database via repository.
     * When [source] is [ElevationSource.MANUAL_USER], the record is automatically locked.
     */
    fun updateLocation(id: Long, name: String, altitude: Double, source: ElevationSource) {
        viewModelScope.launch {
            repository.updateLocation(id, name, altitude, source)
            dismissEditDialog()
        }
    }

    /**
     * Commits edited location changes including custom geofence radius to database via repository.
     * When [source] is [ElevationSource.MANUAL_USER], the record is automatically locked.
     */
    fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource) {
        viewModelScope.launch {
            repository.updateLocation(id, name, altitude, radius, source)
            dismissEditDialog()
        }
    }

    /**
     * Deletes a known location.
     */
    fun deleteLocation(id: Long) {
        viewModelScope.launch {
            repository.deleteLocation(id)
            if (_uiState.value.selectedLocationForEdit?.id == id) {
                dismissEditDialog()
            }
        }
    }

    /**
     * Queries Open-Meteo DEM elevation and commits updated altitude to repository.
     */
    suspend fun refreshDem(id: Long, latLng: LatLng): ElevationResult {
        return repository.refreshDem(id, latLng)
    }

    /**
     * Dispatches legacy placeholder name healing in background.
     */
    fun healLegacyNames() {
        viewModelScope.launch {
            repository.healLegacyNames()
        }
    }

    private fun applySort(
        items: List<KnownLocationItem>,
        sortOrder: KnownLocationSortOrder,
        userLocation: LatLng? = _uiState.value.userLocation,
        startsByLocationId: Map<Long, Int> = _uiState.value.startsByLocationId
    ): List<KnownLocationItem> {
        return when (sortOrder) {
            KnownLocationSortOrder.STARTS -> items.sortedWith(
                compareByDescending<KnownLocationItem> { startsByLocationId[it.id] ?: it.hitCount }
                    .thenBy { it.name.lowercase() }
            )
            KnownLocationSortOrder.DISTANCE_TO_USER -> {
                if (userLocation == null) {
                    items.sortedWith(
                        compareByDescending<KnownLocationItem> { startsByLocationId[it.id] ?: it.hitCount }
                            .thenBy { it.name.lowercase() }
                    )
                } else {
                    items.sortedWith(
                        compareBy<KnownLocationItem> { item ->
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                userLocation.latitude, userLocation.longitude,
                                item.latLng.latitude, item.latLng.longitude,
                                results
                            )
                            results[0]
                        }.thenBy { it.name.lowercase() }
                    )
                }
            }
            KnownLocationSortOrder.ALTITUDE -> items.sortedWith(
                compareByDescending<KnownLocationItem> { it.altitude }
                    .thenBy { it.name.lowercase() }
            )
            KnownLocationSortOrder.NAME -> items.sortedWith(
                compareBy<KnownLocationItem> { it.name.lowercase() }
                    .thenByDescending { startsByLocationId[it.id] ?: it.hitCount }
            )
        }
    }

    private data class SpatialLocationBounds(
        val item: KnownLocationItem,
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    )

    private fun groupStartsByLocation(
        locations: List<KnownLocationItem>,
        workouts: List<WorkoutData>
    ): Map<Long, Int> {
        if (locations.isEmpty() || workouts.isEmpty()) return emptyMap()
        val boundsList = locations.map { loc ->
            val latDelta = loc.radius / 111139.0
            val cosLat = Math.cos(Math.toRadians(loc.latLng.latitude))
            val lngDelta = loc.radius / (111139.0 * Math.max(0.01, Math.abs(cosLat)))
            SpatialLocationBounds(
                item = loc,
                minLat = loc.latLng.latitude - latDelta,
                maxLat = loc.latLng.latitude + latDelta,
                minLng = loc.latLng.longitude - lngDelta,
                maxLng = loc.latLng.longitude + lngDelta
            )
        }
        val result = mutableMapOf<Long, Int>()
        for (workout in workouts) {
            val start = workout.startLatLng ?: continue
            for (bounds in boundsList) {
                if (start.latitude < bounds.minLat || start.latitude > bounds.maxLat ||
                    start.longitude < bounds.minLng || start.longitude > bounds.maxLng
                ) {
                    continue
                }
                val distance = WorkoutClusterEngine.distanceBetween(bounds.item.latLng, start)
                if (distance <= bounds.item.radius) {
                    result[bounds.item.id] = (result[bounds.item.id] ?: 0) + 1
                }
            }
        }
        return result
    }

    private fun applyFilter(items: List<KnownLocationItem>, query: String): List<KnownLocationItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return items
        return items.filter { item ->
            item.name.lowercase().contains(trimmed) ||
            item.latLng.latitude.toString().contains(trimmed) ||
            item.latLng.longitude.toString().contains(trimmed)
        }
    }

    private fun groupClustersByLocation(
        locations: List<KnownLocationItem>,
        clusters: List<WorkoutCluster>
    ): Map<Long, List<WorkoutCluster>> {
        if (locations.isEmpty() || clusters.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<WorkoutCluster>>()
        for (cluster in clusters) {
            val clusterStart = LatLng(cluster.startLat, cluster.startLng)
            for (location in locations) {
                val distance = WorkoutClusterEngine.distanceBetween(location.latLng, clusterStart)
                if (distance <= location.radius) {
                    result.getOrPut(location.id) { mutableListOf() }.add(cluster)
                }
            }
        }
        return result
    }
}
