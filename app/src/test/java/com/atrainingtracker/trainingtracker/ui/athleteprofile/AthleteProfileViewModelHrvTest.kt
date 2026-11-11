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
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvTrendMetricMode
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AthleteProfileViewModel] HRV readiness integration
 * and synchronized time-range selection (`REQ-UI-513.4`, `TST-UI-513.3`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AthleteProfileViewModelHrvTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepo: AthleteMetricsRepository
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockHrvRepo: HrvReadinessRepository
    private lateinit var mockSettingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockApplication = mockk(relaxed = true)
        every { mockApplication.applicationContext } returns mockApplication

        mockRepo = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)
        mockHrvRepo = mockk(relaxed = true)
        mockSettingsDataStore = mockk(relaxed = true)

        mockkObject(AthleteMetricsRepository.Companion)
        every { AthleteMetricsRepository.getInstance(any()) } returns mockRepo
        every { mockRepo.entriesFlow } returns MutableStateFlow<List<AthleteMetricEntry>>(emptyList())
        coEvery { mockRepo.initializeAndRefresh() } returns Unit

        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } returns emptyList()
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(any()) } returns emptyList()

        mockkObject(SettingsDataStore.Companion)
        every { SettingsDataStore.getInstance(any()) } returns mockSettingsDataStore
        every { mockSettingsDataStore.hrMaxFlow } returns flowOf<Int?>(null)

        HrvReadinessRepository.resetForTesting(mockHrvRepo)

        mockkConstructor(TuningPreferencesDataStore::class)
        every { anyConstructed<TuningPreferencesDataStore>().tuningConfigFlow } returns flowOf(
            TuningConfig(enableCardioPowerDynamics = true)
        )
    }

    @After
    fun tearDown() {
        HrvReadinessRepository.resetForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        hrvEntries: List<HrvReadinessEntry> = emptyList()
    ): AthleteProfileViewModel {
        coEvery { mockHrvRepo.getAllEntries() } returns hrvEntries
        return AthleteProfileViewModel(
            application = mockApplication,
            workoutRepository = mockWorkoutRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun testHrvState_emptyOnboardingWhenNoEntries() = runTest(testDispatcher) {
        val viewModel = createViewModel(emptyList())
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("hasHrvReadings must be false when 0 entries exist", state.hasHrvReadings)
        assertTrue("hrvReadinessEntries must be empty", state.hrvReadinessEntries.isEmpty())
        assertNull("hrvTrendSeries must be null when empty", state.hrvTrendSeries)

        collectJob.cancel()
    }

    @Test
    fun testHrvState_populatedWhenEntriesExist() = runTest(testDispatcher) {
        val entry = HrvReadinessEntry(
            id = 1L,
            timestampMs = 1730419200000L,
            dateIso = "2026-11-01",
            timezoneId = "UTC",
            restingHrBpm = 52,
            rmssdMs = 65.0,
            lnRmssd = 4.17,
            hrvScore100 = 85.0,
            durationSec = 60,
            posture = HrvPosture.SUPINE,
            isPrimaryForDay = true
        )
        val viewModel = createViewModel(listOf(entry))
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("hasHrvReadings must be true", state.hasHrvReadings)
        assertEquals(1, state.hrvReadinessEntries.size)
        assertEquals(entry, state.hrvReadinessEntries.first())
        assertNotNull("hrvTrendSeries must be generated", state.hrvTrendSeries)

        collectJob.cancel()
    }

    @Test
    fun testHrvPostureAndMetricModeSelection() = runTest(testDispatcher) {
        val viewModel = createViewModel(emptyList())
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.selectHrvPosture(HrvPosture.SEATED)
        viewModel.selectHrvMetricMode(HrvTrendMetricMode.RMSSD)
        viewModel.toggleHrvTrainingLoad(true)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HrvPosture.SEATED, state.selectedHrvPosture)
        assertEquals(HrvTrendMetricMode.RMSSD, state.selectedHrvMetricMode)
        assertTrue(state.showHrvTrainingLoad)

        collectJob.cancel()
    }

    @Test
    fun testDeleteHrvReading_callsRepositoryAndReloads() = runTest(testDispatcher) {
        coEvery { mockHrvRepo.deleteEntry(42L) } returns true
        val viewModel = createViewModel(emptyList())
        testScheduler.advanceUntilIdle()

        viewModel.deleteHrvReading(42L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockHrvRepo.deleteEntry(42L) }
        coVerify(atLeast = 2) { mockHrvRepo.getAllEntries() }
    }

    @Test
    fun testSelectTimeRange_synchronizesPmcAndCardioRanges() = runTest(testDispatcher) {
        val viewModel = createViewModel(emptyList())
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        viewModel.selectTimeRange(MetricsTimeRange.THREE_MONTHS)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MetricsTimeRange.THREE_MONTHS, state.selectedTimeRange)
        assertEquals(MetricsTimeRange.THREE_MONTHS, state.selectedPmcTimeRange)
        assertEquals(MetricsTimeRange.THREE_MONTHS, state.selectedCardioTimeRange)

        collectJob.cancel()
    }
}
