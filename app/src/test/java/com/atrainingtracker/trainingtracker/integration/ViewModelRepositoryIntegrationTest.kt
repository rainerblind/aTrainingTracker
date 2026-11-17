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
import android.content.ContentValues
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModel
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
 * 1. Real SQLite WorkoutSummariesDatabaseManager -> WorkoutRepository -> WorkoutSummariesViewModel reactive Flow emissions.
 * 2. Real BANALServiceRepository StateFlow emissions without deadlocks or main-thread blocking.
 * 3. Direct SQLite workout resolution via WorkoutRepository.getWorkoutDataDirect() backed by real SQLite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelRepositoryIntegrationTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var banalServiceRepository: BANALServiceRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        application = ApplicationProvider.getApplicationContext()
        cleanupDatabases()

        val appField = TrainingApplication::class.java.getDeclaredField("cAppContext")
        appField.isAccessible = true
        appField.set(null, application)

        val prefField = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        prefField.isAccessible = true
        prefField.set(null, PreferenceManager.getDefaultSharedPreferences(application))

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        WorkoutRepository.resetForTesting(null)
        workoutRepository = WorkoutRepository.getInstance(application)
        banalServiceRepository = BANALServiceRepository.getInstance(application)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        cleanupDatabases()
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun cleanupDatabases() {
        listOf(
            "WorkoutSummaries.db",
            "WorkoutSamples.db",
            "SportTypes.db",
            "Equipment.db",
            "KnownLocations.db",
            "WorkoutCluster.db",
            "Climbs.db"
        ).forEach { application.deleteDatabase(it) }
    }

    private fun insertRealWorkoutRow(
        workoutName: String,
        fileBaseName: String,
        finished: Int = 0,
        distanceM: Double = 5200.0
    ): Long {
        val db = WorkoutSummariesDatabaseManager.getInstance(application).database!!
        val values = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, workoutName)
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.EQUIPMENT_ID, -1L)
            put(WorkoutSummaries.TIME_START, "2026-11-17 08:30:00")
            put(WorkoutSummaries.TIME_ACTIVE_s, 900)
            put(WorkoutSummaries.TIME_TOTAL_s, 920)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, distanceM)
            put(WorkoutSummaries.SPEED_AVERAGE_mps, 5.77)
            put(WorkoutSummaries.FINISHED, finished)
            put(WorkoutSummaries.COMMUTE, 0)
            put(WorkoutSummaries.TRAINER, 0)
            put(WorkoutSummaries.RACE, 0)
            put(WorkoutSummaries.UPLOAD_TO_STRAVA, -1)
            put(WorkoutSummaries.ASCENDING, 45)
            put(WorkoutSummaries.DESCENDING, 40)
            put(WorkoutSummaries.DESCRIPTION, "Real SQLite integration test")
            put(WorkoutSummaries.GOAL, "Endurance")
            put(WorkoutSummaries.METHOD, "Steady")
        }
        return db.insertOrThrow(WorkoutSummaries.TABLE, null, values)
    }

    /**
     * TST-INT-010.1: Verifies real SQLite -> WorkoutRepository -> WorkoutSummariesViewModel
     * reactive Flow delivery on insert and subsequent update.
     */
    @Test
    fun testWorkoutSummariesViewModel_observesRealSqliteFlowEmissions() = runBlocking {
        val workoutId = insertRealWorkoutRow(
            workoutName = "Morning Gravel Ride",
            fileBaseName = "workout_real_sqlite_1",
            finished = 0
        )
        val db = WorkoutSummariesDatabaseManager.getInstance(application).database!!
        assertEquals(
            1L,
            RealSqliteTestHelper.getRowCount(db, WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
        )

        val viewModel = WorkoutSummariesViewModel(application)
        workoutRepository.reloadWorkoutData(workoutId)

        val emittedInRepo = withTimeout(5_000L) {
            workoutRepository.getWorkoutById(workoutId).first { it != null }
        }
        val emittedInViewModel = withTimeout(5_000L) {
            viewModel.allWorkouts.first { list -> list.any { it.id == workoutId } }
        }.first { it.id == workoutId }

        assertNotNull(emittedInRepo)
        assertEquals("Morning Gravel Ride", emittedInRepo?.workoutName)
        assertEquals("Morning Gravel Ride", emittedInViewModel.workoutName)
        assertEquals(5200.0, emittedInViewModel.totalDistance, 0.01)

        // Update row in real SQLite and verify ViewModel StateFlow receives updated record
        val updateValues = ContentValues().apply {
            put(WorkoutSummaries.WORKOUT_NAME, "Morning Gravel Ride (Updated)")
        }
        db.update(
            WorkoutSummaries.TABLE,
            updateValues,
            "${WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        )
        workoutRepository.reloadWorkoutData(workoutId)

        val updatedInViewModel = withTimeout(5_000L) {
            viewModel.allWorkouts.first { list ->
                list.any { it.id == workoutId && it.workoutName == "Morning Gravel Ride (Updated)" }
            }
        }.first { it.id == workoutId }
        assertEquals("Morning Gravel Ride (Updated)", updatedInViewModel.workoutName)
    }

    /**
     * TST-INT-010.2: Verifies BANALServiceRepository reactive sensor telemetry and sport type Flows
     * expose non-null StateFlow streams across IF-SWE-02.
     */
    @Test
    fun testBanalServiceRepository_reactiveStateFlows() {
        val sensorFlow = banalServiceRepository.allFilteredSensorData
        assertNotNull("allFilteredSensorData StateFlow must not be null", sensorFlow)
        assertNotNull("Initial StateFlow value must be accessible", sensorFlow.value)

        banalServiceRepository.setUserSelectedSportType(BSportType.RUN)
        assertEquals(BSportType.RUN, banalServiceRepository.bSportType.value)
    }

    /**
     * TST-INT-010.3: Verifies direct resolution of a stored workout from real SQLite
     * without mocks or deadlocks.
     */
    @Test
    fun testWorkoutRepository_directQuery_realSqliteResolution() {
        val workoutId = insertRealWorkoutRow(
            workoutName = "Tempo Ride",
            fileBaseName = "workout_real_sqlite_2",
            finished = 0,
            distanceM = 12345.0
        )

        val directResult = workoutRepository.getWorkoutDataDirect(workoutId)
        assertNotNull("getWorkoutDataDirect must resolve stored workout from real SQLite", directResult)
        assertEquals(workoutId, directResult?.id)
        assertEquals("Tempo Ride", directResult?.workoutName)
        assertEquals(12345.0, directResult?.totalDistance ?: 0.0, 0.01)
        assertTrue("Repository cache must remain consistent", workoutRepository.hasUnfinishedWorkout())
    }
}
