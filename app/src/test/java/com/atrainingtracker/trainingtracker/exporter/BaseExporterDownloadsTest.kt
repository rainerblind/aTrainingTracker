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

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.atrainingtracker.trainingtracker.exporter.writer.BaseFileWriter
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream

/**
 * Unit tests verifying FileFormat MIME types, Downloads directory copying policy,
 * internal upload privacy protection, and MediaStore null safety
 * (REQ-EXP-022, TST-EXP-019.3, ATT-4013).
 */
class BaseExporterDownloadsTest {

    private lateinit var mockContext: Context
    private lateinit var mockResolver: ContentResolver
    private lateinit var tempDir: File

    private class TestFileWriter(
        context: Context,
        val downloadsSupported: Boolean = true
    ) : BaseFileWriter(context) {
        override fun isDownloadsCopySupported(): Boolean = downloadsSupported

        override fun doExport(exportInfo: ExportInfo): ExportResult {
            val file = getOutputFile(exportInfo)
            file.parentFile?.mkdirs()
            file.writeText("test dummy export content")
            return ExportResult(true, false, "Success")
        }

        public override fun onFinished(exportInfo: ExportInfo) {
            super.onFinished(exportInfo)
        }

        public override fun copyFileToDownloads(exportInfo: ExportInfo) {
            super.copyFileToDownloads(exportInfo)
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit

        tempDir = java.nio.file.Files.createTempDirectory("downloads_export_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        mockResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockResolver
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    @Test
    fun testFileFormat_getMimeType_returnsExpectedStandards() {
        assertEquals("application/gpx+xml", FileFormat.GPX.mimeType)
        assertEquals("application/vnd.garmin.tcx+xml", FileFormat.TCX.mimeType)
        assertEquals("application/vnd.garmin.tcx+xml", FileFormat.STRAVA.mimeType)
        assertEquals("application/vnd.ant.fit", FileFormat.FIT.mimeType)
        assertEquals("text/csv", FileFormat.CSV.mimeType)
        assertEquals("application/json", FileFormat.GC.mimeType)
    }

    @Test
    fun testBaseFileWriter_onFinished_skipsDownloadsForStravaAndCommunity() {
        val writer = spyk(TestFileWriter(mockContext, downloadsSupported = true))
        every { writer.copyFileToDownloads(any()) } just Runs

        // 1. Explicit file export (GPX) -> calls copyFileToDownloads on Android Q+
        val gpxFileExport = ExportInfo("gpx_test", FileFormat.GPX, ExportType.FILE)
        writer.onFinished(gpxFileExport)
        verify(exactly = 1) { writer.copyFileToDownloads(gpxFileExport) }

        // 2. Strava format file export -> SKIPPED (privacy protection)
        clearMocks(writer, answers = false)
        val stravaExport = ExportInfo("strava_test", FileFormat.STRAVA, ExportType.FILE)
        writer.onFinished(stravaExport)
        verify(exactly = 0) { writer.copyFileToDownloads(any()) }

        // 3. Community upload type (e.g. TCX community upload) -> SKIPPED (privacy protection)
        clearMocks(writer, answers = false)
        val communityExport = ExportInfo("community_test", FileFormat.TCX, ExportType.COMMUNITY)
        writer.onFinished(communityExport)
        verify(exactly = 0) { writer.copyFileToDownloads(any()) }
    }

    @Test
    fun testBaseFileWriter_onFinished_skipsDownloadsWhenNotSupported() {
        val writer = spyk(TestFileWriter(mockContext, downloadsSupported = false))
        every { writer.copyFileToDownloads(any()) } just Runs

        val gpxFileExport = ExportInfo("gpx_test", FileFormat.GPX, ExportType.FILE)
        writer.onFinished(gpxFileExport)
        verify(exactly = 0) { writer.copyFileToDownloads(any()) }
    }

    @Test
    fun testCopyFileToDownloads_usesCorrectMimeTypeInContentValues() {
        val writer = TestFileWriter(mockContext)
        val exportInfo = ExportInfo("test_fit", FileFormat.FIT, ExportType.FILE)

        val outputFile = writer.getOutputFile(exportInfo)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("binary fit dummy")

        val stringPuts = mutableMapOf<String, String>()
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            val key = args[0] as? String
            val value = args[1] as? String
            if (key != null && value != null) {
                stringPuts[key] = value
            }
        }

        val mockUri = mockk<Uri>()
        every {
            mockResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, any())
        } returns mockUri
        val dummyOut = mockk<OutputStream>(relaxed = true)
        every { mockResolver.openOutputStream(mockUri) } returns dummyOut

        writer.copyFileToDownloads(exportInfo)

        verify(exactly = 1) { mockResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, any()) }
        assertEquals("application/vnd.ant.fit", stringPuts[MediaStore.MediaColumns.MIME_TYPE])
        assertEquals(outputFile.name, stringPuts[MediaStore.MediaColumns.DISPLAY_NAME])
        assertEquals(Environment.DIRECTORY_DOWNLOADS, stringPuts[MediaStore.MediaColumns.RELATIVE_PATH])
    }

    @Test
    fun testCopyFileToDownloads_handlesNullTargetUriGracefully() {
        val writer = TestFileWriter(mockContext)
        val exportInfo = ExportInfo("test_null_uri", FileFormat.CSV, ExportType.FILE)

        val outputFile = writer.getOutputFile(exportInfo)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("csv,dummy")

        // MediaStore insert returns null
        every {
            mockResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, any())
        } returns null

        // Must not throw NullPointerException or crash
        writer.copyFileToDownloads(exportInfo)

        verify(exactly = 1) { mockResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, any()) }
        verify(exactly = 0) { mockResolver.openOutputStream(any()) }
    }
}
