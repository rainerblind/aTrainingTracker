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
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Pure Kotlin in-memory acoustic synthesizer generating low-latency radar alert chimes (REQ-TRK-063.2).
 *
 * Emits distinct multi-tone acoustic patterns (2.2–3.2 kHz harmonics) cutting through outdoor wind and road
 * noise. Pre-loads static PCM waveforms in memory via [AudioTrack.MODE_STATIC] with [AudioAttributes.USAGE_MEDIA]
 * and executes warm replay ([AudioTrack.pause] + [AudioTrack.setPlaybackHeadPosition] to 0 + [AudioTrack.play])
 * to eliminate AudioFlinger mixer port renegotiation latency and prevent attack transient truncation (ATT-5649, §3.4).
 *
 * Supports phone speaker override routing via [AudioTrack.setPreferredDevice] with [AudioDeviceInfo.TYPE_BUILTIN_SPEAKER]
 * without switching [AudioManager] into telephony communication mode (REQ-TRK-063.5).
 */
class RadarAudioSynthesizer(
    private val context: Context? = null,
    audioManager: AudioManager? = null,
    private val audioTrackFactory: ((pcmData: ByteArray) -> AudioTrack?)? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "RadarAudioSynth"
        const val SAMPLE_RATE: Int = 48000

        // Resonant chime frequencies (Hz) cutting through road noise
        const val FREQ_APPROACH_1: Double = 2400.0
        const val FREQ_APPROACH_2: Double = 2880.0
        const val FREQ_FAST_1: Double = 2800.0
        const val FREQ_FAST_2: Double = 3050.0
        const val FREQ_FAST_3: Double = 3300.0
        const val FREQ_CLEAR: Double = 2100.0
        const val FREQ_DISCONNECT_1: Double = 2600.0
        const val FREQ_DISCONNECT_2: Double = 1750.0
        const val FREQ_LANE_TICK: Double = 2400.0

        /** External audio output device types representing headphones, headsets, or external Bluetooth/USB audio. */
        val EXTERNAL_DEVICE_TYPES: Set<Int> = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_HEARING_AID
        )

        const val TAU_ATTACK: Double = 0.002 // 2 ms attack smoothing

        /**
         * Synthesizes 16-bit mono PCM bytes for the double ascending Approach chime.
         */
        fun synthesizeApproachPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val durationSec = 0.250
            val totalSamples = (durationSec * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            val tone1Start = 0.010
            val tone1End = 0.090
            val tone2Start = 0.110
            val tone2End = 0.220

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0

                if (t in tone1Start..tone1End) {
                    val dt = t - tone1Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > tone1End - 0.015) 0.5 * (1.0 + cos(PI * (t - (tone1End - 0.015)) / 0.015)) else 1.0
                    sample = 0.75 * attack * fadeOut * sin(2.0 * PI * FREQ_APPROACH_1 * dt)
                } else if (t in tone2Start..tone2End) {
                    val dt = t - tone2Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > tone2End - 0.020) 0.5 * (1.0 + cos(PI * (t - (tone2End - 0.020)) / 0.020)) else 1.0
                    sample = 0.85 * attack * fadeOut * sin(2.0 * PI * FREQ_APPROACH_2 * dt)
                }

                writeSampleToBytes(sample, pcmBytes, i * 2)
            }
            return pcmBytes
        }

        /**
         * Synthesizes 16-bit mono PCM bytes for the urgent triple high-pitch Fast Threat chime.
         */
        fun synthesizeFastThreatPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val durationSec = 0.260
            val totalSamples = (durationSec * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            val t1Start = 0.005; val t1End = 0.065
            val t2Start = 0.080; val t2End = 0.140
            val t3Start = 0.155; val t3End = 0.235

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0

                if (t in t1Start..t1End) {
                    val dt = t - t1Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > t1End - 0.010) 0.5 * (1.0 + cos(PI * (t - (t1End - 0.010)) / 0.010)) else 1.0
                    sample = 0.85 * attack * fadeOut * sin(2.0 * PI * FREQ_FAST_1 * dt)
                } else if (t in t2Start..t2End) {
                    val dt = t - t2Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > t2End - 0.010) 0.5 * (1.0 + cos(PI * (t - (t2End - 0.010)) / 0.010)) else 1.0
                    sample = 0.90 * attack * fadeOut * sin(2.0 * PI * FREQ_FAST_2 * dt)
                } else if (t in t3Start..t3End) {
                    val dt = t - t3Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > t3End - 0.015) 0.5 * (1.0 + cos(PI * (t - (t3End - 0.015)) / 0.015)) else 1.0
                    sample = 0.95 * attack * fadeOut * sin(2.0 * PI * FREQ_FAST_3 * dt)
                }

                writeSampleToBytes(sample, pcmBytes, i * 2)
            }
            return pcmBytes
        }

        /**
         * Synthesizes 16-bit mono PCM bytes for the soft single All-Clear chime.
         */
        fun synthesizeAllClearPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val durationSec = 0.200
            val totalSamples = (durationSec * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            val toneStart = 0.010
            val toneEnd = 0.180

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0

                if (t in toneStart..toneEnd) {
                    val dt = t - toneStart
                    val attack = 1.0 - exp(-dt / 0.005)
                    val decay = exp(-dt / 0.060)
                    sample = 0.65 * attack * decay * sin(2.0 * PI * FREQ_CLEAR * dt)
                }

                writeSampleToBytes(sample, pcmBytes, i * 2)
            }
            return pcmBytes
        }

        /**
         * Synthesizes 16-bit mono PCM bytes for the descending two-tone Disconnect chime.
         */
        fun synthesizeDisconnectPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val durationSec = 0.300
            val totalSamples = (durationSec * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            val t1Start = 0.010; val t1End = 0.110
            val t2Start = 0.130; val t2End = 0.270

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0

                if (t in t1Start..t1End) {
                    val dt = t - t1Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > t1End - 0.015) 0.5 * (1.0 + cos(PI * (t - (t1End - 0.015)) / 0.015)) else 1.0
                    sample = 0.80 * attack * fadeOut * sin(2.0 * PI * FREQ_DISCONNECT_1 * dt)
                } else if (t in t2Start..t2End) {
                    val dt = t - t2Start
                    val attack = 1.0 - exp(-dt / TAU_ATTACK)
                    val fadeOut = if (t > t2End - 0.020) 0.5 * (1.0 + cos(PI * (t - (t2End - 0.020)) / 0.020)) else 1.0
                    sample = 0.85 * attack * fadeOut * sin(2.0 * PI * FREQ_DISCONNECT_2 * dt)
                }

                writeSampleToBytes(sample, pcmBytes, i * 2)
            }
            return pcmBytes
        }

        /**
         * Synthesizes 16-bit mono PCM bytes for the subtle spatial lane-change tick cue (REQ-TRK-065.3).
         */
        fun synthesizeLaneChangeTickPcm(sampleRate: Int = SAMPLE_RATE): ByteArray {
            val durationSec = 0.025
            val totalSamples = (durationSec * sampleRate).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val attack = 1.0 - exp(-t / 0.001)
                val decay = exp(-t / 0.005)
                val sample = 0.70 * attack * decay * sin(2.0 * PI * FREQ_LANE_TICK * t)
                writeSampleToBytes(sample, pcmBytes, i * 2)
            }
            return pcmBytes
        }

        private fun writeSampleToBytes(sample: Double, pcmBytes: ByteArray, byteIndex: Int) {
            val clamped = sample.coerceIn(-1.0, 1.0)
            val pcmValue = (clamped * Short.MAX_VALUE).toInt().toShort()
            pcmBytes[byteIndex] = (pcmValue.toInt() and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((pcmValue.toInt() shr 8) and 0xFF).toByte()
        }

        /**
         * Creates [AudioAttributes] for radar playback configured with [AudioAttributes.USAGE_MEDIA] (REQ-TRK-063.2).
         */
        fun createAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
    }

    val approachPcm: ByteArray = synthesizeApproachPcm()
    val fastThreatPcm: ByteArray = synthesizeFastThreatPcm()
    val allClearPcm: ByteArray = synthesizeAllClearPcm()
    val disconnectPcm: ByteArray = synthesizeDisconnectPcm()
    val laneChangeTickPcm: ByteArray = synthesizeLaneChangeTickPcm()

    private val lock = Any()
    private var isClosed = false

    private val audioManager: AudioManager? = audioManager
        ?: (context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)

    val audioAttributes: AudioAttributes = createAudioAttributes()

    private var approachTrack: AudioTrack? = initializeTrack(approachPcm)
    private var fastThreatTrack: AudioTrack? = initializeTrack(fastThreatPcm)
    private var allClearTrack: AudioTrack? = initializeTrack(allClearPcm)
    private var disconnectTrack: AudioTrack? = initializeTrack(disconnectPcm)
    private var laneChangeTickTrack: AudioTrack? = initializeTrack(laneChangeTickPcm)

    private fun initializeTrack(pcmData: ByteArray): AudioTrack? {
        return try {
            audioTrackFactory?.invoke(pcmData) ?: createStaticTrack(pcmData)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize AudioTrack", t)
            null
        }
    }

    private fun createStaticTrack(pcmData: ByteArray): AudioTrack? {
        return try {
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(
                audioAttributes,
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
     * Checks whether connected audio output routing includes external headphones, headsets, or Bluetooth audio.
     */
    fun isHeadphonesOrBluetoothConnected(): Boolean {
        val am = audioManager ?: return false
        return try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices?.any { it.type in EXTERNAL_DEVICE_TYPES } == true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to query audio output devices", t)
            false
        }
    }

    /**
     * Applies preferred audio device routing ([RadarAudioRoute.FORCE_SPEAKER] vs [RadarAudioRoute.SYSTEM_DEFAULT]).
     */
    fun applyAudioRoute(track: AudioTrack?, route: RadarAudioRoute) {
        if (track == null) return
        val am = audioManager ?: return
        try {
            if (route == RadarAudioRoute.FORCE_SPEAKER) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val builtInSpeaker = devices?.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (builtInSpeaker != null) {
                    track.setPreferredDevice(builtInSpeaker)
                }
            } else {
                track.setPreferredDevice(null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to configure AudioTrack routing", t)
        }
    }

    private fun playTrack(
        track: AudioTrack?,
        volume: Float,
        route: RadarAudioRoute,
        leftGain: Float = 1.0f,
        rightGain: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        synchronized(lock) {
            if (isClosed || track == null) return
            try {
                applyAudioRoute(track, route)
                val clamped = volume.coerceIn(0.0f, 1.0f)
                val finalLeft = (clamped * leftGain.coerceIn(0.0f, 1.0f)).coerceIn(0.0f, 1.0f)
                val finalRight = (clamped * rightGain.coerceIn(0.0f, 1.0f)).coerceIn(0.0f, 1.0f)
                val effectiveVolume = maxOf(finalLeft, finalRight)
                track.setVolume(effectiveVolume)

                val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
                try {
                    val params = track.playbackParams
                    if (params != null && (clampedPitch != 1.0f || params.pitch != 1.0f || params.speed != 1.0f)) {
                        params.pitch = clampedPitch
                        params.speed = clampedPitch
                        track.playbackParams = params
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "PlaybackParams pitch adjustment failed", t)
                }

                // Reliable warm MODE_STATIC replay invariant (§3.4, ATT-5649)
                track.pause()
                track.playbackHeadPosition = 0
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "Error playing audio chime", e)
            }
        }
    }

    fun playApproach(volume: Float = 0.9f, route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT) {
        playTrack(approachTrack, volume, route, 1.0f, 1.0f, 1.0f)
    }

    fun playApproach(
        volume: Float,
        route: RadarAudioRoute,
        leftGain: Float,
        rightGain: Float,
        pitch: Float = 1.0f
    ) {
        playTrack(approachTrack, volume, route, leftGain, rightGain, pitch)
    }

    fun playFastThreat(volume: Float = 0.9f, route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT) {
        playTrack(fastThreatTrack, volume, route, 1.0f, 1.0f, 1.0f)
    }

    fun playFastThreat(
        volume: Float,
        route: RadarAudioRoute,
        leftGain: Float,
        rightGain: Float,
        pitch: Float = 1.0f
    ) {
        playTrack(fastThreatTrack, volume, route, leftGain, rightGain, pitch)
    }

    fun playAllClear(volume: Float = 0.7f, route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT) {
        playTrack(allClearTrack, volume, route)
    }

    fun playDisconnect(volume: Float = 0.9f, route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT) {
        playTrack(disconnectTrack, volume, route)
    }

    fun playLaneChangeTick(
        volume: Float = 0.7f,
        route: RadarAudioRoute = RadarAudioRoute.SYSTEM_DEFAULT,
        leftGain: Float = 1.0f,
        rightGain: Float = 1.0f
    ) {
        playTrack(laneChangeTickTrack, volume, route, leftGain, rightGain, 1.0f)
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            listOf(approachTrack, fastThreatTrack, allClearTrack, disconnectTrack, laneChangeTickTrack).forEach { track ->
                try {
                    track?.pause()
                    track?.flush()
                    track?.release()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error releasing AudioTrack", t)
                }
            }
            approachTrack = null
            fastThreatTrack = null
            allClearTrack = null
            disconnectTrack = null
            laneChangeTickTrack = null
        }
    }
}
