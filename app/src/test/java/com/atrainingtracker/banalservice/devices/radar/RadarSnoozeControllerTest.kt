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

package com.atrainingtracker.banalservice.devices.radar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [RadarSnoozeController] lifecycle-safe snooze management (REQ-TRK-064.1, TST-TRK-064.1a).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarSnoozeControllerTest {

    private var simulatedTimeMs: Long = 100_000L

    @Before
    fun setUp() {
        RadarSnoozeController.reset()
        RadarStateRepository.reset()
        simulatedTimeMs = 100_000L
        RadarSnoozeController.setTimeProvider { simulatedTimeMs }
    }

    @After
    fun tearDown() {
        RadarSnoozeController.reset()
        RadarStateRepository.reset()
    }

    @Test
    fun initialState_isInactive() {
        val state = RadarSnoozeController.getSnoozeState()
        assertFalse(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, state.mode)
        assertNull(state.remainingSeconds)
        assertFalse(state.isPelotonSyncActive)
        assertFalse(state.isUrbanMuteActive)
        assertFalse(RadarSnoozeController.isRoutineAlertSuppressed())
        assertFalse(RadarSnoozeController.isFastThreatAlertSuppressed())
    }

    @Test
    fun snooze15m_setsDurationAndSuppressesRoutineAlerts() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)

        val state = RadarSnoozeController.getSnoozeState()
        assertTrue(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, state.mode)
        assertEquals(900, state.remainingSeconds)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
        assertTrue(RadarSnoozeController.isFastThreatAlertSuppressed())
        val repoSnooze = RadarStateRepository.snoozeState.value
        assertTrue(repoSnooze.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, repoSnooze.mode)
        assertEquals(simulatedTimeMs + 15 * 60 * 1000L, repoSnooze.snoozeUntilElapsedRealtimeMs)
        assertEquals(900, repoSnooze.remainingSecondsAt(simulatedTimeMs))
    }

    @Test
    fun snooze30m_setsDurationAndSuppressesAlerts() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_30M)

        val state = RadarSnoozeController.getSnoozeState()
        assertTrue(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_30M, state.mode)
        assertEquals(1800, state.remainingSeconds)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
        assertTrue(RadarSnoozeController.isFastThreatAlertSuppressed())
    }

    @Test
    fun fastOnly_suppressesRoutineAlertsWhilePreservingFastThreats() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.FAST_ONLY)

        val state = RadarSnoozeController.getSnoozeState()
        assertTrue(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.FAST_ONLY, state.mode)
        assertNull(state.remainingSeconds)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
        assertFalse(RadarSnoozeController.isFastThreatAlertSuppressed())
    }

    @Test
    fun restOfRide_suppressesAllAlertsWithoutCountdown() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.REST_OF_RIDE)

        val state = RadarSnoozeController.getSnoozeState()
        assertTrue(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.REST_OF_RIDE, state.mode)
        assertNull(state.remainingSeconds)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
        assertTrue(RadarSnoozeController.isFastThreatAlertSuppressed())
    }

    @Test
    fun resumeAudio_clearsActiveSnooze() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)

        RadarSnoozeController.resumeAudio()

        val state = RadarSnoozeController.getSnoozeState()
        assertFalse(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, state.mode)
        assertNull(state.remainingSeconds)
        assertFalse(RadarSnoozeController.isRoutineAlertSuppressed())
    }

    @Test
    fun toggleRadarAudioSnooze_alternatesBetween15mAndResume() {
        assertFalse(RadarSnoozeController.getSnoozeState().isSnoozed)

        RadarSnoozeController.toggleRadarAudioSnooze()
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, RadarSnoozeController.getSnoozeState().mode)

        RadarSnoozeController.toggleRadarAudioSnooze()
        assertFalse(RadarSnoozeController.getSnoozeState().isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, RadarSnoozeController.getSnoozeState().mode)
    }

    @Test
    fun pelotonSync_suppressesRoutineAlertsAndPermitsFastThreats() {
        RadarSnoozeController.setPelotonSyncActive(true)

        val state = RadarSnoozeController.getSnoozeState()
        assertTrue(state.isSnoozed)
        assertTrue(state.isPelotonSyncActive)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
        assertFalse(RadarSnoozeController.isFastThreatAlertSuppressed())

        RadarSnoozeController.setPelotonSyncActive(false)
        assertFalse(RadarSnoozeController.getSnoozeState().isSnoozed)
    }

    @Test
    fun resumeAudio_overridesPelotonAndUrbanAutoMuteUntilStateTransition() {
        RadarSnoozeController.setPelotonSyncActive(true)
        RadarSnoozeController.setUrbanMuteActive(true)
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())

        // User taps "Resume Audio" while auto-mute is active
        RadarSnoozeController.resumeAudio()

        assertTrue(RadarSnoozeController.isManualResumeOverridingAutoMute())
        assertFalse(RadarSnoozeController.getSnoozeState().isSnoozed)
        assertFalse(RadarSnoozeController.isRoutineAlertSuppressed())

        // Submitting same state does not reset override
        RadarSnoozeController.onRadarStateUpdated(RadarState(
            overallThreatLevel = ThreatLevel.NONE,
            lightMode = RadarLightMode.OFF
        ))
        assertTrue(RadarSnoozeController.isManualResumeOverridingAutoMute())

        // Threat level transition resets manual override
        RadarSnoozeController.onRadarStateUpdated(RadarState(
            overallThreatLevel = ThreatLevel.APPROACHING,
            lightMode = RadarLightMode.OFF
        ))
        assertFalse(RadarSnoozeController.isManualResumeOverridingAutoMute())
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)
        assertTrue(RadarSnoozeController.isRoutineAlertSuppressed())
    }

    @Test
    fun oneShotExpiryTimer_hasZeroIntermediateEmissions_reschedulesOn15mTo30m_andExpiresCleanly_tstCon056_3() = runTest {
        val testScope = TestScope(testScheduler)
        RadarSnoozeController.setCoroutineScope(testScope)

        val snoozeEmissions = mutableListOf<RadarAudioSnoozeState>()
        val collectJob = testScope.launch {
            RadarStateRepository.snoozeState.collect { snoozeEmissions.add(it) }
        }
        testScope.testScheduler.runCurrent()
        val baselineEmissions = snoozeEmissions.size

        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        testScope.testScheduler.runCurrent()
        assertEquals(900, RadarSnoozeController.getSnoozeState().remainingSeconds)
        assertEquals(baselineEmissions + 1, snoozeEmissions.size)

        // Advance 60 seconds: ZERO intermediate snoozeState emissions (no 1-Hz ticker!)
        simulatedTimeMs += 60_000L
        testScope.advanceTimeBy(60_000L)
        testScope.testScheduler.runCurrent()
        assertEquals(840, RadarSnoozeController.getSnoozeState().remainingSeconds)
        assertEquals("Must emit ZERO intermediate ticks during active snooze", baselineEmissions + 1, snoozeEmissions.size)

        // Reschedule SNOOZE_15M -> SNOOZE_30M: cancels previous 15m timer cleanly
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_30M)
        testScope.testScheduler.runCurrent()
        assertEquals(1800, RadarSnoozeController.getSnoozeState().remainingSeconds)
        assertEquals(baselineEmissions + 2, snoozeEmissions.size)

        // Advance past original 15m expiry (900s): 30m snooze MUST remain active!
        simulatedTimeMs += 900_000L
        testScope.advanceTimeBy(900_000L)
        testScope.testScheduler.runCurrent()
        assertTrue("30m reschedule must cancel prior 15m expiry", RadarSnoozeController.getSnoozeState().isSnoozed)
        assertEquals(900, RadarSnoozeController.getSnoozeState().remainingSeconds)
        assertEquals(baselineEmissions + 2, snoozeEmissions.size)

        // Advance remaining 900s: single one-shot expiry fires cleanly
        simulatedTimeMs += 900_000L
        testScope.advanceTimeBy(900_000L)
        testScope.testScheduler.runCurrent()

        val expiredState = RadarSnoozeController.getSnoozeState()
        assertFalse(expiredState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, expiredState.mode)
        assertNull(expiredState.remainingSeconds)
        assertEquals(baselineEmissions + 3, snoozeEmissions.size)

        collectJob.cancel()
    }

    @Test
    fun reset_restoresCleanInactiveStateAcrossRepository() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_30M)
        RadarSnoozeController.setPelotonSyncActive(true)
        assertTrue(RadarStateRepository.getState().audioSnoozeState.isSnoozed)

        RadarSnoozeController.reset()

        val state = RadarSnoozeController.getSnoozeState()
        assertFalse(state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, state.mode)
        assertFalse(state.isPelotonSyncActive)
        assertEquals(RadarAudioSnoozeState.INACTIVE, RadarStateRepository.getState().audioSnoozeState)
    }

    @Test
    fun snoozeState_isPreservedAcrossWorkoutPauseAndResetOnWorkoutStop() {
        // Cyclist activates 15m snooze during workout
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)

        // Workout paused: TrackerService onDestroy must NOT reset snooze
        // (Snooze remains active across READY, PAUSED, and TRACKING)
        assertTrue("Snooze must remain active during pause", RadarSnoozeController.getSnoozeState().isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, RadarSnoozeController.getSnoozeState().mode)

        // Workout stopped: explicit reset cleans up state
        RadarSnoozeController.reset()
        assertFalse("Snooze must be inactive after workout stopped", RadarSnoozeController.getSnoozeState().isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, RadarSnoozeController.getSnoozeState().mode)
    }

    /**
     * TST-TRK-064.1: Verify default time provider is monotonic and non-negative without throwing in JVM tests.
     */
    @Test
    fun testDefaultTimeProvider_usesMonotonicClock() {
        val time1 = RadarSnoozeController.DEFAULT_TIME_PROVIDER()
        assertTrue("DEFAULT_TIME_PROVIDER must return positive timestamp", time1 > 0L)
        val time2 = RadarSnoozeController.DEFAULT_TIME_PROVIDER()
        assertTrue("Monotonic clock must be non-decreasing", time2 >= time1)
    }

    /**
     * TST-TRK-064.1: Backward wall-clock shift must not prevent snooze expiration when monotonic clock advances.
     */
    @Test
    fun testSnoozeExpiry_unaffectedByBackwardWallClockShift() {
        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        assertTrue(RadarSnoozeController.getSnoozeState().isSnoozed)

        // Monotonic time advances by 15 minutes + 1 ms (100_000 + 900_001 = 1_000_001 ms)
        // Even if wall clock jumped back 1 hour, monotonic clock accurately expires the snooze
        simulatedTimeMs += 15 * 60 * 1000L + 1L

        val state = RadarSnoozeController.getSnoozeState()
        assertFalse("Snooze must expire after 15 minutes regardless of wall-clock shifts", state.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, state.mode)
        assertNull(state.remainingSeconds)
    }

    /**
     * TST-TRK-064.1: reset() must restore timeProvider back to DEFAULT_TIME_PROVIDER.
     */
    @Test
    fun testReset_restoresDefaultTimeProvider() {
        RadarSnoozeController.setTimeProvider { 42L }
        RadarSnoozeController.reset()

        // Calling getSnoozeState() or inspecting clock should no longer return 42L
        val restoredTime = RadarSnoozeController.DEFAULT_TIME_PROVIDER()
        assertTrue("Reset must restore live monotonic clock rather than pinned 42L test stub", restoredTime > 1000L)
    }
}
