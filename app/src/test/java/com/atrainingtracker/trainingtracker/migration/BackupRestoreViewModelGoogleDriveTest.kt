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
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
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

/**
 * Unit tests verifying Google Drive bulk recovery trigger in [BackupRestoreViewModel]
 * (REQ-MIG-034, TST-MIG-031).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelGoogleDriveTest {

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

        every { mockContext.getString(R.string.google_drive_disconnected_status) } returns "You are not connected to Google Drive"
        every { mockApp.getString(R.string.google_drive_disconnected_status) } returns "You are not connected to Google Drive"
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
    fun testBulkRecoverGoogleDriveData_whenDisconnected_emitsErrorState() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.getGoogleDriveAuthToken() } returns null

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.bulkRecoverGoogleDriveData(mockContext, "all", testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("State should be Error when disconnected", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("You are not connected to Google Drive", (state as BackupRestoreViewModel.UiState.Error).message)
    }

    @Test
    fun testBulkRecoverGoogleDriveData_whenConnected_executesRecoveryAndEmitsSuccess() = runTest(testDispatcher) {
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.getGoogleDriveAuthToken() } returns "valid_token_123"

        mockkObject(LegacyImportEngine)
        coEvery {
            LegacyImportEngine.bulkRecoverFromGoogleDrive(any(), any(), any(), any())
        } returns LegacyImportEngine.RecoveryResult(
            importedCount = 5,
            skippedCount = 0,
            failedCount = 0,
            totalScanned = 5
        )

        val viewModel = BackupRestoreViewModel(mockApp)
        val job = viewModel.bulkRecoverGoogleDriveData(mockContext, "all", testDispatcher)
        job.join()

        val state = viewModel.uiState.value
        assertTrue("State should be Success upon completion", state is BackupRestoreViewModel.UiState.Success)
        val successState = state as BackupRestoreViewModel.UiState.Success
        assertTrue("Message should report finished count", successState.message.contains("5"))
    }
}
