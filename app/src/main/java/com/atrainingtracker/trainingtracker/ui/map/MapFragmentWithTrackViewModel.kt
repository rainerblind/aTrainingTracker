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

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.util.safeEnumValueOfOrNull
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbDeduplicator
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MapFragmentUIState(
    val segments: List<MapSegment> = emptyList(),
    val routes: List<MapRoute> = emptyList(),
    val clusters: List<MapCluster> = emptyList(),
    val climbs: List<Climb> = emptyList(),
    val knownLocations: List<KnownLocationItem> = emptyList(),
    val markers: List<LocationMarker> = emptyList(),
    val currentTrack: List<LatLng> = emptyList(),
    val bSportType: BSportType = BSportType.UNKNOWN
)

class MapFragmentWithTrackViewModel @JvmOverloads constructor(
    application: Application,
    private val knownLocationsRepository: KnownLocationsRepository = KnownLocationsRepository.getInstance(application),
    banalRepo: BANALServiceRepository? = null,
    segmentsRepo: SegmentsRepository? = null,
    routesRepo: RoutesRepository? = null,
    workoutClusterRepo: WorkoutClusterRepository? = null,
    liveClimbsRepo: LiveClimbsRepository? = null,
    prefs: SharedPreferences? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    private val banalRepository = banalRepo ?: BANALServiceRepository.getInstance(application)
    private val segmentsRepository = segmentsRepo ?: SegmentsRepository.getInstance(application)
    private val routesRepository = routesRepo ?: RoutesRepository.getInstance(application)
    private val workoutClusterRepository = workoutClusterRepo ?: WorkoutClusterRepository.getInstance(application)
    private val liveClimbsRepository = liveClimbsRepo ?: LiveClimbsRepository.getInstance(application)
    private val sharedPreferences = prefs ?: PreferenceManager.getDefaultSharedPreferences(application)

    private val _enabledLayers = MutableStateFlow(loadEnabledLayers())
    val enabledLayers: StateFlow<Set<GeneralMapLayer>> = _enabledLayers.asStateFlow()

    init {
        if (GeneralMapLayer.CLUSTERS in _enabledLayers.value && workoutClusterRepository.allClusters.value.isEmpty()) {
            viewModelScope.launch {
                workoutClusterRepository.loadAllClustersLightweight()
            }
        }
    }

    private fun loadEnabledLayers(): Set<GeneralMapLayer> {
        val saved = sharedPreferences.getStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, null)
            ?: return GeneralMapLayer.DEFAULT_ENABLED
        return saved.mapNotNull { name ->
            safeEnumValueOfOrNull<GeneralMapLayer>(name)
        }.toSet()
    }

    fun toggleLayer(layer: GeneralMapLayer) {
        val current = _enabledLayers.value
        val updated = if (layer in current) current - layer else current + layer
        _enabledLayers.value = updated
        sharedPreferences.edit()
            .putStringSet(PREF_GENERAL_MAP_ENABLED_LAYERS, updated.map { it.name }.toSet())
            .apply()
        if (layer == GeneralMapLayer.CLUSTERS && layer in updated && workoutClusterRepository.allClusters.value.isEmpty()) {
            viewModelScope.launch {
                workoutClusterRepository.loadAllClustersLightweight()
            }
        }
    }

    val liveSegments = segmentsRepository.allSegmentsWithPath
    val allRoutes = routesRepository.allRoutes
    val activeNavigatedRouteId = routesRepository.activeNavigatedRouteId
    val knownLocations = knownLocationsRepository.locationsFlow
    val allClusters = workoutClusterRepository.allClusters

    private val mapRoutesFlow = combine(
        routesRepository.allRoutes,
        routesRepository.activeNavigatedRouteId
    ) { allRoutes, activeRouteId ->
        allRoutes.map { it.toMapRoute(isActiveNavigation = (it.summary.id == activeRouteId)) }
    }

    private val mapClustersFlow = combine(
        enabledLayers,
        workoutClusterRepository.allClusters,
        workoutClusterRepository.clusterStats
    ) { layers, clusterList, statsMap ->
        if (GeneralMapLayer.CLUSTERS !in layers) {
            emptyList()
        } else {
            clusterList.mapNotNull { cluster ->
                cluster.toMapCluster(stats = statsMap[cluster.id])
            }
        }
    }.flowOn(defaultDispatcher).distinctUntilChanged()

    private val mapClimbsFlow = combine(enabledLayers, liveClimbsRepository.allStoredClimbs) { layers, storedClimbs ->
        if (GeneralMapLayer.CLIMBS !in layers) {
            emptyList()
        } else {
            ClimbDeduplicator.deduplicateForDisplay(storedClimbs)
        }
    }.flowOn(defaultDispatcher).distinctUntilChanged()

    private data class StaticMapLayers(
        val segments: List<MapSegment>,
        val routes: List<MapRoute>,
        val clusters: List<MapCluster>,
        val climbs: List<Climb>,
        val knownLocations: List<KnownLocationItem>
    )

    private val staticMapLayersFlow = combine(
        segmentsRepository.allSegmentsWithPath,
        mapRoutesFlow,
        mapClustersFlow,
        mapClimbsFlow,
        knownLocationsRepository.locationsFlow
    ) { segments, routes, clusters, climbs, knownLocations ->
        StaticMapLayers(
            segments = segments.map { liveSegment ->
                MapSegment(
                    stravaId = liveSegment.summary.stravaId,
                    name = liveSegment.summary.name,
                    bSportType = liveSegment.summary.bSportType,
                    path = liveSegment.path,
                    showStartAndFinishText = true
                )
            },
            routes = routes,
            clusters = clusters,
            climbs = climbs,
            knownLocations = knownLocations
        )
    }

    val uiState: StateFlow<MapFragmentUIState> = combine(
        banalRepository.bSportType,
        banalRepository.currentTrack,
        staticMapLayersFlow
    ) { bSportType, currentTrack, staticLayers ->

        // Logic for Start Marker
        val markers = if (currentTrack.isNotEmpty()) {
            listOf(
                LocationMarker(
                    position = currentTrack.first(),
                    iconResId = R.drawable.start_logo_map,
                    title = application.getString(R.string.Start)
                )
            )
        } else {
            emptyList()
        }

        MapFragmentUIState(
            segments = staticLayers.segments,
            routes = staticLayers.routes,
            clusters = staticLayers.clusters,
            climbs = staticLayers.climbs,
            knownLocations = staticLayers.knownLocations,
            bSportType = bSportType,
            currentTrack = currentTrack,
            markers = markers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapFragmentUIState()
    )

    val currentLocation: StateFlow<LatLng?> = banalRepository.currentLocation

    fun onToggleRoute(id: Long, selected: Boolean) {
        viewModelScope.launch {
            routesRepository.toggleRouteSelection(routeId = id, isSelected = selected)
        }
    }

    fun updateKnownLocation(id: Long, name: String, altitude: Double, radius: Int, source: ElevationSource) {
        viewModelScope.launch {
            knownLocationsRepository.updateLocation(id, name, altitude, radius, source)
        }
    }

    companion object {
        const val PREF_GENERAL_MAP_ENABLED_LAYERS = "pref_general_map_enabled_layers"
    }
}
