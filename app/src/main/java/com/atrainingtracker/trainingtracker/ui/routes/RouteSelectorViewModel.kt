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
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.RouteAutoDetector
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * UI State for Quick Route Selector sheet (REQ-MAP-024, REQ-UI-280 / ATT-2459).
 */
data class RouteSelectorUiState(
    val routes: List<RouteWithPath> = emptyList(),
    val totalRouteCount: Int = 0,
    val activeRoute: RouteWithPath? = null,
    val autoDetectedCandidate: RouteWithPath? = null,
    val isAutoPromptVisible: Boolean = false
)

/**
 * ViewModel managing state and user actions for Quick Route Selector & Route Auto Detection (REQ-UI-280 / ATT-2459).
 */
class RouteSelectorViewModel(
    private val routesRepository: RoutesRepository,
    private val autoDetector: RouteAutoDetector = RouteAutoDetector(),
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

    private val _lastLocation = MutableStateFlow<Location?>(null)
    private val _autoDetectedCandidate = MutableStateFlow<RouteWithPath?>(null)

    val uiState: StateFlow<RouteSelectorUiState> = combine(
        routesRepository.allRoutes,
        routesRepository.activeNavigatedRouteId,
        _lastLocation,
        _autoDetectedCandidate
    ) { allRoutes, activeRouteId, location, candidate ->
        val activeRoute = allRoutes.find { it.summary.id == activeRouteId }

        val currentLatLng = location?.let { LatLng(it.latitude, it.longitude) }
        val currentBearing = if (location != null && location.hasBearing()) location.bearing else null
        val rankedRoutes = RouteProximityRanker.rankRoutes(
            routes = allRoutes,
            currentLocation = currentLatLng,
            currentBearing = currentBearing,
            activeSport = null
        )

        RouteSelectorUiState(
            routes = rankedRoutes,
            totalRouteCount = allRoutes.size,
            activeRoute = activeRoute,
            autoDetectedCandidate = candidate,
            isAutoPromptVisible = candidate != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = sharingStarted,
        initialValue = RouteSelectorUiState()
    )

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

        val candidate = autoDetector.evaluate(location, allRoutes, activeRouteId)
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
