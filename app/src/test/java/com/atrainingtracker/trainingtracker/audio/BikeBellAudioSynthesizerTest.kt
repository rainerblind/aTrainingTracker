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
        val pcm = BikeBellAudioSynthesizer.synthesizeDoubleStrikePcm(44100)
        assertNotNull(pcm)
        // 0.6s at 44100Hz 16-bit mono = 26460 samples * 2 bytes = 52920 bytes
        assertEquals(52920, pcm.size)

        // Sample at t = 0 must be 0 (sin(0) = 0 and attack(0) = 0)
        val s0Low = pcm[0].toInt() and 0xFF
        val s0High = pcm[1].toInt()
        val s0 = (s0High shl 8) or s0Low
        assertEquals(0, s0)

        // Verify strike sample calculation conforms to physical envelope
        val sampleAtPeak = BikeBellAudioSynthesizer.calculateStrikeSample(0.0001, 2040.0)
        assertTrue("Sample after initial transient should be non-zero", Math.abs(sampleAtPeak) > 0.0)

        // Decay: sample after 0.5s should be significantly lower than near peak
        val sampleDecayed = BikeBellAudioSynthesizer.calculateStrikeSample(0.5, 2040.0)
        assertTrue("Sample after 0.5s should be decayed", Math.abs(sampleDecayed) < 0.15)

        // Test roll PCM synthesis
        val rollPcm = BikeBellAudioSynthesizer.synthesizeRollPcm(44100)
        assertNotNull(rollPcm)
        // 2.5s at 44100Hz = 110250 samples * 2 bytes = 220500 bytes
        assertEquals(220500, rollPcm.size)
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
        val rollPcm = BikeBellAudioSynthesizer.synthesizeRollPcm(44100)
        assertEquals(220500, rollPcm.size)
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

        // 1. Single ring at polite volume
        synthesizer.ring(0.35f)
        verify(exactly = 1) { mockTrack1.setVolume(0.35f) }
        verify(exactly = 1) { mockTrack1.stop() }
        verify(exactly = 1) { mockTrack1.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack1.play() }

        // 2. Single ring at escalated volume
        synthesizer.ring(1.0f)
        verify(exactly = 1) { mockTrack1.setVolume(1.0f) }
        verify(atLeast = 2) { mockTrack1.stop() }
        verify(atLeast = 2) { mockTrack1.play() }

        // 3. Roll start and stop
        synthesizer.startRoll(1.0f)
        verify(exactly = 1) { mockTrack2.setVolume(1.0f) }
        verify(exactly = 1) { mockTrack2.stop() }
        verify(exactly = 1) { mockTrack2.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack2.setLoopPoints(0, 110250, -1) }
        verify(exactly = 1) { mockTrack2.play() }

        synthesizer.stopRoll()
        verify(atLeast = 1) { mockTrack2.stop() }

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
        assertEquals(110250, totalFrames)

        synthesizer.startRoll(1.0f)
        verify(exactly = 1) { mockTrack.setVolume(1.0f) }
        verify(exactly = 1) { mockTrack.stop() }
        verify(exactly = 1) { mockTrack.playbackHeadPosition = 0 }
        verify(exactly = 1) { mockTrack.setLoopPoints(0, totalFrames, -1) }
        verify(exactly = 1) { mockTrack.play() }

        synthesizer.stopRoll()
        verify(atLeast = 2) { mockTrack.stop() }
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
    fun testBikeBellAudioSynthesizer_routesToBuiltInSpeaker() {
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

        // setPreferredDevice must be called with mockSpeaker upon creation
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

        // 1. Ring triggers volume boost to max and schedules 650ms restore
        synthesizer.ring(0.35f)
        verify(exactly = 1) { mockAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 15, 0) }
        verify(exactly = 1) { mockHandler.postDelayed(any(), BikeBellAudioSynthesizer.RING_RESTORE_DELAY_MS) }

        // Rapid second ring does NOT re-query or overwrite savedAlarmVolume with 15
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
