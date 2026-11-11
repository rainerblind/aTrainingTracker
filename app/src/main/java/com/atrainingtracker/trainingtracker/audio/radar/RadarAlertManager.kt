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
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeMode
import com.atrainingtracker.banalservice.devices.radar.RadarAudioSnoozeState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarLightController
import com.atrainingtracker.banalservice.devices.radar.RadarLightMode
import com.atrainingtracker.banalservice.devices.radar.RadarSnoozeController
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarStateRepository
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.trainingtracker.settings.RadarAudioAlertMode
import com.atrainingtracker.trainingtracker.settings.RadarAudioRoute
import com.atrainingtracker.trainingtracker.settings.RadarTrafficOrientation
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Audio preview simulation scenarios for settings testing (REQ-TRK-065.5).
 */
enum class RadarAudioPreviewType {
    CENTERED,
    PASSING,
    SLOW,
    FAST,
    LANE_CHANGE
}

/**
 * State-transition alert manager coordinating radar acoustic chimes and haptic feedback (REQ-TRK-063.1, REQ-TRK-064).
 *
 * Observes [RadarStateRepository.radarState] flow in [com.atrainingtracker.trainingtracker.tracker.TrackerService]
 * to deliver reliable alerts during active tracking including screen-off and background execution.
 *
 * Coordinates 1-tap audio snooze, peloton light mode audio sync, and smart urban congestion suppression (REQ-TRK-064).
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
        const val URBAN_CONGESTION_DURATION_MS: Long = 30_000L
        const val URBAN_CLEAR_DURATION_MS: Long = 20_000L
        const val URBAN_SPEED_THRESHOLD_MPS: Float = 5.0f
        const val LANE_CHANGE_COOLDOWN_MS: Long = 3000L

        @Volatile
        @JvmStatic
        var activeInstance: RadarAlertManager? = null
            internal set

        @Volatile
        private var previewSynthesizer: RadarAudioSynthesizer? = null

        @JvmStatic
        fun releasePreviewSynthesizer() {
            synchronized(RadarAlertManager::class.java) {
                try {
                    previewSynthesizer?.close()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to release preview synthesizer", t)
                } finally {
                    previewSynthesizer = null
                }
            }
        }

        @JvmStatic
        fun toggleRadarAudioSnooze() {
            RadarSnoozeController.toggleRadarAudioSnooze()
        }

        fun snooze(mode: RadarAudioSnoozeMode) {
            RadarSnoozeController.snooze(mode)
        }

        fun resumeAudio() {
            RadarSnoozeController.resumeAudio()
        }

        /**
         * Standalone preview playback for Radar Settings UI (REQ-TRK-065.5).
         */
        @JvmStatic
        fun playAudioPreview(context: Context, type: RadarAudioPreviewType, config: TuningConfig) {
            val instance = activeInstance
            if (instance != null) {
                instance.playPreview(type)
                return
            }
            try {
                val synth = previewSynthesizer ?: synchronized(RadarAlertManager::class.java) {
                    previewSynthesizer ?: RadarAudioSynthesizer(context).also { previewSynthesizer = it }
                }
                val vol = (config.radarAlertVolume / 100f).coerceIn(0.1f, 1.0f)
                val route = config.radarAudioRoute
                when (type) {
                    RadarAudioPreviewType.CENTERED -> synth.playApproach(vol, route)
                    RadarAudioPreviewType.PASSING -> {
                        val offset = if (config.radarTrafficOrientation == RadarTrafficOrientation.RIGHT_HAND) -2.0f else 2.0f
                        val hasHeadphones = synth.isHeadphonesOrBluetoothConnected()
                        val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                            offset, config.radarTrafficOrientation, true, route, hasHeadphones
                        )
                        synth.playApproach(vol, route, leftGain, rightGain, 1.0f)
                    }
                    RadarAudioPreviewType.SLOW -> {
                        val pitch = RadarSpatialAudioMath.computeSpeedPitch(3.0f, true)
                        synth.playApproach(vol, route, 1.0f, 1.0f, pitch)
                    }
                    RadarAudioPreviewType.FAST -> {
                        val pitch = RadarSpatialAudioMath.computeSpeedPitch(28.0f, true)
                        synth.playApproach(vol, route, 1.0f, 1.0f, pitch)
                    }
                    RadarAudioPreviewType.LANE_CHANGE -> {
                        val offset = if (config.radarTrafficOrientation == RadarTrafficOrientation.RIGHT_HAND) -2.0f else 2.0f
                        val hasHeadphones = synth.isHeadphonesOrBluetoothConnected()
                        val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                            offset, config.radarTrafficOrientation, true, route, hasHeadphones
                        )
                        synth.playLaneChangeTick(vol * 0.7f, route, leftGain, rightGain)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Audio preview playback failed", t)
            }
        }
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

    // Urban congestion tracking state (REQ-TRK-064.5)
    private var isUrbanSuppressionActive: Boolean = false
    private var urbanCongestionStartTimeMs: Long? = null
    private var urbanClearStartTimeMs: Long? = null

    // Spatial target tracking state (REQ-TRK-065.3)
    data class LaneTrackState(
        var previousOffsetMeters: Float?,
        var lastTickTimestampMs: Long = 0L,
        var hasTriggeredTick: Boolean = false
    )
    private val laneTrackStates = mutableMapOf<Int, LaneTrackState>()

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
            laneTrackStates.clear()
        }
    }

    /**
     * Starts observing [RadarStateRepository.radarState] telemetry stream.
     */
    fun start() {
        synchronized(lock) {
            if (isReleased || observationJob?.isActive == true) return
            activeInstance = this
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
            if (activeInstance === this) activeInstance = null
            observationJob?.cancel()
            observationJob = null
            restoreSpeakerVolumeLocked()
            abandonAudioFocusLocked()
            laneTrackStates.clear()
        }
    }

    /**
     * Activates 1-tap temporary audio snooze (REQ-TRK-064.1).
     */
    fun snooze(mode: RadarAudioSnoozeMode) {
        RadarSnoozeController.snooze(mode)
    }

    /**
     * Resumes normal audio alerts, clearing any active snooze (REQ-TRK-064.3).
     */
    fun resumeAudio() {
        RadarSnoozeController.resumeAudio()
    }

    /**
     * Handlebar / remote shifter button toggle: alternates between 15m snooze and resume (REQ-TRK-064.3).
     */
    fun toggleSnooze() {
        RadarSnoozeController.toggleRadarAudioSnooze()
    }

    /**
     * Executes interactive audio preview for settings configuration (REQ-TRK-065.5).
     */
    fun playPreview(type: RadarAudioPreviewType) {
        synchronized(lock) {
            if (isReleased) return
            val config = getCurrentConfig()
            when (type) {
                RadarAudioPreviewType.CENTERED -> {
                    playChime(config) { vol, route -> synthesizer.playApproach(vol, route) }
                }
                RadarAudioPreviewType.PASSING -> {
                    val offset = if (config.trafficOrientation == RadarTrafficOrientation.RIGHT_HAND) -2.0f else 2.0f
                    val hasHeadphones = synthesizer.isHeadphonesOrBluetoothConnected()
                    val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                        offset, config.trafficOrientation, true, config.route, hasHeadphones
                    )
                    playChime(config) { vol, route ->
                        synthesizer.playApproach(vol, route, leftGain, rightGain, 1.0f)
                    }
                }
                RadarAudioPreviewType.SLOW -> {
                    val pitch = RadarSpatialAudioMath.computeSpeedPitch(3.0f, true)
                    playChime(config) { vol, route ->
                        synthesizer.playApproach(vol, route, 1.0f, 1.0f, pitch)
                    }
                }
                RadarAudioPreviewType.FAST -> {
                    val pitch = RadarSpatialAudioMath.computeSpeedPitch(28.0f, true)
                    playChime(config) { vol, route ->
                        synthesizer.playApproach(vol, route, 1.0f, 1.0f, pitch)
                    }
                }
                RadarAudioPreviewType.LANE_CHANGE -> {
                    val offset = if (config.trafficOrientation == RadarTrafficOrientation.RIGHT_HAND) -2.0f else 2.0f
                    val hasHeadphones = synthesizer.isHeadphonesOrBluetoothConnected()
                    val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                        offset, config.trafficOrientation, true, config.route, hasHeadphones
                    )
                    playChime(config) { vol, route ->
                        synthesizer.playLaneChangeTick(vol * 0.7f, route, leftGain, rightGain)
                    }
                }
            }
        }
    }

    /**
     * Evaluates state transitions and fires debounced alerts.
     */
    fun onRadarStateUpdated(state: RadarState) {
        synchronized(lock) {
            if (isReleased) return

            try {
                val currentThreat = state.overallThreatLevel
                val currentConn = state.connectionState
                val now = timeProvider()
                val config = getCurrentConfig()

                // 0a. Update Peloton Light Mode Audio Sync (REQ-TRK-064.4)
                val isPelotonLight = RadarLightController.currentMode.value == RadarLightMode.PELOTON || state.lightMode == RadarLightMode.PELOTON
                val pelotonSuppressed = config.pelotonAudioSync && isPelotonLight
                RadarSnoozeController.setPelotonSyncActive(pelotonSuppressed)

                // 0b. Update Smart Urban Congestion Filter (REQ-TRK-064.5)
            if (config.smartUrbanMute) {
                val slowTargetCount = state.targets.count { it.approachSpeedMps < URBAN_SPEED_THRESHOLD_MPS }
                val totalTargetCount = state.targets.size
                if (slowTargetCount >= 3) {
                    urbanClearStartTimeMs = null
                    if (urbanCongestionStartTimeMs == null) {
                        urbanCongestionStartTimeMs = now
                    } else if (now - urbanCongestionStartTimeMs!! >= URBAN_CONGESTION_DURATION_MS && !isUrbanSuppressionActive) {
                        isUrbanSuppressionActive = true
                        RadarSnoozeController.setUrbanMuteActive(true)
                    }
                } else if (totalTargetCount >= 3 && slowTargetCount >= 2 && urbanCongestionStartTimeMs != null) {
                    urbanClearStartTimeMs = null
                    if (now - urbanCongestionStartTimeMs!! >= URBAN_CONGESTION_DURATION_MS && !isUrbanSuppressionActive) {
                        isUrbanSuppressionActive = true
                        RadarSnoozeController.setUrbanMuteActive(true)
                    }
                } else {
                    urbanCongestionStartTimeMs = null
                    if (isUrbanSuppressionActive) {
                        if (urbanClearStartTimeMs == null) {
                            urbanClearStartTimeMs = now
                        } else if (now - urbanClearStartTimeMs!! >= URBAN_CLEAR_DURATION_MS) {
                            isUrbanSuppressionActive = false
                            urbanClearStartTimeMs = null
                            RadarSnoozeController.setUrbanMuteActive(false)
                        }
                    }
                }
            } else if (isUrbanSuppressionActive) {
                isUrbanSuppressionActive = false
                urbanCongestionStartTimeMs = null
                urbanClearStartTimeMs = null
                RadarSnoozeController.setUrbanMuteActive(false)
            }

            // Find primary threatening target (closest target with approach speed > 0 or highest threat)
            val primaryTarget = state.targets
                .filter { it.threatLevel != ThreatLevel.NONE }
                .minByOrNull { it.rangeMeters }
                ?: state.targets.minByOrNull { it.rangeMeters }

            // Check lane-change transition for spatial tick cue (REQ-TRK-065.3)
            val activeTargetIds = state.targets.map { it.targetId }.toSet()
            laneTrackStates.keys.retainAll(activeTargetIds)

            if (config.laneChangeTickEnabled && !isRoutineAlertSuppressed() && config.alertMode != RadarAudioAlertMode.MUTED) {
                for (target in state.targets) {
                    val offset = target.lateralOffsetMeters ?: continue
                    val trackState = laneTrackStates.getOrPut(target.targetId) {
                        LaneTrackState(previousOffsetMeters = offset)
                    }
                    if (!trackState.hasTriggeredTick && (now - trackState.lastTickTimestampMs >= LANE_CHANGE_COOLDOWN_MS)) {
                        if (RadarSpatialAudioMath.isLaneChangeToSafePassing(trackState.previousOffsetMeters, offset, config.trafficOrientation)) {
                            trackState.hasTriggeredTick = true
                            trackState.lastTickTimestampMs = now
                            triggerLaneChangeTick(offset, config)
                        }
                    } else if (kotlin.math.abs(offset) < RadarSpatialAudioMath.IN_LANE_THRESHOLD_METERS) {
                        trackState.hasTriggeredTick = false
                    }
                    trackState.previousOffsetMeters = offset
                }
            } else {
                for (target in state.targets) {
                    target.lateralOffsetMeters?.let { offset ->
                        laneTrackStates.getOrPut(target.targetId) { LaneTrackState(previousOffsetMeters = offset) }
                            .previousOffsetMeters = offset
                    }
                }
            }

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
                triggerApproachAlert(primaryTarget, config)
                return
            }

            // 3. High-speed escalation alert (ANY -> FAST_APPROACHING)
            if (currentThreat == ThreatLevel.FAST_APPROACHING && lastThreatLevel != ThreatLevel.FAST_APPROACHING) {
                lastAlertTimestampMs = now
                lastThreatLevel = currentThreat
                val fastTarget = state.targets
                    .filter { it.threatLevel == ThreatLevel.FAST_APPROACHING }
                    .maxByOrNull { it.approachSpeedMps }
                    ?: primaryTarget
                triggerFastThreatAlert(fastTarget, config)
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
            } finally {
                RadarSnoozeController.onRadarStateUpdated(state)
            }
        }
    }

    private fun isRoutineAlertSuppressed(): Boolean {
        return RadarSnoozeController.isRoutineAlertSuppressed()
    }

    private fun isFastThreatAlertSuppressed(): Boolean {
        return RadarSnoozeController.isFastThreatAlertSuppressed()
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
                haptics = cfg.radarHapticAlert,
                pelotonAudioSync = cfg.radarPelotonAudioSync,
                smartUrbanMute = cfg.radarSmartUrbanMute,
                spatialAudioEnabled = cfg.radarSpatialAudioEnabled,
                speedPitchModulation = cfg.radarSpeedPitchModulation,
                trafficOrientation = cfg.radarTrafficOrientation,
                laneChangeTickEnabled = cfg.radarLaneChangeTickEnabled
            )
        } else {
            TuningPreferencesConfigSnapshot(
                alertMode = TuningPreferencesDefaults.RADAR_AUDIO_ALERT_MODE,
                volumePercent = TuningPreferencesDefaults.RADAR_ALERT_VOLUME,
                speakerBoost = TuningPreferencesDefaults.RADAR_SPEAKER_BOOST,
                route = TuningPreferencesDefaults.RADAR_AUDIO_ROUTE,
                haptics = TuningPreferencesDefaults.RADAR_HAPTIC_ALERT,
                pelotonAudioSync = TuningPreferencesDefaults.RADAR_PELOTON_AUDIO_SYNC,
                smartUrbanMute = TuningPreferencesDefaults.RADAR_SMART_URBAN_MUTE,
                spatialAudioEnabled = TuningPreferencesDefaults.RADAR_SPATIAL_AUDIO_ENABLED,
                speedPitchModulation = TuningPreferencesDefaults.RADAR_SPEED_PITCH_MODULATION,
                trafficOrientation = TuningPreferencesDefaults.RADAR_TRAFFIC_ORIENTATION,
                laneChangeTickEnabled = TuningPreferencesDefaults.RADAR_LANE_CHANGE_TICK_ENABLED
            )
        }
    }

    private fun triggerApproachAlert(primaryTarget: RadarTarget?, config: TuningPreferencesConfigSnapshot) {
        if (config.haptics) {
            vibrate(VibrationPattern.APPROACH)
        }
        if (isRoutineAlertSuppressed()) return
        if (config.alertMode == RadarAudioAlertMode.ALL_ALERTS || config.alertMode == RadarAudioAlertMode.APPROACH_AND_FAST) {
            val hasHeadphones = synthesizer.isHeadphonesOrBluetoothConnected()
            val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                primaryTarget?.lateralOffsetMeters,
                config.trafficOrientation,
                config.spatialAudioEnabled,
                config.route,
                hasHeadphones
            )
            val pitch = RadarSpatialAudioMath.computeSpeedPitch(
                primaryTarget?.approachSpeedMps,
                config.speedPitchModulation
            )
            playChime(config) { vol, route ->
                if (leftGain == 1.0f && rightGain == 1.0f && pitch == 1.0f) {
                    synthesizer.playApproach(vol, route)
                } else {
                    synthesizer.playApproach(vol, route, leftGain, rightGain, pitch)
                }
            }
        }
    }

    private fun triggerFastThreatAlert(fastTarget: RadarTarget?, config: TuningPreferencesConfigSnapshot) {
        if (config.haptics) {
            vibrate(VibrationPattern.FAST_THREAT)
        }
        if (isFastThreatAlertSuppressed()) return
        if (config.alertMode != RadarAudioAlertMode.MUTED) {
            val hasHeadphones = synthesizer.isHeadphonesOrBluetoothConnected()
            val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
                fastTarget?.lateralOffsetMeters,
                config.trafficOrientation,
                config.spatialAudioEnabled,
                config.route,
                hasHeadphones
            )
            val pitch = RadarSpatialAudioMath.computeSpeedPitch(
                fastTarget?.approachSpeedMps,
                config.speedPitchModulation
            )
            playChime(config) { vol, route ->
                if (leftGain == 1.0f && rightGain == 1.0f && pitch == 1.0f) {
                    synthesizer.playFastThreat(vol, route)
                } else {
                    synthesizer.playFastThreat(vol, route, leftGain, rightGain, pitch)
                }
            }
        }
    }

    private fun triggerLaneChangeTick(lateralOffsetMeters: Float?, config: TuningPreferencesConfigSnapshot) {
        val hasHeadphones = synthesizer.isHeadphonesOrBluetoothConnected()
        val (leftGain, rightGain) = RadarSpatialAudioMath.computeStereoGains(
            lateralOffsetMeters,
            config.trafficOrientation,
            config.spatialAudioEnabled,
            config.route,
            hasHeadphones
        )
        playChime(config) { vol, route ->
            synthesizer.playLaneChangeTick(vol * 0.7f, route, leftGain, rightGain)
        }
    }

    private fun triggerAllClearAlert() {
        val config = getCurrentConfig()
        if (config.haptics) {
            vibrate(VibrationPattern.ALL_CLEAR)
        }
        if (isRoutineAlertSuppressed()) return
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
            if (activeInstance === this) activeInstance = null
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
        val haptics: Boolean,
        val pelotonAudioSync: Boolean,
        val smartUrbanMute: Boolean,
        val spatialAudioEnabled: Boolean,
        val speedPitchModulation: Boolean,
        val trafficOrientation: RadarTrafficOrientation,
        val laneChangeTickEnabled: Boolean
    )
}
