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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.app.Application
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Automated unit test suite verifying reactive workout filtering and sorting interplay
 * in [WorkoutSummariesViewModel] (REQ-UI-132, TST-UI-085).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummariesViewModelFilterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)

    private val allWorkoutsFlow = MutableStateFlow<List<WorkoutData>>(emptyList())

    private fun createWorkout(
        id: Long,
        workoutName: String,
        sportId: Long,
        sportName: String,
        bSportType: BSportType,
        year: Int,
        totalDistance: Double,
        activeTimeSec: Long,
        commute: Boolean = false,
        trainer: Boolean = false
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = workoutName,
            sportId = sportId,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = id * 1000L,
            formattedDate = "$year-01-01",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(year, 1, 1, 10, 0),
            equipmentName = "Equipment $id",
            equipmentId = id + 100,
            commute = commute,
            trainer = trainer,
            mapPolyline = if (trainer) "" else "polyline_$id",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = totalDistance,
            maxDisplacement = null,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec,
            avgSpeedMps = 10.0,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = 50.0,
            maxAltitude = 150.0,
            description = "Description for $workoutName",
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkConstructor(MyPreferenceManager::class)
        every { anyConstructed<MyPreferenceManager>().workoutFilterCriteriaFlow } returns flowOf(WorkoutFilterCriteria())
        coEvery { anyConstructed<MyPreferenceManager>().setWorkoutFilterCriteria(any()) } just Runs
        every { anyConstructed<MyPreferenceManager>().clearWorkoutFilterCriteria() } just Runs
        every { anyConstructed<MyPreferenceManager>().isCompactViewFlow } returns flowOf(false)

        every { mockWorkoutRepo.allWorkouts } returns allWorkoutsFlow
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testWorkoutsFlow_UnfilteredCombinesWithSortOrder() = runTest(testDispatcher) {
        val w1 = createWorkout(1L, "Ride A", 10L, "Road Cycling", BSportType.BIKE, 2024, 50000.0, 3600L)
        val w2 = createWorkout(2L, "Ride B", 10L, "Road Cycling", BSportType.BIKE, 2025, 75000.0, 7200L)
        allWorkoutsFlow.value = listOf(w1, w2)

        val viewModel = WorkoutSummariesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect()
        }
        advanceUntilIdle()

        // Default sort is DATE descending (w2 has higher startTimeS than w1)
        val currentWorkouts = viewModel.workouts.value
        assertEquals(2, currentWorkouts.size)
        assertEquals(2L, currentWorkouts[0].id)
        assertEquals(1L, currentWorkouts[1].id)
    }

    @Test
    fun testWorkoutsFlow_FilteredByYear() = runTest(testDispatcher) {
        val w1 = createWorkout(1L, "Ride 2024", 10L, "Road Cycling", BSportType.BIKE, 2024, 50000.0, 3600L)
        val w2 = createWorkout(2L, "Ride 2025", 10L, "Road Cycling", BSportType.BIKE, 2025, 75000.0, 7200L)
        allWorkoutsFlow.value = listOf(w1, w2)

        val viewModel = WorkoutSummariesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect()
        }
        advanceUntilIdle()

        // Apply Year 2024 filter
        viewModel.setFilterCriteria(WorkoutFilterCriteria(year = 2024))
        advanceUntilIdle()

        val filtered = viewModel.workouts.value
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
    }

    @Test
    fun testWorkoutsFlow_FilteredBySearchQuery() = runTest(testDispatcher) {
        val w1 = createWorkout(1L, "Alps Mountain Climb", 10L, "Road Cycling", BSportType.BIKE, 2024, 50000.0, 3600L)
        val w2 = createWorkout(2L, "Flat Lake Loop", 10L, "Road Cycling", BSportType.BIKE, 2025, 75000.0, 7200L)
        allWorkoutsFlow.value = listOf(w1, w2)

        val viewModel = WorkoutSummariesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect()
        }
        advanceUntilIdle()

        // Search for "alps"
        viewModel.setFilterCriteria(WorkoutFilterCriteria(query = "alps"))
        advanceUntilIdle()

        val filtered = viewModel.workouts.value
        assertEquals(1, filtered.size)
        assertEquals("Alps Mountain Climb", filtered[0].workoutName)
    }

    @Test
    fun testWorkoutsFlow_ClearFilterRestoresAllWorkouts() = runTest(testDispatcher) {
        val w1 = createWorkout(1L, "Ride A", 10L, "Road Cycling", BSportType.BIKE, 2024, 50000.0, 3600L)
        val w2 = createWorkout(2L, "Run B", 20L, "Running", BSportType.RUN, 2025, 10000.0, 3000L)
        allWorkoutsFlow.value = listOf(w1, w2)

        val viewModel = WorkoutSummariesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect()
        }
        advanceUntilIdle()

        viewModel.setFilterCriteria(WorkoutFilterCriteria(sportTypeId = 10L))
        advanceUntilIdle()
        assertEquals(1, viewModel.workouts.value.size)

        // Clear all filters
        viewModel.clearFilterCriteria()
        advanceUntilIdle()
        assertEquals(2, viewModel.workouts.value.size)
        assertTrue(viewModel.filterCriteria.value.isEmpty)
    }

    @Test
    fun testWorkoutsFlow_SortOrderInterplayWithFilter() = runTest(testDispatcher) {
        val w1 = createWorkout(1L, "Short Ride", 10L, "Road Cycling", BSportType.BIKE, 2025, 30000.0, 1800L)
        val w2 = createWorkout(2L, "Long Ride", 10L, "Road Cycling", BSportType.BIKE, 2025, 100000.0, 10800L)
        val w3 = createWorkout(3L, "Medium Ride", 10L, "Road Cycling", BSportType.BIKE, 2024, 60000.0, 5400L)
        allWorkoutsFlow.value = listOf(w1, w2, w3)

        val viewModel = WorkoutSummariesViewModel(mockApplication)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect()
        }
        advanceUntilIdle()

        // Filter for 2025 (w1 and w2)
        viewModel.setFilterCriteria(WorkoutFilterCriteria(year = 2025))
        advanceUntilIdle()

        // Sort by distance descending
        viewModel.setSortOrder(WorkoutSortOrder.WORKOUT_DISTANCE)
        advanceUntilIdle()

        val result = viewModel.workouts.value
        assertEquals(2, result.size)
        assertEquals("Long Ride", result[0].workoutName)
        assertEquals("Short Ride", result[1].workoutName)
    }
}
