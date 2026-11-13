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
import com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Integration and registration tests for FileFormat.FIT, ExportType, ExportManager, and TrainingApplication.
 */
class ExportManagerFitTest {

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
    fun testFileFormat_fitRegisteredInStandardFormats() {
        assertTrue(
            "FileFormat.STANDARD_FILE_FORMATS must contain FIT",
            FileFormat.STANDARD_FILE_FORMATS.contains(FileFormat.FIT)
        )
        assertEquals(".fit", FileFormat.FIT.fileEnding)
        assertEquals("FIT", FileFormat.FIT.dirName)
    }

    @Test
    fun testExportType_dropboxIncludesFit() {
        val dropboxFormats = ExportType.DROPBOX.exportToFileFormats
        assertTrue(
            "ExportType.DROPBOX must support FileFormat.FIT",
            dropboxFormats.contains(FileFormat.FIT)
        )
    }

    @Test
    fun testExportManager_getExporterReturnsFitFileWriter() {
        val exportInfo = ExportInfo("workout_test", FileFormat.FIT, ExportType.FILE)
        val exporter = ExportManager.getExporter(mockContext, exportInfo)
        assertTrue(
            "ExportManager.getExporter must return an instance of FitFileWriter",
            exporter is FitFileWriter
        )
    }

    @Test
    fun testTrainingApplication_exportToFitPreference() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_EXPORT_FIT, false) } returns true

        assertTrue(TrainingApplication.exportToFIT())

        TrainingApplication.setExportToFIT(true)
        verify { mockEditor.putBoolean(TrainingApplication.SP_EXPORT_FIT, true) }
    }

    @Test
    fun testFileFormat_healthConnectRegisteredInOnlineCommunities() {
        assertTrue(
            "FileFormat.ONLINE_COMMUNITIES must contain HEALTH_CONNECT",
            FileFormat.ONLINE_COMMUNITIES.contains(FileFormat.HEALTH_CONNECT)
        )
        assertEquals("HealthConnect", FileFormat.HEALTH_CONNECT.dirName)
        assertEquals(com.atrainingtracker.R.string.health_connect_title, FileFormat.HEALTH_CONNECT.uiNameId)
    }

    @Test
    fun testExportType_communityIncludesHealthConnect() {
        val communityFormats = ExportType.COMMUNITY.exportToFileFormats
        assertTrue(
            "ExportType.COMMUNITY must support FileFormat.HEALTH_CONNECT",
            communityFormats.contains(FileFormat.HEALTH_CONNECT)
        )
    }

    @Test
    fun testExportManager_getExporterHealthConnectFallback() {
        val exportInfo = ExportInfo("workout_test", FileFormat.HEALTH_CONNECT, ExportType.COMMUNITY)
        val exporter = ExportManager.getExporter(mockContext, exportInfo)
        assertTrue(
            "ExportManager.getExporter for HEALTH_CONNECT must return NotYetImplementedExporter",
            exporter is NotYetImplementedExporter
        )
    }

    @Test
    fun testTrainingApplication_uploadToCommunityRoutesHealthConnect() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_SYNC_TO_HEALTH_CONNECT, false) } returns true
        assertTrue(TrainingApplication.uploadToCommunity(FileFormat.HEALTH_CONNECT))

        every { mockPrefs.getBoolean(TrainingApplication.SP_SYNC_TO_HEALTH_CONNECT, false) } returns false
        org.junit.Assert.assertFalse(TrainingApplication.uploadToCommunity(FileFormat.HEALTH_CONNECT))
    }
}
