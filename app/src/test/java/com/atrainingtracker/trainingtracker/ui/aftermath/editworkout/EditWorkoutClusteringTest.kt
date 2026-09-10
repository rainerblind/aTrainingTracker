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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite verifying multi-sport candidate derivation, cost-minimization ranking,
 * and suggested sport dropdown expansion in [EditWorkoutViewModel] according to REQ-SET-064 and TST-SET-056 (ATT-820).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditWorkoutClusteringTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockApplication: Application
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockEquipmentRepository: EquipmentRepository
    private lateinit var mockSportTypesRepository: SportTypesRepository
    private lateinit var mockSportTypeDatabaseManager: SportTypeDatabaseManager
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine

    private val initialWorkoutLoadedLiveEvent = MutableLiveData<WorkoutData>()

    private val sportEinkaufen = SimpleSportTypeInfo(150L, "Einkaufen", BSportType.BIKE)
    private val sportRunning = SimpleSportTypeInfo(200L, "Running", BSportType.RUN)
    private val sportCycling = SimpleSportTypeInfo(100L, "Road Cycling", BSportType.BIKE)

    private val clusterEinkaufen = WorkoutCluster(
        id = 10L,
        name = "Bakery Commute",
        startLat = 48.0,
        startLng = 11.0,
        endLat = 48.01,
        endLng = 11.01,
        maxDispLat = 48.005,
        maxDispLng = 11.005,
        refDistance = 2500.0,
        hitCount = 3,
        bSportType = BSportType.BIKE,
        probableSportId = 150L
    )

    private val clusterRunning = WorkoutCluster(
        id = 20L,
        name = "Bakery Commute Run",
        startLat = 48.0,
        startLng = 11.0,
        endLat = 48.01,
        endLng = 11.01,
        maxDispLat = 48.005,
        maxDispLng = 11.005,
        refDistance = 2500.0,
        hitCount = 2,
        bSportType = BSportType.RUN,
        probableSportId = 200L
    )

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

        mockApplication = mockk(relaxed = true)
        every { mockApplication.getString(R.string.all_sports) } returns "+ all sports +"
        every { mockApplication.getString(R.string.all_shoes) } returns "+ all shoes +"
        every { mockApplication.getString(R.string.all_bikes) } returns "+ all bikes +"
        every { mockApplication.getString(R.string.no_equipment) } returns "- none -"

        mockWorkoutRepository = mockk(relaxed = true)
        mockEquipmentRepository = mockk(relaxed = true)
        mockSportTypesRepository = mockk(relaxed = true)
        mockSportTypeDatabaseManager = mockk(relaxed = true)
        mockDiscoveryManager = mockk(relaxed = true)
        mockClusterEngine = mockk(relaxed = true)

        WorkoutRepository.resetForTesting(mockWorkoutRepository)
        EquipmentRepository.resetForTesting(mockEquipmentRepository)
        SportTypesRepository.resetForTesting(mockSportTypesRepository)
        SportTypeDatabaseManager.resetForTesting(mockSportTypeDatabaseManager)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(mockDiscoveryManager)
        WorkoutClusterEngine.resetForTesting(mockClusterEngine)

        every { mockEquipmentRepository.equipmentList } returns emptyList()
        every { mockSportTypesRepository.sportTypesList } returns listOf(sportEinkaufen, sportRunning, sportCycling)
        every { mockWorkoutRepository.initialWorkoutLoaded } returns initialWorkoutLoadedLiveEvent

        every { mockSportTypeDatabaseManager.getBSportType(150L) } returns BSportType.BIKE
        every { mockSportTypeDatabaseManager.getBSportType(200L) } returns BSportType.RUN
        every { mockSportTypeDatabaseManager.getBSportType(100L) } returns BSportType.BIKE
        every { mockSportTypeDatabaseManager.getUIName(150L) } returns "Einkaufen"
        every { mockSportTypeDatabaseManager.getUIName(200L) } returns "Running"

        every { mockSportTypeDatabaseManager.getStravaName(any()) } returns null
        every { mockDiscoveryManager.getEquipmentNamesForSport(any()) } returns emptySet()
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        EquipmentRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        EquipmentAndSportTypeDiscoveryManager.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)

        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createBakeryCommuteWorkout(
        bSportType: BSportType = BSportType.UNKNOWN,
        sportName: String = "Other",
        sportId: Long = 1L,
        avgSpeedMps: Double = 2.78
    ): WorkoutData {
        return WorkoutData(
            id = 42L,
            finished = true,
            fileBaseName = "commute_42",
            workoutName = "commute_42",
            sportId = sportId,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = 1773129600L,
            formattedDate = "10.09.2026",
            formattedTime = "07:30",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = -1L,
            commute = true,
            trainer = false,
            mapPolyline = "mock_polyline",
            encodedAltitudes = "mock_alt",
            encodedDistances = "mock_dist",
            uploadToStrava = 0,
            totalDistance = 2500.0,
            maxDisplacement = 1200.0,
            activeTimeSec = 900L,
            totalTimeSec = 950L,
            avgSpeedMps = avgSpeedMps,
            ascentMeters = 20L,
            descentMeters = 20L,
            minAltitude = 500.0,
            maxAltitude = 520.0,
            minAltitudeLatLng = LatLng(48.0, 11.0),
            maxAltitudeLatLng = LatLng(48.005, 11.005),
            startLatLng = LatLng(48.0, 11.0),
            endLatLng = LatLng(48.01, 11.01),
            maxDisplacementLatLng = LatLng(48.005, 11.005),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun fetchClusterSuggestions_withSensorlessAmbiguousSpeed_evaluatesBothRunAndBikeCandidates() = runTest(testDispatcher) {
        // Given: Sensorless workout at 2.78 m/s (~10 km/h) matching both Running (RUN) and Einkaufen (BIKE)
        val workout = createBakeryCommuteWorkout(bSportType = BSportType.UNKNOWN)
        every { mockDiscoveryManager.getLinkedSportTypeIds(42L) } returns emptySet()
        every { mockDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, 2.78) } returns setOf(BSportType.RUN, BSportType.BIKE)

        val capturedCandidates = slot<Set<BSportType>>()
        every {
            mockClusterEngine.getClusterScores(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                workoutName = any(),
                candidateSportTypes = capture(capturedCandidates),
                minAltPos = any(),
                maxAltPos = any()
            )
        } returns listOf(clusterEinkaufen to 0.25, clusterRunning to 0.40)

        // When: Initializing ViewModel and loading workout
        val viewModel = EditWorkoutViewModel(mockApplication, 42L)
        initialWorkoutLoadedLiveEvent.value = workout
        advanceUntilIdle()

        // Then: Candidate sport types passed to cluster engine must contain both RUN and BIKE
        assertTrue(capturedCandidates.isCaptured)
        assertEquals(setOf(BSportType.RUN, BSportType.BIKE), capturedCandidates.captured)

        // And: Lowest cost cluster (Einkaufen, cost 0.25) is ranked #1 in suggestions
        val suggestions = viewModel.clusterSuggestions.value
        assertEquals(2, suggestions.size)
        assertEquals(clusterEinkaufen.id, suggestions[0].first.id)
        assertEquals(0.25, suggestions[0].second, 0.001)
        assertEquals(clusterRunning.id, suggestions[1].first.id)
        assertEquals(0.40, suggestions[1].second, 0.001)
    }

    @Test
    fun fetchClusterSuggestions_whenInitiallyGuessedAsRun_doesNotPenalizeBikeClusterWhenSpeedMatchesBoth() = runTest(testDispatcher) {
        // Given: Tracking completion guessed sport as RUN, but workout was sensorless and speed matches both RUN and BIKE
        val workout = createBakeryCommuteWorkout(bSportType = BSportType.RUN, sportName = "Running", sportId = 200L)
        every { mockDiscoveryManager.getLinkedSportTypeIds(42L) } returns emptySet()
        every { mockDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, 2.78) } returns setOf(BSportType.RUN, BSportType.BIKE)

        val capturedCandidates = slot<Set<BSportType>>()
        every {
            mockClusterEngine.getClusterScores(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                workoutName = any(),
                candidateSportTypes = capture(capturedCandidates),
                minAltPos = any(),
                maxAltPos = any()
            )
        } returns listOf(clusterEinkaufen to 0.25, clusterRunning to 0.40)

        // When: Initializing ViewModel
        val viewModel = EditWorkoutViewModel(mockApplication, 42L)
        initialWorkoutLoadedLiveEvent.value = workout
        advanceUntilIdle()

        // Then: Candidate sports must include BIKE despite workout.bSportType == RUN
        assertTrue(capturedCandidates.isCaptured)
        assertEquals(setOf(BSportType.RUN, BSportType.BIKE), capturedCandidates.captured)
        assertEquals(clusterEinkaufen.id, viewModel.clusterSuggestions.value.first().first.id)
    }

    @Test
    fun updateSuggestedSportTypeNames_withSensorlessAmbiguousSpeed_includesSportsAcrossBaseSports() = runTest(testDispatcher) {
        // Given: Sensorless workout matching both Running and Einkaufen
        val workout = createBakeryCommuteWorkout(bSportType = BSportType.UNKNOWN)
        every { mockDiscoveryManager.getLinkedSportTypeIds(42L) } returns emptySet()
        every { mockDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, 2.78) } returns setOf(BSportType.RUN, BSportType.BIKE)
        every { mockDiscoveryManager.getSpeedBasedSportTypeNames(BSportType.UNKNOWN, 2.78) } returns setOf("Running", "Einkaufen")

        // When: Initializing ViewModel
        val viewModel = EditWorkoutViewModel(mockApplication, 42L)
        initialWorkoutLoadedLiveEvent.value = workout
        advanceUntilIdle()

        // Then: Suggested dropdown must contain both Running and Einkaufen
        val dropdownNames = viewModel.sportTypeNames.value
        assertNotNull(dropdownNames)
        assertTrue(dropdownNames!!.contains("Running"))
        assertTrue(dropdownNames.contains("Einkaufen"))
    }

    @Test
    fun fetchClusterSuggestions_withHardwareSensors_preservesStrictHardwareSovereignty() = runTest(testDispatcher) {
        // Given: Workout recorded with dedicated cadence sensor linked to BIKE (REQ-SET-030)
        val workout = createBakeryCommuteWorkout(bSportType = BSportType.BIKE)
        every { mockDiscoveryManager.getLinkedSportTypeIds(42L) } returns setOf(150L) // linked to Einkaufen (BIKE)
        every { mockSportTypeDatabaseManager.getBSportType(150L) } returns BSportType.BIKE

        val capturedCandidates = slot<Set<BSportType>>()
        every {
            mockClusterEngine.getClusterScores(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                workoutName = any(),
                candidateSportTypes = capture(capturedCandidates),
                minAltPos = any(),
                maxAltPos = any()
            )
        } returns listOf(clusterEinkaufen to 0.25)

        // When: Initializing ViewModel
        val viewModel = EditWorkoutViewModel(mockApplication, 42L)
        initialWorkoutLoadedLiveEvent.value = workout
        advanceUntilIdle()

        // Then: Candidates must strictly be {BIKE}, excluding RUN
        assertTrue(capturedCandidates.isCaptured)
        assertEquals(setOf(BSportType.BIKE), capturedCandidates.captured)
    }

    @Test
    fun fetchClusterSuggestions_whenUserManuallyChangesSport_restrictsCandidatesToSelectedSport() = runTest(testDispatcher) {
        // Given: Ambiguous workout loaded
        val workout = createBakeryCommuteWorkout(bSportType = BSportType.UNKNOWN)
        every { mockDiscoveryManager.getLinkedSportTypeIds(42L) } returns emptySet()
        every { mockDiscoveryManager.getCandidateBSportTypes(BSportType.UNKNOWN, 2.78) } returns setOf(BSportType.RUN, BSportType.BIKE)

        val capturedCandidates = mutableListOf<Set<BSportType>>()
        every {
            mockClusterEngine.getClusterScores(
                start = any(),
                end = any(),
                apex = any(),
                distance = any(),
                workoutName = any(),
                candidateSportTypes = capture(capturedCandidates),
                minAltPos = any(),
                maxAltPos = any()
            )
        } returns listOf(clusterRunning to 0.30)

        val viewModel = EditWorkoutViewModel(mockApplication, 42L)
        initialWorkoutLoadedLiveEvent.value = workout
        advanceUntilIdle()

        // When: User explicitly changes sport to Running in UI dropdown
        viewModel.updateSportName("Running")
        advanceUntilIdle()

        // Then: The second call to getClusterScores must strictly be {RUN}
        assertTrue(capturedCandidates.size >= 2)
        assertEquals(setOf(BSportType.RUN), capturedCandidates.last())
    }
}
