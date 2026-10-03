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
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit test suite verifying historical corrupted altitude workout sanitization (REQ-CON-017, TST-CON-008.5).
 */
class WorkoutSummariesDatabaseManagerSanitizationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { constructedWith<ContentValues>().getAsString(any<String>()) } returns null

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        manager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSanitizeCorruptedAltitudeWorkouts_repairsStaircaseProfileAndExtrema() {
        val corruptedWorkoutId = 14L

        // Mock rawQuery to return workoutId 14 as corrupted
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), null) } returns mockCursor
        every { mockCursor.moveToNext() } returnsMany listOf(true, false)
        every { mockCursor.getLong(0) } returns corruptedWorkoutId

        // Mock string query for ALTITUDE_STREAM
        val originalStaircase = listOf(7300.0, 7300.0, 7200.0, 6000.0, 507.0, 510.0, 508.0)
        val encodedStream = NumericalEncodingUtils.encodeDoubles(originalStaircase)

        val streamCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID}=?",
                arrayOf(corruptedWorkoutId.toString()),
                null, null, null
            )
        } returns streamCursor
        every { streamCursor.moveToFirst() } returns true
        every { streamCursor.getString(any()) } returns encodedStream

        // Mock fileBaseName query
        val fileCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID}=?",
                arrayOf(corruptedWorkoutId.toString()),
                null, null, null
            )
        } returns fileCursor
        every { fileCursor.moveToFirst() } returns true
        every { fileCursor.getString(any()) } returns "2026-10-01-073000"

        var capturedEncodedStream: String? = null
        every {
            constructedWith<ContentValues>().put(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM,
                any<String>()
            )
        } answers {
            capturedEncodedStream = secondArg<String>()
        }
        every {
            constructedWith<ContentValues>().getAsString(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM)
        } answers {
            capturedEncodedStream
        }

        val capturedValues = slot<ContentValues>()
        every {
            mockDb.update(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                capture(capturedValues),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID}=?",
                arrayOf(corruptedWorkoutId.toString())
            )
        } returns 1

        every {
            mockDb.update(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE_EXTREMA_VALUES,
                any(), any(), any()
            )
        } returns 1

        // Act
        val sanitizedCount = manager.sanitizeCorruptedAltitudeWorkouts()

        // Assert
        assertEquals(1, sanitizedCount)
        verify { mockDb.setTransactionSuccessful() }

        // Verify the stream in capturedValues was sanitized
        assertTrue(capturedValues.isCaptured)
        val newStreamEncoded = capturedValues.captured.getAsString(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM)
        val decodedAlts = NumericalEncodingUtils.decodeDoubles(newStreamEncoded)

        // All points > 2000m should be replaced with valid baseline 507.0m
        assertEquals(7, decodedAlts.size)
        assertEquals(507.0, decodedAlts[0], 0.001)
        assertEquals(507.0, decodedAlts[1], 0.001)
        assertEquals(507.0, decodedAlts[2], 0.001)
        assertEquals(507.0, decodedAlts[3], 0.001)
        assertEquals(507.0, decodedAlts[4], 0.001)
        assertEquals(510.0, decodedAlts[5], 0.001)
        assertEquals(508.0, decodedAlts[6], 0.001)
    }

    @Test
    fun testSanitizeCorruptedAltitudeWorkouts_whenNoCorruptedWorkouts_returnsZero() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), null) } returns mockCursor
        every { mockCursor.moveToNext() } returns false

        val count = manager.sanitizeCorruptedAltitudeWorkouts()
        assertEquals(0, count)
        verify(exactly = 0) { mockDb.beginTransaction() }
    }
}
