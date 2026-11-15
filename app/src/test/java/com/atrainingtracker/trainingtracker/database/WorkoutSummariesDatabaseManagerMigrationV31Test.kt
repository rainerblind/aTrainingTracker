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

@file:Suppress("MaxLineLength")

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying WorkoutSummaries.db v31 migration, Polar H10 retry sync pending columns,
 * and DatabaseSchemaRegistry registration (REQ-SET-106.5, TST-SET-108.2, ATT-7919).
 */
class WorkoutSummariesDatabaseManagerMigrationV31Test {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testDbVersionIs31() {
        assertEquals("WorkoutSummariesDbHelper.DB_VERSION must be 31", 31, WorkoutSummariesDbHelper.DB_VERSION)
    }

    @Test
    fun testDatabaseSchemaRegistryRecordsV31() {
        val version = DatabaseSchemaRegistry.getMaxSupportedVersion("WorkoutSummaries.db")
        assertEquals("WorkoutSummaries.db registered version must be 31", 31, version)
    }

    @Test
    fun testColumnConstantsExist() {
        assertEquals("polarDeviceId", WorkoutSummaries.POLAR_DEVICE_ID)
        assertEquals("polarExerciseId", WorkoutSummaries.POLAR_EXERCISE_ID)
        assertEquals("polarExpectedStartMs", WorkoutSummaries.POLAR_EXPECTED_START_MS)
        assertEquals("polarExpectedEndMs", WorkoutSummaries.POLAR_EXPECTED_END_MS)
        assertEquals("polarSyncPending", WorkoutSummaries.POLAR_SYNC_PENDING)
    }

    @Test
    fun testCreateTableContainsNewColumns() {
        val createSql = WorkoutSummariesDbHelper.CREATE_TABLE
        assertTrue(createSql.contains(WorkoutSummaries.POLAR_DEVICE_ID))
        assertTrue(createSql.contains(WorkoutSummaries.POLAR_EXERCISE_ID))
        assertTrue(createSql.contains(WorkoutSummaries.POLAR_EXPECTED_START_MS))
        assertTrue(createSql.contains(WorkoutSummaries.POLAR_EXPECTED_END_MS))
        assertTrue(createSql.contains(WorkoutSummaries.POLAR_SYNC_PENDING))
    }

    @Test
    fun testOnUpgrade_from30to31_addsMissingColumnsIdempotently() {
        val mockDb = mockk<SQLiteDatabase>(relaxed = true)
        val mockCursor = mockk<Cursor>(relaxed = true)

        // Mock cursor returning column name index and empty cursor so columns do not exist yet
        every { mockDb.rawQuery(match { it.contains("PRAGMA table_info") }, null) } returns mockCursor
        every { mockCursor.getColumnIndex("name") } returns 1
        every { mockCursor.moveToNext() } returns false
        every { mockCursor.close() } just Runs

        val helper = WorkoutSummariesDbHelper(mockContext)
        helper.onUpgrade(mockDb, 30, 31)

        verify {
            mockDb.execSQL(match { it.contains("ALTER TABLE WorkoutSummaries ADD COLUMN polarDeviceId text") })
            mockDb.execSQL(match { it.contains("ALTER TABLE WorkoutSummaries ADD COLUMN polarExerciseId text") })
            mockDb.execSQL(
                match { it.contains("ALTER TABLE WorkoutSummaries ADD COLUMN polarExpectedStartMs int DEFAULT 0") }
            )
            mockDb.execSQL(
                match { it.contains("ALTER TABLE WorkoutSummaries ADD COLUMN polarExpectedEndMs int DEFAULT 0") }
            )
            mockDb.execSQL(
                match { it.contains("ALTER TABLE WorkoutSummaries ADD COLUMN polarSyncPending int DEFAULT 0") }
            )
        }
    }
}
