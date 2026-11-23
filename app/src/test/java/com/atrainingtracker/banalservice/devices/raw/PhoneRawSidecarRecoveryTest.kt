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
 * Pure-JVM tests for [PhoneRawSidecarRecovery] crash salvage and seamless resume
 * (REQ-EXP-045.2, TST-EXP-045.2).
 */
// TST-EXP-045.2
class PhoneRawSidecarRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun recoverAbandonedTmpToFinal_salvagesIntactPreCrashRowsAndDropsPartialTail_TST_EXP_045_2() {
        val target = File(tempFolder.root, "2026-11-21_090000.phone_raw.csv.gz")
        val tmp = File(tempFolder.root, "2026-11-21_090000.phone_raw.csv.gz.tmp")

        val meta = PhoneRawMetadata(
            schemaVersion = 1,
            appVersion = "5.0.0",
            deviceModel = "Pixel 9 Pro",
            sportId = 7L,
            sessionStartTimeMs = 1_760_000_000_000L
        )
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos, true).use { gzip ->
            val writer = OutputStreamWriter(gzip, StandardCharsets.UTF_8)
            writer.write("${meta.toHeaderLine()}\n")
            writer.write("${PhoneRawTelemetryRow.CSV_HEADER}\n")
            for (i in 1..40) {
                val row = PhoneRawTelemetryRow(i * 20_000_000L, 1_760_000_000_000L + i * 20L, PhoneRawChannel.ACC, 0.1 * i, -0.2, 9.81)
                writer.write("${row.toCsvLine()}\n")
            }
            writer.flush()
            // Simulate partial crash-truncated tail without newline
            writer.write("999000000,1760000009999,GNSS,48.13,11.57,")
            writer.flush()
        }
        val bytes = baos.toByteArray()
        // Strip GZIP footer + tail bytes to simulate SIGKILL before close()
        tmp.writeBytes(bytes.copyOfRange(0, bytes.size - 12))

        assertTrue("Recovery must produce a valid final archive", PhoneRawSidecarRecovery.recoverAbandonedTmpToFinal(tmp, target))
        assertTrue(target.exists())
        assertFalse("Temp file must be cleaned up after salvage", tmp.exists())

        val parsed = PhoneRawTelemetryReader.readAll(target)
        val parsedMeta = requireNotNull(parsed.metadata)
        assertEquals("Pixel 9 Pro", parsedMeta.deviceModel)
        assertEquals(7L, parsedMeta.sportId)
        assertFalse("Recovered final archive must be valid RFC-1952 GZIP", parsed.truncatedEof)
        assertTrue("Must salvage pre-crash rows (got ${parsed.rows.size})", parsed.rows.size >= 35)
        assertEquals(0, parsed.skippedMalformedLines)
    }

    @Test
    fun resumeRecorder_salvagesTruncatedTmpAndAppendsPostCrashRows_TST_EXP_045_2() = runBlocking {
        val target = File(tempFolder.root, "2026-11-21_091000.phone_raw.csv.gz")
        val tmp = File(tempFolder.root, "2026-11-21_091000.phone_raw.csv.gz.tmp")

        val meta = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel 9", sportId = 3L, sessionStartTimeMs = 1_760_000_100_000L)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos, true).use { gzip ->
            val writer = OutputStreamWriter(gzip, StandardCharsets.UTF_8)
            writer.write("${meta.toHeaderLine()}\n")
            writer.write("${PhoneRawTelemetryRow.CSV_HEADER}\n")
            for (i in 1..20) {
                writer.write("${PhoneRawTelemetryRow(i * 10L, 1000L + i, PhoneRawChannel.GYRO, 0.01 * i, 0.02, -0.03).toCsvLine()}\n")
            }
            writer.flush()
        }
        val raw = baos.toByteArray()
        tmp.writeBytes(raw.copyOfRange(0, raw.size - 8))

        val resumed = SmartphoneRawTelemetryRecorder(
            targetFile = target,
            metadata = meta,
            resumeExisting = true
        )
        resumed.start()
        resumed.record(PhoneRawTelemetryRow(900L, 2000L, PhoneRawChannel.STEP))
        resumed.record(PhoneRawTelemetryRow(950L, 2050L, PhoneRawChannel.STATE, 1.0, 2.0, 3.0))
        resumed.stop()

        val parsed = PhoneRawTelemetryReader.readAll(target)
        assertFalse(parsed.truncatedEof)
        assertTrue("Resumed archive must contain pre-crash + post-crash rows (got ${parsed.rows.size})", parsed.rows.size >= 20)
        assertEquals(PhoneRawChannel.STATE, parsed.rows.last().channel)
        assertEquals(3.0, parsed.rows.last().v2, 1e-9)
    }

    @Test
    fun truncatedNonNewlineTrailingGnssRow_isStrictlyDroppedByRecoveryAndReader_TST_EXP_045_2() {
        val target = File(tempFolder.root, "2026-11-21_092000.phone_raw.csv.gz")
        val tmp = File(tempFolder.root, "2026-11-21_092000.phone_raw.csv.gz.tmp")

        val meta = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel", sportId = 1L, sessionStartTimeMs = 1000L)
        val validGnss = PhoneRawTelemetryRow(1000L, 1000L, PhoneRawChannel.GNSS, 48.13, 11.57, 520.0, 3.5, 180.0, 15.4)
        // Truncated 9-column GNSS line lacking trailing '\n': accuracy "15.4" cut off at "1"
        val truncatedGnssNoNewline = "2000,2000,GNSS,48.14,11.58,521.0,3.6,180.0,1"

        GZIPOutputStream(tmp.outputStream(), true).use { gzip ->
            val writer = OutputStreamWriter(gzip, StandardCharsets.UTF_8)
            writer.write("${meta.toHeaderLine()}\n")
            writer.write("${PhoneRawTelemetryRow.CSV_HEADER}\n")
            writer.write("${validGnss.toCsvLine()}\n")
            writer.write(truncatedGnssNoNewline) // NO trailing newline!
            writer.flush()
        }

        // 1. Direct reader must strictly drop non-newline-terminated trailing line
        val directRead = PhoneRawTelemetryReader.readAll(tmp)
        assertEquals("Reader must drop non-newline-terminated truncated GNSS row", 1, directRead.rows.size)
        assertEquals(15.4, directRead.rows[0].v5, 1e-6)
        assertEquals(1, directRead.skippedMalformedLines)

        // 2. Crash recovery must also drop non-newline-terminated trailing line
        assertTrue(PhoneRawSidecarRecovery.recoverAbandonedTmpToFinal(tmp, target))
        val recovered = PhoneRawTelemetryReader.readAll(target)
        assertEquals("Recovery must drop non-newline-terminated truncated GNSS row", 1, recovered.rows.size)
        assertEquals(15.4, recovered.rows[0].v5, 1e-6)
        assertEquals(0, recovered.skippedMalformedLines)
    }

    // REQ-EXP-047.1, REQ-EXP-047.3 / TST-EXP-047.1
    @Test
    fun recoverAbandonedTmpToFinal_salvagesGnssAndGnssFusedRowsAndDropsTruncatedTail_TST_EXP_047_1() {
        val target = File(tempFolder.root, "2026-11-22_093000.phone_raw.csv.gz")
        val tmp = File(tempFolder.root, "2026-11-22_093000.phone_raw.csv.gz.tmp")

        val meta = PhoneRawMetadata(appVersion = "5.0.0", deviceModel = "Pixel 10", sportId = 1L, sessionStartTimeMs = 1000L)
        val validGps = PhoneRawTelemetryRow(1000L, 1000L, PhoneRawChannel.GNSS, 48.13, 11.57, 478.0, 3.5, 180.0, 4.2)
        val validFused = PhoneRawTelemetryRow(1050L, 1000L, PhoneRawChannel.GNSS_FUSED, 48.13, 11.57, 493.0, 3.4, 181.0, 5.1)
        val truncatedFusedNoNewline = "2050,2000,GNSS_FUSED,48.14,11.58,494.0,3.6,180.0,5"

        GZIPOutputStream(tmp.outputStream(), true).use { gzip ->
            val writer = OutputStreamWriter(gzip, StandardCharsets.UTF_8)
            writer.write("${meta.toHeaderLine()}\n")
            writer.write("${PhoneRawTelemetryRow.CSV_HEADER}\n")
            writer.write("${validGps.toCsvLine()}\n")
            writer.write("${validFused.toCsvLine()}\n")
            writer.write(truncatedFusedNoNewline)
            writer.flush()
        }

        assertTrue(PhoneRawSidecarRecovery.recoverAbandonedTmpToFinal(tmp, target))
        val recovered = PhoneRawTelemetryReader.readAll(target)
        assertEquals(2, recovered.rows.size)
        assertEquals(PhoneRawChannel.GNSS, recovered.rows[0].channel)
        assertEquals(478.0, recovered.rows[0].v2, 1e-6)
        assertEquals(PhoneRawChannel.GNSS_FUSED, recovered.rows[1].channel)
        assertEquals(493.0, recovered.rows[1].v2, 1e-6)
        assertEquals(0, recovered.skippedMalformedLines)
    }
}
