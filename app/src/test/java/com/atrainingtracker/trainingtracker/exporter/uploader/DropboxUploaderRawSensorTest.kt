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
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests verifying [DropboxUploader] graceful skip behavior for raw Polar sidecar exports (REQ-EXP-044.1, TST-EXP-044.2).
 */
// TST-EXP-044.2
@RunWith(RobolectricTestRunner::class)
class DropboxUploaderRawSensorTest {

    private lateinit var context: Context
    private lateinit var uploader: DropboxUploader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        uploader = DropboxUploader(context)
    }

    @After
    fun tearDown() {
        val rawDir = File(context.filesDir, "raw_sensor")
        if (rawDir.exists()) rawDir.deleteRecursively()
    }

    @Test
    fun testDropboxSupportsRawSensor() {
        // TST-EXP-044.2
        assertTrue(ExportType.DROPBOX.exportToFileFormats.contains(FileFormat.RAW_SENSOR))
    }

    @Test
    fun testDoExport_whenRawSensorMissing_gracefulSkip() {
        // TST-EXP-044.2
        val exportInfo = ExportInfo("workout_raw_missing", FileFormat.RAW_SENSOR, ExportType.DROPBOX)
        val result = uploader.export(exportInfo)

        assertTrue(result.success())
        assertEquals("Raw sensor sidecar does not exist (graceful skip)", result.answer())
    }

    @Test
    fun testDoExport_whenStandardFormatMissing_fails() {
        // TST-EXP-044.2
        val exportInfo = ExportInfo("workout_tcx_missing", FileFormat.TCX, ExportType.DROPBOX)
        val result = uploader.export(exportInfo)

        assertFalse(result.success())
        assertTrue(result.answer().contains("Dropbox file does not exist"))
    }

    // REQ-EXP-045.3 / TST-EXP-045.3: Multi-file (Phone-only, Both) upload and partial-failure non-masking
    @Test
    fun testDoExport_multiFileRawSensorAndPartialFailure_TST_EXP_045_3() {
        val rawDir = File(context.filesDir, "raw_sensor").apply { mkdirs() }
        val base = "workout_dbx_both_9348"
        File(rawDir, "$base.polar_raw.csv.gz").writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))
        File(rawDir, "$base.phone_raw.csv.gz").writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))

        val uploadedPaths = mutableListOf<String>()
        var failOnPath: String? = null
        val mockClient = mockk<com.dropbox.core.v2.DbxClientV2>(relaxed = true)
        val mockFiles = mockk<com.dropbox.core.v2.files.DbxUserFilesRequests>(relaxed = true)
        every { mockClient.files() } returns mockFiles
        every { mockFiles.uploadBuilder(any()) } answers {
            val remotePath = firstArg<String>()
            val builder = mockk<com.dropbox.core.v2.files.UploadBuilder>(relaxed = true)
            every { builder.withMode(any()) } returns builder
            every { builder.uploadAndFinish(any()) } answers {
                if (remotePath == failOnPath) {
                    throw com.dropbox.core.NetworkIOException(java.io.IOException("simulated drop"))
                }
                uploadedPaths.add(remotePath)
                mockk(relaxed = true)
            }
            builder
        }

        io.mockk.mockkStatic(com.atrainingtracker.trainingtracker.TrainingApplication::class)
        try {
            every { com.atrainingtracker.trainingtracker.TrainingApplication.readDropboxCredential() } returns
                com.dropbox.core.oauth.DbxCredential("tok")
            every { com.atrainingtracker.trainingtracker.TrainingApplication.setDropboxLastSyncTimestamp(any()) } returns Unit

            val testUploader = object : DropboxUploader(context) {
                override fun createDbxClient(credential: com.dropbox.core.oauth.DbxCredential): com.dropbox.core.v2.DbxClientV2 = mockClient
            }

            // Both present -> uploads both
            val bothRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.DROPBOX))
            assertTrue(bothRes.success())
            assertEquals(
                listOf("/raw_sensor/$base.polar_raw.csv.gz", "/raw_sensor/$base.phone_raw.csv.gz"),
                uploadedPaths
            )

            // Partial failure (2nd file fails with transient NetworkIOException) -> must NOT mask failure!
            uploadedPaths.clear()
            failOnPath = "/raw_sensor/$base.phone_raw.csv.gz"
            val partialFailRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.DROPBOX))
            assertFalse("Partial failure must return success=false", partialFailRes.success())
            assertTrue("Transient Dropbox NetworkIOException must request retry", partialFailRes.shallRetry())

            // Phone-only present -> uploads phone sidecar cleanly
            uploadedPaths.clear()
            failOnPath = null
            File(rawDir, "$base.polar_raw.csv.gz").delete()
            val phoneOnlyRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.DROPBOX))
            assertTrue(phoneOnlyRes.success())
            assertEquals(listOf("/raw_sensor/$base.phone_raw.csv.gz"), uploadedPaths)
        } finally {
            unmockkAll()
        }
    }
}
