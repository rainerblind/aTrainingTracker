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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    @Test
    fun testSyncIncremental_rejectsTraversalFilenamesBeforeDownloadAndImportsValidSibling_TST_MIG_061_3() = runBlocking {
        AutoImportManager.snapshotProvider = { ExistingWorkoutsSnapshot(initialBaseNames = emptySet()) }

        val unsafeEntries = listOf(
            AutoImportManager.CloudFileItem("u1", "../../evil_1.fit", "/FIT/evil_1.fit"),
            AutoImportManager.CloudFileItem("u2", "sub/evil_2.tcx", "/TCX/evil_2.tcx"),
            AutoImportManager.CloudFileItem("u3", "sub\\evil_3.fit", "/FIT/evil_3.fit"),
            AutoImportManager.CloudFileItem("u4", "ride..fit", "/FIT/ride..fit"),
            AutoImportManager.CloudFileItem("u5", "bad\u0000.fit", "/FIT/bad.fit"),
            AutoImportManager.CloudFileItem("u6", ".", "/FIT/dot")
        )
        val validEntry = AutoImportManager.CloudFileItem("ok1", "valid_ride.fit", "/FIT/valid_ride.fit")

        AutoImportManager.dropboxLister = { _, _, _ -> unsafeEntries + validEntry }
        val downloadedNames = mutableListOf<String>()
        AutoImportManager.dropboxDownloader = { _, item, targetFile ->
            downloadedNames.add(item.name)
            targetFile.writeText("fit-payload")
            true
        }

        coEvery {
            LegacyImportEngine.importFromFitResult(any(), any(), null, false)
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 42L)

        val result = AutoImportManager.syncIncremental(mockContext)

        assertEquals(listOf("valid_ride.fit"), downloadedNames)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(unsafeEntries.size, result.failedCount)
        assertFalse("Temp dir must be purged after sync", File(tempCacheDir, "auto_import_temp").exists())
        assertTrue("Zero escaped files in cacheDir", tempCacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun testSyncIncremental_rejectsZipSlipArchiveAndImportsValidZipSibling_TST_MIG_061_3() = runBlocking {
        val snapshot = ExistingWorkoutsSnapshot(initialBaseNames = setOf("existing_in_zip"))
        AutoImportManager.snapshotProvider = { snapshot }

        val evilZipItem = AutoImportManager.CloudFileItem("z1", "malicious_bundle.zip", "/FIT/malicious_bundle.zip")
        val validZipItem = AutoImportManager.CloudFileItem("z2", "valid_bundle.zip", "/FIT/valid_bundle.zip")
        AutoImportManager.dropboxLister = { _, _, _ -> listOf(evilZipItem, validZipItem) }

        AutoImportManager.dropboxDownloader = { _, item, targetFile ->
            ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
                if (item.id == "z1") {
                    zos.putNextEntry(ZipEntry("../../escaped_from_zip.fit"))
                    zos.write("pwned".toByteArray())
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry("existing_in_zip.fit"))
                    zos.write("dup".toByteArray())
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("new_from_zip.fit"))
                    zos.write("valid-fit".toByteArray())
                    zos.closeEntry()
                }
            }
            true
        }

        val importedFileNames = mutableListOf<String>()
        coEvery {
            LegacyImportEngine.importFromFitResult(any(), any(), null, false)
        } coAnswers {
            importedFileNames.add(secondArg<File>().name)
            LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 77L)
        }

        val result = AutoImportManager.syncIncremental(mockContext)

        assertEquals(listOf("new_from_zip.fit"), importedFileNames)
        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.failedCount)
        assertFalse("Escaped zip-slip file must never exist", File(tempCacheDir.parentFile, "escaped_from_zip.fit").exists())
        assertFalse("Temp dir must be purged after sync", File(tempCacheDir, "auto_import_temp").exists())
    }

    @Test
    fun testDownloadDropboxWithRetry_backsOffOnRateLimitExceptionAndSucceeds_TST_MIG_061_3() = runBlocking {
        val mockDbxClient = mockk<com.dropbox.core.v2.DbxClientV2>(relaxed = true)
        val mockFiles = mockk<com.dropbox.core.v2.files.DbxUserFilesRequests>(relaxed = true)
        val mockDownloader = mockk<com.dropbox.core.DbxDownloader<com.dropbox.core.v2.files.FileMetadata>>(relaxed = true)
        every { mockDbxClient.files() } returns mockFiles

        var attempts = 0
        every { mockFiles.download("/FIT/rate_limited.fit") } answers {
            attempts++
            if (attempts == 1) {
                throw com.dropbox.core.RateLimitException("req-1", "rate limited", 2L, java.util.concurrent.TimeUnit.SECONDS)
            }
            mockDownloader
        }
        every { mockDownloader.download(any<java.io.OutputStream>()) } returns mockk(relaxed = true)

        val delays = mutableListOf<Long>()
        val target = File(tempCacheDir, "rate_limited.fit")
        val ok = CloudWorkoutSourceScanner.downloadDropboxWithRetry(
            dbxClient = mockDbxClient,
            pathLower = "/FIT/rate_limited.fit",
            targetFile = target,
            delayFn = { ms -> delays.add(ms) }
        )

        assertTrue("Download must succeed after rate-limit retry", ok)
        assertEquals(2, attempts)
        assertEquals(listOf(2500L), delays)
    }

    @Test
    fun testDownloadDropboxWithRetry_exhaustedRateLimitRetriesSkipsFinalTailSleep_TST_MIG_061_3() = runBlocking {
        val mockDbxClient = mockk<com.dropbox.core.v2.DbxClientV2>(relaxed = true)
        val mockFiles = mockk<com.dropbox.core.v2.files.DbxUserFilesRequests>(relaxed = true)
        every { mockDbxClient.files() } returns mockFiles

        var attempts = 0
        every { mockFiles.download("/FIT/always_limited.fit") } answers {
            attempts++
            throw com.dropbox.core.RateLimitException("req-$attempts", "rate limited", 3L, java.util.concurrent.TimeUnit.SECONDS)
        }

        val delays = mutableListOf<Long>()
        val target = File(tempCacheDir, "always_limited.fit")
        val ok = CloudWorkoutSourceScanner.downloadDropboxWithRetry(
            dbxClient = mockDbxClient,
            pathLower = "/FIT/always_limited.fit",
            targetFile = target,
            maxRetries = 3,
            delayFn = { ms -> delays.add(ms) }
        )

        assertFalse("Download must return false once maxRetries exhausted", ok)
        assertEquals(3, attempts)
        assertEquals("Must sleep only between retries (maxRetries - 1), never after the final attempt", listOf(3500L, 3500L), delays)
    }
}
