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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
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
 * Unit verification for [TrackOnMapAftermathViewModel] telemetry ingestion (REQ-UI-235 / TST-UI-194.2 / ATT-2006).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackOnMapViewModelTelemetryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
    private val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)
    private val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
    private val mockPrefManager = mockk<MyPreferenceManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo

        mockkObject(SegmentsRepository.Companion)
        every { SegmentsRepository.getInstance(any()) } returns mockSegmentsRepo
        every { mockSegmentsRepo.allSegmentsWithPath } returns MutableStateFlow(emptyList())

        mockkObject(RoutesRepository.Companion)
        every { RoutesRepository.getInstance(any()) } returns mockRoutesRepo
        every { mockRoutesRepo.allRoutes } returns MutableStateFlow(emptyList())

        mockkConstructor(MyPreferenceManager::class)
        every { anyConstructed<MyPreferenceManager>().enabledTrackTypesFlow } returns MutableStateFlow(setOf("BEST"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testAftermathMapUIState_containsTelemetryPath_defaultEmpty() {
        val state = AftermathMapUIState()
        assertTrue(state.telemetryPath.isEmpty())

        val dummyPoints = listOf(
            PathPoint(
                distance = 0.0,
                latLng = LatLng(0.0, 0.0),
                altitude = 0.0,
                timeSec = 10L,
                hr = 150
            )
        )
        val stateWithTelemetry = state.copy(telemetryPath = dummyPoints)
        assertEquals(1, stateWithTelemetry.telemetryPath.size)
        assertEquals(150, stateWithTelemetry.telemetryPath[0].hr)
    }

    @Test
    fun testLoadAftermathData_whenTracksEmpty_populatesTelemetryPath() = runTest(testDispatcher) {
        val workoutData = createWorkoutData(id = 42L, mapPolyline = "")

        coEvery { mockWorkoutRepo.getWorkoutMarkers(any()) } returns emptyList()
        coEvery { mockWorkoutRepo.getWorkoutTrackPoints(42L, any()) } returns emptyList()

        val expectedPoints = listOf(
            PathPoint(
                distance = 0.0,
                latLng = LatLng(0.0, 0.0),
                altitude = 0.0,
                timeSec = 0L,
                hr = 135,
                power = 200
            ),
            PathPoint(
                distance = 0.0,
                latLng = LatLng(0.0, 0.0),
                altitude = 0.0,
                timeSec = 1L,
                hr = 136,
                power = 205
            )
        )
        coEvery { mockWorkoutRepo.getWorkoutTelemetryPoints(42L) } returns expectedPoints

        val viewModel = TrackOnMapAftermathViewModel(mockApplication, ioDispatcher = testDispatcher)
        viewModel.loadAftermathData(workoutData)
        advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue("Tracks should be empty for trackless workout", currentState.tracks.isEmpty())
        assertEquals("TelemetryPath must be populated from getWorkoutTelemetryPoints", 2, currentState.telemetryPath.size)
        assertEquals(135, currentState.telemetryPath[0].hr)
        assertEquals(200, currentState.telemetryPath[0].power)
    }

    private fun createWorkoutData(id: Long, mapPolyline: String): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "2026-10-02-10-00-00",
            workoutName = "Treadmill Run",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1700000000L,
            formattedDate = "2026-10-02",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = true,
            mapPolyline = mapPolyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 0.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 5.5,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = null,
            endLatLng = null,
            startLocationName = null,
            endLocationName = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }
}
