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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Unit verification for Polar raw telemetry reader and parser (TST-SET-119.2 / REQ-SET-117.2).
 */
@RunWith(RobolectricTestRunner::class)
class PolarRawTelemetryReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // TST-SET-119.2
    @Test
    fun readFile_emptyOrMissingFile_returnsEmptySession() {
        val nonExistent = File(tempFolder.root, "does_not_exist.csv.gz")
        val session1 = PolarRawTelemetryReader.readFile(nonExistent)
        assertTrue(session1.rows.isEmpty())

        val emptyFile = tempFolder.newFile("empty.csv.gz")
        val session2 = PolarRawTelemetryReader.readFile(emptyFile)
        assertTrue(session2.rows.isEmpty())
    }

    // TST-SET-119.2
    @Test
    fun readFile_validGzipSidecar_parsesMetadataAndRowsCorrectly() {
        val file = tempFolder.newFile("test_workout.polar_raw.csv.gz")
        val metadata = PolarRawMetadata(
            schemaVersion = 1,
            appVersion = "5.0.0",
            sensorModel = "Polar Verity Sense 98765432",
            streamMode = "LIVE",
            accRateHz = 52,
            gyroRateHz = 52,
            sportId = 2,
            poolLengthM = 25.0f
        )

        FileOutputStream(file).use { fos ->
            GZIPOutputStream(fos).use { gzipOut ->
                BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8)).use { writer ->
                    writer.write("# metadata: ${metadata.toJsonString()}")
                    writer.newLine()
                    writer.write(RawTelemetryRow.CSV_HEADER)
                    writer.newLine()
                    writer.write("1000000000,0,ACC,10,-20,990,,,,,,")
                    writer.newLine()
                    writer.write("1020000000,20,GYRO,,,,0.50,-1.20,0.10,,")
                    writer.newLine()
                    writer.write("1040000000,40,HR,,,,,,,138,")
                    writer.newLine()
                    writer.write("1060000000,60,RR,,,,,,,,720.0")
                    writer.newLine()
                }
            }
        }

        val session = PolarRawTelemetryReader.readFile(file)
        assertEquals(1, session.metadata.schemaVersion)
        assertEquals("5.0.0", session.metadata.appVersion)
        assertEquals("Polar Verity Sense 98765432", session.metadata.sensorModel)
        assertEquals(52, session.metadata.accRateHz)
        assertEquals(52, session.metadata.gyroRateHz)
        assertEquals(2, session.metadata.sportId)
        assertEquals(25.0f, session.metadata.poolLengthM)

        assertEquals(4, session.rows.size)
        assertEquals(RawTelemetryChannel.ACC, session.rows[0].channel)
        assertEquals(10, session.rows[0].axMg)
        assertEquals(-20, session.rows[0].ayMg)
        assertEquals(990, session.rows[0].azMg)

        assertEquals(RawTelemetryChannel.GYRO, session.rows[1].channel)
        assertEquals(0.50f, session.rows[1].gxMdps)
        assertEquals(-1.20f, session.rows[1].gyMdps)
        assertEquals(0.10f, session.rows[1].gzMdps)

        assertEquals(RawTelemetryChannel.HR, session.rows[2].channel)
        assertEquals(138, session.rows[2].hrBpm)

        assertEquals(RawTelemetryChannel.RR, session.rows[3].channel)
        assertEquals(720.0, session.rows[3].rrMs!!, 0.01)
    }

    // TST-SET-119.2
    @Test
    fun readFile_corruptGzip_handledGracefullyWithoutException() {
        val file = tempFolder.newFile("corrupt.csv.gz")
        FileOutputStream(file).use { fos ->
            fos.write("Not a gzip file at all".toByteArray(StandardCharsets.UTF_8))
        }

        val session = PolarRawTelemetryReader.readFile(file)
        assertNotNull(session)
        assertTrue(session.rows.isEmpty())
    }
}
