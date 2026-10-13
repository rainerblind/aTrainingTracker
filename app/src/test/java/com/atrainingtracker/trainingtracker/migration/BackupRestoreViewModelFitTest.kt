/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit tests verifying batch ingestion logic, duplicate skipping reporting, and UI state
 * transitions in [BackupRestoreViewModel] for FIT workouts (REQ-DAT-019, TST-DAT-014).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelFitTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getString(any(), any()) } answers { secondArg<String?>() }
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"

        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getClusterTolEndpoints() } returns 100.0f
        every { TrainingApplication.getClusterTolApex() } returns 100.0f
        every { TrainingApplication.getClusterTolDistance() } returns 100.0f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100.0f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        every { mockContext.contentResolver } returns mockContentResolver
        val tempDir = File(System.getProperty("java.io.tmpdir"), "fit_vm_test")
        tempDir.mkdirs()
        every { mockContext.cacheDir } returns tempDir

        every { mockContext.getString(R.string.import_fit_progress, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            "Importing ${formatArgs[0]} of ${formatArgs[1]}"
        }
        every { mockContext.getString(R.string.import_fit_summary_success, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            "Successfully imported ${formatArgs[0]} workouts (${formatArgs[1]} duplicates skipped)."
        }
        every { mockContext.getString(R.string.import_fit_summary_with_errors, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            "Imported ${formatArgs[0]} workouts, ${formatArgs[1]} duplicates skipped, ${formatArgs[2]} failed."
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun importFitFiles_processesMultipleUris_andEmitsSuccessSummary() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uris = listOf(uri1, uri2)

        every { mockContentResolver.openInputStream(uri1) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        every { mockContentResolver.openInputStream(uri2) } answers { ByteArrayInputStream(byteArrayOf(4, 5, 6)) }
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null

        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.importFromFitResult(mockContext, match { it.name.contains("_0.fit") }, any(), any())
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 101L)

        coEvery {
            LegacyImportEngine.importFromFitResult(mockContext, match { it.name.contains("_1.fit") }, any(), any())
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED)

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.importFitFiles(mockContext, uris, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("State should be Success but was $state", state is BackupRestoreViewModel.UiState.Success)
        val successState = state as BackupRestoreViewModel.UiState.Success
        val msg = successState.message
        assertTrue(msg.contains("1 workouts"))
        assertTrue(msg.contains("1 duplicates skipped"))
        assertEquals(101L, successState.importedWorkoutId)
    }

    @Test
    fun importFitFiles_withFailure_emitsSummaryWithErrors() = runTest(testDispatcher) {
        val uri1 = mockk<Uri>()
        val uris = listOf(uri1)

        every { mockContentResolver.openInputStream(uri1) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null

        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.importFromFitResult(mockContext, any(), any(), any())
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.FAILED)

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.importFitFiles(mockContext, uris, testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("State should be Error when 0 imported but was $state", state is BackupRestoreViewModel.UiState.Error)
        val msg = (state as BackupRestoreViewModel.UiState.Error).message
        assertTrue(msg.contains("1 failed"))
    }
}
