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
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.time.LocalDateTime

/**
 * Unit tests verifying WorkoutRepository.updateLapDetails (REQ-UI-142, TST-UI-095, ATT-511).
 * Verifies that lap edits are persisted to LapsDatabaseManager on Dispatchers.IO
 * and atomically updated in the in-memory cache so UI updates reactively without reloading.
 */
class WorkoutRepositoryLapUpdateTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
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

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb
        every { mockLapsDb.updateLapDetails(any(), any(), any(), any()) } returns true

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = constructor.newInstance(mockApplication)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkStatic(LapsDatabaseManager::class)
        unmockkAll()
    }

    private fun seedWorkouts(workouts: List<WorkoutData>) {
        val field: Field = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = workouts
    }

    private fun createDummyWorkout(workoutId: Long, laps: List<LapData>): WorkoutData {
        return WorkoutData(
            id = workoutId,
            finished = true,
            fileBaseName = "workout_$workoutId",
            workoutName = "Morning Run",
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
            laps = laps,
            exportStatuses = emptyList()
        )
    }

    @Test
    fun testUpdateLapDetails_persistsToDbAndUpdatesInMemoryCache() = runBlocking {
        val lap1 = LapData(id = 1L, workoutId = 101L, lapNr = 1L, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33)
        val lap2 = LapData(id = 2L, workoutId = 101L, lapNr = 2L, timeTotalS = 250, distanceTotalM = 1000.0, speedAverageMps = 4.0)
        val lap3 = LapData(id = 3L, workoutId = 101L, lapNr = 3L, timeTotalS = 400, distanceTotalM = 1000.0, speedAverageMps = 2.5)

        val workout = createDummyWorkout(101L, listOf(lap1, lap2, lap3))
        seedWorkouts(listOf(workout))

        // Act: Update Lap 2 with name and description
        repository.updateLapDetails(
            workoutId = 101L,
            lapNr = 2L,
            name = "Fast Interval",
            description = "High intensity effort at 4:10 pace"
        )

        // Verify DB persistence
        verify(exactly = 1) {
            mockLapsDb.updateLapDetails(101L, 2L, "Fast Interval", "High intensity effort at 4:10 pace")
        }

        // Verify in-memory state updated reactively
        val updatedWorkouts = repository.allWorkouts.value
        assertEquals(1, updatedWorkouts.size)

        val updatedWorkout = updatedWorkouts.first()
        val updatedLaps = updatedWorkout.laps

        assertEquals(3, updatedLaps.size)
        // Lap 1 is untouched
        assertEquals(1L, updatedLaps[0].lapNr)
        assertNull(updatedLaps[0].name)
        assertNull(updatedLaps[0].description)

        // Lap 2 is updated
        assertEquals(2L, updatedLaps[1].lapNr)
        assertEquals("Fast Interval", updatedLaps[1].name)
        assertEquals("High intensity effort at 4:10 pace", updatedLaps[1].description)

        // Lap 3 is untouched
        assertEquals(3L, updatedLaps[2].lapNr)
        assertNull(updatedLaps[2].name)
        assertNull(updatedLaps[2].description)
    }

    @Test
    fun testUpdateLapDetails_clearDetails_updatesToNull() = runBlocking {
        val lap1 = LapData(
            id = 1L,
            workoutId = 202L,
            lapNr = 1L,
            timeTotalS = 300,
            distanceTotalM = 1000.0,
            speedAverageMps = 3.33,
            name = "Warm-up",
            description = "Easy jog"
        )

        val workout = createDummyWorkout(202L, listOf(lap1))
        seedWorkouts(listOf(workout))

        // Act: Clear name and description (null)
        repository.updateLapDetails(
            workoutId = 202L,
            lapNr = 1L,
            name = null,
            description = null
        )

        verify(exactly = 1) {
            mockLapsDb.updateLapDetails(202L, 1L, null, null)
        }

        val updatedLap = repository.allWorkouts.value.first().laps.first()
        assertNull(updatedLap.name)
        assertNull(updatedLap.description)
    }
}
