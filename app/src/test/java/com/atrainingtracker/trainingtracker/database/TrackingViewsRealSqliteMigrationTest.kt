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
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    /**
     * TST-UI-384.1: Database migration v14 -> v15 adds SHOW_BIKE_BELL column and sets cycling defaults.
     */
    @Test
    fun testTrackingViewsDbHelper_v14ToV15Migration_addsShowBikeBellColumnAndSetsCyclingDefaults() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V13)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 14

        // Insert cycling tabs and running tab at v14
        val bikeSpeedCadenceView = ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Bike Speed/Cadence")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, "BIKE_SPEED_AND_CADENCE")
            put(TrackingViewsDbHelper.LAYOUT_NR, 1)
        }
        val bikePowerView = ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Bike Power")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, "BIKE_POWER")
            put(TrackingViewsDbHelper.LAYOUT_NR, 2)
        }
        val runView = ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Running")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, "RUN")
            put(TrackingViewsDbHelper.LAYOUT_NR, 3)
        }

        val id1 = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, bikeSpeedCadenceView)
        val id2 = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, bikePowerView)
        val id3 = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, runView)

        // Execute upgrade v14 -> v15
        helper.onUpgrade(database, 14, 15)
        database.version = 15

        // Verify column exists
        assertTrue(
            "Column SHOW_BIKE_BELL must exist",
            RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.VIEWS_TABLE, TrackingViewsDbHelper.SHOW_BIKE_BELL)
        )

        // Verify default = 1 for cycling tabs
        database.query(
            TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(TrackingViewsDbHelper.SHOW_BIKE_BELL),
            "${TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(id1.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BIKE_SPEED_AND_CADENCE must default showBikeBell to 1", 1, cursor.getInt(0))
        }

        database.query(
            TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(TrackingViewsDbHelper.SHOW_BIKE_BELL),
            "${TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(id2.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BIKE_POWER must default showBikeBell to 1", 1, cursor.getInt(0))
        }

        // Verify default = 0 for running tab
        database.query(
            TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(TrackingViewsDbHelper.SHOW_BIKE_BELL),
            "${TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(id3.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("RUN must default showBikeBell to 0", 0, cursor.getInt(0))
        }

        // Verify idempotency (re-running onUpgrade must not throw)
        helper.onUpgrade(database, 14, 15)

        // Verify DatabaseSchemaRegistry versions
        assertEquals(
            TrackingViewsDbHelper.DB_VERSION,
            com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry.getMaxSupportedVersion("FlexibleGenericView.db")
        )
        assertEquals(
            TrackingViewsDbHelper.DB_VERSION,
            com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db")
        )
    }

    /**
     * TST-TRK-046.5: Database migration v15 -> v16 adds ShowValue and ShowBar columns and backfills defaults.
     */
    @Test
    fun testTrackingViewsDbHelper_v15ToV16Migration_addsShowValueAndShowBar() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V15)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V8)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 15

        // Insert rows with different sensor types
        val hrRow = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HR")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
        }
        val speedRow = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED_mps")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
        }
        val hrId = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, hrRow)
        val speedId = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, speedRow)

        // Execute upgrade v15 -> v16
        helper.onUpgrade(database, 15, 16)
        database.version = 16

        // Verify columns exist
        assertTrue(
            "Column SHOW_VALUE must exist",
            RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.ROWS_TABLE, TrackingViewsDbHelper.SHOW_VALUE)
        )
        assertTrue(
            "Column SHOW_BAR must exist",
            RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.ROWS_TABLE, TrackingViewsDbHelper.SHOW_BAR)
        )

        // Verify HR defaults: ShowValue = 1, ShowBar = 1
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.SHOW_VALUE, TrackingViewsDbHelper.SHOW_BAR),
            "${TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(hrId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("HR ShowValue must default to 1", 1, cursor.getInt(0))
            assertEquals("HR ShowBar must default to 1", 1, cursor.getInt(1))
        }

        // Verify SPEED_mps defaults: ShowValue = 1, ShowBar = 0
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.SHOW_VALUE, TrackingViewsDbHelper.SHOW_BAR),
            "${TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(speedId.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("SPEED_mps ShowValue must default to 1", 1, cursor.getInt(0))
            assertEquals("SPEED_mps ShowBar must default to 0", 0, cursor.getInt(1))
        }

        // Verify idempotency
        helper.onUpgrade(database, 15, 16)
    }

    /**
     * TST-UI-437.1: Database migration v16 -> v17 adds ColWeight column and backfills default 1.0.
     */
    @Test
    fun testTrackingViewsDbHelper_v16ToV17Migration_addsColWeightAndBackfillsDefaults() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V15)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V16)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 16

        val field1 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "POWER")
            put(TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        }
        val field2 = ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, 1)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 2)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "CADENCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 0)
        }
        val id1 = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, field1)
        val id2 = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, field2)

        // Execute upgrade v16 -> v17
        helper.onUpgrade(database, 16, 17)
        database.version = 17

        // Verify column exists
        assertTrue(
            "Column COL_WEIGHT must exist",
            RealSqliteTestHelper.hasColumn(database, TrackingViewsDbHelper.ROWS_TABLE, TrackingViewsDbHelper.COL_WEIGHT)
        )

        // Verify default = 1.0 backfilled for existing rows
        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.COL_WEIGHT),
            "${TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(id1.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Field 1 ColWeight must default to 1.0", 1.0, cursor.getDouble(0), 0.001)
        }

        database.query(
            TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDbHelper.COL_WEIGHT),
            "${TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(id2.toString()),
            null, null, null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Field 2 ColWeight must default to 1.0", 1.0, cursor.getDouble(0), 0.001)
        }

        // Verify idempotency
        helper.onUpgrade(database, 16, 17)
    }

    /**
     * TST-TRK-046.1: Database migration v17 -> v18 evaluates sport types, re-applies legacy showGauge per profile,
     * restricts Balance/W'Bal to Bike, and enforces ShowValue = 1 when ShowBar == 0.
     */
    @Test
    fun testTrackingViewsDbHelper_v17ToV18Migration_sportDependentBarsAndLegacyGaugeParity() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V15)
        database.execSQL(TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V17)
        database.execSQL(TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 17

        // Configure legacy zone display options: HR_RUN showGauge = true, PWR_BIKE showGauge = false
        val dataStore = SettingsDataStore.getInstance(context)
        runBlocking {
            dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.HR_RUN, ZoneDisplayOptions(showGauge = true))
            dataStore.saveZoneDisplayOptions(SettingsDataStore.ZoneType.PWR_BIKE, ZoneDisplayOptions(showGauge = false))
            dataStore.snapshotStateFlow.filter { it.hrRunDisplayOptions.showGauge }.first()
        }

        // 1. Insert views for different sports
        val bikeViewId = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Bike Cockpit")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, com.atrainingtracker.banalservice.ActivityType.BIKE_POWER.name)
            put(TrackingViewsDbHelper.LAYOUT_NR, 1)
        })
        val runViewId = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Run Cockpit")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, com.atrainingtracker.banalservice.ActivityType.RUN_SPEED_AND_CADENCE.name)
            put(TrackingViewsDbHelper.LAYOUT_NR, 2)
        })
        val genericViewId = database.insertOrThrow(TrackingViewsDbHelper.VIEWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.NAME, "Other Cockpit")
            put(TrackingViewsDbHelper.ACTIVITY_TYPE, com.atrainingtracker.banalservice.ActivityType.GENERIC_HR.name)
            put(TrackingViewsDbHelper.LAYOUT_NR, 3)
        })

        // 2. Insert test rows
        // Bike Power: ShowBar originally 1, but PWR_BIKE showGauge == false -> should become 0, ShowValue 1
        val rowBikePower = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, bikeViewId)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "POWER")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        })

        // Bike Balance: Should receive ShowBar = 1
        val rowBikeBalance = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, bikeViewId)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "PEDAL_POWER_BALANCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 0)
        })

        // Bike W'Bal: Should receive ShowBar = 1
        val rowBikeWPrime = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, bikeViewId)
            put(TrackingViewsDbHelper.ROW_NR, 3)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "W_PRIME_BAL")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 0)
        })

        // Run HR: HR_RUN showGauge == true -> should receive ShowBar = 1
        val rowRunHr = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, runViewId)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HR")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 0)
        })

        // Run Power: Not supported on Run -> should become ShowBar = 0, ShowValue = 1
        val rowRunPower = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, runViewId)
            put(TrackingViewsDbHelper.ROW_NR, 2)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "POWER")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        })

        // Run Balance: Not supported on Run -> should become ShowBar = 0, ShowValue = 1
        val rowRunBalance = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, runViewId)
            put(TrackingViewsDbHelper.ROW_NR, 3)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "PEDAL_POWER_BALANCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        })

        // Generic HR: Not supported on Generic -> should become ShowBar = 0, ShowValue = 1
        val rowGenericHr = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, genericViewId)
            put(TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "HR")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        })

        // Bike Speed: Unsupported bar sensor -> should become ShowBar = 0, ShowValue = 1
        val rowBikeSpeed = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, bikeViewId)
            put(TrackingViewsDbHelper.ROW_NR, 4)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "SPEED_mps")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDbHelper.SHOW_BAR, 1)
        })

        // Row with ShowValue = 0, ShowBar = 0 (Empty tile invariant violation) -> ShowValue MUST be repaired to 1
        val rowEmptyTile = database.insertOrThrow(TrackingViewsDbHelper.ROWS_TABLE, null, ContentValues().apply {
            put(TrackingViewsDbHelper.VIEW_ID, bikeViewId)
            put(TrackingViewsDbHelper.ROW_NR, 5)
            put(TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDbHelper.SENSOR_TYPE, "CADENCE")
            put(TrackingViewsDbHelper.VIEW_SIZE, "NORMAL")
            put(TrackingViewsDbHelper.SHOW_VALUE, 0)
            put(TrackingViewsDbHelper.SHOW_BAR, 0)
        })

        // Execute upgrade v17 -> v18
        helper.onUpgrade(database, 17, 18)
        database.version = 18

        fun queryRow(rowId: Long): Pair<Int, Int> {
            return database.query(
                TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDbHelper.SHOW_VALUE, TrackingViewsDbHelper.SHOW_BAR),
                "${TrackingViewsDbHelper.ROW_ID} = ?",
                arrayOf(rowId.toString()),
                null, null, null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                Pair(cursor.getInt(0), cursor.getInt(1))
            }
        }

        // Verify Bike Power: ShowBar = 0, ShowValue = 1 (legacy PWR_BIKE showGauge was false)
        val (pwrVal, pwrBar) = queryRow(rowBikePower)
        assertEquals("Bike Power ShowValue must be 1", 1, pwrVal)
        assertEquals("Bike Power ShowBar must be 0", 0, pwrBar)

        // Verify Bike Balance: ShowBar = 1, ShowValue = 1
        val (balVal, balBar) = queryRow(rowBikeBalance)
        assertEquals("Bike Balance ShowValue must be 1", 1, balVal)
        assertEquals("Bike Balance ShowBar must be 1", 1, balBar)

        // Verify Bike W'Bal: ShowBar = 1, ShowValue = 1
        val (wVal, wBar) = queryRow(rowBikeWPrime)
        assertEquals("Bike W'Bal ShowValue must be 1", 1, wVal)
        assertEquals("Bike W'Bal ShowBar must be 1", 1, wBar)

        // Verify Run HR: ShowBar = 1, ShowValue = 1 (legacy HR_RUN showGauge was true)
        val (hrVal, hrBar) = queryRow(rowRunHr)
        assertEquals("Run HR ShowValue must be 1", 1, hrVal)
        assertEquals("Run HR ShowBar must be 1", 1, hrBar)

        // Verify Run Power: ShowBar = 0, ShowValue = 1
        val (rPwrVal, rPwrBar) = queryRow(rowRunPower)
        assertEquals("Run Power ShowValue must be 1", 1, rPwrVal)
        assertEquals("Run Power ShowBar must be 0", 0, rPwrBar)

        // Verify Run Balance: ShowBar = 0, ShowValue = 1
        val (rBalVal, rBalBar) = queryRow(rowRunBalance)
        assertEquals("Run Balance ShowValue must be 1", 1, rBalVal)
        assertEquals("Run Balance ShowBar must be 0", 0, rBalBar)

        // Verify Generic HR: ShowBar = 0, ShowValue = 1
        val (gHrVal, gHrBar) = queryRow(rowGenericHr)
        assertEquals("Generic HR ShowValue must be 1", 1, gHrVal)
        assertEquals("Generic HR ShowBar must be 0", 0, gHrBar)

        // Verify Bike Speed: ShowBar = 0, ShowValue = 1
        val (spdVal, spdBar) = queryRow(rowBikeSpeed)
        assertEquals("Bike Speed ShowValue must be 1", 1, spdVal)
        assertEquals("Bike Speed ShowBar must be 0", 0, spdBar)

        // Verify Empty Tile repair: ShowBar = 0 -> ShowValue must be repaired to 1
        val (emptyVal, emptyBar) = queryRow(rowEmptyTile)
        assertEquals("Empty tile must be repaired to ShowValue = 1", 1, emptyVal)
        assertEquals("Empty tile ShowBar must be 0", 0, emptyBar)

        // Verify DatabaseSchemaRegistry parity (Rule 62)
        assertTrue(
            "TrackingViews.db version 18 must be supported",
            com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 18)
        )
        assertTrue(
            "TrackingViews.db version 19 must be supported",
            com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 19)
        )
        assertEquals(
            TrackingViewsDbHelper.DB_VERSION,
            com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db")
        )

        // Verify idempotency
        helper.onUpgrade(database, 17, 18)
    }
}
