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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [WorkoutSummariesDatabaseManager] unfinalized workout query and update predicates
 * for NULL and 0 finished states (REQ-UI-345, TST-UI-305, ATT-3165).
 */
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
        val dbField = WorkoutSummariesDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, mockDb)
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
}
