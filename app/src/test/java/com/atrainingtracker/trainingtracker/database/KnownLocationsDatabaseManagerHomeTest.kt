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
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying SQLite Schema V6 upgrade, single-home transactional exclusivity,
 * and home location query/clear functionality in KnownLocationsDatabaseManager.
 *
 * Traceability:
 * - REQ-MAP-034: Designated Home-Base Selection for Return Navigation & Substring Disambiguation.
 * - TST-MAP-036.1: Database Migration & Transactional Exclusivity Test.
 */
class KnownLocationsDatabaseManagerHomeTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

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

    @Test
    fun testSchemaV6Upgrade_addsIsHomeColumn() {
        val helper = KnownLocationsDatabaseManager.KnownLocationsDbHelper(mockContext)
        helper.onUpgrade(mockDb, 5, 6)

        verify(exactly = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE StartLocation2Altitude ADD COLUMN is_home integer default 0") })
        }
    }

    @Test
    fun testSetHomeLocation_transactionalExclusivity() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val targetId = 42L
        manager.setHomeLocation(targetId)

        verifyOrder {
            mockDb.beginTransaction()
            mockDb.update(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                any(),
                null,
                null
            )
            mockDb.update(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                any(),
                "${KnownLocationsDatabaseManager.KnownLocationsDbHelper.C_ID}=?",
                arrayOf("42")
            )
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }
    }

    @Test
    fun testClearHomeLocation_clearsAll() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.clearHomeLocation()

        verifyOrder {
            mockDb.beginTransaction()
            mockDb.update(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                any(),
                null,
                null
            )
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }

        // Verify no second update with id was called
        verify(exactly = 0) {
            mockDb.update(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                any(),
                match { it != null && it.contains(KnownLocationsDatabaseManager.KnownLocationsDbHelper.C_ID) },
                any()
            )
        }
    }

    @Test
    fun testGetHomeLocation_returnsDesignatedLocation() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
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
            every { mockCursor.getColumnIndex(name) } returns idx
        }

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getLong(0) } returns 42L
        every { mockCursor.getString(1) } returns "My Home"
        every { mockCursor.getDouble(3) } returns 500.0
        every { mockCursor.getDouble(4) } returns 11.5
        every { mockCursor.getDouble(5) } returns 48.1
        every { mockCursor.getInt(6) } returns 200
        every { mockCursor.getInt(7) } returns 15
        every { mockCursor.getInt(8) } returns 1
        every { mockCursor.getString(9) } returns "MANUAL_USER"
        every { mockCursor.getInt(10) } returns 1

        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                "${KnownLocationsDatabaseManager.KnownLocationsDbHelper.IS_HOME}=1",
                null, null, null, null, "1"
            )
        } returns mockCursor

        val home = manager.homeLocation
        assertNotNull(home)
        assertEquals(42L, home!!.id)
        assertEquals("My Home", home.name)
        assertTrue(home.isHome)
    }

    @Test
    fun testGetHomeLocation_returnsNullWhenNoneConfigured() {
        resetSingleton()
        val manager = KnownLocationsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false
        every {
            mockDb.query(
                KnownLocationsDatabaseManager.KnownLocationsDbHelper.TABLE,
                null,
                "${KnownLocationsDatabaseManager.KnownLocationsDbHelper.IS_HOME}=1",
                null, null, null, null, "1"
            )
        } returns mockCursor

        val home = manager.homeLocation
        assertNull(home)
    }
}
