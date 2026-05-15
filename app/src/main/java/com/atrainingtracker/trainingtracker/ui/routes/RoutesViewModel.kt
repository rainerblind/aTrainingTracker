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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RoutesViewModel(application: Application) : AndroidViewModel(application) {
    private val routesRepository = RoutesRepository.getInstance(application)

    // The list of routes to display
    val routes: StateFlow<List<RouteWithPath>> = routesRepository.allRoutes

    // Static list of sports for the Tabbed Layout
    val sports = listOf(
        BSportType.BIKE,
        BSportType.RUN,
        BSportType.UNKNOWN
    )

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