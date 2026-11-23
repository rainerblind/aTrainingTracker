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
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests verifying single-unzip `.attbackup` staging reuse in [BackupRestoreViewModel] &
 * [ImportEngine], immediate temp archive cleanup, SHA-256 mismatch fallback, and guaranteed
 * staging directory purge on completion, [BackupRestoreViewModel.clearState], and `onCleared()`
 * (`REQ-MIG-061.5`, `TST-MIG-061.5`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupStagingReuseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private lateinit var mockResolver: ContentResolver
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        cacheDir = tempFolder.newFolder("cache")
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockResolver = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)

        every { mockApp.cacheDir } returns cacheDir
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.applicationContext } returns mockApp
        every { mockContext.contentResolver } returns mockResolver

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"
        every { mockPrefs.getBoolean("automated_backups", any()) } returns true

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getClusterTolEndpoints() } returns 100f
        every { TrainingApplication.getClusterTolApex() } returns 100f
        every { TrainingApplication.getClusterTolDistance() } returns 100f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun buildValidAttBackupBytes(tag: String = "v1"): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            listOf("databases/WorkoutSummaries.db", "databases/WorkoutSamples.db", "databases/Laps.db", "databases/SportType.db").forEach { name ->
                zos.putNextEntry(ZipEntry(name))
                zos.write("sqlite-placeholder-$tag-$name".toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun analyzeImportThenPerformIncrementalImport_unzipsExactlyOnceAndCleansUp_TST_MIG_061_5() = runTest(testDispatcher) {
        val archiveBytes = buildValidAttBackupBytes("v1")
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.toString() } returns "content://test/backup_v1.attbackup"
        every { mockResolver.openInputStream(mockUri) } answers { ByteArrayInputStream(archiveBytes) }

        mockkObject(SecureZipExtractor)
        mockkObject(ImportEngine)

        val expectedAnalysis = ImportEngine.AnalysisResult(
            sportTypes = listOf(ImportEngine.SportTypeInfo(1L, "Bike", BSportType.BIKE)),
            equipment = emptyList()
        )
        var capturedStagedDir: File? = null
        every { ImportEngine.analyzeBackupToStaging(any(), any(), any()) } answers {
            val backupFile = secondArg<File>()
            val stagedDir = thirdArg<File>()
            SecureZipExtractor.extractBackupArchive(backupFile, stagedDir)
            capturedStagedDir = stagedDir
            expectedAnalysis
        }
        every {
            ImportEngine.performIncrementalImportFromStaging(any(), any(), any(), any(), any())
        } answers {
            val stagedDir = secondArg<File>()
            assertTrue("Staged dir must contain extracted WorkoutSummaries.db", File(stagedDir, "databases/WorkoutSummaries.db").isFile)
            stagedDir.deleteRecursively()
            3
        }

        val vm = BackupRestoreViewModel(mockApp, testDispatcher)
        vm.analyzeImport(mockContext, mockUri).join()
        advanceUntilIdle()

        assertTrue("Expected MappingRequired state", vm.uiState.value is BackupRestoreViewModel.UiState.MappingRequired)
        assertTrue("Staged dir must exist after analyzeImport", capturedStagedDir?.exists() == true)
        // Copied .attbackup temp file must already be deleted after analyzeImport
        assertTrue(
            "Copied temp .attbackup must be deleted immediately",
            cacheDir.listFiles { f -> f.name.endsWith(".attbackup") }.orEmpty().isEmpty()
        )

        vm.performIncrementalImport(mockContext, mockUri, mapOf(1L to 1L), emptyMap()).join()
        advanceUntilIdle()

        val finalState = vm.uiState.value
        assertTrue("Expected Success state, got $finalState", finalState is BackupRestoreViewModel.UiState.Success)
        assertEquals("Successfully imported 3 workouts", (finalState as BackupRestoreViewModel.UiState.Success).message)

        // Core invariant: SecureZipExtractor.extractBackupArchive invoked EXACTLY ONCE across both steps!
        verify(exactly = 1) { SecureZipExtractor.extractBackupArchive(any(), any(), any()) }
        verify(exactly = 1) { ImportEngine.performIncrementalImportFromStaging(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { ImportEngine.performIncrementalImport(any(), any(), any(), any(), any()) }
        assertFalse("Staged dir must be purged after import", capturedStagedDir!!.exists())
    }

    @Test
    fun analyzeImportThenClearStateOrOnCleared_purgesStagedDirectory_TST_MIG_061_5() = runTest(testDispatcher) {
        val archiveBytes = buildValidAttBackupBytes("cancel")
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.toString() } returns "content://test/cancel.attbackup"
        every { mockResolver.openInputStream(mockUri) } answers { ByteArrayInputStream(archiveBytes) }

        mockkObject(ImportEngine)
        var stagedDir1: File? = null
        every { ImportEngine.analyzeBackupToStaging(any(), any(), any()) } answers {
            val backupFile = secondArg<File>()
            val stagedDir = thirdArg<File>()
            SecureZipExtractor.extractBackupArchive(backupFile, stagedDir)
            stagedDir1 = stagedDir
            ImportEngine.AnalysisResult(listOf(ImportEngine.SportTypeInfo(1L, "Run", BSportType.RUN)), emptyList())
        }

        val vm = BackupRestoreViewModel(mockApp, testDispatcher)
        vm.analyzeImport(mockContext, mockUri).join()
        advanceUntilIdle()
        val firstStagedDir = requireNotNull(stagedDir1)
        assertTrue("Staged dir should exist while awaiting mapping", firstStagedDir.exists())

        // Dismiss via clearState()
        vm.clearState()
        assertFalse("Staged dir must be purged on clearState()", firstStagedDir.exists())
        assertTrue(vm.uiState.value is BackupRestoreViewModel.UiState.Idle)

        // Stage again and simulate ViewModel destruction via onCleared()
        vm.analyzeImport(mockContext, mockUri).join()
        advanceUntilIdle()
        val stagedDir2 = requireNotNull(stagedDir1)
        assertTrue("Second staged dir should exist", stagedDir2.exists())

        val onClearedMethod = BackupRestoreViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(vm)
        assertFalse("Staged dir must be purged on onCleared()", stagedDir2.exists())
    }

    @Test
    fun performIncrementalImport_whenArchiveContentChanged_fallsBackToFreshExtract_TST_MIG_061_5() = runTest(testDispatcher) {
        val archiveV1 = buildValidAttBackupBytes("v1")
        val archiveV2 = buildValidAttBackupBytes("v2_modified")
        var currentBytes = archiveV1

        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.toString() } returns "content://test/mutable.attbackup"
        every { mockResolver.openInputStream(mockUri) } answers { ByteArrayInputStream(currentBytes) }

        mockkObject(ImportEngine)
        var stagedDir: File? = null
        every { ImportEngine.analyzeBackupToStaging(any(), any(), any()) } answers {
            val backupFile = secondArg<File>()
            val dir = thirdArg<File>()
            SecureZipExtractor.extractBackupArchive(backupFile, dir)
            stagedDir = dir
            ImportEngine.AnalysisResult(listOf(ImportEngine.SportTypeInfo(1L, "Bike", BSportType.BIKE)), emptyList())
        }
        every { ImportEngine.performIncrementalImport(any(), any(), any(), any(), any()) } returns 5

        val vm = BackupRestoreViewModel(mockApp, testDispatcher)
        vm.analyzeImport(mockContext, mockUri).join()
        advanceUntilIdle()
        val capturedStagedDir = requireNotNull(stagedDir)
        assertTrue(capturedStagedDir.exists())

        // Mutate archive bytes behind the same URI before confirming import
        currentBytes = archiveV2
        vm.performIncrementalImport(mockContext, mockUri, mapOf(1L to 1L), emptyMap()).join()
        advanceUntilIdle()

        verify(exactly = 0) { ImportEngine.performIncrementalImportFromStaging(any(), any(), any(), any(), any()) }
        verify(exactly = 1) { ImportEngine.performIncrementalImport(any(), any(), any(), any(), any()) }
        assertFalse("Stale staged dir must be deleted on fallback", capturedStagedDir.exists())
        assertTrue(
            "Fallback temp .attbackup must be deleted in finally",
            cacheDir.listFiles { f -> f.name.endsWith(".attbackup") }.orEmpty().isEmpty()
        )
        val state = vm.uiState.value
        assertTrue(state is BackupRestoreViewModel.UiState.Success)
        assertEquals("Successfully imported 5 workouts", (state as BackupRestoreViewModel.UiState.Success).message)
    }

    @Test
    fun importEngine_performIncrementalImportFromStaging_rejectsOutOfCacheDirectory_TST_MIG_061_5() {
        val outsideDir = tempFolder.newFolder("outside_cache")
        var threw = false
        try {
            ImportEngine.performIncrementalImportFromStaging(mockContext, outsideDir, emptyMap(), emptyMap())
        } catch (_: SecurityException) {
            threw = true
        }
        assertTrue("Must reject staging directory outside cacheDir", threw)
    }
}
