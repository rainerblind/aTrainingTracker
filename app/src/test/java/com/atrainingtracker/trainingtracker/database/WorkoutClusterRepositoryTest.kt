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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [WorkoutClusterRepository.refreshClusters] logic:
 * - Surviving workout clusters keep their lifetime hitCount untouched (ATT-296).
 * - Unlinked zero-workout orphan clusters are automatically purged (REQ-SET-062).
 * - Route-linked clusters are preserved even when zero workouts remain (REQ-SET-062).
 */
// Linked test specs: TST-SET-048
class WorkoutClusterRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockSummariesDb: SQLiteDatabase
    private lateinit var mockSummariesManager: WorkoutSummariesDatabaseManager
    private lateinit var mockClusterDb: WorkoutClusterDatabaseManager
    private lateinit var mockRoutesDb: RoutesDatabaseManager
    private lateinit var mockSportDb: SportTypeDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockSummariesManager = mockk(relaxed = true)
        mockClusterDb = mockk(relaxed = true)
        mockRoutesDb = mockk(relaxed = true)
        mockSportDb = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getInt(any(), any()) } returns 10 // avoid repair pass

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getString(R.string.cluster_migration_title) } returns "Cluster Migration"
        every { mockContext.getString(R.string.cluster_migration_healing) } returns "Healing"

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        mockkStatic(SportTypeDatabaseManager::class)

        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        RoutesDatabaseManager.resetForTesting(mockRoutesDb)

        every { mockSummariesManager.database } returns mockSummariesDb
        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN

        WorkoutClusterRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testRefreshClusters_survivingCluster_preservesHitCountUntouched() = runBlocking {
        val clusterId = 100L
        val survivingCluster = WorkoutCluster(
            id = clusterId,
            name = "Historic Loop",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 8000.0,
            hitCount = 25, // Lifetime hitCount is 25
            bSportType = BSportType.RUN,
            previewPaths = listOf("polyline_surviving")
        )

        every { mockClusterDb.getAllClusters() } returns listOf(survivingCluster)
        every { mockRoutesDb.getRouteByClusterId(clusterId) } returns null

        // Phase 1: mock cursor returning count of 5 surviving sessions for clusterId
        val mockCountCursor = mockk<Cursor>(relaxed = true)
        var cursorAdvanced = false
        every { mockCountCursor.moveToNext() } answers {
            if (!cursorAdvanced) {
                cursorAdvanced = true
                true
            } else {
                false
            }
        }
        every { mockCountCursor.getLong(0) } returns clusterId
        every { mockCountCursor.getInt(1) } returns 5 // 5 surviving workouts
        every { mockCountCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, "COUNT(*)"),
                any(), any(), any(), any(), any()
            )
        } returns mockCountCursor

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters(forceShowProgress = false, forceCheckIntegrity = true)

        // Verify clusterDb does NOT update cluster with a reduced hitCount
        verify(exactly = 0) {
            mockClusterDb.updateCluster(match { it.id == clusterId && it.hitCount < 25 })
        }
        // Verify cluster was NOT deleted
        verify(exactly = 0) {
            mockClusterDb.deleteCluster(clusterId)
        }

        // Verify exposed list retains lifetime hitCount = 25
        val clusters = repo.allClusters.value
        assertEquals(1, clusters.size)
        assertEquals(25, clusters[0].hitCount)
    }

    @Test
    fun testRefreshClusters_unlinkedZeroWorkoutOrphan_purgesCluster() = runBlocking {
        val orphanClusterId = 101L
        val orphanCluster = WorkoutCluster(
            id = orphanClusterId,
            name = "Purged Route",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 5000.0,
            hitCount = 3,
            bSportType = BSportType.RUN,
            routePolyline = null
        )

        every { mockClusterDb.getAllClusters() } returns listOf(orphanCluster)
        every { mockRoutesDb.getRouteByClusterId(orphanClusterId) } returns null

        // Phase 1: mock cursor returning empty (0 surviving workouts for this cluster)
        val mockEmptyCursor = mockk<Cursor>(relaxed = true)
        every { mockEmptyCursor.moveToNext() } returns false
        every { mockEmptyCursor.close() } returns Unit

        every {
            mockSummariesDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID, "COUNT(*)"),
                any(), any(), any(), any(), any()
            )
        } returns mockEmptyCursor

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        repo.refreshClusters(forceShowProgress = false, forceCheckIntegrity = true)

        // Verify orphan cluster with 0 actual workouts and no route is deleted (REQ-SET-062)
        verify {
            mockClusterDb.deleteCluster(orphanClusterId)
        }
        assertEquals(0, repo.allClusters.value.size)
    }

    @Test
    fun testLoadAllClustersLightweight_populatesAllClustersWithoutHeavyAggregation() = runBlocking {
        val cluster = WorkoutCluster(
            id = 55L,
            name = "Fast Loop",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 6000.0,
            hitCount = 12,
            bSportType = BSportType.BIKE
        )
        every { mockClusterDb.getAllClusters() } returns listOf(cluster)

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        val result = repo.loadAllClustersLightweight()

        assertEquals(1, result.size)
        assertEquals("Fast Loop", result.first().name)
        assertEquals(1, repo.allClusters.value.size)
        assertEquals(55L, repo.allClusters.value.first().id)
        // Verify no heavy summaries queries were executed
        verify(exactly = 0) {
            mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testLoadAllClustersLightweight_hydratesClusterStatsWhenEmpty_TST_UI_282_13() = runBlocking {
        val cluster = WorkoutCluster(
            id = 55L,
            name = "Fast Loop",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 6000.0,
            hitCount = 12,
            bSportType = BSportType.BIKE
        )
        val mockStats = WorkoutClusterStats(
            clusterId = 55L,
            workoutCount = 1,
            bestDurationSec = 1200L,
            lastHitTimestampS = 1700000000L,
            totalAscentMeters = 150L
        )
        every { mockClusterDb.getAllClusters() } returns listOf(cluster)
        every { mockSummariesManager.getWorkoutClusterStatsForAllClusters() } returns mapOf(55L to mockStats)

        val repo = WorkoutClusterRepository.getInstance(mockContext)
        assertEquals(0, repo.clusterStats.value.size)

        val result = repo.loadAllClustersLightweight()

        assertEquals(1, result.size)
        assertEquals(1, repo.clusterStats.value.size)
        assertEquals(mockStats, repo.clusterStats.value[55L])
        verify(exactly = 1) { mockSummariesManager.getWorkoutClusterStatsForAllClusters() }

        // Second call when stats are already populated should not re-query summariesManager
        repo.loadAllClustersLightweight()
        verify(exactly = 1) { mockSummariesManager.getWorkoutClusterStatsForAllClusters() }
    }

    /**
     * TST-PER-048.2: Cluster rename, merge, delete, split, and route-synced renames propagate to
     * WorkoutRepository, RoutesRepository, and emit corresponding ClusterMutationEvents (REQ-PER-040.1, REQ-PER-040.2).
     */
    @Test
    fun testClusterMutations_propagateToWorkoutRepositoryRoutesAndEmitEvents_TST_PER_048_2() = runBlocking {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        RoutesRepository.resetForTesting(mockRoutesRepo)
        WorkoutClusterEngine.resetForTesting(mockEngine)

        try {
            val cluster10 = WorkoutCluster(
                id = 10L,
                name = "Old Name",
                probableSportId = 1L,
                startLat = 48.0,
                startLng = 9.0,
                endLat = 48.1,
                endLng = 9.1,
                maxDispLat = 48.2,
                maxDispLng = 9.2,
                refDistance = 5000.0,
                hitCount = 3,
                bSportType = BSportType.RUN
            )
            val cluster20 = cluster10.copy(id = 20L, name = "Target Route")
            every { mockClusterDb.getAllClusters() } returns listOf(cluster10, cluster20)
            every { mockClusterDb.getClusterById(20L) } returns cluster20
            every { mockClusterDb.getClusterById(10L) } returns cluster10.copy(name = "Route Synced")
            every { mockEngine.mergeClusters(any(), 10L, 20L) } returns true
            every { mockEngine.splitCluster(any(), 10L, any(), true) } returns listOf(30L)

            val repo = WorkoutClusterRepository.getInstance(mockContext)

            // 1. Rename via updateCluster
            val renameEvent = async(start = CoroutineStart.UNDISPATCHED) { repo.clusterMutationEvents.first() }
            repo.updateCluster(cluster10.copy(name = "New Cluster"))
            assertEquals(ClusterMutationEvent.Renamed(10L, "New Cluster"), renameEvent.await())
            coVerify(exactly = 1) { mockRoutesRepo.syncClusterNameChange(10L, "New Cluster") }
            verify(exactly = 1) { mockWorkoutRepo.onClusterRenamed(10L, "New Cluster") }

            // 2. Merge
            val mergeEvent = async(start = CoroutineStart.UNDISPATCHED) { repo.clusterMutationEvents.first() }
            assertTrue(repo.mergeClusters(10L, 20L))
            assertEquals(ClusterMutationEvent.Merged(10L, 20L, "Target Route"), mergeEvent.await())
            coVerify(atLeast = 1) { mockRoutesRepo.refreshRoutes() }
            verify(exactly = 1) { mockWorkoutRepo.onClusterMerged(10L, 20L, "Target Route") }

            // 3. Delete
            val deleteEvent = async(start = CoroutineStart.UNDISPATCHED) { repo.clusterMutationEvents.first() }
            repo.deleteCluster(20L)
            assertEquals(ClusterMutationEvent.Deleted(20L), deleteEvent.await())
            verify(exactly = 1) { mockWorkoutRepo.onClusterDeleted(20L) }

            // 4. Split
            val splitWorkout = mockk<WorkoutData>(relaxed = true) { every { id } returns 501L }
            val splitEvent = async(start = CoroutineStart.UNDISPATCHED) { repo.clusterMutationEvents.first() }
            val createdIds = repo.splitCluster(10L, listOf("Variant A" to listOf(splitWorkout)), renameWorkouts = true)
            assertEquals(listOf(30L), createdIds)
            assertEquals(ClusterMutationEvent.Split(10L, listOf(30L), listOf(501L)), splitEvent.await())
            coVerify(exactly = 1) { mockWorkoutRepo.reloadWorkouts(listOf(501L)) }
        } finally {
            WorkoutClusterEngine.resetForTesting(null)
            RoutesRepository.resetForTesting(null)
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-PER-048.2: Route-originated rename propagates to WorkoutRepository and emits Renamed event
     * without re-invoking RoutesRepository.syncClusterNameChange (REQ-PER-040.2).
     */
    @Test
    fun testOnRouteSyncedClusterRename_propagatesWithoutCallingRoutesRepository_TST_PER_048_2() = runBlocking {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        RoutesRepository.resetForTesting(mockRoutesRepo)

        try {
            val cluster10 = WorkoutCluster(
                id = 10L,
                name = "Route Synced",
                probableSportId = 1L,
                startLat = 48.0,
                startLng = 9.0,
                endLat = 48.1,
                endLng = 9.1,
                maxDispLat = 48.2,
                maxDispLng = 9.2,
                refDistance = 5000.0,
                hitCount = 3,
                bSportType = BSportType.RUN
            )
            every { mockClusterDb.getClusterById(10L) } returns cluster10
            every { mockClusterDb.getAllClusters() } returns listOf(cluster10)

            val repo = WorkoutClusterRepository.getInstance(mockContext)
            verify(timeout = 1000, atLeast = 1) { mockClusterDb.getAllClusters() }
            repo.loadAllClustersLightweight()
            clearMocks(mockClusterDb, mockSummariesManager, answers = false)
            every { mockClusterDb.getClusterById(10L) } returns cluster10

            val routeRenameEvent = async(start = CoroutineStart.UNDISPATCHED) { repo.clusterMutationEvents.first() }
            repo.onRouteSyncedClusterRename(10L)
            assertEquals(ClusterMutationEvent.Renamed(10L, "Route Synced"), routeRenameEvent.await())
            assertEquals("Route Synced", repo.allClusters.value.first { it.id == 10L }.name)
            verify(exactly = 1) { mockWorkoutRepo.onClusterRenamed(10L, "Route Synced") }
            coVerify(exactly = 0) { mockRoutesRepo.syncClusterNameChange(any(), any()) }
            verify(exactly = 0) { mockClusterDb.getAllClusters() }
            verify(exactly = 0) { mockSummariesManager.getWorkoutClusterStatsForAllClusters() }
        } finally {
            RoutesRepository.resetForTesting(null)
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-PER-049.2: loadAllClustersLightweight self-heals untrimmed and blank persisted names
     * with collision resolution and propagates changes cleanly.
     */
    @Test
    fun testLoadAllClusters_selfHealsUntrimmedNamesWithCollision_TST_PER_049_2() = runBlocking {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        RoutesRepository.resetForTesting(mockRoutesRepo)

        try {
            val repo = WorkoutClusterRepository.getInstance(mockContext)
            verify(timeout = 1000, atLeast = 1) { mockClusterDb.getAllClusters() }
            repo.loadAllClustersLightweight()
            clearMocks(mockClusterDb, mockWorkoutRepo, mockRoutesRepo, answers = false)

            val base = WorkoutCluster(
                id = 1L, name = "Kurze Runde", probableSportId = 1L,
                startLat = 48.0, startLng = 9.0, endLat = 48.1, endLng = 9.1,
                maxDispLat = 48.2, maxDispLng = 9.2, refDistance = 5000.0,
                hitCount = 5, bSportType = BSportType.RUN
            )
            every { mockClusterDb.getAllClusters() } returns listOf(
                base,
                base.copy(id = 2L, name = "  Alte Runde  ", hitCount = 3),
                base.copy(id = 3L, name = "Kurze Runde ", hitCount = 2),
                base.copy(id = 4L, name = "   ", hitCount = 1)
            )
            every { mockSummariesManager.getWorkoutClusterStatsForAllClusters() } returns emptyMap()

            val events = mutableListOf<ClusterMutationEvent>()
            val collector = async(start = CoroutineStart.UNDISPATCHED) {
                repo.clusterMutationEvents.collect { events.add(it) }
            }
            val loaded = repo.loadAllClustersLightweight()
            collector.cancel()

            assertEquals("Kurze Runde", loaded.first { it.id == 1L }.name)
            assertEquals("Alte Runde", loaded.first { it.id == 2L }.name)
            assertEquals("Kurze Runde var 2", loaded.first { it.id == 3L }.name)
            assertEquals("Cluster 4", loaded.first { it.id == 4L }.name)
            verify(exactly = 1) { mockClusterDb.updateCluster(match { it.id == 2L && it.name == "Alte Runde" }) }
            verify(exactly = 1) {
                mockClusterDb.updateCluster(match { it.id == 3L && it.name == "Kurze Runde var 2" })
            }
            coVerify(exactly = 1) { mockRoutesRepo.syncClusterNameChange(2L, "Alte Runde") }
            verify(exactly = 1) { mockWorkoutRepo.onClusterRenamed(3L, "Kurze Runde var 2") }
            assertTrue(events.contains(ClusterMutationEvent.Renamed(2L, "Alte Runde")))
            assertTrue(events.contains(ClusterMutationEvent.Renamed(3L, "Kurze Runde var 2")))
        } finally {
            RoutesRepository.resetForTesting(null)
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-PER-049.1 / TST-PER-049.2: refreshClusters fast-path self-heals untrimmed rows and
     * updateCluster / updateClusterSync trim names and reject blank-only updates.
     */
    @Test
    fun testRefreshAndUpdate_healsTrimsAndRejectsBlank_TST_PER_049_1() = runBlocking {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        RoutesRepository.resetForTesting(mockRoutesRepo)

        try {
            val repo = WorkoutClusterRepository.getInstance(mockContext)
            verify(timeout = 1000, atLeast = 1) { mockClusterDb.getAllClusters() }
            repo.loadAllClustersLightweight()
            clearMocks(mockClusterDb, mockWorkoutRepo, mockRoutesRepo, answers = false)

            val base = WorkoutCluster(
                id = 5L, name = " Ostpark ", probableSportId = 1L,
                startLat = 48.0, startLng = 9.0, endLat = 48.1, endLng = 9.1,
                maxDispLat = 48.2, maxDispLng = 9.2, refDistance = 5000.0,
                hitCount = 4, bSportType = BSportType.RUN, previewPaths = listOf("poly")
            )
            every { mockClusterDb.getAllClusters() } returns listOf(base)
            repo.refreshClusters()

            assertEquals("Ostpark", repo.allClusters.value.first().name)
            verify(exactly = 1) { mockClusterDb.updateCluster(match { it.id == 5L && it.name == "Ostpark" }) }
            verify(exactly = 1) { mockWorkoutRepo.onClusterRenamed(5L, "Ostpark") }

            repo.updateCluster(base.copy(id = 1L, name = "   "))
            verify(exactly = 0) { mockClusterDb.updateCluster(match { it.id == 1L }) }
            repo.updateCluster(base.copy(id = 1L, name = "  Neu Benannt  "))
            verify(exactly = 1) { mockClusterDb.updateCluster(match { it.id == 1L && it.name == "Neu Benannt" }) }
            repo.updateClusterSync(base.copy(id = 1L, name = "  Sync Trim  "))
            verify(exactly = 1) { mockClusterDb.updateCluster(match { it.id == 1L && it.name == "Sync Trim" }) }
        } finally {
            RoutesRepository.resetForTesting(null)
            WorkoutRepository.resetForTesting(null)
        }
    }

    /**
     * TST-PERF-003 (REQ-PER-003) / TST-PERF-007 (REQ-PER-006):
     * Empty DB bootstrap via refreshClusters() triggers recalculateClustersWithProgress(), emitting
     * Phase 1 (routes) -> Phase 1 (1.0f) + Phase 2 (workouts) MigrationStatus and clearing to null.
     */
    @Test
    fun testRefreshClusters_emptyDbBootstrapAndRecalculate_emitsMultiPhaseProgress_TST_PERF_003_007() = runBlocking {
        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        mockkObject(WorkoutClusterEngine.Companion)
        every { WorkoutClusterEngine.getInstance(any()) } returns mockEngine

        every { mockContext.getString(R.string.cluster_migration_processing_routes) } returns "Processing routes"
        every { mockContext.getString(R.string.cluster_migration_processing_workouts, any(), any()) } returns "Processing workouts"

        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToNext() } returns false
        every { mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyCursor
        every { mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any()) } returns emptyCursor
        every { mockSummariesManager.getWorkoutClusterStatsForAllClusters() } returns emptyMap()

        val populatedCluster = WorkoutCluster(
            id = 1L,
            name = "Morning Loop",
            probableSportId = 1L,
            startLat = 48.0, startLng = 9.0, endLat = 48.0, endLng = 9.0,
            maxDispLat = 48.1, maxDispLng = 9.1, refDistance = 5000.0,
            hitCount = 3, bSportType = BSportType.RUN, previewPaths = listOf("encoded_poly")
        )
        var callCount = 0
        every { mockClusterDb.getAllClusters() } answers {
            callCount++
            if (callCount <= 2) emptyList() else listOf(populatedCluster)
        }

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = WorkoutClusterRepository(mockContext, testScope)
        val statusLog = java.util.concurrent.CopyOnWriteArrayList<MigrationStatus?>()
        val phase1Observed = java.util.concurrent.CountDownLatch(1)
        val phase2Observed = java.util.concurrent.CountDownLatch(1)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val collector = collectorScope.async(start = CoroutineStart.UNDISPATCHED) {
            repo.migrationStatus.collect { status ->
                statusLog.add(status)
                if (status != null && status.phases.size == 1 && status.phases[0].id == 1) {
                    phase1Observed.countDown()
                }
                if (status != null && status.phases.size == 2 && status.phases[1].id == 2) {
                    phase2Observed.countDown()
                }
            }
        }

        every { mockEngine.recalculateHistory(any(), any()) } answers {
            val listener = secondArg<ClusterMigrationListener>()
            listener.onPhase1Progress(1, 2)
            assertTrue(phase1Observed.await(2, java.util.concurrent.TimeUnit.SECONDS))
            listener.onPhase2Progress(3, 5)
            assertTrue(phase2Observed.await(2, java.util.concurrent.TimeUnit.SECONDS))
        }

        try {
            repo.refreshClusters()

            val nonNullStatuses = statusLog.filterNotNull()
            assertTrue(
                "Empty DB bootstrap must emit Phase 1 and Phase 2 MigrationStatus (TST-PERF-003/007)",
                nonNullStatuses.any { it.phases.size == 1 && it.phases[0].id == 1 }
            )
            assertTrue(
                "Multi-phase recalculation must emit Phase 1 complete (1.0f) + active Phase 2",
                nonNullStatuses.any {
                    it.phases.size == 2 && it.phases[0].progress == 1.0f && it.phases[1].id == 2
                }
            )
            assertNull("migrationStatus must clear to null after recalculation completes", repo.migrationStatus.value)
        } finally {
            collector.cancel()
            collectorScope.cancel()
            testScope.cancel()
        }
    }

    /**
     * TST-PERF-003 (REQ-PER-003) & TST-PERF-009 (REQ-PER-006):
     * Initial un-enriched clusters emit loading/healing/preview progress when cache is cold;
     * subsequent visits with persisted previewPaths are silent (migrationStatus == null) unless forced.
     */
    @Test
    fun testRefreshClusters_initialEnrichmentEmitsProgress_thenSilentOnRevisit_TST_PERF_003_009() = runBlocking {
        every { mockContext.getString(R.string.cluster_migration_loading) } returns "Loading"
        every { mockContext.getString(R.string.cluster_migration_previews, any(), any()) } returns "Generating previews"

        val polylineCursor = mockk<Cursor>(relaxed = true)
        every { polylineCursor.moveToNext() } returnsMany listOf(true, false, true, false)
        every { polylineCursor.getString(0) } returns "encoded_poly_1"

        val countsCursor = mockk<Cursor>(relaxed = true)
        every { countsCursor.moveToNext() } returnsMany listOf(true, false, true, false)
        every { countsCursor.getLong(0) } returns 10L
        every { countsCursor.getInt(1) } returns 4

        every {
            mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any())
        } returns countsCursor
        every {
            mockSummariesDb.query(any(), any(), any(), any(), any(), any(), any(), any())
        } returns polylineCursor

        val unenriched = WorkoutCluster(
            id = 10L, name = "Park Loop", probableSportId = 1L,
            startLat = 48.0, startLng = 9.0, endLat = 48.0, endLng = 9.0,
            maxDispLat = 48.1, maxDispLng = 9.1, refDistance = 5000.0,
            hitCount = 4, bSportType = BSportType.RUN, previewPaths = emptyList()
        )
        val enriched = unenriched.copy(previewPaths = listOf("encoded_poly_1"))

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = WorkoutClusterRepository(mockContext, testScope)
        val statusLog = mutableListOf<MigrationStatus?>()
        val collector = async(start = CoroutineStart.UNDISPATCHED) {
            repo.migrationStatus.collect { statusLog.add(it) }
        }

        try {
            // 1. Cold initial load with un-enriched cluster -> emits loading/healing/previews -> null
            every { mockClusterDb.getAllClusters() } returns listOf(unenriched)
            repo.refreshClusters(forceShowProgress = false)

            assertTrue(
                "Cold initial un-enriched load must emit MigrationStatus phases (TST-PERF-003)",
                statusLog.filterNotNull().isNotEmpty()
            )
            assertNull(repo.migrationStatus.value)

            // 2. Revisit with persisted previews -> strictly silent (0 non-null emissions)
            statusLog.clear()
            every { mockClusterDb.getAllClusters() } returns listOf(enriched)
            repo.refreshClusters(forceShowProgress = false)

            assertTrue(
                "Second visit with persisted previewPaths must stay silent (TST-PERF-009)",
                statusLog.filterNotNull().isEmpty()
            )

            // 3. Explicit forceShowProgress = true DOES emit progress
            statusLog.clear()
            repo.refreshClusters(forceShowProgress = true)
            assertTrue(
                "Forced refresh must emit progress even when previews exist (TST-PERF-009)",
                statusLog.filterNotNull().isNotEmpty()
            )
            assertNull(repo.migrationStatus.value)
        } finally {
            collector.cancel()
            testScope.cancel()
        }
    }
}
