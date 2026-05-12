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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository responsible for managing Route data.
 * Bridges the UI and the RoutesDatabaseManager.
 */
class RoutesRepository private constructor(context: Context) {

    private val routesDb = RoutesDatabaseManager.getInstance(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for the UI to observe the list of routes
    private val _allRoutes = MutableStateFlow<List<RouteSummary>>(emptyList())
    val allRoutes: StateFlow<List<RouteSummary>> = _allRoutes.asStateFlow()

    // 2. StateFlow for the Map & Engine (Detailed Paths)
    private val _selectedRoutes = MutableStateFlow<List<RouteWithPath>>(emptyList())
    val selectedRoutes: StateFlow<List<RouteWithPath>> = _selectedRoutes.asStateFlow()

    init {
        // Initial load of route summaries from the database
        refreshRoutes()
    }

    /**
     * Refreshes both flows from the database.
     * This is called automatically after any DB modification.
     */
    fun refreshRoutes() {
        repositoryScope.launch {
            // Fetch summaries for the list view
            val summaries = routesDb.getAllRouteSummaries()
            _allRoutes.value = summaries

            // Fetch full paths only for those marked 'is_selected'
            val selectedDetailed = routesDb.getSelectedRoutes()
            _selectedRoutes.value = selectedDetailed
        }
    }

    /**
     * Fetches the full high-resolution path for a specific route.
     * This is used when a user selects a route to "Follow".
     */
    suspend fun getRouteWithPath(routeId: Long): RouteWithPath? = withContext(Dispatchers.IO) {
        routesDb.getRouteWithPath(routeId)
    }

    /**
     * Inserts a new route (from GPX import or API) and refreshes the flow.
     */
    suspend fun insertRoute(summary: RouteSummary, path: List<PathPoint>): Long = withContext(Dispatchers.IO) {
        val newId = routesDb.insertRoute(summary, path)
        refreshRoutes() // Notify observers that a new route is available
        newId
    }

    /**
     * Toggles the visibility/selection of a route on the map.
     */
    suspend fun toggleRouteSelection(routeId: Long, isSelected: Boolean) = withContext(Dispatchers.IO) {
        routesDb.setRouteSelected(routeId, isSelected)
        refreshRoutes() // Refresh so the UI shows the new "Selected" state
    }

    /**
     * Deletes a route and updates the list.
     */
    suspend fun deleteRoute(routeId: Long) = withContext(Dispatchers.IO) {
        routesDb.deleteRoute(routeId)
        refreshRoutes()
    }



    companion object {
        @Volatile
        private var instance: RoutesRepository? = null

        fun getInstance(context: Context): RoutesRepository {
            return instance ?: synchronized(this) {
                instance ?: RoutesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}