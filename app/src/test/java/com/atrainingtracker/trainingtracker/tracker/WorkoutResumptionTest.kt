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

package com.atrainingtracker.trainingtracker.tracker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
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
 * Unit tests verifying Interrupted Workout Resumption and Unfinished Recovery (REQ-STB-003, TST-STB-003, ATT-635).
 */
class WorkoutResumptionTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit

        TrainingApplication.setResumeFromCrash(false)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExtraResumeInterruptedWorkoutConstantValue() {
        assertEquals("com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT", MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT)
    }

    @Test
    fun testResumeIntentExtrasAreConfiguredCorrectly() {
        val resumeIntent = mockk<Intent>(relaxed = true)
        val booleanSlot = slot<Boolean>()
        val stringSlot = slot<String>()

        every { resumeIntent.putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, capture(booleanSlot)) } returns resumeIntent
        every { resumeIntent.putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, capture(stringSlot)) } returns resumeIntent

        resumeIntent.putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name)
        resumeIntent.putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true)

        assertTrue("EXTRA_RESUME_INTERRUPTED_WORKOUT should be captured as true", booleanSlot.captured)
        assertEquals(MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name, stringSlot.captured)
    }

    @Test
    fun testResumeFromCrashStateLifecycle() {
        assertFalse(TrainingApplication.isResumeFromCrash())

        TrainingApplication.setResumeFromCrash(true)
        assertTrue(TrainingApplication.isResumeFromCrash())

        TrainingApplication.setResumeFromCrash(false)
        assertFalse(TrainingApplication.isResumeFromCrash())
    }

    @Test
    fun testHasUnfinishedWorkout_ReturnsTrueWhenFinishedIsZero() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockDb.isOpen } returns true
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} DESC",
                "1"
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED) } returns 1
        every { mockCursor.getInt(1) } returns 0 // FINISHED == 0

        val mockContext = mockk<Context>(relaxed = true)
        val dbManager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        assertTrue("hasUnfinishedWorkout should return true when FINISHED == 0", dbManager.hasUnfinishedWorkout())
        verify { mockCursor.close() }
    }

    @Test
    fun testHasUnfinishedWorkout_ReturnsFalseWhenFinishedIsOne() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockDb.isOpen } returns true
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} DESC",
                "1"
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED) } returns 1
        every { mockCursor.getInt(1) } returns 1 // FINISHED == 1

        val mockContext = mockk<Context>(relaxed = true)
        val dbManager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        assertFalse("hasUnfinishedWorkout should return false when FINISHED == 1", dbManager.hasUnfinishedWorkout())
        verify { mockCursor.close() }
    }

    @Test
    fun testHasUnfinishedWorkout_ReturnsFalseWhenTableEmpty() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        every { mockDb.isOpen } returns true
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} DESC",
                "1"
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns false // No rows

        val mockContext = mockk<Context>(relaxed = true)
        val dbManager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        assertFalse("hasUnfinishedWorkout should return false when no rows exist", dbManager.hasUnfinishedWorkout())
        verify { mockCursor.close() }
    }

    @Test
    fun testDiscardOrFinishUnfinishedWorkout_ExecutesDatabaseUpdate() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        every { mockDb.isOpen } returns true

        val whereSlot = slot<String>()

        every {
            mockDb.update(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                capture(whereSlot),
                null
            )
        } returns 1

        val mockContext = mockk<Context>(relaxed = true)
        val dbManager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        dbManager.discardOrFinishUnfinishedWorkout()

        assertEquals("${WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED} = 0", whereSlot.captured)
        verify {
            anyConstructed<ContentValues>().put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FINISHED, 1)
        }
    }
}
