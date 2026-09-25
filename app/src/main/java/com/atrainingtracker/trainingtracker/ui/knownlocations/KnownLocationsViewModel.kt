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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Active tab perspective on the Known Start Locations screen.
 */
enum class KnownLocationsTab {
    LIST,
    MAP
}

/**
 * Unified UI State for Known Start Locations management.
 */
data class KnownLocationsUiState(
    val locations: List<KnownLocationItem> = emptyList(),
    val filteredLocations: List<KnownLocationItem> = emptyList(),
    val visibleMapLocations: List<KnownLocationItem> = emptyList(),
    val selectedTab: KnownLocationsTab = KnownLocationsTab.LIST,
    val sortOrder: KnownLocationSortOrder = KnownLocationSortOrder.STARTS,
    val isLocationAvailable: Boolean = false,
    val userLocation: LatLng? = null,
    val searchQuery: String = "",
    val isMetric: Boolean = true,
    val isLoading: Boolean = false,
    val selectedLocationForEdit: KnownLocationItem? = null,
    val selectedLocationForMapPeek: KnownLocationItem? = null,
    val showMapInEditDialog: Boolean = false
)

/**
 * ViewModel orchestrating Known Start Locations screen state, search filtering,
 * viewport-based map culling, and editing operations.
 *
 * Traceability: REQ-UI-165, REQ-DAT-007, REQ-DAT-014, TST-UI-117.
 */
class KnownLocationsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: KnownLocationsRepository = KnownLocationsRepository.getInstance(application),
    initialIsMetric: Boolean? = null,
    banalServiceRepository: BANALServiceRepository? = null
) : AndroidViewModel(application) {

    private val banalRepo: BANALServiceRepository? = banalServiceRepository ?: try {
        BANALServiceRepository.getInstance(application)
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

    private var currentViewportBounds: LatLngBounds? = null

    init {
        viewModelScope.launch {
            repository.locationsFlow.collect { items ->
                _uiState.update { state ->
                    val sorted = applySort(items, state.sortOrder, state.userLocation)
                    val filtered = applyFilter(sorted, state.searchQuery)
                    val visibleMap = applyViewportCulling(filtered, currentViewportBounds)
                    state.copy(
                        locations = sorted,
                        filteredLocations = filtered,
                        visibleMapLocations = visibleMap,
                        isLoading = false,
                        // Update edit/peek references if data updated
                        selectedLocationForEdit = items.find { it.id == state.selectedLocationForEdit?.id },
                        selectedLocationForMapPeek = items.find { it.id == state.selectedLocationForMapPeek?.id }
                    )
                }
            }
        }

        banalRepo?.currentLocation?.let { locFlow ->
            viewModelScope.launch {
                locFlow.collect { loc ->
                    _uiState.update { state ->
                        val locationAvailable = loc != null
                        val sorted = if (state.sortOrder == KnownLocationSortOrder.DISTANCE_TO_USER) {
                            applySort(state.locations, state.sortOrder, loc)
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
     * Switches between List and Map tabs.
     */
    fun selectTab(tab: KnownLocationsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Sets the active sort order for known locations.
     */
    fun setSortOrder(order: KnownLocationSortOrder) {
        _uiState.update { state ->
            val sorted = applySort(state.locations, order, state.userLocation)
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
            val visibleMap = applyViewportCulling(filtered, currentViewportBounds)
            state.copy(
                searchQuery = query,
                filteredLocations = filtered,
                visibleMapLocations = visibleMap
            )
        }
    }

    /**
     * Restricts visible map pins to the active Google Maps camera viewport bounds.
     * Caps rendering to 100 locations to preserve 60 FPS performance.
     */
    fun onViewportBoundsChanged(bounds: LatLngBounds?) {
        currentViewportBounds = bounds
        _uiState.update { state ->
            state.copy(
                visibleMapLocations = applyViewportCulling(state.filteredLocations, bounds)
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
     * Opens map peek summary card for the tapped map marker pin.
     */
    fun openMapPeek(location: KnownLocationItem) {
        _uiState.update { it.copy(selectedLocationForMapPeek = location) }
    }

    /**
     * Dismisses map peek summary card.
     */
    fun dismissMapPeek() {
        _uiState.update { it.copy(selectedLocationForMapPeek = null) }
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
     * Deletes a known location.
     */
    fun deleteLocation(id: Long) {
        viewModelScope.launch {
            repository.deleteLocation(id)
            if (_uiState.value.selectedLocationForMapPeek?.id == id) {
                dismissMapPeek()
            }
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

    /**
     * Resolves the primary/fallback location for initial map camera centering.
     * Selects the location with the highest visit count ([KnownLocationItem.hitCount]),
     * or null if no locations exist in SQLite.
     *
     * Traceability: REQ-UI-166, TST-UI-118.2.
     */
    fun getFallbackMapLocation(): KnownLocationItem? {
        val items = _uiState.value.locations
        return if (items.isEmpty()) null else items.maxByOrNull { it.hitCount }
    }

    private fun applySort(
        items: List<KnownLocationItem>,
        sortOrder: KnownLocationSortOrder,
        userLocation: LatLng? = _uiState.value.userLocation
    ): List<KnownLocationItem> {
        return when (sortOrder) {
            KnownLocationSortOrder.STARTS -> items.sortedWith(
                compareByDescending<KnownLocationItem> { it.hitCount }
                    .thenBy { it.name.lowercase() }
            )
            KnownLocationSortOrder.DISTANCE_TO_USER -> {
                if (userLocation == null) {
                    items.sortedWith(
                        compareByDescending<KnownLocationItem> { it.hitCount }
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
                    .thenByDescending { it.hitCount }
            )
        }
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

    private fun applyViewportCulling(items: List<KnownLocationItem>, bounds: LatLngBounds?): List<KnownLocationItem> {
        val candidateList = if (bounds != null) {
            items.filter { bounds.contains(it.latLng) }
        } else {
            items
        }
        return candidateList.take(MAX_RENDERED_MAP_LOCATIONS)
    }

    companion object {
        const val MAX_RENDERED_MAP_LOCATIONS = 100
    }
}
