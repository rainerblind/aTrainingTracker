/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.dropbox.core.DbxException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DbxUserFilesRequests
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.UploadBuilder
import com.dropbox.core.v2.files.WriteMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * Unit tests verifying file existence validation, OAuth credential checking,
 * client initialization, upload dispatching, and error handling in [DropboxUploader]
 * (REQ-TST-001.4, TST-TST-001.4-6).
 */
class DropboxUploaderTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var uploader: DropboxUploader

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false

        tempDir = Files.createTempDirectory("dropbox_uploader_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        uploader = DropboxUploader(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createLocalFile(relativePath: String, content: String): File {
        val file = File(tempDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun testDoExport_missingLocalFile_returnsError() {
        // Given: ExportInfo referencing a non-existent file in filesDir
        val exportInfo = ExportInfo("missing_file", FileFormat.CSV, ExportType.DROPBOX)

        // When: doExport runs
        val result = uploader.doExport(exportInfo)

        // Then:
        assertFalse("Export must fail when file does not exist", result.success())
        assertTrue("Error message must mention missing file", result.answer().contains("Dropbox file does not exist"))
    }

    @Test
    fun testDoExport_nullCredential_returnsError() {
        // Given: Local file exists, but Dropbox credential is null
        val exportInfo = ExportInfo("workout_test", FileFormat.CSV, ExportType.DROPBOX)
        createLocalFile(exportInfo.shortPath, "sample data")

        every { TrainingApplication.readDropboxCredential() } returns null

        // When: doExport runs
        val result = uploader.doExport(exportInfo)

        // Then:
        assertFalse("Export must fail when credential is null", result.success())
        assertTrue("Error message must indicate null credential", result.answer().contains("Dropbox credential is null"))
    }

    @Test
    fun testDoExport_successfulUpload() {
        // Given: Local file exists and valid credential returned
        val exportInfo = ExportInfo("workout_success", FileFormat.CSV, ExportType.DROPBOX)
        createLocalFile(exportInfo.shortPath, "TIME_TOTAL,SPEED\n1,5.0\n")

        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockFiles = mockk<DbxUserFilesRequests>(relaxed = true)
        val mockUploadBuilder = mockk<UploadBuilder>(relaxed = true)
        val mockMetadata = mockk<FileMetadata>(relaxed = true)

        every { anyConstructed<DbxClientV2>().files() } returns mockFiles
        every { mockFiles.uploadBuilder("/" + exportInfo.shortPath) } returns mockUploadBuilder
        every { mockUploadBuilder.withMode(WriteMode.OVERWRITE) } returns mockUploadBuilder
        every { mockUploadBuilder.uploadAndFinish(any<InputStream>()) } returns mockMetadata

        // When: doExport runs
        val result = uploader.doExport(exportInfo)

        // Then:
        assertTrue("Export must succeed on clean upload", result.success())
        assertTrue("Success message must confirm upload", result.answer().contains("successfully uploaded"))
    }

    @Test
    fun testDoExport_dbxException_returnsError() {
        // Given: Local file and credential exist, but Dropbox client throws DbxException
        val exportInfo = ExportInfo("workout_error_dbx", FileFormat.CSV, ExportType.DROPBOX)
        createLocalFile(exportInfo.shortPath, "sample content")

        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockFiles = mockk<DbxUserFilesRequests>(relaxed = true)
        val mockUploadBuilder = mockk<UploadBuilder>(relaxed = true)

        every { anyConstructed<DbxClientV2>().files() } returns mockFiles
        every { mockFiles.uploadBuilder(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.withMode(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.uploadAndFinish(any<InputStream>()) } throws NetworkIOException(IOException("Connection reset"))

        // When: doExport runs
        val result = uploader.doExport(exportInfo)

        // Then:
        assertFalse("Export must fail when DbxException is thrown", result.success())
        assertTrue("Message must mention DropboxException", result.answer().contains("DropboxException"))
    }

    @Test
    fun testDoExport_ioException_returnsError() {
        // Given: Local file and credential exist, but stream upload throws IOException
        val exportInfo = ExportInfo("workout_error_io", FileFormat.CSV, ExportType.DROPBOX)
        createLocalFile(exportInfo.shortPath, "sample content")

        val mockCredential = mockk<DbxCredential>(relaxed = true)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential

        mockkConstructor(DbxClientV2::class)
        val mockFiles = mockk<DbxUserFilesRequests>(relaxed = true)
        val mockUploadBuilder = mockk<UploadBuilder>(relaxed = true)

        every { anyConstructed<DbxClientV2>().files() } returns mockFiles
        every { mockFiles.uploadBuilder(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.withMode(any()) } returns mockUploadBuilder
        every { mockUploadBuilder.uploadAndFinish(any<InputStream>()) } throws IOException("Disk read error")

        // When: doExport runs
        val result = uploader.doExport(exportInfo)

        // Then:
        assertFalse("Export must fail when IOException is thrown", result.success())
        assertTrue("Message must mention IOException", result.answer().contains("IOException"))
    }
}
