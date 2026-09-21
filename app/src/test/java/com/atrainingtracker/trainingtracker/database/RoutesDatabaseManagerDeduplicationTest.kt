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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RouteContract
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RoutesDbHelper
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying idempotency and deduplication in [RoutesDatabaseManager.insertRoute] (REQ-EXT-009, ATT-1078).
 */
class RoutesDatabaseManagerDeduplicationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: RoutesDatabaseManager

    @Before
    fun setUp() {
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
    }

    @After
    fun tearDown() {
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testInsertRoute_whenNewRoute_insertsRowAndPoints() {
        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToNext() } returns false

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID),
                any(),
                any(),
                null,
                null,
                any()
            )
        } returns emptyCursor

        every { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) } returns 101L
        every { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) } returns 1L

        val summary = RouteSummary(
            id = 0,
            externalId = "strava_12345",
            name = "Morning Ride",
            description = "Fun route",
            isSelected = false,
            distance = 15000.0,
            elevationGain = 200.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA
        )
        val path = listOf(
            PathPoint(0.0, LatLng(48.0, 9.0), 500.0),
            PathPoint(100.0, LatLng(48.001, 9.001), 505.0)
        )

        val insertedId = manager.insertRoute(summary, path)

        assertEquals(101L, insertedId)
        verify(exactly = 1) { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) }
        verify(exactly = 2) { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) }
        verify(exactly = 0) { mockDb.update(RouteContract.TABLE_ROUTES, any(), any(), any()) }
    }

    @Test
    fun testInsertRoute_whenDuplicateExternalId_updatesSummaryAndReplacesPoints() {
        val existingCursor = mockk<Cursor>(relaxed = true)
        every { existingCursor.moveToNext() } returnsMany listOf(true, false)
        every { existingCursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0
        every { existingCursor.getLong(0) } returns 42L

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID),
                "${RouteContract.COLUMN_EXTERNAL_ID} = ? AND ${RouteContract.COLUMN_SOURCE} = ?",
                arrayOf("strava_12345", RouteSource.STRAVA.name),
                null,
                null,
                "${RouteContract.COLUMN_ID} ASC"
            )
        } returns existingCursor

        every { mockDb.update(RouteContract.TABLE_ROUTES, any(), "${RouteContract.COLUMN_ID} = ?", arrayOf("42")) } returns 1
        every { mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("42")) } returns 2
        every { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) } returns 1L

        val summary = RouteSummary(
            id = 0,
            externalId = "strava_12345",
            name = "Morning Ride Updated",
            description = "Fun route updated",
            isSelected = false,
            distance = 15500.0,
            elevationGain = 210.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA
        )
        val path = listOf(
            PathPoint(0.0, LatLng(48.0, 9.0), 500.0),
            PathPoint(100.0, LatLng(48.001, 9.001), 505.0)
        )

        val returnedId = manager.insertRoute(summary, path)

        assertEquals(42L, returnedId)
        verify(exactly = 1) { mockDb.update(RouteContract.TABLE_ROUTES, any(), "${RouteContract.COLUMN_ID} = ?", arrayOf("42")) }
        verify(exactly = 1) { mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("42")) }
        verify(exactly = 0) { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) }
        verify(exactly = 2) { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) }
    }

    @Test
    fun testInsertRoute_whenMultipleLegacyDuplicatesExist_purgesLegacyDuplicates() {
        val duplicatesCursor = mockk<Cursor>(relaxed = true)
        every { duplicatesCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { duplicatesCursor.getColumnIndexOrThrow(RouteContract.COLUMN_ID) } returns 0
        every { duplicatesCursor.getLong(0) } returnsMany listOf(42L, 43L)

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                arrayOf(RouteContract.COLUMN_ID),
                any(),
                any(),
                null,
                null,
                any()
            )
        } returns duplicatesCursor

        every { mockDb.update(RouteContract.TABLE_ROUTES, any(), "${RouteContract.COLUMN_ID} = ?", arrayOf("42")) } returns 1
        every { mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("42")) } returns 2
        every { mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("43")) } returns 2
        every { mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("43")) } returns 1

        val summary = RouteSummary(
            id = 0,
            externalId = "strava_dup",
            name = "Duplicate Route",
            description = "",
            isSelected = false,
            distance = 1000.0,
            elevationGain = 10.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA
        )
        val path = listOf(PathPoint(0.0, LatLng(48.0, 9.0), 500.0))

        val returnedId = manager.insertRoute(summary, path)

        assertEquals(42L, returnedId)
        // Secondary duplicate 43 must be deleted from both tables
        verify(exactly = 1) { mockDb.delete(RouteContract.TABLE_ROUTES, "${RouteContract.COLUMN_ID} = ?", arrayOf("43")) }
        verify(exactly = 1) { mockDb.delete(RouteContract.TABLE_ROUTE_POINTS, "${RouteContract.COLUMN_ROUTE_ID_FK} = ?", arrayOf("43")) }
    }
}
