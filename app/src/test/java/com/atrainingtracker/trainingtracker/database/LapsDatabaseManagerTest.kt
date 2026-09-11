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
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit test verifying LapsDatabaseManager, schema upgrade safety (v1 -> v2),
 * vectorized batch retrieval, and lap mutation (REQ-UI-141, TST-UI-094, ATT-510).
 */
class LapsDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        mockDb = mockk<SQLiteDatabase>(relaxed = true)

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
            val instanceField: Field = LapsDatabaseManager::class.java.getDeclaredField("cInstance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun injectMockDatabase(manager: LapsDatabaseManager, db: SQLiteDatabase) {
        val dbField: Field = LapsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, db)
    }

    private fun setupMockCursorColumns(mockCursor: Cursor) {
        val colMap = mapOf(
            LapsDatabaseManager.Laps.C_ID to 0,
            LapsDatabaseManager.Laps.WORKOUT_ID to 1,
            LapsDatabaseManager.Laps.LAP_NR to 2,
            LapsDatabaseManager.Laps.TIME_START to 3,
            LapsDatabaseManager.Laps.TIME_TOTAL_s to 4,
            LapsDatabaseManager.Laps.DISTANCE_TOTAL_m to 5,
            LapsDatabaseManager.Laps.SPEED_AVERAGE_mps to 6,
            LapsDatabaseManager.Laps.NAME to 7,
            LapsDatabaseManager.Laps.DESCRIPTION to 8
        )
        colMap.forEach { (name, idx) ->
            every { mockCursor.getColumnIndex(name) } returns idx
            every { mockCursor.getColumnIndexOrThrow(name) } returns idx
        }
    }

    @Test
    fun testDbHelperConstantsAndSchema() {
        assertEquals("Database version must be upgraded to 2", 2, LapsDatabaseManager.LapsDbHelper.DB_VERSION)
        assertTrue(
            "CREATE_TABLE must declare name TEXT column",
            LapsDatabaseManager.LapsDbHelper.CREATE_TABLE.contains("name TEXT")
        )
        assertTrue(
            "CREATE_TABLE must declare description TEXT column",
            LapsDatabaseManager.LapsDbHelper.CREATE_TABLE.contains("description TEXT")
        )
        assertEquals("name", LapsDatabaseManager.Laps.NAME)
        assertEquals("description", LapsDatabaseManager.Laps.DESCRIPTION)
    }

    @Test
    fun testOnUpgrade_v1ToV2_altersTableNonDestructivelyWithoutDropping() {
        val helper = LapsDatabaseManager.LapsDbHelper(mockContext)
        helper.onUpgrade(mockDb, 1, 2)

        // Verify ALTER TABLE statements executed
        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("ADD COLUMN") && it.contains("name") })
        }
        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE") && it.contains("ADD COLUMN") && it.contains("description") })
        }

        // Verify table was NEVER dropped (Zero Data Loss Invariant)
        verify(exactly = 0) {
            mockDb.execSQL(match { it.lowercase().contains("drop table") })
        }
    }

    @Test
    fun testGetLaps_singleWorkout_mapsColumnsCorrectly() {
        resetSingleton()
        val manager = LapsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)
        every { mockDb.isOpen } returns true

        val mockCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(mockCursor)

        every {
            mockDb.query(
                LapsDatabaseManager.Laps.TABLE,
                null,
                "${LapsDatabaseManager.Laps.WORKOUT_ID} = ?",
                arrayOf("42"),
                null,
                null,
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)

        every { mockCursor.getLong(0) } returnsMany listOf(1L, 2L)
        every { mockCursor.getLong(1) } returns 42L
        every { mockCursor.getLong(2) } returnsMany listOf(1L, 2L)
        every { mockCursor.getString(3) } returns "2026-09-11 10:00:00"
        every { mockCursor.getInt(4) } returnsMany listOf(300, 310)
        every { mockCursor.getDouble(5) } returnsMany listOf(1000.0, 1050.0)
        every { mockCursor.getDouble(6) } returnsMany listOf(3.33, 3.39)
        every { mockCursor.isNull(7) } returnsMany listOf(false, true)
        every { mockCursor.getString(7) } returns "Warmup"
        every { mockCursor.isNull(8) } returnsMany listOf(false, true)
        every { mockCursor.getString(8) } returns "Easy jog"

        val laps = manager.getLaps(42L)

        assertEquals(2, laps.size)
        assertEquals("Warmup", laps[0].name)
        assertEquals("Easy jog", laps[0].description)
        assertEquals(300, laps[0].timeTotalS)
        assertEquals(1000.0, laps[0].distanceTotalM, 0.01)

        assertNull(laps[1].name)
        assertNull(laps[1].description)
        assertEquals("Lap 2", laps[1].getDisplayName(2))

        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testGetLapsForWorkouts_vectorizedBatch_eliminatesNPlusOne() {
        resetSingleton()
        val manager = LapsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)
        every { mockDb.isOpen } returns true

        val mockCursor = mockk<Cursor>(relaxed = true)
        setupMockCursorColumns(mockCursor)

        every {
            mockDb.query(
                LapsDatabaseManager.Laps.TABLE,
                null,
                match { it.contains(LapsDatabaseManager.Laps.WORKOUT_ID) && it.contains("IN") },
                any(),
                null,
                null,
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)

        every { mockCursor.getLong(0) } returnsMany listOf(10L, 20L)
        every { mockCursor.getLong(1) } returnsMany listOf(100L, 200L)
        every { mockCursor.getLong(2) } returns 1L
        every { mockCursor.getString(3) } returns "2026-09-11 12:00:00"
        every { mockCursor.getInt(4) } returns 240
        every { mockCursor.getDouble(5) } returns 800.0
        every { mockCursor.getDouble(6) } returns 3.33
        every { mockCursor.isNull(7) } returns true
        every { mockCursor.isNull(8) } returns true

        val resultMap = manager.getLapsForWorkouts(listOf(100L, 200L))

        assertEquals(2, resultMap.size)
        assertTrue(resultMap.containsKey(100L))
        assertTrue(resultMap.containsKey(200L))
        assertEquals(1, resultMap[100L]?.size)
        assertEquals(1, resultMap[200L]?.size)

        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testUpdateLapDetails_executesCorrectUpdate() {
        resetSingleton()
        val manager = LapsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)
        every { mockDb.isOpen } returns true

        every {
            mockDb.update(
                LapsDatabaseManager.Laps.TABLE,
                any(),
                "${LapsDatabaseManager.Laps.WORKOUT_ID} = ? AND ${LapsDatabaseManager.Laps.LAP_NR} = ?",
                arrayOf("50", "3")
            )
        } returns 1

        val updated = manager.updateLapDetails(50L, 3L, "Sprint", "All out effort")

        assertTrue(updated)
        verify {
            anyConstructed<ContentValues>().put(LapsDatabaseManager.Laps.NAME, "Sprint")
            anyConstructed<ContentValues>().put(LapsDatabaseManager.Laps.DESCRIPTION, "All out effort")
            mockDb.update(
                LapsDatabaseManager.Laps.TABLE,
                any(),
                "${LapsDatabaseManager.Laps.WORKOUT_ID} = ? AND ${LapsDatabaseManager.Laps.LAP_NR} = ?",
                arrayOf("50", "3")
            )
        }
    }
}
