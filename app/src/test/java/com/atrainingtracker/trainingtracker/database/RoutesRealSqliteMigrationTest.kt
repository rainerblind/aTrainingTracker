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
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RouteContract
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real SQLite migration verification suite for RoutesDbHelper (TST-MIG-042.4, REQ-MIG-045).
 * Verifies real SQLite upgrade from v5 to v10, testing column additions, waypoints table creation, and spatial bounds migration.
 */
@RunWith(RobolectricTestRunner::class)
class RoutesRealSqliteMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_routes_migration.db"
    private var db: SQLiteDatabase? = null
    private lateinit var helper: RoutesDbHelper

    private val createV5RoutesTable = """
        CREATE TABLE ${RouteContract.TABLE_ROUTES} (
            ${RouteContract.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${RouteContract.COLUMN_NAME} TEXT,
            ${RouteContract.COLUMN_DESCRIPTION} TEXT,
            ${RouteContract.COLUMN_DISTANCE} REAL,
            ${RouteContract.COLUMN_ELEVATION_GAIN} REAL,
            ${RouteContract.COLUMN_SPORT_TYPE} TEXT,
            ${RouteContract.COLUMN_SOURCE} TEXT,
            ${RouteContract.COLUMN_IS_SELECTED} INTEGER
        );
    """.trimIndent()

    private val createV5RoutePointsTable = """
        CREATE TABLE ${RouteContract.TABLE_ROUTE_POINTS} (
            ${RouteContract.COLUMN_POINT_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${RouteContract.COLUMN_ROUTE_ID_FK} INTEGER,
            ${RouteContract.COLUMN_LAT} REAL,
            ${RouteContract.COLUMN_LNG} REAL,
            ${RouteContract.COLUMN_DIST_FROM_START} REAL,
            ${RouteContract.COLUMN_ALTITUDE} REAL
        );
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        helper = RoutesDbHelper(context)
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db, dbName)
    }

    private fun scaffoldV5Schema(database: SQLiteDatabase) {
        database.execSQL(createV5RoutesTable)
        database.execSQL(createV5RoutePointsTable)
        database.version = 5
    }

    @Test
    fun testUpgradeV5ToV10CreatesWaypointsTableAndMigratesBounds() {
        val database = checkNotNull(db)
        scaffoldV5Schema(database)

        // Insert historical route at v5
        val routeCv = ContentValues().apply {
            put(RouteContract.COLUMN_NAME, "Alpine Loop")
            put(RouteContract.COLUMN_DESCRIPTION, "Scenic mountain pass")
            put(RouteContract.COLUMN_DISTANCE, 45200.0)
            put(RouteContract.COLUMN_ELEVATION_GAIN, 1250.0)
            put(RouteContract.COLUMN_SPORT_TYPE, "BIKE")
            put(RouteContract.COLUMN_SOURCE, "LOCAL")
            put(RouteContract.COLUMN_IS_SELECTED, 1)
        }
        val routeId = database.insertOrThrow(RouteContract.TABLE_ROUTES, null, routeCv)
        assertEquals(1L, routeId)

        // Insert route points with coordinate spread
        val p1 = ContentValues().apply {
            put(RouteContract.COLUMN_ROUTE_ID_FK, 1)
            put(RouteContract.COLUMN_LAT, 47.1234)
            put(RouteContract.COLUMN_LNG, 11.5678)
            put(RouteContract.COLUMN_DIST_FROM_START, 0.0)
            put(RouteContract.COLUMN_ALTITUDE, 600.0)
        }
        database.insertOrThrow(RouteContract.TABLE_ROUTE_POINTS, null, p1)

        val p2 = ContentValues().apply {
            put(RouteContract.COLUMN_ROUTE_ID_FK, 1)
            put(RouteContract.COLUMN_LAT, 47.3456)
            put(RouteContract.COLUMN_LNG, 11.7890)
            put(RouteContract.COLUMN_DIST_FROM_START, 45200.0)
            put(RouteContract.COLUMN_ALTITUDE, 1850.0)
        }
        database.insertOrThrow(RouteContract.TABLE_ROUTE_POINTS, null, p2)

        // Execute upgrade v5 -> v10
        helper.onUpgrade(database, 5, 10)
        database.version = 10

        // Verify table_route_waypoints was created in real SQLite
        assertTrue("Table route_waypoints must exist", RealSqliteTestHelper.hasTable(database, RouteContract.TABLE_ROUTE_WAYPOINTS))
        assertTrue("Column waypoint_name must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTE_WAYPOINTS, RouteContract.COLUMN_WAYPOINT_NAME))
        assertTrue("Column waypoint_type must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTE_WAYPOINTS, RouteContract.COLUMN_WAYPOINT_TYPE))

        // Verify newly added columns on routes table
        assertTrue("Column cluster_id must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_CLUSTER_ID))
        assertTrue("Column bound_min_lat must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_BOUND_MIN_LAT))
        assertTrue("Column bound_min_lng must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_BOUND_MIN_LNG))
        assertTrue("Column bound_max_lat must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_BOUND_MAX_LAT))
        assertTrue("Column bound_max_lng must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_BOUND_MAX_LNG))
        assertTrue("Column synced_at must exist", RealSqliteTestHelper.hasColumn(database, RouteContract.TABLE_ROUTES, RouteContract.COLUMN_SYNCED_AT))

        // Verify spatial bounds migration populated bounding box for route 1
        database.query(RouteContract.TABLE_ROUTES, null, "${RouteContract.COLUMN_ID} = ?", arrayOf("1"), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alpine Loop", cursor.getString(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_NAME)))
            assertEquals(-1, cursor.getInt(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_CLUSTER_ID)))
            assertEquals(47.1234, cursor.getDouble(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MIN_LAT)), 0.0001)
            assertEquals(11.5678, cursor.getDouble(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MIN_LNG)), 0.0001)
            assertEquals(47.3456, cursor.getDouble(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MAX_LAT)), 0.0001)
            assertEquals(11.7890, cursor.getDouble(cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MAX_LNG)), 0.0001)
        }

        // Verify insertion into route_waypoints table works
        val waypointCv = ContentValues().apply {
            put(RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK, 1)
            put(RouteContract.COLUMN_WAYPOINT_NAME, "Summit Pass")
            put(RouteContract.COLUMN_WAYPOINT_LAT, 47.3456)
            put(RouteContract.COLUMN_WAYPOINT_LNG, 11.7890)
            put(RouteContract.COLUMN_WAYPOINT_ELEVATION, 1850.0)
            put(RouteContract.COLUMN_WAYPOINT_TYPE, "SUMMIT")
        }
        val wpId = database.insertOrThrow(RouteContract.TABLE_ROUTE_WAYPOINTS, null, waypointCv)
        assertEquals(1L, wpId)
    }
}
