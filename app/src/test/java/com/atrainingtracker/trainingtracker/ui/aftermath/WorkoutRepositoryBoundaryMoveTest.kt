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
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WorkoutRepository.moveLapBoundary (TST-UI-450.2 / REQ-UI-450).
 */
class WorkoutRepositoryBoundaryMoveTest {

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

    private fun createPoint(rowId: Long, index: Int, timeS: Long, distM: Double, lapNr: Long) =
        CropSamplePoint(
            rowId = rowId,
            index = index,
            timeStr = "2026-11-05 10:00:$index",
            timeTotalS = timeS,
            timeActiveS = timeS,
            distanceTotalM = distM,
            latLng = LatLng(48.0 + index * 0.001, 11.0 + index * 0.001),
            altitude = 500.0,
            lapNr = lapNr
        )

    @Test
    fun `moveLapBoundary forward shift updates samples range and replaces laps in LapsDb`() = runBlocking {
        val workoutId = 200L
        val baseFileName = "workout_200"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        // Samples 0..2 are Lap 1 (rowIds 10..12), 3..5 are Lap 2 (rowIds 13..15)
        val samples = listOf(
            createPoint(10, 0, 0, 0.0, 1),
            createPoint(11, 1, 100, 500.0, 1),
            createPoint(12, 2, 200, 1000.0, 1),
            createPoint(13, 3, 300, 1500.0, 2),
            createPoint(14, 4, 400, 2000.0, 2),
            createPoint(15, 5, 500, 2500.0, 2)
        )

        val repoSpy = spyk(repository)
        coEvery { repoSpy.getCropSamplePoints(workoutId) } returns samples
        coEvery { repoSpy.reloadWorkoutData(workoutId) } just Runs

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0),
            LapData(id = 2L, workoutId = workoutId, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0)
        )
        every { mockSamplesDb.updateLapNumberForSampleRange(baseFileName, 13L, 13L, 1L) } returns 1
        every { mockLapsDb.replaceCroppedLaps(workoutId, any()) } just Runs

        // Shift boundary from 3 forward to 4:
        // Sample index 3 (rowId 13) changes from Lap 2 to Lap 1
        val result = repoSpy.moveLapBoundary(workoutId, boundaryLapNr = 1L, newBoundarySampleIndex = 4)

        assertTrue(result.isSuccess)
        val newLaps = result.getOrNull()
        assertNotNull(newLaps)
        assertEquals(2, newLaps!!.size)
        assertEquals(1L, newLaps[0].lapNr)
        assertEquals(2L, newLaps[1].lapNr)

        // Lap 1: samples 0..3 -> duration 300s, distance 1500m
        assertEquals(300, newLaps[0].timeTotalS)
        assertEquals(1500.0, newLaps[0].distanceTotalM, 1e-4)

        // Lap 2: samples 4..5 -> duration 100s, distance 500m
        assertEquals(100, newLaps[1].timeTotalS)
        assertEquals(500.0, newLaps[1].distanceTotalM, 1e-4)

        verify { mockSamplesDb.updateLapNumberForSampleRange(baseFileName, 13L, 13L, 1L) }
        verify { mockLapsDb.replaceCroppedLaps(workoutId, any()) }
        coVerify { repoSpy.reloadWorkoutData(workoutId) }
    }

    @Test
    fun `moveLapBoundary backward shift updates samples range and replaces laps in LapsDb`() = runBlocking {
        val workoutId = 200L
        val baseFileName = "workout_200"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        // Samples 0..2 are Lap 1 (rowIds 10..12), 3..5 are Lap 2 (rowIds 13..15)
        val samples = listOf(
            createPoint(10, 0, 0, 0.0, 1),
            createPoint(11, 1, 100, 500.0, 1),
            createPoint(12, 2, 200, 1000.0, 1),
            createPoint(13, 3, 300, 1500.0, 2),
            createPoint(14, 4, 400, 2000.0, 2),
            createPoint(15, 5, 500, 2500.0, 2)
        )

        val repoSpy = spyk(repository)
        coEvery { repoSpy.getCropSamplePoints(workoutId) } returns samples
        coEvery { repoSpy.reloadWorkoutData(workoutId) } just Runs

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0),
            LapData(id = 2L, workoutId = workoutId, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0)
        )
        // Shift boundary from 3 backward to 2:
        // Sample index 2 (rowId 12) changes from Lap 1 to Lap 2
        every { mockSamplesDb.updateLapNumberForSampleRange(baseFileName, 12L, 12L, 2L) } returns 1
        every { mockLapsDb.replaceCroppedLaps(workoutId, any()) } just Runs

        val result = repoSpy.moveLapBoundary(workoutId, boundaryLapNr = 1L, newBoundarySampleIndex = 2)

        assertTrue(result.isSuccess)
        val newLaps = result.getOrNull()
        assertNotNull(newLaps)
        assertEquals(2, newLaps!!.size)

        // Lap 1: samples 0..1 -> duration 100s, distance 500m
        assertEquals(100, newLaps[0].timeTotalS)
        assertEquals(500.0, newLaps[0].distanceTotalM, 1e-4)

        // Lap 2: samples 2..5 -> duration 300s, distance 1500m
        assertEquals(300, newLaps[1].timeTotalS)
        assertEquals(1500.0, newLaps[1].distanceTotalM, 1e-4)

        verify { mockSamplesDb.updateLapNumberForSampleRange(baseFileName, 12L, 12L, 2L) }
        verify { mockLapsDb.replaceCroppedLaps(workoutId, any()) }
        coVerify { repoSpy.reloadWorkoutData(workoutId) }
    }

    @Test
    fun `moveLapBoundary returns failure on empty samples or missing laps`() = runBlocking {
        val workoutId = 200L
        val baseFileName = "workout_200"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val repoSpy = spyk(repository)
        coEvery { repoSpy.getCropSamplePoints(workoutId) } returns emptyList()

        val result = repoSpy.moveLapBoundary(workoutId, boundaryLapNr = 1L, newBoundarySampleIndex = 2)
        assertTrue(result.isFailure)
    }
}
