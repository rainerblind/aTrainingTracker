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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.monetization.GarageLeagueDetector
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutMutationEvent
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutStatsSummary
import com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingHeroTotals
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingHorizon
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingJourneyData
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingJourneyRepository
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingJourneyViewModel
import com.atrainingtracker.trainingtracker.ui.sportingjourney.SportingSportFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies reactive recalculation of Banister PMC, Cardio-Power Trends, HRV daily loads,
 * Equipment stats, and Sporting Journey totals upon [WorkoutMutationEvent] even when
 * [WorkoutRepository.allWorkouts] is unhydrated/empty, while preserving `REQ-STB-020.4` 1-Hz
 * unfinished-workout isolation (`REQ-DAT-049.4`, `TST-DAT-045.4`, `ATT-9607`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AthleteProfileViewModelPmcDeletionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockMetricsRepo: AthleteMetricsRepository
    private lateinit var mockSettingsDataStore: SettingsDataStore
    private lateinit var mockTuningDataStore: TuningPreferencesDataStore
    private lateinit var mockHrvRepo: HrvReadinessRepository
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var allWorkoutsFlow: MutableStateFlow<List<WorkoutData>>
    private lateinit var mutationEventsFlow: MutableSharedFlow<WorkoutMutationEvent>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockMetricsRepo = mockk(relaxed = true)
        mockSettingsDataStore = mockk(relaxed = true)
        mockTuningDataStore = mockk(relaxed = true)
        mockHrvRepo = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)

        allWorkoutsFlow = MutableStateFlow(emptyList())
        mutationEventsFlow = MutableSharedFlow(extraBufferCapacity = 16)

        every { mockWorkoutRepo.allWorkouts } returns allWorkoutsFlow
        every { mockWorkoutRepo.workoutMutationEvents } returns mutationEventsFlow
        every { mockMetricsRepo.entriesFlow } returns MutableStateFlow(emptyList())
        every { mockSettingsDataStore.hrMaxFlow } returns flowOf(190)
        every { mockTuningDataStore.tuningConfigFlow } returns flowOf(
            TuningConfig(
                enablePmcModel = true,
                enableCardioPowerDynamics = true
            )
        )
        coEvery { mockHrvRepo.getAllEntries() } returns emptyList<HrvReadinessEntry>()

        mockkObject(AthleteMetricsRepository.Companion)
        every { AthleteMetricsRepository.getInstance(any()) } returns mockMetricsRepo
        mockkObject(SettingsDataStore.Companion)
        every { SettingsDataStore.getInstance(any()) } returns mockSettingsDataStore
        mockkObject(TuningPreferencesDataStore.Companion)
        every { TuningPreferencesDataStore.getInstance(any()) } returns mockTuningDataStore
        mockkObject(HrvReadinessRepository.Companion)
        every { HrvReadinessRepository.getInstance(any()) } returns mockHrvRepo
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isHealthConnectHrvImportEnabled() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testUnhydratedAllWorkouts_deletedEventClearsPmcPeakAndCardioPoint() = runTest(testDispatcher) {
        val nowMs = System.currentTimeMillis()
        val simulatorPeak = WorkoutTrainingLoadSample(
            timestampMs = nowMs - 3_600_000L,
            activeTimeSeconds = 7200,
            powerTss = 320.0,
            normalizedPowerWatts = 290.0,
            avgPowerWatts = 270.0,
            avgHrBpm = 165.0,
            rpe = 9
        )
        val cardioSample = CardioPowerTrendSample(
            workoutId = 99L,
            timestampMs = nowMs - 3_600_000L,
            record = CardioPowerDynamicsRecord(
                workoutId = 99L,
                tauStart = 28.0,
                hr0Start = 62.0,
                kStart = 0.42,
                hr200Start = 146.0,
                r2Start = 0.88,
                rmseStart = 2.1,
                tauEnd = 30.0,
                hr0End = 65.0,
                kEnd = 0.44,
                hr200End = 153.0,
                r2End = 0.86,
                rmseEnd = 2.4,
                deltaHr200 = 7.0,
                deltaTau = 2.0,
                deltaHr0 = 3.0
            )
        )

        var activeLoadRecords = listOf(simulatorPeak)
        var activeCardioRecords = listOf(cardioSample)
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } answers { activeLoadRecords }
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) } answers { activeCardioRecords }

        val viewModel = AthleteProfileViewModel(mockApp, mockWorkoutRepo, ioDispatcher = testDispatcher)
        val pmcJob = launch { viewModel.pmcUiState.collect {} }
        val cardioJob = launch { viewModel.cardioUiState.collect {} }

        try {
            advanceUntilIdle()
            assertTrue(
                "Initial PMC graph must reflect the simulator ride peak",
                viewModel.pmcUiState.value.pmcSummary.dailyPoints.isNotEmpty()
            )
            assertEquals(1, viewModel.cardioUiState.value.cardioTrendPoints.size)

            // Simulate deletion from SQLite while allWorkouts remains empty (unhydrated cold-start)
            activeLoadRecords = emptyList()
            activeCardioRecords = emptyList()
            assertTrue(allWorkoutsFlow.value.isEmpty())
            assertTrue(mutationEventsFlow.tryEmit(WorkoutMutationEvent.Deleted(workoutId = 99L)))
            advanceUntilIdle()

            assertTrue(
                "PMC peak must disappear immediately after WorkoutMutationEvent.Deleted",
                viewModel.pmcUiState.value.pmcSummary.dailyPoints.isEmpty()
            )
            assertTrue(
                "Cardio-Power trend point must disappear immediately after WorkoutMutationEvent.Deleted",
                viewModel.cardioUiState.value.cardioTrendPoints.isEmpty()
            )
        } finally {
            pmcJob.cancel()
            cardioJob.cancel()
        }
    }

    @Test
    fun testBulkDeletedEvent_recalculatesPmcImmediately() = runTest(testDispatcher) {
        val nowMs = System.currentTimeMillis()
        var activeLoadRecords = listOf(
            WorkoutTrainingLoadSample(nowMs - 86_400_000L, 3600, 180.0, 250.0, 240.0, 155.0, 7)
        )
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } answers { activeLoadRecords }
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returns emptyList()

        val viewModel = AthleteProfileViewModel(mockApp, mockWorkoutRepo, ioDispatcher = testDispatcher)
        val pmcJob = launch { viewModel.pmcUiState.collect {} }
        try {
            advanceUntilIdle()
            assertTrue(viewModel.pmcUiState.value.pmcSummary.dailyPoints.isNotEmpty())

            activeLoadRecords = emptyList()
            assertTrue(mutationEventsFlow.tryEmit(WorkoutMutationEvent.BulkDeleted(listOf(10L, 11L))))
            advanceUntilIdle()

            assertTrue(viewModel.pmcUiState.value.pmcSummary.dailyPoints.isEmpty())
        } finally {
            pmcJob.cancel()
        }
    }

    @Test
    fun testUnfinishedLiveWorkoutUpdates_triggerZeroHistoricalDbRequeries() = runTest(testDispatcher) {
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } returns emptyList()
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) } returns emptyList()

        val viewModel = AthleteProfileViewModel(mockApp, mockWorkoutRepo, ioDispatcher = testDispatcher)
        advanceUntilIdle()

        val initialLoadCalls = 1
        verify(exactly = initialLoadCalls) { mockWorkoutRepo.getWorkoutTrainingLoadRecords() }
        verify(exactly = initialLoadCalls) { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) }

        // Push unfinished live updates — REQ-STB-020.4 must suppress historical re-queries
        val liveTick1 = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 500L
            every { finished } returns false
            every { totalDistance } returns 100.0
        }
        val liveTick2 = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 500L
            every { finished } returns false
            every { totalDistance } returns 250.0
        }
        allWorkoutsFlow.value = listOf(liveTick1)
        advanceUntilIdle()
        allWorkoutsFlow.value = listOf(liveTick2)
        advanceUntilIdle()

        verify(exactly = initialLoadCalls) { mockWorkoutRepo.getWorkoutTrainingLoadRecords() }
        verify(exactly = initialLoadCalls) { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) }
        assertTrue(viewModel.pmcUiState.value.pmcSummary.dailyPoints.isEmpty())
    }

    @Test
    fun testEquipmentViewModel_reloadsStatsOnWorkoutDeletedEvent() = runTest(testDispatcher) {
        val mockEquipRepo = mockk<EquipmentRepository>(relaxed = true)
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        val mockDeviceRepo = mockk<DeviceDataRepository>(relaxed = true)
        val mockGarageDetector = mockk<GarageLeagueDetector>(relaxed = true)
        val equipData = com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.EquipmentData(
            7L,
            "Road Bike",
            BSportType.BIKE,
            com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.ROAD,
            "Road Bike",
            "b1",
            false
        )
        every { mockEquipRepo.getEquipmentItems(BSportType.BIKE) } returns listOf(equipData)
        every { mockEquipRepo.getEquipmentItems(BSportType.RUN) } returns emptyList()
        every { mockWorkoutRepo.getEquipmentStats(7L) } returnsMany listOf(
            WorkoutStatsSummary(totalDistanceM = 50_000.0, count = 2),
            WorkoutStatsSummary(totalDistanceM = 20_000.0, count = 1)
        )

        val vm = EquipmentViewModel(
            application = mockApp,
            ioDispatcher = testDispatcher,
            equipmentRepository = mockEquipRepo,
            sportTypesRepository = mockSportRepo,
            deviceDataRepository = mockDeviceRepo,
            workoutRepository = mockWorkoutRepo,
            syncStatusFlow = MutableStateFlow(false),
            garageLeagueDetector = mockGarageDetector
        )
        advanceUntilIdle()
        verify(exactly = 1) { mockWorkoutRepo.getEquipmentStats(7L) }

        assertTrue(mutationEventsFlow.tryEmit(WorkoutMutationEvent.Deleted(workoutId = 99L, equipmentId = 7L)))
        advanceUntilIdle()

        verify(exactly = 2) { mockWorkoutRepo.getEquipmentStats(7L) }
        assertEquals(1, vm.bikes.value.size)
    }

    @Test
    fun testSportingJourneyViewModel_reloadsOnWorkoutDeletedEvent() = runTest(testDispatcher) {
        val mockJourneyRepo = mockk<SportingJourneyRepository>()
        coEvery { mockJourneyRepo.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.ALL) } returnsMany listOf(
            SportingJourneyData(heroTotals = SportingHeroTotals(totalWorkouts = 5), isLoading = false),
            SportingJourneyData(heroTotals = SportingHeroTotals(totalWorkouts = 4), isLoading = false)
        )

        val vm = SportingJourneyViewModel(
            application = mockApp,
            repository = mockJourneyRepo,
            workoutRepository = mockWorkoutRepo
        )
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.heroTotals.totalWorkouts)

        assertTrue(mutationEventsFlow.tryEmit(WorkoutMutationEvent.Deleted(workoutId = 99L)))
        advanceUntilIdle()

        assertEquals(4, vm.uiState.value.heroTotals.totalWorkouts)
        coVerify(exactly = 2) { mockJourneyRepo.loadJourneyData(SportingHorizon.AllTime, SportingSportFilter.ALL) }
    }

    @Test
    fun testColdStartHydration_withRealMultiThreadedIoDispatcher_neverDropsInitialTrigger() = kotlinx.coroutines.runBlocking {
        // Regression test (Stage 6 Bounce 1): Channel.CONFLATED must retain the initial refresh token
        // even when allWorkouts emits on a different Dispatchers.IO thread before the consumer subscribes.
        val nowMs = System.currentTimeMillis()
        val sample = WorkoutTrainingLoadSample(
            timestampMs = nowMs - 3_600_000L,
            activeTimeSeconds = 3600,
            powerTss = 210.0,
            normalizedPowerWatts = 260.0,
            avgPowerWatts = 245.0,
            avgHrBpm = 158.0,
            rpe = 8
        )
        val cardioSample = CardioPowerTrendSample(
            workoutId = 77L,
            timestampMs = nowMs - 3_600_000L,
            record = CardioPowerDynamicsRecord(
                workoutId = 77L,
                tauStart = 29.0,
                hr0Start = 60.0,
                kStart = 0.41,
                hr200Start = 144.0,
                r2Start = 0.89,
                rmseStart = 2.0,
                tauEnd = 31.0,
                hr0End = 63.0,
                kEnd = 0.43,
                hr200End = 150.0,
                r2End = 0.87,
                rmseEnd = 2.2,
                deltaHr200 = 6.0,
                deltaTau = 2.0,
                deltaHr0 = 3.0
            )
        )
        var currentLoad = listOf(sample)
        var currentCardio = listOf(cardioSample)
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } answers { currentLoad }
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(0.50) } answers { currentCardio }

        Dispatchers.setMain(Dispatchers.Default)
        try {
            repeat(20) { iteration ->
                val vm = AthleteProfileViewModel(mockApp, mockWorkoutRepo, ioDispatcher = Dispatchers.IO)
                val pmcJob = launch(Dispatchers.Default) { vm.pmcUiState.collect {} }
                val cardioJob = launch(Dispatchers.Default) { vm.cardioUiState.collect {} }
                try {
                    val hydrated = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                        while (
                            vm.pmcUiState.value.pmcSummary.dailyPoints.isEmpty() ||
                            vm.cardioUiState.value.cardioTrendPoints.isEmpty()
                        ) {
                            kotlinx.coroutines.delay(5L)
                        }
                        true
                    }
                    assertTrue(
                        "Cold-start hydration dropped initial trigger on iteration $iteration",
                        hydrated == true
                    )
                } finally {
                    pmcJob.cancel()
                    cardioJob.cancel()
                    vm.viewModelScope.cancel()
                }
            }

            // Also verify manual loadCardioTrendSamples() routes through the same serialized refresh channel
            val vm = AthleteProfileViewModel(mockApp, mockWorkoutRepo, ioDispatcher = Dispatchers.IO)
            val cardioJob = launch(Dispatchers.Default) { vm.cardioUiState.collect {} }
            try {
                val initial = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                    while (vm.cardioUiState.value.cardioTrendPoints.isEmpty()) {
                        kotlinx.coroutines.delay(5L)
                    }
                    true
                }
                assertTrue("Initial cardio trend must hydrate", initial == true)

                currentCardio = emptyList()
                vm.loadCardioTrendSamples()
                val cleared = kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                    while (vm.cardioUiState.value.cardioTrendPoints.isNotEmpty()) {
                        kotlinx.coroutines.delay(5L)
                    }
                    true
                }
                assertTrue("loadCardioTrendSamples() must clear via serialized channel", cleared == true)
            } finally {
                cardioJob.cancel()
                vm.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.setMain(testDispatcher)
        }
    }
}
