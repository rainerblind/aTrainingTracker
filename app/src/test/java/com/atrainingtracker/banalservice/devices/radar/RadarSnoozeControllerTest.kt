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
        simulatedTimeMs = 100_000L
        RadarSnoozeController.setTimeProvider { simulatedTimeMs }
        RadarSnoozeController.reset()
        RadarStateRepository.reset()
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
        assertEquals(state, RadarStateRepository.getState().audioSnoozeState)
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
    fun tickerCoroutine_decrementsRemainingSecondsAndAutoExpires() = runTest {
        val testScope = TestScope(testScheduler)
        RadarSnoozeController.setCoroutineScope(testScope)

        RadarSnoozeController.snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        assertEquals(900, RadarSnoozeController.getSnoozeState().remainingSeconds)

        // Advance 10 seconds
        simulatedTimeMs += 10_000L
        testScope.advanceTimeBy(10_000L)
        assertEquals(890, RadarSnoozeController.getSnoozeState().remainingSeconds)

        // Advance past expiration (remaining 890s + 1s)
        simulatedTimeMs += 891_000L
        testScope.advanceTimeBy(891_000L)

        val expiredState = RadarSnoozeController.getSnoozeState()
        assertFalse(expiredState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, expiredState.mode)
        assertNull(expiredState.remainingSeconds)
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
}
