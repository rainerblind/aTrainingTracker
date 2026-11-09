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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying retroactive altitude drift correction and sourced-column
 * synchronization across canonical ALTITUDE and source-attributed ALTITUDE (*) columns
 * (REQ-TRK-057.3, TST-TRK-057.4).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSamplesDatabaseManagerTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutSamplesDatabaseManager
    private lateinit var db: SQLiteDatabase

    private val testTable = "\"test_samples_2026_11_10\""

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_NAME)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        dbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        db = dbManager.database

        // Create sample table with canonical ALTITUDE and sourced "ALTITUDE (Höhe aus Luftdruck)"
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $testTable (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "ALTITUDE REAL, " +
                    "\"ALTITUDE (Höhe aus Luftdruck)\" REAL, " +
                    "HEART_RATE REAL)"
        )
    }

    @After
    fun tearDown() {
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        context.deleteDatabase(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.DB_NAME)
    }

    /**
     * TST-TRK-057.4 (Case A): Uncalibrated start (t0Ms <= 0).
     * Uniformly shifts both canonical ALTITUDE and sourced "ALTITUDE (Höhe aus Luftdruck)"
     * by deltaOffset across all existing rows while leaving unrelated columns untouched.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_caseA_uniformShift_updatesAllAltitudeColumns() {
        // Insert 3 sample rows
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", HEART_RATE) VALUES ('2026-11-10 10:00:00', 300.0, 300.0, 140.0)")
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", HEART_RATE) VALUES ('2026-11-10 10:00:10', 305.0, 305.0, 142.0)")
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", HEART_RATE) VALUES ('2026-11-10 10:00:20', 310.0, 310.0, 145.0)")

        // Act: Apply +20.0m uniform drift correction (Case A: uncalibrated start t0Ms = -1)
        dbManager.applyAltitudeDriftCorrection(testTable, 20.0, -1L, 1794304820000L)

        // Assert: both columns must be shifted symmetrically by +20m
        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", HEART_RATE FROM $testTable ORDER BY _id ASC", null).use { cursor ->
            val expectedAlts = listOf(320.0, 325.0, 330.0)
            val expectedHr = listOf(140.0, 142.0, 145.0)
            var index = 0
            while (cursor.moveToNext()) {
                val alt = cursor.getDouble(0)
                val sourcedAlt = cursor.getDouble(1)
                val hr = cursor.getDouble(2)

                assertEquals("Canonical ALTITUDE row $index must be shifted by +20m", expectedAlts[index], alt, 0.001)
                assertEquals("Sourced ALTITUDE row $index must match canonical shift", expectedAlts[index], sourcedAlt, 0.001)
                assertEquals("HEART_RATE row $index must remain untouched", expectedHr[index], hr, 0.001)
                index++
            }
            assertEquals("All 3 rows must have been inspected", 3, index)
        }
    }

    /**
     * TST-TRK-057.4 (Case B): Piecewise-linear drift interpolation (t0Ms > 0 and t1Ms > t0Ms).
     * Samples in [t0, t1] receive linearly interpolated shift; samples > t1 receive full deltaOffset;
     * samples < t0 remain untouched. Both canonical and sourced columns update identically.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_caseB_piecewiseLinear_updatesAllAltitudeColumns() {
        val t0Sec = 1762770000L // 2025-11-10 10:20:00
        val t1Sec = 1762770100L // 2025-11-10 10:21:40 (duration = 100s)

        // Row 0: t < t0 (t0Sec - 20) -> should NOT be modified
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES (datetime(${t0Sec - 20}, 'unixepoch'), 200.0, 200.0)")
        // Row 1: t == t0 -> shift = 0.0 -> alt = 200.0
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES (datetime($t0Sec, 'unixepoch'), 200.0, 200.0)")
        // Row 2: t == t0 + 50s (midpoint) -> shift = 20.0 * 0.5 = +10.0m -> alt = 210.0
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES (datetime(${t0Sec + 50}, 'unixepoch'), 200.0, 200.0)")
        // Row 3: t == t1 -> shift = +20.0m -> alt = 220.0
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES (datetime($t1Sec, 'unixepoch'), 200.0, 200.0)")
        // Row 4: t > t1 (t1Sec + 30) -> uniform shift = +20.0m -> alt = 220.0
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES (datetime(${t1Sec + 30}, 'unixepoch'), 200.0, 200.0)")

        // Act: apply piecewise-linear drift correction (+20.0m)
        dbManager.applyAltitudeDriftCorrection(testTable, 20.0, t0Sec * 1000L, t1Sec * 1000L)

        // Assert: verify each row
        val expected = listOf(200.0, 200.0, 210.0, 220.0, 220.0)
        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\" FROM $testTable ORDER BY _id ASC", null).use { cursor ->
            var idx = 0
            while (cursor.moveToNext()) {
                val canonicalAlt = cursor.getDouble(0)
                val sourcedAlt = cursor.getDouble(1)

                assertEquals("Row $idx canonical ALTITUDE", expected[idx], canonicalAlt, 0.05)
                assertEquals("Row $idx sourced ALTITUDE must match canonical", expected[idx], sourcedAlt, 0.05)
                idx++
            }
            assertEquals("All 5 rows verified", 5, idx)
        }
    }

    /**
     * TST-TRK-057.4 (Case A - Negative Offset): Uniform negative drift correction (-15.0m).
     */
    @Test
    fun testApplyAltitudeDriftCorrection_negativeOffset_shiftsDownwardsSymmetrically() {
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES ('2026-11-10 10:00:00', 500.0, 500.0)")

        // Act: Apply -15.0m shift
        dbManager.applyAltitudeDriftCorrection(testTable, -15.0, -1L, 1000L)

        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\" FROM $testTable", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(485.0, cursor.getDouble(0), 0.001)
            assertEquals(485.0, cursor.getDouble(1), 0.001)
        }
    }

    /**
     * Edge case: Zero or near-zero deltaOffset (< 0.001) skips execution without modifying rows.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_nearZeroOffset_skipsExecution() {
        db.execSQL("INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES ('2026-11-10 10:00:00', 500.0, 500.0)")

        dbManager.applyAltitudeDriftCorrection(testTable, 0.0001, -1L, 1000L)

        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\" FROM $testTable", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(500.0, cursor.getDouble(0), 0.001)
            assertEquals(500.0, cursor.getDouble(1), 0.001)
        }
    }
}
