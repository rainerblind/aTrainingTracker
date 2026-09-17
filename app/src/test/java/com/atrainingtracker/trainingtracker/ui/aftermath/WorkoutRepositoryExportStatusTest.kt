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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.time.LocalDateTime

/**
 * Unit tests verifying WorkoutRepository.reloadExportStatusesFor updates in-memory workoutName
 * when refreshed in WorkoutSummaries database (ATT-902).
 */
class WorkoutRepositoryExportStatusTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockkConstructor(StravaUploadDbHelper::class)
        every { anyConstructed<StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(ExportStatusDataProvider::class)
        every { anyConstructed<ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkStatic(WorkoutSummariesDatabaseManager::class)
        unmockkAll()
    }

    private fun seedWorkouts(workouts: List<WorkoutData>) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = workouts
    }

    private fun createDummyWorkout(workoutId: Long, fileBaseName: String, workoutName: String): WorkoutData {
        return WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = fileBaseName,
            workoutName = workoutName,
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-09-11",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 9, 11, 10, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = null,
            activeTimeSec = 1500L,
            totalTimeSec = 1600L,
            avgSpeedMps = 3.33,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            extremaRows = emptyList(),
            laps = emptyList(),
            exportStatuses = emptyList()
        )
    }

    @Test
    fun testReloadExportStatuses_refreshesWorkoutNameFromDatabase() = runBlocking {
        val workout = createDummyWorkout(101L, "2026_09_11_10_00_00", "2026_09_11_10_00_00")
        seedWorkouts(listOf(workout))

        every {
            mockSummariesDb.getString(101L, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
        } returns "Strava Lunch Run"

        val method: Method = WorkoutRepository::class.java.getDeclaredMethod("reloadExportStatusesFor", String::class.java)
        method.isAccessible = true
        method.invoke(repository, "2026_09_11_10_00_00")

        var attempts = 0
        while (repository.allWorkouts.value.first().workoutName != "Strava Lunch Run" && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Strava Lunch Run", repository.allWorkouts.value.first().workoutName)
    }
}
