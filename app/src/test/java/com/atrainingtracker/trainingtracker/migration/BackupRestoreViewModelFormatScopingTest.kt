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
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import io.mockk.*
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
 * Unit tests verifying format-scoped cloud recovery and legacy import triggers in [BackupRestoreViewModel]
 * for FIT, TCX, and GPX (REQ-UI-293, TST-UI-253, ATT-2623).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelFormatScopingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences

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

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.getString(any(), any()) } answers { secondArg<String?>() }
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"

        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getClusterTolEndpoints() } returns 100.0f
        every { TrainingApplication.getClusterTolApex() } returns 100.0f
        every { TrainingApplication.getClusterTolDistance() } returns 100.0f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100.0f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false

        every { mockContext.getString(R.string.google_drive_disconnected_status) } returns "Google Drive disconnected"
        every { mockContext.getString(R.string.dropbox_disconnected_status) } returns "Dropbox disconnected"
        every { mockApp.getString(R.string.legacy_import__finished_all_new, *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            "Finished: ${formatArgs[0]} imported out of ${formatArgs[1]}"
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testBulkRecoverLegacyData_passesCorrectFormatToDropbox() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)

        mockkObject(LegacyImportEngine)
        val capturedFormats = mutableListOf<String>()
        coEvery {
            LegacyImportEngine.bulkRecoverFromDropbox(any(), capture(capturedFormats), any(), any())
        } returns LegacyImportEngine.RecoveryResult(
            importedCount = 1,
            skippedCount = 0,
            failedCount = 0,
            totalScanned = 1
        )

        val viewModel = BackupRestoreViewModel(mockApp)

        val jobFit = viewModel.bulkRecoverLegacyData(mockContext, "fit", testDispatcher)
        jobFit.join()

        val jobTcx = viewModel.bulkRecoverLegacyData(mockContext, "tcx", testDispatcher)
        jobTcx.join()

        val jobGpx = viewModel.bulkRecoverLegacyData(mockContext, "gpx", testDispatcher)
        jobGpx.join()

        assertEquals(listOf("fit", "tcx", "gpx"), capturedFormats)
    }

    @Test
    fun testBulkRecoverGoogleDriveData_passesCorrectFormatToGoogleDrive() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.getGoogleDriveAuthToken() } returns "fake_token"

        mockkObject(LegacyImportEngine)
        val capturedFormats = mutableListOf<String>()
        coEvery {
            LegacyImportEngine.bulkRecoverFromGoogleDrive(any(), capture(capturedFormats), any(), any())
        } returns LegacyImportEngine.RecoveryResult(
            importedCount = 1,
            skippedCount = 0,
            failedCount = 0,
            totalScanned = 1
        )

        val viewModel = BackupRestoreViewModel(mockApp)

        val jobFit = viewModel.bulkRecoverGoogleDriveData(mockContext, "fit", testDispatcher)
        jobFit.join()

        val jobTcx = viewModel.bulkRecoverGoogleDriveData(mockContext, "tcx", testDispatcher)
        jobTcx.join()

        val jobGpx = viewModel.bulkRecoverGoogleDriveData(mockContext, "gpx", testDispatcher)
        jobGpx.join()

        assertEquals(listOf("fit", "tcx", "gpx"), capturedFormats)
    }

    @Test
    fun testImportLegacyFile_resolvesFormatCorrectly() = runTest(testDispatcher) {
        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.importFromTcxResult(any(), any(), any(), any())
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 42L)
        coEvery {
            LegacyImportEngine.importFromGpxResult(any(), any(), any(), any())
        } returns LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.SUCCESS, 43L)

        val mockUri = mockk<Uri>(relaxed = true)
        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { mockContext.contentResolver } returns contentResolver
        every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { contentResolver.openInputStream(mockUri) } answers {
            ByteArrayInputStream("fake_track_data".toByteArray())
        }
        every { contentResolver.query(mockUri, any(), any(), any(), any()) } returns null

        val viewModel = BackupRestoreViewModel(mockApp)

        viewModel.importLegacyFile(mockContext, mockUri, "tcx")
        advanceUntilIdle()

        coVerify(exactly = 1) { LegacyImportEngine.importFromTcxResult(any(), any(), any(), any()) }

        viewModel.importLegacyFile(mockContext, mockUri, "gpx")
        advanceUntilIdle()

        coVerify(exactly = 1) { LegacyImportEngine.importFromGpxResult(any(), any(), any(), any()) }
    }
}
