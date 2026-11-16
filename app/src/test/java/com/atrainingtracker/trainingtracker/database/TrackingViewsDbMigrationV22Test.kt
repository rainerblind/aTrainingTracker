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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite database migration tests verifying schema upgrade from v21 to v22 (TST-TRK-081.1, ATT-7760).
 */
@RunWith(RobolectricTestRunner::class)
class TrackingViewsDbMigrationV22Test {

    private lateinit var context: Context
    private val dbName = "test_tracking_views_v22_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: TrackingViewsDatabaseManager.TrackingViewsDbHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(context)
    }

    @After
    fun tearDown() {
        db?.let {
            if (it.isOpen) it.close()
        }
        context.deleteDatabase(dbName)
    }

    private fun setupV21Database(database: SQLiteDatabase): Long {
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V21)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V19)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 21

        val fieldCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID, 1L)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR, 0)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR, 0)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE, SensorType.POWER.name)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BAR, 0)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_WEIGHT, 1.0f)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_GRAPH, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_HORIZON_S, 300)
        }
        return database.insertOrThrow(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
            null,
            fieldCv
        )
    }

    /**
     * TST-TRK-081.1: Verifies that upgrading from v21 to v22 adds the GraphColor column
     * to LayoutRowsTable with DEFAULT 'GLOBAL_DEFAULT'.
     */
    @Test
    fun testTrackingViewsDbHelper_v21ToV22Migration_addsGraphColorColumn() {
        // TST-TRK-081
        val database = checkNotNull(db)
        val rowId = setupV21Database(database)

        helper.onUpgrade(database, 21, 22)
        database.version = 22

        val cursor = database.rawQuery(
            "PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE + ")",
            null
        )
        val columns = mutableSetOf<String>()
        cursor.use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                columns.add(c.getString(nameIdx))
            }
        }
        assertTrue(
            "GraphColor must exist in LayoutRowsTable after v22 migration",
            columns.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_COLOR)
        )

        database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_COLOR),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(rowId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "GraphColor must default to GLOBAL_DEFAULT for pre-existing rows",
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.DEFAULT_GRAPH_COLOR,
                c.getString(0)
            )
        }
    }

    /**
     * TST-TRK-081.1: Verifies migration idempotency and DatabaseSchemaRegistry version support.
     */
    @Test
    fun testTrackingViewsDbHelper_v21ToV22Migration_seedsDefaultsAndRegistry() {
        // TST-TRK-081
        val database = checkNotNull(db)
        setupV21Database(database)

        helper.onUpgrade(database, 21, 22)
        database.version = 22

        assertTrue(DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 22))
        assertEquals(22, DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db"))

        // Idempotency check: running upgrade again does not crash
        helper.onUpgrade(database, 21, 22)
    }

    /**
     * TST-TRK-081.1: Verifies CREATE_LAYOUTS_TABLE_V22 contains GraphColor column.
     */
    @Test
    fun testTrackingViewsDbHelper_createLayoutsTableV22_containsGraphColor() {
        // TST-TRK-081
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V22)

        val cursor = database.rawQuery(
            "PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE + ")",
            null
        )
        val columns = mutableSetOf<String>()
        cursor.use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                columns.add(c.getString(nameIdx))
            }
        }
        assertTrue(
            "GraphColor must exist in CREATE_LAYOUTS_TABLE_V22",
            columns.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_COLOR)
        )
    }
}
