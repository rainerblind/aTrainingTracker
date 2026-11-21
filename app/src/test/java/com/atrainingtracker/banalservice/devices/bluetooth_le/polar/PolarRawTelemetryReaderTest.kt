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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Pure-JVM unit verification for Polar raw telemetry reader and parser
 * (TST-SET-119.2 / REQ-SET-117.2, TST-EXP-046.1 / REQ-EXP-046.1).
 */
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

    // TST-EXP-046.1
    @Test
    fun metadata_roundTripWithEscapedCharactersAndUnicode_pureJvm() {
        val original = PolarRawMetadata(
            schemaVersion = 1,
            appVersion = "5.0.0-\"beta\"\\rc1",
            sensorModel = "Polar \"H10\" \\ Pro",
            firmwareVersion = "3.2.0\tbuild",
            streamMode = "OFFLINE",
            accRateHz = 200,
            gyroRateHz = 52,
            sportId = 1,
            poolLengthM = null
        )
        val json = original.toJsonString()
        val parsed = PolarRawMetadata.fromJsonString(json)
        assertEquals(original, parsed)
        assertNull(parsed.poolLengthM)

        // Verify \uXXXX unicode escape support matches org.json.JSONObject parity
        val unicodeParsed = PolarRawMetadata.fromJsonString(
            """{"schemaVersion":1,"sensorModel":"Polar H10 \u00B0C"}"""
        )
        assertEquals("Polar H10 °C", unicodeParsed.sensorModel)
    }

    // TST-EXP-046.1
    @Test
    fun metadata_malformedJson_fallsBackToDefaultsWithoutException() {
        val malformed1 = PolarRawMetadata.fromJsonString("{broken json without closing")
        assertEquals(PolarRawMetadata(), malformed1)

        val malformed2 = PolarRawMetadata.fromJsonString("not-even-json")
        assertEquals(PolarRawMetadata(), malformed2)
    }

    // TST-EXP-046.1
    @Test
    fun readFile_truncatedGzip_salvagesCompleteLinesAndDropsPartialTrailingRow() {
        val fullBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos, true).use { gzipOut ->
                val writer = BufferedWriter(OutputStreamWriter(gzipOut, StandardCharsets.UTF_8))
                val metadata = PolarRawMetadata(sensorModel = "Polar H10 9999", accRateHz = 200)
                writer.write("# metadata: ${metadata.toJsonString()}\n")
                writer.write("${RawTelemetryRow.CSV_HEADER}\n")
                writer.write("1000000000,0,ACC,10,20,980,,,,,,\n")
                writer.write("1005000000,5,ACC,15,25,985,,,,,,\n")
                writer.flush() // syncFlush flushes complete block to stream
                // Write a trailing row WITHOUT newline to simulate crash-truncated partial line
                writer.write("1010000000,10,ACC,100,200,9")
                writer.flush()
            }
            baos.toByteArray()
        }

        // Chop off the final 8 bytes (GZIP CRC/ISIZE trailer) to simulate abrupt EOF
        val truncatedFile = tempFolder.newFile("truncated.polar_raw.csv.gz")
        FileOutputStream(truncatedFile).use { fos ->
            fos.write(fullBytes, 0, fullBytes.size - 8)
        }

        val session = PolarRawTelemetryReader.readFile(truncatedFile)
        assertEquals("Polar H10 9999", session.metadata.sensorModel)
        assertEquals(200, session.metadata.accRateHz)
        assertEquals("Only complete newline-terminated rows should be salvaged", 2, session.rows.size)
        assertEquals(0L, session.rows[0].elapsedMs)
        assertEquals(5L, session.rows[1].elapsedMs)
    }
}
