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

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.RouteAutoDetector
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * UI State for Quick Route Selector sheet (REQ-MAP-024, REQ-UI-280 / ATT-2459, REQ-UI-281 / ATT-2460, REQ-UI-289 / ATT-2627).
 */
data class RouteSelectorUiState(
    val routes: List<RouteWithPath> = emptyList(),
    val totalRouteCount: Int = 0,
    val activeRoute: RouteWithPath? = null,
    val autoDetectedCandidate: RouteWithPath? = null,
    val isAutoPromptVisible: Boolean = false,
    val isTrackingActive: Boolean = false,
    val contextEmptyHintRes: Int = R.string.route_no_routes_nearby
)

/**
 * ViewModel managing state and user actions for Quick Route Selector & Route Auto Detection (REQ-UI-280, REQ-UI-281, REQ-UI-289).
 */
class RouteSelectorViewModel(
    private val routesRepository: RoutesRepository,
    private val autoDetector: RouteAutoDetector = RouteAutoDetector(),
    private val tuningPreferencesDataStore: TuningPreferencesDataStore? = null,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

    constructor(
        routesRepository: RoutesRepository,
        autoDetector: RouteAutoDetector,
        sharingStarted: SharingStarted
    ) : this(routesRepository, autoDetector, null, sharingStarted)

    private val _lastLocation = MutableStateFlow<Location?>(null)
    private val _autoDetectedCandidate = MutableStateFlow<RouteWithPath?>(null)
    private val _isTrackingActive = MutableStateFlow(false)
    private val _activeSportType = MutableStateFlow<BSportType>(BSportType.UNKNOWN)

    private val radiusFlow: Flow<Float> = tuningPreferencesDataStore?.tuningConfigFlow
        ?.map { it.routeSelectionRadiusKm * 1000.0f }
        ?: flowOf(TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM * 1000.0f)

    private data class RouteContext(
        val location: Location?,
        val isTracking: Boolean,
        val radiusMeters: Float,
        val activeSport: BSportType
    )

    private val routeContextFlow: Flow<RouteContext> = combine(
        _lastLocation,
        _isTrackingActive,
        radiusFlow,
        _activeSportType
    ) { location, isTracking, radiusMeters, activeSport ->
        RouteContext(location, isTracking, radiusMeters, activeSport)
    }

    val uiState: StateFlow<RouteSelectorUiState> = combine(
        routesRepository.allRoutes,
        routesRepository.activeNavigatedRouteId,
        _autoDetectedCandidate,
        routeContextFlow
    ) { allRoutes, activeRouteId, candidate, context ->
        val activeRoute = allRoutes.find { it.summary.id == activeRouteId }
        val currentLatLng = context.location?.let { LatLng(it.latitude, it.longitude) }

        val (candidateRoutes, emptyHintRes) = if (!context.isTracking) {
            val ranked = RouteProximityRanker.filterAndRankRoutes(
                routes = allRoutes,
                currentLocation = currentLatLng,
                radiusMeters = context.radiusMeters,
                activeSport = context.activeSport
            )
            Pair(ranked, R.string.route_no_routes_nearby)
        } else {
            val matched = if (context.location != null) {
                val candidateRoutesForSport = allRoutes.filter {
                    RouteProximityRanker.matchesSport(it.summary.bSportType, context.activeSport)
                }
                autoDetector.evaluateMatchingRoutes(
                    location = context.location,
                    routes = candidateRoutesForSport,
                    currentlyActiveRouteId = activeRouteId
                )
            } else {
                emptyList()
            }
            Pair(matched, R.string.route_no_matching_route_detected)
        }

        RouteSelectorUiState(
            routes = candidateRoutes,
            totalRouteCount = allRoutes.size,
            activeRoute = activeRoute,
            autoDetectedCandidate = candidate,
            isAutoPromptVisible = candidate != null,
            isTrackingActive = context.isTracking,
            contextEmptyHintRes = emptyHintRes
        )
    }.stateIn(
        scope = viewModelScope,
        started = sharingStarted,
        initialValue = RouteSelectorUiState()
    )

    fun setActiveSport(sport: BSportType) {
        _activeSportType.value = sport
    }

    fun setTrackingActive(isActive: Boolean) {
        _isTrackingActive.value = isActive
    }

    fun selectRoute(routeId: Long) {
        routesRepository.setActiveNavigatedRoute(routeId)
        if (_autoDetectedCandidate.value?.summary?.id == routeId) {
            _autoDetectedCandidate.value = null
        }
    }

    fun stopRoute() {
        routesRepository.setActiveNavigatedRoute(null)
    }

    fun clearRoute() {
        stopRoute()
    }

    fun onLocationChanged(location: Location) {
        _lastLocation.value = location
        val activeRouteId = routesRepository.activeNavigatedRouteId.value
        val allRoutes = routesRepository.allRoutes.value

        val filteredRoutes = allRoutes.filter {
            RouteProximityRanker.matchesSport(it.summary.bSportType, _activeSportType.value)
        }
        val candidate = autoDetector.evaluate(location, filteredRoutes, activeRouteId)
        if (candidate != null) {
            _autoDetectedCandidate.value = candidate
        }
    }

    fun dismissCandidate(routeId: Long) {
        autoDetector.dismissRoute(routeId)
        if (_autoDetectedCandidate.value?.summary?.id == routeId) {
            _autoDetectedCandidate.value = null
        }
    }

    fun activateCandidate(routeId: Long) {
        selectRoute(routeId)
    }
}

