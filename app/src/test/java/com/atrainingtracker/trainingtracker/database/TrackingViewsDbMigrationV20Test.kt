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
 * Real SQLite database migration tests verifying schema upgrade from v19 to v20 (TST-TRK-058.6a, ATT-6688).
 */
@RunWith(RobolectricTestRunner::class)
class TrackingViewsDbMigrationV20Test {

    private lateinit var context: Context
    private val dbName = "test_tracking_views_v20_migration.db"
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
    fun testTrackingViewsDbHelper_v19ToV20Migration_addsShowMmpCurveColumn() {
        val database = checkNotNull(db)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_VIEWS_TABLE_V15)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_LAYOUTS_TABLE_V19)
        database.execSQL(TrackingViewsDatabaseManager.TrackingViewsDbHelper.CREATE_SECTION_DIVIDERS_TABLE_V14)
        database.version = 19

        // Insert test tab view at v19
        val viewCv = ContentValues().apply {
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME, "Power Analysis Cockpit")
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE, ActivityType.BIKE_POWER.name)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP, 1)
            put(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE, 1)
        }
        val tabId = database.insertOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE, null, viewCv)

        // Execute migration v19 -> v20
        helper.onUpgrade(database, 19, 20)
        database.version = 20

        // Verify column added
        val cursorPragma = database.rawQuery("PRAGMA table_info(" + TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE + ")", null)
        val columnNames = mutableSetOf<String>()
        cursorPragma.use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) {
                columnNames.add(c.getString(nameIdx))
            }
        }
        assertTrue("ShowMmpCurve column must exist in ViewsTable", columnNames.contains(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MMP_CURVE))

        // Query migrated view row
        database.query(
            TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
            arrayOf(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.NAME,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MAP,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_ELEVATION_PROFILE,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_MMP_CURVE
            ),
            "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID} = ?",
            arrayOf(tabId.toString()),
            null, null, null
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Name must remain intact", "Power Analysis Cockpit", c.getString(1))
            assertEquals("ShowMap must remain 1", 1, c.getInt(2))
            assertEquals("ShowElevationProfile must remain 1", 1, c.getInt(3))
            assertEquals("ShowMmpCurve must default to 0", 0, c.getInt(4))
        }

        // Verify DatabaseSchemaRegistry parity (Rule 62)
        assertTrue(DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 20))
        assertEquals(20, DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db"))

        // Verify idempotency
        helper.onUpgrade(database, 19, 20)
    }
}
