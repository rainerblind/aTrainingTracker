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
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.routes.WaypointType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying database persistence, queries, cascade deletion, and schema migration
 * for route waypoints in [RoutesDatabaseManager] (REQ-MAP-026, TST-MAP-028 Group 4).
 */
class RoutesDatabaseManagerWaypointTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: RoutesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

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
    fun testInsertRoute_withWaypoints_persistsWaypointsToDatabase() {
        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.moveToNext() } returns false

        every {
            mockDb.query(
                RouteContract.TABLE_ROUTES,
                any(), any(), any(), any(), any(), any()
            )
        } returns emptyCursor

        every { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) } returns 42L
        every { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) } returns 101L
        every { mockDb.insert(RouteContract.TABLE_ROUTE_WAYPOINTS, null, any()) } returns 201L

        val summary = RouteSummary(
            id = 0L,
            externalId = "wpt_route_1",
            name = "Waypoint Route",
            description = "Test route with POIs",
            isSelected = false,
            distance = 1500.0,
            elevationGain = 50.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
            PathPoint(distance = 1500.0, latLng = LatLng(48.01, 11.01), altitude = 550.0)
        )
        val waypoints = listOf(
            RouteWaypoint(
                latLng = LatLng(48.005, 11.005),
                name = "Spring",
                type = WaypointType.POI_WATER,
                distanceFromStart = 750.0
            ),
            RouteWaypoint(
                latLng = LatLng(48.01, 11.01),
                name = "Summit",
                type = WaypointType.POI_SUMMIT,
                distanceFromStart = 1500.0
            )
        )

        val insertedId = manager.insertRoute(summary, path, waypoints)

        assertEquals(42L, insertedId)
        verify(exactly = 1) { mockDb.insert(RouteContract.TABLE_ROUTES, null, any()) }
        verify(exactly = 2) { mockDb.insert(RouteContract.TABLE_ROUTE_POINTS, null, any()) }
        verify(exactly = 2) { mockDb.insert(RouteContract.TABLE_ROUTE_WAYPOINTS, null, any()) }
    }

    @Test
    fun testDeleteRoute_removesRouteAndWaypoints() {
        every { mockDb.delete(any(), any(), any()) } returns 1

        val result = manager.deleteRoute(42L)

        assertEquals(1, result)
        verify {
            mockDb.delete(
                RouteContract.TABLE_ROUTE_WAYPOINTS,
                "${RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK} = ?",
                arrayOf("42")
            )
        }
        verify {
            mockDb.delete(
                RouteContract.TABLE_ROUTES,
                "${RouteContract.COLUMN_ID} = ?",
                arrayOf("42")
            )
        }
    }

    @Test
    fun testOnUpgrade_fromV9ToV10_createsRouteWaypointsTableAndIndex() {
        val helper = RoutesDbHelper(mockContext)
        val db = mockk<SQLiteDatabase>(relaxed = true)

        helper.onUpgrade(db, 9, 10)

        verify { db.execSQL(RouteContract.CREATE_TABLE_ROUTE_WAYPOINTS) }
        verify { db.execSQL(RouteContract.CREATE_INDEX_ROUTE_WAYPOINTS) }
    }
}
