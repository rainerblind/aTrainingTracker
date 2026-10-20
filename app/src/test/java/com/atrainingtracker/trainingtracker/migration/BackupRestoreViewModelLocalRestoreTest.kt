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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying REQ-MIG-036 / TST-MIG-033:
 * - Parsing and fallback inference of LastBackupInfo targets
 * - Inspection of internal automatic rolling backup timestamp
 * - In-place restore triggering from internal rolling backup
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelLocalRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var filesDir: File

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

        filesDir = tempFolder.newFolder("files")
        mockApp = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.filesDir } returns filesDir
        every { mockApp.filesDir } returns filesDir
        every { mockContext.applicationContext } returns mockApp

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("backup_interval_days", any()) } returns "1"
        every { mockPrefs.getBoolean("automated_backups", any()) } returns true

        every { mockContext.getString(R.string.restore_automatic_backup_not_found) } returns "No automatic backup found"
        every { mockContext.getString(R.string.restore_failed) } returns "Restore failed"

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
        every { TrainingApplication.getClusterTolEndpoints() } returns 100.0f
        every { TrainingApplication.getClusterTolApex() } returns 100.0f
        every { TrainingApplication.getClusterTolDistance() } returns 100.0f
        every { TrainingApplication.getClusterTolAltitudePos() } returns 100.0f
        every { TrainingApplication.useSportTypeForClustering() } returns false
        every { TrainingApplication.useAltitudePosForClustering() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testLastBackupInfo_whenTargetsSerialized_parsesLocalDropboxGoogleDriveStatuses() {
        every { mockPrefs.getLong("last_backup_timestamp", 0L) } returns 1700000000000L
        every { mockPrefs.getString("last_backup_status", null) } returns "PARTIAL_SUCCESS"
        every { mockPrefs.getString("last_backup_targets", null) } returns "local:SUCCESS;dropbox:SUCCESS;google_drive:FAILED"

        val viewModel = BackupRestoreViewModel(mockApp)
        val info = viewModel.lastBackupInfo.value

        assertNotNull(info)
        assertEquals(1700000000000L, info?.timestamp)
        assertEquals("PARTIAL_SUCCESS", info?.status)
        assertEquals(
            mapOf("local" to true, "dropbox" to true, "google_drive" to false),
            info?.targets
        )
    }

    @Test
    fun testLastBackupInfo_whenTargetsMissing_infersTargetsFromStatus() {
        every { mockPrefs.getLong("last_backup_timestamp", 0L) } returns 1700000000000L
        every { mockPrefs.getString("last_backup_status", null) } returns "LOCAL_SUCCESS"
        every { mockPrefs.getString("last_backup_targets", null) } returns null

        val viewModel = BackupRestoreViewModel(mockApp)
        val info = viewModel.lastBackupInfo.value

        assertNotNull(info)
        assertEquals(mapOf("local" to true), info?.targets)
    }

    @Test
    fun testLastBackupInfo_whenCreationFailsAndTargetsMissing_infersLocalFailed() {
        every { mockPrefs.getLong("last_backup_timestamp", 0L) } returns 1700000000000L
        every { mockPrefs.getString("last_backup_status", null) } returns "Local backup creation failed"
        every { mockPrefs.getString("last_backup_targets", null) } returns null

        val viewModel = BackupRestoreViewModel(mockApp)
        val info = viewModel.lastBackupInfo.value

        assertNotNull(info)
        assertEquals(mapOf("local" to false), info?.targets)
    }

    @Test
    fun testGetAutomaticBackupTimestamp_whenFileExists_returnsLastModified() {
        val backupsDir = File(filesDir, "backups")
        backupsDir.mkdirs()
        val backupFile = File(backupsDir, BackupManager.BACKUP_FILENAME)
        backupFile.writeText("dummy backup payload")
        backupFile.setLastModified(1690000000000L)

        val viewModel = BackupRestoreViewModel(mockApp)
        val timestamp = viewModel.getAutomaticBackupTimestamp(mockContext)

        assertEquals(backupFile.lastModified(), timestamp)
    }

    @Test
    fun testGetAutomaticBackupTimestamp_whenFileDoesNotExist_returnsNull() {
        val viewModel = BackupRestoreViewModel(mockApp)
        val timestamp = viewModel.getAutomaticBackupTimestamp(mockContext)

        assertNull(timestamp)
    }

    @Test
    fun testGetAutomaticBackupTimestamp_whenFileIsEmpty_returnsNull() {
        val backupsDir = File(filesDir, "backups")
        backupsDir.mkdirs()
        val backupFile = File(backupsDir, BackupManager.BACKUP_FILENAME)
        backupFile.createNewFile()

        val viewModel = BackupRestoreViewModel(mockApp)
        val timestamp = viewModel.getAutomaticBackupTimestamp(mockContext)

        assertNull(timestamp)
    }

    @Test
    fun testRestoreFromLocalBackup_whenFileMissing_emitsError() = runTest(testDispatcher) {
        val viewModel = BackupRestoreViewModel(mockApp)

        viewModel.restoreFromLocalBackup(mockContext)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be UiState.Error when backup file is missing", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("No automatic backup found", (state as BackupRestoreViewModel.UiState.Error).message)
    }

    @Test
    fun testRestoreFromLocalBackup_whenFileExistsAndRestoreSucceeds_invokesMigrationEngine() = runTest(testDispatcher) {
        val backupsDir = File(filesDir, "backups")
        backupsDir.mkdirs()
        val backupFile = File(backupsDir, BackupManager.BACKUP_FILENAME)
        backupFile.writeText("dummy backup payload")

        mockkObject(MigrationEngine)
        every { MigrationEngine.performFullRestore(any(), any(), any()) } returns true

        val viewModel = BackupRestoreViewModel(mockApp)
        viewModel.restoreFromLocalBackup(mockContext)
        advanceUntilIdle()

        verify(exactly = 1) {
            MigrationEngine.performFullRestore(
                eq(mockContext),
                match { it.absolutePath == backupFile.absolutePath },
                any()
            )
        }
    }

    @Test
    fun testRestoreFromLocalBackup_whenRestoreReturnsFalse_emitsError() = runTest(testDispatcher) {
        val backupsDir = File(filesDir, "backups")
        backupsDir.mkdirs()
        val backupFile = File(backupsDir, BackupManager.BACKUP_FILENAME)
        backupFile.writeText("dummy backup payload")

        mockkObject(MigrationEngine)
        every { MigrationEngine.performFullRestore(any(), any(), any()) } returns false

        val viewModel = BackupRestoreViewModel(mockApp)
        viewModel.restoreFromLocalBackup(mockContext)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be UiState.Error when restore returns false", state is BackupRestoreViewModel.UiState.Error)
        assertEquals("Restore failed", (state as BackupRestoreViewModel.UiState.Error).message)
    }

    @Test
    fun testRestoreFromLocalBackup_whenRestoreThrowsException_emitsError() = runTest(testDispatcher) {
        val backupsDir = File(filesDir, "backups")
        backupsDir.mkdirs()
        val backupFile = File(backupsDir, BackupManager.BACKUP_FILENAME)
        backupFile.writeText("dummy backup payload")

        mockkObject(MigrationEngine)
        every { MigrationEngine.performFullRestore(any(), any(), any()) } throws RuntimeException("Corrupt SQLite database")

        val viewModel = BackupRestoreViewModel(mockApp)
        viewModel.restoreFromLocalBackup(mockContext)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State must be UiState.Error on exception", state is BackupRestoreViewModel.UiState.Error)
        assertTrue((state as BackupRestoreViewModel.UiState.Error).message.contains("Corrupt SQLite database"))
    }
}
