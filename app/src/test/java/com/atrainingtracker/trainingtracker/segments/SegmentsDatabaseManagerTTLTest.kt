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
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaMap
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegment
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.SegmentsDbHelper
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying 7-day TTL cache retention, cascade stream pruning, and orphan pruning
 * in [SegmentsDatabaseManager] (REQ-EXT-010, TST-EXT-007, ATT-1177).
 */
class SegmentsDatabaseManagerTTLTest {

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

        Class.forName(SegmentsDbHelper::class.java.name)
        Class.forName(SportTypeDatabaseManager::class.java.name)

        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockSportTypeMgr = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeMgr

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().putNull(any<String>()) } returns Unit

        mockkConstructor(SegmentsDbHelper::class)
        every { anyConstructed<SegmentsDbHelper>().writableDatabase } returns mockDb

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
    fun testSchemaV9_dbVersionIsNine_andOnUpgradeExecutesAlterTableAndBackfill() {
        assertEquals(9, SegmentsDbHelper.DB_VERSION)

        val helper = SegmentsDbHelper(mockContext)
        helper.onUpgrade(mockDb, 7, 9)

        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("synced_at") })
            mockDb.execSQL(match { it.contains("UPDATE") && it.contains("synced_at") })
        }
    }

    @Test
    fun testPruneExpiredSegments_deletesExpiredSegmentsAndCascadeStreams() {
        val cursor = mockk<Cursor>(relaxed = true)
        // Two expired segments with Strava IDs 501L and 502L
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        every { cursor.getColumnIndexOrThrow(Segments.STRAVA_SEGMENT_ID) } returns 0
        every { cursor.getLong(0) } returnsMany listOf(501L, 502L)

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.STRAVA_SEGMENT_ID),
                match { it.contains(Segments.SYNCED_AT) },
                any(), null, null, null
            )
        } returns cursor

        val deletedCount = manager.pruneExpiredSegments(7 * 24 * 60 * 60 * 1000L)

        assertEquals(2, deletedCount)
        // Both segments must be deleted from TABLE_STARRED_SEGMENTS and TABLE_SEGMENT_STREAMS
        verify {
            mockDb.delete(Segments.TABLE_STARRED_SEGMENTS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("501"))
            mockDb.delete(Segments.TABLE_SEGMENT_STREAMS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("501"))
            mockDb.delete(Segments.TABLE_STARRED_SEGMENTS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("502"))
            mockDb.delete(Segments.TABLE_SEGMENT_STREAMS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("502"))
        }
    }

    @Test
    fun testPruneOrphanSegments_deletesSegmentsNotPresentInRemoteSet() {
        val cursor = mockk<Cursor>(relaxed = true)
        // Three local segments: 1001L (active), 1002L (orphan/unstarred), 1003L (active)
        every { cursor.moveToNext() } returnsMany listOf(true, true, true, false)
        every { cursor.getColumnIndexOrThrow(Segments.STRAVA_SEGMENT_ID) } returns 0
        every { cursor.getLong(0) } returnsMany listOf(1001L, 1002L, 1003L)

        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                arrayOf(Segments.STRAVA_SEGMENT_ID),
                null, null, null, null, null
            )
        } returns cursor

        val activeIds = setOf(1001L, 1003L)
        val deletedCount = manager.pruneOrphanSegments(activeIds)

        assertEquals(1, deletedCount)
        // Segment 1002L must be cascade-deleted
        verify {
            mockDb.delete(Segments.TABLE_STARRED_SEGMENTS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("1002"))
            mockDb.delete(Segments.TABLE_SEGMENT_STREAMS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("1002"))
        }
        // Active segments 1001L and 1003L must not be deleted
        verify(exactly = 0) {
            mockDb.delete(Segments.TABLE_STARRED_SEGMENTS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("1001"))
            mockDb.delete(Segments.TABLE_STARRED_SEGMENTS, "${Segments.STRAVA_SEGMENT_ID}=?", arrayOf("1003"))
        }
    }

    @Test
    fun testAddOrUpdateSegment_persistsSyncedAtTimestamp() {
        val queryCursor = mockk<Cursor>(relaxed = true)
        every { queryCursor.moveToNext() } returns false
        every {
            mockDb.query(
                Segments.TABLE_STARRED_SEGMENTS,
                any(), any(), any(), any(), any(), any()
            )
        } returns queryCursor

        every { mockDb.insert(Segments.TABLE_STARRED_SEGMENTS, null, any()) } returns 1L

        val segment = StravaSegment(
            id = 777L,
            name = "Test Climb",
            activityType = "Ride",
            distance = 2500.0,
            averageGrade = 5.0,
            maximumGrade = 8.0,
            elevationHigh = 400.0,
            elevationLow = 275.0,
            totalElevationGain = 125.0,
            climbCategory = 1,
            city = "Freiburg",
            state = "BW",
            country = "Germany",
            prTime = 360,
            map = StravaMap(id = "map_777", polyline = null, summaryPolyline = null),
            startLatLng = listOf(48.0, 7.8),
            endLatLng = listOf(48.02, 7.82)
        )

        manager.addOrUpdateSegment(segment)

        verify {
            anyConstructed<ContentValues>().put(Segments.SYNCED_AT, any<Long>())
        }
    }
}
