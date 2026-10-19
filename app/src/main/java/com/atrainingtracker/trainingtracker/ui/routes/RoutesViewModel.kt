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

package com.atrainingtracker.trainingtracker.ui.routes

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.routes.GpxRouteImporter
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.util.BaseMappableListViewModel
import com.atrainingtracker.trainingtracker.ui.util.MappableSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


enum class RouteSortOrder(@StringRes override val labelResId: Int) : MappableSortOrder {
    DISTANCE_TO_USER(R.string.sort_closest),
    TOTAL_ELEVATION_GAIN(R.string.sort_elevation_gain),
    ROUTE_DISTANCE(R.string.sort_length),
    NAME(R.string.sort_name)
}

data class BatchImportProgress(val current: Int, val total: Int)
data class BatchImportResult(val importedCount: Int, val failedCount: Int, val totalCount: Int)

class RoutesViewModel @JvmOverloads constructor(
    application: Application,
    private val routesRepository: RoutesRepository = RoutesRepository.getInstance(application),
    private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application),
    private val segmentsRepository: SegmentsRepository = SegmentsRepository.getInstance(application),
    private val gpxRouteImporter: GpxRouteImporter = GpxRouteImporter(application)
) : BaseMappableListViewModel<RouteWithPath, RouteSortOrder>(application, RouteSortOrder.DISTANCE_TO_USER, RouteSortOrder.DISTANCE_TO_USER) {
    private val preferenceManager = MyPreferenceManager(application)
    private val tuningDataStore = TuningPreferencesDataStore(application)

    val tuningConfig: StateFlow<TuningConfig> = tuningDataStore.tuningConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TuningConfig()
        )

    private val _isSyncingStrava = MutableStateFlow(false)
    val isSyncingStrava = _isSyncingStrava.asStateFlow()

    // Sync status: null = idle, true = success, false = failure
    private val _syncStravaStatus = MutableStateFlow<Boolean?>(null)
    val syncStravaStatus = _syncStravaStatus.asStateFlow()

    fun resetSyncStravaStatus() {
        _syncStravaStatus.value = null
    }

    init {
        if (TrainingApplication.getStravaAccessToken() != null) {
            viewModelScope.launch {
                if (routesRepository.allRoutes.value.none { it.summary.source == RouteSource.STRAVA }) {
                    syncStravaRoutes()
                }
            }
        }
    }

    override val isLocationAvailable: StateFlow<Boolean> = banalServiceRepository.currentLocation
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val segments = segmentsRepository.allSegmentsWithPath
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Currently active navigated route ID, if any workout route navigation is active (REQ-UI-347).
     */
    val activeNavigatedRouteId: StateFlow<Long?> = routesRepository.activeNavigatedRouteId

    val filterCriteria: StateFlow<RouteFilterCriteria> = preferenceManager.routeFilterCriteriaFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RouteFilterCriteria()
        )

    // The list of routes to display; properly filtered and sorted
    val routes: StateFlow<List<RouteWithPath>> = combine(
        routesRepository.allRoutes,
        _sortOrder,
        banalServiceRepository.currentLocation,
        filterCriteria
    ) { allRoutes, order, location, criteria ->
        val filtered = if (criteria.isEmpty) allRoutes else allRoutes.filter { criteria.matches(it) }

        when (order) {
            RouteSortOrder.NAME ->
                filtered.sortedBy { it.summary.name.lowercase() }

            RouteSortOrder.TOTAL_ELEVATION_GAIN ->
                filtered.sortedWith(
                    compareByDescending<RouteWithPath> { it.summary.elevationGain }
                        .thenBy { it.summary.name.lowercase() }
                )

            RouteSortOrder.ROUTE_DISTANCE ->
                filtered.sortedWith(
                    compareByDescending<RouteWithPath> { it.summary.distance }
                        .thenBy { it.summary.name.lowercase() }
                )

            RouteSortOrder.DISTANCE_TO_USER -> {
                if (location == null) {
                    filtered.sortedBy { it.summary.name.lowercase() }
                } else {
                    filtered.sortedBy { route ->
                        val startPoint = route.path.firstOrNull()
                        if (startPoint != null) {
                            calculateDistance(
                                location.latitude, location.longitude,
                                startPoint.latLng.latitude, startPoint.latLng.longitude
                            )
                        } else {
                            Float.MAX_VALUE
                        }
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilterCriteria(criteria: RouteFilterCriteria) {
        viewModelScope.launch {
            preferenceManager.setRouteFilterCriteria(criteria)
        }
    }

    fun clearFilterCriteria() {
        preferenceManager.clearRouteFilterCriteria()
    }

    fun updateFilterCriteria(transform: (RouteFilterCriteria) -> RouteFilterCriteria) {
        val updated = transform(filterCriteria.value)
        setFilterCriteria(updated)
    }




    fun toggleRouteSelection(routeId: Long, isSelected: Boolean) {
        viewModelScope.launch {
            routesRepository.toggleRouteSelection(routeId, isSelected)
        }
    }

    // State for navigation/editing
    var editingRoute by mutableStateOf<RouteSummary?>(null)
        private set

    fun startEditing(route: RouteSummary) {
        editingRoute = route
    }

    fun stopEditing() {
        editingRoute = null
    }

    fun updateRoute(summary: RouteSummary) {
        viewModelScope.launch {
            routesRepository.updateRouteSummary(summary)
            stopEditing()
            refresh() // Reload the list
        }
    }

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch {
            routesRepository.deleteRoute(routeId)
        }
    }

    fun duplicateRouteAsLocal(routeId: Long, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val newId = routesRepository.duplicateRouteAsLocal(routeId)
            onComplete?.invoke(newId != -1L)
        }
    }

    fun refresh() {
        routesRepository.refreshRoutes()
    }

    fun syncStravaRoutes() {
        viewModelScope.launch {
            _isSyncingStrava.value = true
            try {
                routesRepository.syncRoutesFromStrava()
                _syncStravaStatus.value = true
            } catch (e: Exception) {
                _syncStravaStatus.value = false
            } finally {
                _isSyncingStrava.value = false
            }
        }
    }

    private val _batchImportProgress = MutableStateFlow<BatchImportProgress?>(null)
    val batchImportProgress: StateFlow<BatchImportProgress?> = _batchImportProgress.asStateFlow()

    private val _batchImportResult = MutableStateFlow<BatchImportResult?>(null)
    val batchImportResult: StateFlow<BatchImportResult?> = _batchImportResult.asStateFlow()

    fun clearBatchImportResult() {
        _batchImportResult.value = null
    }

    /**
     * Imports multiple route files in batch on Dispatchers.IO (REQ-MAP-053.3).
     * Reports live progress via batchImportProgress and final tally via batchImportResult.
     */
    fun importRoutesBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            val total = uris.size
            for ((index, uri) in uris.withIndex()) {
                _batchImportProgress.value = BatchImportProgress(current = index + 1, total = total)
                try {
                    val result = gpxRouteImporter.importRouteFromGpx(uri)
                    if (result.isSuccess) {
                        val data = result.getOrNull()
                        if (data != null) {
                            routesRepository.insertRoute(data.summary, data.pathPoints, data.waypoints)
                            imported++
                        } else {
                            failed++
                        }
                    } else {
                        Log.w(TAG, "Failed to parse route from $uri: ${result.exceptionOrNull()?.message}")
                        failed++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exception importing route from $uri: ${e.message}", e)
                    failed++
                }
            }
            _batchImportProgress.value = null
            _batchImportResult.value = BatchImportResult(
                importedCount = imported,
                failedCount = failed,
                totalCount = total
            )
        }
    }

    companion object {
        private const val TAG = "RoutesViewModel"
    }
}
