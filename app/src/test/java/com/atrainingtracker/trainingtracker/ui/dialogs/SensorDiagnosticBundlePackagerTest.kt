/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests verifying [SensorDiagnosticBundlePackager] zip archive creation, entry layout,
 * and metadata generation (REQ-EXP-044.2, TST-EXP-044.3).
 */
// TST-EXP-044.3
@RunWith(RobolectricTestRunner::class)
class SensorDiagnosticBundlePackagerTest {

    private lateinit var context: Context
    private val testBaseName = "2026-11-16_14-30-00"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseName)
        sidecar.parentFile?.mkdirs()
        sidecar.writeText("test_sidecar_content")
    }

    @After
    fun tearDown() {
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseName)
        if (sidecar.exists()) sidecar.delete()
        val diagDir = File(context.cacheDir, "diagnostics")
        diagDir.deleteRecursively()
    }

    @Test
    fun testCreateBundle_createsZipWithAllEntries() = runBlocking {
        // TST-EXP-044.3
        val result = SensorDiagnosticBundlePackager.createBundle(
            context = context,
            fileBaseName = testBaseName,
            includeGps = true,
            symptomNotes = "Heart rate spike during interval 2"
        )

        assertTrue(result.isSuccess)
        val zipFile = result.getOrThrow()
        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0)
        assertEquals("sensor_diagnostic_$testBaseName.zip", zipFile.name)

        ZipFile(zipFile).use { zf ->
            assertNotNull(zf.getEntry("$testBaseName.polar_raw.csv.gz"))
            assertNotNull(zf.getEntry("workout_summary.json"))
            assertNotNull(zf.getEntry("laps.json"))
            assertNotNull(zf.getEntry("samples_1hz.csv"))
            assertNotNull(zf.getEntry("diagnostic_metadata.json"))

            val metaEntry = zf.getEntry("diagnostic_metadata.json")
            val metaString = zf.getInputStream(metaEntry).bufferedReader().readText()
            val metaJson = JSONObject(metaString)

            assertEquals(testBaseName, metaJson.getString("fileBaseName"))
            assertEquals("Heart rate spike during interval 2", metaJson.getString("symptomNotes"))
            assertTrue(metaJson.getBoolean("includeGps"))
            assertTrue(metaJson.has("appVersion"))
            assertTrue(metaJson.has("androidVersion"))
        }
    }
}
