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
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying post-workout climb extraction, Climbs.db ingestion,
 * 50m spatial deduplication, and LiveClimbsRepository refresh (REQ-MAP-064.1, TST-MAP-066.1, ATT-2766).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutClimbIngestionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockClimbsDb = mockk<ClimbsDatabaseManager>(relaxed = true)
    private val mockLiveClimbsRepo = mockk<LiveClimbsRepository>(relaxed = true)
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

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

        every { mockApplication.applicationContext } returns mockApplication

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        ClimbsDatabaseManager.resetForTesting(mockClimbsDb)
        LiveClimbsRepository.resetForTesting(mockLiveClimbsRepo)

        val constructor = WorkoutRepository::class.java.getDeclaredConstructor(Application::class.java)
        constructor.isAccessible = true
        repository = spyk(constructor.newInstance(mockApplication))
    }

    @After
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        WorkoutRepository.resetForTesting(null)
        ClimbsDatabaseManager.resetForTesting(null)
        LiveClimbsRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testExtractAndPersistClimbsFromWorkout_detectsAndInsertsClimbs() = runBlocking {
        val workoutId = 42L
        // 1000m climb at 8% grade: start alt 200m, end alt 280m
        val points = mutableListOf<PathPoint>()
        for (i in 0..20) {
            val dist = i * 50.0
            val alt = 200.0 + (dist * 0.08)
            points.add(PathPoint(distance = dist, latLng = LatLng(47.5 + (i * 0.0005), 10.0), altitude = alt))
        }

        coEvery { repository.getWorkoutTrackPoints(workoutId, TrackType.BEST) } returns points
        coEvery { mockClimbsDb.insertClimbsWithDeduplicationBatch(any(), thresholdMeters = 50.0f) } returns listOf(101L)
        coEvery { mockLiveClimbsRepo.refreshClimbs() } returns Unit

        val resultIds = repository.extractAndPersistClimbsFromWorkout(workoutId)

        assertEquals("Should return 1 inserted/deduplicated climb ID", listOf(101L), resultIds)

        coVerify(exactly = 1) {
            mockClimbsDb.insertClimbsWithDeduplicationBatch(
                match { climbs ->
                    climbs.size == 1 &&
                    climbs[0].routeId == null &&
                    climbs[0].category != ClimbCategory.UNCATEGORIZED
                },
                thresholdMeters = 50.0f
            )
        }

        coVerify(exactly = 1) {
            mockLiveClimbsRepo.refreshClimbs()
        }
    }

    @Test
    fun testExtractAndPersistClimbsFromWorkout_tooFewPoints_doesNothing() = runBlocking {
        val workoutId = 43L
        coEvery { repository.getWorkoutTrackPoints(workoutId, TrackType.BEST) } returns listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.5, 10.0), altitude = 200.0)
        )

        val resultIds = repository.extractAndPersistClimbsFromWorkout(workoutId)

        assertTrue("Result IDs should be empty for < 2 points", resultIds.isEmpty())
        coVerify(exactly = 0) {
            mockClimbsDb.insertClimbsWithDeduplicationBatch(any(), any())
        }
    }

    @Test
    fun testExtractAndPersistClimbsFromWorkout_flatTrack_doesNothing() = runBlocking {
        val workoutId = 44L
        val flatPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(47.5, 10.0), altitude = 200.0),
            PathPoint(distance = 1000.0, latLng = LatLng(47.51, 10.0), altitude = 200.0)
        )
        coEvery { repository.getWorkoutTrackPoints(workoutId, TrackType.BEST) } returns flatPoints

        val resultIds = repository.extractAndPersistClimbsFromWorkout(workoutId)

        assertTrue("Result IDs should be empty for flat track", resultIds.isEmpty())
        coVerify(exactly = 0) {
            mockClimbsDb.insertClimbsWithDeduplicationBatch(any(), any())
        }
    }
}
