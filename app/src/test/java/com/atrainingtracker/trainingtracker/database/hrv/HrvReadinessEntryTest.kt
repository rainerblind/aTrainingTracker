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

package com.atrainingtracker.trainingtracker.database.hrv

import android.database.MatrixCursor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-PER-038.1: Unit tests verifying [HrvReadinessEntry] DTO mapping symmetry,
 * cursor extraction overloads, and content values conversion (Rule 20).
 */
@RunWith(RobolectricTestRunner::class)
class HrvReadinessEntryTest {

    private val columns = arrayOf(
        HrvReadinessDbHelper.COLUMN_ID,
        HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS,
        HrvReadinessDbHelper.COLUMN_DATE_ISO,
        HrvReadinessDbHelper.COLUMN_TIMEZONE_ID,
        HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM,
        HrvReadinessDbHelper.COLUMN_RMSSD_MS,
        HrvReadinessDbHelper.COLUMN_LN_RMSSD,
        HrvReadinessDbHelper.COLUMN_HRV_SCORE_100,
        HrvReadinessDbHelper.COLUMN_SDNN_MS,
        HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT,
        HrvReadinessDbHelper.COLUMN_DURATION_SEC,
        HrvReadinessDbHelper.COLUMN_POSTURE,
        HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY,
        HrvReadinessDbHelper.COLUMN_NOTES
    )

    @Test
    fun fromCursor_and_fromCursorBatch_yieldIdenticalResults_Rule20() {
        val cursor = MatrixCursor(columns)
        cursor.addRow(
            arrayOf<Any?>(
                1L,
                1731222000000L,
                "2026-11-10",
                "Europe/Berlin",
                52,
                65.4,
                4.1805,
                83.61,
                78.2,
                1.2,
                180,
                "SUPINE",
                1,
                "Morning check"
            )
        )
        cursor.addRow(
            arrayOf<Any?>(
                2L,
                1731225600000L,
                "2026-11-10",
                "Europe/Berlin",
                58,
                45.0,
                3.8066,
                76.13,
                null,
                null,
                120,
                "SEATED",
                0,
                null
            )
        )

        // Single fromCursor mapping on row 1
        cursor.moveToPosition(0)
        val singleEntry1 = HrvReadinessEntry.fromCursor(cursor)

        // Single fromCursor mapping on row 2
        cursor.moveToPosition(1)
        val singleEntry2 = HrvReadinessEntry.fromCursor(cursor)

        // Batch fromCursorBatch mapping across entire cursor
        val batchEntries = HrvReadinessEntry.fromCursorBatch(cursor)

        assertEquals(2, batchEntries.size)
        assertEquals(singleEntry1, batchEntries[0])
        assertEquals(singleEntry2, batchEntries[1])

        // Verify row 1 values
        assertEquals(1L, singleEntry1.id)
        assertEquals(1731222000000L, singleEntry1.timestampMs)
        assertEquals("2026-11-10", singleEntry1.dateIso)
        assertEquals("Europe/Berlin", singleEntry1.timezoneId)
        assertEquals(52, singleEntry1.restingHrBpm)
        assertEquals(65.4, singleEntry1.rmssdMs, 1e-4)
        assertEquals(4.1805, singleEntry1.lnRmssd, 1e-4)
        assertEquals(83.61, singleEntry1.hrvScore100, 1e-4)
        assertEquals(78.2, singleEntry1.sdnnMs!!, 1e-4)
        assertEquals(1.2, singleEntry1.artifactRatePct!!, 1e-4)
        assertEquals(180, singleEntry1.durationSec)
        assertEquals(HrvPosture.SUPINE, singleEntry1.posture)
        assertTrue(singleEntry1.isPrimaryForDay)
        assertEquals("Morning check", singleEntry1.notes)

        // Verify row 2 null handling
        assertEquals(2L, singleEntry2.id)
        assertNull(singleEntry2.sdnnMs)
        assertNull(singleEntry2.artifactRatePct)
        assertNull(singleEntry2.notes)
        assertEquals(HrvPosture.SEATED, singleEntry2.posture)
        assertFalse(singleEntry2.isPrimaryForDay)
    }

    @Test
    fun toContentValues_preservesAllFields() {
        val entry = HrvReadinessEntry(
            id = 5L,
            timestampMs = 1731222000000L,
            dateIso = "2026-11-10",
            timezoneId = "UTC",
            restingHrBpm = 48,
            rmssdMs = 80.0,
            lnRmssd = 4.382,
            hrvScore100 = 87.64,
            sdnnMs = 95.0,
            artifactRatePct = 0.5,
            durationSec = 240,
            posture = HrvPosture.STANDING,
            isPrimaryForDay = true,
            notes = "Test Note"
        )

        val cv = entry.toContentValues()
        assertEquals(5L, cv.getAsLong(HrvReadinessDbHelper.COLUMN_ID))
        assertEquals(1731222000000L, cv.getAsLong(HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS))
        assertEquals("2026-11-10", cv.getAsString(HrvReadinessDbHelper.COLUMN_DATE_ISO))
        assertEquals("UTC", cv.getAsString(HrvReadinessDbHelper.COLUMN_TIMEZONE_ID))
        assertEquals(48, cv.getAsInteger(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM).toInt())
        assertEquals(80.0, cv.getAsDouble(HrvReadinessDbHelper.COLUMN_RMSSD_MS), 1e-4)
        assertEquals(4.382, cv.getAsDouble(HrvReadinessDbHelper.COLUMN_LN_RMSSD), 1e-4)
        assertEquals(87.64, cv.getAsDouble(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100), 1e-4)
        assertEquals(95.0, cv.getAsDouble(HrvReadinessDbHelper.COLUMN_SDNN_MS), 1e-4)
        assertEquals(0.5, cv.getAsDouble(HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT), 1e-4)
        assertEquals(240, cv.getAsInteger(HrvReadinessDbHelper.COLUMN_DURATION_SEC).toInt())
        assertEquals("STANDING", cv.getAsString(HrvReadinessDbHelper.COLUMN_POSTURE))
        assertEquals(1, cv.getAsInteger(HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY).toInt())
        assertEquals("Test Note", cv.getAsString(HrvReadinessDbHelper.COLUMN_NOTES))
    }
}
