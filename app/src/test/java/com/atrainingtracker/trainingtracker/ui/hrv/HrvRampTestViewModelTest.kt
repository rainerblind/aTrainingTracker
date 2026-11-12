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

package com.atrainingtracker.trainingtracker.ui.hrv

import android.app.Application
import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Contract tests for [HrvRampTestViewModel] implementing TST-TRK-072 (TST-TRK-072.1 .. TST-TRK-072.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HrvRampTestViewModelTest {

    private lateinit var application: Application
    private lateinit var banalRepository: BANALServiceRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        banalRepository = BANALServiceRepository.getInstance(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testProtocolConfiguration_CyclingAndRunningPresets_TST_TRK_072_1() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)

        // 1. Initial State defaults
        val defaultState = viewModel.uiState.value
        assertEquals(HrvTestPhase.CONFIG, defaultState.phase)
        assertEquals(HrvTestSport.CYCLING, defaultState.config.sport)
        assertEquals(HrvTestProtocolType.GUIDED_STEP, defaultState.config.protocolType)
        assertEquals(240, defaultState.config.stepDurationSec)
        assertEquals(100.0, defaultState.config.startingTarget, 0.001)
        assertEquals(15.0, defaultState.config.stepIncrement, 0.001)

        // 2. Switch to Running Preset
        viewModel.setSport(HrvTestSport.RUNNING)
        val runningState = viewModel.uiState.value
        assertEquals(HrvTestSport.RUNNING, runningState.config.sport)
        assertEquals(8.0, runningState.config.startingTarget, 0.001)
        assertEquals(0.5, runningState.config.stepIncrement, 0.001)
        assertEquals(8.0, runningState.targetIntensity, 0.001)

        // 3. Clamped Step Duration (minimum 180 s)
        viewModel.setStepDurationSec(120) // Attempts below 180
        assertEquals(180, viewModel.uiState.value.config.stepDurationSec)

        // 4. Protocol Type update
        viewModel.setProtocolType(HrvTestProtocolType.GUIDED_RAMP)
        assertEquals(HrvTestProtocolType.GUIDED_RAMP, viewModel.uiState.value.config.protocolType)
    }

    @Test
    fun testPreFlightSensorQualityTiers_TST_TRK_072_2() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)

        // 1. High Quality: RR stream present, artifact rate <= 3.0%
        viewModel.evaluatePreFlightQuality(artifactRate = 1.8, hasRrStream = true, isSmoothed = false)
        assertEquals(SensorQualityTier.HIGH, viewModel.uiState.value.sensorQualityTier)
        assertTrue(viewModel.uiState.value.canStartTest)
        assertEquals(R.string.hrv_test_preflight_pass, viewModel.uiState.value.preFlightMessageResId)

        // 2. Moderate Quality: artifact rate between 3.0% and 5.0%
        viewModel.evaluatePreFlightQuality(artifactRate = 4.2, hasRrStream = true, isSmoothed = false)
        assertEquals(SensorQualityTier.MODERATE, viewModel.uiState.value.sensorQualityTier)
        assertTrue(viewModel.uiState.value.canStartTest)
        assertEquals(R.string.hrv_test_preflight_warning, viewModel.uiState.value.preFlightMessageResId)

        // 3. Unsuitable: artifact rate > 5.0%
        viewModel.evaluatePreFlightQuality(artifactRate = 6.5, hasRrStream = true, isSmoothed = false)
        assertEquals(SensorQualityTier.UNSUITABLE, viewModel.uiState.value.sensorQualityTier)
        assertFalse(viewModel.uiState.value.canStartTest)
        assertEquals(R.string.hrv_test_preflight_fail, viewModel.uiState.value.preFlightMessageResId)

        // 4. Unsuitable: No RR stream (e.g. optical wrist sensor)
        viewModel.evaluatePreFlightQuality(artifactRate = 0.0, hasRrStream = false, isSmoothed = false)
        assertEquals(SensorQualityTier.UNSUITABLE, viewModel.uiState.value.sensorQualityTier)
        assertFalse(viewModel.uiState.value.canStartTest)
        assertEquals(R.string.hrv_test_preflight_no_sensor, viewModel.uiState.value.preFlightMessageResId)

        // 5. Unsuitable: Firmware smoothing detected
        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = true)
        assertEquals(SensorQualityTier.UNSUITABLE, viewModel.uiState.value.sensorQualityTier)
        assertFalse(viewModel.uiState.value.canStartTest)
        assertEquals(R.string.hrv_test_preflight_fail, viewModel.uiState.value.preFlightMessageResId)
    }

    @Test
    fun testLifecycleStateMachine_WarmupToTestingToCooldown_TST_TRK_072_1() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)

        // Pre-flight pass
        viewModel.evaluatePreFlightQuality(artifactRate = 1.5, hasRrStream = true, isSmoothed = false)

        // Configure short warmup (2s) and short step (3s) for deterministic progression
        viewModel.setWarmupDurationSec(2)
        viewModel.setStepDurationSec(180) // Clamped to 180

        viewModel.startProtocol(autoStartTimer = false)
        assertEquals(HrvTestPhase.WARMUP, viewModel.uiState.value.phase)
        assertEquals(100.0, viewModel.uiState.value.targetIntensity, 0.001)
        assertEquals(2, viewModel.uiState.value.stepRemainingSec)

        // Tick 1
        viewModel.processTick()
        assertEquals(HrvTestPhase.WARMUP, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.stepRemainingSec)

        // Tick 2: Warmup finishes, transitions to TESTING Step 1
        viewModel.processTick()
        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.currentStepNumber)
        assertEquals(100.0, viewModel.uiState.value.targetIntensity, 0.001)

        // Transition to cooldown
        viewModel.transitionToCooldown()
        assertEquals(HrvTestPhase.COOLDOWN, viewModel.uiState.value.phase)
        assertEquals(50.0, viewModel.uiState.value.targetIntensity, 0.001) // 50% recovery load

        // Finish test
        viewModel.finishTest()
        assertEquals(HrvTestPhase.FINISHED, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun testStepProgressionPacingIncrements_TST_TRK_072_4() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)
        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = false)
        viewModel.setWarmupDurationSec(0) // Skip warm-up directly to testing
        viewModel.setStartingTarget(120.0)
        viewModel.setStepIncrement(20.0)

        viewModel.startProtocol(autoStartTimer = false)
        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)
        assertEquals(1, viewModel.uiState.value.currentStepNumber)
        assertEquals(120.0, viewModel.uiState.value.targetIntensity, 0.001)

        // Simulate step countdown expiring (simulate remaining seconds down to 0)
        val stepSec = viewModel.uiState.value.stepRemainingSec
        for (i in 0 until stepSec) {
            viewModel.processTick()
        }

        // Must advance to Step 2 with +20 W increment
        assertEquals(2, viewModel.uiState.value.currentStepNumber)
        assertEquals(140.0, viewModel.uiState.value.targetIntensity, 0.001)
    }

    @Test
    fun testEarlySubExhaustionAutoCompletionDetection_TST_TRK_072_3() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)
        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = false)
        viewModel.setWarmupDurationSec(0)
        viewModel.startProtocol(autoStartTimer = false)

        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.earlyCompletionDetected)

        // 1. Transient dip below 0.45 for 30s
        for (i in 1..30) {
            viewModel.processTick(injectedDfaA1 = 0.42)
        }
        assertEquals(30, viewModel.uiState.value.sub45DurationSec)
        assertFalse(viewModel.uiState.value.earlyCompletionDetected)

        // 2. Recovery above 0.45 resets counter
        viewModel.processTick(injectedDfaA1 = 0.52)
        assertEquals(0, viewModel.uiState.value.sub45DurationSec)
        assertFalse(viewModel.uiState.value.earlyCompletionDetected)

        // 3. Sustained drop for 59s
        for (i in 1..59) {
            viewModel.processTick(injectedDfaA1 = 0.41)
        }
        assertEquals(59, viewModel.uiState.value.sub45DurationSec)
        assertFalse(viewModel.uiState.value.earlyCompletionDetected)
        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)

        // 4. 60th second: triggers early completion alert!
        viewModel.processTick(injectedDfaA1 = 0.40)
        assertEquals(60, viewModel.uiState.value.sub45DurationSec)
        assertTrue(viewModel.uiState.value.earlyCompletionDetected)
        assertEquals(HrvTestPhase.EARLY_COMPLETION, viewModel.uiState.value.phase)
    }

    @Test
    fun testContinuousRampPacingProgression_TST_TRK_072_1() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)
        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = false)
        viewModel.setProtocolType(HrvTestProtocolType.GUIDED_RAMP)
        viewModel.setWarmupDurationSec(0)
        viewModel.setStartingTarget(100.0)

        viewModel.startProtocol(autoStartTimer = false)
        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)
        assertEquals(100.0, viewModel.uiState.value.targetIntensity, 0.001)

        // Advance 60 seconds (1 minute at +15 W/min slope)
        for (i in 1..60) {
            viewModel.processTick()
        }

        // At 60 seconds elapsed, target must be 100 + 15 = 115 W
        assertEquals(115.0, viewModel.uiState.value.targetIntensity, 0.001)

        // Advance another 60 seconds (2 minutes at +15 W/min slope)
        for (i in 1..60) {
            viewModel.processTick()
        }
        assertEquals(130.0, viewModel.uiState.value.targetIntensity, 0.001)
    }

    @Test
    fun testCancelProtocol_TST_TRK_072_1() = runTest(testDispatcher) {
        val viewModel = HrvRampTestViewModel(application, banalRepository, testDispatcher)
        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = false)
        viewModel.setWarmupDurationSec(0)
        viewModel.startProtocol(autoStartTimer = false)
        assertEquals(HrvTestPhase.TESTING, viewModel.uiState.value.phase)

        viewModel.cancelTest()
        assertEquals(HrvTestPhase.CONFIG, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.isCompleted)
        assertFalse(viewModel.uiState.value.earlyCompletionDetected)
    }

    @Test
    fun testStartProtocolAndTagging_TST_TRK_072_6() = runTest(testDispatcher) {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val viewModel = HrvRampTestViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            workoutRepository = mockWorkoutRepo,
            dispatcher = testDispatcher
        )

        viewModel.evaluatePreFlightQuality(artifactRate = 1.0, hasRrStream = true, isSmoothed = false)
        viewModel.setProtocolType(HrvTestProtocolType.GUIDED_STEP)
        viewModel.setStepDurationSec(240)
        viewModel.setStepIncrement(15.0)

        viewModel.tagActiveWorkoutWithProtocol(targetWorkoutId = 42L)

        val slot = slot<ContentValues>()
        verify { mockWorkoutRepo.updateWorkoutSummary(42L, capture(slot)) }
        assertEquals("GUIDED_STEP", slot.captured.getAsString(WorkoutSummariesDatabaseManager.WorkoutSummaries.METHOD))
        assertTrue(slot.captured.getAsString(WorkoutSummariesDatabaseManager.WorkoutSummaries.GOAL).contains("stepDurationSec=240"))
        assertTrue(viewModel.isWorkoutTagged)
    }

    @Test
    fun testFinishTest_tagsWorkoutAndSetsCompleted_TST_TRK_072_6() = runTest(testDispatcher) {
        val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)
        val viewModel = HrvRampTestViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            workoutRepository = mockWorkoutRepo,
            dispatcher = testDispatcher
        )

        viewModel.setProtocolType(HrvTestProtocolType.GUIDED_RAMP)
        viewModel.tagActiveWorkoutWithProtocol(targetWorkoutId = 99L)
        viewModel.finishTest()

        assertTrue(viewModel.uiState.value.isCompleted)
        assertEquals(HrvTestPhase.FINISHED, viewModel.uiState.value.phase)
        val slot = slot<ContentValues>()
        verify { mockWorkoutRepo.updateWorkoutSummary(99L, capture(slot)) }
        assertEquals("GUIDED_RAMP", slot.captured.getAsString(WorkoutSummariesDatabaseManager.WorkoutSummaries.METHOD))
    }
}
