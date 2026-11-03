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
import android.util.Log
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests for [KnownLocationsDatabaseManager.upsertLocationByGeofence] manual calibration (REQ-TRK-047, TST-TRK-047.2).
 */
class KnownLocationsManualCalibrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        val mockResources = mockk<android.content.res.Resources>(relaxed = true)
        every { mockResources.getString(any()) } returns "Startort"
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.resources } returns mockResources
        every { mockContext.getString(any()) } returns "Startort"
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(Location::class)
        every { anyConstructed<Location>().setLatitude(any()) } returns Unit
        every { anyConstructed<Location>().setLongitude(any()) } returns Unit

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val instanceField: Field = KnownLocationsDatabaseManager::class.java.getDeclaredField("cInstance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun injectMockDatabase(manager: KnownLocationsDatabaseManager, db: SQLiteDatabase) {
        val dbField: Field = KnownLocationsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, db)
    }

    private fun setupMockCursorColumns(cursor: Cursor) {
        val columns = mapOf(
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.C_ID to 0,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.NAME to 1,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.EXTREMA_TYPE to 2,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.ALTITUDE to 3,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.LONGITUDE to 4,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.LATITUDE to 5,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.RADIUS to 6,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.HIT_COUNT to 7,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.IS_LOCKED to 8,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.SOURCE to 9,
            KnownLocationsDatabaseManager.KnownLocationsDbHelper.IS_HOME to 10
        )
        columns.forEach { (name, idx) ->
            every { cursor.getColumnIndex(name) } returns idx
            every { cursor.getColumnIndexOrThrow(name) } returns idx
        }
    }

    @Test
    fun testManualCalibration_createsNewLocationWithLockedManualUser() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val emptyCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(emptyCursor)
        every { emptyCursor.moveToNext() } returns false
        every { mockDb.query(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, null, null, null, null, null, null) } returns emptyCursor
        every { mockDb.insert(any(), any(), any()) } returns 101L

        val resultCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(resultCursor)
        every { resultCursor.moveToNext() } returns true andThen false
        every { resultCursor.getLong(0) } returns 101L
        every { resultCursor.getString(1) } returns "Mountain Pass"
        every { resultCursor.getString(2) } returns "START"
        every { resultCursor.getDouble(3) } returns 1420.0
        every { resultCursor.getDouble(4) } returns 11.234
        every { resultCursor.getDouble(5) } returns 47.567
        every { resultCursor.getInt(6) } returns 200
        every { resultCursor.getInt(7) } returns 1
        every { resultCursor.getInt(8) } returns 1
        every { resultCursor.getString(9) } returns "MANUAL_USER"

        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                match { it.contains("_id=?") },
                arrayOf("101"),
                null, null, null, null
            )
        } returns resultCursor

        val loc = manager.upsertLocationByGeofence(
            LatLng(47.567, 11.234),
            1420.0,
            "Mountain Pass",
            ExtremaType.START,
            ElevationSource.MANUAL_USER,
            true
        )

        assertNotNull(loc)
        assertEquals(101L, loc!!.id)
        assertEquals("Mountain Pass", loc.name)
        assertEquals(1420.0, loc.altitude, 1e-4)
        assertTrue(loc.isLocked)
        assertEquals(ElevationSource.MANUAL_USER, loc.source)
    }

    @Test
    fun testManualCalibration_updatesExistingLockedLocation() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val existingCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(existingCursor)
        // Existing location is locked (isLocked = 1) with altitude 1400.0
        every { existingCursor.moveToNext() } returns true andThen false
        every { existingCursor.getLong(0) } returns 42L
        every { existingCursor.getString(1) } returns "Old Landmark"
        every { existingCursor.getDouble(3) } returns 1400.0
        every { existingCursor.getDouble(4) } returns 11.234
        every { existingCursor.getDouble(5) } returns 47.567
        every { existingCursor.getInt(6) } returns 200
        every { existingCursor.getInt(7) } returns 5
        every { existingCursor.getInt(8) } returns 1 // LOCKED!
        every { existingCursor.getString(9) } returns "MANUAL_USER"

        every { mockDb.query(KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE, null, null, null, null, null, null) } returns existingCursor
        every { anyConstructed<Location>().distanceTo(any()) } returns 30.0f // within 200m radius

        val updatedCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(updatedCursor)
        every { updatedCursor.moveToFirst() } returns true
        every { updatedCursor.moveToNext() } returns true andThen false
        every { updatedCursor.getLong(0) } returns 42L
        every { updatedCursor.getString(1) } returns "Updated Landmark"
        every { updatedCursor.getString(2) } returns "START"
        every { updatedCursor.getDouble(3) } returns 1425.0 // updated altitude
        every { updatedCursor.getDouble(4) } returns 11.234
        every { updatedCursor.getDouble(5) } returns 47.567
        every { updatedCursor.getInt(6) } returns 200
        every { updatedCursor.getInt(7) } returns 5
        every { updatedCursor.getInt(8) } returns 1
        every { updatedCursor.getString(9) } returns "MANUAL_USER"
        every { updatedCursor.getInt(10) } returns 0

        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                match { it.contains("_id=?") },
                arrayOf("42"),
                null, null, null, null
            )
        } returns updatedCursor

        val loc = manager.upsertLocationByGeofence(
            LatLng(47.567, 11.234),
            1425.0,
            "Updated Landmark",
            ExtremaType.START,
            ElevationSource.MANUAL_USER,
            true
        )

        assertNotNull(loc)
        assertEquals(42L, loc!!.id)
        verify(exactly = 1) {
            anyConstructed<ContentValues>().put(KnownLocationsDatabaseManager.KnownLocationsDbHelper.ALTITUDE, 1425.0)
            anyConstructed<ContentValues>().put(KnownLocationsDatabaseManager.KnownLocationsDbHelper.NAME, "Updated Landmark")
        }
    }
}
