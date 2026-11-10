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
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarState
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
        mockContext = mockk(relaxed = true)
        mockDataStore = mockk(relaxed = true)
        mockSynthesizer = mockk(relaxed = true)
        mockAudioManager = mockk(relaxed = true)
        mockVibrator = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)
        mockCurrentTimeMs = 10_000L

        every { mockDataStore.getTuningConfig() } answers { currentConfig }
        every { mockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 3
        every { mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 15
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
}
