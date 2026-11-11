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

package com.atrainingtracker.banalservice.devices.radar

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lifecycle-safe central controller for cycling rearview radar audio snooze and suppression (REQ-TRK-064.1).
 *
 * Coordinates manual 1-tap snooze durations (15m, 30m, FAST_ONLY, REST_OF_RIDE), Peloton light mode audio sync,
 * Smart Urban congestion filtering, and manual resume auto-mute overrides. Operates independently of
 * [com.atrainingtracker.trainingtracker.tracker.TrackerService] recording lifecycle so snooze works
 * seamlessly across READY, PAUSED, and TRACKING modes, survives transient BLE drops, and clears cleanly on workout stop.
 */
object RadarSnoozeController {

    private val lock = Any()

    @Volatile
    private var coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var timeProvider: () -> Long = { System.currentTimeMillis() }

    private var currentSnoozeMode: RadarAudioSnoozeMode = RadarAudioSnoozeMode.NONE
    private var snoozeEndTimeMs: Long? = null
    private var snoozeTickerJob: Job? = null

    private var isPelotonSyncActive: Boolean = false
    private var isUrbanMuteActive: Boolean = false
    private var isManualResumeOverridingAutoMute: Boolean = false

    private var lastObservedThreatLevel: ThreatLevel = ThreatLevel.NONE
    private var lastObservedLightMode: RadarLightMode = RadarLightMode.OFF

    /**
     * Returns current immutable [RadarAudioSnoozeState].
     */
    @JvmStatic
    fun getSnoozeState(): RadarAudioSnoozeState {
        synchronized(lock) {
            if (checkTimedSnoozeExpirationLocked(timeProvider())) {
                publishSnoozeStateLocked()
            }
            return buildSnoozeStateLocked()
        }
    }

    /**
     * Activates 1-tap temporary audio snooze (REQ-TRK-064.1).
     */
    @JvmStatic
    fun snooze(mode: RadarAudioSnoozeMode) {
        synchronized(lock) {
            currentSnoozeMode = mode
            isManualResumeOverridingAutoMute = false
            val now = timeProvider()
            snoozeEndTimeMs = when (mode) {
                RadarAudioSnoozeMode.SNOOZE_15M -> now + 15 * 60 * 1000L
                RadarAudioSnoozeMode.SNOOZE_30M -> now + 30 * 60 * 1000L
                else -> null
            }
            ensureTickerRunningLocked()
            publishSnoozeStateLocked()
        }
    }

    /**
     * Resumes normal audio alerts, clearing active snooze and overriding Peloton/Urban auto-mute
     * until the next radar state transition (REQ-TRK-064.3).
     */
    @JvmStatic
    fun resumeAudio() {
        synchronized(lock) {
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            if (isPelotonSyncActive || isUrbanMuteActive) {
                isManualResumeOverridingAutoMute = true
            }
            snoozeTickerJob?.cancel()
            snoozeTickerJob = null
            publishSnoozeStateLocked()
        }
    }

    /**
     * Handlebar / remote shifter button toggle: alternates between 15m snooze and resume (REQ-TRK-064.3).
     */
    @JvmStatic
    fun toggleRadarAudioSnooze() {
        synchronized(lock) {
            val state = buildSnoozeStateLocked()
            if (state.isSnoozed) {
                resumeAudio()
            } else {
                snooze(RadarAudioSnoozeMode.SNOOZE_15M)
            }
        }
    }

    /**
     * Updates active state of Peloton tail light audio synchronization (REQ-TRK-064.4).
     */
    @JvmStatic
    fun setPelotonSyncActive(active: Boolean) {
        synchronized(lock) {
            if (isPelotonSyncActive != active) {
                isPelotonSyncActive = active
                if (!active) {
                    isManualResumeOverridingAutoMute = false
                }
                publishSnoozeStateLocked()
            }
        }
    }

    /**
     * Updates active state of Smart Urban congestion filter suppression (REQ-TRK-064.5).
     */
    @JvmStatic
    fun setUrbanMuteActive(active: Boolean) {
        synchronized(lock) {
            if (isUrbanMuteActive != active) {
                isUrbanMuteActive = active
                if (!active) {
                    isManualResumeOverridingAutoMute = false
                }
                publishSnoozeStateLocked()
            }
        }
    }

    /**
     * Observes radar state updates and resets temporary manual resume override upon threat level or light mode changes.
     */
    @JvmStatic
    fun onRadarStateUpdated(state: RadarState) {
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            val threatChanged = state.overallThreatLevel != lastObservedThreatLevel
            val lightChanged = state.lightMode != lastObservedLightMode
            lastObservedThreatLevel = state.overallThreatLevel
            lastObservedLightMode = state.lightMode

            var shouldPublish = expired
            if (isManualResumeOverridingAutoMute && (threatChanged || lightChanged)) {
                isManualResumeOverridingAutoMute = false
                shouldPublish = true
            }
            if (shouldPublish) {
                publishSnoozeStateLocked()
            }
        }
    }

    /**
     * Checks if routine approach and all-clear alerts are currently suppressed for audio chimes (REQ-TRK-064.1, REQ-TRK-064.3).
     */
    @JvmStatic
    fun isRoutineAlertSuppressed(): Boolean {
        synchronized(lock) {
            if (checkTimedSnoozeExpirationLocked(timeProvider())) {
                publishSnoozeStateLocked()
            }
            if (currentSnoozeMode != RadarAudioSnoozeMode.NONE) return true
            if (!isManualResumeOverridingAutoMute) {
                if (isPelotonSyncActive) return true
                if (isUrbanMuteActive) return true
            }
            return false
        }
    }

    /**
     * Checks if fast-approaching threat alerts are suppressed (only on full ride mute or timed snooze).
     */
    @JvmStatic
    fun isFastThreatAlertSuppressed(): Boolean {
        synchronized(lock) {
            if (checkTimedSnoozeExpirationLocked(timeProvider())) {
                publishSnoozeStateLocked()
            }
            return currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_15M ||
                    currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_30M ||
                    currentSnoozeMode == RadarAudioSnoozeMode.REST_OF_RIDE
        }
    }

    /**
     * Returns true if manual resume is currently overriding Peloton or Urban auto-mute.
     */
    @JvmStatic
    fun isManualResumeOverridingAutoMute(): Boolean {
        synchronized(lock) {
            return isManualResumeOverridingAutoMute
        }
    }

    /**
     * Clears all snooze states and cancels ticker jobs on workout completion (REQ-TRK-064.1).
     */
    @JvmStatic
    fun reset() {
        synchronized(lock) {
            snoozeTickerJob?.cancel()
            snoozeTickerJob = null
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            isPelotonSyncActive = false
            isUrbanMuteActive = false
            isManualResumeOverridingAutoMute = false
            lastObservedThreatLevel = ThreatLevel.NONE
            lastObservedLightMode = RadarLightMode.OFF
            publishSnoozeStateLocked()
        }
    }

    @VisibleForTesting
    fun setTimeProvider(provider: () -> Long) {
        synchronized(lock) {
            timeProvider = provider
        }
    }

    @VisibleForTesting
    fun setCoroutineScope(scope: CoroutineScope) {
        synchronized(lock) {
            coroutineScope = scope
        }
    }

    private fun checkTimedSnoozeExpirationLocked(now: Long): Boolean {
        if (snoozeEndTimeMs != null && now >= snoozeEndTimeMs!!) {
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            snoozeTickerJob?.cancel()
            snoozeTickerJob = null
            return true
        }
        return false
    }

    private fun computeRemainingSecondsLocked(now: Long): Int? {
        val endMs = snoozeEndTimeMs ?: return null
        return ((endMs - now).coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun buildSnoozeStateLocked(): RadarAudioSnoozeState {
        val now = timeProvider()
        checkTimedSnoozeExpirationLocked(now)
        val remaining = computeRemainingSecondsLocked(now)
        val pelotonEffective = isPelotonSyncActive && !isManualResumeOverridingAutoMute
        val urbanEffective = isUrbanMuteActive && !isManualResumeOverridingAutoMute
        val isSnoozed = currentSnoozeMode != RadarAudioSnoozeMode.NONE || pelotonEffective || urbanEffective

        return RadarAudioSnoozeState(
            isSnoozed = isSnoozed,
            mode = currentSnoozeMode,
            remainingSeconds = remaining,
            isPelotonSyncActive = isPelotonSyncActive,
            isUrbanMuteActive = isUrbanMuteActive
        )
    }

    private fun publishSnoozeStateLocked() {
        val state = buildSnoozeStateLocked()
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(audioSnoozeState = state))
    }

    private fun ensureTickerRunningLocked() {
        if (snoozeTickerJob?.isActive == true) return
        snoozeTickerJob = coroutineScope.launch {
            while (isActive) {
                delay(1000L)
                synchronized(lock) {
                    if (currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_15M || currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_30M) {
                        val endMs = snoozeEndTimeMs
                        if (endMs != null) {
                            val now = timeProvider()
                            val remaining = ((endMs - now) / 1000L).toInt()
                            if (remaining <= 0) {
                                currentSnoozeMode = RadarAudioSnoozeMode.NONE
                                snoozeEndTimeMs = null
                                snoozeTickerJob?.cancel()
                                snoozeTickerJob = null
                                publishSnoozeStateLocked()
                            } else {
                                publishSnoozeStateLocked()
                            }
                        }
                    } else {
                        snoozeTickerJob?.cancel()
                        snoozeTickerJob = null
                    }
                }
            }
        }
    }
}
