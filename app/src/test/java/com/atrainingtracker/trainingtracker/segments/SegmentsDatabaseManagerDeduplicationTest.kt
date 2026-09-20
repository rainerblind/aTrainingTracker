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

package com.atrainingtracker.trainingtracker.segments

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaMap
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegment
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying deduplication and legacy duplicate pruning in [SegmentsDatabaseManager] (REQ-EXT-009, ATT-1078).
 */
class SegmentsDatabaseManagerDeduplicationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: SegmentsDatabaseManager
    private lateinit var mockSportTypeMgr: SportTypeDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        Class.forName(SegmentsDatabaseManager.SegmentsDbHelper::class.java.name)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns mockContext
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { TrainingApplication.getDebug(any()) } returns false

        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockSportTypeMgr = mockk(relaxed = true)
        every { mockSportTypeMgr.getBSportTypeFromStravaName(any()) } returns BSportType.BIKE
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeMgr

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Boolean>()) } returns Unit
        every { anyConstructed<ContentValues>().putNull(any<String>()) } returns Unit

        mockkConstructor(SegmentsDatabaseManager.SegmentsDbHelper::class)
        every { anyConstructed<SegmentsDatabaseManager.SegmentsDbHelper>().writableDatabase } returns mockDb

        SegmentsDatabaseManager.resetForTesting(null)
        manager = SegmentsDatabaseManager.getInstance(mockContext)
        val dbField = SegmentsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, mockDb)
    }

    @After
    fun tearDown() {
        SegmentsDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testGetAllSegmentSummaries_deduplicatesDuplicateRows() {
        val cursor = mockk<Cursor>(relaxed = true)
        // Simulate two cursor rows with identical strava_segment_id = 999L
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        every { cursor.getColumnIndexOrThrow(Segments.STRAVA_SEGMENT_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(Segments.ACTIVITY_TYPE) } returns 1
        every { cursor.getColumnIndexOrThrow(Segments.DISTANCE) } returns 2
        every { cursor.getColumnIndexOrThrow(Segments.AVERAGE_GRADE) } returns 3
        every { cursor.getColumnIndexOrThrow(Segments.MAXIMUM_GRADE) } returns 4
        every { cursor.getColumnIndexOrThrow(Segments.TOTAL_ELEVATION_GAIN) } returns 5
        every { cursor.getColumnIndexOrThrow(Segments.ELEVATION_LOW) } returns 6
        every { cursor.getColumnIndexOrThrow(Segments.ELEVATION_HIGH) } returns 7
        every { cursor.getColumnIndexOrThrow(Segments.PR_TIME) } returns 8
        every { cursor.getColumnIndexOrThrow(Segments.CLIMB_CATEGORY) } returns 9
        every { cursor.getColumnIndexOrThrow(Segments.CITY) } returns 10
        every { cursor.getColumnIndexOrThrow(Segments.SEGMENT_NAME) } returns 11
        every { cursor.getColumnIndexOrThrow(Segments.MAP_POLYLINE) } returns 12
        every { cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LAT) } returns 13
        every { cursor.getColumnIndexOrThrow(Segments.BOUND_MIN_LNG) } returns 14
        every { cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LAT) } returns 15
        every { cursor.getColumnIndexOrThrow(Segments.BOUND_MAX_LNG) } returns 16

        every { cursor.getLong(0) } returns 999L
        every { cursor.getString(1) } returns "Ride"
        every { cursor.getString(11) } returns "Test Segment"
        every { cursor.getString(12) } returns ""

        every { mockDb.query(Segments.TABLE_STARRED_SEGMENTS, null, null, null, null, null, null) } returns cursor

        val summaries = manager.allSegmentSummaries

        // Must only return 1 summary instead of 2
        assertEquals(1, summaries.size)
        assertEquals(999L, summaries[0].stravaId)
    }

    @Test
    fun testAddOrUpdateSegment_whenLegacyDuplicatesExist_prunesSecondaryRows() {
        val cursor = mockk<Cursor>(relaxed = true)
        // Two existing rows: rowId 10 and rowId 11 for the same Strava ID 500
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        every { cursor.getLong(0) } returnsMany listOf(10L, 11L)

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.C_ID),
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("500"),
                null, null,
                "${Segments.C_ID} ASC"
            )
        } returns cursor

        val segment = StravaSegment(
            id = 500L,
            name = "Hill Climb",
            activityType = "Ride",
            distance = 1200.0,
            averageGrade = 7.5,
            maximumGrade = 12.0,
            elevationHigh = 450.0,
            elevationLow = 300.0,
            totalElevationGain = 150.0,
            startLatLng = listOf(48.0, 9.0),
            endLatLng = listOf(48.01, 9.01),
            climbCategory = 2,
            city = "Stuttgart",
            state = "BW",
            country = "Germany",
            map = StravaMap("id", null, null),
            prTime = 240
        )

        manager.addOrUpdateSegment(segment)

        // Primary row 10 updated
        verify(exactly = 1) {
            mockDb.update(
                Segments.TABLE_STARRED_SEGMENTS,
                any(),
                "${Segments.C_ID}=?",
                arrayOf("10")
            )
        }
        // Secondary row 11 deleted
        verify(exactly = 1) {
            mockDb.delete(
                Segments.TABLE_STARRED_SEGMENTS,
                "${Segments.C_ID}=?",
                arrayOf("11")
            )
        }
        // No new insert
        verify(exactly = 0) { mockDb.insert(Segments.TABLE_STARRED_SEGMENTS, null, any()) }
    }

    @Test
    fun testInsertSegmentStreams_clearsExistingStreamsBeforeInserting() {
        val rows = listOf(ContentValues(), ContentValues())

        manager.insertSegmentStreams(777L, rows, false)

        verify(exactly = 1) {
            mockDb.delete(
                Segments.TABLE_SEGMENT_STREAMS,
                "${Segments.STRAVA_SEGMENT_ID}=?",
                arrayOf("777")
            )
        }
        verify(exactly = 2) {
            mockDb.insert(Segments.TABLE_SEGMENT_STREAMS, null, any())
        }
    }
}
