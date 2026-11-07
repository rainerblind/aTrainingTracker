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

package com.atrainingtracker.trainingtracker.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import io.mockk.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Unit and concurrency test suite for BikeBellAudioSynthesizer (REQ-UI-421.4, TST-UI-384.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BikeBellAudioSynthesizerTest {

    @Test
    fun testPcmSynthesis_mathematicalPropertiesAndDuration() {
        val pcm = BikeBellAudioSynthesizer.synthesizeDoubleStrikePcm(48000)
        assertNotNull(pcm)
        // 0.95s at 48000Hz 16-bit mono = 45600 samples * 2 bytes = 91200 bytes
        assertEquals(91200, pcm.size)

        // Sample at t = 0 must be 0
        val s0Low = pcm[0].toInt() and 0xFF
        val s0High = pcm[1].toInt()
        val s0 = (s0High shl 8) or s0Low
        assertEquals(0, s0)

        // SmartPA lead-in: at t = 0.02s (sample index 960), amplitude is minimal pilot signal
        val idx960 = 960 * 2
        val sPilotLow = pcm[idx960].toInt() and 0xFF
        val sPilotHigh = pcm[idx960 + 1].toInt()
        val sPilot = (sPilotHigh shl 8) or sPilotLow
        assertTrue("Pilot carrier amplitude should be sub-audible", Math.abs(sPilot) <= 10)

        // Verify strike sample calculation conforms to physical envelope
        val sampleAtPeak = BikeBellAudioSynthesizer.calculateStrikeSample(0.0001, 2040.0)
        assertTrue("Sample after initial transient should be non-zero", Math.abs(sampleAtPeak) > 0.0)

        // Decay: sample after 0.5s should be significantly lower than near peak
        val sampleDecayed = BikeBellAudioSynthesizer.calculateStrikeSample(0.5, 2040.0)
        assertTrue("Sample after 0.5s should be decayed", Math.abs(sampleDecayed) < 0.15)

        // Cosine fade-out: last sample at t ~ 0.95s must taper down to 0
        val lastIdx = (45600 - 1) * 2
        val sLastLow = pcm[lastIdx].toInt() and 0xFF
        val sLastHigh = pcm[lastIdx + 1].toInt()
        val sLast = (sLastHigh shl 8) or sLastLow
        assertEquals(0, sLast)

        // Test roll PCM synthesis: 2.5s at 48000Hz = 120000 samples * 2 bytes = 240000 bytes
        val rollPcm = BikeBellAudioSynthesizer.synthesizeRollPcm(48000)
        assertNotNull(rollPcm)
        assertEquals(240000, rollPcm.size)
    }

    @Test
    fun testPcmSynthesis_5ModeBrassAcousticPropertiesAndEnvelope_TST_UI_384_3() {
        // 1. Verify 5-mode resonant frequencies and detuning ratio (REQ-UI-421.4, ATT-5282)
        assertEquals(1020.0, BikeBellAudioSynthesizer.HUM_FREQ, 0.001)
        assertEquals(2040.0, BikeBellAudioSynthesizer.FUNDAMENTAL_FREQ, 0.001)
        assertEquals(3120.0, BikeBellAudioSynthesizer.TIERCE_FREQ, 0.001)
        assertEquals(4280.0, BikeBellAudioSynthesizer.NOMINAL_FREQ, 0.001)
        assertEquals(6200.0, BikeBellAudioSynthesizer.CLAPPER_FREQ, 0.001)
        assertEquals(1.025, BikeBellAudioSynthesizer.STRIKE_2_DETUNE_RATIO, 0.001)

        // 2. Clapper attack envelope smoothing: zero at t <= 0, rising smoothly
        assertEquals(0.0, BikeBellAudioSynthesizer.calculateStrikeSample(-0.01, 2040.0), 0.0)
        assertEquals(0.0, BikeBellAudioSynthesizer.calculateStrikeSample(0.0, 2040.0), 0.0)

        val sampleAtAttack = BikeBellAudioSynthesizer.calculateStrikeSample(0.002, 2040.0)
        assertTrue("Sample at 2ms should rise smoothly", Math.abs(sampleAtAttack) > 0.05)

        // 3. Roll strike sample attack and rapid dampening
        assertEquals(0.0, BikeBellAudioSynthesizer.calculateRollStrikeSample(0.0, 2040.0), 0.0)
        val rollSampleEarly = BikeBellAudioSynthesizer.calculateRollStrikeSample(0.002, 2040.0)
        assertTrue("Roll sample at 2ms should be active", Math.abs(rollSampleEarly) > 0.05)

        val rollSampleDamped = BikeBellAudioSynthesizer.calculateRollStrikeSample(0.2, 2040.0)
        assertTrue("Roll strike sample at 0.2s should be damped", Math.abs(rollSampleDamped) < 0.05)

        // 4. Verify roll PCM linear headroom scaling (no clipping overflow)
        val rollPcm = BikeBellAudioSynthesizer.synthesizeRollPcm(48000)
        assertEquals(240000, rollPcm.size)
        for (i in 0 until rollPcm.size step 2) {
            val low = rollPcm[i].toInt() and 0xFF
            val high = rollPcm[i + 1].toInt()
            val sample = (high shl 8) or low
            assertTrue(sample >= Short.MIN_VALUE && sample <= Short.MAX_VALUE)
        }
    }

    @Test
    fun testBikeBellAudioSynthesizer_pcmSynthesisAndThreadSafeLifecycle() {
        val mockTrack1 = mockk<AudioTrack>(relaxed = true)
        val mockTrack2 = mockk<AudioTrack>(relaxed = true)
        var callCount = 0

        val synthesizer = BikeBellAudioSynthesizer(
            audioTrackFactory = {
                callCount++
                if (callCount == 1) mockTrack1 else mockTrack2
            }
        )

        // 1. Single ring at polite volume with warm pause() replay (ATT-5649)
        synthesizer.ring(0.35f)
        verify(exactly = 1) { mockTrack1.setVolume(0.35f) }
        verify(exactly = 1) { mockTrack1.pause() }
        verify(exactly = 0) { mockTrack1.stop() }
        verify(exactly = 1) { mockTrack1.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack1.play() }

        // 2. Single ring at escalated volume
        synthesizer.ring(1.0f)
        verify(exactly = 1) { mockTrack1.setVolume(1.0f) }
        verify(atLeast = 2) { mockTrack1.pause() }
        verify(exactly = 0) { mockTrack1.stop() }
        verify(atLeast = 2) { mockTrack1.play() }

        // 3. Roll start and stop with warm pause() replay
        synthesizer.startRoll(1.0f)
        verify(exactly = 1) { mockTrack2.setVolume(1.0f) }
        verify(exactly = 1) { mockTrack2.pause() }
        verify(exactly = 0) { mockTrack2.stop() }
        verify(exactly = 1) { mockTrack2.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack2.setLoopPoints(0, 120000, -1) }
        verify(exactly = 1) { mockTrack2.play() }

        synthesizer.stopRoll()
        verify(atLeast = 1) { mockTrack2.pause() }
        verify(exactly = 0) { mockTrack2.stop() }

        // 4. Concurrent calls from 10 threads
        val threadPool = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
        runBlocking(threadPool) {
            val jobs = List(50) { index ->
                launch {
                    if (index % 3 == 0) {
                        synthesizer.ring(0.85f)
                    } else if (index % 3 == 1) {
                        synthesizer.startRoll(1.0f)
                    } else {
                        synthesizer.stopRoll()
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        // 5. Close and verify safe no-op on subsequent calls
        synthesizer.close()
        verify(atLeast = 1) { mockTrack1.release() }
        verify(atLeast = 1) { mockTrack2.release() }

        // Subsequent calls after close must safely no-op without exceptions
        synthesizer.ring(1.0f)
        synthesizer.startRoll(1.0f)
        synthesizer.stopRoll()
        synthesizer.close() // Idempotent close
    }

    @Test
    fun testBikeBellAudioSynthesizer_startRoll_configuresSetLoopPoints() {
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = BikeBellAudioSynthesizer(
            audioTrackFactory = { mockTrack }
        )

        val totalFrames = synthesizer.rollPcm.size / 2
        assertEquals(120000, totalFrames)

        synthesizer.startRoll(1.0f)
        verify(exactly = 1) { mockTrack.setVolume(1.0f) }
        verify(exactly = 1) { mockTrack.pause() }
        verify(exactly = 0) { mockTrack.stop() }
        verify(exactly = 1) { mockTrack.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack.setLoopPoints(0, totalFrames, -1) }
        verify(exactly = 1) { mockTrack.play() }

        synthesizer.stopRoll()
        verify(atLeast = 1) { mockTrack.pause() }
        verify(atLeast = 2) { mockTrack.playbackHeadPosition = 0 }

        synthesizer.close()
    }

    @Test
    fun testBikeBellAudioSynthesizer_createAudioAttributes_alarmAndSonification() {
        val attributes = BikeBellAudioSynthesizer.createAudioAttributes()
        assertEquals(AudioAttributes.USAGE_ALARM, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.contentType)
    }

    @Test
    fun testBikeBellAudioSynthesizer_routesToBuiltInSpeaker_whenExternalDevicesConnected() {
        val mockSpeaker = mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        val mockHeadphones = mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockHeadphones, mockSpeaker)
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 3
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockTrack = mockk<AudioTrack>(relaxed = true)

        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack }
        )

        // setPreferredDevice must be called with mockSpeaker upon creation because headphones are connected
        verify(atLeast = 1) { mockTrack.setPreferredDevice(mockSpeaker) }

        // And again prior to ring()
        synthesizer.ring(0.35f)
        verify(atLeast = 2) { mockTrack.setPreferredDevice(mockSpeaker) }

        // And prior to startRoll()
        synthesizer.startRoll(1.0f)
        verify(atLeast = 3) { mockTrack.setPreferredDevice(mockSpeaker) }

        synthesizer.close()
    }

    @Test
    fun testBikeBellAudioSynthesizer_conditionalSpeakerRouting_nullWhenNoExternalDevices() {
        val mockSpeaker = mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockSpeaker)
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 3
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockTrack = mockk<AudioTrack>(relaxed = true)

        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack }
        )

        // Without external audio devices, setPreferredDevice(null) must be called to prevent port-thrashing (ATT-5649)
        verify(atLeast = 1) { mockTrack.setPreferredDevice(null) }
        verify(exactly = 0) { mockTrack.setPreferredDevice(mockSpeaker) }

        synthesizer.ring(0.35f)
        verify(atLeast = 2) { mockTrack.setPreferredDevice(null) }

        synthesizer.close()
    }

    @Test
    fun testBikeBellAudioSynthesizer_volumeBoostAndDelayedAutoRestore() {
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(any()) } returns emptyArray()
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 2
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockHandler = mockk<Handler>(relaxed = true)
        val runnableSlot = slot<Runnable>()
        every { mockHandler.postDelayed(capture(runnableSlot), any()) } returns true

        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack },
            handler = mockHandler
        )

        // 1. Ring triggers volume boost to max and schedules 5500ms restore (ATT-5649)
        assertEquals(5500L, BikeBellAudioSynthesizer.RING_RESTORE_DELAY_MS)
        synthesizer.ring(0.35f)
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 15, 0) }
        verify(exactly = 1) { mockHandler.postDelayed(any(), 5500L) }

        // Rapid second ring within 5s does NOT re-query or overwrite savedAlarmVolume with 15
        synthesizer.ring(0.65f)
        verify(exactly = 1) { mockAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) }

        // Execute scheduled restore callback
        assertTrue(runnableSlot.isCaptured)
        runnableSlot.captured.run()
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0) }

        // Subsequent ring after restore will capture volume again
        synthesizer.ring(0.35f)
        verify(exactly = 2) { mockAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) }

        synthesizer.close()
    }

    @Test
    fun testBikeBellAudioSynthesizer_volumeAlreadyAtMax_skipsSetStreamVolume() {
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(any()) } returns emptyArray()
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 15
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockHandler = mockk<Handler>(relaxed = true)
        val runnableSlot = slot<Runnable>()
        every { mockHandler.postDelayed(capture(runnableSlot), any()) } returns true

        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack },
            handler = mockHandler
        )

        // Volume is already at max (15 == 15): setStreamVolume must NOT be called, preventing OS mute events (ATT-5649)
        synthesizer.ring(0.35f)
        verify(exactly = 0) { mockAudioManager.setStreamVolume(any(), any(), any()) }

        // Restore runnable runs: setStreamVolume must still NOT be called
        if (runnableSlot.isCaptured) {
            runnableSlot.captured.run()
        }
        verify(exactly = 0) { mockAudioManager.setStreamVolume(any(), any(), any()) }

        synthesizer.close()
        verify(exactly = 0) { mockAudioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun testBikeBellAudioSynthesizer_rollVolumeBoostAndStopRollDelayedRestore() {
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(any()) } returns emptyArray()
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 5
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockHandler = mockk<Handler>(relaxed = true)
        val runnableSlot = slot<Runnable>()
        every { mockHandler.postDelayed(capture(runnableSlot), any()) } returns true

        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack },
            handler = mockHandler
        )

        // startRoll boosts volume and cancels any pending restore
        synthesizer.startRoll(1.0f)
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 15, 0) }
        verify { mockHandler.removeCallbacks(any<Runnable>()) }

        // stopRoll schedules restore after 150ms
        synthesizer.stopRoll()
        verify(exactly = 1) { mockHandler.postDelayed(any(), BikeBellAudioSynthesizer.ROLL_RESTORE_DELAY_MS) }

        // Run restore runnable
        runnableSlot.captured.run()
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 5, 0) }

        synthesizer.close()
    }

    @Test
    fun testBikeBellAudioSynthesizer_closeRestoresBoostedVolumeImmediately() {
        val mockAudioManager = mockk<AudioManager> {
            every { getDevices(any()) } returns emptyArray()
            every { getStreamVolume(AudioManager.STREAM_ALARM) } returns 4
            every { getStreamMaxVolume(AudioManager.STREAM_ALARM) } returns 15
            every { setStreamVolume(any(), any(), any()) } just Runs
        }
        val mockHandler = mockk<Handler>(relaxed = true)
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = BikeBellAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack },
            handler = mockHandler
        )

        synthesizer.ring(0.35f)
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 15, 0) }

        // Closing before restore timeout fires restores volume immediately and removes callbacks
        synthesizer.close()
        verify { mockHandler.removeCallbacks(any<Runnable>()) }
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 4, 0) }
    }
}
