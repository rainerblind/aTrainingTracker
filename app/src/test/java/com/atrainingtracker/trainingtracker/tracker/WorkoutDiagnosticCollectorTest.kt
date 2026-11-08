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

package com.atrainingtracker.trainingtracker.tracker

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for WorkoutRecordingErrorCollector (TST-TRK-053.2, REQ-TRK-053.2).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDiagnosticCollectorTest {

    @Test
    fun testCleanCollectorReturnsNullJsonAndEmptyList() {
        val collector = WorkoutRecordingErrorCollector()
        assertTrue(collector.isEmpty())
        assertEquals(0, collector.getEntries().size)
        assertNull(collector.toJson())
    }

    @Test
    fun testRepeatedSampleInsertErrorsDeduplicatedWithCountAndTimestamps() {
        val collector = WorkoutRecordingErrorCollector()
        val exception = SQLiteException("table WorkoutSamples has no column named cadence")

        for (i in 1..100) {
            val shouldSync = collector.record(
                DiagnosticStage.SAMPLE_WRITE,
                exception,
                "Error inserting sample"
            )
            // Expect sync triggers at 1, 10, 50, 100
            if (i == 1 || i == 10 || i == 50 || i == 100) {
                assertTrue("Expected sync at count $i", shouldSync)
            } else {
                assertFalse("Unexpected sync trigger at count $i", shouldSync)
            }
        }

        assertFalse(collector.isEmpty())
        val entries = collector.getEntries()
        assertEquals(1, entries.size)

        val entry = entries[0]
        assertEquals(DiagnosticStage.SAMPLE_WRITE, entry.stage)
        assertEquals("android.database.sqlite.SQLiteException", entry.exceptionClass)
        assertEquals("Error inserting sample", entry.message)
        assertEquals(100, entry.count)
        assertTrue(entry.firstTimestampMs > 0)
        assertTrue(entry.lastTimestampMs >= entry.firstTimestampMs)

        val json = collector.toJson()
        assertNotNull(json)
        val deserialized = WorkoutRecordingErrorCollector.fromJson(json)
        assertEquals(1, deserialized.size)
        assertEquals(100, deserialized[0].count)
    }

    @Test
    fun testLifecycleStagesSeparation() {
        val collector = WorkoutRecordingErrorCollector()

        collector.record(DiagnosticStage.INIT, IllegalStateException("Table init failed"))
        for (i in 1..10) {
            collector.record(DiagnosticStage.SAMPLE_WRITE, SQLiteException("disk I/O error"))
        }
        collector.record(DiagnosticStage.FINALIZE, RuntimeException("Cluster auto-naming failed"))

        val entries = collector.getEntries()
        assertEquals(3, entries.size)

        val stages = entries.map { it.stage }
        assertTrue(stages.contains(DiagnosticStage.INIT))
        assertTrue(stages.contains(DiagnosticStage.SAMPLE_WRITE))
        assertTrue(stages.contains(DiagnosticStage.FINALIZE))

        val sampleEntry = entries.first { it.stage == DiagnosticStage.SAMPLE_WRITE }
        assertEquals(10, sampleEntry.count)
    }

    @Test
    fun testPayloadSizeCapUnder8KB() {
        val collector = WorkoutRecordingErrorCollector()

        // Insert 200 distinct errors with deep stack traces
        for (i in 1..200) {
            val ex = Exception("Distinct error number $i with a rather verbose descriptive message intended to test memory boundedness")
            collector.record(DiagnosticStage.SAMPLE_WRITE, ex)
        }

        val json = collector.toJson()
        assertNotNull(json)
        val byteLength = json!!.toByteArray(Charsets.UTF_8).size
        assertTrue(
            "Payload size ($byteLength bytes) must not exceed MAX_PAYLOAD_BYTES (${WorkoutRecordingErrorCollector.MAX_PAYLOAD_BYTES})",
            byteLength <= WorkoutRecordingErrorCollector.MAX_PAYLOAD_BYTES
        )
    }

    @Test
    fun testCategoryResolution() {
        // Storage full
        val storageEntries = listOf(
            DiagnosticErrorEntry(
                stage = DiagnosticStage.SAMPLE_WRITE,
                exceptionClass = "android.database.sqlite.SQLiteFullException",
                message = "database or disk is full",
                firstTimestampMs = 1L,
                lastTimestampMs = 2L,
                count = 1,
                stackTrace = ""
            )
        )
        assertEquals(
            DiagnosticCategory.STORAGE_FULL,
            WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(storageEntries)
        )

        // Sample write
        val sampleEntries = listOf(
            DiagnosticErrorEntry(
                stage = DiagnosticStage.SAMPLE_WRITE,
                exceptionClass = "android.database.sqlite.SQLiteException",
                message = "missing column",
                firstTimestampMs = 1L,
                lastTimestampMs = 2L,
                count = 5,
                stackTrace = ""
            )
        )
        assertEquals(
            DiagnosticCategory.SAMPLE_WRITE,
            WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(sampleEntries)
        )

        // Finalize only
        val finalizeEntries = listOf(
            DiagnosticErrorEntry(
                stage = DiagnosticStage.FINALIZE,
                exceptionClass = "java.lang.NullPointerException",
                message = "error computing extrema",
                firstTimestampMs = 1L,
                lastTimestampMs = 2L,
                count = 1,
                stackTrace = ""
            )
        )
        assertEquals(
            DiagnosticCategory.FINALIZE,
            WorkoutRecordingErrorCollector.resolvePrimaryCauseCategory(finalizeEntries)
        )
    }

    @Test
    fun testEmailBodyFormatting() {
        val collector = WorkoutRecordingErrorCollector()
        collector.record(DiagnosticStage.SAMPLE_WRITE, SQLiteException("Corrupt table"), "Corrupt table")
        val json = collector.toJson()

        val emailBody = WorkoutRecordingErrorCollector.formatEmailBody(
            workoutName = "Evening Bike Ride",
            sportName = "Cycling",
            recordingErrorsJson = json
        )

        assertTrue(emailBody.contains("aTrainingTracker Fehlerbericht"))
        assertTrue(emailBody.contains("Evening Bike Ride"))
        assertTrue(emailBody.contains("Cycling"))
        assertTrue(emailBody.contains("Corrupt table"))
    }
}
