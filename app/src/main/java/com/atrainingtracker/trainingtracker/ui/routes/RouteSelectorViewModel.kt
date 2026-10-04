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
import kotlinx.coroutines.launch

/**
 * Filter tabs for the Quick Route Selector (REQ-MAP-024 / ATT-1835).
 */
enum class RouteFilterTab(val labelResId: Int) {
    NEARBY(R.string.route_filter_near),
    RECENT(R.string.route_filter_recent),
    LENGTH(R.string.route_filter_length)
}

/**
 * UI State for Quick Route Selector sheet.
 */
data class RouteSelectorUiState(
    val routes: List<RouteWithPath> = emptyList(),
    val totalRouteCount: Int = 0,
    val showFilterTabs: Boolean = false,
    val activeRoute: RouteWithPath? = null,
    val selectedTab: RouteFilterTab = RouteFilterTab.NEARBY,
    val autoDetectedCandidate: RouteWithPath? = null,
    val isAutoPromptVisible: Boolean = false
)

/**
 * ViewModel managing state and user actions for Quick Route Selector & Route Auto Detection.
 */
class RouteSelectorViewModel(
    private val routesRepository: RoutesRepository,
    private val autoDetector: RouteAutoDetector = RouteAutoDetector(),
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(RouteFilterTab.NEARBY)
    private val _lastLocation = MutableStateFlow<Location?>(null)
    private val _autoDetectedCandidate = MutableStateFlow<RouteWithPath?>(null)

    val uiState: StateFlow<RouteSelectorUiState> = combine(
        routesRepository.allRoutes,
        routesRepository.activeNavigatedRouteId,
        _selectedTab,
        _lastLocation,
        _autoDetectedCandidate
    ) { allRoutes, activeRouteId, tab, location, candidate ->
        val activeRoute = allRoutes.find { it.summary.id == activeRouteId }

        val filteredAndSortedRoutes = when (tab) {
            RouteFilterTab.NEARBY -> {
                val currentLatLng = location?.let { LatLng(it.latitude, it.longitude) }
                val currentBearing = if (location != null && location.hasBearing()) location.bearing else null
                RouteProximityRanker.rankRoutes(
                    routes = allRoutes,
                    currentLocation = currentLatLng,
                    currentBearing = currentBearing,
                    activeSport = null
                )
            }
            RouteFilterTab.RECENT -> {
                allRoutes.sortedByDescending { it.summary.syncedAt }
            }
            RouteFilterTab.LENGTH -> {
                allRoutes.sortedBy { it.summary.distance }
            }
        }

        RouteSelectorUiState(
            routes = filteredAndSortedRoutes,
            totalRouteCount = allRoutes.size,
            showFilterTabs = allRoutes.size >= 5,
            activeRoute = activeRoute,
            selectedTab = tab,
            autoDetectedCandidate = candidate,
            isAutoPromptVisible = candidate != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = sharingStarted,
        initialValue = RouteSelectorUiState()
    )

    fun setFilterTab(tab: RouteFilterTab) {
        _selectedTab.value = tab
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
