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

package com.atrainingtracker.banalservice.odometry

import kotlin.math.abs

/**
 * Pure-Kotlin monotonic clock normalizer unifying GNSS (`Location.elapsedRealtimeNanos`),
 * hardware IMU (`SensorEvent.timestamp`), and external wheel measurements onto Android's
 * `CLOCK_BOOTTIME` (`elapsedRealtime`) millisecond domain while preserving authentic UTC
 * wall-clock epoch timestamps at external persistence boundaries (`REQ-FIL-042`, `ATT-9600`).
 *
 * Cross-device & test invariants:
 * 1. Standard Android `CLOCK_BOOTTIME` sensors (`|sensorMs - refNowMs| <= 5 s`) use
 *    `event.timestamp / 1_000_000L` directly (`offset = 0`), preserving FIFO/CHRE intervals.
 * 2. Divergent OEM HALs (`uptimeMillis` pausing across deep sleep or Unix epoch nanoseconds
 *    with `> 5 s` skew) latch and drift-reanchor per-sensor-type offsets onto `elapsedRealtime`.
 * 3. Plain-JVM tests where `SystemClock` is unmocked (`elapsedRealtimeNanosProvider() <= 0L`)
 *    degrade cleanly to [wallClockMillisProvider], and Robolectric paused-clock tests remain
 *    coherent across late-arriving sensor types without freezing.
 * 4. Leaf-only internal lock (`lock`) never acquires device or EKF monitors.
 */
@Suppress("MagicNumber")
class AttFusedTimestampNormalizer(
    private val elapsedRealtimeNanosProvider: () -> Long,
    private val wallClockMillisProvider: () -> Long
) {
    companion object {
        /** Max allowed skew (5 s) before classifying a sensor HAL as non-`CLOCK_BOOTTIME`. */
        const val OEM_CLOCK_DIVERGENCE_THRESHOLD_MS = 5_000L
        /** Max allowed skew (5 min) before classifying a location `elapsedRealtimeNanos` as broken. */
        const val MAX_LOCATION_DOMAIN_SKEW_MS = 300_000L
        /** Floor distinguishing Unix epoch milliseconds (>= 2017-07-14) from monotonic/synthetic clocks. */
        const val UNIX_EPOCH_MS_FLOOR = 1_500_000_000_000L
    }

    private val lock = Any()
    private val oemOffsetMsByType = HashMap<Int, Long>()
    private val lastResolvedSensorMsByType = HashMap<Int, Long>()
    private var initialPlatformMonoMs: Long = Long.MIN_VALUE
    private var lastResolvedLocationMs: Long = Long.MIN_VALUE
    private var lastResolvedMonotonicMs: Long = Long.MIN_VALUE
    private var wallOriginMs: Long = Long.MIN_VALUE
    private var monoOriginMs: Long = Long.MIN_VALUE

    private fun safePlatformElapsedNanos(): Long = try {
        val ns = elapsedRealtimeNanosProvider()
        if (ns > 0L) ns else 0L
    } catch (_: Throwable) {
        0L
    }

    private fun notePlatformMonoLocked(rawPlatformMonoMs: Long) {
        if (initialPlatformMonoMs == Long.MIN_VALUE) {
            initialPlatformMonoMs = rawPlatformMonoMs
        }
    }

    private fun noteResolvedMonotonicLocked(resolvedMs: Long) {
        if (lastResolvedMonotonicMs == Long.MIN_VALUE || resolvedMs > lastResolvedMonotonicMs) {
            lastResolvedMonotonicMs = resolvedMs
        }
    }

    /**
     * Returns the current unified monotonic timestamp in milliseconds (`SystemClock.elapsedRealtime`
     * on Android; non-rewinding against previously resolved events; degrades to wall clock on plain JVM).
     */
    fun currentMonotonicMs(): Long {
        val platformNs = safePlatformElapsedNanos()
        val hasPlatformMono = platformNs > 0L
        val rawMonoMs = if (hasPlatformMono) platformNs / 1_000_000L else wallClockMillisProvider()
        synchronized(lock) {
            if (hasPlatformMono) {
                notePlatformMonoLocked(rawMonoMs)
            }
            val resolved = if (lastResolvedMonotonicMs != Long.MIN_VALUE && rawMonoMs < lastResolvedMonotonicMs) {
                lastResolvedMonotonicMs
            } else {
                rawMonoMs
            }
            noteResolvedMonotonicLocked(resolved)
            return resolved
        }
    }

    /**
     * Resolves a GNSS [android.location.Location] timestamp onto the monotonic millisecond clock,
     * preferring [locationElapsedRealtimeNanos] whenever available (`REQ-FIL-042.1`).
     */
    fun resolveLocationTimestampMs(locationElapsedRealtimeNanos: Long, locationTimeMs: Long): Long {
        val platformNs = safePlatformElapsedNanos()
        val hasPlatformMono = platformNs > 0L
        val rawPlatformMonoMs = if (hasPlatformMono) platformNs / 1_000_000L else 0L

        val rawCandidateMs = when {
            locationElapsedRealtimeNanos > 0L -> {
                val locMonoMs = locationElapsedRealtimeNanos / 1_000_000L
                val isBrokenEpochDomain = hasPlatformMono &&
                    locMonoMs >= UNIX_EPOCH_MS_FLOOR &&
                    rawPlatformMonoMs < UNIX_EPOCH_MS_FLOOR
                val isWildlySkewed = hasPlatformMono &&
                    abs(locMonoMs - rawPlatformMonoMs) > MAX_LOCATION_DOMAIN_SKEW_MS
                if (isBrokenEpochDomain || isWildlySkewed) rawPlatformMonoMs else locMonoMs
            }
            hasPlatformMono -> {
                if (locationTimeMs in 1L until UNIX_EPOCH_MS_FLOOR) locationTimeMs else rawPlatformMonoMs
            }
            locationTimeMs > 0L -> locationTimeMs
            else -> wallClockMillisProvider()
        }

        synchronized(lock) {
            if (hasPlatformMono) {
                notePlatformMonoLocked(rawPlatformMonoMs)
            }
            val monotonicLocMs = if (lastResolvedLocationMs != Long.MIN_VALUE && rawCandidateMs < lastResolvedLocationMs) {
                lastResolvedLocationMs
            } else {
                rawCandidateMs
            }
            lastResolvedLocationMs = monotonicLocMs
            noteResolvedMonotonicLocked(monotonicLocMs)
            return monotonicLocMs
        }
    }

    /**
     * Resolves a hardware [android.hardware.SensorEvent] timestamp onto the unified monotonic
     * millisecond clock, detecting and normalizing divergent OEM HAL clock bases (`REQ-FIL-042.2`).
     */
    fun resolveSensorEventTimestampMs(sensorType: Int, sensorTimestampNs: Long): Long {
        val platformNs = safePlatformElapsedNanos()
        val hasPlatformMono = platformNs > 0L
        val rawPlatformMonoMs = if (hasPlatformMono) platformNs / 1_000_000L else wallClockMillisProvider()

        synchronized(lock) {
            val platformAdvanced = hasPlatformMono &&
                initialPlatformMonoMs != Long.MIN_VALUE &&
                rawPlatformMonoMs > initialPlatformMonoMs
            val refNowMs = if (hasPlatformMono &&
                lastResolvedMonotonicMs != Long.MIN_VALUE &&
                lastResolvedMonotonicMs > rawPlatformMonoMs
            ) {
                lastResolvedMonotonicMs
            } else {
                rawPlatformMonoMs
            }

            val candidateMs = if (sensorTimestampNs > 0L) {
                val sensorMs = sensorTimestampNs / 1_000_000L
                val existingOffset = oemOffsetMsByType[sensorType]
                val effectiveOffset = if (existingOffset == null) {
                    val initialOffset = if (abs(sensorMs - refNowMs) <= OEM_CLOCK_DIVERGENCE_THRESHOLD_MS) {
                        0L
                    } else {
                        refNowMs - sensorMs
                    }
                    oemOffsetMsByType[sensorType] = initialOffset
                    initialOffset
                } else {
                    val alignedMs = sensorMs + existingOffset
                    val driftedBehind = (rawPlatformMonoMs - alignedMs) > OEM_CLOCK_DIVERGENCE_THRESHOLD_MS
                    val driftedAheadOnLiveClock = platformAdvanced &&
                        (alignedMs - rawPlatformMonoMs) > OEM_CLOCK_DIVERGENCE_THRESHOLD_MS
                    if (driftedBehind || driftedAheadOnLiveClock) {
                        val refreshedOffset = rawPlatformMonoMs - sensorMs
                        oemOffsetMsByType[sensorType] = refreshedOffset
                        refreshedOffset
                    } else {
                        existingOffset
                    }
                }
                sensorMs + effectiveOffset
            } else {
                refNowMs
            }

            if (hasPlatformMono) {
                notePlatformMonoLocked(rawPlatformMonoMs)
            }
            val prevResolvedMs = lastResolvedSensorMsByType[sensorType]
            val monotonicMs = if (prevResolvedMs != null && candidateMs < prevResolvedMs) {
                prevResolvedMs
            } else {
                candidateMs
            }
            lastResolvedSensorMsByType[sensorType] = monotonicMs
            noteResolvedMonotonicLocked(monotonicMs)
            return monotonicMs
        }
    }

    /**
     * Resolves an external wheel speed callback timestamp onto the unified monotonic millisecond
     * domain, mapping production wall-clock `MySensorManager` timestamps onto `elapsedRealtime`
     * while preserving explicit monotonic or synthetic relative test timestamps (`REQ-FIL-042.2`).
     */
    fun resolveWheelTimestampMs(incomingTimestampMs: Long): Long {
        val platformNs = safePlatformElapsedNanos()
        val hasPlatformMono = platformNs > 0L
        val rawPlatformMonoMs = if (hasPlatformMono) platformNs / 1_000_000L else 0L

        val rawCandidateMs = when {
            hasPlatformMono -> {
                if (incomingTimestampMs > 0L &&
                    (abs(incomingTimestampMs - rawPlatformMonoMs) <= OEM_CLOCK_DIVERGENCE_THRESHOLD_MS ||
                        incomingTimestampMs < UNIX_EPOCH_MS_FLOOR)
                ) {
                    incomingTimestampMs
                } else {
                    rawPlatformMonoMs
                }
            }
            incomingTimestampMs > 0L -> incomingTimestampMs
            else -> wallClockMillisProvider()
        }

        synchronized(lock) {
            if (hasPlatformMono) {
                notePlatformMonoLocked(rawPlatformMonoMs)
            }
            noteResolvedMonotonicLocked(rawCandidateMs)
            return rawCandidateMs
        }
    }

    /**
     * Maps an internal monotonic millisecond timestamp to an authentic, NTP-rewind-immune UTC
     * epoch millisecond timestamp for external persistence (`MultisportTelemetryAnchor`, sidecars).
     * Passes through synthetic non-epoch test timelines unchanged (`REQ-FIL-042.1`).
     */
    fun toWallClockEpochMs(monotonicMs: Long): Long {
        if (monotonicMs == Long.MIN_VALUE) return wallClockMillisProvider()
        val wallNowMs = wallClockMillisProvider()
        if (monotonicMs >= UNIX_EPOCH_MS_FLOOR || wallNowMs < UNIX_EPOCH_MS_FLOOR) {
            return monotonicMs
        }
        synchronized(lock) {
            if (wallOriginMs == Long.MIN_VALUE || monoOriginMs == Long.MIN_VALUE) {
                wallOriginMs = wallNowMs
                monoOriginMs = monotonicMs
            }
            return wallOriginMs + (monotonicMs - monoOriginMs)
        }
    }

    /**
     * Clears all latched OEM offsets, monotonic high-water marks, and UTC anchor origins.
     */
    fun reset() {
        synchronized(lock) {
            oemOffsetMsByType.clear()
            lastResolvedSensorMsByType.clear()
            initialPlatformMonoMs = Long.MIN_VALUE
            lastResolvedLocationMs = Long.MIN_VALUE
            lastResolvedMonotonicMs = Long.MIN_VALUE
            wallOriginMs = Long.MIN_VALUE
            monoOriginMs = Long.MIN_VALUE
        }
    }
}
