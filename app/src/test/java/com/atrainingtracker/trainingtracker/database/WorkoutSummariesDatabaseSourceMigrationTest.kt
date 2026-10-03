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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying WorkoutSummariesDatabaseManager Schema V24 upgrade and source column persistence (REQ-DAT-017, TST-DAT-012.1).
 */
class WorkoutSummariesDatabaseSourceMigrationTest {

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
        every { anyConstructed<ContentValues>().getAsString(any<String>()) } answers {
            capturedValuesMap[firstArg<String>()] as? String
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDbVersion_is24() {
        assertEquals("DB_VERSION must be 24", 24, WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_VERSION)
    }

    @Test
    fun testSourceColumnConstant_isSource() {
        assertEquals("Column constant must be 'source'", "source", WorkoutSummaries.SOURCE)
    }

    @Test
    fun testOnUpgrade_fromV23ToV24_addsSourceColumn() {
        val helper = WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper(mockContext)

        // Mock cursor returning column names to simulate column check
        val cursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.moveToNext() } returns false
        every { cursor.getColumnIndex("name") } returns 1
        every { cursor.getString(1) } returns WorkoutSummaries.C_ID

        helper.onUpgrade(mockDb, 23, 24)

        // Verify ALTER TABLE execution for adding source column with default 'TRACKED'
        verify {
            mockDb.execSQL(match {
                it.contains("ALTER TABLE ${WorkoutSummaries.TABLE} ADD COLUMN ${WorkoutSummaries.SOURCE} text DEFAULT 'TRACKED'")
            })
        }
    }

    @Test
    fun testUpdateWorkoutData_persistsSource() {
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
        every { workoutData.source } returns WorkoutSource.TCX
        manager.updateWorkoutData(workoutData)

        assertEquals("TCX", capturedValuesMap[WorkoutSummaries.SOURCE])
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
