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
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for shared [BackupOrchestrator] verifying lock acquisition, local-only vs cloud-required
 * modes, multi-provider upload dispatch, `commit = true` SharedPreferences persistence, and
 * [BackupWorker.formatTargets] parity (`REQ-MIG-061.5`, `TST-MIG-061.5`).
 */
class BackupOrchestratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var savedValues: MutableMap<String, Any?>

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        savedValues = mutableMapOf()

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } answers {
            savedValues[firstArg()] = secondArg<String>()
            mockEditor
        }
        every { mockEditor.putLong(any(), any()) } answers {
            savedValues[firstArg()] = secondArg<Long>()
            mockEditor
        }
        every { mockEditor.commit() } returns true

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.uploadToDropbox() } returns false
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns null
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun executeBackupAndUpload_requireCloudWhenNoneConnected_skipsLock_TST_MIG_061_5() = runBlocking {
        mockkObject(BackupManager)

        val result = BackupOrchestrator.executeBackupAndUpload(
            context = mockContext,
            requireCloudProvider = true
        )

        assertEquals(BackupOrchestrator.BackupOutcome.NO_CLOUD_PROVIDER, result.outcome)
        verify(exactly = 0) { BackupManager.acquireLock(any()) }
    }

    @Test
    fun executeBackupAndUpload_localOnlySucceedsAndCommitsPrefs_TST_MIG_061_5() = runBlocking {
        val dummyFile = tempFolder.newFile("local.attbackup")
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any()) } returns dummyFile

        val result = BackupOrchestrator.executeBackupAndUpload(
            context = mockContext,
            requireCloudProvider = false
        )

        assertEquals(BackupOrchestrator.BackupOutcome.LOCAL_SUCCESS, result.outcome)
        assertEquals("LOCAL_SUCCESS", savedValues["last_backup_status"])
        assertEquals("local:SUCCESS", savedValues["last_backup_targets"])
        assertTrue((savedValues["last_backup_timestamp"] as Long) > 0L)
        verify(exactly = 1) { mockEditor.commit() }
        verify(exactly = 1) { mockLock.close() }
    }

    @Test
    fun executeBackupAndUpload_partialCloudUploadReportsProgressAndCommitsPrefs_TST_MIG_061_5() = runBlocking {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToGoogleDrive() } returns true
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns true

        val dummyFile = tempFolder.newFile("cloud.attbackup")
        val mockLock = mockk<BackupManager.BackupLock>(relaxed = true)
        mockkObject(BackupManager)
        every { BackupManager.acquireLock(any()) } returns mockLock
        every { BackupManager.createBackup(any(), any()) } returns dummyFile

        mockkObject(DropboxBackupManager)
        coEvery { DropboxBackupManager.uploadBackup(any(), dummyFile) } returns true
        mockkObject(GoogleDriveBackupManager)
        coEvery { GoogleDriveBackupManager.uploadBackup(any(), dummyFile) } returns false

        val progress = mutableListOf<String>()
        val result = BackupOrchestrator.executeBackupAndUpload(
            context = mockContext,
            requireCloudProvider = true,
            onProgress = { progress.add(it) }
        )

        assertEquals(BackupOrchestrator.BackupOutcome.CLOUD_PARTIAL_SUCCESS, result.outcome)
        assertEquals("PARTIAL_SUCCESS", savedValues["last_backup_status"])
        assertEquals("local:SUCCESS;dropbox:SUCCESS;google_drive:FAILED", savedValues["last_backup_targets"])
        assertEquals(
            BackupOrchestrator.formatTargets(result.targets),
            BackupWorker.formatTargets(result.targets)
        )
        assertTrue(progress.contains("Creating backup..."))
        assertTrue(progress.contains("Uploading to Dropbox..."))
        assertTrue(progress.contains("Uploading to Google Drive..."))
        verify(exactly = 1) { mockLock.close() }
    }
}
