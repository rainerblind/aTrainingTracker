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
}
