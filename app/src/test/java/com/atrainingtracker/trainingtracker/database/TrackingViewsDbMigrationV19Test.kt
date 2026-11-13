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
import com.atrainingtracker.banalservice.ActivityType
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
 * Real SQLite database migration tests verifying schema upgrade from v18 to v19 (TST-TRK-059.1, ATT-1294).
 */
@RunWith(RobolectricTestRunner::class)
class TrackingViewsDbMigrationV19Test {

    private lateinit var context: Context
    private val dbName = "test_tracking_views_v19_migration.db"
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

    @Test
    fun testTrackingViewsDbHelper_v18ToV19Migration_addsShowGraphAndGraphHorizonColumns() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V15)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V18)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 18

        // Insert test tab view
        val viewCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME, "Bike Cockpit")
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE, ActivityType.BIKE_POWER.name)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR, 1)
        }
        val tabId = database.insertOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE, null, viewCv)

        // Insert test sensor field at v18
        val rowCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID, tabId)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE, SensorType.POWER.name)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE, "LARGE")
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_VALUE, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BAR, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_WEIGHT, 2.0f)
        }
        val rowId = database.insertOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE, null, rowCv)

        // Execute migration v18 -> v19
        helper.onUpgrade(database, 18, 19)
        database.version = 19

        // Verify columns added
        val cursorPragma = database.rawQuery("PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE + ")", null)
        val columnNames = mutableSetOf<String>()
        cursorPragma.use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                columnNames.add(c.getString(nameIdx))
            }
        }
        assertTrue("ShowGraph column must exist", columnNames.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_GRAPH))
        assertTrue("GraphHorizonS column must exist", columnNames.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_HORIZON_S))

        // Query migrated row
        database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
            arrayOf(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_VALUE,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_BAR,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_WEIGHT,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_GRAPH,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.GRAPH_HORIZON_S
            ),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID} = ?",
            arrayOf(rowId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ShowValue must remain 1", 1, c.getInt(1))
            assertEquals("ShowBar must remain 1", 1, c.getInt(2))
            assertEquals("ColWeight must remain 2.0", 2.0f, c.getFloat(3), 0.001f)
            assertEquals("ShowGraph must default to 0", 0, c.getInt(4))
            assertEquals("GraphHorizonS must default to 300", 300, c.getInt(5))
        }

        // Verify DatabaseSchemaRegistry parity (Rule 62)
        assertTrue(DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 19))
        assertEquals(20, DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db"))

        // Verify idempotency
        helper.onUpgrade(database, 18, 19)
    }
}
