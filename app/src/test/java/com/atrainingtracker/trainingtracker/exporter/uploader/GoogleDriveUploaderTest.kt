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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Field

/**
 * Unit tests verifying GoogleDriveUploader export lifecycle, credential checks, and folder resolution.
 */
class GoogleDriveUploaderTest {

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
        setStaticField(TrainingApplication::class.java, "DEBUG", false)
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
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

    private fun createTestUploader(baseDir: File): GoogleDriveUploader {
        return object : GoogleDriveUploader(mockContext) {
            override fun getBaseFile(filename: String): File {
                return File(baseDir, filename)
            }

            override fun createClient(): GoogleDriveClient {
                return mockClient
            }
        }
    }

    @Test
    fun testDoExport_fileDoesNotExist_returnsFailure() {
        val uploader = createTestUploader(tempFolder.root)
        val exportInfo = ExportInfo("missing_workout", FileFormat.FIT, ExportType.GOOGLE_DRIVE)

        val result = uploader.doExport(exportInfo)
        assertFalse(result.success())
        assertTrue(result.answer().contains("does not exist"))
    }

    @Test
    fun testDoExport_missingCredentials_returnsFailure() {
        val fitDir = File(tempFolder.root, "FIT").apply { mkdirs() }
        File(fitDir, "workout.fit").apply { writeText("data") }
        val exportInfo = ExportInfo("workout", FileFormat.FIT, ExportType.GOOGLE_DRIVE)

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns null
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns false

        val uploader = createTestUploader(tempFolder.root)
        val result = uploader.doExport(exportInfo)
        assertFalse(result.success())
        assertTrue(result.answer().contains("credential is null"))
    }

    @Test
    fun testDoExport_folderResolutionFails_returnsFailure() {
        val fitDir = File(tempFolder.root, "FIT").apply { mkdirs() }
        File(fitDir, "workout.fit").apply { writeText("data") }
        val exportInfo = ExportInfo("workout", FileFormat.FIT, ExportType.GOOGLE_DRIVE)

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "valid_token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        every { mockClient.ensureFolderHierarchy(any()) } returns null

        val uploader = createTestUploader(tempFolder.root)
        val result = uploader.doExport(exportInfo)
        assertFalse(result.success())
        assertTrue(result.answer().contains("Failed to resolve Google Drive folder hierarchy"))
    }

    @Test
    fun testDoExport_uploadSuccess_updatesTimestampAndReturnsSuccess() {
        val fitDir = File(tempFolder.root, "FIT").apply { mkdirs() }
        File(fitDir, "workout.fit").apply { writeText("data") }
        val exportInfo = ExportInfo("workout", FileFormat.FIT, ExportType.GOOGLE_DRIVE)

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "valid_token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        every { mockClient.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts")) } returns "workouts_folder_id"
        every { mockClient.uploadOrOverwriteFile("workouts_folder_id", "workout.fit", any(), any()) } returns true

        val uploader = createTestUploader(tempFolder.root)
        val result = uploader.doExport(exportInfo)

        assertTrue(result.success())
        verify { mockEditor.putString(TrainingApplication.SP_GOOGLE_DRIVE_LAST_SYNC_STATUS, "SUCCESS") }
        verify { mockEditor.putLong(TrainingApplication.SP_GOOGLE_DRIVE_LAST_SYNC, any()) }
    }

    @Test
    fun testDoExport_folderResolutionFailsWith401_returnsLocalizedAuthExpiredMessage() {
        val fitDir = File(tempFolder.root, "FIT").apply { mkdirs() }
        File(fitDir, "workout.fit").apply { writeText("data") }
        val exportInfo = ExportInfo("workout", FileFormat.FIT, ExportType.GOOGLE_DRIVE)

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "expired_token"
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns true

        every { mockClient.ensureFolderHierarchy(any()) } returns null
        every { mockClient.lastHttpCode } returns 401
        every { mockContext.getString(com.atrainingtracker.R.string.google_drive_error_auth_expired) } returns "Google Drive session expired or unauthorized. Please re-authenticate in Settings."

        val uploader = createTestUploader(tempFolder.root)
        val result = uploader.doExport(exportInfo)

        assertFalse(result.success())
        assertEquals("Google Drive session expired or unauthorized. Please re-authenticate in Settings.", result.answer())
    }
}
