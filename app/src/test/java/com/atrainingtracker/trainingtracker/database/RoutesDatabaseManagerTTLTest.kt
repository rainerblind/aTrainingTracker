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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RouteContract
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying 7-day TTL cache retention, orphan pruning, and route duplication
 * in [RoutesDatabaseManager] (REQ-EXT-010, TST-EXT-007, ATT-1177).
 */
class RoutesDatabaseManagerTTLTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: RoutesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().putNull(any<String>()) } returns Unit

        mockkConstructor(RoutesDbHelper::class)
        every { anyConstructed<RoutesDbHelper>().writableDatabase } returns mockDb

        RoutesDatabaseManager.resetForTesting(null)
        manager = RoutesDatabaseManager.getInstance(mockContext)
        val dbField = RoutesDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, mockDb)
    }

    @After
    fun tearDown() {
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testSchemaV9_dbVersionIsNine_andOnUpgradeExecutesAlterTableAndBackfill() {
        assertEquals(9, RoutesDbHelper.DB_VERSION)

        val helper = RoutesDbHelper(mockContext)
        helper.onUpgrade(mockDb, 7, 9)

        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("synced_at") })
            mockDb.execSQL(match { it.contains("UPDATE") && it.contains("synced_at") })
        }
    }

    @Test
    fun testPruneExpiredStravaRoutes_deletesExpiredRecords() {
        val cursor = mockk<Cursor>(relaxed = true)
        // Two expired Strava routes with IDs 101 and 102
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0
        every { cursor.getLong(0) } returnsMany listOf(101L, 102L)

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID),
                match { it.contains(RouteContract.COLUMN_SOURCE) && it.contains(RouteContract.COLUMN_SYNCED_AT) },
                match { it.contains(RouteSource.STRAVA.name) },
                null, null, null
            )
        } returns cursor

        val deletedCount = manager.pruneExpiredStravaRoutes(7 * 24 * 60 * 60 * 1000L)

        assertEquals(2, deletedCount)
        verify {
            mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("101"))
            mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("101"))
            mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("102"))
            mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("102"))
            mockDb.setTransactionSuccessful()
        }
    }

    @Test
    fun testPruneExpiredStravaRoutes_whenNoExpiredRoutes_returnsZero() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID),
                any(), any(), null, null, null
            )
        } returns cursor

        val deletedCount = manager.pruneExpiredStravaRoutes(7 * 24 * 60 * 60 * 1000L)

        assertEquals(0, deletedCount)
        verify(exactly = 0) {
            mockDb.delete(any(), any(), any())
        }
    }

    @Test
    fun testPruneOrphanStravaRoutes_deletesRoutesNotPresentInRemoteSet() {
        val cursor = mockk<Cursor>(relaxed = true)
        // 3 local Strava routes: "10" (active), "20" (unstarred/orphan), "30" (active)
        every { cursor.moveToNext() } returnsMany listOf(true, true, true, false)
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_EXTERNAL_ID) } returns 1

        every { cursor.getLong(0) } returnsMany listOf(1L, 2L, 3L)
        every { cursor.getString(1) } returnsMany listOf("10", "20", "30")

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID, RouteContract.COLUMN_EXTERNAL_ID),
                "${RouteContract.COLUMN_SOURCE} = ?",
                arrayOf(RouteSource.STRAVA.name),
                null, null, null
            )
        } returns cursor

        val activeIds = setOf("10", "30")
        val deletedCount = manager.pruneOrphanStravaRoutes(activeIds)

        assertEquals(1, deletedCount)
        // Route with ID 2L (extId "20") must be deleted
        verify {
            mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("2"))
            mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("2"))
            mockDb.setTransactionSuccessful()
        }
        // Routes 1 and 3 must not be deleted
        verify(exactly = 0) {
            mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("1"))
            mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("3"))
        }
    }

    @Test
    fun testDuplicateRouteAsLocal_createsLocalGpxRouteDecoupledFromStrava() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_EXTERNAL_ID) } returns 1
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_NAME) } returns 2
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DESCRIPTION) } returns 3
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_IS_SELECTED) } returns 4
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_DISTANCE) } returns 5
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_ELEVATION_GAIN) } returns 6
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SPORT_TYPE) } returns 7
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_SOURCE) } returns 8
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_CLUSTER_ID) } returns 9
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MIN_LAT) } returns 10
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MIN_LNG) } returns 11
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MAX_LAT) } returns 12
        every { cursor.getColumnIndexOrThrow(RouteContract.COLUMN_BOUND_MAX_LNG) } returns 13
        every { cursor.getColumnIndex(RouteContract.COLUMN_SYNCED_AT) } returns 14

        every { cursor.getLong(0) } returns 50L
        every { cursor.getString(1) } returns "strava_999"
        every { cursor.getString(2) } returns "Alpe d'Huez"
        every { cursor.getString(3) } returns "Climb"
        every { cursor.getInt(4) } returns 0
        every { cursor.getDouble(5) } returns 14000.0
        every { cursor.getDouble(6) } returns 1100.0
        every { cursor.getString(7) } returns "BIKE"
        every { cursor.getString(8) } returns "STRAVA"
        every { cursor.getLong(9) } returns -1L
        every { cursor.isNull(any()) } returns false
        every { cursor.getDouble(10) } returns 45.0
        every { cursor.getDouble(11) } returns 6.0
        every { cursor.getDouble(12) } returns 45.1
        every { cursor.getDouble(13) } returns 6.1
        every { cursor.getLong(14) } returns 123456789L

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                null,
                "${RouteContract.COLUMN_ID} = ?",
                arrayOf("50"),
                null, null, null
            )
        } returns cursor

        // Points query for getRoutePath
        val pointsCursor = mockk<Cursor>(relaxed = true)
        every { pointsCursor.moveToNext() } returnsMany listOf(true, false)
        every { pointsCursor.getColumnIndexOrThrow(RouteContract.COLUMN_LAT) } returns 0
        every { pointsCursor.getColumnIndexOrThrow(RouteContract.COLUMN_LNG) } returns 1
        every { pointsCursor.getColumnIndexOrThrow(RouteContract.COLUMN_DIST_FROM_START) } returns 2
        every { pointsCursor.getColumnIndexOrThrow(RouteContract.COLUMN_ALTITUDE) } returns 3
        every { pointsCursor.getDouble(0) } returns 45.0
        every { pointsCursor.getDouble(1) } returns 6.0
        every { pointsCursor.getDouble(2) } returns 0.0
        every { pointsCursor.getDouble(3) } returns 700.0

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTE_POINTS,
                any(),
                "${RouteContract.COLUMN_ROUTE_ID_FK} = ?",
                arrayOf("50"),
                null, null, any()
            )
        } returns pointsCursor

        // Mock insertion of new duplicate route
        every { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) } returns 105L
        every { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) } returns 1L

        val duplicatedId = manager.duplicateRouteAsLocal(50L)

        assertEquals(105L, duplicatedId)
        verify {
            anyConstructed<ContentValues>().put(RouteContract.COLUMN_SOURCE, RouteSource.LOCAL_GPX.name)
            anyConstructed<ContentValues>().put(RouteContract.COLUMN_EXTERNAL_ID, "")
            anyConstructed<ContentValues>().put(RouteContract.COLUMN_NAME, "Alpe d'Huez (Local)")
            anyConstructed<ContentValues>().put(RouteContract.COLUMN_SYNCED_AT, any<Long>())
        }
    }
}
