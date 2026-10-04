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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

/**
 * Unit tests verifying database persistence, queries, and spatial deduplication
 * in [ClimbsDatabaseManager] (REQ-MAP-027, TST-MAP-029 Group 2).
 */
class ClimbsDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var mockDbHelper: ClimbsDbHelper
    private lateinit var manager: ClimbsDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        mockDbHelper = mockk(relaxed = true)

        every { mockDbHelper.writableDatabase } returns mockDb
        every { mockDbHelper.readableDatabase } returns mockDb

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().putNull(any<String>()) } returns Unit

        manager = ClimbsDatabaseManager(
            context = mockContext,
            dbDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            dbHelper = mockDbHelper
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun insertClimb_insertsRecordAndReturnsId() = runBlocking {
        every { mockDb.insert(ClimbsDbHelper.TABLE_CLIMBS, null, any()) } returns 101L

        val climb = Climb(
            name = "Test Ascent 1.5km @ 6%",
            routeId = 42L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.02,
            distanceMeters = 1500.0,
            elevationGainMeters = 90.0,
            avgGradePercent = 6.0,
            maxGradePercent = 9.5,
            category = ClimbCategory.CAT_4
        )

        val id = manager.insertClimb(climb)
        assertEquals(101L, id)
        verify { mockDb.insert(ClimbsDbHelper.TABLE_CLIMBS, null, any()) }
    }

    @Test
    fun spatialDeduplication_skipsDuplicateWithin50Meters() = runBlocking {
        // Mock existing climb in database at (48.0, 11.0) -> (48.01, 11.02)
        val mockCursor = mockk<Cursor>(relaxed = true)
        var cursorRead = false
        every { mockCursor.moveToNext() } answers {
            if (!cursorRead) {
                cursorRead = true
                true
            } else false
        }
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ID) } returns 0
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_NAME) } returns 1
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ROUTE_ID) } returns 2
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LAT) } returns 3
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LNG) } returns 4
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LAT) } returns 5
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LNG) } returns 6
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_DISTANCE_M) } returns 7
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M) } returns 8
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_AVG_GRADE) } returns 9
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_MAX_GRADE) } returns 10
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_CATEGORY) } returns 11
        every { mockCursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_PATH_POLYLINE) } returns 12

        every { mockCursor.getLong(0) } returns 55L
        every { mockCursor.getString(1) } returns "Existing Ascent"
        every { mockCursor.isNull(2) } returns true
        every { mockCursor.getDouble(3) } returns 48.0
        every { mockCursor.getDouble(4) } returns 11.0
        every { mockCursor.getDouble(5) } returns 48.01
        every { mockCursor.getDouble(6) } returns 11.02
        every { mockCursor.getDouble(7) } returns 1500.0
        every { mockCursor.getDouble(8) } returns 90.0
        every { mockCursor.getDouble(9) } returns 6.0
        every { mockCursor.getDouble(10) } returns 9.0
        every { mockCursor.getString(11) } returns "CAT_4"
        every { mockCursor.getString(12) } returns ""

        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, null, null, null, null, null, any()) } returns mockCursor

        // New candidate climb starting ~13m away from existing start, ending ~13m away from existing end
        val duplicateClimb = Climb(
            name = "Duplicate Hill",
            startLat = 48.0001,
            startLng = 11.0001,
            endLat = 48.0101,
            endLng = 11.0201,
            distanceMeters = 1505.0,
            elevationGainMeters = 91.0,
            avgGradePercent = 6.0,
            maxGradePercent = 9.0,
            category = ClimbCategory.CAT_4
        )

        val resultId = manager.insertClimbWithDeduplication(duplicateClimb)
        assertEquals(55L, resultId)
        // Verify insert was NOT called because it was deduplicated
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }

    @Test
    fun pathPointsSerialization_roundtripsCorrectly() {
        val original = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 200.0),
            PathPoint(distance = 500.0, latLng = LatLng(48.005, 11.005), altitude = 230.0),
            PathPoint(distance = 1200.0, latLng = LatLng(48.01, 11.01), altitude = 280.0)
        )

        val serialized = ClimbsDatabaseManager.serializePathPoints(original)
        assertFalse(serialized.isBlank())

        val deserialized = ClimbsDatabaseManager.deserializePathPoints(serialized)
        assertEquals(3, deserialized.size)
        assertEquals(original[0].latLng.latitude, deserialized[0].latLng.latitude, 0.000001)
        assertEquals(original[0].latLng.longitude, deserialized[0].latLng.longitude, 0.000001)
        assertEquals(original[0].altitude, deserialized[0].altitude, 0.01)
        assertEquals(original[2].distance, deserialized[2].distance, 0.01)
    }

    @Test
    fun deleteClimb_executesDeleteStatement() = runBlocking {
        every { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, any(), any()) } returns 1
        val deleted = manager.deleteClimb(42L)
        assertEquals(1, deleted)
        verify { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("42")) }
    }

    @Test
    fun insertClimbsWithDeduplicationBatch_executesInTransaction() = runBlocking {
        every { mockDb.beginTransaction() } just Runs
        every { mockDb.setTransactionSuccessful() } just Runs
        every { mockDb.endTransaction() } just Runs

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns false
        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, null, null, null, null, null, any()) } returns mockCursor
        every { mockDb.insert(any(), any(), any()) } returns 101L andThen 102L

        val climbs = listOf(
            Climb(
                name = "Hill 1",
                startLat = 48.0,
                startLng = 11.0,
                endLat = 48.01,
                endLng = 11.02,
                distanceMeters = 1500.0,
                elevationGainMeters = 90.0,
                avgGradePercent = 6.0,
                maxGradePercent = 9.0,
                category = ClimbCategory.CAT_4
            ),
            Climb(
                name = "Hill 2",
                startLat = 48.1,
                startLng = 11.1,
                endLat = 48.11,
                endLng = 11.12,
                distanceMeters = 2000.0,
                elevationGainMeters = 120.0,
                avgGradePercent = 6.0,
                maxGradePercent = 10.0,
                category = ClimbCategory.CAT_3
            )
        )

        val ids = manager.insertClimbsWithDeduplicationBatch(climbs)
        assertEquals(listOf(101L, 102L), ids)
        verify { mockDb.beginTransaction() }
        verify { mockDb.setTransactionSuccessful() }
        verify { mockDb.endTransaction() }
    }
}
