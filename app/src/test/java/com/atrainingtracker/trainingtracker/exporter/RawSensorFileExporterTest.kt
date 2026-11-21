/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryRecorder
import com.atrainingtracker.trainingtracker.exporter.writer.RawSensorFileExporter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests verifying [RawSensorFileExporter] sidecar export and graceful skip policy (REQ-EXP-044.1).
 */
// TST-EXP-044.1
@RunWith(RobolectricTestRunner::class)
class RawSensorFileExporterTest {

    private lateinit var context: Context
    private lateinit var exporter: RawSensorFileExporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exporter = RawSensorFileExporter(context)
    }

    @After
    fun tearDown() {
        val rawDir = File(context.filesDir, "raw_sensor")
        if (rawDir.exists()) rawDir.deleteRecursively()
    }

    @Test
    fun testMimeTypeAndFormatProperties() {
        // TST-EXP-044.1
        assertEquals("application/gzip", FileFormat.RAW_SENSOR.mimeType)
        assertEquals(".polar_raw.csv.gz", FileFormat.RAW_SENSOR.fileEnding)
        assertEquals("raw_sensor", FileFormat.RAW_SENSOR.dirName)
        assertTrue(FileFormat.STANDARD_FILE_FORMATS.contains(FileFormat.RAW_SENSOR))
    }

    @Test
    fun testDoExport_whenSidecarDoesNotExist_gracefulSkip() {
        // TST-EXP-044.1
        val exportInfo = ExportInfo("workout_nonexistent_123", FileFormat.RAW_SENSOR, ExportType.FILE)
        val result = exporter.export(exportInfo)

        assertTrue(result.success())
        assertEquals("Raw sensor sidecar does not exist (graceful skip)", result.answer())
    }

    @Test
    fun testDoExport_whenSidecarExists_exportsSuccessfully() {
        // TST-EXP-044.1
        val rawDir = File(context.filesDir, "raw_sensor").apply { mkdirs() }
        val sidecarFile = File(rawDir, "workout_exists_456.polar_raw.csv.gz")
        sidecarFile.writeBytes(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00)) // GZIP header

        val exportInfo = ExportInfo("workout_exists_456", FileFormat.RAW_SENSOR, ExportType.FILE)
        val result = exporter.export(exportInfo)

        assertTrue(result.success())
        assertEquals("Successfully exported raw sensor sidecar", result.answer())
    }

    // REQ-STB-025.2 / TST-STB-025.2: Export fallback salvages abandoned crash-truncated .polar_raw.csv.gz.tmp
    @Test
    fun testDoExport_whenAbandonedTmpExists_salvagesToFinalSidecarAndExports_TST_STB_025_2() {
        val fileBaseName = "workout_abandoned_tmp_2422"
        val rawDir = File(context.filesDir, "raw_sensor").apply { mkdirs() }
        val tmpFile = File(rawDir, "$fileBaseName.polar_raw.csv.gz.tmp")

        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(baos, true).use { gzip ->
            val writer = java.io.OutputStreamWriter(gzip, java.nio.charset.StandardCharsets.UTF_8)
            writer.write("# metadata: {\"schemaVersion\":1,\"sensorModel\":\"Polar Verity Sense\",\"streamMode\":\"LIVE\"}\n")
            writer.write("sensorTimestampNs,elapsedMs,channel,axMg,ayMg,azMg,gxMdps,gyMdps,gzMdps,hrBpm,rrMs\n")
            for (i in 1..25) {
                writer.write("${1_760_000_000_000_000_000L + i * 20_000_000L},${i * 20L},ACC,$i,0,1000,,,,,\n")
            }
            writer.flush()
            writer.write("1760000000999999999,999,GYRO,") // partial crash-truncated tail
            writer.flush()
        }
        val bytes = baos.toByteArray()
        tmpFile.writeBytes(bytes.copyOfRange(0, bytes.size - 10))

        val exportInfo = ExportInfo(fileBaseName, FileFormat.RAW_SENSOR, ExportType.FILE)
        val result = exporter.export(exportInfo)

        assertTrue("Export must succeed after salvaging abandoned .tmp", result.success())
        assertEquals("Successfully exported raw sensor sidecar", result.answer())

        val finalSidecar = PolarRawTelemetryRecorder.getSidecarFile(context, fileBaseName)
        assertTrue("Salvaged .polar_raw.csv.gz must exist on disk", finalSidecar.exists())
        val parsed = com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryReader.readFile(finalSidecar)
        assertEquals("Polar Verity Sense", parsed.metadata.sensorModel)
        assertTrue("Salvaged sidecar must contain complete pre-crash rows (got ${parsed.rows.size})", parsed.rows.size >= 20)
    }
}
