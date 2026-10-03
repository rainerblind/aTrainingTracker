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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field

/**
 * Unit tests verifying GoogleDriveBackupManager upload and download operations.
 */
class GoogleDriveBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockClient: GoogleDriveClient

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
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        mockClient = mockk(relaxed = true)

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
        GoogleDriveBackupManager.clientProvider = { mockClient }
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        GoogleDriveBackupManager.resetForTesting()
        unmockkAll()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testUploadBackup_unauthenticated_returnsFalse() = runBlocking {
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns null
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns false

        val backupFile = tempFolder.newFile("test.attbackup")
        val result = GoogleDriveBackupManager.uploadBackup(mockContext, backupFile)
        assertFalse(result)
    }

    @Test
    fun testUploadBackup_fileDoesNotExist_returnsFalse() = runBlocking {
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        val nonExistentFile = File(tempFolder.root, "non_existent.attbackup")
        val result = GoogleDriveBackupManager.uploadBackup(mockContext, nonExistentFile)
        assertFalse(result)
    }

    @Test
    fun testUploadBackup_success_updatesStatusAndReturnsTrue() = runBlocking {
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        every { mockClient.ensureFolderHierarchy(listOf("aTrainingTracker", "Backups")) } returns "backups_folder_id"
        every {
            mockClient.uploadOrOverwriteFile(
                folderId = "backups_folder_id",
                fileName = GoogleDriveBackupManager.GOOGLE_DRIVE_BACKUP_FILENAME,
                mimeType = "application/octet-stream",
                file = any()
            )
        } returns true

        val backupFile = tempFolder.newFile("test.attbackup").apply { writeText("backup_data") }
        val result = GoogleDriveBackupManager.uploadBackup(mockContext, backupFile)

        assertTrue(result)
        verify { mockEditor.putString(TrainingApplication.SP_GOOGLE_DRIVE_LAST_SYNC_STATUS, "SUCCESS") }
        verify { mockEditor.putLong(TrainingApplication.SP_GOOGLE_DRIVE_LAST_SYNC, any()) }
    }

    @Test
    fun testDownloadBackup_success_returnsTrue() = runBlocking {
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        every { mockClient.ensureFolderHierarchy(listOf("aTrainingTracker", "Backups")) } returns "backups_folder_id"
        every {
            mockClient.downloadFile(
                folderId = "backups_folder_id",
                fileName = GoogleDriveBackupManager.GOOGLE_DRIVE_BACKUP_FILENAME,
                destinationFile = any()
            )
        } returns true

        val destFile = File(tempFolder.root, "dest.attbackup")
        val result = GoogleDriveBackupManager.downloadBackup(mockContext, destFile)
        assertTrue(result)
    }
}
