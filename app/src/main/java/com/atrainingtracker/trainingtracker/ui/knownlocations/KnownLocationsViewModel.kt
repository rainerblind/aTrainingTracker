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
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.KnownLocationsGeometryMath
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.BaseMappableListViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Workout starts partitioned by athletic sport type (REQ-UI-470.6).
 */
data class SportStartsCount(
    val bike: Int = 0,
    val run: Int = 0,
    val other: Int = 0
) {
    val total: Int get() = bike + run + other
}

/**
 * Unified UI State for Known Start Locations management (REQ-UI-165, REQ-UI-180, REQ-UI-186).
 */
data class KnownLocationsUiState(
    val locations: List<KnownLocationItem> = emptyList(),
    val filteredLocations: List<KnownLocationItem> = emptyList(),
    val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap(),
    val startsByLocationId: Map<Long, Int> = emptyMap(),
    val sportStartsByLocationId: Map<Long, SportStartsCount> = emptyMap(),
    val sortOrder: KnownLocationSortOrder = KnownLocationSortOrder.STARTS,
    val isLocationAvailable: Boolean = false,
    val userLocation: LatLng? = null,
    val searchQuery: String = "",
    val filterCriteria: KnownLocationsFilterCriteria = KnownLocationsFilterCriteria(),
    val isMetric: Boolean = true,
    val isLoading: Boolean = false,
    val selectedLocationForEdit: KnownLocationItem? = null,
    val showMapInEditDialog: Boolean = false
) {
    val isFilterActive: Boolean get() = filterCriteria.isNotEmpty
    val activeFilterCount: Int get() = filterCriteria.activeFilterCount
}

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
) : BaseMappableListViewModel<KnownLocationItem, KnownLocationSortOrder>(
    application = application,
    initialSortOrder = KnownLocationSortOrder.STARTS,
    distanceSortOrder = KnownLocationSortOrder.DISTANCE_TO_USER
) {

    private data class SpatialGroupingResult(
        val locations: List<KnownLocationItem> = emptyList(),
        val clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap(),
        val startsByLocationId: Map<Long, Int> = emptyMap(),
        val sportStartsByLocationId: Map<Long, SportStartsCount> = emptyMap()
    )

    private data class EditDialogState(
        val selected: KnownLocationItem? = null,
        val showMap: Boolean = false
    )

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

    private val _filterCriteria = MutableStateFlow(KnownLocationsFilterCriteria())
    private val _editDialogState = MutableStateFlow(EditDialogState())

    private val locationFlow: Flow<LatLng?> = banalRepo?.currentLocation ?: flowOf(null)

    override val isLocationAvailable: StateFlow<Boolean> = locationFlow
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = banalRepo?.currentLocation?.value != null
        )

    private val _uiState = MutableStateFlow(
        KnownLocationsUiState(
            isMetric = isMetricSetting,
            isLoading = true
        )
    )
    val uiState: StateFlow<KnownLocationsUiState> = _uiState.asStateFlow()

    init {
        // REQ-STB-020.4: Only finished workouts participate in O(locations * N) start counting.
        val finishedWorkoutsFlow: Flow<List<WorkoutData>> = workoutRepo?.allWorkouts
            ?.map { workouts -> workouts.filter { it.finished } }
            ?.distinctUntilChangedBy { finishedWorkouts ->
                finishedWorkouts.map { Triple(it.id, it.startLatLng, it.bSportType) }
            }
            ?: flowOf(emptyList())

        // Declarative spatial grouping flow; skips heavy O(locations * N) work on GPS/sort/filter/dialog updates.
        val spatialGroupingFlow: Flow<SpatialGroupingResult> = combine(
            repository.locationsFlow,
            clusterRepository.allClusters,
            finishedWorkoutsFlow
        ) { locations, clusters, finishedWorkouts ->
            Triple(locations, clusters, finishedWorkouts)
        }.map { (locations, clusters, finishedWorkouts) ->
            if (locations.isEmpty()) {
                SpatialGroupingResult(locations = locations)
            } else {
                val (sportStartsMapping, clustersMapping) = withContext(defaultDispatcher) {
                    Pair(
                        groupSportStartsByLocation(locations, finishedWorkouts),
                        groupClustersByLocation(locations, clusters)
                    )
                }
                SpatialGroupingResult(
                    locations = locations,
                    clustersByLocationId = clustersMapping,
                    startsByLocationId = sportStartsMapping.mapValues { it.value.total },
                    sportStartsByLocationId = sportStartsMapping
                )
            }
        }

        viewModelScope.launch {
            combine(
                spatialGroupingFlow,
                _sortOrder,
                _filterCriteria,
                locationFlow,
                _editDialogState
            ) { spatial, sortOrder, filterCriteria, userLoc, dialogState ->
                val sorted = applySort(spatial.locations, sortOrder, userLoc, spatial.startsByLocationId)
                val filtered = applyFilter(sorted, filterCriteria, spatial.startsByLocationId)
                val resolvedEditSelection = dialogState.selected?.let { sel ->
                    if (sel.id == 0L) sel else spatial.locations.find { it.id == sel.id } ?: sel
                }
                KnownLocationsUiState(
                    locations = sorted,
                    filteredLocations = filtered,
                    clustersByLocationId = spatial.clustersByLocationId,
                    startsByLocationId = spatial.startsByLocationId,
                    sportStartsByLocationId = spatial.sportStartsByLocationId,
                    sortOrder = sortOrder,
                    isLocationAvailable = userLoc != null,
                    userLocation = userLoc,
                    searchQuery = filterCriteria.query,
                    filterCriteria = filterCriteria,
                    isMetric = isMetricSetting,
                    isLoading = false,
                    selectedLocationForEdit = resolvedEditSelection,
                    showMapInEditDialog = dialogState.showMap
                )
            }.collect { nextState ->
                _uiState.value = nextState
            }
        }

        viewModelScope.launch {
            repository.reconcileHitCounts()
            repository.healLegacyNames()
            workoutRepo?.loadAllWorkouts()
        }
    }

    /**
     * Sets the active sort order for known locations.
     */
    override fun setSortOrder(order: KnownLocationSortOrder) {
        super.setSortOrder(order)
        _uiState.update { state ->
            val sorted = applySort(state.locations, order, state.userLocation, state.startsByLocationId)
            val filtered = applyFilter(sorted, state.filterCriteria, state.startsByLocationId)
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
        val updatedCriteria = _filterCriteria.value.copy(query = query)
        _filterCriteria.value = updatedCriteria
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(query = query)
            val filtered = applyFilter(state.locations, newCriteria, state.startsByLocationId)
            state.copy(
                searchQuery = query,
                filterCriteria = newCriteria,
                filteredLocations = filtered
            )
        }
    }

    /**
     * Applies new multi-dimensional filter criteria (REQ-UI-466).
     */
    fun setFilterCriteria(criteria: KnownLocationsFilterCriteria) {
        _filterCriteria.value = criteria
        _uiState.update { state ->
            val filtered = applyFilter(state.locations, criteria, state.startsByLocationId)
            state.copy(
                searchQuery = criteria.query,
                filterCriteria = criteria,
                filteredLocations = filtered
            )
        }
    }

    /**
     * Updates minimum starts threshold without affecting active query (REQ-UI-466.1, REQ-UI-466.3).
     */
    fun setMinStarts(minStarts: Int?) {
        val updatedCriteria = _filterCriteria.value.copy(minStarts = minStarts)
        _filterCriteria.value = updatedCriteria
        _uiState.update { state ->
            val newCriteria = state.filterCriteria.copy(minStarts = minStarts)
            val filtered = applyFilter(state.locations, newCriteria, state.startsByLocationId)
            state.copy(
                filterCriteria = newCriteria,
                filteredLocations = filtered
            )
        }
    }

    /**
     * Resets all filter criteria to empty (minStarts = null, query = "").
     */
    fun clearFilters() {
        setFilterCriteria(KnownLocationsFilterCriteria(query = "", minStarts = null))
    }

    /**
     * Opens modal edit dialog for the specified location item.
     */
    fun openEditDialog(location: KnownLocationItem, showMap: Boolean = false) {
        _editDialogState.value = EditDialogState(selected = location, showMap = showMap)
        _uiState.update {
            it.copy(
                selectedLocationForEdit = location,
                showMapInEditDialog = showMap
            )
        }
    }

    /**
     * Opens modal edit dialog with a new location template for athlete creation (REQ-UI-470.3).
     */
    fun openCreateDialog() {
        val userLoc = _uiState.value.userLocation
        val defaultLatLng = if (userLoc != null) {
            LatLng(userLoc.latitude, userLoc.longitude)
        } else {
            _uiState.value.locations.firstOrNull()?.latLng ?: LatLng(0.0, 0.0)
        }
        val defaultAltitude = _uiState.value.locations.firstOrNull()?.altitude ?: 0.0

        val newTemplate = KnownLocationItem(
            id = 0L,
            name = "",
            altitude = defaultAltitude,
            radius = KnownLocationsDatabaseManager.DEFAULT_RADIUS,
            latLng = defaultLatLng,
            hitCount = 0,
            isLocked = true,
            source = ElevationSource.MANUAL_USER,
            isHome = false
        )
        _editDialogState.value = EditDialogState(selected = newTemplate, showMap = false)
        _uiState.update {
            it.copy(
                selectedLocationForEdit = newTemplate,
                showMapInEditDialog = false
            )
        }
    }

    /**
     * Dismisses modal edit dialog.
     */
    fun dismissEditDialog() {
        _editDialogState.value = EditDialogState(selected = null, showMap = false)
        _uiState.update { it.copy(selectedLocationForEdit = null) }
    }

    /**
     * Creates a new known location manually via repository (REQ-UI-470.3).
     */
    fun createLocation(
        name: String,
        altitude: Double,
        radius: Int,
        latLng: LatLng,
        source: ElevationSource = ElevationSource.MANUAL_USER,
        isHome: Boolean = false
    ) {
        viewModelScope.launch {
            repository.addLocation(
                name = name,
                altitude = altitude,
                radius = radius,
                latLng = latLng,
                source = source,
                isHome = isHome
            )
            dismissEditDialog()
        }
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
     * Commits edited location changes including custom geofence radius and home base status (REQ-MAP-034).
     */
    fun updateLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource, isHome: Boolean) {
        if (id == 0L) {
            val template = _uiState.value.selectedLocationForEdit
            val latLng = template?.latLng ?: LatLng(0.0, 0.0)
            createLocation(name, altitude, radius, latLng, source, isHome)
            return
        }
        viewModelScope.launch {
            repository.updateLocation(id, name, altitude, radius, source)
            if (isHome) {
                repository.setHomeLocation(id)
            } else {
                val current = repository.locationsFlow.value.firstOrNull { it.id == id }
                if (current?.isHome == true) {
                    repository.clearHomeLocation()
                }
            }
            dismissEditDialog()
        }
    }

    /**
     * Atomically sets the specified location as the designated home base (REQ-MAP-034).
     */
    fun setHomeLocation(id: Long) {
        viewModelScope.launch {
            repository.setHomeLocation(id)
        }
    }

    /**
     * Clears the designated home base across all locations (REQ-MAP-034).
     */
    fun clearHomeLocation() {
        viewModelScope.launch {
            repository.clearHomeLocation()
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
     * ATT-5889 / REQ-SET-095.2: Retrieves candidate locations for merging with [source],
     * filtering to those within 2,000 meters geodetic distance and sorting ascending by raw
     * numeric distance (ATT-3324).
     */
    fun getCandidateLocationsForMerge(source: KnownLocationItem): List<Pair<KnownLocationItem, Double>> {
        val all = repository.locationsFlow.value
        return all.filter { it.id != source.id }
            .map { candidate ->
                val dist = KnownLocationsGeometryMath.calculateDistanceMeters(source.latLng, candidate.latLng)
                Pair(candidate, dist)
            }
            .filter { it.second <= 2000.0 }
            .sortedBy { it.second }
    }

    /**
     * ATT-5889 / REQ-SET-095.5: Dispatches merging of [source] location into [target] location.
     */
    fun mergeLocations(source: KnownLocationItem, target: KnownLocationItem) {
        viewModelScope.launch {
            repository.mergeLocations(source, target)
            clusterRepository.refreshClusters()
            if (_uiState.value.selectedLocationForEdit?.id == source.id) {
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
                            calculateDistance(
                                userLocation.latitude, userLocation.longitude,
                                item.latLng.latitude, item.latLng.longitude
                            )
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

    @VisibleForTesting
    internal fun groupSportStartsByLocation(
        locations: List<KnownLocationItem>,
        workouts: List<WorkoutData>
    ): Map<Long, SportStartsCount> {
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
        val result = mutableMapOf<Long, SportStartsCount>()
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
                    val current = result[bounds.item.id] ?: SportStartsCount()
                    val updated = when (workout.bSportType) {
                        BSportType.BIKE -> current.copy(bike = current.bike + 1)
                        BSportType.RUN -> current.copy(run = current.run + 1)
                        else -> current.copy(other = current.other + 1)
                    }
                    result[bounds.item.id] = updated
                }
            }
        }
        return result
    }

    @VisibleForTesting
    internal fun groupStartsByLocation(
        locations: List<KnownLocationItem>,
        workouts: List<WorkoutData>
    ): Map<Long, Int> {
        return groupSportStartsByLocation(locations, workouts).mapValues { it.value.total }
    }

    private fun applyFilter(
        items: List<KnownLocationItem>,
        criteria: KnownLocationsFilterCriteria,
        startsMapping: Map<Long, Int>
    ): List<KnownLocationItem> {
        return items.filter { item ->
            val effectiveStarts = startsMapping[item.id] ?: item.hitCount
            criteria.matches(item, effectiveStarts)
        }
    }

    @VisibleForTesting
    internal fun applyFilter(items: List<KnownLocationItem>, query: String): List<KnownLocationItem> {
        val criteria = KnownLocationsFilterCriteria(query = query, minStarts = null)
        return applyFilter(items, criteria, emptyMap())
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
