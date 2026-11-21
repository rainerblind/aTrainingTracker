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
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.SensorDiagnosticBundlePackager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests verifying strict GPS coordinate stripping and privacy toggle enforcement in
 * [SensorDiagnosticBundlePackager] (REQ-EXP-044.3, TST-EXP-044.4).
 */
// TST-EXP-044.4
@RunWith(RobolectricTestRunner::class)
class SensorDiagnosticBundlePrivacyTest {

    private lateinit var context: Context
    private val testBaseName = "2026-11-16_15-00-00"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseName)
        sidecar.parentFile?.mkdirs()
        sidecar.writeText("test_sidecar_content")

        val db = WorkoutSamplesDatabaseManager.getInstance(context).database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(testBaseName)
        db.execSQL("DROP TABLE IF EXISTS $tableName")
        db.execSQL("CREATE TABLE $tableName (TIME_S INTEGER, HEART_RATE INTEGER, LATITUDE REAL, LONGITUDE REAL)")
        db.execSQL("INSERT INTO $tableName VALUES (1, 142, 48.137154, 11.576124)")
    }

    @After
    fun tearDown() {
        val sidecar = PolarRawTelemetryRecorder.getSidecarFile(context, testBaseName)
        if (sidecar.exists()) sidecar.delete()
        val diagDir = File(context.cacheDir, "diagnostics")
        diagDir.deleteRecursively()

        val db = WorkoutSamplesDatabaseManager.getInstance(context).database
        val tableName = WorkoutSamplesDatabaseManager.getTableName(testBaseName)
        db.execSQL("DROP TABLE IF EXISTS $tableName")
    }

    @Test
    fun testGPSStripping_whenOptedOut_omitsCoordinatesFromCsvAndSummary() = runBlocking {
        // TST-EXP-044.4
        val result = SensorDiagnosticBundlePackager.createBundle(
            context = context,
            fileBaseName = testBaseName,
            includeGps = false,
            symptomNotes = "Testing GPS stripping"
        )

        assertTrue(result.isSuccess)
        val zipFile = result.getOrThrow()

        ZipFile(zipFile).use { zf ->
            // Check samples_1hz.csv
            val csvEntry = zf.getEntry("samples_1hz.csv")
            val csvText = zf.getInputStream(csvEntry).bufferedReader().readText()

            assertFalse("CSV must NOT contain LATITUDE header when includeGps=false", csvText.contains("LATITUDE"))
            assertFalse("CSV must NOT contain LONGITUDE header when includeGps=false", csvText.contains("LONGITUDE"))
            assertFalse("CSV must NOT contain coordinate values", csvText.contains("48.137154"))
            assertFalse("CSV must NOT contain coordinate values", csvText.contains("11.576124"))
            assertTrue("CSV must contain HEART_RATE", csvText.contains("HEART_RATE"))
            assertTrue("CSV must contain heart rate data", csvText.contains("142"))

            // Check workout_summary.json
            val summaryEntry = zf.getEntry("workout_summary.json")
            val summaryText = zf.getInputStream(summaryEntry).bufferedReader().readText()
            val summaryJson = JSONObject(summaryText)
            if (summaryJson.has("mapPolyline")) {
                assertEquals("", summaryJson.getString("mapPolyline"))
            }

            // Check metadata
            val metaEntry = zf.getEntry("diagnostic_metadata.json")
            val metaText = zf.getInputStream(metaEntry).bufferedReader().readText()
            val metaJson = JSONObject(metaText)
            assertFalse(metaJson.getBoolean("includeGps"))
        }
    }

    @Test
    fun testGPSPreservation_whenOptedIn_includesCoordinates() = runBlocking {
        // TST-EXP-044.4
        val result = SensorDiagnosticBundlePackager.createBundle(
            context = context,
            fileBaseName = testBaseName,
            includeGps = true,
            symptomNotes = "Testing GPS inclusion"
        )

        assertTrue(result.isSuccess)
        val zipFile = result.getOrThrow()

        ZipFile(zipFile).use { zf ->
            val csvEntry = zf.getEntry("samples_1hz.csv")
            val csvText = zf.getInputStream(csvEntry).bufferedReader().readText()

            assertTrue("CSV must contain LATITUDE header when includeGps=true", csvText.contains("LATITUDE"))
            assertTrue("CSV must contain LONGITUDE header when includeGps=true", csvText.contains("LONGITUDE"))
            assertTrue("CSV must contain latitude value, actual: $csvText", csvText.contains("48.137"))
            assertTrue("CSV must contain longitude value, actual: $csvText", csvText.contains("11.576"))
            assertTrue("CSV must contain HEART_RATE", csvText.contains("HEART_RATE"))

            val metaEntry = zf.getEntry("diagnostic_metadata.json")
            val metaText = zf.getInputStream(metaEntry).bufferedReader().readText()
            val metaJson = JSONObject(metaText)
            assertTrue(metaJson.getBoolean("includeGps"))
        }
    }

    // REQ-EXP-045.4 / TST-EXP-045.4: .phone_raw.csv.gz omitted when includeGps=false, included when true
    @Test
    fun testPhoneRawSidecar_omittedWhenIncludeGpsFalseAndIncludedWhenTrue_TST_EXP_045_4() = runBlocking {
        val phoneSidecar = com.atrainingtracker.banalservice.devices.raw.SmartphoneRawTelemetryRecorder
            .getSidecarFile(context, testBaseName)!!
        phoneSidecar.parentFile?.mkdirs()
        phoneSidecar.writeText("phone_raw_with_gnss_48.137_11.576")

        try {
            val noGpsZip = SensorDiagnosticBundlePackager.createBundle(
                context = context,
                fileBaseName = testBaseName,
                includeGps = false,
                symptomNotes = "No GPS"
            ).getOrThrow()
            ZipFile(noGpsZip).use { zf ->
                assertTrue(
                    "Phone sidecar with GNSS MUST be omitted when includeGps=false",
                    zf.getEntry(phoneSidecar.name) == null
                )
            }

            val withGpsZip = SensorDiagnosticBundlePackager.createBundle(
                context = context,
                fileBaseName = testBaseName,
                includeGps = true,
                symptomNotes = "With GPS"
            ).getOrThrow()
            ZipFile(withGpsZip).use { zf ->
                assertTrue(
                    "Phone sidecar MUST be included when includeGps=true",
                    zf.getEntry(phoneSidecar.name) != null
                )
            }
        } finally {
            phoneSidecar.delete()
        }
    }
}
