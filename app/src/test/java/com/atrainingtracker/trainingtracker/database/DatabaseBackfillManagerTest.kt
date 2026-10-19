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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verification suite for DatabaseBackfillManager (TST-MIG-043.5, TST-MIG-043.6, REQ-MIG-046.2, REQ-MIG-046.3).
 * Verifies off-upgrade idempotent convergence, interruption safety, and DAL graceful fallback.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseBackfillManagerTest {

    private lateinit var context: Context
    private lateinit var summariesDb: SQLiteDatabase
    private lateinit var clusterDb: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)

        summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
        clusterDb = WorkoutClusterDatabaseManager.getInstance(context).getDatabase()

        // Clean tables
        summariesDb.delete(WorkoutSummaries.TABLE, null, null)
        summariesDb.delete(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, null)
        clusterDb.delete(WorkoutClusterContract.TABLE_NAME, null, null)
    }

    @After
    fun tearDown() {
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
    }

    @Test
    fun testHasPendingBackfillsDetectsUnpopulatedData() {
        // When tables are empty, hasPendingSpatialBounds is false
        assertFalse(DatabaseBackfillManager.hasPendingSpatialBounds(summariesDb))

        // Insert workout with mapPolyline but missing spatial bounds
        val points = listOf(LatLng(48.137, 11.575), LatLng(48.140, 11.580))
        val polyline = PolyUtil.encode(points)

        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Test Polyline Workout")
            put(WorkoutSummaries.MAP_POLYLINE, polyline)
            putNull(WorkoutSummaries.BOUND_MIN_LAT)
            putNull(WorkoutSummaries.BOUND_MIN_LNG)
            putNull(WorkoutSummaries.BOUND_MAX_LAT)
            putNull(WorkoutSummaries.BOUND_MAX_LNG)
        }
        summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, cv)

        assertTrue("hasPendingSpatialBounds must detect missing bounding box", DatabaseBackfillManager.hasPendingSpatialBounds(summariesDb))
        assertTrue("hasPendingBackfills must return true when unpopulated data exists", DatabaseBackfillManager.hasPendingBackfills(context))
    }

    @Test
    fun testExecutePendingBackfillsConvergesAndIsIdempotent() = runBlocking {
        // Insert 2 workouts:
        // Workout 1 has polyline, missing spatial bounds
        val points = listOf(LatLng(48.100, 11.500), LatLng(48.200, 11.600))
        val polyline = PolyUtil.encode(points)

        val cv1 = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Workout With Polyline")
            put(WorkoutSummaries.MAP_POLYLINE, polyline)
            putNull(WorkoutSummaries.BOUND_MIN_LAT)
            putNull(WorkoutSummaries.BOUND_MAX_LAT)
        }
        val id1 = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, cv1)

        // Workout 2 has null polyline (e.g. indoor workout)
        val cv2 = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Indoor Workout")
            put(WorkoutSummaries.MAP_POLYLINE, "")
        }
        val id2 = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, cv2)

        assertTrue(DatabaseBackfillManager.hasPendingSpatialBounds(summariesDb))

        // Execute backfill
        DatabaseBackfillManager.executePendingBackfills(context)

        // Verify Workout 1 spatial bounds were populated
        summariesDb.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf(id1.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val minLatIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
            val maxLatIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)
            val minLngIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)
            val maxLngIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)

            assertFalse("boundMinLat should be populated", cursor.isNull(minLatIdx))
            assertFalse("boundMaxLat should be populated", cursor.isNull(maxLatIdx))
            assertEquals(48.100, cursor.getDouble(minLatIdx), 0.001)
            assertEquals(48.200, cursor.getDouble(maxLatIdx), 0.001)
            assertEquals(11.500, cursor.getDouble(minLngIdx), 0.001)
            assertEquals(11.600, cursor.getDouble(maxLngIdx), 0.001)
        }

        // Verify spatial bounds pending state is now false
        assertFalse(DatabaseBackfillManager.hasPendingSpatialBounds(summariesDb))

        // Idempotency: execute backfill a second time - must not crash or alter values
        DatabaseBackfillManager.executePendingBackfills(context)

        summariesDb.query(WorkoutSummaries.TABLE, null, "${WorkoutSummaries.C_ID} = ?", arrayOf(id1.toString()), null, null, null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val minLatIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
            assertEquals(48.100, cursor.getDouble(minLatIdx), 0.001)
        }
    }

    @Test
    fun testDalGracefulFallbackOnUnmigratedRows() {
        // Insert workout with null spatial bounds and empty streams
        val cv = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Unmigrated Workout")
            putNull(WorkoutSummaries.BOUND_MIN_LAT)
            putNull(WorkoutSummaries.BOUND_MAX_LAT)
            putNull(WorkoutSummaries.DISTANCE_STREAM)
            putNull(WorkoutSummaries.ALTITUDE_STREAM)
        }
        val id = summariesDb.insertOrThrow(WorkoutSummaries.TABLE, null, cv)

        val manager = WorkoutSummariesDatabaseManager.getInstance(context)

        // Reading summary cursor must not throw NPE or crash
        val cursor = manager.getWorkoutCursor(id)
        assertNotNull("Workout cursor should not be null", cursor)
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Unmigrated Workout", it.getString(it.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)))
            val minLatIdx = it.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
            assertTrue("boundMinLat should be null for unmigrated workout", it.isNull(minLatIdx))
        }
    }
}
