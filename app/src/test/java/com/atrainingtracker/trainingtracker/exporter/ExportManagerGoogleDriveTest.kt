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

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import android.content.SharedPreferences
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.uploader.GoogleDriveUploader
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying ExportType.GOOGLE_DRIVE and ExportManager resolution.
 */
class ExportManagerGoogleDriveTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
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

    @Test
    fun testExportType_googleDriveContainsExpectedFormats() {
        val formats = ExportType.GOOGLE_DRIVE.exportToFileFormats.toList()
        assertTrue("Google Drive must support FIT format", formats.contains(FileFormat.FIT))
        assertTrue("Google Drive must support TCX format", formats.contains(FileFormat.TCX))
        assertTrue("Google Drive must support GPX format", formats.contains(FileFormat.GPX))
        assertTrue("Google Drive must support CSV format", formats.contains(FileFormat.CSV))
        assertTrue("Google Drive must support GC format", formats.contains(FileFormat.GC))
    }

    @Test
    fun testExportManager_getExporterReturnsGoogleDriveUploader() {
        val exportInfo = ExportInfo("workout_drive_test", FileFormat.FIT, ExportType.GOOGLE_DRIVE)
        val exporter = ExportManager.getExporter(mockContext, exportInfo)
        assertTrue(
            "ExportManager.getExporter must return an instance of GoogleDriveUploader",
            exporter is GoogleDriveUploader
        )
    }
}
