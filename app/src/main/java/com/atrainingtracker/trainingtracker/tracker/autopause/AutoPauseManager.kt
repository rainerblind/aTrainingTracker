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
    val speedThresholdKmh: Float? = null,
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
}

/**
 * Default Android ToneGenerator audio notifier.
 */
class DefaultAutoPauseAudioNotifier : AutoPauseAudioNotifier {
    override fun playTone(soundCue: SoundCue) {
        try {
            when (soundCue) {
                SoundCue.AUTO_PAUSE -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                }
                SoundCue.AUTO_RESUME -> {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                }
                SoundCue.NONE -> {}
            }
        } catch (t: Throwable) {
            Log.w("AutoPauseManager", "Audio tone error: ${t.message}")
        }
    }
}

/**
 * Domain engine managing sport-specific auto-pause transitions, anti-chatter hysteresis,
 * debounce filtering, stale packet TTL guards, and mutex precedence (REQ-TRK-018 / ATT-45).
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

    fun resetDebounceCounter() {
        debounceCounter = 0
    }

    fun setState(state: AutoPauseState) {
        currentState = state
        if (state == AutoPauseState.TRACKING || state == AutoPauseState.AUTO_PAUSED || state == AutoPauseState.MANUAL_PAUSED) {
            debounceCounter = 0
            sensorLostRecoveryTicks = 0
        }
    }

    fun isAutoPaused(): Boolean = currentState == AutoPauseState.AUTO_PAUSED

    /**
     * Evaluates a 1Hz telemetry tick.
     *
     * @param currentSpeedMps Raw horizontal speed in m/s (or null if absent)
     * @param packetTimestampMs Timestamp of the speed packet (in System.currentTimeMillis)
     * @param currentTimestampMs Current wall-clock time in ms
     * @param sportType Active sport profile
     * @param config Active auto-pause settings
     * @param manualPauseLocked Whether user has explicitly manually paused
     * @return AutoPauseTransition containing resulting state and required actions
     */
    fun evaluateTick(
        currentSpeedMps: Double?,
        packetTimestampMs: Long,
        currentTimestampMs: Long,
        sportType: BSportType,
        config: AutoPauseConfig,
        manualPauseLocked: Boolean
    ): AutoPauseTransition {
        val prevState = currentState

        // 1. Manual Pause Mutex Precedence (REQ-TRK-018 section 5 & Chesterton's Fence)
        if (manualPauseLocked) {
            currentState = AutoPauseState.MANUAL_PAUSED
            debounceCounter = 0
            sensorLostRecoveryTicks = 0
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
        val hasFiniteSpeed = isSpeedFiniteAndValid(currentSpeedMps)

        // 4. Stale Packet Drop (TTL = 5000ms) & SENSOR_LOST Detection
        val isPacketStale = if (packetTimestampMs <= 0L) {
            true
        } else {
            (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS
        }

        if (!hasFiniteSpeed || isPacketStale) {
            // Packet missing or stale
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

        // Valid and fresh packet received
        lastValidSpeedPacketTimestampMs = packetTimestampMs
        val speedMps = currentSpeedMps!!

        // 5. Sensor Lost Recovery Contract: 2 consecutive seconds of fresh speed
        if (currentState == AutoPauseState.SENSOR_LOST) {
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
                // Must reach resume threshold (including hysteresis) to resume
                if (speedMps >= resumeThresholdMps) {
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
                    // Still stationary or within deadband (chatter suppression)
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
