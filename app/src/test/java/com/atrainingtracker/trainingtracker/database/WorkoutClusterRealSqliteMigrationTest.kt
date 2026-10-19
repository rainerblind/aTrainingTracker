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

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.WorkoutClusterContract
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.WorkoutClusterDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration verification for WorkoutClusterDbHelper (TST-MIG-043.3, REQ-MIG-046.1).
 * Verifies deterministic DDL execution, idempotent column addition via PRAGMA table_info,
 * and preservation of existing cluster rows from legacy schema versions (v6 -> v11).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutClusterRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_cluster_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: WorkoutClusterDbHelper

    private val createV6Table = """
        CREATE TABLE ${WorkoutClusterContract.TABLE_NAME} (
            ${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${WorkoutClusterContract.COLUMN_NAME} TEXT,
            ${WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID} INTEGER,
            ${WorkoutClusterContract.COLUMN_START_LAT} REAL,
            ${WorkoutClusterContract.COLUMN_START_LNG} REAL,
            ${WorkoutClusterContract.COLUMN_END_LAT} REAL,
            ${WorkoutClusterContract.COLUMN_END_LNG} REAL,
            ${WorkoutClusterContract.COLUMN_MAX_DISP_LAT} REAL,
            ${WorkoutClusterContract.COLUMN_MAX_DISP_LNG} REAL,
            ${WorkoutClusterContract.COLUMN_REF_DISTANCE} REAL,
            ${WorkoutClusterContract.COLUMN_HIT_COUNT} INTEGER,
            ${WorkoutClusterContract.COLUMN_SPORT_TYPE} TEXT
        )
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = WorkoutClusterDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    @Test
    fun testClusterUpgradeV6ToV11DeterministicColumns() {
        val database = checkNotNull(db)
        database.execSQL(createV6Table)
        database.version = 6

        // Insert baseline cluster row at v6
        val cv = ContentValues().apply {
            put(WorkoutClusterContract.COLUMN_NAME, "Mountain Trail Loop")
            put(WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID, 2)
            put(WorkoutClusterContract.COLUMN_REF_DISTANCE, 12500.0)
            put(WorkoutClusterContract.COLUMN_HIT_COUNT, 7)
            put(WorkoutClusterContract.COLUMN_SPORT_TYPE, "BICYCLING")
        }
        val insertedId = database.insertOrThrow(WorkoutClusterContract.TABLE_NAME, null, cv)
        assertEquals(1L, insertedId)

        // Perform upgrade from v6 to v11
        helper.onUpgrade(database, 6, 11)
        database.version = 11

        // Verify spatial bound columns exist (added in v7)
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_BOUND_MIN_LAT))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_BOUND_MIN_LNG))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_BOUND_MAX_LAT))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_BOUND_MAX_LNG))

        // Verify preview and polyline columns exist (added in v8)
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_PREVIEW_PATHS))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_ROUTE_POLYLINE))

        // Verify altitude extrema columns exist (added in v10)
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_MIN_ALT_LAT))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_MIN_ALT_LNG))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_MAX_ALT_LAT))
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_MAX_ALT_LNG))

        // Verify activity counter column exists (added in v11)
        assertTrue(RealSqliteTestHelper.hasColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_HAS_COUNTER))
        val counterCol = RealSqliteTestHelper.getColumn(database, WorkoutClusterContract.TABLE_NAME, WorkoutClusterContract.COLUMN_HAS_COUNTER)
        assertNotNull(counterCol)
        assertEquals("1", counterCol?.dfltValue)

        // Verify row data was preserved across migrations
        database.query(WorkoutClusterContract.TABLE_NAME, null, "${BaseColumns._ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mountain Trail Loop", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_NAME)))
            assertEquals(12500.0, cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_REF_DISTANCE)), 0.001)
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_HIT_COUNT)))
            assertEquals("BICYCLING", cursor.getString(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_SPORT_TYPE)))
            // has_counter receives default 1
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutClusterContract.COLUMN_HAS_COUNTER)))
        }

        // Verify idempotent re-run does not throw duplicate column errors
        helper.onUpgrade(database, 6, 11)
    }
}
