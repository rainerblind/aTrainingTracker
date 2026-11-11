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

package com.atrainingtracker.trainingtracker.audio.radar

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarSnoozeController
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
 * Unit tests for [RadarAlertManager] (TST-TRK-063.1, TST-TRK-063.3, TST-TRK-063.4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class RadarAlertManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDataStore: TuningPreferencesDataStore
    private lateinit var mockSynthesizer: RadarAudioSynthesizer
    private lateinit var mockAudioManager: AudioManager
    private lateinit var mockVibrator: Vibrator
    private lateinit var mockHandler: Handler
    private val testScope = TestScope()

    private var currentConfig = TuningConfig()
    private var mockCurrentTimeMs = 10_000L

    @Before
    fun setup() {
        RadarStateRepository.reset()
        mockContext = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        mockSynthesizer = mockk(relaxed = true)
        mockAudioManager = mockk(relaxed = true)
        mockVibrator = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)
        mockCurrentTimeMs = 10_000L
        RadarSnoozeController.reset()
        RadarSnoozeController.setTimeProvider { mockCurrentTimeMs }

        every { mockDataStore.getTuningConfig() } answers { currentConfig }
        every { mockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 3
        every { mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15
        every { mockSynthesizer.isHeadphonesOrBluetoothConnected() } returns true
    }

    @After
    fun tearDown() {
        RadarAlertManager.activeInstance = null
        RadarAlertManager.releasePreviewSynthesizer()
        RadarSnoozeController.reset()
        RadarStateRepository.reset()
    }

    private fun createManager(): RadarAlertManager {
        return RadarAlertManager(
            context = mockContext,
            tuningDataStore = mockDataStore,
            audioSynthesizer = mockSynthesizer,
            audioManager = mockAudioManager,
            vibrator = mockVibrator,
            handler = mockHandler,
            coroutineScope = testScope,
            timeProvider = { mockCurrentTimeMs }
        )
    }

    @Test
    fun onRadarStateUpdated_approachTransition_triggersApproachChimeAndHaptic() {
        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }
        verify(atLeast = 1) { mockVibrator.vibrate(any<VibrationEffect>()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_fastApproachEscalation_triggersFastThreatChime() {
        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.FAST_APPROACHING)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 1) { mockSynthesizer.playFastThreat(any(), any()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_allClearTransition_triggersAllClearChime() {
        currentConfig = currentConfig.copy(radarAudioAlertMode = RadarAudioAlertMode.ALL_ALERTS)
        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 1) { mockSynthesizer.playAllClear(any(), any()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_disconnectError_triggersDisconnectChimeAndLongVibration() {
        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.DISCONNECTED_ERROR, overallThreatLevel = ThreatLevel.NONE)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 1) { mockSynthesizer.playDisconnect(any(), any()) }
        verify(atLeast = 1) { mockVibrator.vibrate(any<VibrationEffect>()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_rapidOscillationWithinDebounceWindow_isDebounced() {
        currentConfig = currentConfig.copy(radarAudioAlertMode = RadarAudioAlertMode.ALL_ALERTS)
        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        val state3 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)

        manager.onRadarStateUpdated(state1)
        manager.onRadarStateUpdated(state2)
        mockCurrentTimeMs += 200L // Rapid flutter (200ms < 800ms cooldown)
        manager.onRadarStateUpdated(state3)

        // All clear must be suppressed because 200ms < 800ms
        verify(exactly = 0) { mockSynthesizer.playAllClear(any(), any()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_debouncing_preventsRepeatedAlertsOnContinuousTelemetry() {
        val manager = createManager()
        val stateApproaching = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)

        // Simulate 8 Hz telemetry frames for the same threat level
        for (i in 0 until 10) {
            manager.onRadarStateUpdated(stateApproaching)
        }

        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }

        manager.release()
    }

    @Test
    fun alertMode_muted_suppressesAudioChimes() {
        currentConfig = currentConfig.copy(radarAudioAlertMode = RadarAudioAlertMode.MUTED)
        val manager = createManager()

        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        val state3 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.FAST_APPROACHING)

        manager.onRadarStateUpdated(state1)
        manager.onRadarStateUpdated(state2)
        manager.onRadarStateUpdated(state3)

        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }
        verify(exactly = 0) { mockSynthesizer.playFastThreat(any(), any()) }

        manager.release()
    }

    @Test
    fun speakerBoost_boostsVolumeDuringChimeAndRestoresAfterwards() {
        currentConfig = currentConfig.copy(
            radarAudioRoute = RadarAudioRoute.FORCE_SPEAKER,
            radarSpeakerBoost = true,
            radarAlertVolume = 90
        )
        val runnableSlot = slot<Runnable>()
        every { mockHandler.postDelayed(capture(runnableSlot), any()) } returns true

        val manager = createManager()
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)

        manager.onRadarStateUpdated(state1)
        manager.onRadarStateUpdated(state2)

        // 90% of 15 max volume = ~14
        verify { mockAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 14, 0) }

        // Execute scheduled restore runnable
        runnableSlot.captured.run()

        // Restores to original volume (3)
        verify { mockAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0) }

        manager.release()
    }

    @Test
    fun hapticDisabled_suppressesVibration() {
        currentConfig = currentConfig.copy(radarHapticAlert = false)
        val manager = createManager()

        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)

        manager.onRadarStateUpdated(state1)
        manager.onRadarStateUpdated(state2)

        verify(exactly = 0) { mockVibrator.vibrate(any<VibrationEffect>()) }

        manager.release()
    }

    @Test
    fun snooze_15m_suppressesRoutineAlerts_andAutoExpires() {
        val manager = createManager()
        manager.snooze(RadarAudioSnoozeMode.SNOOZE_15M)

        val snoozeState = RadarStateRepository.getState().audioSnoozeState
        assertTrue(snoozeState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, snoozeState.mode)

        // NONE -> APPROACHING while snoozed
        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }

        // Advance beyond 15m (15 * 60 * 1000L = 900_000L)
        mockCurrentTimeMs += 900_000L + 1000L
        val state3 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state4 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        manager.onRadarStateUpdated(state3)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state4)

        // After expiry, approach alert must play
        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isSnoozed)

        manager.release()
    }

    @Test
    fun snooze_fastOnly_suppressesRoutineAlerts_butPlaysFastThreat() {
        val manager = createManager()
        manager.snooze(RadarAudioSnoozeMode.FAST_ONLY)

        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        val state3 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.FAST_APPROACHING)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        // Routine approach alert suppressed
        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }

        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state3)

        // High-threat escalation MUST sound
        verify(exactly = 1) { mockSynthesizer.playFastThreat(any(), any()) }

        manager.release()
    }

    @Test
    fun snooze_restOfRide_suppressesAllAlerts_untilResumeAudioCalled() {
        val manager = createManager()
        manager.snooze(RadarAudioSnoozeMode.REST_OF_RIDE)

        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }

        manager.resumeAudio()
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isSnoozed)

        val state3 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state4 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state3)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state4)

        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }

        manager.release()
    }

    @Test
    fun toggleRadarAudioSnooze_togglesBetweenSnooze15mAndResume() {
        val manager = createManager()
        RadarAlertManager.activeInstance = manager

        assertFalse(RadarStateRepository.getState().audioSnoozeState.isSnoozed)

        RadarAlertManager.toggleRadarAudioSnooze()
        assertTrue(RadarStateRepository.getState().audioSnoozeState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, RadarStateRepository.getState().audioSnoozeState.mode)

        RadarAlertManager.toggleRadarAudioSnooze()
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.NONE, RadarStateRepository.getState().audioSnoozeState.mode)

        manager.release()
    }

    @Test
    fun snooze_preservesHaptics_whenAudioMuted() {
        currentConfig = currentConfig.copy(radarHapticAlert = true)
        val manager = createManager()
        RadarAlertManager.snooze(RadarAudioSnoozeMode.SNOOZE_15M)

        val state1 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val state2 = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.APPROACHING)
        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        // Audio chime is silenced
        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }
        // BUT haptic vibration MUST still be triggered
        verify(atLeast = 1) { mockVibrator.vibrate(any<VibrationEffect>()) }

        manager.release()
    }

    @Test
    fun resumeAudio_overridesPelotonMute_allowingApproachChime() {
        currentConfig = currentConfig.copy(radarPelotonAudioSync = true)
        val manager = createManager()

        val statePeloton1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            lightMode = RadarLightMode.PELOTON
        )
        manager.onRadarStateUpdated(statePeloton1)
        assertTrue(RadarSnoozeController.getSnoozeState().isPelotonSyncActive)

        // Rider explicitly taps "Resume Audio"
        RadarAlertManager.resumeAudio()
        assertTrue(RadarSnoozeController.isManualResumeOverridingAutoMute())
        assertFalse(RadarSnoozeController.getSnoozeState().isSnoozed)

        // Transition to APPROACHING with same PELOTON light mode
        mockCurrentTimeMs += 1000L
        val statePeloton2 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            lightMode = RadarLightMode.PELOTON
        )
        manager.onRadarStateUpdated(statePeloton2)

        // Approach chime should now play because manual resume overrode peloton auto-mute
        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }

        manager.release()
    }

    @Test
    fun urbanCongestion_warmupPreserved_whenSinglePassingVehicleDetected() {
        currentConfig = currentConfig.copy(radarSmartUrbanMute = true)
        val manager = createManager()

        // 3 slow vehicles approaching
        val slowTargets = listOf(
            RadarTarget(targetId = 1, rangeMeters = 50f, approachSpeedMps = 3.0f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 2, rangeMeters = 60f, approachSpeedMps = 2.5f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 3, rangeMeters = 70f, approachSpeedMps = 4.0f, threatLevel = ThreatLevel.APPROACHING)
        )
        val stateSlow = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = slowTargets
        )

        manager.onRadarStateUpdated(stateSlow)

        // Advance 15s (halfway through 30s warmup)
        mockCurrentTimeMs += 15_000L

        // Single passing vehicle at 8.0 m/s joins (now 2 slow + 1 fast vehicle = 3 total)
        val mixedTargets = listOf(
            RadarTarget(targetId = 1, rangeMeters = 30f, approachSpeedMps = 3.0f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 2, rangeMeters = 40f, approachSpeedMps = 2.5f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 4, rangeMeters = 80f, approachSpeedMps = 8.0f, threatLevel = ThreatLevel.APPROACHING)
        )
        val stateMixed = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = mixedTargets
        )

        manager.onRadarStateUpdated(stateMixed)

        // Advance another 16s (total 31s elapsed since initial slow traffic)
        mockCurrentTimeMs += 16_000L
        manager.onRadarStateUpdated(stateMixed)

        // Urban mute should now be ACTIVE because warmup timer was preserved
        assertTrue(RadarSnoozeController.getSnoozeState().isUrbanMuteActive)

        manager.release()
    }

    @Test
    fun pelotonModeSync_whenEnabled_suppressesApproach_andPreservesFastThreat() {
        currentConfig = currentConfig.copy(radarPelotonAudioSync = true)
        val manager = createManager()

        val statePeloton1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            lightMode = RadarLightMode.PELOTON
        )
        val statePeloton2 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            lightMode = RadarLightMode.PELOTON
        )
        val statePeloton3 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            lightMode = RadarLightMode.PELOTON
        )

        manager.onRadarStateUpdated(statePeloton1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(statePeloton2)

        // Routine approach chime suppressed in Peloton light mode
        verify(exactly = 0) { mockSynthesizer.playApproach(any(), any()) }
        assertTrue(RadarStateRepository.getState().audioSnoozeState.isPelotonSyncActive)

        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(statePeloton3)

        // High-threat escalation MUST sound even in Peloton light mode
        verify(exactly = 1) { mockSynthesizer.playFastThreat(any(), any()) }

        // Switch back to DAY_FLASH
        val stateDayFlash1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            lightMode = RadarLightMode.DAY_FLASH
        )
        val stateDayFlash2 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            lightMode = RadarLightMode.DAY_FLASH
        )
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateDayFlash1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateDayFlash2)

        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isPelotonSyncActive)

        manager.release()
    }

    @Test
    fun pelotonModeSync_whenDisabled_doesNotSuppressApproach() {
        currentConfig = currentConfig.copy(radarPelotonAudioSync = false)
        val manager = createManager()

        val state1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            lightMode = RadarLightMode.PELOTON
        )
        val state2 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            lightMode = RadarLightMode.PELOTON
        )

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(state2)

        // Peloton sync disabled -> normal approach chime plays
        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any()) }

        manager.release()
    }

    @Test
    fun smartUrbanCongestionFilter_suppressesApproachAfterCongestionWindow_andPreservesFastThreat() {
        currentConfig = currentConfig.copy(radarSmartUrbanMute = true)
        val manager = createManager()

        val slowTargets = listOf(
            RadarTarget(targetId = 0, rangeMeters = 20f, approachSpeedMps = 3.0f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 1, rangeMeters = 30f, approachSpeedMps = 3.5f, threatLevel = ThreatLevel.APPROACHING),
            RadarTarget(targetId = 2, rangeMeters = 40f, approachSpeedMps = 4.0f, threatLevel = ThreatLevel.APPROACHING)
        )

        // Frame 1: Queue starts
        val stateCongested1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = slowTargets
        )
        manager.onRadarStateUpdated(stateCongested1)
        // First approach alert sounds before congestion window elapsed
        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any(), any(), any(), any()) }

        // Advance 30 seconds into congestion
        mockCurrentTimeMs += 30_000L
        manager.onRadarStateUpdated(stateCongested1)
        assertTrue(RadarStateRepository.getState().audioSnoozeState.isUrbanMuteActive)

        // Now, subsequent approach alert within urban congestion is suppressed
        val stateClearTraffic = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = slowTargets
        )
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateClearTraffic)

        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateCongested1)
        // No additional approach chime (still exactly 1 from start)
        verify(exactly = 1) { mockSynthesizer.playApproach(any(), any(), any(), any(), any()) }

        // Fast threat escalation MUST sound even during urban mute
        val stateFastThreat = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = slowTargets
        )
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateFastThreat)
        verify(exactly = 1) { mockSynthesizer.playFastThreat(any(), any(), any(), any(), any()) }

        // Exit congestion: clear queue for 20 seconds
        val emptyTargets = emptyList<RadarTarget>()
        val stateNoTargets = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyTargets
        )
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateNoTargets)

        // Advance 20s
        mockCurrentTimeMs += 20_000L
        manager.onRadarStateUpdated(stateNoTargets)
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isUrbanMuteActive)

        // Now normal approach chime works again
        val stateNewVehicle = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(targetId = 5, rangeMeters = 50f, approachSpeedMps = 10f, threatLevel = ThreatLevel.APPROACHING))
        )
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateNewVehicle)
        verify(exactly = 2) { mockSynthesizer.playApproach(any(), any(), any(), any(), any()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_withPassingTarget_computesStereoPanningAndPitch() {
        val manager = createManager()
        val passingTarget = RadarTarget(
            targetId = 1,
            rangeMeters = 35f,
            approachSpeedMps = 16.5f,
            threatLevel = ThreatLevel.APPROACHING,
            lateralOffsetMeters = -2.0f
        )
        val stateNone = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val stateApproach = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(passingTarget)
        )

        manager.onRadarStateUpdated(stateNone)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateApproach)

        // RHT vehicle at x = -2.0m pans left (1.0f, 0.20f), 16.5 m/s scales to 1.15x pitch
        verify(exactly = 1) {
            mockSynthesizer.playApproach(
                any(),
                any(),
                eq(1.0f),
                match { kotlin.math.abs(it - 0.20f) < 0.01f },
                match { kotlin.math.abs(it - 1.15f) < 0.01f }
            )
        }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_whenLaneChangeOccurs_triggersLaneChangeTickCue() {
        val manager = createManager()
        val inLaneTarget = RadarTarget(
            targetId = 2,
            rangeMeters = 30f,
            approachSpeedMps = 10f,
            threatLevel = ThreatLevel.APPROACHING,
            lateralOffsetMeters = 0.0f
        )
        val shiftedPassingTarget = inLaneTarget.copy(lateralOffsetMeters = -2.0f)

        val state1 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(inLaneTarget)
        )
        val state2 = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(shiftedPassingTarget)
        )

        manager.onRadarStateUpdated(state1)
        mockCurrentTimeMs += 200L
        manager.onRadarStateUpdated(state2)

        // Transitioning from 0.0m to -2.0m triggers lane-change tick panned to left (1.0f, 0.20f)
        verify(exactly = 1) {
            mockSynthesizer.playLaneChangeTick(
                any(),
                any(),
                eq(1.0f),
                match { kotlin.math.abs(it - 0.20f) < 0.01f }
            )
        }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_whenSpeakerRouteSelected_collapsesToCenteredStereo() {
        currentConfig = currentConfig.copy(radarAudioRoute = RadarAudioRoute.FORCE_SPEAKER)
        val manager = createManager()
        val passingTarget = RadarTarget(
            targetId = 3,
            rangeMeters = 25f,
            approachSpeedMps = 3.0f,
            threatLevel = ThreatLevel.APPROACHING,
            lateralOffsetMeters = -2.0f
        )
        val stateNone = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val stateApproach = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(passingTarget)
        )

        manager.onRadarStateUpdated(stateNone)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateApproach)

        // Force Phone Speaker collapses gains to (1.0f, 1.0f) (TST-TRK-065.4)
        verify(exactly = 1) {
            mockSynthesizer.playApproach(
                any(),
                eq(RadarAudioRoute.FORCE_SPEAKER),
                eq(1.0f),
                eq(1.0f),
                match { kotlin.math.abs(it - 0.95f) < 0.01f }
            )
        }

        manager.release()
    }

    @Test
    fun playPreview_executesPreviewsCorrectly() {
        val manager = createManager()
        manager.playPreview(RadarAudioPreviewType.CENTERED)
        verify(atLeast = 1) { mockSynthesizer.playApproach(any(), any()) }

        manager.playPreview(RadarAudioPreviewType.PASSING)
        verify(atLeast = 1) { mockSynthesizer.playApproach(any(), any(), any(), any(), any()) }

        manager.playPreview(RadarAudioPreviewType.LANE_CHANGE)
        verify(atLeast = 1) { mockSynthesizer.playLaneChangeTick(any(), any(), any(), any()) }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_fastThreatEscalation_selectsFastApproachingTargetKinematics() {
        val manager = createManager()
        val slowTrailingCar = RadarTarget(
            targetId = 1,
            rangeMeters = 25f,
            approachSpeedMps = 4.0f,
            threatLevel = ThreatLevel.APPROACHING,
            lateralOffsetMeters = 0.0f
        )
        val fastApproachingThreat = RadarTarget(
            targetId = 2,
            rangeMeters = 85f,
            approachSpeedMps = 25.0f,
            threatLevel = ThreatLevel.FAST_APPROACHING,
            lateralOffsetMeters = -2.0f
        )

        val stateInitial = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(slowTrailingCar)
        )
        val stateEscalation = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(slowTrailingCar, fastApproachingThreat)
        )

        manager.onRadarStateUpdated(stateInitial)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateEscalation)

        // Verifies fast threat alert uses target 2's kinematics (25 m/s -> ~1.28x pitch, x = -2.0m -> left pan 1.0f, 0.20f)
        // rather than the closer slow car (4 m/s -> ~0.96x pitch, centered 1.0f, 1.0f)
        verify(exactly = 1) {
            mockSynthesizer.playFastThreat(
                any(),
                any(),
                eq(1.0f),
                match { kotlin.math.abs(it - 0.20f) < 0.01f },
                match { it > 1.25f }
            )
        }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_whenSystemDefaultWithoutHeadphones_collapsesToCenteredStereo() {
        every { mockSynthesizer.isHeadphonesOrBluetoothConnected() } returns false
        currentConfig = currentConfig.copy(radarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT)
        val manager = createManager()
        val passingTarget = RadarTarget(
            targetId = 3,
            rangeMeters = 25f,
            approachSpeedMps = 15.0f,
            threatLevel = ThreatLevel.APPROACHING,
            lateralOffsetMeters = -2.0f
        )
        val stateNone = RadarState(connectionState = RadarConnectionState.CONNECTED, overallThreatLevel = ThreatLevel.NONE)
        val stateApproach = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(passingTarget)
        )

        manager.onRadarStateUpdated(stateNone)
        mockCurrentTimeMs += 1000L
        manager.onRadarStateUpdated(stateApproach)

        // SYSTEM_DEFAULT without headphones collapses gains to (1.0f, 1.0f) (REQ-TRK-065.1, ATT-6709)
        verify(exactly = 1) {
            mockSynthesizer.playApproach(
                any(),
                eq(RadarAudioRoute.SYSTEM_DEFAULT),
                eq(1.0f),
                eq(1.0f),
                any()
            )
        }

        manager.release()
    }

    @Test
    fun onRadarStateUpdated_laneChangeCooldownAndMultiCar_preventsFlutterAndTracksAllTargets() {
        val manager = createManager()
        val car1InLane = RadarTarget(1, 30f, 10f, ThreatLevel.APPROACHING, lateralOffsetMeters = 0.0f)
        val car2InLane = RadarTarget(2, 45f, 12f, ThreatLevel.APPROACHING, lateralOffsetMeters = 0.0f)

        val state1 = RadarState(RadarConnectionState.CONNECTED, ThreatLevel.APPROACHING, listOf(car1InLane, car2InLane))
        manager.onRadarStateUpdated(state1)

        // Car 1 changes lane to passing (x = -2.0m) -> first cue
        mockCurrentTimeMs += 200L
        val car1Passing = car1InLane.copy(lateralOffsetMeters = -2.0f)
        manager.onRadarStateUpdated(state1.copy(targets = listOf(car1Passing, car2InLane)))
        verify(exactly = 1) { mockSynthesizer.playLaneChangeTick(any(), any(), eq(1.0f), any()) }

        // Car 1 jitters laterally between -1.4m and -1.8m within cooldown (< 3000 ms) -> zero additional ticks
        mockCurrentTimeMs += 300L
        val car1Jitter = car1InLane.copy(lateralOffsetMeters = -1.6f)
        manager.onRadarStateUpdated(state1.copy(targets = listOf(car1Jitter, car2InLane)))
        verify(exactly = 1) { mockSynthesizer.playLaneChangeTick(any(), any(), any(), any()) }

        // Car 2 changes lane to passing (x = 2.0m in LHT or -2.0m in RHT) -> second car triggers independent tick!
        mockCurrentTimeMs += 200L
        val car2Passing = car2InLane.copy(lateralOffsetMeters = -2.0f)
        manager.onRadarStateUpdated(state1.copy(targets = listOf(car1Jitter, car2Passing)))
        verify(exactly = 2) { mockSynthesizer.playLaneChangeTick(any(), any(), eq(1.0f), any()) }

        manager.release()
    }

    @Test
    fun releasePreviewSynthesizer_releasesPreviewSynthesizerCleanly() {
        // Calling releasePreviewSynthesizer when not initialized shouldn't throw
        RadarAlertManager.releasePreviewSynthesizer()
    }
}
