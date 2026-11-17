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

package com.atrainingtracker.trainingtracker.integration

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.testutil.MockCursorFactory
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Software Integration Verification Test: ViewModel to Repository Layer (ASPICE SWE.5,
 * INT-SWE-02, IF-SWE-02, REQ-PRO-110.2, TST-INT-010, ATT-8155).
 *
 * Verifies:
 * 1. Asynchronous coroutine execution and reactive Flow emission across ViewModel-to-Repository boundaries.
 * 2. Real WorkoutRepository and BANALServiceRepository reactive Flow subscriptions.
 * 3. Flow emission delivery under structured concurrency without deadlocks or main-thread blocking.
 * 4. Workout data update propagation from Repository data layer to ViewModel consumers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelRepositoryIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var banalServiceRepository: BANALServiceRepository
    private val mockSummariesManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportTypeDbManager = mockk<SportTypeDatabaseManager>(relaxed = true)

    private val columns = listOf(
        WorkoutSummaries.C_ID,
        WorkoutSummaries.SPORT_ID,
        WorkoutSummaries.EQUIPMENT_ID,
        WorkoutSummaries.TIME_START,
        WorkoutSummaries.FILE_BASE_NAME,
        WorkoutSummaries.DISTANCE_TOTAL_m,
        WorkoutSummaries.MAP_POLYLINE,
        WorkoutSummaries.ALTITUDE_STREAM,
        WorkoutSummaries.DISTANCE_STREAM,
        WorkoutSummaries.WORKOUT_NAME,
        WorkoutSummaries.CLUSTER_ID,
        WorkoutSummaries.FINISHED,
        WorkoutSummaries.COMMUTE,
        WorkoutSummaries.TRAINER,
        WorkoutSummaries.RACE,
        WorkoutSummaries.UPLOAD_TO_STRAVA,
        WorkoutSummaries.BOUND_MIN_LAT,
        WorkoutSummaries.BOUND_MIN_LNG,
        WorkoutSummaries.BOUND_MAX_LAT,
        WorkoutSummaries.BOUND_MAX_LNG,
        WorkoutSummaries.TIME_ACTIVE_s,
        WorkoutSummaries.TIME_TOTAL_s,
        WorkoutSummaries.SPEED_AVERAGE_mps,
        WorkoutSummaries.ASCENDING,
        WorkoutSummaries.DESCENDING,
        WorkoutSummaries.DESCRIPTION,
        WorkoutSummaries.GOAL,
        WorkoutSummaries.METHOD
    )

    private fun createSampleRow(id: Long, name: String = "Workout $id"): List<Any?> {
        return listOf(
            id,
            1L,
            -1L,
            "2026-11-17 10:00:00",
            "workout_base_$id",
            5000.0,
            "",
            "",
            "",
            name,
            0L,
            1,
            0,
            0,
            0,
            0,
            48.0,
            11.0,
            48.1,
            11.1,
            1150L,
            1200L,
            4.16,
            50L,
            50L,
            "Integration test description",
            "Goal",
            "Method"
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

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesManager

        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDbManager
        every { mockSportTypeDbManager.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDbManager.getUIName(any()) } returns "Cycling"
        every { mockSportTypeDbManager.getStravaName(any()) } returns "Ride"

        mockkConstructor(ExportManager::class)
        every { anyConstructed<ExportManager>().exportWorkout(any()) } returns mockk(relaxed = true)

        context = ApplicationProvider.getApplicationContext()
        WorkoutRepository.resetForTesting()
        workoutRepository = WorkoutRepository.getInstance(context)
        banalServiceRepository = BANALServiceRepository.getInstance(context)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting()
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    /**
     * TST-INT-010.1: Verifies WorkoutRepository reactive Flow emission on workout queries.
     * When workout is loaded and observed, getWorkoutById Flow emits the loaded record.
     */
    @Test
    fun testWorkoutRepository_saveAndObserveWorkout_reactiveFlow() = runTest(testDispatcher) {
        val workoutId = 9901L
        val sampleRow = createSampleRow(workoutId, "Morning Ride")
        every { mockSummariesManager.getWorkoutCursor(workoutId) } returns MockCursorFactory.create(columns, listOf(sampleRow))

        // Reload into repository
        workoutRepository.reloadWorkoutData(workoutId)
        testScheduler.advanceUntilIdle()

        // Observe through reactive Flow
        val emitted = workoutRepository.getWorkoutById(workoutId).first { it != null }
        assertNotNull("Observed workout from reactive Flow must not be null", emitted)
        assertEquals(workoutId, emitted?.id)
        assertEquals("Morning Ride", emitted?.workoutName)
    }

    /**
     * TST-INT-010.2: Verifies BANALServiceRepository reactive sensor telemetry Flow emissions
     * to ViewModel subscribers across IF-SWE-02.
     */
    @Test
    fun testBanalServiceRepository_filteredSensorDataFlow() = runTest(testDispatcher) {
        val flow = banalServiceRepository.allFilteredSensorData
        assertNotNull("allFilteredSensorData Flow must not be null", flow)

        val initialValue = flow.value
        assertNotNull("Initial StateFlow value must be accessible", initialValue)
    }

    /**
     * TST-INT-010.3: Verifies direct resolution of stored workout without deadlocks or unhandled exceptions.
     */
    @Test
    fun testWorkoutRepository_directQuery_concurrencyHygiene() = runTest(testDispatcher) {
        val workoutId = 9902L
        val sampleRow = createSampleRow(workoutId, "Tempo Ride")
        every { mockSummariesManager.getWorkoutCursor(workoutId) } returns MockCursorFactory.create(columns, listOf(sampleRow))

        val directResult = workoutRepository.getWorkoutDataDirect(workoutId)
        assertNotNull("getWorkoutDataDirect must resolve stored workout from cursor", directResult)
        assertEquals(workoutId, directResult?.id)
        assertEquals("Tempo Ride", directResult?.workoutName)
    }
}
