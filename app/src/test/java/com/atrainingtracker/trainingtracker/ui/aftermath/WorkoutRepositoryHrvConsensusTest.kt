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
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvScatterIntensityMode
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdEstimator
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.ThresholdConfidence
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying [WorkoutRepository.getRecentHrvSessionThresholds]
 * filtering, telemetry evaluation, caching, and cache invalidation (REQ-PER-037.3, TST-PER-045).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepositoryHrvConsensusTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    private val nowEpochMs = 1731380000000L // arbitrary fixed time
    private val nowEpochS = nowEpochMs / 1000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } returns Unit

        mockkStatic(LocalBroadcastManager::class)
        val mockLbm = mockk<LocalBroadcastManager>(relaxed = true)
        every { LocalBroadcastManager.getInstance(any()) } returns mockLbm

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper>().getStravaActivityData(any()) } returns null

        mockkConstructor(com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider::class)
        every { anyConstructed<com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusDataProvider>().createGroupData(any(), any()) } returns mockk(relaxed = true)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        repository = spyk(WorkoutRepository.getInstance(mockApplication))
        coEvery { repository.reloadWorkoutData(any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    private fun createWorkout(id: Long, startTimeS: Long, finished: Boolean = true): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = startTimeS,
            formattedDate = "2026-11-12",
            formattedTime = "10:00:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 25000.0,
            maxDisplacement = 5000.0,
            activeTimeSec = 3600L,
            totalTimeSec = 3600L,
            avgSpeedMps = 7.0,
            ascentMeters = 200L,
            descentMeters = 200L,
            minAltitude = 100.0,
            maxAltitude = 300.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Ride"
        )
    }

    private fun createTelemetryPointsWithHrvAndPower(): List<PathPoint> {
        val points = mutableListOf<PathPoint>()
        // Generate ramp telemetry points in fresh window [600s, 3100s] where power spans 120W..320W and DFA alpha 1 spans 1.00..0.40
        for (i in 0..100) {
            val power = 120 + i * 2 // 120 to 320 W
            val dfa = 1.00 - (i / 100.0) * 0.60 // 1.00 down to 0.40
            points.add(
                PathPoint(
                    distance = i * 200.0,
                    latLng = com.google.android.gms.maps.model.LatLng(48.0, 11.0),
                    altitude = 100.0,
                    timeSec = 600L + i * 25L,
                    hr = 110 + i,
                    power = power,
                    speedMps = 8.0,
                    hrvDfaA1 = dfa
                )
            )
        }
        return points
    }

    @Test
    fun testGetRecentHrvSessionThresholds_filters30DayWindowAndExcludesCurrent() = runBlocking {
        val currentWorkoutId = 100L
        val recentWorkout1 = createWorkout(101L, startTimeS = nowEpochS - 5 * 86400L) // 5 days ago
        val recentWorkout2 = createWorkout(102L, startTimeS = nowEpochS - 15 * 86400L) // 15 days ago
        val oldWorkout = createWorkout(103L, startTimeS = nowEpochS - 40 * 86400L) // 40 days ago (outside 30-day window)
        val unfinishedWorkout = createWorkout(104L, startTimeS = nowEpochS - 2 * 86400L, finished = false) // unfinished
        val currentWorkout = createWorkout(currentWorkoutId, startTimeS = nowEpochS) // current workout

        // Inject workouts into repository._allWorkouts
        val allWorkoutsField = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        allWorkoutsField.isAccessible = true
        val flow = allWorkoutsField.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = listOf(currentWorkout, recentWorkout1, recentWorkout2, oldWorkout, unfinishedWorkout)

        coEvery { repository.getWorkoutTelemetryPoints(101L) } returns createTelemetryPointsWithHrvAndPower()
        coEvery { repository.getWorkoutTelemetryPoints(102L) } returns createTelemetryPointsWithHrvAndPower()
        coEvery { repository.getWorkoutTelemetryPoints(103L) } returns createTelemetryPointsWithHrvAndPower()
        coEvery { repository.getWorkoutTelemetryPoints(104L) } returns createTelemetryPointsWithHrvAndPower()

        val results = repository.getRecentHrvSessionThresholds(
            currentWorkoutId = currentWorkoutId,
            referenceTimestampMs = nowEpochMs,
            windowDays = 30
        )

        // Only workouts 101 and 102 must be included
        assertEquals(2, results.size)
        val resultIds = results.map { it.workoutId }.toSet()
        assertTrue(resultIds.contains(101L))
        assertTrue(resultIds.contains(102L))

        // Verify HRVT2 watts and confidence are populated
        results.forEach { session ->
            assertNotNull(session.hrvt2Watts)
            assertTrue(session.hrvt2Watts!! > 0)
        }
    }

    @Test
    fun testGetRecentHrvSessionThresholds_cachesResultsAndInvalidatesOnRefresh() = runBlocking {
        val currentWorkoutId = 200L
        val recentWorkout = createWorkout(201L, startTimeS = nowEpochS - 3 * 86400L)

        val allWorkoutsField = WorkoutRepository::class.java.getDeclaredField("_allWorkouts")
        allWorkoutsField.isAccessible = true
        val flow = allWorkoutsField.get(repository) as MutableStateFlow<List<WorkoutData>>
        flow.value = listOf(recentWorkout)

        coEvery { repository.getWorkoutTelemetryPoints(201L) } returns createTelemetryPointsWithHrvAndPower()

        // First call evaluates telemetry
        val firstResults = repository.getRecentHrvSessionThresholds(
            currentWorkoutId = currentWorkoutId,
            referenceTimestampMs = nowEpochMs
        )
        assertEquals(1, firstResults.size)
        coVerify(exactly = 1) { repository.getWorkoutTelemetryPoints(201L) }

        // Second call should hit the cache without calling getWorkoutTelemetryPoints again
        val secondResults = repository.getRecentHrvSessionThresholds(
            currentWorkoutId = currentWorkoutId,
            referenceTimestampMs = nowEpochMs
        )
        assertEquals(1, secondResults.size)
        coVerify(exactly = 1) { repository.getWorkoutTelemetryPoints(201L) }

        // refreshWorkout(201L) must invalidate cache for workout 201
        repository.refreshWorkout(201L)

        // Third call should re-evaluate telemetry after invalidation
        val thirdResults = repository.getRecentHrvSessionThresholds(
            currentWorkoutId = currentWorkoutId,
            referenceTimestampMs = nowEpochMs
        )
        assertEquals(1, thirdResults.size)
        coVerify(exactly = 2) { repository.getWorkoutTelemetryPoints(201L) }
    }
}
