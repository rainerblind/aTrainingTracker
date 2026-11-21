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

package com.atrainingtracker.banalservice.devices.raw

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Pure-JVM tests verifying lossless 8-channel round-trip and truncated-EOF resilience in
 * [PhoneRawTelemetryReader] for downstream offline replay (`ATT-9349`, REQ-EXP-045.2, TST-EXP-045.2).
 */
// TST-EXP-045.2
class PhoneRawTelemetryReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun roundTripAllEightChannelsAndMetadata_pureJvm_TST_EXP_045_2() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_100000.phone_raw.csv.gz")
        val meta = PhoneRawMetadata(
            schemaVersion = 1,
            appVersion = "5.0.0-rc1",
            deviceModel = "Google \"Pixel\" 9",
            sportId = 12L,
            sessionStartTimeMs = 1_760_123_456_000L
        )
        val inputRows = listOf(
            PhoneRawTelemetryRow(1000L, 1_760_123_456_020L, PhoneRawChannel.ACC, 0.12, -0.34, 9.81),
            PhoneRawTelemetryRow(2000L, 1_760_123_456_040L, PhoneRawChannel.GYRO, 0.05, -0.02, 0.11),
            PhoneRawTelemetryRow(3000L, 1_760_123_456_060L, PhoneRawChannel.ROTVEC, 0.1, 0.2, 0.3, 0.9273618),
            PhoneRawTelemetryRow(4000L, 1_760_123_456_080L, PhoneRawChannel.BARO, 1005.25, 66.8),
            PhoneRawTelemetryRow(5000L, 1_760_123_456_100L, PhoneRawChannel.STEP),
            PhoneRawTelemetryRow(6000L, 1_760_123_456_120L, PhoneRawChannel.GNSS, 48.137154, 11.576124, 519.4, 3.45, 182.5, 4.2),
            PhoneRawTelemetryRow(7000L, 1_760_123_456_140L, PhoneRawChannel.WHEEL, 8.33, 0.05),
            PhoneRawTelemetryRow(8000L, 1_760_123_456_160L, PhoneRawChannel.STATE, 2.0, 1.0, 5.0)
        )

        val recorder = SmartphoneRawTelemetryRecorder(targetFile = target, metadata = meta)
        recorder.start()
        inputRows.forEach { recorder.record(it) }
        recorder.stop()

        val parsed = PhoneRawTelemetryReader.readAll(target)
        assertNotNull(parsed.metadata)
        assertEquals(meta, parsed.metadata)
        assertFalse(parsed.truncatedEof)
        assertEquals(0, parsed.skippedMalformedLines)
        assertEquals(inputRows.size, parsed.rows.size)

        for (i in inputRows.indices) {
            val expected = inputRows[i]
            val actual = parsed.rows[i]
            assertEquals(expected.elapsedRealtimeNs, actual.elapsedRealtimeNs)
            assertEquals(expected.epochMs, actual.epochMs)
            assertEquals(expected.channel, actual.channel)
            if (expected.v0.isFinite()) assertEquals(expected.v0, actual.v0, 1e-6) else assertTrue(actual.v0.isNaN())
            if (expected.v1.isFinite()) assertEquals(expected.v1, actual.v1, 1e-6) else assertTrue(actual.v1.isNaN())
            if (expected.v2.isFinite()) assertEquals(expected.v2, actual.v2, 1e-6) else assertTrue(actual.v2.isNaN())
            if (expected.v3.isFinite()) assertEquals(expected.v3, actual.v3, 1e-6) else assertTrue(actual.v3.isNaN())
            if (expected.v4.isFinite()) assertEquals(expected.v4, actual.v4, 1e-6) else assertTrue(actual.v4.isNaN())
            if (expected.v5.isFinite()) assertEquals(expected.v5, actual.v5, 1e-6) else assertTrue(actual.v5.isNaN())
        }
    }

    @Test
    fun truncatedGzipEof_isHandledGracefullyWithIntactRowsPreserved_TST_EXP_045_2() {
        val truncatedFile = File(tempFolder.root, "truncated.phone_raw.csv.gz")
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos, true).use { gzip ->
            val writer = OutputStreamWriter(gzip, StandardCharsets.UTF_8)
            writer.write("${PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "TestPhone", sportId = 4L, sessionStartTimeMs = 1234L).toHeaderLine()}\n")
            writer.write("${PhoneRawTelemetryRow.CSV_HEADER}\n")
            for (i in 1..30) {
                writer.write("${PhoneRawTelemetryRow(i.toLong(), 1000L + i, PhoneRawChannel.ACC, 0.0, 0.0, 9.81).toCsvLine()}\n")
            }
            writer.flush()
        }
        val bytes = baos.toByteArray()
        truncatedFile.writeBytes(bytes.copyOfRange(0, bytes.size - 10))

        val parsed = PhoneRawTelemetryReader.readAll(truncatedFile)
        assertTrue("Reader must flag truncatedEof=true", parsed.truncatedEof)
        val parsedMeta = requireNotNull(parsed.metadata)
        assertEquals("TestPhone", parsedMeta.deviceModel)
        assertTrue("Reader must preserve complete pre-truncation rows (got ${parsed.rows.size})", parsed.rows.size >= 25)
    }
}
