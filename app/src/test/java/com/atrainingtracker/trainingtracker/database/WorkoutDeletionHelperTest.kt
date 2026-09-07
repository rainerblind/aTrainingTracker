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

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automated unit tests verifying bulk workout deletion, cascade database purge, and
 * forensic fileBaseName sequencing (REQ-DAT-010, TST-DAT-004, ATT-296).
 */
class WorkoutDeletionHelperTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockLapsManager = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSamplesManager = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockExportStatusRepo = mockk<ExportStatusDatabaseManager>(relaxed = true)

    private lateinit var deletionHelper: WorkoutDeletionHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        every { mockContext.applicationContext } returns mockContext

        deletionHelper = WorkoutDeletionHelper(
            mockContext,
            mockSummariesManager,
            mockLapsManager,
            mockSamplesManager,
            mockExportStatusRepo
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that getBaseFileName is queried BEFORE deleting the summary record,
     * ensuring high-frequency sample tables and export statuses are dropped and not orphaned.
     */
    @Test
    fun testDeleteWorkout_queriesBaseFileNameBeforeDroppingSummary_dropsSampleAndExportTables() {
        val workoutId = 123L
        val baseFileName = "2026_09_07_123"

        every { mockSummariesManager.getBaseFileName(workoutId) } returns baseFileName
        every { mockSummariesManager.deleteWorkout(workoutId) } returns true

        val success = deletionHelper.deleteWorkout(workoutId)

        assertTrue("deleteWorkout should return true", success)

        // Verify strict execution ordering: getBaseFileName MUST precede deleteWorkout on summaries manager
        verifyOrder {
            mockSummariesManager.getBaseFileName(workoutId)
            mockSummariesManager.deleteWorkout(workoutId)
            mockLapsManager.deleteWorkout(workoutId)
            mockSamplesManager.deleteWorkout(baseFileName)
            mockExportStatusRepo.deleteWorkout(baseFileName)
        }
    }

    /**
     * Verifies that deleteWorkout handles missing baseFileName gracefully without throwing exceptions.
     */
    @Test
    fun testDeleteWorkout_nullBaseFileName_doesNotCrash() {
        val workoutId = 456L

        every { mockSummariesManager.getBaseFileName(workoutId) } returns null
        every { mockSummariesManager.deleteWorkout(workoutId) } returns true

        val success = deletionHelper.deleteWorkout(workoutId)

        assertTrue(success)
        verify { mockSummariesManager.deleteWorkout(workoutId) }
        verify { mockLapsManager.deleteWorkout(workoutId) }
        verify(exactly = 0) { mockSamplesManager.deleteWorkout(any()) }
        verify(exactly = 0) { mockExportStatusRepo.deleteWorkout(any()) }
    }

    /**
     * Verifies that deleteOldWorkouts queries matching old workout IDs, invokes progressCallback
     * for each session, and completely purges all related tables.
     */
    @Test
    fun testDeleteOldWorkouts_iteratesAndPurgesAllMatchingSessions() {
        val daysToKeep = 365
        val oldIds = listOf(101L, 102L)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(mockContext) } returns mockSummariesManager
        every { mockSummariesManager.getOldWorkouts(daysToKeep) } returns oldIds

        every { mockSummariesManager.getBaseFileName(101L) } returns "file_101"
        every { mockSummariesManager.getBaseFileName(102L) } returns "file_102"
        every { mockSummariesManager.deleteWorkout(any()) } returns true

        val reportedProgressIds = mutableListOf<Long>()
        val success = deletionHelper.deleteOldWorkouts(daysToKeep) { reportedId ->
            reportedProgressIds.add(reportedId)
        }

        assertTrue(success)
        assertEquals("Both old workout IDs should be reported via progressCallback", listOf(101L, 102L), reportedProgressIds)

        // Verify both workouts were purged across summaries, laps, samples, and export status
        verify(exactly = 1) { mockSummariesManager.deleteWorkout(101L) }
        verify(exactly = 1) { mockSummariesManager.deleteWorkout(102L) }
        verify(exactly = 1) { mockSamplesManager.deleteWorkout("file_101") }
        verify(exactly = 1) { mockSamplesManager.deleteWorkout("file_102") }
        verify(exactly = 1) { mockExportStatusRepo.deleteWorkout("file_101") }
        verify(exactly = 1) { mockExportStatusRepo.deleteWorkout("file_102") }
    }

    /**
     * Verifies that deleteOldWorkouts performs zero deletion operations when no sessions exceed threshold.
     */
    @Test
    fun testDeleteOldWorkouts_emptyOldWorkouts_noDeletions() {
        val daysToKeep = 365

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(mockContext) } returns mockSummariesManager
        every { mockSummariesManager.getOldWorkouts(daysToKeep) } returns emptyList()

        var callbackInvoked = false
        val success = deletionHelper.deleteOldWorkouts(daysToKeep) {
            callbackInvoked = true
        }

        assertTrue(success)
        assertTrue("Callback should not be invoked when list is empty", !callbackInvoked)
        verify(exactly = 0) { mockSummariesManager.deleteWorkout(any()) }
        verify(exactly = 0) { mockSamplesManager.deleteWorkout(any()) }
    }
}
