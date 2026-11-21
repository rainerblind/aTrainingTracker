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
 * Unit tests verifying [GoogleDriveUploader] graceful skip behavior for raw Polar sidecar exports (REQ-EXP-044.1, TST-EXP-044.2).
 */
// TST-EXP-044.2
@RunWith(RobolectricTestRunner::class)
class GoogleDriveUploaderRawSensorTest {

    private lateinit var context: Context
    private lateinit var uploader: GoogleDriveUploader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        uploader = GoogleDriveUploader(context)
    }

    @After
    fun tearDown() {
        val rawDir = File(context.filesDir, "raw_sensor")
        if (rawDir.exists()) rawDir.deleteRecursively()
    }

    @Test
    fun testGoogleDriveSupportsRawSensor() {
        // TST-EXP-044.2
        assertTrue(ExportType.GOOGLE_DRIVE.exportToFileFormats.contains(FileFormat.RAW_SENSOR))
    }

    @Test
    fun testDoExport_whenRawSensorMissing_gracefulSkip() {
        // TST-EXP-044.2
        val exportInfo = ExportInfo("workout_raw_missing", FileFormat.RAW_SENSOR, ExportType.GOOGLE_DRIVE)
        val result = uploader.export(exportInfo)

        assertTrue(result.success())
        assertEquals("Raw sensor sidecar does not exist (graceful skip)", result.answer())
    }

    @Test
    fun testDoExport_whenStandardFormatMissing_fails() {
        // TST-EXP-044.2
        val exportInfo = ExportInfo("workout_gpx_missing", FileFormat.GPX, ExportType.GOOGLE_DRIVE)
        val result = uploader.export(exportInfo)

        assertFalse(result.success())
        assertTrue(result.answer().contains("Google Drive file does not exist"))
    }

    // REQ-EXP-045.3 / TST-EXP-045.3: Multi-file (Phone-only, Both) upload and partial-failure non-masking
    @Test
    fun testDoExport_multiFileRawSensorAndPartialFailure_TST_EXP_045_3() {
        val rawDir = File(context.filesDir, "raw_sensor").apply { mkdirs() }
        val base = "workout_gdrive_both_9348"
        File(rawDir, "$base.polar_raw.csv.gz").writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))
        File(rawDir, "$base.phone_raw.csv.gz").writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00))

        val uploadedNames = mutableListOf<String>()
        var failName: String? = null
        val mockClient = mockk<com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient>(relaxed = true)
        every { mockClient.ensureFolderHierarchy(any()) } returns "folder-raw-123"
        every { mockClient.lastHttpCode } answers { if (failName != null) 503 else 200 }
        every { mockClient.uploadOrOverwriteFile(any(), any(), any(), any()) } answers {
            val name = secondArg<String>()
            if (name == failName) {
                false
            } else {
                uploadedNames.add(name)
                true
            }
        }

        io.mockk.mockkStatic(com.atrainingtracker.trainingtracker.TrainingApplication::class)
        try {
            every { com.atrainingtracker.trainingtracker.TrainingApplication.uploadToGoogleDrive() } returns true
            every { com.atrainingtracker.trainingtracker.TrainingApplication.getGoogleDriveAuthToken() } returns "token-123"
            every { com.atrainingtracker.trainingtracker.TrainingApplication.setGoogleDriveLastSyncTimestamp(any()) } returns Unit
            every { com.atrainingtracker.trainingtracker.TrainingApplication.setGoogleDriveLastSyncStatus(any()) } returns Unit

            val testUploader = object : GoogleDriveUploader(context) {
                override fun createClient() = mockClient
            }

            // Both present -> uploads both
            val bothRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.GOOGLE_DRIVE))
            assertTrue(bothRes.success())
            assertEquals(listOf("$base.polar_raw.csv.gz", "$base.phone_raw.csv.gz"), uploadedNames)

            // Partial failure (2nd file returns false with HTTP 503) -> must NOT mask failure!
            uploadedNames.clear()
            failName = "$base.phone_raw.csv.gz"
            val partialFailRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.GOOGLE_DRIVE))
            assertFalse("Partial failure must return success=false", partialFailRes.success())
            assertTrue("HTTP 503 failure must request retry", partialFailRes.shallRetry())

            // Phone-only present -> uploads phone sidecar cleanly
            uploadedNames.clear()
            failName = null
            File(rawDir, "$base.polar_raw.csv.gz").delete()
            val phoneOnlyRes = testUploader.export(ExportInfo(base, FileFormat.RAW_SENSOR, ExportType.GOOGLE_DRIVE))
            assertTrue(phoneOnlyRes.success())
            assertEquals(listOf("$base.phone_raw.csv.gz"), uploadedNames)
        } finally {
            unmockkAll()
        }
    }
}
