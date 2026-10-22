/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.tracker.autopause

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults

/**
 * State enum for the Auto-Pause Engine (REQ-TRK-018 / ATT-45).
 */
enum class AutoPauseState {
    TRACKING,
    PAUSE_DEBOUNCE_PENDING,
    AUTO_PAUSED,
    MANUAL_PAUSED,
    SENSOR_LOST
}

/**
 * Config value object representing active auto-pause settings.
 */
data class AutoPauseConfig(
    val bikeEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_ENABLED,
    val runEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_ENABLED,
    val otherEnabled: Boolean = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_ENABLED,
    val bikeSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_SPEED_THRESHOLD_METRIC,
    val bikeDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_BIKE_DEBOUNCE_SEC,
    val runSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_SPEED_THRESHOLD_METRIC,
    val runDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_RUN_DEBOUNCE_SEC,
    val otherSpeedThreshold: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_SPEED_THRESHOLD_METRIC,
    val otherDebounceSec: Int = TuningPreferencesDefaults.DEFAULT_AUTO_PAUSE_OTHER_DEBOUNCE_SEC,
    val isMetric: Boolean = true,
    // Deprecated backward-compatibility bridges
    @Deprecated(message = "Use sport-specific speed thresholds", replaceWith = ReplaceWith("bikeSpeedThreshold"))
    val speedThresholdKmh: Float? = null,
    @Deprecated(message = "Use sport-specific debounces", replaceWith = ReplaceWith("bikeDebounceSec"))
    val debounceSec: Int? = null
) {
    fun isEnabledForSport(sportType: BSportType): Boolean {
        return when (sportType) {
            BSportType.BIKE -> bikeEnabled
            BSportType.RUN -> runEnabled
            else -> otherEnabled
        }
    }

    fun getSpeedThresholdForSport(sportType: BSportType): Double {
        if (speedThresholdKmh != null) return speedThresholdKmh.toDouble()
        return when (sportType) {
            BSportType.BIKE -> bikeSpeedThreshold.toDouble()
            BSportType.RUN -> runSpeedThreshold.toDouble()
            else -> otherSpeedThreshold.toDouble()
        }
    }

    fun getDebounceSecForSport(sportType: BSportType): Int {
        if (debounceSec != null) return debounceSec
        return when (sportType) {
            BSportType.BIKE -> bikeDebounceSec
            BSportType.RUN -> runDebounceSec
            else -> otherDebounceSec
        }
    }
}

/**
 * Sound cues emitted by Auto-Pause events.
 */
enum class SoundCue {
    NONE,
    AUTO_PAUSE,
    AUTO_RESUME
}

/**
 * Result of evaluating a 1Hz telemetry tick in the AutoPauseManager.
 */
data class AutoPauseTransition(
    val previousState: AutoPauseState,
    val newState: AutoPauseState,
    val triggerPause: Boolean = false,
    val triggerResume: Boolean = false,
    val soundCue: SoundCue = SoundCue.NONE,
    val auditLogEvent: String? = null
)

/**
 * Audio notifier interface for tone playback, injectable for isolated unit tests.
 */
interface AutoPauseAudioNotifier {
    fun playTone(soundCue: SoundCue)
    fun release() {}
}

/**
 * Default Android ToneGenerator audio notifier.
 * Lazily reuses a single [ToneGenerator] instance across tones and releases it idempotently (REQ-TRK-028.4 / Rule 36).
 */
class DefaultAutoPauseAudioNotifier(
    private val toneGeneratorProvider: (() -> ToneGenerator?)? = null
) : AutoPauseAudioNotifier {
    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    private fun getOrCreateToneGenerator(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = toneGeneratorProvider?.invoke()
                ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        }
        return toneGenerator
    }

    @Synchronized
    override fun playTone(soundCue: SoundCue) {
        try {
            when (soundCue) {
                SoundCue.AUTO_PAUSE -> {
                    getOrCreateToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                }
                SoundCue.AUTO_RESUME -> {
                    getOrCreateToneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                }
                SoundCue.NONE -> {}
            }
        } catch (t: Throwable) {
            Log.w("AutoPauseManager", "Audio tone error: ${t.message}")
        }
    }

    /**
     * Releases the underlying native [ToneGenerator] resource idempotently.
     */
    @Synchronized
    override fun release() {
        try {
            toneGenerator?.release()
        } catch (t: Throwable) {
            Log.w("AutoPauseManager", "ToneGenerator release error: ${t.message}")
        } finally {
            toneGenerator = null
        }
    }
}

/**
 * Domain engine managing sport-specific auto-pause transitions, anti-chatter hysteresis,
 * debounce filtering, registered-source standstill vs. sensor-lost detection, and mutex precedence
 * (REQ-TRK-018 / REQ-TRK-028 / ATT-45 / ATT-3984).
 */
class AutoPauseManager(
    private val audioNotifier: AutoPauseAudioNotifier = DefaultAutoPauseAudioNotifier()
) {
    companion object {
        const val STALE_PACKET_TIMEOUT_MS = 5000L
        const val HYSTERESIS_OFFSET_USER_UNIT = 0.5
        const val HYSTERESIS_OFFSET_KMH = 0.5f
        const val RESUME_HARD_CEILING_KMH = 6.0f
        const val RESUME_HARD_CEILING_MPH = 6.0f
        const val SENSOR_LOST_RECOVERY_TICKS = 2

        @JvmStatic
        fun calculateResumeSpeedThresholdKmh(pauseThresholdKmh: Float): Float {
            return (pauseThresholdKmh + HYSTERESIS_OFFSET_KMH).coerceAtMost(RESUME_HARD_CEILING_KMH)
        }

        /**
         * Calculates the resume speed threshold in active user units with anti-chatter hysteresis deadband (+0.5).
         * Parameterized physical ceiling (6.0 km/h Metric, 6.0 mph Imperial) prevents boundary inversion
         * where resume speed could otherwise fall below pause speed under Imperial units.
         */
        @JvmStatic
        fun calculateResumeSpeedThresholdUserUnit(pauseThreshold: Double, isMetric: Boolean): Double {
            val ceiling = if (isMetric) RESUME_HARD_CEILING_KMH.toDouble() else RESUME_HARD_CEILING_MPH.toDouble()
            return (pauseThreshold + HYSTERESIS_OFFSET_USER_UNIT).coerceAtMost(ceiling)
        }

        @JvmStatic
        fun isSpeedFiniteAndValid(speedMps: Double?): Boolean {
            return speedMps != null && !speedMps.isNaN() && !speedMps.isInfinite() && speedMps >= 0.0
        }
    }

    var currentState: AutoPauseState = AutoPauseState.TRACKING
        private set

    var debounceCounter: Int = 0
        private set

    var sensorLostRecoveryTicks: Int = 0
        private set

    var lastValidSpeedPacketTimestampMs: Long = 0L
        private set

    var lastValidSpeedMps: Double? = null
        private set

    @Synchronized
    fun resetDebounceCounter() {
        debounceCounter = 0
    }

    @Synchronized
    fun reset() {
        currentState = AutoPauseState.TRACKING
        debounceCounter = 0
        sensorLostRecoveryTicks = 0
        lastValidSpeedPacketTimestampMs = 0L
        lastValidSpeedMps = null
    }

    @Synchronized
    fun setState(state: AutoPauseState) {
        currentState = state
        if (state == AutoPauseState.TRACKING || state == AutoPauseState.AUTO_PAUSED || state == AutoPauseState.MANUAL_PAUSED) {
            debounceCounter = 0
            sensorLostRecoveryTicks = 0
        }
        if (state == AutoPauseState.TRACKING || state == AutoPauseState.MANUAL_PAUSED) {
            lastValidSpeedMps = null
        }
    }

    @Synchronized
    fun isAutoPaused(): Boolean = currentState == AutoPauseState.AUTO_PAUSED

    /**
     * Synchronizes engine state when the user explicitly resumes from an auto-paused session
     * via the UI or notification while [manualPauseLocked] is false (REQ-TRK-028.3 / REQ-TRK-037.4).
     * If manual pause lock is active, this method is a safe no-op.
     */
    @JvmOverloads
    @Synchronized
    fun onManualResume(manualPauseLocked: Boolean = false) {
        if (manualPauseLocked || currentState == AutoPauseState.MANUAL_PAUSED) {
            return
        }
        debounceCounter = 0
        sensorLostRecoveryTicks = 0
        if (currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
            currentState = AutoPauseState.TRACKING
        }
    }

    /**
     * Releases native audio resources held by [audioNotifier] (REQ-TRK-028.4).
     */
    fun release() {
        audioNotifier.release()
    }

    /**
     * Evaluates a 1Hz telemetry tick.
     *
     * @param currentSpeedMps Raw horizontal speed in m/s (or null if absent)
     * @param packetTimestampMs Timestamp of the speed packet (in System.currentTimeMillis)
     * @param currentTimestampMs Current wall-clock time in ms
     * @param sportType Active sport profile
     * @param config Active auto-pause settings
     * @param manualPauseLocked Whether user has explicitly manually paused
     * @param hasSpeedSource Whether a speed sensor/provider is currently registered in MySensorManager (REQ-TRK-028.2)
     * @return AutoPauseTransition containing resulting state and required actions
     */
    @JvmOverloads
    @Synchronized
    fun evaluateTick(
        currentSpeedMps: Double?,
        packetTimestampMs: Long,
        currentTimestampMs: Long,
        sportType: BSportType,
        config: AutoPauseConfig,
        manualPauseLocked: Boolean,
        hasSpeedSource: Boolean = (isSpeedFiniteAndValid(currentSpeedMps) || packetTimestampMs > 0L)
    ): AutoPauseTransition {
        val prevState = currentState

        // 1. Manual Pause Mutex Precedence (REQ-TRK-018 section 5 & Chesterton's Fence)
        if (manualPauseLocked) {
            currentState = AutoPauseState.MANUAL_PAUSED
            debounceCounter = 0
            sensorLostRecoveryTicks = 0
            lastValidSpeedMps = null
            return AutoPauseTransition(
                previousState = prevState,
                newState = AutoPauseState.MANUAL_PAUSED,
                triggerPause = false,
                triggerResume = false,
                soundCue = SoundCue.NONE,
                auditLogEvent = if (prevState != AutoPauseState.MANUAL_PAUSED) "TRANSITION_MANUAL_PAUSED" else null
            )
        } else if (currentState == AutoPauseState.MANUAL_PAUSED) {
            // Manual lock was cleared (e.g. user resumed manually)
            currentState = AutoPauseState.TRACKING
            debounceCounter = 0
            sensorLostRecoveryTicks = 0
            lastValidSpeedMps = null
        }

        // 2. Check if Auto-Pause is enabled for this sport profile
        if (!config.isEnabledForSport(sportType)) {
            // Auto-pause disabled for this sport: if we were auto-paused, resume to tracking
            if (currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
                currentState = AutoPauseState.TRACKING
                debounceCounter = 0
                return AutoPauseTransition(
                    previousState = prevState,
                    newState = AutoPauseState.TRACKING,
                    triggerResume = (prevState == AutoPauseState.AUTO_PAUSED),
                    soundCue = SoundCue.NONE
                )
            }
            return AutoPauseTransition(previousState = prevState, newState = currentState)
        }

        // 3. Numerical Sanitization: Reject NaN, Infinity, negative values
        val isSpeedCorrupt = currentSpeedMps != null && !isSpeedFiniteAndValid(currentSpeedMps)

        // 4. Stale Packet Check (TTL = 5000ms) & Registered Speed Source vs. SENSOR_LOST (REQ-TRK-028.2)
        val isPacketStale = if (packetTimestampMs <= 0L) {
            true
        } else {
            (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS
        }

        val hasFreshValidReading = !isSpeedCorrupt && !isPacketStale && currentSpeedMps != null

        if (isSpeedCorrupt || !hasSpeedSource) {
            // Speed source unregistered/disconnected or corrupt numeric reading -> SENSOR_LOST
            lastValidSpeedMps = null
            if (currentState == AutoPauseState.TRACKING || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
                currentState = AutoPauseState.SENSOR_LOST
                debounceCounter = 0
                sensorLostRecoveryTicks = 0
                return AutoPauseTransition(
                    previousState = prevState,
                    newState = AutoPauseState.SENSOR_LOST,
                    auditLogEvent = "SENSOR_LOST_STALE_TTL_EXCEEDED"
                )
            } else if (currentState == AutoPauseState.SENSOR_LOST) {
                sensorLostRecoveryTicks = 0
                return AutoPauseTransition(previousState = prevState, newState = AutoPauseState.SENSOR_LOST)
            }
            // If already AUTO_PAUSED, remain AUTO_PAUSED
            return AutoPauseTransition(previousState = prevState, newState = currentState)
        }

        if (hasFreshValidReading) {
            lastValidSpeedPacketTimestampMs = packetTimestampMs
            lastValidSpeedMps = currentSpeedMps
        }

        // 5. Sensor Lost Recovery Contract: 2 consecutive seconds of fresh valid speed
        if (currentState == AutoPauseState.SENSOR_LOST) {
            if (!hasFreshValidReading) {
                sensorLostRecoveryTicks = 0
                return AutoPauseTransition(previousState = prevState, newState = AutoPauseState.SENSOR_LOST)
            }
            sensorLostRecoveryTicks++
            if (sensorLostRecoveryTicks >= SENSOR_LOST_RECOVERY_TICKS) {
                currentState = AutoPauseState.TRACKING
                sensorLostRecoveryTicks = 0
                debounceCounter = 0
                return AutoPauseTransition(
                    previousState = prevState,
                    newState = AutoPauseState.TRACKING,
                    auditLogEvent = "SENSOR_LOST_RECOVERED"
                )
            }
            return AutoPauseTransition(previousState = prevState, newState = AutoPauseState.SENSOR_LOST)
        }

        val sportThreshold = config.getSpeedThresholdForSport(sportType)
        val sportDebounceSec = config.getDebounceSecForSport(sportType)
        val unit = if (config.isMetric) MyUnits.METRIC else MyUnits.IMPERIAL
        val pauseThresholdMps = MyHelper.UserUnit2mps(sportThreshold, unit)
        val resumeThresholdUserUnit = calculateResumeSpeedThresholdUserUnit(sportThreshold, config.isMetric)
        val resumeThresholdMps = MyHelper.UserUnit2mps(resumeThresholdUserUnit, unit)

        // 6. Stale Packet Classification (REQ-TRK-037.1 / ATT-4094):
        // If speed packets stopped arriving while cruising in motion (> resumeThresholdMps),
        // classify as SENSOR_LOST (tunnel/dropout) rather than falsely pausing.
        if (!hasFreshValidReading && lastValidSpeedMps != null && lastValidSpeedMps!! > resumeThresholdMps) {
            if (currentState == AutoPauseState.TRACKING || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
                currentState = AutoPauseState.SENSOR_LOST
                debounceCounter = 0
                sensorLostRecoveryTicks = 0
                return AutoPauseTransition(
                    previousState = prevState,
                    newState = AutoPauseState.SENSOR_LOST,
                    auditLogEvent = "SENSOR_LOST_IN_MOTION_DROPOUT"
                )
            } else if (currentState == AutoPauseState.SENSOR_LOST) {
                sensorLostRecoveryTicks = 0
                return AutoPauseTransition(previousState = prevState, newState = AutoPauseState.SENSOR_LOST)
            }
            return AutoPauseTransition(previousState = prevState, newState = currentState)
        }

        // For a registered speed source (hasSpeedSource == true): if the sensor stopped emitting
        // packets at standstill (lastValidSpeedMps <= resumeThresholdMps or null), treat
        // effective speed as 0.0 m/s for pause evaluation while requiring a fresh reading to auto-resume.
        val speedMps = if (hasFreshValidReading) currentSpeedMps else 0.0

        return when (currentState) {
            AutoPauseState.TRACKING -> {
                if (speedMps < pauseThresholdMps) {
                    debounceCounter = 1
                    if (debounceCounter >= sportDebounceSec) {
                        currentState = AutoPauseState.AUTO_PAUSED
                        debounceCounter = 0
                        audioNotifier.playTone(SoundCue.AUTO_PAUSE)
                        AutoPauseTransition(
                            previousState = prevState,
                            newState = AutoPauseState.AUTO_PAUSED,
                            triggerPause = true,
                            soundCue = SoundCue.AUTO_PAUSE,
                            auditLogEvent = "AUTO_PAUSED_SPEED_BELOW_THRESHOLD"
                        )
                    } else {
                        currentState = AutoPauseState.PAUSE_DEBOUNCE_PENDING
                        AutoPauseTransition(
                            previousState = prevState,
                            newState = AutoPauseState.PAUSE_DEBOUNCE_PENDING
                        )
                    }
                } else {
                    debounceCounter = 0
                    AutoPauseTransition(previousState = prevState, newState = AutoPauseState.TRACKING)
                }
            }

            AutoPauseState.PAUSE_DEBOUNCE_PENDING -> {
                if (speedMps < pauseThresholdMps) {
                    debounceCounter++
                    if (debounceCounter >= sportDebounceSec) {
                        currentState = AutoPauseState.AUTO_PAUSED
                        debounceCounter = 0
                        audioNotifier.playTone(SoundCue.AUTO_PAUSE)
                        AutoPauseTransition(
                            previousState = prevState,
                            newState = AutoPauseState.AUTO_PAUSED,
                            triggerPause = true,
                            soundCue = SoundCue.AUTO_PAUSE,
                            auditLogEvent = "AUTO_PAUSED_SPEED_BELOW_THRESHOLD"
                        )
                    } else {
                        AutoPauseTransition(
                            previousState = prevState,
                            newState = AutoPauseState.PAUSE_DEBOUNCE_PENDING
                        )
                    }
                } else {
                    // Speed increased above threshold before debounce expired -> cancel debounce
                    currentState = AutoPauseState.TRACKING
                    debounceCounter = 0
                    AutoPauseTransition(
                        previousState = prevState,
                        newState = AutoPauseState.TRACKING
                    )
                }
            }

            AutoPauseState.AUTO_PAUSED -> {
                // Must have a fresh reading reaching resume threshold (including hysteresis) to resume
                if (hasFreshValidReading && speedMps >= resumeThresholdMps) {
                    currentState = AutoPauseState.TRACKING
                    debounceCounter = 0
                    audioNotifier.playTone(SoundCue.AUTO_RESUME)
                    AutoPauseTransition(
                        previousState = prevState,
                        newState = AutoPauseState.TRACKING,
                        triggerResume = true,
                        soundCue = SoundCue.AUTO_RESUME,
                        auditLogEvent = "AUTO_RESUMED_SPEED_ABOVE_THRESHOLD"
                    )
                } else {
                    // Still stationary, stale, or within deadband (chatter suppression)
                    AutoPauseTransition(
                        previousState = prevState,
                        newState = AutoPauseState.AUTO_PAUSED
                    )
                }
            }

            AutoPauseState.MANUAL_PAUSED,
            AutoPauseState.SENSOR_LOST -> {
                AutoPauseTransition(previousState = prevState, newState = currentState)
            }
        }
    }
}
