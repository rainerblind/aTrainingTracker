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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.app.Application
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AthleteProfileViewModel] Cardio-Power Dynamics longitudinal trend state machine (REQ-REV-011 / TST-REV-012 / ATT-3906).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AthleteProfileViewModelCardioPowerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepo: AthleteMetricsRepository
    private lateinit var mockSettingsDataStore: SettingsDataStore
    private lateinit var mockDb: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockApplication = mockk(relaxed = true)
        val tempFilesDir = java.io.File(System.getProperty("java.io.tmpdir", "/tmp"), "athlete_profile_cardio_test_${System.nanoTime()}").apply { mkdirs() }
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempFilesDir

        mockRepo = mockk(relaxed = true)
        mockSettingsDataStore = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        mockkObject(AthleteMetricsRepository.Companion)
        every { AthleteMetricsRepository.getInstance(any()) } returns mockRepo
        every { mockRepo.entriesFlow } returns MutableStateFlow<List<AthleteMetricEntry>>(emptyList())
        coEvery { mockRepo.initializeAndRefresh() } returns Unit

        mockkObject(SettingsDataStore.Companion)
        every { SettingsDataStore.getInstance(any()) } returns mockSettingsDataStore
        every { mockSettingsDataStore.hrMaxFlow } returns flowOf<Int?>(null)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockDb
        every { mockDb.getWorkoutTrainingLoadRecords() } returns emptyList()

        mockkConstructor(TuningPreferencesDataStore::class)
        every { anyConstructed<TuningPreferencesDataStore>().tuningConfigFlow } returns flowOf(
            TuningConfig(enableCardioPowerDynamics = true)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testUiState_defaultsReflectCardioPowerDynamicsGating() {
        val state = AthleteProfileUiState()
        assertFalse(state.enableCardioPowerDynamics)
        assertEquals(CardioTrendMetric.HR_200W, state.selectedCardioTrendMetric)
        assertEquals(MetricsTimeRange.ALL, state.selectedCardioTimeRange)
        assertTrue(state.cardioTrendPoints.isEmpty())
        assertTrue(state.cardioTrendRollingPoints.isEmpty())
        assertEquals(110.0 to 170.0, state.cardioTrendYDomain)
    }

    @Test
    fun testViewModel_loadsSamplesAndComputesTrends() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        val sampleRecord = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 70.0,
            kStart = 0.25,
            hr200Start = 150.0,
            r2Start = 0.85,
            rmseStart = 2.5
        )

        val samples = listOf(
            CardioPowerTrendSample(workoutId = 1L, timestampMs = now - 20 * day, record = sampleRecord),
            CardioPowerTrendSample(workoutId = 2L, timestampMs = now - 10 * day, record = sampleRecord.copy(workoutId = 2L, hr200Start = 148.0)),
            CardioPowerTrendSample(workoutId = 3L, timestampMs = now, record = sampleRecord.copy(workoutId = 3L, hr200Start = 146.0))
        )

        every { mockDb.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returns samples

        val viewModel = AthleteProfileViewModel(mockApplication, ioDispatcher = testDispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("enableCardioPowerDynamics should be true from TuningConfig", state.enableCardioPowerDynamics)
        assertEquals(3, state.cardioTrendPoints.size)
        assertEquals(150.0, state.cardioTrendPoints[0].value, 0.001)
        assertEquals(148.0, state.cardioTrendPoints[1].value, 0.001)
        assertEquals(146.0, state.cardioTrendPoints[2].value, 0.001)

        // Switch to Tau
        viewModel.selectCardioTrendMetric(CardioTrendMetric.TAU)
        advanceUntilIdle()

        val tauState = viewModel.uiState.value
        assertEquals(CardioTrendMetric.TAU, tauState.selectedCardioTrendMetric)
        assertEquals(3, tauState.cardioTrendPoints.size)
        assertEquals(30.0, tauState.cardioTrendPoints[0].value, 0.001)

        // Switch to Slope (1 / 0.25 = 4.0)
        viewModel.selectCardioTrendMetric(CardioTrendMetric.SLOPE)
        advanceUntilIdle()

        val slopeState = viewModel.uiState.value
        assertEquals(CardioTrendMetric.SLOPE, slopeState.selectedCardioTrendMetric)
        assertEquals(3, slopeState.cardioTrendPoints.size)
        assertEquals(4.0, slopeState.cardioTrendPoints[0].value, 0.001)

        // Switch time range to 3 months (points are within 20 days so still 3)
        viewModel.selectCardioTimeRange(MetricsTimeRange.THREE_MONTHS)
        advanceUntilIdle()
        assertEquals(MetricsTimeRange.THREE_MONTHS, viewModel.uiState.value.selectedCardioTimeRange)
        assertEquals(3, viewModel.uiState.value.cardioTrendPoints.size)
    }

    @Test
    fun testRollingTrend_preservesBoundaryPreSlicing() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        val sampleRecord = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 70.0,
            kStart = 0.25,
            hr200Start = 150.0,
            r2Start = 0.85,
            rmseStart = 2.5
        )

        // Workout 1: 40 days ago (value 150)
        // Workout 2: 20 days ago (value 140) -> 30d window includes w1 & w2 -> avg = 145.0
        // Workout 3: now (value 130) -> 30d window includes w2 & w3 -> avg = 135.0
        val samples = listOf(
            CardioPowerTrendSample(workoutId = 1L, timestampMs = now - 40 * day, record = sampleRecord.copy(workoutId = 1L, hr200Start = 150.0)),
            CardioPowerTrendSample(workoutId = 2L, timestampMs = now - 20 * day, record = sampleRecord.copy(workoutId = 2L, hr200Start = 140.0)),
            CardioPowerTrendSample(workoutId = 3L, timestampMs = now, record = sampleRecord.copy(workoutId = 3L, hr200Start = 130.0))
        )

        every { mockDb.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returns samples

        val viewModel = AthleteProfileViewModel(mockApplication, ioDispatcher = testDispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // In ALL range, all 3 points are present
        val allState = viewModel.uiState.value
        assertEquals(3, allState.cardioTrendPoints.size)
        assertEquals(3, allState.cardioTrendRollingPoints.size)
        assertEquals(145.0, allState.cardioTrendRollingPoints[1].value, 0.001)

        // Switch to ONE_MONTH (30 days)
        viewModel.selectCardioTimeRange(MetricsTimeRange.ONE_MONTH)
        advanceUntilIdle()

        val oneMonthState = viewModel.uiState.value
        // Only workouts within 30 days are visible (workout 2 and workout 3)
        assertEquals(2, oneMonthState.cardioTrendPoints.size)
        assertEquals(2, oneMonthState.cardioTrendRollingPoints.size)
        // Boundary check: Point at 20 days ago STILL has rolling value 145.0 (computed prior to slicing)
        assertEquals(145.0, oneMonthState.cardioTrendRollingPoints[0].value, 0.001)
        assertEquals(135.0, oneMonthState.cardioTrendRollingPoints[1].value, 0.001)
    }

    @Test
    fun testReactiveRefresh_onWorkoutsChange() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        val initialRecord = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 70.0,
            kStart = 0.25,
            hr200Start = 150.0,
            r2Start = 0.85,
            rmseStart = 2.5
        )

        val initialSamples = listOf(
            CardioPowerTrendSample(workoutId = 1L, timestampMs = now - 10 * day, record = initialRecord)
        )
        val updatedSamples = listOf(
            CardioPowerTrendSample(workoutId = 1L, timestampMs = now - 10 * day, record = initialRecord),
            CardioPowerTrendSample(workoutId = 2L, timestampMs = now, record = initialRecord.copy(workoutId = 2L, hr200Start = 142.0))
        )

        val mockWorkoutRepo = mockk<com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository>(relaxed = true)
        val allWorkoutsFlow = MutableStateFlow<List<com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData>>(emptyList())
        every { mockWorkoutRepo.allWorkouts } returns allWorkoutsFlow
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } returns emptyList()
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returnsMany listOf(initialSamples, updatedSamples)

        val viewModel = AthleteProfileViewModel(mockApplication, mockWorkoutRepo, ioDispatcher = testDispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cardioTrendPoints.size)

        // Trigger emission on allWorkouts flow (e.g. workout finished or imported)
        allWorkoutsFlow.value = listOf(mockk(relaxed = true))
        advanceUntilIdle()

        // uiState reactively updates to reflect the new samples
        assertEquals(2, viewModel.uiState.value.cardioTrendPoints.size)
        assertEquals(142.0, viewModel.uiState.value.cardioTrendPoints[1].value, 0.001)
    }
}
