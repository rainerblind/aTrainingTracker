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

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying targeted single-row indexed query and bounds safety
 * for TrackerService crash resumption (REQ-DAT-002, TST-DAT-002, ATT-3549).
 */
class TrackerServiceResumeTest {

    private class TestableTrackerService : TrackerService()

    private lateinit var service: TestableTrackerService
    private val mockDbManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockCursor = mockk<Cursor>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(BANALService::class)
        every { BANALService.setInitialSensorValue(any(), any<Int>()) } returns Unit
        every { BANALService.setInitialSensorValue(any(), any<Double>()) } returns Unit

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockDbManager
        every { mockDbManager.database } returns mockDb

        service = TestableTrackerService()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testRecreateValuesWhenResuming_queriesTargetedProjectionAndAppliesState() {
        val projectionSlot = slot<Array<String>>()
        val orderBySlot = slot<String>()
        val limitSlot = slot<String>()

        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                capture(projectionSlot),
                null,
                null,
                null,
                null,
                capture(orderBySlot),
                capture(limitSlot)
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true

        val expectedColumns = arrayOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
        )

        expectedColumns.forEachIndexed { index, colName ->
            every { mockCursor.getColumnIndexOrThrow(colName) } returns index
        }

        every { mockCursor.getString(0) } returns "2026-10-15_120000"
        every { mockCursor.getLong(1) } returns 42L
        every { mockCursor.getInt(2) } returns 3600
        every { mockCursor.getInt(3) } returns 3450
        every { mockCursor.getInt(4) } returns 550
        every { mockCursor.getInt(5) } returns 3
        every { mockCursor.getDouble(6) } returns 12500.5

        service.recreateValuesWhenResuming()

        // 1. Verify query arguments
        assertArrayEquals(expectedColumns, projectionSlot.captured)
        assertEquals("${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} DESC", orderBySlot.captured)
        assertEquals("1", limitSlot.captured)

        // 2. Verify instance fields in TrackerService
        assertEquals("2026-10-15_120000", service.baseFileName)
        assertEquals(42L, service.workoutID)
        assertEquals(3600, service.timeTotal_s)
        assertEquals(3450, service.timeActive_s)
        assertEquals(12500.5, service.distanceTotal_m, 0.001)

        // 3. Verify BANALService accumulators initialized
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, 3600) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, 3450) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.CALORIES, 550) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.LAP_NR, 3) }
        verify(exactly = 1) { BANALService.setInitialSensorValue(SensorType.DISTANCE_m, 12500.5) }

        // 4. Verify deterministic try-with-resources cursor closure
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testRecreateValuesWhenResuming_emptyTable_logsWarningAndEarlyReturnsWithoutMutation() {
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns false

        service.recreateValuesWhenResuming()

        // State remains unmutated / default
        assertNull(service.baseFileName)
        assertEquals(0L, service.workoutID)
        assertEquals(0, service.timeTotal_s)
        assertEquals(0, service.timeActive_s)
        assertEquals(0.0, service.distanceTotal_m, 0.001)

        // BANALService never called
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Int>()) }
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Double>()) }

        // Warning logged and cursor closed
        verify(exactly = 1) { Log.w("TrackerService", "recreateValuesWhenResuming: No workout summary record found to resume.") }
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testRecreateValuesWhenResuming_missingColumn_failsFastWithoutPartialMutation() {
        every {
            mockDb.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                any(),
                null,
                null,
                null,
                null,
                any(),
                any()
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every {
            mockCursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)
        } throws IllegalArgumentException("Column not found")

        service.recreateValuesWhenResuming()

        // State remains unmutated
        assertNull(service.baseFileName)
        assertEquals(0L, service.workoutID)

        // Error logged, BANALService never called, cursor closed
        verify(exactly = 1) { Log.e(eq("TrackerService"), any<String>(), any<IllegalArgumentException>()) }
        verify(exactly = 0) { BANALService.setInitialSensorValue(any(), any<Int>()) }
        verify(exactly = 1) { mockCursor.close() }
    }
}
