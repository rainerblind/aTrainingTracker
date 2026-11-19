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

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.util.MigrationStatus
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dedicated Robolectric real-SQLite persistence suite verifying that cluster preview paths
 * enriched by [WorkoutClusterRepository.refreshClusters] persist across simulated app restarts
 * without triggering redundant enrichment progress (REQ-PER-006, TST-PERF-010, ATT-8324).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutClusterPreviewPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        every { TrainingApplication.getMinWalkSpeed_mps() } returns 0.5
        every { TrainingApplication.getMaxWalkSpeed_mps() } returns 2.0
        every { TrainingApplication.getMaxRunSpeed_mps() } returns 5.0
        every { TrainingApplication.getMaxMTBSpeed_mps() } returns 8.0
        every { TrainingApplication.getMaxBikeSpeed_mps() } returns 12.0

        resetSingletonsAndDeleteDatabases()

        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt("last_cluster_bounds_repair_v5", 5)
            .putInt(WorkoutClusterRepository.SP_KEY_LAST_APEX_REPAIR, 2)
            .commit()
    }

    @After
    fun tearDown() {
        resetSingletonsAndDeleteDatabases()
        unmockkAll()
    }

    private fun resetSingletonsAndDeleteDatabases() {
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        context.deleteDatabase("RouteClusters.db")
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
        context.deleteDatabase(RoutesDatabaseManager.RoutesDbHelper.DB_NAME)
    }

    /**
     * TST-PERF-010 (REQ-PER-006): Persistent Previews Audit.
     * Seeds SQLite with an un-enriched cluster and workout polyline, enriches previews on first
     * run, resets repository & DB helper singletons to simulate restart, and verifies persisted
     * previewPaths reload silently (migrationStatus == null).
     */
    @Test
    fun refreshClusters_persistsEnrichedPreviewsToSqlite_andReloadsSilentlyAfterRestart_TST_PERF_010() = runBlocking {
        val encodedPoly = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val clusterDb = WorkoutClusterDatabaseManager.getInstance(context)
        val clusterId = clusterDb.insertCluster(
            WorkoutCluster(
                name = "Test Valley Loop",
                probableSportId = 1L,
                startLat = 48.0,
                startLng = 9.0,
                endLat = 48.0,
                endLng = 9.0,
                maxDispLat = 48.1,
                maxDispLng = 9.1,
                refDistance = 12_000.0,
                hitCount = 1,
                bSportType = BSportType.BIKE,
                previewPaths = emptyList()
            )
        )
        assertTrue(clusterId > 0L)

        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Valley Ride")
            put(WorkoutSummaries.FILE_BASE_NAME, "valley_ride_1")
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.TIME_START, "2026-01-15 10:00:00")
            put(WorkoutSummaries.FINISHED, 1)
            put(WorkoutSummaries.CLUSTER_ID, clusterId)
            put(WorkoutSummaries.MAP_POLYLINE, encodedPoly)
        }
        val workoutId = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, cv)
        assertTrue(workoutId > 0L)

        // First launch: enriches missing previewPaths and persists them in RouteClusters.db
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = WorkoutClusterRepository(context, scope1)
        val coldStatuses = mutableListOf<MigrationStatus?>()
        val coldJob = async(start = CoroutineStart.UNDISPATCHED) {
            repo1.migrationStatus.collect { coldStatuses.add(it) }
        }
        repo1.refreshClusters()
        coldJob.cancel()
        scope1.cancel()

        assertTrue("Initial enrichment must emit progress on cold preview population", coldStatuses.filterNotNull().isNotEmpty())
        assertEquals(listOf(encodedPoly), repo1.allClusters.value.first { it.id == clusterId }.previewPaths)

        // Verify persisted directly in SQLite before resetting singletons
        val persistedRows = WorkoutClusterDatabaseManager.getInstance(context).getAllClusters()
        assertEquals(listOf(encodedPoly), persistedRows.first { it.id == clusterId }.previewPaths)

        // Simulated restart: clear in-memory singletons while preserving SQLite files on disk
        WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = WorkoutClusterRepository(context, scope2)
        val restartStatuses = mutableListOf<MigrationStatus?>()
        val restartJob = async(start = CoroutineStart.UNDISPATCHED) {
            repo2.migrationStatus.collect { restartStatuses.add(it) }
        }
        repo2.refreshClusters()
        restartJob.cancel()
        scope2.cancel()

        assertTrue(
            "Restart with persisted previews must NOT emit enrichment progress (TST-PERF-010)",
            restartStatuses.filterNotNull().isEmpty()
        )
        assertNull(repo2.migrationStatus.value)
        assertEquals(listOf(encodedPoly), repo2.allClusters.value.first { it.id == clusterId }.previewPaths)
    }
}
