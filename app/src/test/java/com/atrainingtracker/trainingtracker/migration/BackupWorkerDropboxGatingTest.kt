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
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field

/**
 * Unit tests verifying BackupWorker Dropbox backup gating and dynamic network constraints (TST-DAT-031.3).
 */
class BackupWorkerDropboxGatingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockParams: WorkerParameters
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockParams = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        every { mockContext.applicationContext } returns mockContext
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.commit() } returns true

        mockkStatic(TrainingApplication::class)
        mockkObject(DropboxBackupManager)
        mockkObject(GoogleDriveBackupManager)
        mockkObject(BackupManager)

        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBuildConstraints_whenNoCloudBackupEnabled_returnsNotRequired() {
        every { TrainingApplication.uploadToDropbox() } returns false
        every { TrainingApplication.uploadBackupToDropbox() } returns false
        every { TrainingApplication.readDropboxCredential() } returns null
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadBackupToGoogleDrive() } returns false

        val constraints = BackupWorker.buildConstraints(mockContext)
        assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
    }

    @Test
    fun testBuildConstraints_whenDropboxBackupEnabled_withoutWifiOnly_returnsConnected() {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns false
        every { TrainingApplication.uploadToGoogleDrive() } returns false

        val constraints = BackupWorker.buildConstraints(mockContext)
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun testBuildConstraints_whenDropboxBackupEnabled_withWifiOnly_returnsUnmetered() {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns true
        every { TrainingApplication.uploadToGoogleDrive() } returns false

        val constraints = BackupWorker.buildConstraints(mockContext)
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
    }

    @Test
    fun testDoWork_whenUploadBackupToDropboxDisabled_skipsDropboxUpload() = runBlocking {
        every { mockPrefs.getBoolean("automated_backups", true) } returns true
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns false
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)

        val fakeBackupFile = File(tempFolder.root, "test.attbackup").apply { writeText("dummy") }
        every { BackupManager.acquireLock(any()) } returns mockk<BackupManager.BackupLock>(relaxed = true)
        every { BackupManager.createBackup(any()) } returns fakeBackupFile

        val worker = BackupWorker(mockContext, mockParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { DropboxBackupManager.uploadBackup(any(), any()) }
        verify { mockEditor.putString("last_backup_status", "LOCAL_SUCCESS") }
    }

    @Test
    fun testDoWork_whenUploadBackupToDropboxEnabled_invokesDropboxUpload() = runBlocking {
        every { mockPrefs.getBoolean("automated_backups", true) } returns true
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadBackupToDropbox() } returns true
        every { TrainingApplication.readDropboxCredential() } returns mockk<DbxCredential>(relaxed = true)

        val fakeBackupFile = File(tempFolder.root, "test.attbackup").apply { writeText("dummy") }
        every { BackupManager.acquireLock(any()) } returns mockk<BackupManager.BackupLock>(relaxed = true)
        every { BackupManager.createBackup(any()) } returns fakeBackupFile
        coEvery { DropboxBackupManager.uploadBackup(any(), any()) } returns true

        val worker = BackupWorker(mockContext, mockParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { DropboxBackupManager.uploadBackup(any(), fakeBackupFile) }
        verify { mockEditor.putString("last_backup_status", "SUCCESS") }
    }
}
