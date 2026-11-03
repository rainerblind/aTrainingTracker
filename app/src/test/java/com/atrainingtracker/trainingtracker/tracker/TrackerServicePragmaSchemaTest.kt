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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.banalservice.sensor.SensorValueType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Architectural and contract unit tests for `REQ-TRK-051.1` / `TST-TRK-051.1` (`ATT-4968`):
 * Verifies that `TrackerService.getTableColumnNames()` uses `PRAGMA table_info` to accurately
 * discover table columns in real time across dynamic `ALTER TABLE ... ADD COLUMN` operations,
 * completely bypassing `SQLiteConnection.mPreparedStatementCache` staleness on Android <= 14 (API <= 34).
 */
@RunWith(RobolectricTestRunner::class)
class TrackerServicePragmaSchemaTest {

    private lateinit var db: SQLiteDatabase
    private val tableName = "\"2026-11-03_120000\""

    @Before
    fun setUp() {
        db = RealSqliteTestHelper.createInMemoryDatabase()
        db.execSQL(
            "CREATE TABLE $tableName (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "TIME_TOTAL int null, " +
                "TIME_ACTIVE int null, " +
                "DISTANCE_TOTAL double null" +
                ");"
        )
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db)
    }

    @Test
    fun testGetTableColumnNames_discoversInitialColumnsAccurately() {
        val columns = TrackerService.getTableColumnNames(db, tableName)

        assertTrue("Must discover _id", columns.contains("_id"))
        assertTrue("Must discover TIME_TOTAL", columns.contains("TIME_TOTAL"))
        assertTrue("Must discover TIME_ACTIVE", columns.contains("TIME_ACTIVE"))
        assertTrue("Must discover DISTANCE_TOTAL", columns.contains("DISTANCE_TOTAL"))
        assertEquals("Initial column count must match schema", 4, columns.size)
    }

    @Test
    fun testGetTableColumnNames_isCaseInsensitive() {
        val columns = TrackerService.getTableColumnNames(db, tableName)

        assertTrue("Case insensitive check for lowercase time_total", columns.contains("time_total"))
        assertTrue("Case insensitive check for mixed-case Distance_Total", columns.contains("Distance_Total"))
    }

    @Test
    fun testGetTableColumnNames_discoversDynamicallyAddedColumnsImmediately() {
        // Step 1: Add a dynamic SLOPE column
        val quotedSlope = TrackerService.quoteSqlColumnIdentifier("SLOPE")
        db.execSQL("ALTER TABLE $tableName ADD COLUMN $quotedSlope double null;")

        val columnsAfterSlope = TrackerService.getTableColumnNames(db, tableName)
        assertTrue("Must discover newly added SLOPE column immediately via PRAGMA", columnsAfterSlope.contains("SLOPE"))
        assertTrue("Case-insensitive slope check", columnsAfterSlope.contains("slope"))

        // Step 2: Add a device-qualified column with embedded quote and apostrophe
        val complexColumn = "POWER (Stage's \"Pro\" v2)"
        val quotedComplex = TrackerService.quoteSqlColumnIdentifier(complexColumn)
        db.execSQL("ALTER TABLE $tableName ADD COLUMN $quotedComplex double null;")

        val columnsAfterComplex = TrackerService.getTableColumnNames(db, tableName)
        assertTrue(
            "Must discover device-qualified column with apostrophe and quotes",
            columnsAfterComplex.contains(complexColumn)
        )
    }

    @Test
    fun testGetTableColumnNames_safelyHandlesNonExistentTable() {
        val columns = TrackerService.getTableColumnNames(db, "\"non_existent_table\"")
        assertTrue("Non-existent table must return empty set without throwing exceptions", columns.isEmpty())
    }
}
