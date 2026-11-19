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

import android.app.Application
import android.content.Context
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.dropbox.core.DbxDownloader
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DbxUserFilesRequests
import com.dropbox.core.v2.files.DbxUserListFolderBuilder
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream

/**
 * Unit tests verifying Dropbox TCX recovery workflows:
 * - Idempotent TCX import skipping clustering prompts (`REQ-MIG-013`, `TST-MIG-010`)
 * - Pre-download skip of already-existing workouts (`REQ-MIG-015`, `TST-MIG-011`)
 * - Recursive paginated Dropbox folder scanning (`REQ-MIG-016`, `TST-MIG-012`)
 * - Dropbox scanning & downloading feedback messages (`REQ-MIG-019`, `TST-MIG-015`)
 * - Multi-path recursive discovery & ViewModel summary counts (`REQ-MIG-016`, `REQ-MIG-021`, `TST-MIG-018`)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LegacyImportEngineCloudRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockApp: Application
    private lateinit var mockRepo: WorkoutRepository
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        cacheDir = tempFolder.newFolder("cache")
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockApp
        every { mockContext.cacheDir } returns cacheDir
        every { mockApp.cacheDir } returns cacheDir
        every { mockContext.getString(R.string.legacy_import__downloading_dropbox, *anyVararg()) } answers {
            val argsArr = args[1] as Array<*>
            "Downloading ${argsArr[0]} from Dropbox…"
        }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)

        mockRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockRepo)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    private fun stubEmptyDbCursorForSnapshot() {
        val mockSqlDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        val emptyCursor = MatrixCursor(arrayOf(WorkoutSummaries.FILE_BASE_NAME, WorkoutSummaries.TIME_START, WorkoutSummaries.B_SPORT))
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns emptyCursor
    }

    @Test
    fun testImportFromTcxResult_whenAlreadyExisting_skipsWithoutClusterPrompt_TST_MIG_010() = runBlocking {
        every { mockRepo.isWorkoutExisting("2026-08-01_10-00-00") } returns true

        val tcxFile = File(tempFolder.newFolder("tcx_idem"), "2026-08-01_10-00-00.tcx").apply {
            writeText("<TrainingCenterDatabase/>")
        }
        val listener = mockk<LegacyImportEngine.ProgressListener>(relaxed = true)

        val result = LegacyImportEngine.importFromTcxResult(mockContext, tcxFile, listener)

        assertEquals(LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED, result.status)
        coVerify(exactly = 0) {
            listener.onNewClusterCandidate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun testBulkRecoverFromDropbox_skipsExistingBeforeDownloadAndPaginatesRecursively_TST_MIG_011_TST_MIG_012_TST_MIG_015() = runBlocking {
        mockkObject(ExistingWorkoutsSnapshot.Companion)
        val snapshot = ExistingWorkoutsSnapshot(setOf("existing_ride"), emptySet())
        every { ExistingWorkoutsSnapshot.create(any()) } returns snapshot

        val existingEntry = mockk<FileMetadata>(relaxed = true) {
            every { name } returns "existing_ride.tcx"
            every { pathLower } returns "/tcx/2025/existing_ride.tcx"
        }
        val newEntry1 = mockk<FileMetadata>(relaxed = true) {
            every { name } returns "new_ride_1.tcx"
            every { pathLower } returns "/tcx/2025/new_ride_1.tcx"
        }
        val newEntry2 = mockk<FileMetadata>(relaxed = true) {
            every { name } returns "new_ride_2.tcx"
            every { pathLower } returns "/apps/workouts/tcx/sub/new_ride_2.tcx"
        }

        val page1 = mockk<ListFolderResult>(relaxed = true) {
            every { entries } returns listOf(existingEntry, newEntry1)
            every { hasMore } returns true
            every { cursor } returns "cursor_page_2"
        }
        val page2 = mockk<ListFolderResult>(relaxed = true) {
            every { entries } returns listOf(newEntry2)
            every { hasMore } returns false
        }
        val emptyPage = mockk<ListFolderResult>(relaxed = true) {
            every { entries } returns emptyList()
            every { hasMore } returns false
        }

        val mockFilesReq = mockk<DbxUserFilesRequests>(relaxed = true)
        val builderTcx = mockk<DbxUserListFolderBuilder>(relaxed = true)
        val builderApps = mockk<DbxUserListFolderBuilder>(relaxed = true)
        every { builderTcx.withRecursive(true) } returns builderTcx
        every { builderApps.withRecursive(true) } returns builderApps
        every { builderTcx.start() } returns page1
        every { builderApps.start() } returns emptyPage
        every { mockFilesReq.listFolderBuilder("/TCX") } returns builderTcx
        every { mockFilesReq.listFolderBuilder("/apps/Workouts/TCX") } returns builderApps
        every { mockFilesReq.listFolderContinue("cursor_page_2") } returns page2

        val downloadedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val mockDownloader = mockk<DbxDownloader<FileMetadata>>(relaxed = true)
        every { mockDownloader.download(any<OutputStream>()) } returns mockk(relaxed = true)
        every { mockFilesReq.download(any()) } answers {
            downloadedPaths.add(firstArg())
            mockDownloader
        }

        mockkConstructor(DbxClientV2::class)
        every { anyConstructed<DbxClientV2>().files() } returns mockFilesReq

        mockkObject(LegacyImportEngine)
        io.mockk.coEvery {
            LegacyImportEngine.importFromTcxInternal(any(), any(), any(), any())
        } returns LegacyImportEngine.ImportStatus.SUCCESS

        val statusMessages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val listener = object : LegacyImportEngine.ProgressListener {
            override fun onProgress(current: Int, total: Int, name: String) {}
            override fun onStatus(message: String) {
                statusMessages.add(message)
            }
            override suspend fun onNewClusterCandidate(
                date: String,
                start: com.google.android.gms.maps.model.LatLng,
                end: com.google.android.gms.maps.model.LatLng,
                apex: com.google.android.gms.maps.model.LatLng,
                distance: Double,
                bSportType: com.atrainingtracker.banalservice.BSportType,
                polyline: String,
                workoutName: String?,
                candidateSportTypes: Set<com.atrainingtracker.banalservice.BSportType>,
                minAltPos: com.google.android.gms.maps.model.LatLng?,
                maxAltPos: com.google.android.gms.maps.model.LatLng?
            ) = LegacyImportEngine.ClusterDecision(null, null)
        }

        val result = LegacyImportEngine.bulkRecoverFromDropbox(mockContext, "tcx", listener)

        // TST-MIG-012: recursive builder & cursor continuation used
        verify(exactly = 1) { builderTcx.withRecursive(true) }
        verify(exactly = 1) { mockFilesReq.listFolderContinue("cursor_page_2") }

        // TST-MIG-011: existing_ride.tcx must NEVER be downloaded
        assertEquals(listOf("/tcx/2025/new_ride_1.tcx", "/apps/workouts/tcx/sub/new_ride_2.tcx").toSet(), downloadedPaths.toSet())
        assertEquals(2, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(3, result.totalScanned)

        // TST-MIG-015: scanning and downloading feedback emitted
        assertTrue("Expected scanning status", statusMessages.any { it.contains("Scanning /TCX") })
        assertTrue("Expected downloading status", statusMessages.any { it.contains("Downloading") && it.contains("Dropbox") })
    }

    @Test
    fun testBackupRestoreViewModel_emitsInformativeSummaryCountsAfterDropboxScan_TST_MIG_018() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            every { mockApp.getString(R.string.legacy_import__finished_with_skipped, any(), any(), any()) } answers {
                val varargs = args[1] as Array<*>
                "Import finished: ${varargs[0]} new workout(s) imported (${varargs[1]} skipped) out of ${varargs[2]} files scanned."
            }
            val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
            val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
            every { mockPrefs.edit() } returns mockEditor
            every { mockPrefs.getString("backup_interval_days", any()) } returns "1"
            mockkStatic(androidx.preference.PreferenceManager::class)
            every { androidx.preference.PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
            every { TrainingApplication.getClusterTolEndpoints() } returns 100f
            every { TrainingApplication.getClusterTolApex() } returns 100f
            every { TrainingApplication.getClusterTolDistance() } returns 100f
            every { TrainingApplication.getClusterTolAltitudePos() } returns 100f
            every { TrainingApplication.useSportTypeForClustering() } returns false
            every { TrainingApplication.useAltitudePosForClustering() } returns false

            mockkObject(LegacyImportEngine)
            io.mockk.coEvery {
                LegacyImportEngine.bulkRecoverFromDropbox(any(), "tcx", any(), any())
            } returns LegacyImportEngine.RecoveryResult(importedCount = 2, skippedCount = 1, failedCount = 0, totalScanned = 3)

            val mockPeriodsRepo = mockk<com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository>(relaxed = true)
            com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)
            val mockClusterRepo = mockk<com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository>(relaxed = true)
            com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository.resetForTesting(mockClusterRepo)

            val vm = BackupRestoreViewModel(mockApp)
            vm.bulkRecoverLegacyData(mockContext, "tcx", dispatcher)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("Expected UiState.Success, got $state", state is BackupRestoreViewModel.UiState.Success)
            val msg = (state as BackupRestoreViewModel.UiState.Success).message
            assertTrue("Summary must report 2 imported, 1 skipped, 3 scanned: $msg", msg.contains("2") && msg.contains("1") && msg.contains("3"))
        } finally {
            com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.resetInstanceForTesting()
            com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository.resetForTesting(null)
            Dispatchers.resetMain()
        }
    }
}
