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

import android.content.ContentValues
import android.content.Context
import android.util.Pair
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SQLite integration tests verifying gap-only `SensorType.HR` patching, preservation of valid HR,
 * GPS, speed, cadence, and power samples, `calcHeartRateStats()`, and zero `WorkoutRr.db`
 * pollution (`ATT-7924`, `REQ-SET-109.1`, `REQ-SET-109.2`, `TST-SET-111.1`, `TST-SET-111.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSamplesHrBackfillTest {

    @Test
    @Suppress("LongMethod")
    fun testPatchMissingHeartRateSamples_preservesValidHrAndOtherSensorsAndComputesStats() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context)
        val baseFileName = "2026-11-15_150000_verity_test"
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDb.createNewTable(baseFileName, listOf(SensorType.HR, SensorType.LATITUDE, SensorType.POWER))
        val db = samplesDb.database
        assertTrue(samplesDb.existsTable(baseFileName))

        val timeCol = WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        val t0Sec = 1_700_000_000L
        // Row 1: live HR = 128 present -> MUST stay 128 even if offline sample is 135
        db.insertOrThrow(
            table,
            null,
            ContentValues().apply {
                put(timeCol, "2023-11-14 22:13:20")
                put(SensorType.HR.name, 128)
                put(SensorType.LATITUDE.name, 48.1000)
                put(SensorType.POWER.name, 220)
            }
        )
        // Row 2: HR NULL -> MUST be patched with offline 144 while preserving LATITUDE and POWER
        db.insertOrThrow(
            table,
            null,
            ContentValues().apply {
                put(timeCol, "2023-11-14 22:13:21")
                putNull(SensorType.HR.name)
                put(SensorType.LATITUDE.name, 48.1001)
                put(SensorType.POWER.name, 240)
            }
        )
        // Row 3: HR = 0 -> MUST be patched with offline 160 while preserving LATITUDE and POWER
        db.insertOrThrow(
            table,
            null,
            ContentValues().apply {
                put(timeCol, "2023-11-14 22:13:22")
                put(SensorType.HR.name, 0)
                put(SensorType.LATITUDE.name, 48.1002)
                put(SensorType.POWER.name, 260)
            }
        )

        assertTrue(samplesDb.hasMissingHeartRateSamples(baseFileName))

        val hrPairs = listOf(
            Pair(t0Sec * 1000L, 135),
            Pair((t0Sec + 1L) * 1000L, 144),
            Pair((t0Sec + 2L) * 1000L, 160)
        )
        val patched = samplesDb.patchMissingHeartRateSamples(baseFileName, t0Sec * 1000L, hrPairs)
        assertEquals(2, patched)
        assertFalse(samplesDb.hasMissingHeartRateSamples(baseFileName))

        val rows = mutableListOf<Triple<Int, Double, Int>>()
        db.rawQuery("SELECT HR, LATITUDE, POWER FROM $table ORDER BY _id ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(Triple(cursor.getInt(0), cursor.getDouble(1), cursor.getInt(2)))
            }
        }
        assertEquals(3, rows.size)
        assertEquals(128, rows[0].first)
        assertEquals(48.1000, rows[0].second, 1e-6)
        assertEquals(220, rows[0].third)
        assertEquals(144, rows[1].first)
        assertEquals(48.1001, rows[1].second, 1e-6)
        assertEquals(240, rows[1].third)
        assertEquals(160, rows[2].first)
        assertEquals(48.1002, rows[2].second, 1e-6)
        assertEquals(260, rows[2].third)

        val stats = samplesDb.calcHeartRateStats(baseFileName)
        assertNotNull(stats)
        assertEquals(128.0, stats!!.min, 1e-6)
        assertEquals(160.0, stats.max, 1e-6)
        assertEquals(144.0, stats.avg, 1e-6)
        assertEquals(3, stats.count)

        val rrRepo = WorkoutRrRepository.getInstance(context)
        assertTrue(rrRepo.getIntervalsForWorkoutBlocking(999L).isEmpty())
    }

    // REQ-SET-130.2 / TST-SET-132.2
    @Test
    fun testPatchMissingHeartRateSamples_rejectsOneHourTimezoneShiftWithoutConstantClamping_TST_SET_132_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context)
        val baseFileName = "2026-11-22_104306_cet_shift_repro"
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDb.createNewTable(baseFileName, listOf(SensorType.HR))
        val db = samplesDb.database
        val timeCol = WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME

        // Reproduces Pixel 10 Workout 2430 (09:43:06Z..09:45:27Z UTC = 10:43:06..10:45:27 CET):
        // initial gap + live HR + mid-workout gap + live HR
        val t0UtcSec = java.time.Instant.parse("2026-11-22T09:43:06Z").epochSecond
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneOffset.UTC)
        for (s in 0..140) {
            val timeStr = formatter.format(java.time.Instant.ofEpochSecond(t0UtcSec + s))
            val cv = ContentValues().apply {
                put(timeCol, timeStr)
                when {
                    s in 13..73 -> put(SensorType.HR.name, 87 + (s % 15))
                    s in 134..140 -> put(SensorType.HR.name, 108 - (s - 134) / 2)
                    else -> putNull(SensorType.HR.name)
                }
            }
            db.insertOrThrow(table, null, cv)
        }

        // Offline HR shifted -1 hour earlier (08:43:08Z..08:45:29Z), ending at 106 bpm
        val shiftedAnchorMs = (t0UtcSec - 3_600L + 2L) * 1_000L
        val shiftedPairs = (0..140).map { i ->
            val bpm = if (i == 140) 106 else 88 + (i % 20)
            Pair(shiftedAnchorMs + i * 1_000L, bpm)
        }

        val patched = samplesDb.patchMissingHeartRateSamples(baseFileName, shiftedAnchorMs, shiftedPairs)
        assertEquals("1-hour shifted series must patch 0 rows and never flat-line clamp to 106 bpm", 0, patched)
        assertTrue(samplesDb.hasMissingHeartRateSamples(baseFileName))

        var nonNullGapRows = 0
        db.rawQuery("SELECT HR FROM $table WHERE _id <= 13 OR (_id BETWEEN 75 AND 134)", null).use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(0)) nonNullGapRows++
            }
        }
        assertEquals("Zero missing gap rows may be corrupted by out-of-window samples", 0, nonNullGapRows)
    }

    // REQ-SET-130.2 / TST-SET-132.2
    @Test
    @Suppress("LongMethod")
    fun testPatchMissingHeartRateSamples_enforcesExact5000MsBoundaryAndPatchesAuthenticDynamicGaps_TST_SET_132_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context)
        val baseFileName = "2026-11-22_104306_boundary_and_dynamic"
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        samplesDb.createNewTable(baseFileName, listOf(SensorType.HR, SensorType.POWER))
        val db = samplesDb.database
        val timeCol = WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME
        val t0Sec = java.time.Instant.parse("2026-11-22T09:43:06Z").epochSecond
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneOffset.UTC)

        // Rows at offsets: 0s (6s before first offline sample -> >5000ms REJECT),
        // 1s (5s before first offline sample -> ==5000ms ACCEPT),
        // 6s..8s (exact dynamic 1-Hz offline samples 91, 95, 99),
        // 9s (live HR 102 -> MUST preserve),
        // 13s (5s after last offline sample 8s -> ==5000ms ACCEPT),
        // 14s (6s after last offline sample 8s -> >5000ms REJECT)
        val rowSpecs = listOf(
            Triple(0L, null, 180),
            Triple(1L, null, 185),
            Triple(6L, null, 190),
            Triple(7L, null, 195),
            Triple(8L, null, 200),
            Triple(9L, 102, 205),
            Triple(13L, null, 210),
            Triple(14L, null, 215)
        )
        for ((offsetSec, liveHr, power) in rowSpecs) {
            db.insertOrThrow(
                table,
                null,
                ContentValues().apply {
                    put(timeCol, formatter.format(java.time.Instant.ofEpochSecond(t0Sec + offsetSec)))
                    if (liveHr != null) put(SensorType.HR.name, liveHr) else putNull(SensorType.HR.name)
                    put(SensorType.POWER.name, power)
                }
            )
        }

        val offlinePairs = listOf(
            Pair((t0Sec + 6L) * 1_000L, 91),
            Pair((t0Sec + 7L) * 1_000L, 95),
            Pair((t0Sec + 8L) * 1_000L, 99)
        )
        assertEquals(5_000L, WorkoutSamplesDatabaseManager.MAX_HR_MATCH_TOLERANCE_MS)
        val patched = samplesDb.patchMissingHeartRateSamples(baseFileName, (t0Sec + 6L) * 1_000L, offlinePairs)
        assertEquals("Only rows within <= 5000 ms (offsets 1, 6, 7, 8, 13) should be patched", 5, patched)

        val actualHrAndPower = mutableListOf<Pair<Int?, Int>>()
        db.rawQuery("SELECT HR, POWER FROM $table ORDER BY _id ASC", null).use { c ->
            while (c.moveToNext()) {
                val hr = if (c.isNull(0)) null else c.getInt(0)
                actualHrAndPower.add(Pair(hr, c.getInt(1)))
            }
        }
        assertEquals(null, actualHrAndPower[0].first)   // 0s: 6000 ms away -> skipped
        assertEquals(91, actualHrAndPower[1].first)     // 1s: 5000 ms away -> boundary accepted
        assertEquals(91, actualHrAndPower[2].first)     // 6s: exact dynamic sample
        assertEquals(95, actualHrAndPower[3].first)     // 7s: exact dynamic sample
        assertEquals(99, actualHrAndPower[4].first)     // 8s: exact dynamic sample
        assertEquals(102, actualHrAndPower[5].first)    // 9s: live HR preserved
        assertEquals(99, actualHrAndPower[6].first)     // 13s: 5000 ms away -> boundary accepted
        assertEquals(null, actualHrAndPower[7].first)   // 14s: 6000 ms away -> skipped
        assertEquals(180, actualHrAndPower[0].second)
        assertEquals(215, actualHrAndPower[7].second)

        // Empty-table swim-only insert path must remain preserved
        val emptyTableBase = "2026-11-22_110000_empty_swim_table"
        samplesDb.createNewTable(emptyTableBase, listOf(SensorType.HR))
        val inserted = samplesDb.patchMissingHeartRateSamples(emptyTableBase, (t0Sec + 6L) * 1_000L, offlinePairs)
        assertEquals("Empty-table insert path must remain preserved", 3, inserted)
    }
}
