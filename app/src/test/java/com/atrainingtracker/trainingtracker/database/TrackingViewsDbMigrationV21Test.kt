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
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite database migration tests verifying schema upgrade from v20 to v21 (TST-CON-043.1, ATT-7972).
 */
@RunWith(RobolectricTestRunner::class)
class TrackingViewsDbMigrationV21Test {

    private lateinit var context: Context
    private val dbName = "test_tracking_views_v21_migration.db"
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

    private fun setupV20Database(database: SQLiteDatabase): Pair<Long, Long> {
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V20)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V19)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 20

        val bikeCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME, "Bike Power")
            put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE,
                ActivityType.BIKE_POWER.name
            )
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR, 1)
        }
        val bikeId = database.insertOrThrow(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            null,
            bikeCv
        )

        val runCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME, "Run")
            put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE,
                ActivityType.RUN_SPEED_AND_CADENCE.name
            )
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR, 1)
        }
        val runId = database.insertOrThrow(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            null,
            runCv
        )
        return Pair(bikeId, runId)
    }

    @Test
    fun testTrackingViewsDbHelper_v20ToV21Migration_addsShowRadarLightColumn() {
        val database = checkNotNull(db)
        setupV20Database(database)

        helper.onUpgrade(database, 20, 21)
        database.version = 21

        val cursor = database.rawQuery(
            "PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE + ")",
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
            "ShowRadarLight must exist in ViewsTable",
            columns.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT)
        )
    }

    @Test
    fun testTrackingViewsDbHelper_v20ToV21Migration_seedsDefaultsAndRegistry() {
        val database = checkNotNull(db)
        val (bikeId, runId) = setupV20Database(database)

        helper.onUpgrade(database, 20, 21)
        database.version = 21

        database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(bikeId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ShowRadarLight must default to 1 for cycling tabs", 1, c.getInt(0))
        }

        database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(runId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ShowRadarLight must default to 0 for running tabs", 0, c.getInt(0))
        }

        assertTrue(DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 21))
        assertEquals(21, DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db"))

        // Idempotency check
        helper.onUpgrade(database, 20, 21)
    }

    @Test
    fun testTrackingViewsDbHelper_createViewsTableV21_containsShowRadarLight() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V21)

        val cursor = database.rawQuery(
            "PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE + ")",
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
            "ShowRadarLight must exist in CREATE_VIEWS_TABLE_V21",
            columns.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_RADAR_LIGHT)
        )
    }
}
