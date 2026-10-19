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
}
