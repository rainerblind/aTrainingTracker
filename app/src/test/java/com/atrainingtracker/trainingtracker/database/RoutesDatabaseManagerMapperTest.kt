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
import android.database.Cursor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.RouteContract
import com.atrainingtracker.trainingtracker.routes.RouteWaypoint
import com.atrainingtracker.trainingtracker.routes.WaypointType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for cursor-to-entity mapping in [RoutesDatabaseManager]
 * enforcing Rule 20, Rule 36, REQ-DAT-025, and TST-DAT-020.
 */
class RoutesDatabaseManagerMapperTest {

    private lateinit var mockContext: Context
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
        RoutesDatabaseManager.resetForTesting(null)
        manager = RoutesDatabaseManager.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        RoutesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testMapCursorToRouteWaypoint_allFieldsExtractedCorrectly() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_WAYPOINT_ID to 0,
            RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK to 1,
            RouteContract.COLUMN_WAYPOINT_NAME to 2,
            RouteContract.COLUMN_WAYPOINT_DESCRIPTION to 3,
            RouteContract.COLUMN_WAYPOINT_LAT to 4,
            RouteContract.COLUMN_WAYPOINT_LNG to 5,
            RouteContract.COLUMN_WAYPOINT_ELEVATION to 6,
            RouteContract.COLUMN_WAYPOINT_DIST_FROM_START to 7,
            RouteContract.COLUMN_WAYPOINT_TYPE to 8
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.getLong(0) } returns 101L
        every { cursor.getLong(1) } returns 42L
        every { cursor.getString(2) } returns "Mountain Spring"
        every { cursor.getString(3) } returns "Fresh water station"
        every { cursor.getDouble(4) } returns 47.1234
        every { cursor.getDouble(5) } returns 11.5678
        every { cursor.isNull(6) } returns false
        every { cursor.getDouble(6) } returns 1250.0
        every { cursor.getDouble(7) } returns 4500.0
        every { cursor.getString(8) } returns "POI_WATER"

        val waypoint = manager.mapCursorToRouteWaypoint(cursor)

        assertEquals(101L, waypoint.id)
        assertEquals(42L, waypoint.routeId)
        assertEquals("Mountain Spring", waypoint.name)
        assertEquals("Fresh water station", waypoint.description)
        assertEquals(47.1234, waypoint.latLng.latitude, 0.0001)
        assertEquals(11.5678, waypoint.latLng.longitude, 0.0001)
        assertEquals(1250.0, waypoint.altitude, 0.0001)
        assertEquals(4500.0, waypoint.distanceFromStart, 0.0001)
        assertEquals(WaypointType.POI_WATER, waypoint.type)
    }

    @Test
    fun testMapCursorToRouteWaypoint_invalidType_fallsBackToGeneric() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_WAYPOINT_ID to 0,
            RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK to 1,
            RouteContract.COLUMN_WAYPOINT_NAME to 2,
            RouteContract.COLUMN_WAYPOINT_DESCRIPTION to 3,
            RouteContract.COLUMN_WAYPOINT_LAT to 4,
            RouteContract.COLUMN_WAYPOINT_LNG to 5,
            RouteContract.COLUMN_WAYPOINT_ELEVATION to 6,
            RouteContract.COLUMN_WAYPOINT_DIST_FROM_START to 7,
            RouteContract.COLUMN_WAYPOINT_TYPE to 8
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.getString(8) } returns "CORRUPTED_NON_EXISTENT_TYPE"

        val waypoint = manager.mapCursorToRouteWaypoint(cursor)
        assertEquals(WaypointType.GENERIC, waypoint.type)
    }

    @Test
    fun testMapCursorToRouteWaypoint_nullElevation_defaultsToZero() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_WAYPOINT_ID to 0,
            RouteContract.COLUMN_WAYPOINT_ROUTE_ID_FK to 1,
            RouteContract.COLUMN_WAYPOINT_NAME to 2,
            RouteContract.COLUMN_WAYPOINT_DESCRIPTION to 3,
            RouteContract.COLUMN_WAYPOINT_LAT to 4,
            RouteContract.COLUMN_WAYPOINT_LNG to 5,
            RouteContract.COLUMN_WAYPOINT_ELEVATION to 6,
            RouteContract.COLUMN_WAYPOINT_DIST_FROM_START to 7,
            RouteContract.COLUMN_WAYPOINT_TYPE to 8
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.isNull(6) } returns true

        val waypoint = manager.mapCursorToRouteWaypoint(cursor)
        assertEquals(0.0, waypoint.altitude, 0.0001)
    }

    @Test
    fun testMapCursorToPathPoint_allFieldsExtracted() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_LAT to 0,
            RouteContract.COLUMN_LNG to 1,
            RouteContract.COLUMN_DIST_FROM_START to 2,
            RouteContract.COLUMN_ALTITUDE to 3
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.getDouble(0) } returns 48.137154
        every { cursor.getDouble(1) } returns 11.576124
        every { cursor.getDouble(2) } returns 1200.5
        every { cursor.getDouble(3) } returns 520.0

        val pathPoint = manager.mapCursorToPathPoint(cursor)

        assertEquals(48.137154, pathPoint.latLng.latitude, 0.00001)
        assertEquals(11.576124, pathPoint.latLng.longitude, 0.00001)
        assertEquals(1200.5, pathPoint.distance, 0.001)
        assertEquals(520.0, pathPoint.altitude, 0.001)
    }

    @Test
    fun testMapCursorToRouteSummary_allFieldsExtracted() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_ID to 0,
            RouteContract.COLUMN_EXTERNAL_ID to 1,
            RouteContract.COLUMN_NAME to 2,
            RouteContract.COLUMN_DESCRIPTION to 3,
            RouteContract.COLUMN_IS_SELECTED to 4,
            RouteContract.COLUMN_DISTANCE to 5,
            RouteContract.COLUMN_ELEVATION_GAIN to 6,
            RouteContract.COLUMN_SPORT_TYPE to 7,
            RouteContract.COLUMN_SOURCE to 8,
            RouteContract.COLUMN_CLUSTER_ID to 9,
            RouteContract.COLUMN_BOUND_MIN_LAT to 10,
            RouteContract.COLUMN_BOUND_MIN_LNG to 11,
            RouteContract.COLUMN_BOUND_MAX_LAT to 12,
            RouteContract.COLUMN_BOUND_MAX_LNG to 13,
            RouteContract.COLUMN_SYNCED_AT to 14
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.getColumnIndex(RouteContract.COLUMN_SYNCED_AT) } returns 14
        every { cursor.getLong(0) } returns 77L
        every { cursor.getString(1) } returns "strava_12345"
        every { cursor.getString(2) } returns "Isar Trail Loop"
        every { cursor.getString(3) } returns "Gravel route along the river"
        every { cursor.getInt(4) } returns 1
        every { cursor.getDouble(5) } returns 25400.0
        every { cursor.getDouble(6) } returns 180.0
        every { cursor.getString(7) } returns "BIKE"
        every { cursor.getString(8) } returns "STRAVA"
        every { cursor.getLong(9) } returns 12L
        every { cursor.isNull(10) } returns false
        every { cursor.getDouble(10) } returns 48.0
        every { cursor.isNull(11) } returns false
        every { cursor.getDouble(11) } returns 11.5
        every { cursor.isNull(12) } returns false
        every { cursor.getDouble(12) } returns 48.2
        every { cursor.isNull(13) } returns false
        every { cursor.getDouble(13) } returns 11.7
        every { cursor.isNull(14) } returns false
        every { cursor.getLong(14) } returns 1700000000000L

        val summary = manager.mapCursorToRouteSummary(cursor)

        assertEquals(77L, summary.id)
        assertEquals("strava_12345", summary.externalId)
        assertEquals("Isar Trail Loop", summary.name)
        assertEquals("Gravel route along the river", summary.description)
        assertTrue(summary.isSelected)
        assertEquals(25400.0, summary.distance, 0.1)
        assertEquals(180.0, summary.elevationGain, 0.1)
        assertEquals(BSportType.BIKE, summary.bSportType)
        assertEquals(RouteSource.STRAVA, summary.source)
        assertEquals(12L, summary.clusterId)
        assertEquals(48.0, summary.minLat ?: 0.0, 0.0001)
        assertEquals(11.5, summary.minLng ?: 0.0, 0.0001)
        assertEquals(48.2, summary.maxLat ?: 0.0, 0.0001)
        assertEquals(11.7, summary.maxLng ?: 0.0, 0.0001)
    }

    @Test
    fun testRowMapper_doesNotAdvanceOrCloseCursor() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_LAT to 0,
            RouteContract.COLUMN_LNG to 1,
            RouteContract.COLUMN_DIST_FROM_START to 2,
            RouteContract.COLUMN_ALTITUDE to 3
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: -1 }

        manager.mapCursorToPathPoint(cursor)

        verify(exactly = 0) { cursor.moveToNext() }
        verify(exactly = 0) { cursor.moveToFirst() }
        verify(exactly = 0) { cursor.close() }
    }

    @Test
    fun testBatchMappers_singlePassIndexCaching_matchesRowByRow() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            RouteContract.COLUMN_LAT to 0,
            RouteContract.COLUMN_LNG to 1,
            RouteContract.COLUMN_DIST_FROM_START to 2,
            RouteContract.COLUMN_ALTITUDE to 3
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: -1 }

        var row = 0
        every { cursor.moveToNext() } answers {
            row++
            row <= 3
        }
        every { cursor.getDouble(0) } answers { 48.0 + row }
        every { cursor.getDouble(1) } answers { 11.0 + row }
        every { cursor.getDouble(2) } answers { 1000.0 * row }
        every { cursor.getDouble(3) } answers { 500.0 + (row * 10) }

        val points = manager.mapCursorToPathPoints(cursor)

        assertEquals(3, points.size)
        assertEquals(49.0, points[0].latLng.latitude, 0.001)
        assertEquals(50.0, points[1].latLng.latitude, 0.001)
        assertEquals(51.0, points[2].latLng.latitude, 0.001)
        verify(exactly = 0) { cursor.close() }
    }
}
