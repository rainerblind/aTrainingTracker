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
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Pure Kotlin in-memory acoustic synthesizer generating metallic brass bicycle bell audio (REQ-UI-421.4).
 *
 * Emits crisp metallic resonance using a 4-mode inharmonic acoustic model (2.4 kHz fundamental,
 * 3.84 kHz, 5.28 kHz, and 7.6 kHz overtones with exponential decay envelopes) pre-loaded in memory
 * via [AudioTrack.MODE_STATIC] for zero-latency instant playback without disk I/O.
 *
 * Configured with [AudioAttributes.USAGE_ASSISTANCE_SONIFICATION] without audio focus ducking to
 * guarantee non-intrusive coexistence with turn-by-turn spoken prompts and music playback.
 * All public operations are thread-safe ([synchronized]), and resources are cleanly released via [close].
 */
class BikeBellAudioSynthesizer(
    audioTrackFactory: ((pcmData: ByteArray) -> AudioTrack?)? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "BikeBellAudioSynth"
        const val SAMPLE_RATE: Int = 44100
        const val DOUBLE_STRIKE_DURATION_SEC: Double = 0.6
        const val STRIKE_2_DELAY_SEC: Double = 0.110
        const val ROLL_DURATION_SEC: Double = 2.5
        const val ROLL_STRIKE_INTERVAL_SEC: Double = 0.077

        // Resonant modal frequencies for 2.4 kHz brass bell dome
        const val FUNDAMENTAL_FREQ: Double = 2400.0
        const val OVERTONE_1_FREQ: Double = 3840.0
        const val OVERTONE_2_FREQ: Double = 5280.0
        const val SHIMMER_FREQ: Double = 7600.0

        // Clapper rebound detuned frequencies (strike 2)
        const val STRIKE_2_FUNDAMENTAL: Double = 2460.0
        const val STRIKE_2_OVERTONE_1: Double = 3936.0
        const val STRIKE_2_OVERTONE_2: Double = 5412.0
        const val STRIKE_2_SHIMMER: Double = 7790.0

        // Exponential decay time constants (seconds)
        const val TAU_1: Double = 0.35
        const val TAU_2: Double = 0.18
        const val TAU_3: Double = 0.09
        const val TAU_4: Double = 0.04

        // Modal amplitudes
        const val AMP_1: Double = 0.50
        const val AMP_2: Double = 0.28
        const val AMP_3: Double = 0.15
        const val AMP_4: Double = 0.07

        /**
         * Calculates acoustic amplitude sample for a single bell dome strike at offset [dt].
         *
         * @param dt Time elapsed since strike in seconds.
         * @param fundamental Fundamental frequency in Hertz.
         * @return Dimensionless acoustic sample in range [-1.0, 1.0].
         */
        fun calculateStrikeSample(dt: Double, fundamental: Double): Double {
            if (dt < 0.0) return 0.0
            val ratio = fundamental / FUNDAMENTAL_FREQ
            val f1 = FUNDAMENTAL_FREQ * ratio
            val f2 = OVERTONE_1_FREQ * ratio
            val f3 = OVERTONE_2_FREQ * ratio
            val f4 = SHIMMER_FREQ * ratio

            val s1 = AMP_1 * Math.exp(-dt / TAU_1) * Math.sin(2.0 * Math.PI * f1 * dt)
            val s2 = AMP_2 * Math.exp(-dt / TAU_2) * Math.sin(2.0 * Math.PI * f2 * dt)
            val s3 = AMP_3 * Math.exp(-dt / TAU_3) * Math.sin(2.0 * Math.PI * f3 * dt)
            val s4 = AMP_4 * Math.exp(-dt / TAU_4) * Math.sin(2.0 * Math.PI * f4 * dt)
            return s1 + s2 + s3 + s4
        }

        /**
         * Calculates acoustic amplitude sample for rapid repeated striker rolls with clapper dampening.
         */
        fun calculateRollStrikeSample(dt: Double, fundamental: Double): Double {
            if (dt < 0.0) return 0.0
            val ratio = fundamental / FUNDAMENTAL_FREQ
            val f1 = FUNDAMENTAL_FREQ * ratio
            val f2 = OVERTONE_1_FREQ * ratio
            val f3 = OVERTONE_2_FREQ * ratio
            val f4 = SHIMMER_FREQ * ratio

            val s1 = AMP_1 * Math.exp(-dt / 0.08) * Math.sin(2.0 * Math.PI * f1 * dt)
            val s2 = AMP_2 * Math.exp(-dt / 0.06) * Math.sin(2.0 * Math.PI * f2 * dt)
            val s3 = AMP_3 * Math.exp(-dt / 0.04) * Math.sin(2.0 * Math.PI * f3 * dt)
            val s4 = AMP_4 * Math.exp(-dt / 0.02) * Math.sin(2.0 * Math.PI * f4 * dt)
            return s1 + s2 + s3 + s4
        }

        /**
         * Synthesizes 16-bit signed PCM mono audio bytes for the double-strike (*"Ding-Ding!"*) bell tone.
         */
        fun synthesizeDoubleStrikePcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val totalSamples = (DOUBLE_STRIKE_DURATION_SEC * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val s1 = calculateStrikeSample(t, FUNDAMENTAL_FREQ)
                val s2 = calculateStrikeSample(t - STRIKE_2_DELAY_SEC, STRIKE_2_FUNDAMENTAL)
                val combined = ((s1 + s2) * 0.95).coerceIn(-1.0, 1.0)
                val pcmValue = (combined * Short.MAX_VALUE).toInt().toShort()

                val byteIndex = i * 2
                pcmBytes[byteIndex] = (pcmValue.toInt() and 0xFF).toByte()
                pcmBytes[byteIndex + 1] = ((pcmValue.toInt() shr 8) and 0xFF).toByte()
            }
            return pcmBytes
        }

        /**
         * Synthesizes 16-bit signed PCM mono audio bytes for continuous striker roll (*"Brriiiing!"*).
         */
        fun synthesizeRollPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val totalSamples = (ROLL_DURATION_SEC * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)
            val strikeInterval = ROLL_STRIKE_INTERVAL_SEC

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sum = 0.0
                var strikeIndex = 0
                while (strikeIndex * strikeInterval <= t) {
                    val strikeTime = strikeIndex * strikeInterval
                    val dt = t - strikeTime
                    val freq = if (strikeIndex % 2 == 0) FUNDAMENTAL_FREQ else STRIKE_2_FUNDAMENTAL
                    sum += calculateRollStrikeSample(dt, freq)
                    strikeIndex++
                }
                val clamped = (sum * 0.90).coerceIn(-1.0, 1.0)
                val pcmValue = (clamped * Short.MAX_VALUE).toInt().toShort()

                val byteIndex = i * 2
                pcmBytes[byteIndex] = (pcmValue.toInt() and 0xFF).toByte()
                pcmBytes[byteIndex + 1] = ((pcmValue.toInt() shr 8) and 0xFF).toByte()
            }
            return pcmBytes
        }
    }

    val doubleStrikePcm: ByteArray = synthesizeDoubleStrikePcm()
    val rollPcm: ByteArray = synthesizeRollPcm()

    private val lock = Any()
    private var isClosed = false

    private var singleStrikeTrack: AudioTrack? = try {
        audioTrackFactory?.invoke(doubleStrikePcm) ?: createStaticTrack(doubleStrikePcm)
    } catch (t: Throwable) {
        Log.w(TAG, "AudioTrack init failed for single strike", t)
        null
    }

    private var rollTrack: AudioTrack? = try {
        audioTrackFactory?.invoke(rollPcm) ?: createStaticTrack(rollPcm)
    } catch (t: Throwable) {
        Log.w(TAG, "AudioTrack init failed for roll", t)
        null
    }

    private fun createStaticTrack(pcmData: ByteArray): AudioTrack? {
        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(
                attributes,
                format,
                pcmData.size,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(pcmData, 0, pcmData.size)
            track
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack instantiation failed: ${t.message}")
            null
        }
    }

    /**
     * Plays the double-strike (*"Ding-Ding!"*) bell tone at the specified [volume].
     *
     * @param volume Linear volume scale in range [0.0f, 1.0f].
     */
    fun ring(volume: Float = 0.70f) {
        synchronized(lock) {
            if (isClosed) return
            try {
                val track = singleStrikeTrack ?: return
                val clamped = volume.coerceIn(0.0f, 1.0f)
                track.setVolume(clamped)
                track.pause()
                track.playbackHeadPosition = 0
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Error playing bell ring", e)
            }
        }
    }

    /**
     * Starts continuous striker roll (*"Brriiiing!"*) playback at the specified [volume].
     */
    fun startRoll(volume: Float = 1.0f) {
        synchronized(lock) {
            if (isClosed) return
            try {
                val track = rollTrack ?: return
                val clamped = volume.coerceIn(0.0f, 1.0f)
                track.setVolume(clamped)
                track.pause()
                track.playbackHeadPosition = 0
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Error starting bell roll", e)
            }
        }
    }

    /**
     * Stops continuous striker roll playback.
     */
    fun stopRoll() {
        synchronized(lock) {
            if (isClosed) return
            try {
                rollTrack?.pause()
                rollTrack?.playbackHeadPosition = 0
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping bell roll", e)
            }
        }
    }

    /**
     * Releases native [AudioTrack] audio hardware resources.
     */
    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            try {
                singleStrikeTrack?.pause()
                singleStrikeTrack?.flush()
                singleStrikeTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing single strike track", e)
            } finally {
                singleStrikeTrack = null
            }
            try {
                rollTrack?.pause()
                rollTrack?.flush()
                rollTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing roll track", e)
            } finally {
                rollTrack = null
            }
        }
    }
}
