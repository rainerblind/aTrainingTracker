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
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


enum class RouteSortOrder(@StringRes val labelResId: Int) {
    DISTANCE_TO_USER(R.string.sort_closest),
    TOTAL_ELEVATION_GAIN(R.string.sort_elevation_gain),
    ROUTE_DISTANCE(R.string.sort_route_length),
    NAME(R.string.sort_name)
}

class RoutesViewModel(application: Application) : AndroidViewModel(application) {
    private val routesRepository = RoutesRepository.getInstance(application)
    private val banalServiceRepository = BANALServiceRepository.getInstance(application)


    private val _sortOrder = MutableStateFlow(RouteSortOrder.DISTANCE_TO_USER)
    val sortOrder = _sortOrder.asStateFlow()

    private var lastScrolledOrder: RouteSortOrder? = null

    fun shouldScrollToTop(currentOrder: RouteSortOrder): Boolean {
        Log.i("RouteListViewModel", "shouldScroll(currentOrder=$currentOrder), lastScrolledOrder=$lastScrolledOrder")
        if (lastScrolledOrder != currentOrder) {
            lastScrolledOrder = currentOrder
            return true
        }
        return false
    }

    fun setSortOrder(order: RouteSortOrder) {
        _sortOrder.value = order
    }

    // The list of routes to display; properly sorted
    val routes: StateFlow<List<RouteWithPath>> = combine(
        routesRepository.allRoutes,
        _sortOrder,
        banalServiceRepository.currentLocation // Directly observing the BANALService source
    ) { routes, order, location ->
        when (order) {
            RouteSortOrder.NAME ->
                routes.sortedBy { it.summary.name.lowercase() }

            RouteSortOrder.TOTAL_ELEVATION_GAIN ->
                routes.sortedWith(
                    compareByDescending<RouteWithPath> { it.summary.elevationGain }
                        .thenBy { it.summary.name.lowercase() }
                )

            RouteSortOrder.ROUTE_DISTANCE ->
                routes.sortedWith(
                    compareByDescending<RouteWithPath> { it.summary.distance }
                        .thenBy { it.summary.name.lowercase() }
                )

            RouteSortOrder.DISTANCE_TO_USER -> {
                if (location == null) {
                    routes.sortedBy { it.summary.name.lowercase() }
                } else {
                    routes.sortedBy { route ->
                        calculateDistance(
                            location.latitude, location.longitude,
                            route.path[0].latLng.latitude, route.path[0].latLng.longitude
                        )
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun calculateDistance(uLat: Double, uLon: Double, sLat: Double, sLon: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(uLat, uLon, sLat, sLon, results)
        return results[0]
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

    fun refresh() {
        routesRepository.refreshRoutes()
    }
}