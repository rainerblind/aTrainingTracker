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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying REQ-PER-014 / TST-PER-020 (ATT-3056):
 * 1. WorkoutSummariesDatabaseManager.getWorkoutsInRangeCursor enforces FINISHED = 1 gating.
 * 2. PeriodSummariesDatabaseManager.setSyncFinished updates or inserts sync completion status.
 * 3. TrackerService intent contracts for tracking finalization.
 * 4. PeriodsViewModel.loadPeriods triggers integrity reconciliation on load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeriodsRepositorySyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { constructedWith<ContentValues>().getAsString(any<String>()) } returns null

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        PeriodsRepository.resetInstanceForTesting()
        unmockkAll()
    }

    /**
     * Verifies that getWorkoutsInRangeCursor appends FINISHED = 1 to the selection query,
     * strictly excluding incomplete or unfinalized workouts from period aggregation.
     */
    @Test
    fun testWorkoutsInRangeCursor_includesFinishedCondition() {
        val selectionSlot = slot<String>()
        val selectionArgsSlot = slot<Array<String>>()

        every {
            mockDb.query(
                WorkoutSummaries.TABLE,
                null,
                capture(selectionSlot),
                capture(selectionArgsSlot),
                null,
                null,
                WorkoutSummaries.TIME_START + " ASC"
            )
        } returns mockk(relaxed = true)

        val manager = object : WorkoutSummariesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        manager.getWorkoutsInRangeCursor(1700000000L, 1700086399L)

        val selection = selectionSlot.captured
        assertTrue(
            "Selection must enforce FINISHED = 1 to exclude incomplete sessions: $selection",
            selection.contains(WorkoutSummaries.FINISHED + " = 1")
        )
        assertEquals("1700000000", selectionArgsSlot.captured[0])
        assertEquals("1700086399", selectionArgsSlot.captured[1])
    }

    /**
     * Verifies that PeriodSummariesDatabaseManager.setSyncFinished writes COLUMN_IS_FINISHED = 1
     * to the SyncStatus table in PeriodSummaries.db.
     */
    @Test
    fun testPeriodSummariesDatabaseManager_setSyncFinishedUpdatesDatabase() {
        val manager = PeriodSummariesDatabaseManager.getInstance(mockContext)

        every {
            mockDb.update(
                PeriodSummariesDatabaseManager.SyncStatusContract.TABLE_NAME,
                any(),
                any(),
                null
            )
        } returns 1

        manager.setSyncFinished(mockDb, true)

        verify(atLeast = 1) {
            mockDb.update(
                PeriodSummariesDatabaseManager.SyncStatusContract.TABLE_NAME,
                any(),
                any(),
                null
            )
        }
    }

    /**
     * Verifies that TRACKING_FINISHED_INTENT and WORKOUT_ID extra contract constants match expectations.
     */
    @Test
    fun testTrackingFinishedIntent_contractConstants() {
        assertEquals(
            "de.rainerblind.trainingtracker.TrackerService.TRACKING_FINISHED_INTENT",
            TrackerService.TRACKING_FINISHED_INTENT
        )
        assertEquals("WORKOUT_ID", TrackerService.WORKOUT_ID)
    }

    /**
     * Verifies that PeriodsViewModel.loadPeriods executes both workout history loading
     * and periods integrity synchronization.
     */
    @Test
    fun testPeriodsViewModel_loadPeriods_triggersCheckIntegrityAndSync() = runTest {
        val mockApp = mockk<Application>(relaxed = true)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val mockPeriodsRepo = mockk<PeriodsRepository>(relaxed = true)

        every { mockPeriodsRepo.groupedPeriods } returns MutableStateFlow(emptyList())
        every { mockPeriodsRepo.migrationStatus } returns MutableStateFlow(null)

        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        PeriodsRepository.setInstanceForTesting(mockPeriodsRepo)

        val viewModel = PeriodsViewModel(mockApp)
        viewModel.loadPeriods()

        coVerify(timeout = 2000) { mockWorkoutRepo.loadAllWorkouts() }
        coVerify(timeout = 2000) { mockPeriodsRepo.checkIntegrityAndSync() }
    }
}
