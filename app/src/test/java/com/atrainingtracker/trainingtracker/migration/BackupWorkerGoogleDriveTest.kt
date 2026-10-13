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
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.TrainingApplication
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
 * Unit tests verifying BackupWorker independent dispatch to Google Drive and Dropbox.
 */
class BackupWorkerGoogleDriveTest {

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

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
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
    fun testDoWork_whenNeitherCloudConnected_skipsBackup() = runBlocking {
        every { mockPrefs.getBoolean("automated_backups", false) } returns true
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_DROPBOX, false) } returns false
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns false

        val worker = BackupWorker(mockContext, mockParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testDoWork_whenGoogleDriveConnected_triggersUpload() = runBlocking {
        every { mockPrefs.getBoolean("automated_backups", false) } returns true
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_DROPBOX, false) } returns false
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_BACKUP_TO_GOOGLE_DRIVE, true) } returns true

        val dummyBackupFile = tempFolder.newFile("test.attbackup")
        mockkObject(BackupManager)
        every { BackupManager.createBackup(any()) } returns dummyBackupFile

        mockkObject(GoogleDriveBackupManager)
        coEvery { GoogleDriveBackupManager.uploadBackup(any(), dummyBackupFile) } returns true

        val worker = BackupWorker(mockContext, mockParams)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { GoogleDriveBackupManager.uploadBackup(any(), dummyBackupFile) }
        verify { mockEditor.putString("last_backup_status", "SUCCESS") }
    }
}
