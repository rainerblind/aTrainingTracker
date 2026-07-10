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
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RouteClusterRepository private constructor(private val context: Context) {

    private val clusterDb = RouteClusterDatabaseManager.getInstance(context)
    private val summariesManager = WorkoutSummariesDatabaseManager.getInstance(context)
    
    private val mapper by lazy {
        WorkoutDataMapper(
            context,
            summariesManager,
            SportTypeDatabaseManager.getInstance(context),
            EquipmentDbHelper(context),
            StravaUploadDbHelper(context)
        )
    }

    private val _allClusters = MutableStateFlow<List<RouteCluster>>(emptyList())
    val allClusters: StateFlow<List<RouteCluster>> = _allClusters.asStateFlow()

    companion object {
        @Volatile
        private var instance: RouteClusterRepository? = null

        fun getInstance(context: Context): RouteClusterRepository {
            return instance ?: synchronized(this) {
                instance ?: RouteClusterRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun refreshClusters() = withContext(Dispatchers.IO) {
        _allClusters.value = clusterDb.getAllClusters().sortedByDescending { it.hitCount }
    }

    suspend fun updateCluster(cluster: RouteCluster) = withContext(Dispatchers.IO) {
        clusterDb.updateCluster(cluster)
        refreshClusters()
    }

    suspend fun getWorkoutsForCluster(clusterId: Long): List<WorkoutData> = withContext(Dispatchers.IO) {
        val workouts = mutableListOf<WorkoutData>()
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?"
        val args = arrayOf(clusterId.toString())
        
        summariesManager.database.query(
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
