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

        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns mockCursor

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
        assertEquals(original[0].altitude!!, deserialized[0].altitude!!, 0.01)
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
        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns mockCursor
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

    @Test
    fun insertClimbsWithDeduplicationBatch_deduplicatesWithinExpanded100mThreshold() = runBlocking {
        every { mockDb.beginTransaction() } just Runs
        every { mockDb.setTransactionSuccessful() } just Runs
        every { mockDb.endTransaction() } just Runs

        val mockCursor = mockk<Cursor>(relaxed = true)
        var rowRead = false
        every { mockCursor.moveToNext() } answers {
            if (!rowRead) {
                rowRead = true
                true
            } else {
                false
            }
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

        every { mockCursor.getLong(0) } returns 42L
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

        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns mockCursor

        // Drift: start is ~70m away (0.0006 deg lat), end is ~60m away
        val candidateClimb = Climb(
            name = "Candidate Drift Hill",
            startLat = 48.0006,
            startLng = 11.0,
            endLat = 48.0105,
            endLng = 11.02,
            distanceMeters = 1500.0,
            elevationGainMeters = 88.0,
            avgGradePercent = 5.9,
            maxGradePercent = 9.0,
            category = ClimbCategory.CAT_4
        )

        val ids = manager.insertClimbsWithDeduplicationBatch(listOf(candidateClimb), thresholdMeters = 50.0f)
        assertEquals(listOf(42L), ids)
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }

    @Test
    fun deduplicateExistingClimbs_deletesDuplicatesAndPreservesRouteId() = runBlocking {
        every { mockDb.beginTransaction() } just Runs
        every { mockDb.setTransactionSuccessful() } just Runs
        every { mockDb.endTransaction() } just Runs
        every { mockDb.update(ClimbsDbHelper.TABLE_CLIMBS, any(), any(), any()) } returns 1
        every { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, any(), any()) } returns 1

        val mockCursor = mockk<Cursor>(relaxed = true)
        var cursorIdx = -1
        every { mockCursor.moveToNext() } answers {
            cursorIdx++
            cursorIdx < 2
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

        every { mockCursor.getLong(0) } answers { if (cursorIdx == 0) 100L else 101L }
        every { mockCursor.getString(1) } returns "Duplicate Hill"
        every { mockCursor.isNull(2) } answers { cursorIdx == 0 } // first has null routeId, second has routeId 7L
        every { mockCursor.getLong(2) } answers { if (cursorIdx == 1) 7L else 0L }
        every { mockCursor.getDouble(3) } answers { if (cursorIdx == 0) 48.0 else 48.0004 }
        every { mockCursor.getDouble(4) } returns 11.0
        every { mockCursor.getDouble(5) } answers { if (cursorIdx == 0) 48.01 else 48.0103 }
        every { mockCursor.getDouble(6) } returns 11.02
        every { mockCursor.getDouble(7) } returns 1500.0
        every { mockCursor.getDouble(8) } returns 90.0
        every { mockCursor.getDouble(9) } returns 6.0
        every { mockCursor.getDouble(10) } returns 9.0
        every { mockCursor.getString(11) } returns "CAT_4"
        every { mockCursor.getString(12) } returns ""

        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns mockCursor

        val purged = manager.deduplicateExistingClimbs(thresholdMeters = 100.0f)
        assertEquals(1, purged)
        verify { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("101")) }
        verify { mockDb.update(ClimbsDbHelper.TABLE_CLIMBS, any(), "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("100")) }
    }

    @Test
    fun deserializePathPoints_handlesEdgeCasesAndLargeTrackParity() {
        // TST-STB-021.1: Zero-split parser preserves exact values across edge cases and 2,000+ points
        val edgeSerialized = " 48.123456 , 11.654321 , null , 0.0 ;; -45.5,-73.25,1.5e2,1250.75; bad,row; 48.0,nan,10.0,20.0; 48.9999999999999999,11.1111111111111111,500.5,3456.789;"
        val parsed = ClimbsDatabaseManager.deserializePathPoints(edgeSerialized)
        assertEquals(3, parsed.size)

        assertEquals(48.123456, parsed[0].latLng.latitude, 1e-12)
        assertEquals(11.654321, parsed[0].latLng.longitude, 1e-12)
        assertNull(parsed[0].altitude)
        assertEquals(0.0, parsed[0].distance, 1e-9)

        assertEquals(-45.5, parsed[1].latLng.latitude, 1e-12)
        assertEquals(-73.25, parsed[1].latLng.longitude, 1e-12)
        assertEquals(150.0, parsed[1].altitude!!, 1e-9)
        assertEquals(1250.75, parsed[1].distance, 1e-9)

        assertEquals("48.9999999999999999".toDouble(), parsed[2].latLng.latitude, 1e-12)
        assertEquals("11.1111111111111111".toDouble(), parsed[2].latLng.longitude, 1e-12)
        assertEquals(500.5, parsed[2].altitude!!, 1e-9)
        assertEquals(3456.789, parsed[2].distance, 1e-9)

        assertTrue(ClimbsDatabaseManager.deserializePathPoints("").isEmpty())
        assertTrue(ClimbsDatabaseManager.deserializePathPoints("   ;;; ").isEmpty())

        // 2,500-point round-trip parity
        val largeTrack = (0 until 2500).map { i ->
            PathPoint(
                distance = i * 5.25,
                latLng = LatLng(47.123456 + i * 0.00003, 10.987654 - i * 0.00002),
                altitude = if (i % 7 == 0) null else 850.0 + i * 0.35
            )
        }
        val serializedLarge = ClimbsDatabaseManager.serializePathPoints(largeTrack)
        val roundTripped = ClimbsDatabaseManager.deserializePathPoints(serializedLarge)
        assertEquals(largeTrack.size, roundTripped.size)
        for (i in largeTrack.indices) {
            assertEquals(largeTrack[i].latLng.latitude, roundTripped[i].latLng.latitude, 1e-12)
            assertEquals(largeTrack[i].latLng.longitude, roundTripped[i].latLng.longitude, 1e-12)
            assertEquals(largeTrack[i].distance, roundTripped[i].distance, 1e-9)
            if (largeTrack[i].altitude == null) {
                assertNull(roundTripped[i].altitude)
            } else {
                assertEquals(largeTrack[i].altitude!!, roundTripped[i].altitude!!, 1e-9)
            }
        }
    }

    @Test
    fun insertClimbsWithDeduplicationBatch_skipsPolylineQueriesForDistantAndEndpointMatches() = runBlocking {
        // TST-STB-021.1: Projected METADATA_COLUMNS handles distant climbs and endpoint duplicates
        // with ZERO POLYLINE_ONLY_COLUMN queries.
        every { mockDb.beginTransaction() } just Runs
        every { mockDb.setTransactionSuccessful() } just Runs
        every { mockDb.endTransaction() } just Runs

        val existingRows = listOf(
            // Row 0: Distant climb in Munich
            arrayOf<Any?>(10L, null, 48.0, 11.0, 48.01, 11.01, 1200.0, 80.0),
            // Row 1: Endpoint-match climb in Innsbruck (will match candidate 1 without reading polyline)
            arrayOf<Any?>(20L, 5L, 47.26, 11.39, 47.27, 11.40, 1400.0, 95.0)
        )
        val metaCursor = createMetadataCursor(existingRows)
        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns metaCursor
        every { mockDb.insert(any(), any(), any()) } returns 30L

        val candidates = listOf(
            // Matches Row 1 by endpoint proximity -> no insert, no polyline read
            Climb(
                name = "Innsbruck Dup",
                startLat = 47.2601,
                startLng = 11.3901,
                endLat = 47.2701,
                endLng = 11.4001,
                distanceMeters = 1410.0,
                elevationGainMeters = 96.0,
                avgGradePercent = 6.8,
                maxGradePercent = 10.0,
                category = ClimbCategory.CAT_4
            ),
            // Distant climb in Zurich -> inserted without any polyline read
            Climb(
                name = "Zurich Climb",
                startLat = 47.37,
                startLng = 8.54,
                endLat = 47.38,
                endLng = 8.55,
                distanceMeters = 1300.0,
                elevationGainMeters = 85.0,
                avgGradePercent = 6.5,
                maxGradePercent = 9.0,
                category = ClimbCategory.CAT_4
            )
        )

        val ids = manager.insertClimbsWithDeduplicationBatch(candidates, thresholdMeters = 50.0f)
        assertEquals(listOf(20L, 30L), ids)

        verify(exactly = 1) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any())
        }
        verify(exactly = 0) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, null, any(), any(), any(), any(), any())
        }
    }

    @Test
    fun deduplicateExistingClimbs_lazilyHydratesBothPolylinesOnCurvedCorridorOverlap() = runBlocking {
        // TST-STB-021.1: Two switchback/curved climbs whose raw endpoint boxes are >300m apart laterally
        // must pass conservativeBoundingBox ellipse slack, trigger NEEDS_CORRIDOR_CHECK, lazily hydrate
        // POLYLINE_ONLY_COLUMN for BOTH climbs (cached once per ID), and purge the duplicate.
        every { mockDb.beginTransaction() } just Runs
        every { mockDb.setTransactionSuccessful() } just Runs
        every { mockDb.endTransaction() } just Runs
        every { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, any(), any()) } returns 1

        // Climb 1: starts (48.000, 11.000), bows east through (48.005, 11.020), ends (48.010, 11.000)
        // Climb 2: starts (48.001, 11.005) (>350m east of Climb 1 start), follows same eastern bow, ends (48.009, 11.005)
        val polyline1Points = listOf(
            PathPoint(0.0, LatLng(48.000, 11.000), 500.0),
            PathPoint(800.0, LatLng(48.003, 11.012), 540.0),
            PathPoint(1600.0, LatLng(48.005, 11.020), 580.0),
            PathPoint(2400.0, LatLng(48.007, 11.012), 620.0),
            PathPoint(3200.0, LatLng(48.010, 11.000), 660.0)
        )
        val polyline2Points = listOf(
            PathPoint(0.0, LatLng(48.001, 11.005), 510.0),
            PathPoint(600.0, LatLng(48.003, 11.012), 540.0),
            PathPoint(1400.0, LatLng(48.005, 11.020), 580.0),
            PathPoint(2200.0, LatLng(48.007, 11.012), 620.0),
            PathPoint(2800.0, LatLng(48.009, 11.005), 650.0)
        )
        val poly1Str = ClimbsDatabaseManager.serializePathPoints(polyline1Points)
        val poly2Str = ClimbsDatabaseManager.serializePathPoints(polyline2Points)

        // Row 2 (id=202) is an unrelated distant climb that must NOT load a polyline
        val rows = listOf(
            arrayOf<Any?>(201L, null, 48.000, 11.000, 48.010, 11.000, 3200.0, 160.0),
            arrayOf<Any?>(202L, null, 49.500, 12.500, 49.510, 12.510, 1400.0, 90.0),
            arrayOf<Any?>(203L, null, 48.001, 11.005, 48.009, 11.005, 2800.0, 140.0)
        )
        val metaCursor = createMetadataCursor(rows)
        every { mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.METADATA_COLUMNS, null, null, null, null, any()) } returns metaCursor
        every {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("201"), null, null, null)
        } answers { createPolylineCursor(poly1Str) }
        every {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("203"), null, null, null)
        } answers { createPolylineCursor(poly2Str) }

        val purged = manager.deduplicateExistingClimbs(thresholdMeters = 100.0f)
        assertEquals(1, purged)
        verify(exactly = 1) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("201"), null, null, null)
        }
        verify(exactly = 1) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("203"), null, null, null)
        }
        verify(exactly = 0) {
            mockDb.query(ClimbsDbHelper.TABLE_CLIMBS, ClimbsDatabaseManager.POLYLINE_ONLY_COLUMN, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("202"), null, null, null)
        }
        verify(exactly = 1) { mockDb.delete(ClimbsDbHelper.TABLE_CLIMBS, "${ClimbsDbHelper.COLUMN_ID} = ?", arrayOf("203")) }
    }

    private fun createMetadataCursor(rows: List<Array<Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var idx = -1
        every { cursor.moveToNext() } answers {
            idx++
            idx < rows.size
        }
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ROUTE_ID) } returns 1
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LAT) } returns 2
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_START_LNG) } returns 3
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LAT) } returns 4
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_END_LNG) } returns 5
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_DISTANCE_M) } returns 6
        every { cursor.getColumnIndexOrThrow(ClimbsDbHelper.COLUMN_ELEVATION_GAIN_M) } returns 7

        every { cursor.getLong(0) } answers { rows[idx][0] as Long }
        every { cursor.isNull(1) } answers { rows[idx][1] == null }
        every { cursor.getLong(1) } answers { (rows[idx][1] as? Long) ?: 0L }
        every { cursor.getDouble(2) } answers { rows[idx][2] as Double }
        every { cursor.getDouble(3) } answers { rows[idx][3] as Double }
        every { cursor.getDouble(4) } answers { rows[idx][4] as Double }
        every { cursor.getDouble(5) } answers { rows[idx][5] as Double }
        every { cursor.getDouble(6) } answers { rows[idx][6] as Double }
        every { cursor.getDouble(7) } answers { rows[idx][7] as Double }
        return cursor
    }

    private fun createPolylineCursor(polyline: String): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var read = false
        every { cursor.moveToNext() } answers {
            if (!read) {
                read = true
                true
            } else false
        }
        every { cursor.isNull(0) } returns false
        every { cursor.getString(0) } returns polyline
        return cursor
    }
}
