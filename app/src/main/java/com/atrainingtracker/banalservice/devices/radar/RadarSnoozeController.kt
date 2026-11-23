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

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lifecycle-safe central controller for cycling rearview radar audio snooze and suppression (REQ-TRK-064.1, REQ-CON-056.3).
 *
 * Publishes monotonic [RadarAudioSnoozeState.snoozeUntilElapsedRealtimeMs] deadlines into
 * [RadarStateRepository.snoozeState] and uses a single cancel-and-reschedule one-shot expiry timer
 * instead of a 1-Hz background polling loop, eliminating spurious background wakeups.
 */
object RadarSnoozeController {

    private val lock = Any()

    /**
     * Canonical monotonic time provider using [SystemClock.elapsedRealtime] with JVM test fallback (REQ-TRK-064.1).
     */
    @Suppress("TooGenericExceptionCaught")
    val DEFAULT_TIME_PROVIDER: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }
    }

    @Volatile
    private var coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var timeProvider: () -> Long = DEFAULT_TIME_PROVIDER

    private var currentSnoozeMode: RadarAudioSnoozeMode = RadarAudioSnoozeMode.NONE
    private var snoozeEndTimeMs: Long? = null
    private var snoozeExpiryJob: Job? = null

    private var isPelotonSyncActive: Boolean = false
    private var isUrbanMuteActive: Boolean = false
    private var isManualResumeOverridingAutoMute: Boolean = false

    private var lastObservedThreatLevel: ThreatLevel = ThreatLevel.NONE
    private var lastObservedLightMode: RadarLightMode = RadarLightMode.OFF

    /**
     * Returns current immutable [RadarAudioSnoozeState] with on-demand [RadarAudioSnoozeState.remainingSeconds].
     */
    @JvmStatic
    fun getSnoozeState(): RadarAudioSnoozeState {
        val stateToPublish: RadarAudioSnoozeState?
        val snapshot: RadarAudioSnoozeState
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            stateToPublish = if (expired) buildPublishedSnoozeStateLocked(now) else null
            snapshot = buildQuerySnoozeStateLocked(now)
        }
        stateToPublish?.let { published ->
            RadarStateRepository.updateSnoozeState { published }
        }
        return snapshot
    }

    /**
     * Activates 1-tap temporary audio snooze (REQ-TRK-064.1, REQ-CON-056.3).
     * Always cancels and reschedules any active one-shot expiry timer.
     */
    @JvmStatic
    fun snooze(mode: RadarAudioSnoozeMode) {
        val published: RadarAudioSnoozeState
        synchronized(lock) {
            currentSnoozeMode = mode
            isManualResumeOverridingAutoMute = false
            val now = timeProvider()
            snoozeEndTimeMs = when (mode) {
                RadarAudioSnoozeMode.SNOOZE_15M -> now + 15 * 60 * 1000L
                RadarAudioSnoozeMode.SNOOZE_30M -> now + 30 * 60 * 1000L
                else -> null
            }
            rescheduleExpiryTimerLocked(snoozeEndTimeMs)
            published = buildPublishedSnoozeStateLocked(now)
        }
        RadarStateRepository.updateSnoozeState { published }
    }

    /**
     * Resumes normal audio alerts, clearing active snooze and overriding Peloton/Urban auto-mute
     * until the next radar state transition (REQ-TRK-064.3).
     */
    @JvmStatic
    fun resumeAudio() {
        val published: RadarAudioSnoozeState
        synchronized(lock) {
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            if (isPelotonSyncActive || isUrbanMuteActive) {
                isManualResumeOverridingAutoMute = true
            }
            snoozeExpiryJob?.cancel()
            snoozeExpiryJob = null
            published = buildPublishedSnoozeStateLocked(timeProvider())
        }
        RadarStateRepository.updateSnoozeState { published }
    }

    /**
     * Handlebar / remote shifter button toggle: alternates between 15m snooze and resume (REQ-TRK-064.3).
     */
    @JvmStatic
    fun toggleRadarAudioSnooze() {
        val shouldResume = synchronized(lock) {
            buildQuerySnoozeStateLocked(timeProvider()).isSnoozed
        }
        if (shouldResume) {
            resumeAudio()
        } else {
            snooze(RadarAudioSnoozeMode.SNOOZE_15M)
        }
    }

    /**
     * Updates active state of Peloton tail light audio synchronization (REQ-TRK-064.4).
     */
    @JvmStatic
    fun setPelotonSyncActive(active: Boolean) {
        val published: RadarAudioSnoozeState?
        synchronized(lock) {
            if (isPelotonSyncActive != active) {
                isPelotonSyncActive = active
                if (!active) {
                    isManualResumeOverridingAutoMute = false
                }
                published = buildPublishedSnoozeStateLocked(timeProvider())
            } else {
                published = null
            }
        }
        published?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
    }

    /**
     * Updates active state of Smart Urban congestion filter suppression (REQ-TRK-064.5).
     */
    @JvmStatic
    fun setUrbanMuteActive(active: Boolean) {
        val published: RadarAudioSnoozeState?
        synchronized(lock) {
            if (isUrbanMuteActive != active) {
                isUrbanMuteActive = active
                if (!active) {
                    isManualResumeOverridingAutoMute = false
                }
                published = buildPublishedSnoozeStateLocked(timeProvider())
            } else {
                published = null
            }
        }
        published?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
    }

    /**
     * Observes radar threat-level transitions to reset temporary manual resume override (REQ-CON-056.3).
     */
    @JvmStatic
    fun onThreatLevelChanged(threatLevel: ThreatLevel) {
        val published: RadarAudioSnoozeState?
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            val threatChanged = threatLevel != lastObservedThreatLevel
            lastObservedThreatLevel = threatLevel

            var shouldPublish = expired
            if (isManualResumeOverridingAutoMute && threatChanged) {
                isManualResumeOverridingAutoMute = false
                shouldPublish = true
            }
            published = if (shouldPublish) buildPublishedSnoozeStateLocked(now) else null
        }
        published?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
    }

    /**
     * Observes radar light-mode transitions to reset temporary manual resume override (REQ-CON-056.3).
     */
    @JvmStatic
    fun onLightModeChanged(lightMode: RadarLightMode) {
        val published: RadarAudioSnoozeState?
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            val lightChanged = lightMode != lastObservedLightMode
            lastObservedLightMode = lightMode

            var shouldPublish = expired
            if (isManualResumeOverridingAutoMute && lightChanged) {
                isManualResumeOverridingAutoMute = false
                shouldPublish = true
            }
            published = if (shouldPublish) buildPublishedSnoozeStateLocked(now) else null
        }
        published?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
    }

    /**
     * Observes combined radar state updates and resets temporary manual resume override upon threat level or light mode changes.
     */
    @JvmStatic
    fun onRadarStateUpdated(state: RadarState) {
        val published: RadarAudioSnoozeState?
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
            published = if (shouldPublish) buildPublishedSnoozeStateLocked(now) else null
        }
        published?.let { s ->
            RadarStateRepository.updateSnoozeState { s }
        }
    }

    /**
     * Checks if routine approach and all-clear alerts are currently suppressed for audio chimes (REQ-TRK-064.1, REQ-TRK-064.3).
     */
    @JvmStatic
    fun isRoutineAlertSuppressed(): Boolean {
        val expiredPublish: RadarAudioSnoozeState?
        val suppressed: Boolean
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            expiredPublish = if (expired) buildPublishedSnoozeStateLocked(now) else null
            suppressed = when {
                currentSnoozeMode != RadarAudioSnoozeMode.NONE -> true
                !isManualResumeOverridingAutoMute && (isPelotonSyncActive || isUrbanMuteActive) -> true
                else -> false
            }
        }
        expiredPublish?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
        return suppressed
    }

    /**
     * Checks if fast-approaching threat alerts are suppressed (only on full ride mute or timed snooze).
     */
    @JvmStatic
    fun isFastThreatAlertSuppressed(): Boolean {
        val expiredPublish: RadarAudioSnoozeState?
        val suppressed: Boolean
        synchronized(lock) {
            val now = timeProvider()
            val expired = checkTimedSnoozeExpirationLocked(now)
            expiredPublish = if (expired) buildPublishedSnoozeStateLocked(now) else null
            suppressed = currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_15M ||
                currentSnoozeMode == RadarAudioSnoozeMode.SNOOZE_30M ||
                currentSnoozeMode == RadarAudioSnoozeMode.REST_OF_RIDE
        }
        expiredPublish?.let { state ->
            RadarStateRepository.updateSnoozeState { state }
        }
        return suppressed
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
     * Clears all snooze states and cancels pending expiry jobs on workout completion (REQ-TRK-064.1).
     */
    @JvmStatic
    fun reset() {
        val published: RadarAudioSnoozeState
        synchronized(lock) {
            snoozeExpiryJob?.cancel()
            snoozeExpiryJob = null
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            isPelotonSyncActive = false
            isUrbanMuteActive = false
            isManualResumeOverridingAutoMute = false
            lastObservedThreatLevel = ThreatLevel.NONE
            lastObservedLightMode = RadarLightMode.OFF
            timeProvider = DEFAULT_TIME_PROVIDER
            published = RadarAudioSnoozeState.INACTIVE
        }
        RadarStateRepository.updateSnoozeState { published }
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
        val endMs = snoozeEndTimeMs
        if (endMs != null && now >= endMs) {
            currentSnoozeMode = RadarAudioSnoozeMode.NONE
            snoozeEndTimeMs = null
            snoozeExpiryJob?.cancel()
            snoozeExpiryJob = null
            return true
        }
        return false
    }

    private fun computeRemainingSecondsLocked(now: Long): Int? {
        val endMs = snoozeEndTimeMs ?: return null
        return ((endMs - now).coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun buildQuerySnoozeStateLocked(now: Long): RadarAudioSnoozeState {
        val remaining = computeRemainingSecondsLocked(now)
        val pelotonEffective = isPelotonSyncActive && !isManualResumeOverridingAutoMute
        val urbanEffective = isUrbanMuteActive && !isManualResumeOverridingAutoMute
        val isSnoozed = currentSnoozeMode != RadarAudioSnoozeMode.NONE || pelotonEffective || urbanEffective

        return RadarAudioSnoozeState(
            isSnoozed = isSnoozed,
            mode = currentSnoozeMode,
            remainingSeconds = remaining,
            isPelotonSyncActive = isPelotonSyncActive,
            isUrbanMuteActive = isUrbanMuteActive,
            snoozeUntilElapsedRealtimeMs = snoozeEndTimeMs
        )
    }

    private fun buildPublishedSnoozeStateLocked(now: Long): RadarAudioSnoozeState {
        checkTimedSnoozeExpirationLocked(now)
        val pelotonEffective = isPelotonSyncActive && !isManualResumeOverridingAutoMute
        val urbanEffective = isUrbanMuteActive && !isManualResumeOverridingAutoMute
        val isSnoozed = currentSnoozeMode != RadarAudioSnoozeMode.NONE || pelotonEffective || urbanEffective

        return RadarAudioSnoozeState(
            isSnoozed = isSnoozed,
            mode = currentSnoozeMode,
            remainingSeconds = null,
            isPelotonSyncActive = isPelotonSyncActive,
            isUrbanMuteActive = isUrbanMuteActive,
            snoozeUntilElapsedRealtimeMs = snoozeEndTimeMs
        )
    }

    /**
     * Cancels any previous timer and schedules a single one-shot expiry coroutine for [targetEndMs].
     * Re-checks `timeProvider() >= targetEndMs` on wakeup to guard against Doze/clock drift (REQ-CON-056.3).
     */
    private fun rescheduleExpiryTimerLocked(targetEndMs: Long?) {
        snoozeExpiryJob?.cancel()
        snoozeExpiryJob = null
        if (targetEndMs == null) return

        snoozeExpiryJob = coroutineScope.launch {
            var previousNow = Long.MIN_VALUE
            while (isActive) {
                var expiredState: RadarAudioSnoozeState? = null
                var stalledClock = false
                val waitMs: Long = synchronized(lock) {
                    val activeEnd = snoozeEndTimeMs
                    if (activeEnd != targetEndMs ||
                        (currentSnoozeMode != RadarAudioSnoozeMode.SNOOZE_15M &&
                            currentSnoozeMode != RadarAudioSnoozeMode.SNOOZE_30M)
                    ) {
                        return@launch
                    }
                    val now = timeProvider()
                    val delta = activeEnd - now
                    if (delta <= 0L) {
                        currentSnoozeMode = RadarAudioSnoozeMode.NONE
                        snoozeEndTimeMs = null
                        snoozeExpiryJob = null
                        expiredState = buildPublishedSnoozeStateLocked(now)
                        0L
                    } else if (previousNow != Long.MIN_VALUE && now <= previousNow) {
                        // Injected test clock did not advance across delay(); stop instead of spinning.
                        snoozeExpiryJob = null
                        stalledClock = true
                        0L
                    } else {
                        previousNow = now
                        delta
                    }
                }
                val toPublish = expiredState
                if (toPublish != null) {
                    RadarStateRepository.updateSnoozeState { toPublish }
                    return@launch
                }
                if (stalledClock) {
                    return@launch
                }
                delay(waitMs)
            }
        }
    }
}
