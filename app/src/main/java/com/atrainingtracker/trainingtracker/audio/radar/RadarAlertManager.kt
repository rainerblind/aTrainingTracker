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
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * State-transition alert manager coordinating radar acoustic chimes and haptic feedback (REQ-TRK-063.1).
 *
 * Observes [RadarStateRepository.radarState] flow in [com.atrainingtracker.trainingtracker.tracker.TrackerService]
 * to deliver reliable alerts during active tracking including screen-off and background execution.
 *
 * Manages transient background audio ducking ([AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK], REQ-TRK-063.4)
 * and temporary hardware speaker volume boost with guaranteed auto-restore (REQ-TRK-063.3).
 */
class RadarAlertManager @JvmOverloads constructor(
    private val context: Context,
    private val tuningDataStore: TuningPreferencesDataStore? = null,
    audioSynthesizer: RadarAudioSynthesizer? = null,
    audioManager: AudioManager? = null,
    vibrator: Vibrator? = null,
    handler: Handler? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AutoCloseable {

    companion object {
        private const val TAG = "RadarAlertManager"
        const val CHIME_DURATION_MS: Long = 350L
        const val DEBOUNCE_COOLDOWN_MS: Long = 800L
    }

    private val lock = Any()
    private var isReleased = false

    private val actualDataStore: TuningPreferencesDataStore? by lazy {
        tuningDataStore ?: try {
            TuningPreferencesDataStore.getInstance(context)
        } catch (_: Throwable) {
            null
        }
    }

    private val audioManager: AudioManager? = audioManager
        ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)

    private val vibrator: Vibrator? = vibrator
        ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    private val synthesizer: RadarAudioSynthesizer = audioSynthesizer
        ?: RadarAudioSynthesizer(context, this.audioManager)

    private val handler: Handler? = handler ?: try {
        Handler(Looper.getMainLooper())
    } catch (_: Throwable) {
        null
    }

    private var observationJob: Job? = null

    // State tracking
    private var lastThreatLevel: ThreatLevel = ThreatLevel.NONE
    private var lastConnectionState: RadarConnectionState = RadarConnectionState.DISCONNECTED
    private var lastAlertTimestampMs: Long = 0L

    // Volume boost management
    private var savedStreamVolume: Int? = null
    private var isVolumeBoosted = false

    // Audio focus management
    private var activeFocusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { /* transient ducking listener */ }

    private val restoreRunnable = Runnable {
        synchronized(lock) {
            restoreSpeakerVolumeLocked()
            abandonAudioFocusLocked()
        }
    }

    /**
     * Starts observing [RadarStateRepository.radarState] telemetry stream.
     */
    fun start() {
        synchronized(lock) {
            if (isReleased || observationJob?.isActive == true) return
            observationJob = coroutineScope.launch {
                RadarStateRepository.radarState.collectLatest { state ->
                    onRadarStateUpdated(state)
                }
            }
        }
    }

    /**
     * Stops state observation without destroying synthesized audio tracks.
     */
    fun stop() {
        synchronized(lock) {
            observationJob?.cancel()
            observationJob = null
            restoreSpeakerVolumeLocked()
            abandonAudioFocusLocked()
        }
    }

    /**
     * Evaluates state transitions and fires debounced alerts.
     */
    fun onRadarStateUpdated(state: RadarState) {
        synchronized(lock) {
            if (isReleased) return

            val currentThreat = state.overallThreatLevel
            val currentConn = state.connectionState
            val now = timeProvider()

            // 1. Disconnect warning transition (CONNECTED -> DISCONNECTED_ERROR)
            if (lastConnectionState == RadarConnectionState.CONNECTED && currentConn == RadarConnectionState.DISCONNECTED_ERROR) {
                lastConnectionState = currentConn
                lastThreatLevel = currentThreat
                lastAlertTimestampMs = now
                triggerDisconnectAlert()
                return
            }
            lastConnectionState = currentConn

            if (currentThreat == lastThreatLevel) {
                return
            }

            // Hysteresis / debouncing: prevent alert flutter on rapid oscillations
            if (now - lastAlertTimestampMs < DEBOUNCE_COOLDOWN_MS) {
                lastThreatLevel = currentThreat
                return
            }

            // 2. Initial approach alert (NONE -> APPROACHING or FAST_APPROACHING)
            if (lastThreatLevel == ThreatLevel.NONE && currentThreat == ThreatLevel.APPROACHING) {
                lastAlertTimestampMs = now
                lastThreatLevel = currentThreat
                triggerApproachAlert()
                return
            }

            // 3. High-speed escalation alert (ANY -> FAST_APPROACHING)
            if (currentThreat == ThreatLevel.FAST_APPROACHING && lastThreatLevel != ThreatLevel.FAST_APPROACHING) {
                lastAlertTimestampMs = now
                lastThreatLevel = currentThreat
                triggerFastThreatAlert()
                return
            }

            // 4. All-clear transition (APPROACHING/FAST -> NONE)
            if ((lastThreatLevel == ThreatLevel.APPROACHING || lastThreatLevel == ThreatLevel.FAST_APPROACHING) && currentThreat == ThreatLevel.NONE) {
                lastAlertTimestampMs = now
                lastThreatLevel = currentThreat
                triggerAllClearAlert()
                return
            }

            lastThreatLevel = currentThreat
        }
    }

    private fun getCurrentConfig(): TuningPreferencesConfigSnapshot {
        val store = actualDataStore
        return if (store != null) {
            val cfg = store.getTuningConfig()
            TuningPreferencesConfigSnapshot(
                alertMode = cfg.radarAudioAlertMode,
                volumePercent = cfg.radarAlertVolume,
                speakerBoost = cfg.radarSpeakerBoost,
                route = cfg.radarAudioRoute,
                haptics = cfg.radarHapticAlert
            )
        } else {
            TuningPreferencesConfigSnapshot(
                alertMode = TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE,
                volumePercent = TuningPreferencesDefaults.RADAR_ALERT_VOLUME,
                speakerBoost = TuningPreferencesDefaults.RADAR_SPEAKER_BOOST,
                route = TuningPreferencesDefaults.RADAR_AUDIO_ROUTE,
                haptics = TuningPreferencesDefaults.RADAR_HAPTIC_ALERT
            )
        }
    }

    private fun triggerApproachAlert() {
        val config = getCurrentConfig()
        if (config.haptics) {
            vibrate(VibrationPattern.APPROACH)
        }
        if (config.alertMode == RadarAudioAlertMode.ALL_ALERTS || config.alertMode == RadarAudioAlertMode.APPROACH_AND_FAST) {
            playChime(config) { vol, route -> synthesizer.playApproach(vol, route) }
        }
    }

    private fun triggerFastThreatAlert() {
        val config = getCurrentConfig()
        if (config.haptics) {
            vibrate(VibrationPattern.FAST_THREAT)
        }
        if (config.alertMode != RadarAudioAlertMode.MUTED) {
            playChime(config) { vol, route -> synthesizer.playFastThreat(vol, route) }
        }
    }

    private fun triggerAllClearAlert() {
        val config = getCurrentConfig()
        if (config.haptics) {
            vibrate(VibrationPattern.ALL_CLEAR)
        }
        if (config.alertMode == RadarAudioAlertMode.ALL_ALERTS) {
            playChime(config) { vol, route -> synthesizer.playAllClear(vol, route) }
        }
    }

    private fun triggerDisconnectAlert() {
        val config = getCurrentConfig()
        if (config.haptics) {
            vibrate(VibrationPattern.DISCONNECT)
        }
        if (config.alertMode != RadarAudioAlertMode.MUTED) {
            playChime(config) { vol, route -> synthesizer.playDisconnect(vol, route) }
        }
    }

    private fun playChime(config: TuningPreferencesConfigSnapshot, chimeAction: (Float, RadarAudioRoute) -> Unit) {
        try {
            requestTransientDuckingLocked()
            if (config.speakerBoost && config.route == RadarAudioRoute.FORCE_SPEAKER) {
                boostSpeakerVolumeLocked(config.volumePercent)
            }
            handler?.removeCallbacks(restoreRunnable)
            handler?.postDelayed(restoreRunnable, CHIME_DURATION_MS)

            val normalizedVolume = (config.volumePercent / 100.0f).coerceIn(0.1f, 1.0f)
            chimeAction(normalizedVolume, config.route)
        } catch (t: Throwable) {
            Log.w(TAG, "Error playing radar alert chime", t)
        }
    }

    private fun requestTransientDuckingLocked() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (activeFocusRequest == null) {
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(synthesizer.audioAttributes)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build()
                    val result = am.requestAudioFocus(request)
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        activeFocusRequest = request
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to request transient audio focus", t)
        }
    }

    private fun abandonAudioFocusLocked() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { request ->
                    am.abandonAudioFocusRequest(request)
                    activeFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to abandon audio focus", t)
        }
    }

    private fun boostSpeakerVolumeLocked(targetPercent: Int) {
        val am = audioManager ?: return
        try {
            if (savedStreamVolume == null && !isVolumeBoosted) {
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (maxVol * (targetPercent / 100.0f)).roundToInt().coerceIn(1, maxVol)
                if (currentVol < targetVol) {
                    savedStreamVolume = currentVol
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                }
                isVolumeBoosted = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to boost STREAM_MUSIC volume", t)
        }
    }

    private fun restoreSpeakerVolumeLocked() {
        val original = savedStreamVolume
        try {
            if (original != null) {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore STREAM_MUSIC volume", t)
        } finally {
            savedStreamVolume = null
            isVolumeBoosted = false
        }
    }

    private fun vibrate(pattern: VibrationPattern) {
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (pattern) {
                    VibrationPattern.APPROACH -> VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE)
                    VibrationPattern.FAST_THREAT -> VibrationEffect.createWaveform(
                        longArrayOf(0L, 80L, 50L, 80L, 50L, 100L),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                    VibrationPattern.ALL_CLEAR -> VibrationEffect.createOneShot(60L, 100)
                    VibrationPattern.DISCONNECT -> VibrationEffect.createOneShot(350L, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (pattern) {
                    VibrationPattern.APPROACH -> vib.vibrate(120L)
                    VibrationPattern.FAST_THREAT -> vib.vibrate(longArrayOf(0L, 80L, 50L, 80L, 50L, 100L), -1)
                    VibrationPattern.ALL_CLEAR -> vib.vibrate(60L)
                    VibrationPattern.DISCONNECT -> vib.vibrate(350L)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Vibration failed", t)
        }
    }

    override fun close() {
        release()
    }

    fun release() {
        synchronized(lock) {
            if (isReleased) return
            isReleased = true
            stop()
            synthesizer.close()
            coroutineScope.cancel()
        }
    }

    private enum class VibrationPattern {
        APPROACH,
        FAST_THREAT,
        ALL_CLEAR,
        DISCONNECT
    }

    private data class TuningPreferencesConfigSnapshot(
        val alertMode: RadarAudioAlertMode,
        val volumePercent: Int,
        val speakerBoost: Boolean,
        val route: RadarAudioRoute,
        val haptics: Boolean
    )
}
