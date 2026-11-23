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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectWorkoutSyncWorker
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.dropbox.core.oauth.DbxCredential
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [AutoImportManager] verifying zero-I/O pre-download deduplication,
 * non-interactive parsing, Health Connect downstream relay, and repository refresh (REQ-MIG-052, TST-MIG-050.2).
 */
class AutoImportManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockPeriodsRepo: PeriodsRepository
    private lateinit var mockClusterRepo: WorkoutClusterRepository
    private lateinit var tempCacheDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "test_auto_import_cache_${System.currentTimeMillis()}").apply { mkdirs() }

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.cacheDir } returns tempCacheDir

        mockWorkoutRepo = mockk(relaxed = true)
        mockPeriodsRepo = mockk(relaxed = true)
        mockClusterRepo = mockk(relaxed = true)

        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any<Context>()) } returns mockWorkoutRepo

        mockkObject(PeriodsRepository.Companion)
        every { PeriodsRepository.getInstance(any<Context>()) } returns mockPeriodsRepo

        mockkObject(WorkoutClusterRepository.Companion)
        every { WorkoutClusterRepository.getInstance(any<Context>()) } returns mockClusterRepo

        mockkObject(HealthConnectWorkoutSyncWorker.Companion)
        every { HealthConnectWorkoutSyncWorker.enqueue(any(), any()) } just Runs

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isAutoImportEnabled() } returns true
        every { TrainingApplication.isAutoImportDropboxEnabled() } returns true
        every { TrainingApplication.isAutoImportGoogleDriveEnabled() } returns false
        every { TrainingApplication.uploadToDropbox() } returns true
        val mockDbxCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockDbxCredential
        every { TrainingApplication.getAutoImportFormat() } returns "fit"
        every { TrainingApplication.isSyncImportedWorkoutsToHealthConnect() } returns true
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getAutoImportLastSyncTime() } returns 0L
        every { TrainingApplication.setAutoImportLastSyncTime(any()) } just Runs
        every { TrainingApplication.setAutoImportLastSyncResult(any()) } just Runs

        coEvery { mockWorkoutRepo.loadAllWorkouts() } returns Unit
        coEvery { mockClusterRepo.refreshClusters(any(), any()) } returns Unit

        mockkObject(LegacyImportEngine)

        AutoImportManager.resetForTesting()
    }

    @After
    fun tearDown() {
        AutoImportManager.resetForTesting()
        unmockkAll()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testPreDownloadDeduplicationAndIncrementalSync_TST_MIG_050_2() = runBlocking {
        // 1. Preconditions: Snapshot has "workout_existing", cloud folder has "workout_existing.fit" and "workout_new.fit"
        val existingSnapshot = ExistingWorkoutsSnapshot(
            initialBaseNames = setOf("workout_existing")
        )
        AutoImportManager.snapshotProvider = { existingSnapshot }

        val remoteFiles = listOf(
            AutoImportManager.CloudFileItem("id1", "workout_existing.fit", "/FIT/workout_existing.fit"),
            AutoImportManager.CloudFileItem("id2", "workout_new.fit", "/FIT/workout_new.fit")
        )
        AutoImportManager.dropboxLister = { _, _, _ -> remoteFiles }

        val downloadedItems = mutableListOf<String>()
        AutoImportManager.dropboxDownloader = { _, item, targetFile ->
            downloadedItems.add(item.name)
            targetFile.writeText("fake fit bytes")
            true
        }

        coEvery {
            LegacyImportEngine.importFromFitResult(any(), any(), null, false)
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 999L)

        // 2. Action: Run incremental sync
        val result = AutoImportManager.syncIncremental(mockContext)

        // 3. Expected Results:
        // - Existing file was filtered before download (0 bytes downloaded for existing)
        assertEquals(listOf("workout_new.fit"), downloadedItems)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.failedCount)

        // - Snapshot registered newly imported workout
        assertTrue(existingSnapshot.containsFileBaseName("workout_new"))

        // - Downstream relay to Health Connect was triggered for workout 999
        verify(exactly = 1) { HealthConnectWorkoutSyncWorker.enqueue(mockContext, 999L) }

        // - Repositories were refreshed (PeriodsRepository projects reactively from WorkoutRepository.allWorkouts)
        coVerify(exactly = 1) { mockWorkoutRepo.loadAllWorkouts() }
        coVerify(exactly = 1) { mockClusterRepo.refreshClusters() }
    }

    @Test
    fun testSyncWhenAutoImportDisabledReturnsEarly() = runBlocking {
        every { TrainingApplication.isAutoImportEnabled() } returns false

        val result = AutoImportManager.syncIncremental(mockContext)

        assertEquals(0, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.failedCount)
        assertEquals("Auto-import is disabled", result.errorMessage)
    }

    @Test
    fun testCooldownSuppressesImmediateRepeat() {
        val now = System.currentTimeMillis()
        every { TrainingApplication.getAutoImportLastSyncTime() } returns now - 1000L // 1s ago, within 15m cooldown

        AutoImportManager.triggerSyncIfNeeded(mockContext)

        assertEquals(false, AutoImportManager.isSyncing())
    }

    @Test
    fun testSyncIncremental_installsBulkImportBatchContextAndRefreshesClimbsOnceWhenClimbsPersisted() = runBlocking {
        // TST-STB-021.3: AutoImportManager.syncIncremental runs inside BulkImportBatchContext
        // and calls LiveClimbsRepository.refreshClimbs() once post-batch when climbs were persisted.
        val mockLiveClimbsRepo = mockk<com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository>(relaxed = true)
        com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository.resetForTesting(mockLiveClimbsRepo)
        try {
            AutoImportManager.snapshotProvider = { ExistingWorkoutsSnapshot(initialBaseNames = emptySet()) }
            AutoImportManager.dropboxLister = { _, _, _ ->
                listOf(
                    AutoImportManager.CloudFileItem("id1", "climb_1.fit", "/FIT/climb_1.fit"),
                    AutoImportManager.CloudFileItem("id2", "climb_2.fit", "/FIT/climb_2.fit")
                )
            }
            AutoImportManager.dropboxDownloader = { _, _, targetFile ->
                targetFile.writeText("fit")
                true
            }

            val batchSeen = mutableListOf<Boolean>()
            coEvery {
                LegacyImportEngine.importFromFitResult(any(), any(), null, false)
            } coAnswers {
                val batch = kotlin.coroutines.coroutineContext[BulkImportBatchContext]
                batchSeen.add(batch != null)
                batch?.markClimbsPersisted()
                LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 1001L)
            }

            val result = AutoImportManager.syncIncremental(mockContext)

            assertEquals(2, result.importedCount)
            assertEquals(listOf(true, true), batchSeen)
            coVerify(exactly = 1) { mockWorkoutRepo.loadAllWorkouts() }
            coVerify(exactly = 1) { mockClusterRepo.refreshClusters() }
            coVerify(exactly = 1) { mockLiveClimbsRepo.refreshClimbs() }
        } finally {
            com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository.resetForTesting(null)
        }
    }
}
