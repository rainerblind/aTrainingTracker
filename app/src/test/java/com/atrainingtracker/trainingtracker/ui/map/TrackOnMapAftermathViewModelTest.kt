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

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying [TrackOnMapAftermathViewModel] coroutine concurrency:
 * - Cancelling in-flight loadJob on rapid workout switches
 * (REQ-UI-427.1, TST-UI-392.1, ATT-4018).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackOnMapAftermathViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkObject(WorkoutRepository.Companion)
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(any(), any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getWorkoutTelemetryPoints(any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getWorkoutMarkers(any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getHeartRateZoneDistribution(any(), any()) } returns null
        coEvery { mockWorkoutRepo.getPowerZoneDistribution(any()) } returns null

        mockkObject(SegmentsRepository.Companion)
        val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)
        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepo
        every { mockSegmentsRepo.allSegmentsWithPath } returns MutableStateFlow(emptyList())

        mockkObject(RoutesRepository.Companion)
        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())

        mockkConstructor(MyPreferenceManager::class)
        every { anyConstructed<MyPreferenceManager>().enabledTrackTypesFlow } returns MutableStateFlow(emptySet())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createWorkout(id: Long): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Test Workout $id",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-10-21",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 1000L,
            totalTimeSec = 1100L,
            avgSpeedMps = 5.0,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 200.0,
            maxAltitude = 250.0,
            startLatLng = null,
            endLatLng = null,
            maxDisplacementLatLng = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun loadAftermathData_cancelsPreviousInFlightJob() = runTest(testDispatcher) {
        val viewModel = TrackOnMapAftermathViewModel(mockApplication, ioDispatcher = testDispatcher)

        val workout1 = createWorkout(101L)
        val workout2 = createWorkout(102L)

        viewModel.loadAftermathData(workout1)
        val job1 = viewModel.currentLoadJob
        assertNotNull("First loadJob must not be null", job1)
        assertTrue("First loadJob must be active", job1!!.isActive)

        viewModel.loadAftermathData(workout2)
        assertTrue("Previous loadJob must be cancelled immediately", job1.isCancelled)
        val job2 = viewModel.currentLoadJob
        assertNotNull("Second loadJob must not be null", job2)
        assertTrue("Second loadJob must be active", job2!!.isActive)

        advanceUntilIdle()
    }
}
