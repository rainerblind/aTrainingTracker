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

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [RadarAudioSynthesizer] (TST-TRK-063.2, TST-TRK-063.5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadarAudioSynthesizerTest {

    @Test
    fun pcmSynthesis_generatesValid16BitPcmByteArrays() {
        val approach = RadarAudioSynthesizer.synthesizeApproachPcm()
        assertTrue("Approach PCM byte array must not be empty", approach.isNotEmpty())
        assertEquals("Approach PCM size must be 16-bit aligned", 0, approach.size % 2)

        val fastThreat = RadarAudioSynthesizer.synthesizeFastThreatPcm()
        assertTrue("Fast threat PCM byte array must not be empty", fastThreat.isNotEmpty())
        assertEquals("Fast threat PCM size must be 16-bit aligned", 0, fastThreat.size % 2)

        val allClear = RadarAudioSynthesizer.synthesizeAllClearPcm()
        assertTrue("All-clear PCM byte array must not be empty", allClear.isNotEmpty())
        assertEquals("All-clear PCM size must be 16-bit aligned", 0, allClear.size % 2)

        val disconnect = RadarAudioSynthesizer.synthesizeDisconnectPcm()
        assertTrue("Disconnect PCM byte array must not be empty", disconnect.isNotEmpty())
        assertEquals("Disconnect PCM size must be 16-bit aligned", 0, disconnect.size % 2)
    }

    @Test
    fun audioAttributes_configuresUsageMedia() {
        val attrs = RadarAudioSynthesizer.createAudioAttributes()
        assertEquals(AudioAttributes.USAGE_MEDIA, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, attrs.contentType)
    }

    @Test
    fun playback_executesWarmModeStaticReplayWithoutCallingStop() {
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = RadarAudioSynthesizer(
            audioTrackFactory = { mockTrack }
        )

        synthesizer.playApproach(0.85f, RadarAudioRoute.SYSTEM_DEFAULT)

        verifyOrder {
            mockTrack.setVolume(0.85f)
            mockTrack.pause()
            mockTrack.playbackHeadPosition = 0
            mockTrack.play()
        }

        // Coding standard §3.4 strictly prohibits track.stop() on persistent static tracks (ATT-5649)
        verify(exactly = 0) { mockTrack.stop() }

        synthesizer.close()
    }

    @Test
    fun applyAudioRoute_configuresPreferredDeviceForSpeakerAndDefault() {
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val mockAudioManager = mockk<AudioManager>(relaxed = true)
        val mockSpeakerDevice = mockk<AudioDeviceInfo> {
            every { type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        every { mockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(mockSpeakerDevice)

        val synthesizer = RadarAudioSynthesizer(
            audioManager = mockAudioManager,
            audioTrackFactory = { mockTrack }
        )

        // Force Phone Speaker
        synthesizer.applyAudioRoute(mockTrack, RadarAudioRoute.FORCE_SPEAKER)
        verify { mockTrack.setPreferredDevice(mockSpeakerDevice) }

        // System Default
        synthesizer.applyAudioRoute(mockTrack, RadarAudioRoute.SYSTEM_DEFAULT)
        verify { mockTrack.setPreferredDevice(null) }

        synthesizer.close()
    }

    @Test
    fun close_releasesAllAudioTracksCleanly() {
        val mockTrack = mockk<AudioTrack>(relaxed = true)
        val synthesizer = RadarAudioSynthesizer(
            audioTrackFactory = { mockTrack }
        )

        synthesizer.close()

        verify(atLeast = 1) { mockTrack.pause() }
        verify(atLeast = 1) { mockTrack.flush() }
        verify(atLeast = 1) { mockTrack.release() }
    }
}
