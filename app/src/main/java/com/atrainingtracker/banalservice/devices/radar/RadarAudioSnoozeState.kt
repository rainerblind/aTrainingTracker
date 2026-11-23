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

/**
 * Snooze modes for radar audio alerts (REQ-TRK-064.1).
 */
enum class RadarAudioSnoozeMode {
    /** Audio alerts fully enabled (no snooze). */
    NONE,
    /** Standard audio alerts muted for 15 minutes. */
    SNOOZE_15M,
    /** Standard audio alerts muted for 30 minutes. */
    SNOOZE_30M,
    /** Routine approach and all-clear chimes suppressed; urgent red threats continue to sound. */
    FAST_ONLY,
    /** All audio alerts muted for remainder of workout. */
    REST_OF_RIDE
}

/**
 * Immutable state tracking audio snooze status and monotonic expiration deadline (REQ-TRK-064.1, REQ-CON-056.3).
 *
 * @property isSnoozed True if any audio snooze or suppression is currently active.
 * @property mode Current active snooze mode.
 * @property remainingSeconds Optional snapshot remaining seconds (computed on-demand by [RadarSnoozeController.getSnoozeState] or legacy tests).
 * @property isPelotonSyncActive True if currently silenced by Peloton tail light mode.
 * @property isUrbanMuteActive True if currently suppressed by Smart Urban congestion filter.
 * @property snoozeUntilElapsedRealtimeMs Monotonic `elapsedRealtime()` deadline for timed snooze modes (`SNOOZE_15M`/`SNOOZE_30M`), null if untimed or inactive.
 */
data class RadarAudioSnoozeState @JvmOverloads constructor(
    val isSnoozed: Boolean = false,
    val mode: RadarAudioSnoozeMode = RadarAudioSnoozeMode.NONE,
    val remainingSeconds: Int? = null,
    val isPelotonSyncActive: Boolean = false,
    val isUrbanMuteActive: Boolean = false,
    val snoozeUntilElapsedRealtimeMs: Long? = null
) {
    /**
     * Computes remaining seconds at [nowElapsedRealtimeMs] from [snoozeUntilElapsedRealtimeMs]
     * without requiring a 1-Hz background ticker (REQ-CON-056.3).
     */
    fun remainingSecondsAt(nowElapsedRealtimeMs: Long = RadarSnoozeController.DEFAULT_TIME_PROVIDER()): Int? {
        val until = snoozeUntilElapsedRealtimeMs
        if (until != null) {
            return ((until - nowElapsedRealtimeMs).coerceAtLeast(0L) / 1000L).toInt()
        }
        return remainingSeconds
    }

    companion object {
        @JvmField
        val INACTIVE = RadarAudioSnoozeState()
    }
}
