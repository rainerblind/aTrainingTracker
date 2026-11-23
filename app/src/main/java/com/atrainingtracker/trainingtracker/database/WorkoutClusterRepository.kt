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
import android.database.Cursor
import com.atrainingtracker.R
import com.atrainingtracker.database.inTransaction
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.toClusterWorkoutSnapshot
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import com.atrainingtracker.trainingtracker.ui.util.ProgressPhase
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.maps.android.PolyUtil
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Collects [kotlinx.coroutines.flow.Flow] emissions while rethrowing [kotlinx.coroutines.CancellationException]
 * and ignoring MockK's `KotlinNothingValueException` when a relaxed mock leaves a flow unstubbed.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> kotlinx.coroutines.flow.Flow<T>.collectIgnoringUnstubbedMock(
    action: suspend (T) -> Unit
) {
    try {
        collect { value -> action(value) }
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (t: Throwable) {
        if (t::class.simpleName != "KotlinNothingValueException") {
            throw t
        }
    }
}

/**
 * Domain events emitted after any persistent RouteCluster mutation completes (REQ-PER-040.1).
 */
sealed class ClusterMutationEvent {
    data class Renamed(val clusterId: Long, val newName: String) : ClusterMutationEvent()
    data class Deleted(val clusterId: Long) : ClusterMutationEvent()
    data class Merged(val sourceId: Long, val targetId: Long, val targetName: String) : ClusterMutationEvent()
    data class Split(
        val primaryClusterId: Long,
        val createdClusterIds: List<Long>,
        val affectedWorkoutIds: List<Long>
    ) : ClusterMutationEvent()
}

class WorkoutClusterRepository internal constructor(
    private val context: Context,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val clusterDb get() = WorkoutClusterDatabaseManager.getInstance(context)
    private val summariesManager get() = WorkoutSummariesDatabaseManager.getInstance(context)
    private val DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true)
    private val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    private val isRecalculatingLock = AtomicBoolean(false)
    private val lightweightLoadMutex = Mutex()
    
    private val batchMetadataLoader by lazy {
        com.atrainingtracker.trainingtracker.repositories.BatchMetadataLoader(
            context = context,
            summariesManagerProvider = { summariesManager },
            clusterDbProvider = { clusterDb }
        )
    }

    private val mapper by lazy {
        WorkoutDataMapper(
            context,
            summariesManager,
            SportTypeDatabaseManager.getInstance(context),
            EquipmentDbHelper.getInstance(context),
            StravaUploadDbHelper.getInstance(context),
            batchMetadataLoader = batchMetadataLoader
        )
    }

    private val _allClusters = MutableStateFlow<List<WorkoutCluster>>(emptyList())
    val allClusters: StateFlow<List<WorkoutCluster>> = _allClusters.asStateFlow()

    private val _migrationStatus = MutableStateFlow<MigrationStatus?>(null)
    val migrationStatus: StateFlow<MigrationStatus?> = _migrationStatus.asStateFlow()

    private val _clusterStats = MutableStateFlow<Map<Long, WorkoutClusterStats>>(emptyMap())
    val clusterStats: StateFlow<Map<Long, WorkoutClusterStats>> = _clusterStats.asStateFlow()

    private val _clusterMutationEvents = MutableSharedFlow<ClusterMutationEvent>(extraBufferCapacity = 16)
    val clusterMutationEvents: SharedFlow<ClusterMutationEvent> = _clusterMutationEvents.asSharedFlow()

    private suspend fun notifyWorkoutRepository(action: suspend (WorkoutRepository) -> Unit) {
        try {
            action(WorkoutRepository.getInstance(context))
        } catch (t: Throwable) {
            if (DEBUG) {
                runCatching {
                    android.util.Log.w("WorkoutClusterRepo", "Failed to notify WorkoutRepository: ${t.message}")
                }
            }
        }
    }

    private suspend fun notifyRoutesRepository(action: suspend (RoutesRepository) -> Unit) {
        try {
            action(RoutesRepository.getInstance(context))
        } catch (t: Throwable) {
            if (DEBUG) {
                runCatching {
                    android.util.Log.w("WorkoutClusterRepo", "Failed to notify RoutesRepository: ${t.message}")
                }
            }
        }
    }

    init {
        // Fast, non-blocking asynchronous hydration directly from RouteClusters table (ATT-5074 / REQ-UI-322.5).
        // Populates _allClusters without blocking on heavy WorkoutSummaries aggregation or member hydration.
        repositoryScope.launch(Dispatchers.IO) {
            try {
                if (_allClusters.value.isEmpty()) {
                    loadAllClustersLightweight()
                }
            } catch (t: Throwable) {
                if (DEBUG) runCatching { android.util.Log.w("WorkoutClusterRepo", "Failed initial lightweight clusters load: ${t.message}") }
            }
        }
    }

    /**
     * Fast, lightweight loading pathway from the SQLite RouteClusters table (REQ-UI-322.5, ATT-5074, ATT-5196).
     * Populates [_allClusters] directly and hydrates companion [_clusterStats] if empty (REQ-UI-322.10, ATT-5196)
     * so initial map displays show complete metrics without waiting for cluster list screen visits.
     */
    suspend fun loadAllClustersLightweight(): List<WorkoutCluster> = withContext(Dispatchers.IO) {
        lightweightLoadMutex.withLock {
            val cached = clusterDb.getAllClusters()
            if (cached.isNotEmpty()) {
                val normalized = healUntrimmedClusterNames(cached)
                val stats = if (_clusterStats.value.isEmpty()) {
                    try {
                        summariesManager.getWorkoutClusterStatsForAllClusters().also {
                            _clusterStats.value = it
                        }
                    } catch (t: Throwable) {
                        if (DEBUG) runCatching { android.util.Log.w("WorkoutClusterRepo", "Failed to hydrate cluster stats in lightweight load: ${t.message}") }
                        emptyMap()
                    }
                } else {
                    _clusterStats.value
                }

                // Self-heal clusters with desynchronized hitCount (REQ-UI-493.1, ATT-5846)
                val healed = normalized.map { cluster ->
                    val statCount = stats[cluster.id]?.workoutCount ?: 0
                    if (cluster.hitCount == 0 && statCount > 0) {
                        val updated = cluster.copy(hitCount = statCount)
                        try {
                            clusterDb.updateCluster(updated)
                        } catch (t: Throwable) {
                            if (DEBUG) runCatching { android.util.Log.w("WorkoutClusterRepo", "Failed to heal cluster hitCount: ${t.message}") }
                        }
                        updated
                    } else {
                        cluster
                    }
                }

                val sorted = healed.sortedByDescending { it.hitCount }
                _allClusters.value = sorted
                sorted
            } else {
                emptyList()
            }
        }
    }

    companion object {
        private const val SP_KEY_LAST_BOUNDS_REPAIR = "last_cluster_bounds_repair_v5"
        const val SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"
        @Volatile
        private var instance: WorkoutClusterRepository? = null

        @JvmStatic
        fun getInstance(context: Context): WorkoutClusterRepository {
            return getInstance(context, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        }

        @JvmStatic
        fun getInstance(context: Context, scope: CoroutineScope): WorkoutClusterRepository {
            return instance ?: synchronized(this) {
                instance ?: WorkoutClusterRepository(context.applicationContext, scope).also { instance = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(testInstance: WorkoutClusterRepository? = null) {
            instance = testInstance
        }

        /**
         * Authoritative batch key extraction mapping open cursor to [WorkoutBatchKeys].
         * Delegates to [WorkoutClusterDatabaseManager.fromCursorBatchKeys].
         */
        @JvmStatic
        fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys =
            WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)
    }

    suspend fun refreshClusters(forceShowProgress: Boolean = false, forceCheckIntegrity: Boolean = false): Unit = withContext(Dispatchers.IO) {
        var currentClusters = clusterDb.getAllClusters()
        
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
            currentClusters = clusterDb.getAllClusters()
        }

        // 0.1 Point-to-point Apex Self-Healing Migration (REQ-DAT-039, ATT-6319, ATT-6686)
        val lastApexRepair = prefs.getInt(SP_KEY_LAST_APEX_REPAIR, 0)
        if (lastApexRepair < 2 && currentClusters.isNotEmpty()) {
            val engine = WorkoutClusterEngine.getInstance(context)
            engine.healLegacyPointToPointApexes(context)
            prefs.edit().putInt(SP_KEY_LAST_APEX_REPAIR, 2).apply()
            currentClusters = clusterDb.getAllClusters()
        }

        // 0.2 Whitespace Self-Healing Pass (REQ-PER-041.2, ATT-7757)
        currentClusters = healUntrimmedClusterNames(currentClusters)

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
        
        if (!needsEnrichment && !forceShowProgress && !forceCheckIntegrity) {
            _clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()
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

            // Check actual counts from WorkoutSummaries (surviving workouts)
            val realCount = actualCounts[cluster.id] ?: 0
            
            // Check for explicit route link to preserve imported/created routes (REQ-SET-062 Invariant)
            val linkedRoute = routesDb.getRouteByClusterId(cluster.id)
            val isRouteLinked = linkedRoute != null || !cluster.routePolyline.isNullOrEmpty()

            if (realCount == 0 && !isRouteLinked) {
                if (DEBUG) android.util.Log.i("WorkoutClusterRepo", "Purging unlinked zero-workout orphan cluster: ${cluster.name} (id: ${cluster.id})")
                clusterDb.deleteCluster(cluster.id)
                return@mapIndexed null
            }

            // For surviving workout clusters, keep counters untouched (ATT-296: bulk deletion preserves lifetime hitCount)
            val preservedHitCount = if (realCount > 0) maxOf(cluster.hitCount, realCount) else 0
            val updatedCluster = if (cluster.hitCount != preservedHitCount || (realCount == 0 && cluster.previewPaths.isNotEmpty())) {
                if (DEBUG && cluster.hitCount != preservedHitCount) {
                    android.util.Log.i("WorkoutClusterRepo", "Updating hit count for ${cluster.name}: ${cluster.hitCount} -> $preservedHitCount")
                }
                cluster.copy(
                    hitCount = preservedHitCount,
                    previewPaths = if (realCount == 0) emptyList() else cluster.previewPaths
                )
            } else cluster

            // --- POPULATE PREVIEW PATHS (SCRUM-224 / ATT-441) ---
            var previewPaths = updatedCluster.previewPaths.toMutableList()
            var routePolyline = updatedCluster.routePolyline
            
            if (previewPaths.isEmpty() && updatedCluster.hitCount > 0) {
                // 1. Check for linked route
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
        
        _clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()
        _allClusters.value = enriched.filterNotNull().sortedByDescending { it.hitCount }
        if (showProgress) {
            _migrationStatus.value = null
        }
    }

    /**
     * Asynchronously refreshes clusters and stats on the repository scope (Dispatchers.IO) (ATT-7754 / REQ-SET-059).
     */
    fun refreshClustersAsync() {
        repositoryScope.launch(Dispatchers.IO) {
            refreshClusters()
        }
    }

    suspend fun refreshClusterStats(): Unit = withContext(Dispatchers.IO) {
        _clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()
    }

    suspend fun getStatsForCluster(clusterId: Long): WorkoutClusterStats = withContext(Dispatchers.IO) {
        _clusterStats.value[clusterId] ?: summariesManager.getWorkoutClusterStats(clusterId)
    }

    suspend fun getClusterById(clusterId: Long): WorkoutCluster? = withContext(Dispatchers.IO) {
        _allClusters.value.find { it.id == clusterId } ?: clusterDb.getClusterById(clusterId)
    }

    /**
     * Self-heals untrimmed or blank persisted cluster names in-place with collision resolution,
     * propagating healed names to linked routes, [WorkoutRepository], and [clusterMutationEvents]
     * (REQ-PER-041.2).
     */
    private suspend fun healUntrimmedClusterNames(
        clusters: List<WorkoutCluster>
    ): List<WorkoutCluster> {
        if (clusters.none { it.name != it.name.trim() || it.name.isBlank() }) return clusters

        val working = clusters.toMutableList()
        val healedChanges = mutableListOf<Pair<Long, String>>()
        for (index in working.indices) {
            val cluster = working[index]
            val trimmed = cluster.name.trim()
            if (trimmed == cluster.name && trimmed.isNotEmpty()) continue

            val baseName = trimmed.ifEmpty { "Cluster ${cluster.id}" }
            val otherNames = working.asSequence()
                .filter { it.id != cluster.id }
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val uniqueName = WorkoutClusterEngine.resolveUniqueClusterName(baseName) { candidate ->
                candidate in otherNames
            }
            val updated = cluster.copy(name = uniqueName)
            runCatching { clusterDb.updateCluster(updated) }
                .onFailure { t ->
                    if (DEBUG) {
                        val msg = "Failed to heal cluster name: ${t.message}"
                        runCatching { android.util.Log.w("WorkoutClusterRepo", msg) }
                    }
                }
            working[index] = updated
            if (uniqueName != cluster.name) {
                healedChanges.add(cluster.id to uniqueName)
            }
        }

        for ((clusterId, healedName) in healedChanges) {
            notifyRoutesRepository { it.syncClusterNameChange(clusterId, healedName) }
            notifyWorkoutRepository { it.onClusterRenamed(clusterId, healedName) }
            _clusterMutationEvents.emit(ClusterMutationEvent.Renamed(clusterId, healedName))
        }
        return working
    }

    /**
     * Updates a [WorkoutCluster] in SQLite after trimming its name, syncs linked route name if needed,
     * updates cached [WorkoutRepository] workouts, and emits [ClusterMutationEvent.Renamed]
     * (REQ-PER-040.1, REQ-PER-040.2, REQ-PER-041.1).
     */
    suspend fun updateCluster(cluster: WorkoutCluster) = withContext(Dispatchers.IO) {
        val trimmedName = cluster.name.trim()
        if (trimmedName.isEmpty()) return@withContext
        val sanitized = cluster.copy(name = trimmedName)
        clusterDb.updateCluster(sanitized)
        refreshClusters()
        notifyRoutesRepository { it.syncClusterNameChange(sanitized.id, sanitized.name) }
        notifyWorkoutRepository { it.onClusterRenamed(sanitized.id, sanitized.name) }
        _clusterMutationEvents.emit(ClusterMutationEvent.Renamed(sanitized.id, sanitized.name))
    }

    /**
     * Propagates a cluster rename that originated from a linked route rename in [RoutesRepository],
     * refreshing [_allClusters], updating [WorkoutRepository], and emitting [ClusterMutationEvent.Renamed]
     * without re-invoking [RoutesRepository] to prevent recursion (REQ-PER-040.2).
     */
    suspend fun onRouteSyncedClusterRename(clusterId: Long) = withContext(Dispatchers.IO) {
        if (clusterId == -1L) return@withContext
        val updatedCluster = clusterDb.getClusterById(clusterId) ?: return@withContext
        refreshClusters()
        notifyWorkoutRepository { it.onClusterRenamed(clusterId, updatedCluster.name) }
        _clusterMutationEvents.emit(ClusterMutationEvent.Renamed(clusterId, updatedCluster.name))
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

    /**
     * Triggers asynchronous recalculation of clusters from history on the persistent repository scope (ATT-3452, REQ-SET-075).
     * Optionally updates and persists cluster tuning preferences prior to execution.
     * Enforces a domain-layer lock via [AtomicBoolean] to reject concurrent recalculations.
     * Decoupled from transient UI scopes to prevent cancellation if the user navigates away.
     */
    fun recalculateClustersAsync(
        endpointTol: Float? = null,
        apexTol: Float? = null,
        distTol: Float? = null,
        altPosTol: Float? = null,
        useSport: Boolean? = null,
        useAlt: Boolean? = null,
        scoreThreshold: Float? = null
    ) {
        if (!isRecalculatingLock.compareAndSet(false, true)) {
            if (DEBUG) android.util.Log.w("WorkoutClusterRepo", "Recalculation already active. Rejecting concurrent trigger.")
            return
        }
        repositoryScope.launch {
            try {
                if (endpointTol != null || apexTol != null || distTol != null || altPosTol != null ||
                    useSport != null || useAlt != null || scoreThreshold != null
                ) {
                    com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
                        .getInstance(context)
                        .updateClusterTuning(
                            endpointTol = endpointTol,
                            apexTol = apexTol,
                            distTol = distTol,
                            altPosTol = altPosTol,
                            useSport = useSport,
                            useAlt = useAlt,
                            scoreThreshold = scoreThreshold
                        )
                }
                recalculateClustersWithProgress()
            } catch (t: Throwable) {
                android.util.Log.e("WorkoutClusterRepo", "Error during cluster recalculation", t)
            } finally {
                isRecalculatingLock.set(false)
                _migrationStatus.value = null
            }
        }
    }

    /**
     * Deletes a [WorkoutCluster], clears links in workouts and routes, refreshes caches, updates
     * in-memory [WorkoutRepository] workouts, and emits [ClusterMutationEvent.Deleted] (REQ-PER-040.1).
     */
    suspend fun deleteCluster(clusterId: Long) = withContext(Dispatchers.IO) {
        // 1. Clear links in workouts
        summariesManager.clearClusterLink(clusterId)

        // 2. Clear links in routes
        RoutesDatabaseManager.getInstance(context).clearClusterLink(clusterId)

        // 3. Delete the cluster itself
        clusterDb.deleteCluster(clusterId)

        // 4. Refresh & propagate in-memory
        refreshClusters()
        notifyRoutesRepository { it.refreshRoutes() }
        notifyWorkoutRepository { it.onClusterDeleted(clusterId) }
        _clusterMutationEvents.emit(ClusterMutationEvent.Deleted(clusterId))
    }

    /**
     * Merges a source cluster into a target cluster, refreshes cluster and route flows,
     * updates [WorkoutRepository], and emits [ClusterMutationEvent.Merged] (ATT-5666 / REQ-SET-089 / REQ-PER-040.1).
     */
    suspend fun mergeClusters(sourceId: Long, targetId: Long): Boolean = withContext(Dispatchers.IO) {
        val engine = WorkoutClusterEngine.getInstance(context)
        val targetNameBeforeMerge = clusterDb.getClusterById(targetId)?.name
        val success = engine.mergeClusters(context, sourceId, targetId)
        if (success) {
            refreshClusters()
            val resolvedTargetName = clusterDb.getClusterById(targetId)?.name ?: targetNameBeforeMerge ?: ""
            notifyRoutesRepository { it.refreshRoutes() }
            notifyWorkoutRepository { it.onClusterMerged(sourceId, targetId, resolvedTargetName) }
            _clusterMutationEvents.emit(ClusterMutationEvent.Merged(sourceId, targetId, resolvedTargetName))
        }
        success
    }

    /**
     * Splits selected route variants out of [primaryClusterId], refreshes cluster caches, reloads
     * affected workouts in [WorkoutRepository], and emits [ClusterMutationEvent.Split] (REQ-PER-040.1).
     */
    suspend fun splitCluster(
        primaryClusterId: Long,
        variantsToSplit: List<Pair<String, List<WorkoutData>>>,
        renameWorkouts: Boolean = true
    ): List<Long> = withContext(Dispatchers.IO) {
        val snapshotVariants = variantsToSplit.map { (name, workouts) ->
            name to workouts.map { w ->
                ClusterWorkoutSnapshot(
                    id = w.id,
                    finished = w.finished,
                    clusterId = w.clusterId,
                    sportId = w.sportId,
                    workoutName = w.workoutName,
                    totalDistance = w.totalDistance,
                    startTimeS = w.startTimeS,
                    mapPolyline = w.mapPolyline,
                    startLatLng = w.startLatLng,
                    endLatLng = w.endLatLng,
                    maxDisplacementLatLng = w.maxDisplacementLatLng,
                    minLat = w.minLat,
                    minLng = w.minLng,
                    maxLat = w.maxLat,
                    maxLng = w.maxLng,
                    minAltitudeLatLng = w.minAltitudeLatLng,
                    maxAltitudeLatLng = w.maxAltitudeLatLng
                )
            }
        }
        val createdClusterIds = WorkoutClusterEngine.getInstance(context).splitCluster(
            context = context,
            primaryClusterId = primaryClusterId,
            variantsToSplit = snapshotVariants,
            renameWorkouts = renameWorkouts
        )
        refreshClusters()
        val affectedWorkoutIds = variantsToSplit.flatMap { it.second }.map { it.id }.distinct()
        if (createdClusterIds.isNotEmpty() && affectedWorkoutIds.isNotEmpty()) {
            notifyWorkoutRepository { it.reloadWorkouts(affectedWorkoutIds) }
            _clusterMutationEvents.emit(
                ClusterMutationEvent.Split(
                    primaryClusterId = primaryClusterId,
                    createdClusterIds = createdClusterIds,
                    affectedWorkoutIds = affectedWorkoutIds
                )
            )
        }
        createdClusterIds
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
        val db = summariesManager.database
        
        db.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
            null, selection, args, null, null, 
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START} DESC"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext emptyList<WorkoutData>()

            // 1. Gather raw snapshots in a single forward pass (ATT-6038)
            val snapshots = mutableListOf<WorkoutDataMapper.RawCursorSnapshot>()
            do {
                snapshots.add(mapper.readCursorSnapshot(cursor))
            } while (cursor.moveToNext())

            // 2. Perform complete Batch Metadata lookup via shared BatchMetadataLoader
            val batchMetadata = batchMetadataLoader.loadForSnapshots(snapshots)

            // 3. Map everything using pre-fetched metadata without N+1 queries
            for (snapshot in snapshots) {
                workouts.add(mapper.fromSnapshot(snapshot, batchMetadata, reconcile = false))
            }
        }
        workouts
    }

    suspend fun getWorkoutTrackPoints(workoutId: Long, trackType: TrackType): List<PathPoint> {
        return WorkoutRepository.getInstance(context).getWorkoutTrackPoints(workoutId, trackType)
    }

    fun getSportName(sportId: Long): String {
        return SportTypesRepository.getInstance(context).getUIName(sportId)
    }

    fun getBSportType(sportId: Long): com.atrainingtracker.banalservice.BSportType {
        return SportTypesRepository.getInstance(context).getBSportType(sportId)
    }

    /**
     * Returns whether [workout] has the spatial fingerprint endpoints required for
     * GPS similarity scoring and new-cluster creation (`REQ-UI-2786.3`).
     */
    fun hasSpatialFingerprint(workout: WorkoutData): Boolean =
        workout.startLatLng != null && workout.endLatLng != null && workout.maxDisplacementLatLng != null

    /**
     * Returns candidate route clusters for [workout], scoring by spatial similarity when GPS
     * extrema are present and falling back to all known clusters with [Double.NaN] scores
     * for indoor/non-GPS workouts (`REQ-UI-2786.3`).
     */
    fun getCandidateClustersForWorkout(workout: WorkoutData): List<Pair<WorkoutCluster, Double>> {
        val start = workout.startLatLng
        val end = workout.endLatLng
        val apex = workout.maxDisplacementLatLng
        if (start != null && end != null && apex != null) {
            return WorkoutClusterEngine.getInstance(context)
                .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName, workout.bSportType)
        }
        return getAllClustersSync()
            .sortedWith(
                compareByDescending<WorkoutCluster> { it.bSportType == workout.bSportType }
                    .thenBy { it.name.lowercase() }
            )
            .map { it to Double.NaN }
    }

    /**
     * Moves a GPS workout or assigns an indoor/non-GPS workout to [targetClusterId],
     * refreshing cluster state and reloading workout metadata without modifying `WorkoutRepository.kt` (`REQ-UI-2786.3`).
     */
    suspend fun moveOrAssignWorkoutToCluster(workout: WorkoutData, targetClusterId: Long): String? =
        withContext(Dispatchers.IO) {
            if (workout.clusterId == targetClusterId) return@withContext getClusterNameById(targetClusterId)
            val engine = WorkoutClusterEngine.getInstance(context)
            if (hasSpatialFingerprint(workout)) {
                engine.moveWorkoutToCluster(context, workout.id, workout.clusterId, targetClusterId)
            } else {
                engine.assignClusterToWorkout(context, workout.id, targetClusterId, forceIdentity = true)
            }
            refreshClusters()
            notifyWorkoutRepository { repo ->
                repo.reloadWorkoutData(workout.id)
                repo.loadWorkout(workout.id)
            }
            getClusterNameById(targetClusterId)
        }

    /**
     * Creates a new favorite route cluster from [workout]'s spatial fingerprint and refreshes state (`REQ-UI-2786.3`).
     */
    suspend fun createNewClusterFromWorkout(workout: WorkoutData): Pair<Long, String?> =
        withContext(Dispatchers.IO) {
            val newClusterId = WorkoutClusterEngine.getInstance(context)
                .createNewClusterFromWorkout(context, workout.toClusterWorkoutSnapshot())
            if (newClusterId > 0L) {
                refreshClusters()
                notifyWorkoutRepository { repo ->
                    repo.reloadWorkoutData(workout.id)
                    repo.loadWorkout(workout.id)
                }
                newClusterId to getClusterNameById(newClusterId)
            } else {
                -1L to null
            }
        }

    /**
     * Unassigns [workoutId] from its current favorite route cluster and refreshes state (`REQ-UI-2786.3`).
     */
    suspend fun unassignWorkoutFromCluster(workoutId: Long) = withContext(Dispatchers.IO) {
        WorkoutClusterEngine.getInstance(context)
            .unassignClusterFromWorkout(context, workoutId)
        refreshClusters()
        notifyWorkoutRepository { repo ->
            repo.reloadWorkoutData(workoutId)
            repo.loadWorkout(workoutId)
        }
    }

    private val databaseManager: WorkoutClusterDatabaseManager get() = clusterDb
    fun getAllClustersSync(): List<WorkoutCluster> = clusterDb.getAllClusters()
    fun getClusterByIdSync(id: Long): WorkoutCluster? = clusterDb.getClusterById(id)
    fun getClusterByName(name: String): WorkoutCluster? = clusterDb.getClusterByName(name)
    fun getClusterNameById(id: Long): String? = clusterDb.getClusterNameById(id)
    fun insertCluster(cluster: WorkoutCluster): Long = clusterDb.insertCluster(cluster)
    fun updateClusterSync(cluster: WorkoutCluster) {
        val trimmedName = cluster.name.trim()
        if (trimmedName.isEmpty()) return
        clusterDb.updateCluster(cluster.copy(name = trimmedName))
    }
    fun deleteClusterSync(id: Long) = clusterDb.deleteCluster(id)
    fun deleteAllClustersSync() = clusterDb.deleteAllClusters()
    fun isNameTaken(name: String, excludeId: Long = -1): Boolean = clusterDb.isNameTaken(name, excludeId)

    fun <T> runInTransaction(block: () -> T): T {
        return clusterDb.getDatabase().inTransaction { block() }
    }
}
