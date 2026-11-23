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

import android.hardware.Sensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [AttFusedTimestampNormalizer] verifying unified `elapsedRealtime`
 * clock normalization across GNSS, standard `CLOCK_BOOTTIME` HALs, divergent OEM HALs
 * (`uptimeMillis` + deep sleep, Unix epoch ns + NTP jumps), external wheel callbacks,
 * plain-JVM/Robolectric fallbacks, and UTC boundary preservation (`REQ-FIL-042`, `TST-FIL-042`).
 */
class AttFusedTimestampNormalizerTest {

    // REQ-FIL-042.1 / TST-FIL-042.1: Pixel 10 -2.18 s GNSS wall-clock offset & NTP-immune UTC anchor
    @Test
    fun testStandardClockBoottime_unifiesGnssAndImuIgnoringLaggingLocationTime_TST_FIL_042_1() {
        var platformElapsedNs = 685_756_100_000_000L // 685_756_100 ms
        var wallMs = 1_795_331_659_182L
        val normalizer = AttFusedTimestampNormalizer(
            elapsedRealtimeNanosProvider = { platformElapsedNs },
            wallClockMillisProvider = { wallMs }
        )

        // GPS fix arrives with location.time 2,182 ms behind wallMs, but accurate elapsedRealtimeNanos
        val gpsMonoMs = normalizer.resolveLocationTimestampMs(
            locationElapsedRealtimeNanos = 685_756_090_818_846L,
            locationTimeMs = 1_795_331_657_000L
        )
        assertEquals(685_756_090L, gpsMonoMs)

        // IMU event arrives +459 ms later on CLOCK_BOOTTIME
        platformElapsedNs = 685_756_560_000_000L
        val imuMonoMs = normalizer.resolveSensorEventTimestampMs(
            sensorType = Sensor.TYPE_ACCELEROMETER,
            sensorTimestampNs = 685_756_550_030_993L
        )
        assertEquals(685_756_550L, imuMonoMs)
        assertEquals("IMU must sit +460 ms after GNSS, never +2,182 ms", 460L, imuMonoMs - gpsMonoMs)

        // Next GNSS fix 1 s later must advance strictly monotonically (zero -2.18 s backward sawtooth)
        platformElapsedNs = 685_757_120_000_000L
        val gps2MonoMs = normalizer.resolveLocationTimestampMs(
            locationElapsedRealtimeNanos = 685_757_090_818_846L,
            locationTimeMs = 1_795_331_658_000L
        )
        assertEquals(685_757_090L, gps2MonoMs)
        assertTrue("GNSS #2 must be strictly ahead of IMU #1", gps2MonoMs > imuMonoMs)

        // Legacy/mock Android Location with elapsedRealtimeNanos == 0L and Unix epoch time maps to elapsedRealtime
        platformElapsedNs = 685_758_120_000_000L
        val fallbackLocMs = normalizer.resolveLocationTimestampMs(
            locationElapsedRealtimeNanos = 0L,
            locationTimeMs = 1_795_331_659_000L
        )
        assertEquals(685_758_120L, fallbackLocMs)

        // External UTC epoch mapping stays authentic UTC and immune to mid-workout NTP backward/forward jumps
        val utc0 = normalizer.toWallClockEpochMs(685_756_090L)
        assertEquals(wallMs, utc0)
        wallMs -= 30_000L // -30 s NTP step backward
        val utc1 = normalizer.toWallClockEpochMs(685_757_090L)
        assertEquals("UTC anchor must advance strictly by monotonic +1000 ms despite -30 s NTP jump", utc0 + 1_000L, utc1)
        wallMs += 90_000L // +90 s NTP step forward
        val utc2 = normalizer.toWallClockEpochMs(685_758_090L)
        assertEquals("UTC anchor must advance strictly by monotonic +2000 ms despite +90 s NTP jump", utc0 + 2_000L, utc2)
    }

    // REQ-FIL-042.2 / TST-FIL-042.2: Divergent OEM HALs (uptimeMillis + deep sleep, epoch ns + NTP) & wheel alignment
    @Test
    fun testDivergentOemHals_uptimeMillisDeepSleepAndEpochNsNtpJumpReanchorCleanly_TST_FIL_042_2() {
        var platformElapsedMs = 685_756_000L
        var wallMs = 1_795_331_659_000L
        val normalizer = AttFusedTimestampNormalizer(
            elapsedRealtimeNanosProvider = { platformElapsedMs * 1_000_000L },
            wallClockMillisProvider = { wallMs }
        )

        // 1. ACC on uptimeMillis (-120 s behind elapsedRealtime)
        var uptimeMs = platformElapsedMs - 120_000L
        val acc0 = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, uptimeMs * 1_000_000L)
        assertEquals(685_756_000L, acc0)
        // Next 50-Hz sample preserves exact hardware 20 ms dt
        platformElapsedMs += 20L
        uptimeMs += 20L
        val acc1 = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, uptimeMs * 1_000_000L)
        assertEquals(685_756_020L, acc1)
        assertEquals(20L, acc1 - acc0)

        // Phone enters 30 s deep sleep: elapsedRealtime advances +30 s while uptimeMillis pauses -> re-anchors cleanly
        platformElapsedMs += 30_000L
        uptimeMs += 20L
        val accPostSleep = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, uptimeMs * 1_000_000L)
        assertEquals(685_786_020L, accPostSleep)

        // 2. BARO on Unix epoch ns (~1.795e18) alongside uptimeMillis ACC -> per-type offset keeps both unified
        var baroEpochMs = wallMs + 30_020L
        val baro0 = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_PRESSURE, baroEpochMs * 1_000_000L)
        assertEquals(685_786_020L, baro0)

        // Forward +30 s NTP jump on epoch-ns BARO while platform clock ticks +1 s -> re-anchors onto elapsedRealtime
        platformElapsedMs += 1_000L
        baroEpochMs += 31_000L
        val baroPostForwardNtp = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_PRESSURE, baroEpochMs * 1_000_000L)
        assertEquals(685_787_020L, baroPostForwardNtp)

        // Backward -30 s NTP jump on epoch-ns BARO while platform clock ticks +1 s -> re-anchors without rewinding
        platformElapsedMs += 1_000L
        baroEpochMs -= 29_000L
        val baroPostBackwardNtp = normalizer.resolveSensorEventTimestampMs(Sensor.TYPE_PRESSURE, baroEpochMs * 1_000_000L)
        assertEquals(685_788_020L, baroPostBackwardNtp)

        // 3. Wall-clock wheel measurement from MySensorManager (System.currentTimeMillis()) maps to elapsedRealtime
        val wheelMonoMs = normalizer.resolveWheelTimestampMs(wallMs + 32_020L)
        assertEquals(685_788_020L, wheelMonoMs)

        // Explicit monotonic wheel timestamp is preserved verbatim
        val explicitMonoWheel = normalizer.resolveWheelTimestampMs(685_788_500L)
        assertEquals(685_788_500L, explicitMonoWheel)
    }

    // REQ-FIL-042.2 / TST-FIL-042.2: Plain-JVM unmocked SystemClock, Robolectric frozen clock coherence, and reset()
    @Test
    fun testPlainJvmUnmockedAndRobolectricFrozenClockCoherenceAndReset_TST_FIL_042_2() {
        // 1. Plain JVM where SystemClock throws RuntimeException("Method not mocked")
        var fakeWallMs = 1_000L
        val jvmNormalizer = AttFusedTimestampNormalizer(
            elapsedRealtimeNanosProvider = { throw RuntimeException("Method elapsedRealtimeNanos not mocked") },
            wallClockMillisProvider = { fakeWallMs }
        )
        assertEquals(1_000L, jvmNormalizer.currentMonotonicMs())
        assertEquals(1_000L, jvmNormalizer.resolveLocationTimestampMs(0L, 1_000L))
        fakeWallMs = 2_000L
        assertEquals(2_000L, jvmNormalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, 2_000_000_000L))
        assertEquals(2_000L, jvmNormalizer.resolveWheelTimestampMs(2_000L))

        // 2. Robolectric frozen SystemClock (constant 100 ms) with synthetic relative test timeline (1_000..13_000 ms)
        val roboNormalizer = AttFusedTimestampNormalizer(
            elapsedRealtimeNanosProvider = { 100_000_000L }, // frozen at 100 ms
            wallClockMillisProvider = { 1_000L }
        )
        assertEquals(1_000L, roboNormalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, 1_000_000_000L))
        assertEquals(6_500L, roboNormalizer.resolveSensorEventTimestampMs(Sensor.TYPE_ACCELEROMETER, 6_500_000_000L))
        // Late-arriving STEP_DETECTOR at t = 7_000 ms must latch against lastResolvedMonotonicMs (6_500), NOT frozen 100 ms!
        assertEquals(7_000L, roboNormalizer.resolveSensorEventTimestampMs(Sensor.TYPE_STEP_DETECTOR, 7_000_000_000L))
        // Robolectric Location with elapsedRealtimeNanos == 0L and synthetic relative time < 1.5e12 stays coherent
        assertEquals(8_000L, roboNormalizer.resolveLocationTimestampMs(0L, 8_000L))
        // currentMonotonicMs() (e.g., SPORT_TYPE_CHANGED_BY_USER / triggerDecayTick) must never rewind to 100 ms
        assertEquals(8_000L, roboNormalizer.currentMonotonicMs())

        // 3. reset() clears high-water marks, latched OEM offsets, and UTC anchor cleanly
        roboNormalizer.reset()
        assertEquals(100L, roboNormalizer.currentMonotonicMs())
        assertEquals(500L, roboNormalizer.resolveLocationTimestampMs(0L, 500L))
    }
}
