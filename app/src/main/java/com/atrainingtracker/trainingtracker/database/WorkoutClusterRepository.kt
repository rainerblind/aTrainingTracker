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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WorkoutClusterRepository private constructor(private val context: Context) {

    private val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
    private val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
    private val DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true)
    
    private val mapper by lazy {
        WorkoutDataMapper(
            context,
            summariesManager,
            SportTypeDatabaseManager.getInstance(context),
            EquipmentDbHelper(context),
            StravaUploadDbHelper(context)
        )
    }

    private val _allClusters = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    val allClusters: StateFlow<List<WorkoutCluster>> = _allClusters.asStateFlow()

    companion object {
        @Volatile
        private var instance: WorkoutClusterRepository? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutClusterRepository {
            return instance ?: synchronized(this) {
                instance ?: WorkoutClusterRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun refreshClusters() = withContext(Dispatchers.IO) {
        // --- SELF-HEALING HIT COUNTS (SCRUM-228) ---
        val actualCounts = mutableMapOf<Long, Int>()
        summariesManager.database.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, "COUNT(*)"),
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} != -1",
            null,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID,
            null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                actualCounts[cursor.getLong(0)] = cursor.getInt(1)
            }
        }

        val rawClusters = clusterDb.getAllClusters()
        val routesDb = RoutesDatabaseManager.getInstance(context)

        val enriched = rawClusters.map { cluster ->
            // Update hit count if reality differs (Self-Healing)
            val realCount = actualCounts[cluster.id] ?: 0
            val updatedCluster = if (cluster.hitCount != realCount) {
                if (DEBUG) android.util.Log.i("WorkoutClusterRepo", "Correcting hit count for ${cluster.name}: ${cluster.hitCount} -> $realCount")
                val fixed = cluster.copy(hitCount = realCount)
                clusterDb.updateCluster(fixed)
                fixed
            } else cluster

            // --- POPULATE PREVIEW PATHS (SCRUM-224) ---
            val previewPaths = mutableListOf<String>()
            
            // 1. Check for linked route
            val linkedRoute = routesDb.getRouteByClusterId(updatedCluster.id)
            val routePolyline = if (linkedRoute != null && linkedRoute.path.isNotEmpty()) {
                PolyUtil.encode(linkedRoute.path.map { it.latLng })
            } else null

            // 2. Fetch 5 most recent workout paths
            val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?"
            val args = arrayOf(updatedCluster.id.toString())
            val projection = arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.MAP_POLYLINE)
            
            summariesManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                projection, selection, args, null, null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC",
                "5" // Limit to 5
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val polyline = cursor.getString(0)
                    if (!polyline.isNullOrEmpty()) {
                        previewPaths.add(polyline)
                    }
                }
            }

            updatedCluster.copy(
                bSportType = getBSportType(updatedCluster.probableSportId),
                previewPaths = previewPaths,
                routePolyline = routePolyline
            )
        }
        _allClusters.value = enriched.sortedByDescending { it.hitCount }
    }

    suspend fun updateCluster(cluster: WorkoutCluster) = withContext(Dispatchers.IO) {
        clusterDb.updateCluster(cluster)
        refreshClusters()
    }

    suspend fun deleteCluster(clusterId: Long) = withContext(Dispatchers.IO) {
        // 1. Clear links in workouts
        summariesManager.clearClusterLink(clusterId)

        // 2. Clear links in routes
        RoutesDatabaseManager.getInstance(context).clearClusterLink(clusterId)

        // 3. Delete the cluster itself
        clusterDb.deleteCluster(clusterId)

        // 4. Refresh
        refreshClusters()
    }

    suspend fun getWorkoutsForCluster(clusterId: Long): List<WorkoutData> = withContext(Dispatchers.IO) {
        val workouts = mutableListOf<WorkoutData>()
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?"
        val args = arrayOf(clusterId.toString())
        
        summariesManager.getDatabase().query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null, selection, args, null, null, 
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                workouts.add(mapper.fromCursor(cursor))
            }
        }
        workouts
    }

    suspend fun getUnclusteredWorkouts(): List<WorkoutData> = withContext(Dispatchers.IO) {
        val workouts = mutableListOf<WorkoutData>()
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = -1"

        summariesManager.getDatabase().query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null, selection, null, null, null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                workouts.add(mapper.fromCursor(cursor))
            }
        }
        workouts
    }

    suspend fun getWorkoutTrackPoints(workoutId: Long, trackType: TrackType): List<PathPoint> {
        return WorkoutRepository.getInstance(context as android.app.Application).getWorkoutTrackPoints(workoutId, trackType)
    }

    fun getSportName(sportId: Long): String {
        return SportTypeDatabaseManager.getInstance(context).getUIName(sportId)
    }

    fun getBSportType(sportId: Long): com.atrainingtracker.banalservice.BSportType {
        return SportTypeDatabaseManager.getInstance(context).getBSportType(sportId)
    }
}
