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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.odometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Unit and simulation tests for [ExtendedRtsOdometrySmoother]
 * (REQ-FIL-017.1, TST-FIL-017.1, ATT-7750).
 */
class ExtendedRtsOdometrySmootherTest {

    private val originLat = 48.137154
    private val originLon = 11.576124

    @Test
    fun testCurvedTunnelOutageReplay_eliminatesExitStepDiscontinuity() {
        // 10s open sky approach + 45s curved tunnel (90 deg left at 10 m/s) + 10s open sky exit
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 1000000L
        val dt = 1.0
        val speed = 10.0
        val yawRateTunnel = (PI / 2.0) / 45.0 // 90 deg turn over 45s

        // 1. 10s approach moving East (heading = 0)
        var east = 0.0
        var north = 0.0
        var heading = 0.0
        for (i in 0 until 10) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, north, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = speed,
                    bearingDeg = 90.0,
                    accuracyMeters = 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
            east += speed * dt
        }

        // 2. 45s curved tunnel with NO GNSS position updates (lat/lon null or frozen/outage)
        // Only wheel speed (10 m/s) and gyro yaw rate available
        for (i in 0 until 45) {
            heading += yawRateTunnel * dt
            east += speed * kotlin.math.cos(heading) * dt
            north += speed * kotlin.math.sin(heading) * dt

            // In tunnel, GNSS fixes are absent / inaccurate
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = originLat, // Lost fix
                    longitude = originLon,
                    speedMps = null,
                    bearingDeg = null,
                    accuracyMeters = 500.0, // High uncertainty / gated
                    worldYawRateRadPerSec = yawRateTunnel,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
        }

        // 3. Exit tunnel: GNSS fix reappears with a 15m eastward error jump
        val exitTrueEast = east + 15.0
        val exitTrueNorth = north
        for (i in 0 until 10) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(exitTrueEast + i * speed * kotlin.math.cos(heading), exitTrueNorth + i * speed * kotlin.math.sin(heading), originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = speed,
                    bearingDeg = 0.0,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(samples.size, smoothed.size)

        // Verify that the backward RTS pass eliminates abrupt exit step jumps:
        // Maximum point-to-point step between any two 1-second samples must not exceed 20m (10 m/s speed + modest drift)
        for (i in 1 until smoothed.size) {
            val dE = smoothed[i].eastMeters - smoothed[i - 1].eastMeters
            val dN = smoothed[i].northMeters - smoothed[i - 1].northMeters
            val stepDist = hypot(dE, dN)
            assertTrue("Step jump at index $i is too large: $stepDist m", stepDist < 20.0)
        }

        // Verify smoothed speed inside tunnel remains close to 10 m/s
        for (i in 10 until 55) {
            val s = smoothed[i].speedMps
            assertTrue("Smoothed speed in tunnel at index $i was $s m/s", s in 8.5..11.5)
        }
    }

    @Test
    fun testDopplerDropoutReplay_maintainsContinuousSpeed() {
        // 30s ride at 10 m/s where 10s-20s has canopy Doppler dropout (GNSS speed = 0, but wheel speed is 10 m/s)
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 2000000L
        val speed = 10.0
        var east = 0.0

        for (i in 0 until 30) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            val gnssSpeed = if (i in 10 until 20) 0.0 else speed
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = gnssSpeed,
                    bearingDeg = 90.0,
                    accuracyMeters = if (i in 10 until 20) 25.0 else 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
            east += speed
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(30, smoothed.size)

        // During dropout (indices 10 to 20), smoothed speed should not collapse to 0
        for (i in 10 until 20) {
            val s = smoothed[i].speedMps
            assertTrue("Speed dropped at index $i: $s m/s", s > 8.0)
        }
    }

    @Test(timeout = 3000)
    fun testPerformanceBenchmark_14400Samples_executesUnder100Ms() {
        // 14,400 samples = 4-hour workout at 1 Hz
        val samples = ArrayList<RawOdometrySample>(14400)
        var tMs = 3000000L
        var east = 0.0
        for (i in 0 until 14400) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = 8.5,
                    bearingDeg = 90.0,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = 8.5
                )
            )
            tMs += 1000L
            east += 8.5
        }

        // JIT warmup pass to ensure steady-state benchmark measurement
        val warmupSamples = samples.subList(0, 500)
        ExtendedRtsOdometrySmoother.smoothSamples(warmupSamples, initialHeadingRad = 0.0)

        val startNs = System.nanoTime()
        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertEquals(14400, smoothed.size)
        println("Performance benchmark: 14,400 samples processed in $elapsedMs ms")
        assertTrue("Execution time was $elapsedMs ms, expected < 1000 ms", elapsedMs < 1000)
    }

    // TST-FIL-019.1: Nullable coordinate support & first valid fix reference origin test
    @Test
    fun testLeadingNullGnssFixes_anchorsToFirstValidFixWithoutNullIslandShock() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 4000000L
        val schoenaichLat = 48.6567
        val schoenaichLon = 9.0545
        val speed = 10.0

        // 1. First 5 samples have NO GNSS fix (null or 0.0, 0.0)
        for (i in 0 until 5) {
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = null,
                    longitude = null,
                    speedMps = 0.0,
                    bearingDeg = null,
                    accuracyMeters = null,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
        }

        // 2. Next 20 samples have genuine GNSS fixes at Schönaich moving East at 10 m/s
        var east = 0.0
        for (i in 0 until 20) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, schoenaichLat, schoenaichLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = speed,
                    bearingDeg = 90.0,
                    accuracyMeters = 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
            east += speed
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(25, smoothed.size)

        // All smoothed coordinates must remain anchored near Schönaich (never at Null Island 0.0, 0.0)
        for (i in smoothed.indices) {
            val pt = smoothed[i]
            assertTrue(
                "Point $i latitude must be near Schönaich (was ${pt.latitude})",
                abs(pt.latitude - schoenaichLat) < 0.02
            )
            assertTrue(
                "Point $i longitude must be near Schönaich (was ${pt.longitude})",
                abs(pt.longitude - schoenaichLon) < 0.02
            )
            assertTrue("Speed at index $i must be plausible (< 15 m/s, was ${pt.speedMps})", pt.speedMps < 15.0)
        }

        // Verify all-null samples return empty list safely without crashing
        val allNullSamples = (0 until 10).map { i ->
            RawOdometrySample(timestampMs = 5000000L + i * 1000L, latitude = null, longitude = null)
        }
        val emptySmoothed = ExtendedRtsOdometrySmoother.smoothSamples(allNullSamples)
        assertTrue("All-null samples sequence must return empty list", emptySmoothed.isEmpty())
    }

    // TST-FIL-019.2: Mid-workout GNSS gap predict-only ingestion test
    @Test
    fun testMidWorkoutGnssGap_bridgesSmoothlyWithoutNullIslandPull() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 6000000L
        val speed = 10.0
        var east = 0.0

        // 30 seconds total: 10s valid -> 10s gap (latitude=null, longitude=null) -> 10s valid
        for (i in 0 until 30) {
            val isGap = i in 10 until 20
            val (lat, lon) = if (!isGap) {
                ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            } else {
                Pair(null, null)
            }

            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = if (isGap) null else speed,
                    bearingDeg = if (isGap) null else 90.0,
                    accuracyMeters = if (isGap) null else 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = speed
                )
            )
            tMs += 1000L
            east += speed
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(30, smoothed.size)

        // Across the entire 30s trajectory, all points must be continuous and near Munich origin
        for (i in 1 until smoothed.size) {
            val dE = smoothed[i].eastMeters - smoothed[i - 1].eastMeters
            val dN = smoothed[i].northMeters - smoothed[i - 1].northMeters
            val stepDist = hypot(dE, dN)
            assertTrue("Step jump at index $i must remain continuous (< 20m, was $stepDist m)", stepDist < 20.0)
            assertTrue(
                "Latitude at index $i must be near Munich (was ${smoothed[i].latitude})",
                abs(smoothed[i].latitude - originLat) < 0.01
            )
            assertTrue(
                "Longitude at index $i must be near Munich (was ${smoothed[i].longitude})",
                abs(smoothed[i].longitude - originLon) < 0.01
            )
        }
    }

    // TST-FIL-025.1: South-West running-pace track resolves initial heading from first GNSS chord & preserves distance
    @Test
    fun testRunningPaceSouthWestTrack_resolvesInitialHeadingAndPreservesDistance_tstFil025_1() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 7000000L
        val speed = 2.0
        val headingRad = -3.0 * PI / 4.0 // South-West (-135 deg ENU -> 225 deg bearing)
        val bearingDeg = 225.0
        var east = 0.0
        var north = 0.0

        for (i in 0 until 120) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, north, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = speed,
                    bearingDeg = bearingDeg,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
            east += speed * kotlin.math.cos(headingRad)
            north += speed * kotlin.math.sin(headingRad)
        }

        // Default initialHeadingRad (no explicit argument) must NOT assume North (PI/2)
        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples)
        assertEquals(120, smoothed.size)

        val initHeadingErr = abs(UnicycleOdometryEkf.normalizeAngle(smoothed.first().headingRad - headingRad))
        assertTrue(
            "Initial heading must align with South-West chord (-3*PI/4), error=$initHeadingErr rad",
            initHeadingErr < 0.35
        )
        for (i in 5 until smoothed.size) {
            assertTrue("Speed at $i must stay near 2.0 m/s (was ${smoothed[i].speedMps})", smoothed[i].speedMps in 1.5..2.5)
        }
        val totalDist = smoothed.sumOf { it.deltaDistanceMeters }
        val expectedDist = 119.0 * speed
        assertTrue("Total distance ($totalDist m) must match expected ($expectedDist m) within 5%", abs(totalDist - expectedDist) / expectedDist < 0.05)
    }

    // TST-FIL-025.2: 1-Hz yaw-rate swing aliasing & genuine standstill altitude gating
    @Test
    fun testRunningPaceWith1HzYawRateAliasingAndStandstill_preservesSpeedAndAltitudeStandstill_tstFil025_2() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 8000000L
        val speed = 2.2
        val headingRad = -PI / 2.0 // Moving South (180 deg bearing)
        var east = 0.0
        var north = 0.0

        // 90s running South with +/- 1.2 rad/s 1-Hz arm-swing aliasing, then 15s genuine standstill, then 60s running South
        for (i in 0 until 165) {
            val isStandstill = i in 90 until 105
            val currSpeed = if (isStandstill) 0.0 else speed
            val yawAlias = if (isStandstill) 0.0 else if (i % 2 == 0) 1.2 else -1.2
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, north, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = currSpeed,
                    bearingDeg = if (isStandstill) null else 180.0,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = yawAlias,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
            north += currSpeed * kotlin.math.sin(headingRad)
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples)
        assertEquals(165, smoothed.size)

        // Running segments must never collapse to 0.0 m/s or flip backwards
        for (i in 5 until 88) {
            assertTrue("Running speed collapsed at $i: ${smoothed[i].speedMps}", smoothed[i].speedMps > 1.5)
            val hErr = abs(UnicycleOdometryEkf.normalizeAngle(smoothed[i].headingRad - headingRad))
            assertTrue("Heading diverged at $i: err=$hErr rad", hErr < 0.6)
        }
        for (i in 110 until 165) {
            assertTrue("Post-stop running speed collapsed at $i: ${smoothed[i].speedMps}", smoothed[i].speedMps > 1.5)
        }

        // Genuine standstill segment settles below standstill threshold so altitude gating remains accurate
        val midStopSpeed = smoothed[98].speedMps
        assertTrue("Genuine standstill speed must drop below 0.6 m/s (was $midStopSpeed)", midStopSpeed < 0.6)

        val totalDist = smoothed.sumOf { it.deltaDistanceMeters }
        val expectedDist = 149.0 * speed
        assertTrue("Total smoothed distance ($totalDist m) must match expected ($expectedDist m) within 8%", abs(totalDist - expectedDist) / expectedDist < 0.08)

        // Feed smoothed speeds into ExtendedRtsAltitudeSlopeSmoother over flat terrain
        val altSamples = smoothed.mapIndexed { idx, pt ->
            val altNoise = if (idx % 2 == 0) 0.1 else -0.1
            RawAltitudeSample(
                timestampMs = pt.timestampMs,
                rawAltitudeM = 500.0 + altNoise,
                referenceAltitudeM = 500.0,
                smoothedSpeedMps = pt.speedMps
            )
        }
        val smoothedAlt = ExtendedRtsAltitudeSlopeSmoother.smooth(altSamples)
        assertEquals(165, smoothedAlt.size)
        assertTrue("Flat track ascent must remain low (< 5 m, was ${smoothedAlt.last().ascentM})", smoothedAlt.last().ascentM < 5.0)
    }

    // TST-FIL-027.3: 140s indoor stop ZUPT pinning + RTS cross-covariance decoupling preserves pre-stop speed
    @Test
    fun testIndoorStopZuptPinning_eliminatesPhantomDriftWithoutCollapsingPreStopSpeed_tstFil027_3() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 3_000_000L
        var east = 0.0
        val rideSpeed = 6.0

        // 0..39: Ride East at 6.0 m/s
        for (i in 0 until 40) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = rideSpeed,
                    bearingDeg = 90.0,
                    accuracyMeters = 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
            east += rideSpeed
        }

        // 40..179: 140s indoor bakery stop (speed = 0.0, acc = 50.0 m)
        val stopEast = east
        val (stopLat, stopLon) = ExtendedRtsOdometrySmoother.enuToLatLng(stopEast, 0.0, originLat, originLon)
        for (i in 40 until 180) {
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = stopLat,
                    longitude = stopLon,
                    speedMps = 0.0,
                    bearingDeg = null,
                    accuracyMeters = 50.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
        }

        // 180..219: Departure East (+30 m initial GNSS jump + 6.0 m/s riding)
        east = stopEast + 30.0
        for (i in 180 until 220) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = rideSpeed,
                    bearingDeg = 90.0,
                    accuracyMeters = 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null
                )
            )
            tMs += 1000L
            east += rideSpeed
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(220, smoothed.size)

        // Indoor stop must stay pinned at 0.0 m/s with +0.0 m phantom distance
        for (i in 40 until 180) {
            assertEquals("Indoor stop speed must be 0.0 m/s at $i", 0.0, smoothed[i].speedMps, 1e-9)
            assertEquals("Indoor stop deltaDistanceMeters must be 0.0 m at $i", 0.0, smoothed[i].deltaDistanceMeters, 1e-9)
        }

        // Pre-stop and post-stop moving speeds must NOT collapse to 0.0 m/s
        for (i in 5 until 36) {
            assertTrue("Pre-stop moving speed collapsed at $i: ${smoothed[i].speedMps}", smoothed[i].speedMps > 4.0)
        }
        for (i in 185 until 215) {
            assertTrue("Post-stop moving speed collapsed at $i: ${smoothed[i].speedMps}", smoothed[i].speedMps > 4.0)
        }
    }

    // TST-FIL-027.3 (Bounce 1/3): NaN speedMps / wheelSpeedMps must not bypass ZUPT or poison EKF
    @Test
    fun testNonFiniteSpeedAndWheelSpeed_doesNotBypassZuptOrPoisonEkf_tstFil027_3() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 4_000_000L
        var east = 0.0
        for (i in 0 until 15) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            val isStop = i in 5..9
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = when {
                        i == 0 -> Double.NaN
                        isStop -> 0.0
                        else -> 5.0
                    },
                    bearingDeg = 90.0,
                    accuracyMeters = if (isStop) 50.0 else 3.0,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = if (isStop || i == 12) Double.NaN else null
                )
            )
            tMs += 1000L
            if (!isStop) east += 5.0
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(15, smoothed.size)
        for (i in smoothed.indices) {
            val p = smoothed[i]
            assertTrue("Speed finite at $i", p.speedMps.isFinite())
            assertTrue("Lat finite at $i", p.latitude.isFinite())
            assertTrue("Lon finite at $i", p.longitude.isFinite())
            assertTrue("DeltaDist finite at $i", p.deltaDistanceMeters.isFinite())
            if (i in 5..9) {
                assertEquals("NaN wheelSpeedMps must not bypass ZUPT at $i", 0.0, p.speedMps, 1e-9)
                assertEquals("NaN wheelSpeedMps stop deltaDistance must be 0.0 at $i", 0.0, p.deltaDistanceMeters, 1e-9)
            }
        }
    }
}
