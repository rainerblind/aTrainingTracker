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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RouteContract
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.Laps
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration and index integrity test suite for schema versions and performance indexes (REQ-DAT-034.1, TST-DAT-029.1).
 * Verifies:
 * 1. RoutesDbHelper (v11) creates idx_route_points_route_id on onCreate and onUpgrade.
 * 2. WorkoutSummariesDbHelper (v26) creates idx_extrema_workout_sensor_extrema, idx_summaries_cluster_id,
 *    and idx_summaries_file_base_name_nocase on onCreate and onUpgrade.
 * 3. LapsDbHelper (v3) creates idx_laps_workout_id on onCreate and onUpgrade.
 * 4. Real SQLite EXPLAIN QUERY PLAN confirms indexed index scans.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationIndexTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("Routes.db")
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("Laps.db")
        context.deleteDatabase("test_routes_idx.db")
        context.deleteDatabase("test_summaries_idx.db")
        context.deleteDatabase("test_laps_idx.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("Routes.db")
        context.deleteDatabase("WorkoutSummaries.db")
        context.deleteDatabase("Laps.db")
        context.deleteDatabase("test_routes_idx.db")
        context.deleteDatabase("test_summaries_idx.db")
        context.deleteDatabase("test_laps_idx.db")
    }

    private fun getIndexNames(db: SQLiteDatabase, tableName: String): List<String> {
        val indexes = mutableListOf<String>()
        db.rawQuery("PRAGMA index_list(\"$tableName\")", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes.add(cursor.getString(nameIdx))
            }
        }
        return indexes
    }

    private fun explainQueryPlan(db: SQLiteDatabase, query: String): String {
        val plan = StringBuilder()
        db.rawQuery("EXPLAIN QUERY PLAN $query", null).use { cursor ->
            val detailIdx = cursor.getColumnIndex("detail")
            while (cursor.moveToNext()) {
                plan.append(cursor.getString(detailIdx)).append(" | ")
            }
        }
        return plan.toString()
    }

    @Test
    fun testSchemaVersionConstants() {
        assertEquals("RoutesDbHelper.DB_VERSION must be 11", 11, RoutesDbHelper.DB_VERSION)
        assertEquals("WorkoutSummariesDbHelper.DB_VERSION must be 26", 26, WorkoutSummariesDbHelper.DB_VERSION)
        assertEquals("LapsDbHelper.DB_VERSION must be 3", 3, LapsDbHelper.DB_VERSION)
    }

    // --- Routes.db tests ---

    @Test
    fun testRoutesDb_freshOnCreate_createsIndexAndUsesQueryPlan() {
        val helper = RoutesDbHelper(context)
        val db = helper.writableDatabase

        val indexes = getIndexNames(db, RouteContract.TABLE_ROUTE_POINTS)
        assertTrue("idx_route_points_route_id must exist in route_points indexes: $indexes",
            indexes.contains("idx_route_points_route_id"))

        val plan = explainQueryPlan(db, "SELECT * FROM ${RouteContract.TABLE_ROUTE_POINTS} WHERE ${RouteContract.COLUMN_ROUTE_ID_FK} = 42")
        assertTrue("Query plan must use idx_route_points_route_id: $plan",
            plan.contains("idx_route_points_route_id"))

        db.close()
    }

    @Test
    fun testRoutesDb_onUpgradeV10ToV11_createsIndexIdempotently() {
        val dbName = "test_routes_idx.db"
        context.deleteDatabase(dbName)
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        // Scaffold v10 tables without the new index
        db.execSQL("""
            CREATE TABLE ${RouteContract.TABLE_ROUTE_POINTS} (
                ${RouteContract.COLUMN_POINT_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${RouteContract.COLUMN_ROUTE_ID_FK} INTEGER,
                ${RouteContract.COLUMN_LAT} REAL,
                ${RouteContract.COLUMN_LNG} REAL,
                ${RouteContract.COLUMN_DIST_FROM_START} REAL,
                ${RouteContract.COLUMN_ALTITUDE} REAL
            );
        """.trimIndent())
        db.version = 10

        val helper = RoutesDbHelper(context)
        helper.onUpgrade(db, 10, 11)
        db.version = 11

        val indexes = getIndexNames(db, RouteContract.TABLE_ROUTE_POINTS)
        assertTrue("idx_route_points_route_id must exist after upgrade v10->v11: $indexes",
            indexes.contains("idx_route_points_route_id"))

        // Repeat to confirm idempotence (CREATE INDEX IF NOT EXISTS)
        helper.onUpgrade(db, 10, 11)

        db.close()
    }

    // --- WorkoutSummaries.db tests ---

    @Test
    fun testWorkoutSummariesDb_freshOnCreate_createsAllThreeIndexes() {
        val helper = WorkoutSummariesDbHelper(context)
        val db = helper.writableDatabase

        val extremaIndexes = getIndexNames(db, WorkoutSummaries.TABLE_EXTREMA_VALUES)
        assertTrue("idx_extrema_workout_sensor_extrema must exist: $extremaIndexes",
            extremaIndexes.contains("idx_extrema_workout_sensor_extrema"))

        val summariesIndexes = getIndexNames(db, WorkoutSummaries.TABLE)
        assertTrue("idx_summaries_cluster_id must exist: $summariesIndexes",
            summariesIndexes.contains("idx_summaries_cluster_id"))
        assertTrue("idx_summaries_file_base_name_nocase must exist: $summariesIndexes",
            summariesIndexes.contains("idx_summaries_file_base_name_nocase"))

        val planExtrema = explainQueryPlan(db, "SELECT * FROM ${WorkoutSummaries.TABLE_EXTREMA_VALUES} WHERE ${WorkoutSummaries.WORKOUT_ID} = 1 AND ${WorkoutSummaries.SENSOR_TYPE} = 2 AND ${WorkoutSummaries.EXTREMA_TYPE} = 3")
        assertTrue("Plan must use idx_extrema_workout_sensor_extrema: $planExtrema",
            planExtrema.contains("idx_extrema_workout_sensor_extrema"))

        val planCluster = explainQueryPlan(db, "SELECT * FROM ${WorkoutSummaries.TABLE} WHERE ${WorkoutSummaries.CLUSTER_ID} = 5")
        assertTrue("Plan must use idx_summaries_cluster_id: $planCluster",
            planCluster.contains("idx_summaries_cluster_id"))

        val planNocase = explainQueryPlan(db, "SELECT * FROM ${WorkoutSummaries.TABLE} WHERE ${WorkoutSummaries.FILE_BASE_NAME} = 'track1' COLLATE NOCASE")
        assertTrue("Plan must use idx_summaries_file_base_name_nocase: $planNocase",
            planNocase.contains("idx_summaries_file_base_name_nocase"))

        db.close()
    }

    @Test
    fun testWorkoutSummariesDb_onUpgradeV25ToV26_createsIndexesIdempotently() {
        val dbName = "test_summaries_idx.db"
        context.deleteDatabase(dbName)
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        db.execSQL("""
            CREATE TABLE ${WorkoutSummaries.TABLE_EXTREMA_VALUES} (
                ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${WorkoutSummaries.WORKOUT_ID} int,
                ${WorkoutSummaries.SENSOR_TYPE} int,
                ${WorkoutSummaries.EXTREMA_TYPE} int,
                ${WorkoutSummaries.VALUE} double,
                ${WorkoutSummaries.LATITUDE} double,
                ${WorkoutSummaries.LONGITUDE} double,
                ${WorkoutSummaries.COUNT} int
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE ${WorkoutSummaries.TABLE} (
                ${WorkoutSummaries.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${WorkoutSummaries.WORKOUT_NAME} text,
                ${WorkoutSummaries.FILE_BASE_NAME} text,
                ${WorkoutSummaries.CLUSTER_ID} integer DEFAULT -1
            );
        """.trimIndent())
        db.version = 25

        val helper = WorkoutSummariesDbHelper(context)
        helper.onUpgrade(db, 25, 26)
        db.version = 26

        val extremaIndexes = getIndexNames(db, WorkoutSummaries.TABLE_EXTREMA_VALUES)
        assertTrue(extremaIndexes.contains("idx_extrema_workout_sensor_extrema"))

        val summariesIndexes = getIndexNames(db, WorkoutSummaries.TABLE)
        assertTrue(summariesIndexes.contains("idx_summaries_cluster_id"))
        assertTrue(summariesIndexes.contains("idx_summaries_file_base_name_nocase"))

        // Repeat for idempotency verification
        helper.onUpgrade(db, 25, 26)

        db.close()
    }

    // --- Laps.db tests ---

    @Test
    fun testLapsDb_freshOnCreate_createsIndexAndUsesQueryPlan() {
        val helper = LapsDbHelper(context)
        val db = helper.writableDatabase

        val indexes = getIndexNames(db, Laps.TABLE)
        assertTrue("idx_laps_workout_id must exist in Laps indexes: $indexes",
            indexes.contains("idx_laps_workout_id"))

        val plan = explainQueryPlan(db, "SELECT * FROM ${Laps.TABLE} WHERE ${Laps.WORKOUT_ID} = 10")
        assertTrue("Plan must use idx_laps_workout_id: $plan",
            plan.contains("idx_laps_workout_id"))

        db.close()
    }

    @Test
    fun testLapsDb_onUpgradeV2ToV3_createsIndexIdempotently() {
        val dbName = "test_laps_idx.db"
        context.deleteDatabase(dbName)
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)

        db.execSQL("""
            CREATE TABLE ${Laps.TABLE} (
                ${Laps.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Laps.WORKOUT_ID} INTEGER,
                ${Laps.LAP_NR} INTEGER,
                ${Laps.TIME_START} INTEGER,
                ${Laps.TIME_TOTAL_s} INTEGER,
                ${Laps.DISTANCE_TOTAL_m} REAL,
                ${Laps.SPEED_AVERAGE_mps} REAL,
                ${Laps.NAME} TEXT,
                ${Laps.DESCRIPTION} TEXT
            );
        """.trimIndent())
        db.version = 2

        val helper = LapsDbHelper(context)
        helper.onUpgrade(db, 2, 3)
        db.version = 3

        val indexes = getIndexNames(db, Laps.TABLE)
        assertTrue("idx_laps_workout_id must exist after upgrade v2->v3: $indexes",
            indexes.contains("idx_laps_workout_id"))

        // Verify idempotency
        helper.onUpgrade(db, 2, 3)

        db.close()
    }
}
