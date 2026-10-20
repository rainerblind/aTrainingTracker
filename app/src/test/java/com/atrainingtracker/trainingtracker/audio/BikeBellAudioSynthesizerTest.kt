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
import android.media.AudioTrack
import io.mockk.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

/**
 * Unit and concurrency test suite for BikeBellAudioSynthesizer (REQ-UI-421.4, TST-UI-384.3).
 */
@RunWith(RobolectricTestRunner::class)
class BikeBellAudioSynthesizerTest {

    @Test
    fun testPcmSynthesis_mathematicalPropertiesAndDuration() {
        val pcm = BikeBellAudioSynthesizer.synthesizeDoubleStrikePcm(44100)
        assertNotNull(pcm)
        // 0.6s at 44100Hz 16-bit mono = 26460 samples * 2 bytes = 52920 bytes
        assertEquals(52920, pcm.size)

        // Sample at t = 0 must be 0 (sin(0) = 0)
        val s0Low = pcm[0].toInt() and 0xFF
        val s0High = pcm[1].toInt()
        val s0 = (s0High shl 8) or s0Low
        assertEquals(0, s0)

        // Verify strike sample calculation conforms to physical envelope
        val sampleAtPeak = BikeBellAudioSynthesizer.calculateStrikeSample(0.0001, 2400.0)
        assertTrue("Sample after initial transient should be non-zero", Math.abs(sampleAtPeak) > 0.0)

        // Decay: sample after 0.5s should be significantly lower than near peak
        val sampleDecayed = BikeBellAudioSynthesizer.calculateStrikeSample(0.5, 2400.0)
        assertTrue("Sample after 0.5s should be decayed", Math.abs(sampleDecayed) < 0.1)

        // Test roll PCM synthesis
        val rollPcm = BikeBellAudioSynthesizer.synthesizeRollPcm(44100)
        assertNotNull(rollPcm)
        // 2.5s at 44100Hz = 110250 samples * 2 bytes = 220500 bytes
        assertEquals(220500, rollPcm.size)
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
        synthesizer.ring(0.70f)
        verify(exactly = 1) { mockTrack1.setVolume(0.70f) }
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
    fun testBikeBellAudioSynthesizer_audioAttributesConfiguration() {
        val attributes = BikeBellAudioSynthesizer.createAudioAttributes()
        assertNotNull(attributes)
        assertEquals(AudioAttributes.USAGE_MEDIA, attributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attributes.contentType)
    }
}
