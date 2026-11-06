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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WorkoutRepository.deleteLap and mergeAdjacentLaps (TST-UI-454.2 / REQ-UI-454.3).
 */
class WorkoutRepositoryLapDeleteTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun `deleteLap successfully merges adjacent laps and persists updated sequence`() = runBlocking {
        val workoutId = 100L
        val baseFileName = "workout_100"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val existingLaps = listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, name = "Lap 1"),
            LapData(id = 2L, workoutId = workoutId, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0, name = "Lap 2"),
            LapData(id = 3L, workoutId = workoutId, lapNr = 3L, timeTotalS = 300, distanceTotalM = 1500.0, speedAverageMps = 5.0, name = "Lap 3")
        )

        val repoSpy = spyk(repository)
        coEvery { repoSpy.reloadWorkoutData(workoutId) } just Runs

        every { mockLapsDb.getLaps(workoutId) } returns existingLaps
        every { mockSamplesDb.decrementLapNumbersAbove(baseFileName, 1L) } returns 15
        every { mockLapsDb.replaceCroppedLaps(workoutId, any()) } just Runs

        // Merge boundary 1 (Lap 1 + Lap 2)
        val result = repoSpy.deleteLap(workoutId, removedBoundaryLapNr = 1L)

        assertTrue(result.isSuccess)
        val newLaps = result.getOrNull()
        assertNotNull(newLaps)
        assertEquals(2, newLaps!!.size)
        assertEquals(1L, newLaps[0].lapNr)
        assertEquals(300, newLaps[0].timeTotalS)
        assertEquals(1500.0, newLaps[0].distanceTotalM, 1e-4)
        assertEquals(2L, newLaps[1].lapNr)
        assertEquals(300, newLaps[1].timeTotalS)

        verify { mockSamplesDb.decrementLapNumbersAbove(baseFileName, 1L) }
        verify { mockLapsDb.replaceCroppedLaps(workoutId, any()) }
        coVerify { repoSpy.reloadWorkoutData(workoutId) }
    }

    @Test
    fun `deleteLap rejects deletion when workout has less than 2 laps`() = runBlocking {
        val workoutId = 100L
        val baseFileName = "workout_100"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0)
        )

        val result = repository.deleteLap(workoutId, removedBoundaryLapNr = 1L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `deleteLap rejects invalid boundary lap numbers`() = runBlocking {
        val workoutId = 100L
        val baseFileName = "workout_100"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0),
            LapData(id = 2L, workoutId = workoutId, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0)
        )

        // Boundary 0 is invalid
        val resZero = repository.deleteLap(workoutId, removedBoundaryLapNr = 0L)
        assertTrue(resZero.isFailure)

        // Boundary 2 is invalid (only 2 laps, so boundary can only be 1)
        val resTwo = repository.deleteLap(workoutId, removedBoundaryLapNr = 2L)
        assertTrue(resTwo.isFailure)
    }
}
