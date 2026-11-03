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

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Pure Kotlin in-memory acoustic synthesizer generating metallic brass bicycle bell audio (REQ-UI-421.4).
 *
 * Emits an authentic warm brass resonance using a 5-mode inharmonic acoustic model (1020 Hz hum,
 * 2040 Hz fundamental dome resonance, 3120 Hz tierce, 4280 Hz nominal chime, and 6200 Hz clapper shimmer
 * with smooth attack envelope and exponential decay) pre-loaded in memory via [AudioTrack.MODE_STATIC]
 * for zero-latency instant playback without disk I/O (ATT-5282).
 *
 * Configured with [AudioAttributes.USAGE_ALARM] and forced [AudioDeviceInfo.TYPE_BUILTIN_SPEAKER]
 * routing to project bell audio loudly through the phone loudspeaker even when headphones are connected.
 * Hardware alarm volume is temporarily boosted to 100% during rings and rolls and auto-restored afterwards.
 * All public operations are thread-safe ([synchronized]), and resources are cleanly released via [close].
 */
class BikeBellAudioSynthesizer(
    private val context: Context? = null,
    audioManager: AudioManager? = null,
    audioTrackFactory: ((pcmData: ByteArray) -> AudioTrack?)? = null,
    handler: Handler? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "BikeBellAudioSynth"
        const val SAMPLE_RATE: Int = 44100
        const val DOUBLE_STRIKE_DURATION_SEC: Double = 0.6
        const val STRIKE_2_DELAY_SEC: Double = 0.110
        const val ROLL_DURATION_SEC: Double = 2.5
        const val ROLL_STRIKE_INTERVAL_SEC: Double = 0.077
        const val RING_RESTORE_DELAY_MS: Long = 650L
        const val ROLL_RESTORE_DELAY_MS: Long = 150L

        // Resonant modal frequencies for authentic brass bell dome (REQ-UI-421.4, ATT-5282)
        const val HUM_FREQ: Double = 1020.0
        const val FUNDAMENTAL_FREQ: Double = 2040.0
        const val TIERCE_FREQ: Double = 3120.0
        const val NOMINAL_FREQ: Double = 4280.0
        const val CLAPPER_FREQ: Double = 6200.0

        // Clapper rebound detuned frequencies (strike 2, +2.5%)
        const val STRIKE_2_DETUNE_RATIO: Double = 1.025
        const val STRIKE_2_HUM: Double = HUM_FREQ * STRIKE_2_DETUNE_RATIO
        const val STRIKE_2_FUNDAMENTAL: Double = FUNDAMENTAL_FREQ * STRIKE_2_DETUNE_RATIO
        const val STRIKE_2_TIERCE: Double = TIERCE_FREQ * STRIKE_2_DETUNE_RATIO
        const val STRIKE_2_NOMINAL: Double = NOMINAL_FREQ * STRIKE_2_DETUNE_RATIO
        const val STRIKE_2_CLAPPER: Double = CLAPPER_FREQ * STRIKE_2_DETUNE_RATIO

        // Exponential decay time constants (seconds) for natural brass bell
        const val TAU_HUM: Double = 0.55
        const val TAU_FUNDAMENTAL: Double = 0.38
        const val TAU_TIERCE: Double = 0.08
        const val TAU_NOMINAL: Double = 0.03
        const val TAU_CLAPPER: Double = 0.015

        // Modal amplitudes
        const val AMP_HUM: Double = 0.32
        const val AMP_FUNDAMENTAL: Double = 0.46
        const val AMP_TIERCE: Double = 0.14
        const val AMP_NOMINAL: Double = 0.06
        const val AMP_CLAPPER: Double = 0.02

        // Clapper strike attack rise time constant (seconds) ~1.5 ms
        const val TAU_ATTACK: Double = 0.0015

        // Backward-compatible aliases for legacy 4-mode constants
        @Deprecated("Replaced by TIERCE_FREQ in 5-mode brass synthesis", ReplaceWith("TIERCE_FREQ"))
        const val OVERTONE_1_FREQ: Double = TIERCE_FREQ
        @Deprecated("Replaced by NOMINAL_FREQ in 5-mode brass synthesis", ReplaceWith("NOMINAL_FREQ"))
        const val OVERTONE_2_FREQ: Double = NOMINAL_FREQ
        @Deprecated("Replaced by CLAPPER_FREQ in 5-mode brass synthesis", ReplaceWith("CLAPPER_FREQ"))
        const val SHIMMER_FREQ: Double = CLAPPER_FREQ

        @Deprecated("Replaced by STRIKE_2_TIERCE", ReplaceWith("STRIKE_2_TIERCE"))
        const val STRIKE_2_OVERTONE_1: Double = STRIKE_2_TIERCE
        @Deprecated("Replaced by STRIKE_2_NOMINAL", ReplaceWith("STRIKE_2_NOMINAL"))
        const val STRIKE_2_OVERTONE_2: Double = STRIKE_2_NOMINAL
        @Deprecated("Replaced by STRIKE_2_CLAPPER", ReplaceWith("STRIKE_2_CLAPPER"))
        const val STRIKE_2_SHIMMER: Double = STRIKE_2_CLAPPER

        @Deprecated("Replaced by TAU_FUNDAMENTAL", ReplaceWith("TAU_FUNDAMENTAL"))
        const val TAU_1: Double = TAU_FUNDAMENTAL
        @Deprecated("Replaced by TAU_TIERCE", ReplaceWith("TAU_TIERCE"))
        const val TAU_2: Double = TAU_TIERCE
        @Deprecated("Replaced by TAU_NOMINAL", ReplaceWith("TAU_NOMINAL"))
        const val TAU_3: Double = TAU_NOMINAL
        @Deprecated("Replaced by TAU_CLAPPER", ReplaceWith("TAU_CLAPPER"))
        const val TAU_4: Double = TAU_CLAPPER

        @Deprecated("Replaced by AMP_FUNDAMENTAL", ReplaceWith("AMP_FUNDAMENTAL"))
        const val AMP_1: Double = AMP_FUNDAMENTAL
        @Deprecated("Replaced by AMP_TIERCE", ReplaceWith("AMP_TIERCE"))
        const val AMP_2: Double = AMP_TIERCE
        @Deprecated("Replaced by AMP_NOMINAL", ReplaceWith("AMP_NOMINAL"))
        const val AMP_3: Double = AMP_NOMINAL
        @Deprecated("Replaced by AMP_CLAPPER", ReplaceWith("AMP_CLAPPER"))
        const val AMP_4: Double = AMP_CLAPPER

        /**
         * Calculates acoustic amplitude sample for a single bell dome strike at offset [dt].
         *
         * Generates an authentic warm brass timbre via 5 inharmonic modes (sub-octave hum,
         * fundamental prime, tierce, nominal, clapper) with an exponential attack rise envelope
         * eliminating micro-speaker cone snapping transients (REQ-UI-421.4, ATT-5282).
         *
         * @param dt Time elapsed since strike in seconds.
         * @param fundamental Fundamental frequency in Hertz.
         * @return Dimensionless acoustic sample in range [-1.0, 1.0].
         */
        fun calculateStrikeSample(dt: Double, fundamental: Double): Double {
            if (dt < 0.0) return 0.0
            val ratio = fundamental / FUNDAMENTAL_FREQ
            val f0 = HUM_FREQ * ratio
            val f1 = FUNDAMENTAL_FREQ * ratio
            val f2 = TIERCE_FREQ * ratio
            val f3 = NOMINAL_FREQ * ratio
            val f4 = CLAPPER_FREQ * ratio

            val attack = 1.0 - Math.exp(-dt / TAU_ATTACK)
            val s0 = AMP_HUM * Math.exp(-dt / TAU_HUM) * Math.sin(2.0 * Math.PI * f0 * dt)
            val s1 = AMP_FUNDAMENTAL * Math.exp(-dt / TAU_FUNDAMENTAL) * Math.sin(2.0 * Math.PI * f1 * dt)
            val s2 = AMP_TIERCE * Math.exp(-dt / TAU_TIERCE) * Math.sin(2.0 * Math.PI * f2 * dt)
            val s3 = AMP_NOMINAL * Math.exp(-dt / TAU_NOMINAL) * Math.sin(2.0 * Math.PI * f3 * dt)
            val s4 = AMP_CLAPPER * Math.exp(-dt / TAU_CLAPPER) * Math.sin(2.0 * Math.PI * f4 * dt)
            return attack * (s0 + s1 + s2 + s3 + s4)
        }

        /**
         * Calculates acoustic amplitude sample for rapid repeated striker rolls with clapper dampening
         * and attack envelope smoothing (REQ-UI-421.4, ATT-5282).
         */
        fun calculateRollStrikeSample(dt: Double, fundamental: Double): Double {
            if (dt < 0.0) return 0.0
            val ratio = fundamental / FUNDAMENTAL_FREQ
            val f0 = HUM_FREQ * ratio
            val f1 = FUNDAMENTAL_FREQ * ratio
            val f2 = TIERCE_FREQ * ratio
            val f3 = NOMINAL_FREQ * ratio
            val f4 = CLAPPER_FREQ * ratio

            val attack = 1.0 - Math.exp(-dt / TAU_ATTACK)
            val s0 = AMP_HUM * Math.exp(-dt / 0.10) * Math.sin(2.0 * Math.PI * f0 * dt)
            val s1 = AMP_FUNDAMENTAL * Math.exp(-dt / 0.08) * Math.sin(2.0 * Math.PI * f1 * dt)
            val s2 = AMP_TIERCE * Math.exp(-dt / 0.05) * Math.sin(2.0 * Math.PI * f2 * dt)
            val s3 = AMP_NOMINAL * Math.exp(-dt / 0.025) * Math.sin(2.0 * Math.PI * f3 * dt)
            val s4 = AMP_CLAPPER * Math.exp(-dt / 0.012) * Math.sin(2.0 * Math.PI * f4 * dt)
            return attack * (s0 + s1 + s2 + s3 + s4)
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
                val clamped = (sum * 0.85).coerceIn(-1.0, 1.0)
                val pcmValue = (clamped * Short.MAX_VALUE).toInt().toShort()

                val byteIndex = i * 2
                pcmBytes[byteIndex] = (pcmValue.toInt() and 0xFF).toByte()
                pcmBytes[byteIndex + 1] = ((pcmValue.toInt() shr 8) and 0xFF).toByte()
            }
            return pcmBytes
        }
        /**
         * Creates [AudioAttributes] for bike bell playback configured with [AudioAttributes.USAGE_ALARM]
         * and [AudioAttributes.CONTENT_TYPE_SONIFICATION] (REQ-UI-421.4).
         */
        fun createAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
    }

    val doubleStrikePcm: ByteArray = synthesizeDoubleStrikePcm()
    val rollPcm: ByteArray = synthesizeRollPcm()

    private val lock = Any()
    private var isClosed = false

    private val audioManager: AudioManager? = audioManager
        ?: (context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)

    private val handler: Handler? = handler ?: try {
        Handler(Looper.getMainLooper())
    } catch (_: Throwable) {
        null
    }

    private var savedAlarmVolume: Int? = null

    private val restoreRunnable = Runnable {
        synchronized(lock) {
            restoreAlarmVolumeLocked()
        }
    }

    private fun applySpeakerRouting(track: AudioTrack?) {
        if (track == null) return
        val am = audioManager ?: return
        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val builtInSpeaker = devices?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (builtInSpeaker != null) {
                track.setPreferredDevice(builtInSpeaker)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to setPreferredDevice(TYPE_BUILTIN_SPEAKER)", t)
        }
    }

    private fun boostAlarmVolumeLocked() {
        val am = audioManager ?: return
        try {
            if (savedAlarmVolume == null) {
                savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to boost STREAM_ALARM volume", t)
        }
    }

    private fun restoreAlarmVolumeLocked() {
        val original = savedAlarmVolume ?: return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore STREAM_ALARM volume", t)
        } finally {
            savedAlarmVolume = null
        }
    }

    private var singleStrikeTrack: AudioTrack? = try {
        val track = audioTrackFactory?.invoke(doubleStrikePcm) ?: createStaticTrack(doubleStrikePcm)
        applySpeakerRouting(track)
        track
    } catch (t: Throwable) {
        Log.w(TAG, "AudioTrack init failed for single strike", t)
        null
    }

    private var rollTrack: AudioTrack? = try {
        val track = audioTrackFactory?.invoke(rollPcm) ?: createStaticTrack(rollPcm)
        applySpeakerRouting(track)
        track
    } catch (t: Throwable) {
        Log.w(TAG, "AudioTrack init failed for roll", t)
        null
    }

    private fun createStaticTrack(pcmData: ByteArray): AudioTrack? {
        return try {
            val attributes = createAudioAttributes()
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
     * Forces speaker output, temporarily boosts hardware alarm volume to 100%, and auto-restores
     * after [RING_RESTORE_DELAY_MS] (REQ-UI-421.4).
     *
     * @param volume Linear volume scale in range [0.0f, 1.0f].
     */
    fun ring(volume: Float = 0.35f) {
        synchronized(lock) {
            if (isClosed) return
            try {
                val track = singleStrikeTrack ?: return
                applySpeakerRouting(track)
                boostAlarmVolumeLocked()
                handler?.removeCallbacks(restoreRunnable)
                handler?.postDelayed(restoreRunnable, RING_RESTORE_DELAY_MS)

                val clamped = volume.coerceIn(0.0f, 1.0f)
                track.setVolume(clamped)
                track.stop()
                track.playbackHeadPosition = 0
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Error playing bell ring", e)
            }
        }
    }

    /**
     * Starts continuous striker roll (*"Brriiiing!"*) playback at the specified [volume]
     * looping indefinitely until [stopRoll] is invoked (REQ-UI-421.4).
     *
     * Forces speaker output and temporarily boosts hardware alarm volume to 100%.
     */
    fun startRoll(volume: Float = 1.0f) {
        synchronized(lock) {
            if (isClosed) return
            try {
                val track = rollTrack ?: return
                applySpeakerRouting(track)
                boostAlarmVolumeLocked()
                handler?.removeCallbacks(restoreRunnable)

                val clamped = volume.coerceIn(0.0f, 1.0f)
                track.setVolume(clamped)
                track.stop()
                track.playbackHeadPosition = 0
                val totalFrames = rollPcm.size / 2
                track.setLoopPoints(0, totalFrames, -1)
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Error starting bell roll", e)
            }
        }
    }

    /**
     * Stops continuous striker roll playback and schedules hardware alarm volume auto-restore
     * after [ROLL_RESTORE_DELAY_MS] (REQ-UI-421.4).
     */
    fun stopRoll() {
        synchronized(lock) {
            if (isClosed) return
            try {
                rollTrack?.stop()
                rollTrack?.playbackHeadPosition = 0
                handler?.removeCallbacks(restoreRunnable)
                handler?.postDelayed(restoreRunnable, ROLL_RESTORE_DELAY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping bell roll", e)
            }
        }
    }

    /**
     * Releases native [AudioTrack] audio hardware resources and immediately restores
     * hardware alarm volume if currently boosted.
     */
    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            handler?.removeCallbacks(restoreRunnable)
            restoreAlarmVolumeLocked()
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
