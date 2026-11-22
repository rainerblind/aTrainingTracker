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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [WorkoutSummariesDatabaseManager] unfinalized workout query and update predicates
 * for NULL and 0 finished states (REQ-UI-345, TST-UI-305, ATT-3165).
 */
// Linked test specs: TST-SET-085
class WorkoutSummariesDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: WorkoutSummariesDatabaseManager

    private val contentValuesMap = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        contentValuesMap.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            contentValuesMap[firstArg<String>()] = secondArg<Int>()
        }
        every { anyConstructed<ContentValues>().getAsInteger(any<String>()) } answers {
            contentValuesMap[firstArg<String>()] as? Int
        }

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        every { mockDb.isOpen } returns true

        // Create manager via reflection or singleton
        manager = WorkoutSummariesDatabaseManager.getInstance(mockContext)
        manager.setDatabaseForTesting(mockDb)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testHasUnfinishedWorkout_appliesNullAndZeroFinishedFilter() {
        val selectionSlot = slot<String>()
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true

        every {
            mockDb.query(
                WorkoutSummaries.TABLE,
                any(),
                capture(selectionSlot),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns mockCursor

        val result = manager.hasUnfinishedWorkout()

        assertTrue("Expected hasUnfinishedWorkout to return true when cursor has rows", result)
        assertEquals(
            "Selection predicate must filter both NULL and 0 finished states",
            "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)",
            selectionSlot.captured
        )
    }

    @Test
    fun testHasUnfinishedWorkout_returnsFalseWhenNoMatchingRows() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false

        every {
            mockDb.query(
                WorkoutSummaries.TABLE,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns mockCursor

        val result = manager.hasUnfinishedWorkout()
        assertFalse("Expected hasUnfinishedWorkout to return false when no matching unfinalized rows exist", result)
    }

    @Test
    fun testDiscardOrFinishUnfinishedWorkout_appliesNullAndZeroWhereClause() {
        val whereClauseSlot = slot<String>()
        val valuesSlot = slot<ContentValues>()

        every {
            mockDb.update(
                WorkoutSummaries.TABLE,
                capture(valuesSlot),
                capture(whereClauseSlot),
                any()
            )
        } returns 1

        manager.discardOrFinishUnfinishedWorkout()

        assertEquals(
            "Where clause must update rows where finished IS NULL OR finished = 0",
            "(" + WorkoutSummaries.FINISHED + " IS NULL OR " + WorkoutSummaries.FINISHED + " = 0)",
            whereClauseSlot.captured
        )
        assertEquals(1, valuesSlot.captured.getAsInteger(WorkoutSummaries.FINISHED))
    }

    @Test
    fun testGetFinishedWorkoutCountForSport_queriesFinishedAndSportId() {
        val querySlot = slot<String>()
        val argsSlot = slot<Array<String>>()
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 100

        every {
            mockDb.rawQuery(capture(querySlot), capture(argsSlot))
        } returns mockCursor

        val count = manager.getFinishedWorkoutCountForSport(2L)

        assertEquals(100, count)
        assertTrue(querySlot.captured.contains("finished = 1"))
        assertTrue(querySlot.captured.contains("sportId = ?"))
        assertEquals("2", argsSlot.captured[0])
    }

    @Test
    fun testGetFinishedWorkoutCountForSportType_queriesFinishedAndBSport() {
        val querySlot = slot<String>()
        val argsSlot = slot<Array<String>>()
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 250

        every {
            mockDb.rawQuery(capture(querySlot), capture(argsSlot))
        } returns mockCursor

        val count = manager.getFinishedWorkoutCountForSportType(com.atrainingtracker.banalservice.BSportType.BIKE)

        assertEquals(250, count)
        assertTrue(querySlot.captured.contains("finished = 1"))
        assertTrue(querySlot.captured.contains("Sport = ?"))
        assertEquals("BIKE", argsSlot.captured[0])
    }

    @Test
    fun testGetEarliestWorkoutYear_returnsYearWhenRecordExists() {
        val querySlot = slot<String>()
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns "2018-05-10 14:32:00"

        every {
            mockDb.rawQuery(capture(querySlot), isNull())
        } returns mockCursor

        val year = manager.earliestWorkoutYear

        assertEquals(2018, year)
        assertTrue(querySlot.captured.contains("MIN(timeStart)"))
        assertTrue(querySlot.captured.contains("WorkoutSummaries"))
    }

    @Test
    fun testGetEarliestWorkoutYear_returnsNullWhenEmptyOrInvalid() {
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getString(0) } returns null

        every {
            mockDb.rawQuery(any(), isNull())
        } returns mockCursor

        assertNull(manager.earliestWorkoutYear)

        every { mockCursor.getString(0) } returns "invalid"
        assertNull(manager.earliestWorkoutYear)

        every { mockCursor.moveToFirst() } returns false
        assertNull(manager.earliestWorkoutYear)
    }

    @Test
    fun testNullableGetters_returnNullOnSqlNullAndPreserveNonNullValues_TST_DAT_044_1() {
        val nullCursor = mockk<Cursor>(relaxed = true)
        every { nullCursor.moveToFirst() } returns true
        every { nullCursor.getColumnIndexOrThrow(any()) } returns 0
        every { nullCursor.isNull(0) } returns true
        every { nullCursor.getDouble(0) } returns 0.0
        every { nullCursor.getInt(0) } returns 0
        every { nullCursor.getLong(0) } returns 0L

        every {
            mockDb.query(any(), any(), any(), any(), any(), any(), any())
        } returns nullCursor

        assertNull(manager.getDouble("base_file", WorkoutSummaries.DISTANCE_TOTAL_m))
        assertNull(manager.getInt(42L, WorkoutSummaries.TIME_ACTIVE_s))
        assertNull(manager.getInt("base_file", WorkoutSummaries.TIME_ACTIVE_s))
        assertNull(manager.getInt(null as String?, WorkoutSummaries.TIME_ACTIVE_s))
        assertNull(manager.getLong(42L, WorkoutSummaries.CLUSTER_ID))
        assertNull(
            manager.getExtremaValue(
                mockDb,
                42L,
                com.atrainingtracker.banalservice.sensor.SensorType.HR,
                ExtremaType.MAX
            )
        )

        val valueCursor = mockk<Cursor>(relaxed = true)
        every { valueCursor.moveToFirst() } returns true
        every { valueCursor.getColumnIndexOrThrow(any()) } returns 0
        every { valueCursor.isNull(0) } returns false
        every { valueCursor.getDouble(0) } returns 0.0
        every { valueCursor.getInt(0) } returns 0
        every { valueCursor.getLong(0) } returns 0L

        every {
            mockDb.query(any(), any(), any(), any(), any(), any(), any())
        } returns valueCursor

        assertEquals(0.0, manager.getDouble("base_file", WorkoutSummaries.DISTANCE_TOTAL_m)!!, 0.0)
        assertEquals(0, manager.getInt(42L, WorkoutSummaries.TIME_ACTIVE_s))
        assertEquals(0, manager.getInt("base_file", WorkoutSummaries.TIME_ACTIVE_s))
        assertEquals(0L, manager.getLong(42L, WorkoutSummaries.CLUSTER_ID))
        assertEquals(
            0.0,
            manager.getExtremaValue(
                mockDb,
                42L,
                com.atrainingtracker.banalservice.sensor.SensorType.HR,
                ExtremaType.MAX
            )!!,
            0.0
        )
    }
}
