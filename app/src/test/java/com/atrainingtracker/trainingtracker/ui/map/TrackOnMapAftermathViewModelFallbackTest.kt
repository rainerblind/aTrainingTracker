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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
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
 * Unit tests verifying map polyline fallback protection when sample tracks are sparse (< 2 points)
 * (`REQ-TRK-051.3` / `TST-TRK-051.3`, `ATT-4968`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackOnMapAftermathViewModelFallbackTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private lateinit var mockWorkoutRepo: WorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkObject(WorkoutRepository.Companion)
        mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo
        coEvery { mockWorkoutRepo.getWorkoutMarkers(any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getHeartRateZoneDistribution(any(), any()) } returns null
        coEvery { mockWorkoutRepo.getPowerZoneDistribution(any()) } returns null
        coEvery { mockWorkoutRepo.getWorkoutTelemetryPoints(any()) } returns emptyList()

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

    private fun createWorkoutWithPolyline(id: Long, polyline: String): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Test Workout $id",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-11-03",
            formattedTime = "12:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = polyline,
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
    fun testPhase4LoadTracks_retainsFastTrackWhenSamplesHaveLessThanTwoPoints() = runTest(testDispatcher) {
        val viewModel = TrackOnMapAftermathViewModel(mockApplication, ioDispatcher = testDispatcher)

        // 3-point valid summary polyline
        val fastLatLngs = listOf(
            LatLng(48.137, 11.575),
            LatLng(48.138, 11.576),
            LatLng(48.139, 11.577)
        )
        val encodedFastPolyline = PolyUtil.encode(fastLatLngs)
        val workout = createWorkoutWithPolyline(201L, encodedFastPolyline)

        // Simulate sample DB returning only 1 point in Phase 4
        val singlePoint = listOf(
            PathPoint(
                distance = 0.0,
                latLng = LatLng(48.137, 11.575),
                altitude = 500.0,
                timeSec = 0L,
                speedMps = 0.0
            )
        )
        coEvery { mockWorkoutRepo.loadWorkoutDetailTelemetry(201L, BSportType.BIKE) } throws RuntimeException("Fallback")
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(201L, TrackType.BEST) } returns singlePoint
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(201L, neq(TrackType.BEST)) } returns emptyList()

        viewModel.loadAftermathData(workout)
        advanceUntilIdle()

        val uiTracks = viewModel.uiState.value.tracks
        assertFalse("Tracks must not be empty", uiTracks.isEmpty())
        val bestTrack = uiTracks.first()
        assertEquals("Track should retain the 3 points from fastTrack rather than being overwritten with 1 point", 3, bestTrack.path.size)
    }

    @Test
    fun testPhase4LoadTracks_replacesFastTrackWhenSamplesAreValid() = runTest(testDispatcher) {
        val viewModel = TrackOnMapAftermathViewModel(mockApplication, ioDispatcher = testDispatcher)

        // 2-point summary polyline
        val fastLatLngs = listOf(
            LatLng(48.137, 11.575),
            LatLng(48.138, 11.576)
        )
        val encodedFastPolyline = PolyUtil.encode(fastLatLngs)
        val workout = createWorkoutWithPolyline(202L, encodedFastPolyline)

        // Full detailed 4-point sample track in Phase 4
        val detailedPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.137, 11.575), altitude = 500.0, timeSec = 0L, speedMps = 5.0),
            PathPoint(distance = 5.0, latLng = LatLng(48.1375, 11.5755), altitude = 501.0, timeSec = 1L, speedMps = 5.2),
            PathPoint(distance = 10.0, latLng = LatLng(48.138, 11.576), altitude = 502.0, timeSec = 2L, speedMps = 5.3),
            PathPoint(distance = 15.0, latLng = LatLng(48.1385, 11.5765), altitude = 503.0, timeSec = 3L, speedMps = 5.1)
        )
        coEvery { mockWorkoutRepo.loadWorkoutDetailTelemetry(202L, BSportType.BIKE) } throws RuntimeException("Fallback")
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(202L, TrackType.BEST) } returns detailedPoints
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(202L, neq(TrackType.BEST)) } returns emptyList()

        viewModel.loadAftermathData(workout)
        advanceUntilIdle()

        val uiTracks = viewModel.uiState.value.tracks
        assertFalse("Tracks must not be empty", uiTracks.isEmpty())
        val bestTrack = uiTracks.first()
        assertEquals("Track should be updated with the 4 detailed sample points", 4, bestTrack.path.size)
    }
}
