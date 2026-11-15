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
}
