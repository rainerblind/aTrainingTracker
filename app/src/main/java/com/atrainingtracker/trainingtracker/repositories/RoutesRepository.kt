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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaRoute
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaStream
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

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

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Synchronizes starred routes from Strava.
     */
    suspend fun syncRoutesFromStrava() = withContext(Dispatchers.IO) {
        val accessToken = StravaHelper.getRefreshedAccessToken()
        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "Strava Access Token is null or empty")
            return@withContext
        }

        val athleteId = TrainingApplication.getStravaAthleteId()
        if (athleteId == 0) {
            Log.e(TAG, "Strava Athlete ID is not set")
            return@withContext
        }

        // 1. Fetch all routes from Strava
        // Strava API: GET /athletes/{id}/routes
        val url = "https://www.strava.com/api/v3/athletes/$athleteId/routes?per_page=100"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val routesResponse = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch Strava routes: ${response.code}")
                    return@withContext
                }
                response.body?.string() ?: "[]"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching Strava routes", e)
            return@withContext
        }

        val stravaRoutes = try {
            json.decodeFromString<List<StravaRoute>>(routesResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Strava routes JSON", e)
            return@withContext
        }

        // 2. Identify which routes are already in the DB
        val existingRoutes = routesDb.getAllRoutes()
        val existingExtIds = existingRoutes.map { it.summary.externalId }.toSet()

        for (stravaRoute in stravaRoutes) {
            if (existingExtIds.contains(stravaRoute.idStr)) {
                // TODO: Update existing route if needed? For now we skip.
                continue
            }

            // 3. Transform StravaRoute to RouteSummary & Path
            val sportType = when (stravaRoute.type) {
                1 -> BSportType.BIKE
                2 -> BSportType.RUN
                else -> BSportType.UNKNOWN
            }

            // TRY TO GET DETAILED STREAM FIRST
            val pathPoints = fetchRouteStreams(stravaRoute.id) ?: run {
                // Fallback to polyline if stream fails
                val polyline = stravaRoute.map.polyline ?: stravaRoute.map.summaryPolyline
                if (polyline == null) return@run emptyList<PathPoint>()
                
                val decodedPoints = PolyUtil.decode(polyline)
                var accumulatedDistance = 0.0
                decodedPoints.mapIndexed { index, latLng ->
                    if (index > 0) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            decodedPoints[index - 1].latitude, decodedPoints[index - 1].longitude,
                            latLng.latitude, latLng.longitude,
                            results
                        )
                        accumulatedDistance += results[0]
                    }
                    PathPoint(accumulatedDistance, latLng, 0.0)
                }
            }

            if (pathPoints.isEmpty()) continue

            val summary = RouteSummary(
                id = 0,
                externalId = stravaRoute.idStr,
                name = stravaRoute.name,
                description = stravaRoute.description ?: "",
                isSelected = false,
                distance = stravaRoute.distance,
                elevationGain = stravaRoute.elevationGain,
                bSportType = sportType,
                source = RouteSource.STRAVA
            )

            // 4. Insert into DB
            routesDb.insertRoute(summary, pathPoints)
        }

        // 5. Refresh the list
        refreshRoutes()
    }

    /**
     * Fetches the detailed stream for a Strava route (latlng, altitude, distance).
     */
    private suspend fun fetchRouteStreams(routeId: Long): List<PathPoint>? = withContext(Dispatchers.IO) {
        val accessToken = StravaHelper.getRefreshedAccessToken() ?: return@withContext null
        val url = "https://www.strava.com/api/v3/routes/$routeId/streams?keys=latlng,distance,altitude"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val responseBody = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error fetching route streams: ${response.code}")
                    return@withContext null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching route streams", e)
            null
        } ?: return@withContext null

        val streams = try {
            json.decodeFromString<List<StravaStream>>(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding route stream JSON", e)
            return@withContext null
        }

        if (streams.isEmpty()) return@withContext null

        val latLngStream = streams.find { it.type == "latlng" } ?: return@withContext null
        val distanceStream = streams.find { it.type == "distance" }
        val altitudeStream = streams.find { it.type == "altitude" }

        val size = latLngStream.data.size
        val pathPoints = mutableListOf<PathPoint>()

        for (i in 0 until size) {
            val coords = latLngStream.data[i].jsonArray
            val lat = coords[0].jsonPrimitive.double
            val lng = coords[1].jsonPrimitive.double
            
            val dist = distanceStream?.data?.getOrNull(i)?.jsonPrimitive?.double ?: 0.0
            val alt = altitudeStream?.data?.getOrNull(i)?.jsonPrimitive?.double ?: 0.0
            
            pathPoints.add(PathPoint(dist, LatLng(lat, lng), alt))
        }

        return@withContext pathPoints
    }



    companion object {
        private val TAG = RoutesRepository::class.java.simpleName

        @Volatile
        private var instance: RoutesRepository? = null

        fun getInstance(context: Context): RoutesRepository {
            return instance ?: synchronized(this) {
                instance ?: RoutesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}