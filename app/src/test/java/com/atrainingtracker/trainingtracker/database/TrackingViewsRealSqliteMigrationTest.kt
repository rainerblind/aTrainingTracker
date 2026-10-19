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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration verification suite for TrackingViewsDbHelper (TST-MIG-042.5, REQ-MIG-045).
 * Tests authentic DDL/DML execution in SQLite backed by Robolectric, verifying SectionDividersTable creation and views preservation.
 */
@RunWith(RobolectricTestRunner::class)
class TrackingViewsRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_tracking_views_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: TrackingViewsDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = TrackingViewsDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    private fun scaffoldV13Schema(database: SQLiteDatabase) {
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V13)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V8)
        database.version = 13
    }

    @Test
    fun testUpgradeV13ToV14CreatesSectionDividersTableAndPreservesCockpitViews() {
        val database = checkNotNull(db)
        scaffoldV13Schema(database)

        // Insert historical cockpit view at v13
        val viewCv = ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Main Cockpit")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, "BIKE")
            put(TrackingViewsDbHelper.LAYOUT_NR, 1)
            put(TrackingViewsDbHelper.SHOW_LAP_BUTTON, 1)
            put(TrackingViewsDbHelper.SHOW_MAP, 1)
            put(TrackingViewsDbHelper.SHOW_LIVE_SEGMENTS, 1)
            put(TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE, 1)
            put(TrackingViewsDbHelper.SHOW_LIVE_CLIMBS, 1)
            put(TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS, 1)
            put(TrackingViewsDbHelper.ELEVATION_PROFILE_SCOPE, 0)
            put(TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD, 2000)
        }
        val viewId = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, viewCv)
        assertEquals(1L, viewId)

        // Insert rows in view
        val row1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HEART_RATE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, row1)

        val row2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, row2)

        // Execute upgrade v13 -> v14
        helper.onUpgrade(database, 13, 14)
        database.version = 14

        // Verify SectionDividersTable exists in real SQLite
        assertTrue("Table SectionDividersTable must exist", RealSqliteTestHelper.hasTable(database, TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE))
        assertTrue("Column viewId must exist", RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, TrackingViewsDbHelper.VIEW_ID))
        assertTrue("Column rowNr must exist", RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, TrackingViewsDbHelper.ROW_NR))
        assertTrue("Column title must exist", RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, TrackingViewsDbHelper.TITLE))

        // Verify pre-existing views and rows preserved intact
        database.query(TrackingViewsDbHelper.VIEWS_TABLE, null, "${TrackingViewsDbHelper.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Main Cockpit", cursor.getString(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.NAME)))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.SHOW_LIVE_CLIMBS)))
            assertEquals(2000, cursor.getInt(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.ELEVATION_PROFILE_LOOKAHEAD)))
        }
        assertEquals(2L, RealSqliteTestHelper.getRowCount(database, TrackingViewsDbHelper.ROWS_TABLE))

        // Insert and query a section divider
        val dividerCv = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.TITLE, "Speed & Cadence Metrics")
        }
        val dividerId = database.insertOrThrow(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, dividerCv)
        assertEquals(1L, dividerId)

        database.query(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, "${TrackingViewsDbHelper.C_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Speed & Cadence Metrics", cursor.getString(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.TITLE)))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.VIEW_ID)))
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow(TrackingViewsDbHelper.ROW_NR)))
        }
    }

    @Test
    fun testInsertSensorFieldAt_strictlyIsolatesTabView() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V13)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V8)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 14

        val manager = TrackingViewsDatabaseManager.getInstance(context)
        val dbField = TrackingViewsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, database)

        // Seed Tab 1 (viewId = 1) with 2 rows and a divider at row 2
        val r1Tab1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        val r2Tab1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "CADENCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r1Tab1)
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r2Tab1)

        val divTab1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.TITLE, "Tab 1 Divider")
        }
        database.insertOrThrow(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, divTab1)

        // Seed Tab 2 (viewId = 2) with 2 rows and a divider at row 2
        val r1Tab2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 2)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HEART_RATE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        val r2Tab2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 2)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "POWER")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r1Tab2)
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r2Tab2)

        val divTab2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 2)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.TITLE, "Tab 2 Divider")
        }
        database.insertOrThrow(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, divTab2)

        // Action: Insert a new row at row 1 in Tab 1 (colNr = -1)
        manager.insertSensorFieldAt(
            1L,
            1,
            -1,
            SensorType.DISTANCE_m,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        // Verify Tab 1:
        // Now has 3 rows: row 1 (DISTANCE_m), row 2 (SPEED), row 3 (CADENCE)
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.SENSOR_TYPE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null,
            "${TrackingViewsDbHelper.ROW_NR} ASC"
        ).use { cursor ->
            assertEquals(3, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals("DISTANCE_m", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals("SPEED", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals("CADENCE", cursor.getString(1))
        }

        // Tab 1 Divider shifted from row 2 to row 3
        database.query(
            TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null, null
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals("Tab 1 Divider", cursor.getString(1))
        }

        // Verify Tab 2 (CRITICAL - Multi-tab isolation invariant REQ-UI-413.3):
        // Tab 2 rows MUST remain completely untouched (Row 1: HEART_RATE, Row 2: POWER)
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.COL_NR, TrackingViewsDbHelper.SENSOR_TYPE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("2"),
            null, null,
            "${TrackingViewsDbHelper.ROW_NR} ASC"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("HEART_RATE", cursor.getString(2))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("POWER", cursor.getString(2))
        }

        // Tab 2 Divider MUST remain at Row 2
        database.query(
            TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("2"),
            null, null, null
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals("Tab 2 Divider", cursor.getString(1))
        }
    }

    @Test
    fun testInsertSensorFieldAt_rollsBackEntirelyOnError() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V13)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V8)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 14

        val manager = TrackingViewsDatabaseManager.getInstance(context)
        val dbField = TrackingViewsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, database)

        // Seed Tab 1 with 2 rows and 1 divider
        val r1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        val r2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "CADENCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r1)
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r2)

        val div = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.TITLE, "Pre Divider")
        }
        database.insertOrThrow(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, div)

        // Add a trigger on LayoutRowsTable to simulate a mid-transaction constraint failure on insert
        database.execSQL(
            "CREATE TRIGGER fail_on_altitude BEFORE INSERT ON ${TrackingViewsDbHelper.ROWS_TABLE} " +
            "FOR EACH ROW WHEN NEW.${TrackingViewsDbHelper.SENSOR_TYPE} = 'ALTITUDE' " +
            "BEGIN SELECT RAISE(ABORT, 'Simulated constraint failure'); END;"
        )

        try {
            manager.insertSensorFieldAt(
                1L,
                1,
                -1,
                SensorType.ALTITUDE,
                ViewSize.LARGE,
                null,
                FilterType.INSTANTANEOUS,
                0.0
            )
        } catch (_: Exception) {
            // Expected constraint exception triggered
        }

        // Verify entire transaction rolled back:
        // Rows in Tab 1 must remain exactly Row 1 (SPEED) and Row 2 (CADENCE)
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.SENSOR_TYPE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null,
            "${TrackingViewsDbHelper.ROW_NR} ASC"
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals("SPEED", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals("CADENCE", cursor.getString(1))
        }

        // Divider must remain at Row 2, NOT shifted to Row 3
        database.query(
            TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null, null
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals("Pre Divider", cursor.getString(1))
        }
    }

    @Test
    fun testInsertSensorFieldAt_normalizesGappedGridAndDividers() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V13)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V8)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 14

        val manager = TrackingViewsDatabaseManager.getInstance(context)
        val dbField = TrackingViewsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, database)

        // Seed Tab 1 with gapped rows: Row 1, Row 3, Row 5
        val r1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        val r3 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 3)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "CADENCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        val r5 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 5)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HEART_RATE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
        }
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r1)
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r3)
        database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, r5)

        // Divider at row 3 (above CADENCE)
        val div = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 3)
            put(TrackingViewsDbHelper.TITLE, "Middle Divider")
        }
        database.insertOrThrow(TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, div)

        // Insert new row at row 2
        manager.insertSensorFieldAt(
            1L,
            2,
            -1,
            SensorType.POWER,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        // Post-normalization check (REQ-UI-413.5):
        // All 4 rows must be contiguous 1, 2, 3, 4 with ZERO gaps!
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.SENSOR_TYPE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null,
            "${TrackingViewsDbHelper.ROW_NR} ASC"
        ).use { cursor ->
            assertEquals(4, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertEquals("SPEED", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals("POWER", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(3, cursor.getInt(0))
            assertEquals("CADENCE", cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals(4, cursor.getInt(0))
            assertEquals("HEART_RATE", cursor.getString(1))
        }

        // Section divider remapped accurately to row 3 (remaining above CADENCE)
        database.query(
            TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
            arrayOf(TrackingViewsDbHelper.ROW_NR, TrackingViewsDbHelper.TITLE),
            "${TrackingViewsDbHelper.VIEW_ID} = ?",
            arrayOf("1"),
            null, null, null
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals("Middle Divider", cursor.getString(1))
        }
    }
}
