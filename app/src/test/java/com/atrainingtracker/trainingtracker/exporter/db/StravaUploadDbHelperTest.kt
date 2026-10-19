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

package com.atrainingtracker.trainingtracker.exporter.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying StravaUploadDbHelper parameterized queries and transaction atomicity
 * (REQ-EXP-021, TST-EXP-018, ATT-4012).
 */
class StravaUploadDbHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    private class TestableStravaUploadDbHelper(
        context: Context,
        private val db: SQLiteDatabase
    ) : StravaUploadDbHelper(context) {
        override fun getReadableDatabase(): SQLiteDatabase = db
        override fun getWritableDatabase(): SQLiteDatabase = db
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-EXP-018: getStravaActivityDataForWorkouts uses parameterized ? placeholders in selection
     * and passes fileBaseNames in selectionArgs.
     */
    @Test
    fun testGetStravaActivityDataForWorkouts_usesParameterizedQuery() {
        val helper = TestableStravaUploadDbHelper(mockContext, mockDb)
        val fileNames = listOf("workout_2026_01", "workout_2026_02", "workout_2026_03")

        val capturedSelection = slot<String>()
        val capturedSelectionArgs = slot<Array<String>>()

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME) } returns 0
        every { mockCursor.getColumnIndexOrThrow(StravaUploadDbHelper.STRAVA_ACTIVITY_DATA) } returns 1
        every { mockCursor.moveToNext() } returns false

        every {
            mockDb.query(
                StravaUploadDbHelper.TABLE,
                arrayOf(WorkoutSummaries.FILE_BASE_NAME, StravaUploadDbHelper.STRAVA_ACTIVITY_DATA),
                capture(capturedSelection),
                capture(capturedSelectionArgs),
                null, null, null
            )
        } returns mockCursor

        val result = helper.getStravaActivityDataForWorkouts(fileNames)
        assertNotNull(result)

        assertTrue("Selection must use parameterized placeholders", capturedSelection.isCaptured)
        assertEquals("fileBaseName IN (?,?,?)", capturedSelection.captured)

        assertTrue("Selection args must match input file names", capturedSelectionArgs.isCaptured)
        assertArrayEquals(fileNames.toTypedArray(), capturedSelectionArgs.captured)
    }

    /**
     * TST-EXP-018: updateOrInsert executes inside an atomic transaction.
     */
    @Test
    fun testUpdateOrInsert_usesAtomicTransaction() {
        val helper = TestableStravaUploadDbHelper(mockContext, mockDb)
        val values = mockk<ContentValues>(relaxed = true)
        val fileBaseName = "workout_test_123"

        every { mockDb.update(StravaUploadDbHelper.TABLE, values, "${WorkoutSummaries.FILE_BASE_NAME}=?", arrayOf(fileBaseName)) } returns 1

        helper.updateOrInsert(fileBaseName, values)

        verifyOrder {
            mockDb.beginTransaction()
            mockDb.update(StravaUploadDbHelper.TABLE, values, "${WorkoutSummaries.FILE_BASE_NAME}=?", arrayOf(fileBaseName))
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }
    }
}
