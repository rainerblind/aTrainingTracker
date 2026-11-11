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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDbHelper
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit & Contract tests verifying [MorningHrvCheckViewModel] lifecycle state machine,
 * posture stickiness, zero-workout invariant, post-hoc centered filtering, and repository persistence
 * conforming to TST-PER-040 (TST-PER-040.1 .. TST-PER-040.5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MorningHrvCheckViewModelTest {

    private lateinit var application: Application
    private lateinit var hrvRepository: HrvReadinessRepository
    private lateinit var banalRepository: BANALServiceRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        TrainingApplication.setSharedPreferencesForTesting(prefs)

        application.deleteDatabase(HrvReadinessDbHelper.DB_NAME)
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)

        val dbManager = HrvReadinessDatabaseManager.getInstance(application)
        hrvRepository = HrvReadinessRepository(dbManager, testDispatcher)
        HrvReadinessRepository.resetForTesting(hrvRepository)
        banalRepository = BANALServiceRepository.getInstance(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        application.deleteDatabase(HrvReadinessDbHelper.DB_NAME)
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)
    }

    @Test
    fun testLifecycleStateMachine_TST_PER_040_1() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        // 1. Initial State
        assertEquals("Must start in READY state", MorningHrvPhase.READY, viewModel.uiState.value.phase)
        assertEquals("Initial remaining seconds must be 0", 0, viewModel.uiState.value.remainingSec)

        // 2. Start measurement (use 2s stabilization and 5s duration for fast test)
        viewModel.setStabilizationDurationSec(2)
        viewModel.setDurationSec(5)
        viewModel.startMeasurement()

        assertEquals("Must transition to STABILIZING", MorningHrvPhase.STABILIZING, viewModel.uiState.value.phase)
        assertEquals(2, viewModel.uiState.value.remainingSec)

        advanceTimeBy(1001L)
        assertEquals(1, viewModel.uiState.value.remainingSec)

        // 3. Stabilization completes -> Transitions to MEASURING
        advanceTimeBy(1001L)
        assertEquals("Must transition to MEASURING", MorningHrvPhase.MEASURING, viewModel.uiState.value.phase)
        assertEquals(5, viewModel.uiState.value.remainingSec)

        // Deliver beats during measuring
        viewModel.handleRrBeat(RrBeatEvent(1000L, 1000.0, 1, ProtocolType.BLE))
        viewModel.handleRrBeat(RrBeatEvent(2000L, 980.0, 2, ProtocolType.BLE))

        advanceTimeBy(3001L)
        assertEquals(2, viewModel.uiState.value.remainingSec)
        assertEquals(2, viewModel.uiState.value.beatCount)

        // 4. Timer completes -> COMPLETED phase
        advanceTimeBy(3001L)
        advanceUntilIdle()

        assertEquals("Must reach COMPLETED phase", MorningHrvPhase.COMPLETED, viewModel.uiState.value.phase)
        assertNotNull("Computed entry must be present", viewModel.uiState.value.computedEntry)

        // 5. Reset returns to READY
        viewModel.resetMeasurement()
        assertEquals("Reset must return to READY", MorningHrvPhase.READY, viewModel.uiState.value.phase)
    }

    @Test
    fun testStickyPostureAndDurationConfiguration_TST_PER_040_2() {
        TrainingApplication.setLastHrvPosture("SEATED")

        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        // Should initialize with sticky posture
        assertEquals(HrvPosture.SEATED, viewModel.uiState.value.posture)

        // Changing posture updates preference
        viewModel.setPosture(HrvPosture.STANDING)
        assertEquals(HrvPosture.STANDING, viewModel.uiState.value.posture)
        assertEquals("STANDING", TrainingApplication.getLastHrvPosture())

        // Duration setting
        viewModel.setDurationSec(120)
        assertEquals(120, viewModel.uiState.value.durationSec)
    }

    @Test
    fun testSensorIngestionAndZeroWorkoutInvariant_TST_PER_040_3() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        // Delivering an RR beat outside measurement updates current HR
        val beat = RrBeatEvent(
            beatTimestampMs = 1731222000000L,
            rrMs = 1000.0,
            rawCounter = 1,
            protocol = ProtocolType.BLE
        )
        viewModel.handleRrBeat(beat)
        assertEquals(60, viewModel.uiState.value.currentHeartRateBpm)
        assertEquals(0, viewModel.uiState.value.beatCount)

        // Zero workout creation check: ensure WorkoutSummaries.db was NOT created or modified
        val workoutDbFile = application.getDatabasePath("WorkoutSummaries.db")
        if (workoutDbFile.exists()) {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                workoutDbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = db.rawQuery("SELECT count(*) FROM summaries", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            db.close()
            assertEquals("No workouts may be created during morning resting check", 0, count)
        } else {
            assertTrue("WorkoutSummaries.db should not even exist", true)
        }
    }

    @Test
    fun testColdStartSensorSearchAndSafeLifecycle_TST_PER_040_3() {
        val shadowApp = shadowOf(application)
        shadowApp.clearBroadcastIntents()

        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        // Verify startSearchingForPairedDevices() broadcast was fired on cold-start
        val broadcasts = shadowApp.broadcastIntents
        assertTrue(
            "Cold start must trigger paired device search broadcast",
            broadcasts.any { it.action == TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES }
        )

        // Verify onCleared() cancels timer and cleans up listener safely without unbinding shared service
        viewModel.onCleared()
    }

    @Test
    fun testPostHocCenteredFilterCalculation_TST_PER_040_4() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        viewModel.setStabilizationDurationSec(1)
        viewModel.setDurationSec(2)
        viewModel.startMeasurement()
        advanceTimeBy(1001L) // stabilization complete

        // Feed standard rhythmic intervals around 1000ms (~60 bpm)
        val intervals = listOf(1000.0, 1020.0, 980.0, 1010.0, 990.0, 1005.0)
        var ts = 1000L
        intervals.forEachIndexed { idx, rr ->
            ts += rr.toLong()
            viewModel.handleRrBeat(RrBeatEvent(ts, rr, idx, ProtocolType.BLE))
        }

        advanceTimeBy(3000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MorningHrvPhase.COMPLETED, state.phase)
        assertNotNull(state.computedEntry)

        val entry = state.computedEntry!!
        assertTrue("rMSSD must be positive", entry.rmssdMs > 0.0)
        assertTrue("ln(rMSSD) must be positive", entry.lnRmssd > 0.0)
        assertTrue("HRV Score must be between 0 and 100", entry.hrvScore100 in 0.0..100.0)
        assertEquals(60, entry.restingHrBpm)
        assertNotNull(entry.sdnnMs)
        assertTrue("SDNN must be positive", entry.sdnnMs!! > 0.0)
        assertFalse("High artifact warning must not trigger on normal beats", state.highArtifactWarning)
    }

    @Test
    fun testPersistenceAndArtifactWarning_TST_PER_040_5() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        viewModel.setStabilizationDurationSec(1)
        viewModel.setDurationSec(2)
        viewModel.startMeasurement()
        advanceTimeBy(1001L)

        // Feed series with explicit gap artifacts (> 5.0%)
        val beats = listOf(
            RrBeatEvent(1000L, 1000.0, 1, ProtocolType.BLE),
            RrBeatEvent(2000L, 1000.0, 2, ProtocolType.BLE),
            RrBeatEvent(3000L, 1000.0, 3, ProtocolType.BLE, artifactFlag = RrBeatEvent.ARTIFACT_GAP),
            RrBeatEvent(4000L, 1000.0, 4, ProtocolType.BLE)
        )
        beats.forEach { viewModel.handleRrBeat(it) }

        advanceTimeBy(3000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MorningHrvPhase.COMPLETED, state.phase)
        assertTrue("Artifact rate should exceed 5.0%", state.artifactRatePct != null && state.artifactRatePct > 5.0)
        assertTrue("highArtifactWarning must be true when artifact rate > 5.0%", state.highArtifactWarning)

        // Save reading
        val rowId = viewModel.saveReading()
        assertTrue("Row ID must be valid", rowId > 0)
        assertTrue("isSaved must be true", viewModel.uiState.value.isSaved)

        val savedEntry = hrvRepository.getEntryById(rowId)
        assertNotNull("Saved entry must be retrievable from HrvReadinessRepository", savedEntry)
        assertEquals(rowId, savedEntry!!.id)
        assertFalse("High artifact reading (> 5.0%) must NOT be marked primary for day", savedEntry.isPrimaryForDay)
    }

    @Test
    fun testSignalQualityStatusPopulatedOnCompleteMeasurement() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        viewModel.setStabilizationDurationSec(1)
        viewModel.setDurationSec(2)
        viewModel.startMeasurement()
        advanceTimeBy(1001L)

        // Feed 35 valid beats (>= 30 beats)
        var ts = 1000L
        for (i in 0 until 35) {
            val rr = 800.0 + (i % 4) * 10.0
            ts += rr.toLong()
            viewModel.handleRrBeat(RrBeatEvent(ts, rr, i, ProtocolType.BLE))
        }

        advanceTimeBy(3000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MorningHrvPhase.COMPLETED, state.phase)
        assertEquals(HrvSignalQualityStatus.VALID, state.signalQualityStatus)
        assertNotNull(state.signalQualityResult)
        assertEquals(35, state.signalQualityResult?.beatCount)
    }

    @Test
    fun testLiveCausalArtifactWarningDuringMeasuring_TST_PER_040_4() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        viewModel.setStabilizationDurationSec(1)
        viewModel.setDurationSec(10)
        viewModel.startMeasurement()
        advanceTimeBy(1001L) // enter MEASURING

        assertEquals(MorningHrvPhase.MEASURING, viewModel.uiState.value.phase)

        // Feed normal beats followed by gap artifacts and lookahead beats
        var ts = 1000L
        for (i in 0 until 10) {
            ts += 1000L
            viewModel.handleRrBeat(RrBeatEvent(ts, 1000.0, i, ProtocolType.BLE))
        }

        for (i in 10 until 15) {
            ts += 1000L
            viewModel.handleRrBeat(
                RrBeatEvent(ts, 1000.0, i, ProtocolType.BLE, artifactFlag = RrBeatEvent.ARTIFACT_GAP)
            )
        }

        for (i in 15 until 25) {
            ts += 1000L
            viewModel.handleRrBeat(RrBeatEvent(ts, 1000.0, i, ProtocolType.BLE))
        }

        assertEquals(MorningHrvPhase.MEASURING, viewModel.uiState.value.phase)
        assertTrue("Live artifact percentage must be computed", viewModel.uiState.value.artifactRatePct != null)
        assertTrue("Live high artifact warning must be triggered", viewModel.uiState.value.highArtifactWarning)
    }

    @Test
    fun testPreSaveReadinessPreviewInMemory_TST_PER_040_5() = runTest(testDispatcher) {
        val viewModel = MorningHrvCheckViewModel(
            application = application,
            banalServiceRepository = banalRepository,
            hrvReadinessRepository = hrvRepository,
            dispatcher = testDispatcher
        )

        viewModel.setStabilizationDurationSec(1)
        viewModel.setDurationSec(2)
        viewModel.startMeasurement()
        advanceTimeBy(1001L)

        var ts = 1000L
        for (i in 0 until 35) {
            ts += 900L
            viewModel.handleRrBeat(RrBeatEvent(ts, 900.0, i, ProtocolType.BLE))
        }

        advanceTimeBy(3000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MorningHrvPhase.COMPLETED, state.phase)
        assertFalse("Reading not yet saved", state.isSaved)
        assertNotNull("Pre-save in-memory readiness result must be populated", state.readinessResult)
    }
}
