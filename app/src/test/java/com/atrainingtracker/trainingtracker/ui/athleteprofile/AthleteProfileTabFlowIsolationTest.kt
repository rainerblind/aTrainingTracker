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
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.physiology.WorkoutTrainingLoadSample
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvTrendMetricMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Unit and behavioral Robolectric Compose UI verification suite for `ATT-8134`
 * (`REQ-UI-2803.3`, `TST-UI-2803.3`):
 * - Non-PMC tab chip clicks (Body, HRV, Cardio-Power) and PMC time-range changes perform ZERO
 *   redundant Banister PMC recomputations (`PowerPhysiologyCalculator.computePerformanceManagementChart`).
 * - `_hrMaxBpm` changes via `SettingsDataStore.hrMaxFlow` reactively recompute PMC immediately.
 * - Unrelated `WEIGHT_KG` entries do not recompute PMC, while `FTP_WATTS` entries do.
 * - [AthleteProfileContent] time-range clicks invoke `onSelectTimeRange` once without double mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AthleteProfileTabFlowIsolationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockRepo: AthleteMetricsRepository
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockHrvRepo: HrvReadinessRepository
    private lateinit var mockSettingsDataStore: SettingsDataStore
    private lateinit var entriesFlow: MutableStateFlow<List<AthleteMetricEntry>>
    private lateinit var hrMaxFlow: MutableStateFlow<Int?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockApplication = mockk(relaxed = true)
        val tempFilesDir = java.io.File(
            System.getProperty("java.io.tmpdir", "/tmp"),
            "athlete_profile_isolation_${System.nanoTime()}"
        ).apply { mkdirs() }
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempFilesDir

        mockRepo = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)
        mockHrvRepo = mockk(relaxed = true)
        mockSettingsDataStore = mockk(relaxed = true)

        entriesFlow = MutableStateFlow(
            listOf(
                AthleteMetricEntry(id = 1L, metricType = AthleteMetricType.FTP_WATTS, value = 260.0, timestampMs = 1_700_000_000_000L),
                AthleteMetricEntry(id = 2L, metricType = AthleteMetricType.HR_REST_BPM, value = 52.0, timestampMs = 1_700_000_000_000L),
                AthleteMetricEntry(id = 3L, metricType = AthleteMetricType.WEIGHT_KG, value = 72.0, timestampMs = 1_700_000_000_000L)
            )
        )
        hrMaxFlow = MutableStateFlow(185)

        mockkObject(AthleteMetricsRepository.Companion)
        every { AthleteMetricsRepository.getInstance(any()) } returns mockRepo
        every { mockRepo.entriesFlow } returns entriesFlow
        coEvery { mockRepo.initializeAndRefresh() } returns Unit

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val loadSamples = listOf(
            WorkoutTrainingLoadSample(
                timestampMs = now - 3 * dayMs,
                activeTimeSeconds = 3600,
                powerTss = null,
                avgHrBpm = 152.0,
                avgPowerWatts = null,
                rpe = 6
            ),
            WorkoutTrainingLoadSample(
                timestampMs = now - dayMs,
                activeTimeSeconds = 5400,
                powerTss = null,
                avgHrBpm = 160.0,
                avgPowerWatts = null,
                rpe = 7
            )
        )
        mockkObject(WorkoutRepository.Companion)
        every { WorkoutRepository.getInstance(any()) } returns mockWorkoutRepo
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(emptyList())
        every { mockWorkoutRepo.workoutMutationEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } returns loadSamples
        every { mockWorkoutRepo.getCardioPowerDynamicsFitnessTrendSamples(any()) } returns emptyList()

        mockkObject(SettingsDataStore.Companion)
        every { SettingsDataStore.getInstance(any()) } returns mockSettingsDataStore
        every { mockSettingsDataStore.hrMaxFlow } returns hrMaxFlow

        HrvReadinessRepository.resetForTesting(mockHrvRepo)
        coEvery { mockHrvRepo.getAllEntries() } returns emptyList()

        val mockTuningDataStore = mockk<TuningPreferencesDataStore>(relaxed = true)
        every { mockTuningDataStore.tuningConfigFlow } returns flowOf(
            TuningConfig(
                enablePmcModel = true,
                enableCriticalPowerModel = true,
                enableCardioPowerDynamics = true
            )
        )
        mockkObject(TuningPreferencesDataStore.Companion)
        every { TuningPreferencesDataStore.getInstance(any()) } returns mockTuningDataStore
    }

    @After
    fun tearDown() {
        HrvReadinessRepository.resetForTesting(null)
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testNonPmcChipToggles_triggerZeroPmcRecomputations_andHrMaxFlowUpdatesReactively_tstUi2803_3() =
        runTest(testDispatcher) {
            mockkStatic(PowerPhysiologyCalculator::class)
            val observedHrMaxArgs = mutableListOf<Double?>()
            every {
                PowerPhysiologyCalculator.computePerformanceManagementChart(
                    workoutSamples = any(),
                    nowMs = any(),
                    ftpWatts = any(),
                    hrRestBpm = any(),
                    hrMaxBpm = any(),
                    zoneId = any(),
                    thresholdResolver = any()
                )
            } answers {
                observedHrMaxArgs.add(arg(4))
                callOriginal()
            }

            val viewModel = AthleteProfileViewModel(
                application = mockApplication,
                workoutRepository = mockWorkoutRepo,
                ioDispatcher = testDispatcher
            )
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            val baselinePmcCalls = observedHrMaxArgs.size
            assertTrue("Initial PMC calculation must run at least once", baselinePmcCalls >= 1)
            assertEquals("Initial reactive hrMax must be 185.0", 185.0, observedHrMaxArgs.last())
            val initialCtl = viewModel.pmcUiState.value.pmcSummary.currentCtl

            // Toggle chips across Body, HRV, Cardio-Power, and PMC range — NONE may recompute PMC!
            viewModel.selectPlotMetric(PlotMetricType.FTP)
            viewModel.selectHistoryFilter(AthleteMetricType.WEIGHT_KG)
            viewModel.selectHrvPosture(HrvPosture.SEATED)
            viewModel.selectHrvMetricMode(HrvTrendMetricMode.RMSSD)
            viewModel.toggleHrvTrainingLoad(true)
            viewModel.selectCardioTrendMetric(CardioTrendMetric.TAU)
            viewModel.selectCardioTimeRange(MetricsTimeRange.THREE_MONTHS)
            viewModel.selectPmcTimeRange(MetricsTimeRange.ONE_MONTH)
            viewModel.selectTimeRange(MetricsTimeRange.ONE_YEAR)
            advanceUntilIdle()

            assertEquals(
                "Unrelated tab chips and time-range toggles must NOT recompute Banister PMC",
                baselinePmcCalls,
                observedHrMaxArgs.size
            )
            assertEquals(PlotMetricType.FTP, viewModel.bodyUiState.value.selectedPlotMetric)
            assertEquals(HrvPosture.SEATED, viewModel.hrvUiState.value.selectedHrvPosture)
            assertEquals(CardioTrendMetric.TAU, viewModel.cardioUiState.value.selectedCardioTrendMetric)
            assertEquals(MetricsTimeRange.ONE_YEAR, viewModel.pmcUiState.value.selectedPmcTimeRange)

            // Updating WEIGHT_KG (not FTP or HR_REST) must NOT recompute Banister PMC
            entriesFlow.value = listOf(
                AthleteMetricEntry(id = 4L, metricType = AthleteMetricType.WEIGHT_KG, value = 71.2, timestampMs = 1_700_100_000_000L)
            ) + entriesFlow.value
            advanceUntilIdle()
            assertEquals(
                "Updating WEIGHT_KG alone must NOT recompute Banister PMC",
                baselinePmcCalls,
                observedHrMaxArgs.size
            )

            // Updating _hrMaxBpm reactively via SettingsDataStore.hrMaxFlow MUST immediately recompute PMC
            hrMaxFlow.value = 198
            advanceUntilIdle()

            assertEquals(
                "Updating hrMaxFlow must reactively trigger exactly 1 PMC recomputation",
                baselinePmcCalls + 1,
                observedHrMaxArgs.size
            )
            assertEquals(198.0, observedHrMaxArgs.last())
            assertTrue(
                "Lower HR-reserve fraction with higher hrMax (185 -> 198) must reactively change HR-based CTL ($initialCtl -> ${viewModel.pmcUiState.value.pmcSummary.currentCtl})",
                viewModel.pmcUiState.value.pmcSummary.currentCtl != initialCtl
            )
            assertEquals(
                viewModel.pmcUiState.value.pmcSummary.currentCtl,
                viewModel.uiState.value.pmcSummary.currentCtl,
                1e-6
            )

            verify(exactly = baselinePmcCalls + 1) {
                PowerPhysiologyCalculator.computePerformanceManagementChart(any(), any(), any(), any(), any(), any(), any())
            }
            job.cancel()
        }

    @Test
    fun testLthrAndSportHrMaxEntries_reactivelyRecomputePmc_whileWeightRemainsIsolated_tstSet135_3() =
        runTest(testDispatcher) {
            val now = System.currentTimeMillis()
            val dayMs = 86_400_000L
            every { mockWorkoutRepo.getWorkoutTrainingLoadRecords() } returns listOf(
                WorkoutTrainingLoadSample(
                    timestampMs = now - 2 * dayMs,
                    activeTimeSeconds = 3600,
                    powerTss = null,
                    avgHrBpm = 165.0,
                    avgPowerWatts = null,
                    rpe = 7,
                    bSportType = com.atrainingtracker.banalservice.BSportType.RUN
                )
            )
            mockkStatic(PowerPhysiologyCalculator::class)
            var pmcCalls = 0
            every {
                PowerPhysiologyCalculator.computePerformanceManagementChart(
                    workoutSamples = any(),
                    nowMs = any(),
                    ftpWatts = any(),
                    hrRestBpm = any(),
                    hrMaxBpm = any(),
                    zoneId = any(),
                    thresholdResolver = any()
                )
            } answers {
                pmcCalls++
                callOriginal()
            }

            val viewModel = AthleteProfileViewModel(
                application = mockApplication,
                workoutRepository = mockWorkoutRepo,
                ioDispatcher = testDispatcher
            )
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            advanceUntilIdle()

            val baselineCalls = pmcCalls
            assertTrue("Initial PMC computation must run", baselineCalls >= 1)
            val ctlBeforeLthr = viewModel.pmcUiState.value.pmcSummary.currentCtl

            // Emitting WEIGHT_KG must still trigger 0 PMC recomputations
            entriesFlow.value = listOf(
                AthleteMetricEntry(id = 10L, metricType = AthleteMetricType.WEIGHT_KG, value = 70.5, timestampMs = now - dayMs)
            ) + entriesFlow.value
            advanceUntilIdle()
            assertEquals("WEIGHT_KG emission must not recompute PMC", baselineCalls, pmcCalls)

            // Emitting LTHR_RUN_BPM on entriesFlow MUST reactively recompute PMC and update CTL
            entriesFlow.value = listOf(
                AthleteMetricEntry(id = 11L, metricType = AthleteMetricType.LTHR_RUN_BPM, value = 165.0, timestampMs = now - 5 * dayMs)
            ) + entriesFlow.value
            advanceUntilIdle()
            assertEquals("LTHR_RUN_BPM emission must trigger exactly 1 PMC recomputation", baselineCalls + 1, pmcCalls)
            val ctlAfterLthr = viewModel.pmcUiState.value.pmcSummary.currentCtl
            assertTrue("LTHR_RUN_BPM must alter HR-based CTL ($ctlBeforeLthr -> $ctlAfterLthr)", ctlAfterLthr != ctlBeforeLthr)

            // Emitting HR_MAX_RUN_BPM on entriesFlow MUST reactively recompute PMC
            entriesFlow.value = listOf(
                AthleteMetricEntry(id = 12L, metricType = AthleteMetricType.HR_MAX_RUN_BPM, value = 196.0, timestampMs = now - 5 * dayMs)
            ) + entriesFlow.value
            advanceUntilIdle()
            assertEquals("HR_MAX_RUN_BPM emission must trigger exactly 1 PMC recomputation", baselineCalls + 2, pmcCalls)

            job.cancel()
        }

    @Test
    fun testResolveWorkoutThresholdsInMemory_enforcesHistoricalPrecedenceAndRunFtpExclusion_tstSet135_3() {
        val tOct1 = 1_700_000_000_000L
        val tOct15 = 1_701_200_000_000L
        val tNov1 = 1_702_600_000_000L
        // Sorted DESC by (timestampMs, id) matching repository.entriesFlow
        val entries = listOf(
            AthleteMetricEntry(id = 9L, metricType = AthleteMetricType.HR_MAX_RUN_BPM, value = 198.0, timestampMs = tNov1),
            AthleteMetricEntry(id = 8L, metricType = AthleteMetricType.HR_MAX_BIKE_BPM, value = 190.0, timestampMs = tNov1),
            AthleteMetricEntry(id = 7L, metricType = AthleteMetricType.LTHR_RUN_BPM, value = 178.0, timestampMs = tNov1),
            AthleteMetricEntry(id = 6L, metricType = AthleteMetricType.FTP_WATTS, value = 300.0, timestampMs = tNov1),
            AthleteMetricEntry(id = 5L, metricType = AthleteMetricType.HR_MAX_RUN_BPM, value = 190.0, timestampMs = tOct1),
            AthleteMetricEntry(id = 4L, metricType = AthleteMetricType.HR_MAX_BIKE_BPM, value = 184.0, timestampMs = tOct1),
            AthleteMetricEntry(id = 3L, metricType = AthleteMetricType.LTHR_RUN_BPM, value = 172.0, timestampMs = tOct1),
            AthleteMetricEntry(id = 2L, metricType = AthleteMetricType.LTHR_BIKE_BPM, value = 166.0, timestampMs = tOct1),
            AthleteMetricEntry(id = 1L, metricType = AthleteMetricType.FTP_WATTS, value = 280.0, timestampMs = tOct1),
            AthleteMetricEntry(id = 0L, metricType = AthleteMetricType.HR_REST_BPM, value = 50.0, timestampMs = tOct1)
        )

        // RUN at Oct 15: gets Oct 1 RUN HRmax/LTHR and strictly null cycling FTP_WATTS
        val runOct15 = resolveWorkoutThresholdsInMemory(entries, com.atrainingtracker.banalservice.BSportType.RUN, tOct15, fallbackHrMaxBpm = 198.0)
        assertEquals(190.0, runOct15.hrMaxBpm!!, 1e-6)
        assertEquals(172.0, runOct15.lthrBpm!!, 1e-6)
        assertEquals(50.0, runOct15.hrRestBpm!!, 1e-6)
        assertEquals("RUN workouts must NOT inherit cycling FTP_WATTS", null, runOct15.ftpWatts)

        // BIKE at Oct 15: gets Oct 1 BIKE HRmax/LTHR and cycling FTP_WATTS
        val bikeOct15 = resolveWorkoutThresholdsInMemory(entries, com.atrainingtracker.banalservice.BSportType.BIKE, tOct15, fallbackHrMaxBpm = 198.0)
        assertEquals(184.0, bikeOct15.hrMaxBpm!!, 1e-6)
        assertEquals(166.0, bikeOct15.lthrBpm!!, 1e-6)
        assertEquals(280.0, bikeOct15.ftpWatts!!, 1e-6)

        // UNKNOWN at Oct 15: historical max(RUN=190, BIKE=184)=190 MUST take precedence over latest fallbackHrMaxBpm=198
        val unknownOct15 = resolveWorkoutThresholdsInMemory(entries, com.atrainingtracker.banalservice.BSportType.UNKNOWN, tOct15, fallbackHrMaxBpm = 198.0)
        assertEquals(190.0, unknownOct15.hrMaxBpm!!, 1e-6)
        assertEquals(null, unknownOct15.lthrBpm)

        // UNKNOWN with no sport HRmax rows: falls back to legacy fallbackHrMaxBpm
        val unknownFallback = resolveWorkoutThresholdsInMemory(
            entries = listOf(AthleteMetricEntry(id = 0L, metricType = AthleteMetricType.HR_REST_BPM, value = 50.0, timestampMs = tOct1)),
            sportType = com.atrainingtracker.banalservice.BSportType.UNKNOWN,
            timestampMs = tOct15,
            fallbackHrMaxBpm = 185.0
        )
        assertEquals(185.0, unknownFallback.hrMaxBpm!!, 1e-6)
        assertEquals(null, unknownFallback.lthrBpm)
    }

    @Test
    fun testAthleteProfileContent_pmcTimeRangeClickInvokesCallbackOnceWithoutDoubleMutation_tstUi2803_3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val range3mLabel = context.getString(R.string.athlete_profile_range_3m)
        val rangeCalls = mutableListOf<MetricsTimeRange>()

        composeRule.setContent {
            ATrainingTrackerTheme {
                AthleteProfileContent(
                    uiState = AthleteProfileUiState(
                        enablePmcModel = true,
                        enableCardioPowerDynamics = true,
                        selectedPmcTimeRange = MetricsTimeRange.SIX_MONTHS,
                        pmcSummary = PmcSummaryResult()
                    ),
                    onSelectPlotMetric = {},
                    onSelectTimeRange = { rangeCalls.add(it) },
                    onSelectHistoryFilter = {},
                    onAddEntry = { _, _, _ -> },
                    onUpdateEntry = { _, _, _, _ -> },
                    onDeleteEntry = { _, _ -> },
                    initialTab = 1 // PMC tab
                )
            }
        }
        composeRule.waitForIdle()

        // HorizontalPager pre-composes adjacent Body tab (beyondViewportPageCount = 1); click the displayed PMC pill
        val matchingNodes = composeRule.onAllNodesWithText(range3mLabel)
        val count = matchingNodes.fetchSemanticsNodes().size
        var clicked = false
        for (i in 0 until count) {
            if (matchingNodes[i].isDisplayed()) {
                matchingNodes[i].assertIsDisplayed().performClick()
                clicked = true
                break
            }
        }
        assertTrue("Expected a visible '$range3mLabel' time-range pill on PMC tab", clicked)
        composeRule.waitForIdle()

        assertEquals(
            "Clicking a PMC time-range pill must invoke onSelectTimeRange exactly ONCE (no double mutation)",
            listOf(MetricsTimeRange.THREE_MONTHS),
            rangeCalls
        )
    }
}
