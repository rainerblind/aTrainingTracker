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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _allRoutes = MutableStateFlow<List<RouteWithPath>>(emptyList())
    val allRoutes: StateFlow<List<RouteWithPath>> = _allRoutes.asStateFlow()

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
            _allRoutes.value = routesDb.getAllRoutes()
        }
    }

    /**
     * Fetches the full high-resolution path with distance and altitude for a specific route.
     */
    suspend fun getRoutePath(routeId: Long): List<PathPoint> = withContext(Dispatchers.IO) {
        routesDb.getRoutePath(routeId)
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
     * Updates the metadata (name, description, sport type) of an existing route.
     *
     * @param summary The updated RouteSummary object.
     */
    suspend fun updateRouteSummary(summary: RouteSummary) {
        // 1. Update the record in the database
        routesDb.updateRouteSummary(summary)

        // 2. Trigger a refresh so all collectors (List View, Map View)
        refreshRoutes()
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