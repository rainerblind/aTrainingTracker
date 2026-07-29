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
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.ui.util.ProgressPhase
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
    private val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    
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

    private val _migrationStatus = MutableStateFlow<MigrationStatus?>(null)
    val migrationStatus: StateFlow<MigrationStatus?> = _migrationStatus.asStateFlow()

    companion object {
        private const val SP_KEY_LAST_BOUNDS_REPAIR = "last_cluster_bounds_repair_v5"
        @Volatile
        private var instance: WorkoutClusterRepository? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutClusterRepository {
            return instance ?: synchronized(this) {
                instance ?: WorkoutClusterRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun refreshClusters(forceShowProgress: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val currentClusters = clusterDb.getAllClusters()
        
        // --- SELF-HEALING BOOTSTRAPPER (ATT-392 Refinement) ---
        // If the DB is empty (e.g. after a destructive upgrade), automatically repopulate from history.
        if (currentClusters.isEmpty() && _migrationStatus.value == null) {
            if (DEBUG) android.util.Log.i("WorkoutClusterRepo", "Database empty. Triggering self-healing bootstrap...")
            recalculateClustersWithProgress()
            return@withContext
        }

        // 0. Informative Enrichment Pass (ATT-371/392 Refinement)
        val lastRepair = prefs.getInt(SP_KEY_LAST_BOUNDS_REPAIR, 0)
        if (lastRepair < 5 && currentClusters.isNotEmpty()) {
            repairClusterMetadata()
        }

        // --- ATT-441: Optimized Refresh Logic ---
        // If data is already persistent, load silently.
        val needsEnrichment = currentClusters.any { it.previewPaths.isEmpty() && it.hitCount > 0 }
        val showProgress = forceShowProgress || (needsEnrichment && _allClusters.value.isEmpty())
        
        val title = context.getString(R.string.cluster_migration_title)

        if (showProgress) {
            _migrationStatus.value = MigrationStatus(
                title,
                listOf(ProgressPhase(1, context.getString(R.string.cluster_migration_loading), 0.0f))
            )
        }
        
        if (!needsEnrichment && !forceShowProgress) {
            _allClusters.value = currentClusters.sortedByDescending { it.hitCount }
            return@withContext
        }

        // --- PHASE 1: INTEGRITY CHECK ---
        val msgHealing = context.getString(R.string.cluster_migration_healing)
        if (showProgress) {
            _migrationStatus.value = MigrationStatus(
                title,
                listOf(ProgressPhase(1, msgHealing, 0.1f))
            )
        }
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

        // Phase 1 Complete
        val phase1Finished = ProgressPhase(1, msgHealing, 1.0f)

        // --- PHASE 2: PREVIEW PREPARATION ---
        val total = rawClusters.size
        val enriched = rawClusters.mapIndexed { index, cluster ->
            if (showProgress && index % 5 == 0) {
                val msg = context.getString(R.string.cluster_migration_previews, index + 1, total)
                _migrationStatus.value = MigrationStatus(
                    title,
                    listOf(
                        phase1Finished,
                        ProgressPhase(2, msg, index.toFloat() / total.toFloat())
                    )
                )
            }

            // Update hit count if reality differs (Self-Healing)
            val realCount = actualCounts[cluster.id] ?: 0
            val updatedCluster = if (cluster.hitCount != realCount) {
                if (DEBUG) android.util.Log.i("WorkoutClusterRepo", "Correcting hit count for ${cluster.name}: ${cluster.hitCount} -> $realCount")
                cluster.copy(hitCount = realCount)
            } else cluster

            // --- POPULATE PREVIEW PATHS (SCRUM-224 / ATT-441) ---
            var previewPaths = updatedCluster.previewPaths.toMutableList()
            var routePolyline = updatedCluster.routePolyline
            
            if (previewPaths.isEmpty() && updatedCluster.hitCount > 0) {
                // 1. Check for linked route
                val linkedRoute = routesDb.getRouteByClusterId(updatedCluster.id)
                routePolyline = if (linkedRoute != null && linkedRoute.path.isNotEmpty()) {
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
            }

            val finalCluster = updatedCluster.copy(
                bSportType = getBSportType(updatedCluster.probableSportId),
                previewPaths = previewPaths,
                routePolyline = routePolyline
            )
            
            // Persist the enrichment (ATT-441)
            if (finalCluster != cluster) {
                clusterDb.updateCluster(finalCluster)
            }
            finalCluster
        }
        
        _allClusters.value = enriched.sortedByDescending { it.hitCount }
        if (showProgress) {
            _migrationStatus.value = null
        }
    }

    suspend fun updateCluster(cluster: WorkoutCluster) = withContext(Dispatchers.IO) {
        clusterDb.updateCluster(cluster)
        refreshClusters()
    }

    private suspend fun repairClusterMetadata() = withContext(Dispatchers.Default) {
        val title = context.getString(R.string.cluster_migration_title)
        val engine = WorkoutClusterEngine.getInstance(context)
        
        val listener = object : ClusterMigrationListener {
            override fun onPhase1Progress(current: Int, total: Int) {
                // Not used in repair pass
            }

            override fun onPhase2Progress(current: Int, total: Int) {
                val msg = context.getString(R.string.cluster_migration_enriching, current, total)
                _migrationStatus.value = MigrationStatus(
                    title,
                    listOf(
                        ProgressPhase(1, context.getString(R.string.cluster_migration_healing), 1.0f),
                        ProgressPhase(2, msg, if (total > 0) current.toFloat() / total.toFloat() else 1.0f)
                    )
                )
            }
        }

        engine.enrichAllClusterMetadata(context, listener)
        
        prefs.edit().putInt(SP_KEY_LAST_BOUNDS_REPAIR, 5).apply()
        _migrationStatus.value = null
    }

    suspend fun recalculateClustersWithProgress(): Unit = withContext(Dispatchers.Default) {
        val title = context.getString(R.string.cluster_migration_title)
        val engine = WorkoutClusterEngine.getInstance(context)
        
        val listener = object : ClusterMigrationListener {
            override fun onPhase1Progress(current: Int, total: Int) {
                val msg = context.getString(R.string.cluster_migration_processing_routes)
                _migrationStatus.value = MigrationStatus(
                    title,
                    listOf(ProgressPhase(1, msg, if (total > 0) current.toFloat() / total.toFloat() else 1.0f))
                )
            }

            override fun onPhase2Progress(current: Int, total: Int) {
                val msg = context.getString(R.string.cluster_migration_processing_workouts, current, total)
                _migrationStatus.value = MigrationStatus(
                    title,
                    listOf(
                        ProgressPhase(1, context.getString(R.string.cluster_migration_processing_routes), 1.0f),
                        ProgressPhase(2, msg, if (total > 0) current.toFloat() / total.toFloat() else 1.0f)
                    )
                )
            }
        }

        withContext(Dispatchers.IO) {
            engine.recalculateHistory(context, listener)
        }
        
        refreshClusters()
        _migrationStatus.value = null
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
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = ?"
        val args = arrayOf(clusterId.toString())
        
        loadWorkoutsWithBatchMetadata(selection, args)
    }

    suspend fun getUnclusteredWorkouts(): List<WorkoutData> = withContext(Dispatchers.IO) {
        val selection = "${WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID} = -1"
        loadWorkoutsWithBatchMetadata(selection, null)
    }

    /**
     * Efficiently loads workouts and their associated metadata in batches (ATT-359).
     * Reduces hundreds of sequential DB queries to just 3 per batch.
     */
    private suspend fun loadWorkoutsWithBatchMetadata(selection: String, args: Array<String>?): List<WorkoutData> = withContext(Dispatchers.IO) {
        val workouts = mutableListOf<WorkoutData>()
        val db = summariesManager.getDatabase()
        
        db.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null, selection, args, null, null, 
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext emptyList<WorkoutData>()

            // 1. Gather IDs, FileNames, and Cluster IDs for the batch
            val idList = mutableListOf<Long>()
            val fileNameList = mutableListOf<String>()
            val clusterIdList = mutableSetOf<Long>()
            do {
                idList.add(cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)))
                cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME))?.let {
                    fileNameList.add(it)
                }
                val clusterId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID))
                if (clusterId != -1L) {
                    clusterIdList.add(clusterId)
                }
            } while (cursor.moveToNext())

            // 2. Perform Batch Metadata Lookups (ATT-359/388)
            val extremaList = summariesManager.getExtremaForWorkouts(idList)
            val stravaDataMap = StravaUploadDbHelper(context).getStravaActivityDataForWorkouts(fileNameList)
            val clusterNamesMap = clusterDb.getClusterNamesForIds(clusterIdList)
            
            val batchMetadata = WorkoutDataMapper.BatchMetadata(
                extrema = extremaList.groupBy { it.workoutId },
                stravaData = stravaDataMap,
                clusterNames = clusterNamesMap
            )

            // 3. Map everything using pre-fetched data
            cursor.moveToFirst()
            do {
                workouts.add(mapper.fromCursor(cursor, batchMetadata))
            } while (cursor.moveToNext())
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
