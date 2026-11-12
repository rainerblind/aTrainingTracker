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

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [WorkoutSummariesDatabaseManager.getCardioPowerDynamicsFitnessTrendSamples] (REQ-REV-011 / TST-REV-012 / ATT-3906).
 */
class WorkoutSummariesCardioPowerTrendTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var manager: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        manager = WorkoutSummariesDatabaseManager.getInstance(mockContext)
        val dbField = WorkoutSummariesDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, mockDb)

        val helperField = WorkoutSummariesDatabaseManager::class.java.getDeclaredField("cWorkoutSummariesDbHelper")
        helperField.isAccessible = true
        val mockHelper = mockk<WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper>(relaxed = true)
        helperField.set(null, mockHelper)
        every { mockHelper.writableDatabase } returns mockDb
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testQueryFitnessTrendSamples_executesSqlWithR2ThresholdAndParsesRecords() {
        val sqlSlot = slot<String>()
        val argsSlot = slot<Array<String>>()
        val mockCursor = mockk<Cursor>(relaxed = true)

        var cursorMoved = false
        every { mockCursor.moveToNext() } answers {
            if (!cursorMoved) {
                cursorMoved = true
                true
            } else {
                false
            }
        }

        every { mockCursor.getColumnIndexOrThrow("workout_id") } returns 0
        every { mockCursor.getColumnIndexOrThrow("time_start_str") } returns 1
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_TAU_START) } returns 2
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_HR0_START) } returns 3
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_K_START) } returns 4
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_HR200_START) } returns 5
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_R2_START) } returns 6
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_RMSE_START) } returns 7
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_TAU_END) } returns 8
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_HR0_END) } returns 9
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_K_END) } returns 10
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_HR200_END) } returns 11
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_R2_END) } returns 12
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_RMSE_END) } returns 13
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_DELTA_HR200) } returns 14
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.CPD_DELTA_HR0) } returns 15

        val dateStr = "2026-06-15 14:30:00"
        val expectedMs = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(dateStr)!!.time

        every { mockCursor.getLong(0) } returns 42L
        every { mockCursor.getString(1) } returns dateStr
        every { mockCursor.getDouble(2) } returns 32.5
        every { mockCursor.getDouble(3) } returns 65.0
        every { mockCursor.getDouble(4) } returns 0.20
        every { mockCursor.getDouble(5) } returns 145.0
        every { mockCursor.getDouble(6) } returns 0.78
        every { mockCursor.getDouble(7) } returns 3.2
        every { mockCursor.isNull(8) } returns true
        every { mockCursor.isNull(9) } returns true
        every { mockCursor.isNull(10) } returns true
        every { mockCursor.isNull(11) } returns true
        every { mockCursor.isNull(12) } returns true
        every { mockCursor.isNull(13) } returns true
        every { mockCursor.isNull(14) } returns true
        every { mockCursor.isNull(15) } returns true

        every { mockDb.rawQuery(capture(sqlSlot), capture(argsSlot)) } returns mockCursor

        val samples = manager.getCardioPowerDynamicsFitnessTrendSamples(0.50)

        assertEquals(1, samples.size)
        val sample = samples[0]
        assertEquals(42L, sample.workoutId)
        assertEquals(expectedMs, sample.timestampMs)
        assertEquals(145.0, sample.record.hr200Start, 0.001)
        assertEquals(32.5, sample.record.tauStart, 0.001)
        assertEquals(0.20, sample.record.kStart, 0.001)
        assertEquals(0.78, sample.record.r2Start, 0.001)

        assertTrue(sqlSlot.captured.contains("cardio_power_dynamics"))
        assertTrue(sqlSlot.captured.contains(WorkoutSummaries.CPD_R2_START + " >= ?"))
        assertTrue(sqlSlot.captured.contains("ORDER BY w." + WorkoutSummaries.TIME_START + " ASC"))
        assertEquals("0.5", argsSlot.captured[0])
    }

    @Test
    fun testQueryFitnessTrendSamples_whenDbClosed_returnsEmptyList() {
        every { mockDb.isOpen } returns false
        val samples = manager.getCardioPowerDynamicsFitnessTrendSamples(0.50)
        assertTrue(samples.isEmpty())
    }

    @Test
    fun testQueryFitnessTrendSamples_whenEmptyCursor_returnsEmptyList() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns false
        every { mockDb.rawQuery(any(), any()) } returns mockCursor

        val samples = manager.getCardioPowerDynamicsFitnessTrendSamples(0.50)
        assertTrue(samples.isEmpty())
    }
}
