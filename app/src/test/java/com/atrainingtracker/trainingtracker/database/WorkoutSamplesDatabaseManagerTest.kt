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
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying retroactive altitude drift correction, sourced-column synchronization,
 * and `calcExtremaValue` null-safety across `WorkoutSamplesDatabaseManager`
 * (REQ-TRK-057.3, TST-TRK-057.4; REQ-FIL-004, TST-FIL-003; REQ-FIL-006, REQ-FIL-011, TST-FIL-002).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

        // Act: Apply -15.0m shift with t1Ms >= row timestamp
        dbManager.applyAltitudeDriftCorrection(testTable, -15.0, -1L, 1794304820000L)

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

        dbManager.applyAltitudeDriftCorrection(testTable, 0.0001, -1L, 1794304820000L)

        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\" FROM $testTable", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(500.0, cursor.getDouble(0), 0.001)
            assertEquals(500.0, cursor.getDouble(1), 0.001)
        }
    }

    /**
     * TST-FIL-018.1 (Criterion 1): Non-barometric columns (DEM, GPS, Google Fused, ATT Fused) remain
     * completely immutable under barometric calibration, and canonical ALTITUDE only shifts on rows
     * where barometric pressure was the active canonical source.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_isolatesNonBarometricColumnsAndChecksCanonicalSource() {
        val multiSourceTable = "\"test_multisource_2026_11_15\""
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $multiSourceTable (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "ALTITUDE REAL, " +
                    "\"ALTITUDE (Höhe aus Luftdruck)\" REAL, " +
                    "\"ALTITUDE (Topografische Höhe (DEM))\" REAL, " +
                    "\"ALTITUDE (GPS)\" REAL, " +
                    "\"ALTITUDE (Google Fused)\" REAL, " +
                    "\"ALTITUDE (ATT Fused (Höhe und Steigung))\" REAL)"
        )

        val tSec = 1794304800L // 2026-11-10 10:00:00 UTC
        // Row 0: Canonical ALTITUDE sourced from DEM (448.0), while uncalibrated Baro reads 385.63
        db.execSQL(
            "INSERT INTO $multiSourceTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", " +
                    "\"ALTITUDE (Topografische Höhe (DEM))\", \"ALTITUDE (GPS)\", \"ALTITUDE (Google Fused)\", " +
                    "\"ALTITUDE (ATT Fused (Höhe und Steigung))\") " +
                    "VALUES (datetime($tSec, 'unixepoch'), 448.0, 385.63, 448.0, 452.0, 450.0, 448.5)"
        )
        // Row 1: Canonical ALTITUDE sourced from Barometer (386.0) when DEM/ATT Fused are NULL
        db.execSQL(
            "INSERT INTO $multiSourceTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", " +
                    "\"ALTITUDE (Topografische Höhe (DEM))\", \"ALTITUDE (GPS)\", \"ALTITUDE (Google Fused)\", " +
                    "\"ALTITUDE (ATT Fused (Höhe und Steigung))\") " +
                    "VALUES (datetime(${tSec + 5}, 'unixepoch'), 386.0, 386.0, NULL, 451.0, 449.0, NULL)"
        )

        val canonicalShifted = dbManager.applyAltitudeDriftCorrection(
            multiSourceTable, 62.37, -1L, (tSec + 10) * 1000L, -1L
        )
        assertEquals("Exactly 1 barometric canonical row should shift", 1, canonicalShifted)

        db.rawQuery(
            "SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\", \"ALTITUDE (Topografische Höhe (DEM))\", " +
                    "\"ALTITUDE (GPS)\", \"ALTITUDE (Google Fused)\", \"ALTITUDE (ATT Fused (Höhe und Steigung))\" " +
                    "FROM $multiSourceTable ORDER BY _id ASC", null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Row 0 DEM-sourced canonical ALTITUDE untouched", 448.0, cursor.getDouble(0), 0.001)
            assertEquals("Row 0 Baro column shifted", 448.0, cursor.getDouble(1), 0.001)
            assertEquals("Row 0 DEM column untouched", 448.0, cursor.getDouble(2), 0.001)
            assertEquals("Row 0 GPS column untouched", 452.0, cursor.getDouble(3), 0.001)
            assertEquals("Row 0 Google Fused column untouched", 450.0, cursor.getDouble(4), 0.001)
            assertEquals("Row 0 ATT Fused column untouched", 448.5, cursor.getDouble(5), 0.001)

            assertTrue(cursor.moveToNext())
            assertEquals("Row 1 Baro-sourced canonical ALTITUDE shifted", 448.37, cursor.getDouble(0), 0.001)
            assertEquals("Row 1 Baro column shifted", 448.37, cursor.getDouble(1), 0.001)
            assertTrue("Row 1 DEM remains NULL", cursor.isNull(2))
            assertEquals("Row 1 GPS column untouched", 451.0, cursor.getDouble(3), 0.001)
            assertEquals("Row 1 Google Fused column untouched", 449.0, cursor.getDouble(4), 0.001)
            assertTrue("Row 1 ATT Fused remains NULL", cursor.isNull(5))
        }
    }

    /**
     * TST-FIL-018.2 (Criterion 2): On RESUME_SERVICE_RECREATION (prevCalTimeMs == -1L), Case A
     * shifts ONLY uncalibrated barometric rows within [sessionEpochStartTimeMs, t1Ms], leaving
     * already-calibrated pre-crash rows and post-calibration rows untouched.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_caseA_epochAndUpperBoundsProtectPreCrashAndPostCalRows() {
        val epochSec = 1794305400L // crash/restart epoch start
        val t1Sec = epochSec + 10L   // calibration timestamp

        // Pre-crash calibrated rows (< epochSec)
        val insertPreSql = "INSERT INTO $testTable (time, ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\") VALUES "
        db.execSQL("$insertPreSql(datetime(${epochSec - 60}, 'unixepoch'), 440.0, 440.0)")
        db.execSQL("$insertPreSql(datetime(${epochSec - 10}, 'unixepoch'), 445.0, 445.0)")
        // Post-restart uncalibrated rows in [epochSec, t1Sec]
        db.execSQL("$insertPreSql(datetime($epochSec, 'unixepoch'), 380.0, 380.0)")
        db.execSQL("$insertPreSql(datetime($t1Sec, 'unixepoch'), 385.0, 385.0)")
        // Post-calibration row (> t1Sec), already calibrated at 448.0
        db.execSQL("$insertPreSql(datetime(${t1Sec + 10}, 'unixepoch'), 448.0, 448.0)")

        // Pass epochStartMs with +900ms sub-second offset to verify floor-second inclusivity for epochSec row
        val shifted = dbManager.applyAltitudeDriftCorrection(
            testTable, 62.37, -1L, t1Sec * 1000L + 500L, epochSec * 1000L + 900L
        )
        assertEquals("Only the 2 uncalibrated post-restart rows should shift", 2, shifted)

        val expected = listOf(440.0, 445.0, 442.37, 447.37, 448.0)
        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Höhe aus Luftdruck)\" FROM $testTable ORDER BY _id ASC", null).use { cursor ->
            var idx = 0
            while (cursor.moveToNext()) {
                assertEquals("Row $idx canonical ALTITUDE", expected[idx], cursor.getDouble(0), 0.001)
                assertEquals("Row $idx Baro ALTITUDE", expected[idx], cursor.getDouble(1), 0.001)
                idx++
            }
            assertEquals(5, idx)
        }
    }

    /**
     * Edge case: Without a barometric source column present, DEM/GPS/canonical ALTITUDE are never mutated.
     */
    @Test
    fun testApplyAltitudeDriftCorrection_withoutBarometricColumn_leavesDemAndCanonicalUntouched() {
        val demOnlyTable = "\"test_dem_only_2026_11_15\""
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $demOnlyTable (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "ALTITUDE REAL, " +
                    "\"ALTITUDE (Topografische Höhe (DEM))\" REAL)"
        )
        db.execSQL(
            "INSERT INTO $demOnlyTable (time, ALTITUDE, \"ALTITUDE (Topografische Höhe (DEM))\") " +
                "VALUES ('2026-11-10 10:00:00', 448.0, 448.0)"
        )

        val shifted = dbManager.applyAltitudeDriftCorrection(demOnlyTable, 50.0, -1L, 1794304820000L, -1L)
        assertEquals(0, shifted)

        db.rawQuery("SELECT ALTITUDE, \"ALTITUDE (Topografische Höhe (DEM))\" FROM $demOnlyTable", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(448.0, cursor.getDouble(0), 0.001)
            assertEquals(448.0, cursor.getDouble(1), 0.001)
        }
    }

    @Test
    fun testPatchSwimKinematics_nonExistentTable_returnsSafelyWithoutException() {
        // Must safely return without throwing SQLiteException when table does not exist (ATT-8001 Stage 6 Finding 3)
        dbManager.patchSwimKinematics("non_existent_swim_table", 1_700_000_000_000L, 60_000L, listOf(1_700_000_030_000L), 25.0)
        dbManager.patchSwimKinematicsUniform("non_existent_swim_table", 1_700_000_000_000L, 60_000L, 4, 25.0)
    }

    /**
     * TST-FIL-002.1 (REQ-FIL-006, REQ-FIL-011): `calcExtremaValue` must return `null` (never `0.0`)
     * across all 5 ExtremaType modes when a sensor column has strictly NULL values or is missing.
     */
    @Test
    fun testCalcExtremaValue_allNullAndMissingColumn_returnsNullAcrossAllExtremaTypes() {
        val baseFileName = "extrema_null_check_2026_11_19"
        val quotedTable = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        assertEquals("\"$baseFileName\"", quotedTable)
        assertTrue(quotedTable.length <= 66)

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $quotedTable (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "HR REAL, " +
                "ALTITUDE REAL)"
        )
        // Insert rows with strictly NULL HR and valid ALTITUDE
        db.execSQL("INSERT INTO $quotedTable (time, HR, ALTITUDE) VALUES ('2026-11-19 10:00:00', NULL, 100.0)")
        db.execSQL("INSERT INTO $quotedTable (time, HR, ALTITUDE) VALUES ('2026-11-19 10:00:10', NULL, 150.0)")
        db.execSQL("INSERT INTO $quotedTable (time, HR, ALTITUDE) VALUES ('2026-11-19 10:00:20', NULL, 200.0)")

        val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)

        // 1. All-NULL sensor column must return null (not 0.0) for all 5 ExtremaType modes
        for (type in ExtremaType.values()) {
            assertNull(
                "All-NULL column must return null for $type (TST-FIL-002)",
                dbManager.calcExtremaValue(mockSummariesDb, baseFileName, type, SensorType.HR)
            )
        }

        // 2. Missing sensor column must return null for all 5 ExtremaType modes
        for (type in ExtremaType.values()) {
            assertNull(
                "Missing column must return null for $type (TST-FIL-002)",
                dbManager.calcExtremaValue(mockSummariesDb, baseFileName, type, SensorType.CADENCE)
            )
        }

        // 3. Populated ALTITUDE rows must compute exact extrema across all 5 ExtremaType modes
        assertEquals(200.0, dbManager.calcExtremaValue(mockSummariesDb, baseFileName, ExtremaType.MAX, SensorType.ALTITUDE)!!, 0.001)
        assertEquals(150.0, dbManager.calcExtremaValue(mockSummariesDb, baseFileName, ExtremaType.AVG, SensorType.ALTITUDE)!!, 0.001)
        assertEquals(100.0, dbManager.calcExtremaValue(mockSummariesDb, baseFileName, ExtremaType.MIN, SensorType.ALTITUDE)!!, 0.001)
        assertEquals(100.0, dbManager.calcExtremaValue(mockSummariesDb, baseFileName, ExtremaType.START, SensorType.ALTITUDE)!!, 0.001)
        assertEquals(200.0, dbManager.calcExtremaValue(mockSummariesDb, baseFileName, ExtremaType.END, SensorType.ALTITUDE)!!, 0.001)
    }
}
