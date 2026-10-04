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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * Unit tests verifying WorkoutSummariesDatabaseManager Schema V23 upgrade and race column persistence (REQ-UI-236, TST-UI-195).
 */
class WorkoutSummariesDatabaseRaceMigrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    private val capturedValuesMap = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        capturedValuesMap.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            capturedValuesMap[firstArg<String>()] = secondArg<String>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            capturedValuesMap[firstArg<String>()] = secondArg<Int>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            capturedValuesMap[firstArg<String>()] = secondArg<Long>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } answers {
            capturedValuesMap[firstArg<String>()] = secondArg<Double>()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Boolean>()) } answers {
            capturedValuesMap[firstArg<String>()] = secondArg<Boolean>()
        }
        every { anyConstructed<ContentValues>().putNull(any<String>()) } answers {
            capturedValuesMap[firstArg<String>()] = null
        }
        every { anyConstructed<ContentValues>().getAsInteger(any<String>()) } answers {
            capturedValuesMap[firstArg<String>()] as? Int
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDbVersion_isAtLeast23() {
        assertTrue("DB_VERSION must be at least 23", WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_VERSION >= 23)
    }

    @Test
    fun testRaceColumnConstant_isRace() {
        assertEquals("Column constant must be 'race'", "race", WorkoutSummaries.RACE)
    }

    @Test
    fun testOnUpgrade_fromV22ToV23_addsRaceColumn() {
        val helper = WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper(mockContext)

        // Mock cursor returning column names to simulate column check
        val cursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.moveToNext() } returns false
        every { cursor.getColumnIndex("name") } returns 1
        every { cursor.getString(1) } returns WorkoutSummaries.C_ID

        helper.onUpgrade(mockDb, 22, 23)

        // Verify ALTER TABLE execution for adding race column
        verify {
            mockDb.execSQL(match {
                it.contains("ALTER TABLE ${WorkoutSummaries.TABLE} ADD COLUMN ${WorkoutSummaries.RACE} int DEFAULT 0")
            })
        }
    }

    @Test
    fun testUpdateWorkoutData_persistsRaceTrueAsOne() {
        val manager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.update(
                WorkoutSummaries.TABLE,
                any(),
                match { it.contains(WorkoutSummaries.C_ID) },
                null
            )
        } returns 1

        val workoutData = mockk<WorkoutData>(relaxed = true)
        every { workoutData.id } returns 101L
        every { workoutData.race } returns true
        manager.updateWorkoutData(workoutData)

        assertEquals(1, capturedValuesMap[WorkoutSummaries.RACE])
        verify {
            mockDb.update(
                WorkoutSummaries.TABLE,
                any(),
                match { it.contains(WorkoutSummaries.C_ID) },
                null
            )
        }
    }

    @Test
    fun testUpdateWorkoutData_persistsRaceFalseAsZero() {
        val manager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        every {
            mockDb.update(
                WorkoutSummaries.TABLE,
                any(),
                match { it.contains(WorkoutSummaries.C_ID) },
                null
            )
        } returns 1

        val workoutData = mockk<WorkoutData>(relaxed = true)
        every { workoutData.id } returns 102L
        every { workoutData.race } returns false
        manager.updateWorkoutData(workoutData)

        assertEquals(0, capturedValuesMap[WorkoutSummaries.RACE])
        verify {
            mockDb.update(
                WorkoutSummaries.TABLE,
                any(),
                match { it.contains(WorkoutSummaries.C_ID) },
                null
            )
        }
    }
}
