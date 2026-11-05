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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit tests for WorkoutRepository.splitLapAtSample (TST-UI-449.2 / REQ-UI-449).
 */
class WorkoutRepositoryLapSplitTest {

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
    fun `splitLapAtSample successfully persists split and reloads workout data`() = runBlocking {
        val workoutId = 100L
        val baseFileName = "workout_100"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val samples = listOf(
            createPoint(10, 0, 0, 0.0, 1),
            createPoint(11, 1, 60, 300.0, 1),
            createPoint(12, 2, 120, 600.0, 1),
            createPoint(13, 3, 180, 900.0, 1)
        )

        // Spy repository getCropSamplePoints
        val repoSpy = spyk(repository)
        coEvery { repoSpy.getCropSamplePoints(workoutId) } returns samples
        coEvery { repoSpy.reloadWorkoutData(workoutId) } just Runs

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(id = 1L, workoutId = workoutId, lapNr = 1L, timeTotalS = 180, distanceTotalM = 900.0, speedAverageMps = 5.0)
        )
        every { mockSamplesDb.incrementLapNumbersFromSample(baseFileName, 12L) } returns 2
        every { mockLapsDb.replaceCroppedLaps(workoutId, any()) } just Runs

        val result = repoSpy.splitLapAtSample(workoutId, splitSampleIndex = 2)

        assertTrue(result.isSuccess)
        val newLaps = result.getOrNull()
        assertNotNull(newLaps)
        assertEquals(2, newLaps!!.size)
        assertEquals(1L, newLaps[0].lapNr)
        assertEquals(2L, newLaps[1].lapNr)

        verify { mockSamplesDb.incrementLapNumbersFromSample(baseFileName, 12L) }
        verify { mockLapsDb.replaceCroppedLaps(workoutId, any()) }
        coVerify { repoSpy.reloadWorkoutData(workoutId) }
    }

    @Test
    fun `splitLapAtSample returns failure on invalid split index`() = runBlocking {
        val workoutId = 100L
        val baseFileName = "workout_100"

        every { mockSummariesDb.getBaseFileName(workoutId) } returns baseFileName
        every { mockSamplesDb.existsTable(baseFileName) } returns true

        val samples = listOf(
            createPoint(10, 0, 0, 0.0, 1),
            createPoint(11, 1, 60, 300.0, 1)
        )

        val repoSpy = spyk(repository)
        coEvery { repoSpy.getCropSamplePoints(workoutId) } returns samples

        // Index 0 is invalid (lap start)
        val result = repoSpy.splitLapAtSample(workoutId, splitSampleIndex = 0)
        assertTrue(result.isFailure)

        // Index out of bounds
        val resultOob = repoSpy.splitLapAtSample(workoutId, splitSampleIndex = 5)
        assertTrue(resultOob.isFailure)
    }
}
