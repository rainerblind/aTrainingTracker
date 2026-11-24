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

    // TST-FIL-028.4: Carriage-aware ERTS stationary pinning (tolerates 0.18 m/s GPS jitter) & handlebar vs pocket scaling
    @Test
    fun testCarriageModeAwareErtsReplay_pinsStationaryJitterAndAdaptsGyroScaling_tstFil028_4() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 5_000_000L
        var east = 0.0

        // 0..19: Bike Front Pocket riding East at 6 m/s with strong thigh yaw alias
        for (i in 0 until 20) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            val yaw = 0.65 * kotlin.math.sin(2.0 * PI * 0.25 * i)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = 6.0,
                    bearingDeg = 90.0,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = yaw,
                    carriageMode = PhoneCarriageMode.BIKE_FRONT_POCKET
                )
            )
            tMs += 1000L
            east += 6.0
        }

        // 20..34: Stationary bakery counter with 0.18 m/s GPS drift jitter (above 0.05 raw threshold, below 0.25!)
        val (stopLat, stopLon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
        for (i in 20 until 35) {
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = stopLat,
                    longitude = stopLon,
                    speedMps = 0.18,
                    bearingDeg = null,
                    accuracyMeters = 18.0,
                    worldYawRateRadPerSec = 0.0,
                    carriageMode = PhoneCarriageMode.STATIONARY
                )
            )
            tMs += 1000L
        }

        // 35..54: Remount cycling in Bike Handlebar mode
        for (i in 35 until 55) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(east, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = 7.0,
                    bearingDeg = 90.0,
                    accuracyMeters = 3.0,
                    worldYawRateRadPerSec = 0.0,
                    carriageMode = PhoneCarriageMode.BIKE_HANDLEBAR
                )
            )
            tMs += 1000L
            east += 7.0
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals(55, smoothed.size)
        for (i in 20 until 35) {
            assertEquals("Explicit STATIONARY mode must pin 0.18 m/s GPS jitter at $i", 0.0, smoothed[i].speedMps, 1e-9)
            assertEquals("Explicit STATIONARY stop deltaDistance must be 0.0 at $i", 0.0, smoothed[i].deltaDistanceMeters, 1e-9)
        }
        assertTrue("Handlebar post-stop riding speed must recover cleanly", smoothed[50].speedMps > 5.0)
    }

    // TST-FIL-032.3: 118s indoor bakery stop with +35.66 m departure jump pins [E, N] to 0.00 m Haversine drift
    @Test
    fun testStationaryIndoorStop_pinsCoordinatesToZeroHaversineDriftAcross118sOutage_tstFil032_3() {
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 6_000_000L
        var east = 0.0
        val rideSpeed = 6.0

        // 0..99: Arrival riding East at 6.0 m/s (acc = 3.0 m)
        for (i in 0 until 100) {
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

        // 100..217: 118-second indoor bakery counter stop (speed = 0.00 m/s, acc = 48.5 m > 20 m)
        val stopEast = east
        for (i in 100 until 218) {
            val indoorJitterE = stopEast + (i - 100) * 0.25
            val indoorJitterN = if (i % 2 == 0) 15.0 else -15.0
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(indoorJitterE, indoorJitterN, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = 0.0,
                    bearingDeg = null,
                    accuracyMeters = 48.5,
                    worldYawRateRadPerSec = 0.0,
                    wheelSpeedMps = null,
                    carriageMode = PhoneCarriageMode.STATIONARY
                )
            )
            tMs += 1000L
        }

        // 218..317: Departure with +35.66 m building-exit GNSS jump, then 6.0 m/s riding East
        east = stopEast + 35.66
        for (i in 218 until 318) {
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
        assertEquals(318, smoothed.size)

        var indoorHaversineMeters = 0.0
        for (i in 100 until 218) {
            assertEquals("Indoor stop speed must stay 0.0 m/s at $i", 0.0, smoothed[i].speedMps, 1e-9)
            assertEquals("Indoor stop deltaDistanceMeters must stay 0.0 m at $i", 0.0, smoothed[i].deltaDistanceMeters, 1e-9)
            if (i > 100) {
                indoorHaversineMeters += haversineMeters(
                    smoothed[i - 1].latitude,
                    smoothed[i - 1].longitude,
                    smoothed[i].latitude,
                    smoothed[i].longitude
                )
            }
        }
        assertEquals("Indoor stop Haversine coordinate drift must be 0.00 m", 0.0, indoorHaversineMeters, 1e-6)
        assertEquals(smoothed[100].latitude, smoothed[217].latitude, 1e-12)
        assertEquals(smoothed[100].longitude, smoothed[217].longitude, 1e-12)

        // Pre-stop & post-stop moving speeds and GNSS re-acquisition remain healthy (TST-FIL-027.3 parity)
        for (i in 5 until 95) {
            assertTrue("Pre-stop moving speed must stay > 4.0 m/s at $i (was ${smoothed[i].speedMps})", smoothed[i].speedMps > 4.0)
        }
        for (i in 223 until 313) {
            assertTrue("Post-stop moving speed must stay > 4.0 m/s at $i (was ${smoothed[i].speedMps})", smoothed[i].speedMps > 4.0)
        }
    }

    // TST-FIL-032.3: Direct smoothSnapshots() also pins consecutive stationary [E, N] coordinates to 0.00 m drift
    @Test
    fun testSmoothSnapshots_pinsConsecutiveStationaryPositionsDirectly_tstFil032_3() {
        val ekf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 0.0, initialHeadingRad = 0.0, recordSnapshots = false)
        val snapshots = mutableListOf<OdometrySnapshot>()
        val stationaryMask = BooleanArray(12) { it in 2..8 }

        for (i in 0 until 12) {
            val ts = 7_000_000L + i * 1000L
            // Intentionally call default non-stationary predict to verify smoothSnapshots() pinning alone enforces 0.00 m
            ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = ts)
            val priorState = doubleArrayOf(ekf.eastMeters, ekf.northMeters, ekf.speedMps, ekf.headingRad, ekf.gyroBiasRadPerSec)
            val priorCov = ekf.getCovarianceCopy()
            val jac = ekf.getTransitionJacobianCopy()
            if (stationaryMask[i]) {
                ekf.applyZeroVelocityUpdate(ts)
            } else {
                val targetE = if (i < 2) i * 5.0 else 45.0 + (i - 9) * 5.0
                ekf.updateGnssPosition(targetE, 0.0, 3.0, ts, isStationary = false)
                ekf.updateSpeed(5.0, 0.5, ts)
            }
            val postState = doubleArrayOf(ekf.eastMeters, ekf.northMeters, ekf.speedMps, ekf.headingRad, ekf.gyroBiasRadPerSec)
            val postCov = ekf.getCovarianceCopy()
            snapshots.add(OdometrySnapshot(ts, 1.0, priorState, priorCov, jac, postState, postCov))
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSnapshots(snapshots, originLat, originLon, stationaryMask)
        for (i in 3..8) {
            val dM = haversineMeters(smoothed[i - 1].latitude, smoothed[i - 1].longitude, smoothed[i].latitude, smoothed[i].longitude)
            assertEquals("Direct smoothSnapshots() must pin consecutive stationary step $i to 0.00 m", 0.0, dM, 1e-6)
        }
    }

    // REQ-FIL-036.3 / TST-FIL-036.3
    @Test
    fun testPostUnpinTeleportAnd1TickGlitch_suppresses573MpsCatchUpOvershoot_tstFil036_3() {
        val samples = mutableListOf<RawOdometrySample>()
        val baseTs = 8_000_000L
        var eastM = 0.0

        // t = 0..23 s: 23-second cold-start pinned standstill at origin (0.0 m/s)
        for (sec in 0..23) {
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(0.0, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = baseTs + sec * 1_000L,
                    latitude = lat,
                    longitude = lon,
                    speedMps = 0.0,
                    accuracyMeters = 15.0,
                    worldYawRateRadPerSec = 0.0,
                    carriageMode = PhoneCarriageMode.STATIONARY
                )
            )
        }

        // t = 24..60 s: unpin teleport (+9.34 m at t=24 with isolated 1-tick 9.34 m/s geometric glitch,
        // +12.20 m teleport at t=31), surrounded by steady ~2.0 m/s easy running
        for (sec in 24..60) {
            val stepM = when (sec) {
                24 -> 9.34
                31 -> 12.20
                else -> 2.0
            }
            eastM += stepM
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(eastM, 0.0, originLat, originLon)
            val recordedSpeed = if (sec == 24) 9.34 else 2.0 + (sec % 3) * 0.1
            samples.add(
                RawOdometrySample(
                    timestampMs = baseTs + sec * 1_000L,
                    latitude = lat,
                    longitude = lon,
                    speedMps = recordedSpeed,
                    bearingDeg = 90.0,
                    accuracyMeters = 10.0,
                    worldYawRateRadPerSec = 0.0,
                    carriageMode = PhoneCarriageMode.RUN_WAIST_HIP
                )
            )
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples)
        assertEquals(samples.size, smoothed.size)

        val maxSmoothedSpeed = smoothed.maxOf { it.speedMps }
        assertTrue(
            "Post-unpin ERTS must suppress synthetic catch-up spike (< 3.5 m/s, was $maxSmoothedSpeed m/s)",
            maxSmoothedSpeed < 3.5
        )
        // Steady running segment (t=35..60) must remain accurately near ~2.1 m/s
        val steadyMean = smoothed.subList(35, 61).map { it.speedMps }.average()
        assertTrue("Steady easy-run speed must remain near 2.1 m/s (was $steadyMean)", steadyMean in 1.7..2.5)
    }

    // REQ-FIL-036.3 / TST-FIL-036.3
    @Test
    fun testGenuineLaunchAndMultiSecondSprint_notClippedByTeleportOrGlitchGates_tstFil036_3() {
        val samples = mutableListOf<RawOdometrySample>()
        val baseTs = 9_000_000L
        var eastM = 0.0

        // 3 s standstill, genuine launch (2.0 -> 2.2 -> 2.3), steady jog, then multi-second sprint (2.0 -> 3.5 -> 5.0 -> 6.0 -> 6.0 -> 5.8 -> 4.5 -> 2.5)
        val speedProfile = doubleArrayOf(
            0.0, 0.0, 0.0,
            2.0, 2.2, 2.3, 2.2, 2.1, 2.0, 2.0,
            3.5, 5.0, 6.0, 6.0, 6.0, 5.8, 4.5, 3.0, 2.2, 2.0
        )
        for (i in speedProfile.indices) {
            val v = speedProfile[i]
            eastM += v * 1.0
            val (lat, lon) = ExtendedRtsOdometrySmoother.enuToLatLng(eastM, 0.0, originLat, originLon)
            samples.add(
                RawOdometrySample(
                    timestampMs = baseTs + i * 1_000L,
                    latitude = lat,
                    longitude = lon,
                    speedMps = v,
                    bearingDeg = 90.0,
                    accuracyMeters = 4.0,
                    worldYawRateRadPerSec = 0.0,
                    carriageMode = if (v <= 0.05) PhoneCarriageMode.STATIONARY else PhoneCarriageMode.RUN_WAIST_HIP
                )
            )
        }

        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples)
        assertEquals(samples.size, smoothed.size)
        assertTrue("Genuine launch at t=3 must not be clipped (was ${smoothed[3].speedMps})", smoothed[3].speedMps >= 1.6)
        val peakSprintSpeed = smoothed.maxOf { it.speedMps }
        assertTrue("Genuine multi-second 6.0 m/s sprint must preserve peak >= 5.5 m/s (was $peakSprintSpeed)", peakSprintSpeed >= 5.5)
    }

    // REQ-FIL-045.2 / TST-FIL-045.2: Segment-independent pause-gap decoupling, out-and-back heading,
    // 1-sample & no-GNSS post-pause segments, direct smoothSnapshots() defense, and Long overflow guards.
    @Test
    fun testPauseGapDecouplingAndOutAndBackHeading_zeroPauseDistanceAndNoCrossPauseBleed_TST_FIL_045_2() {
        // 1. Overflow-safe isTimestampPauseGap boundary checks
        org.junit.Assert.assertFalse(ExtendedRtsOdometrySmoother.isTimestampPauseGap(1_000L, 6_000L))
        assertTrue(ExtendedRtsOdometrySmoother.isTimestampPauseGap(1_000L, 6_001L))
        assertTrue(ExtendedRtsOdometrySmoother.isTimestampPauseGap(Long.MIN_VALUE, 0L))
        org.junit.Assert.assertFalse(ExtendedRtsOdometrySmoother.isTimestampPauseGap(Long.MAX_VALUE - 1_000L, Long.MAX_VALUE))
        org.junit.Assert.assertFalse(ExtendedRtsOdometrySmoother.isTimestampPauseGap(10_000L, 5_000L))

        // 2. Out-and-back multi-pause ride:
        //    Seg 0 (0..59): 60 s East at 5.0 m/s (0 -> 300 m)
        //    4 s auto-pause (explicit isPauseGap = true, dt = 5 s) -> Seg 1 (60..79): 20 s East at 5.0 m/s (300 -> 400 m)
        //    17 s auto-pause (dt = 17 s, resume 0.88 m/s) -> Seg 2 (80..94): 15 s East
        //    134 s bakery auto-pause (dt = 134 s, resume 3.84 m/s heading WEST back toward origin!) -> Seg 3 (95..144)
        //    610 s (> 10 min) manual pause -> Seg 4 (145..145): single-sample trailing segment
        //    20 s pause -> Seg 5 (146..150): no-GNSS post-pause segment (lat/lon = null)
        val samples = mutableListOf<RawOdometrySample>()
        var tMs = 10_000_000L
        var eastM = 0.0

        fun addSample(
            speed: Double,
            bearingDeg: Double?,
            hasGps: Boolean = true,
            isPauseGap: Boolean = false
        ) {
            val (lat, lon) = if (hasGps) {
                ExtendedRtsOdometrySmoother.enuToLatLng(eastM, 0.0, originLat, originLon)
            } else {
                null to null
            }
            samples.add(
                RawOdometrySample(
                    timestampMs = tMs,
                    latitude = lat,
                    longitude = lon,
                    speedMps = speed,
                    bearingDeg = bearingDeg,
                    accuracyMeters = if (hasGps) 3.5 else null,
                    worldYawRateRadPerSec = 0.0,
                    isPauseGap = isPauseGap
                )
            )
        }

        // Seg 0: 60 s East at 5.0 m/s
        for (i in 0 until 60) {
            if (i > 0) {
                tMs += 1_000L
                eastM += 5.0
            }
            addSample(5.0, 90.0)
        }
        val pauseIdx4s = samples.size
        tMs += 5_000L // 4 s auto-pause flagged via isPauseGap (dt = 5 s)
        for (i in 0 until 20) {
            if (i > 0) {
                tMs += 1_000L
                eastM += 5.0
            }
            addSample(5.0, 90.0, isPauseGap = (i == 0))
        }
        val pauseIdx17s = samples.size
        tMs += 17_000L // 17 s auto-pause (lock-bike)
        for (i in 0 until 15) {
            val spd = if (i == 0) 0.88 else 4.0
            if (i > 0) {
                tMs += 1_000L
                eastM += spd
            }
            addSample(spd, 90.0)
        }
        val pauseIdx134s = samples.size
        tMs += 134_000L // 134 s bakery auto-pause; rider turns around heading WEST back toward origin!
        for (i in 0 until 50) {
            val spd = if (i == 0) 3.84 else 5.0
            if (i > 0) {
                tMs += 1_000L
                eastM -= spd // Moving West (heading = +-PI, bearing = 270 deg)
            }
            addSample(spd, 270.0)
        }
        val pauseIdx610s = samples.size
        tMs += 610_000L // > 10 min manual pause -> 1-sample segment
        addSample(4.5, 270.0)

        val pauseIdxNoGps = samples.size
        tMs += 20_000L // 20 s pause -> no-GNSS post-pause segment
        for (i in 0 until 5) {
            if (i > 0) tMs += 1_000L
            addSample(3.0, null, hasGps = false)
        }

        // Call with explicit initialHeadingRad = 0.0 (East for Seg 0 only!)
        val smoothed = ExtendedRtsOdometrySmoother.smoothSamples(samples, initialHeadingRad = 0.0)
        assertEquals("Output cardinality must stay 1:1 with input", samples.size, smoothed.size)
        assertTrue(
            "All smoothed outputs must stay finite",
            smoothed.all { it.latitude.isFinite() && it.longitude.isFinite() && it.speedMps.isFinite() && it.deltaDistanceMeters.isFinite() }
        )

        // Every pause-resume sample must emit strictly 0.0 m distance (never v * dt_pause!)
        for (pIdx in listOf(pauseIdx4s, pauseIdx17s, pauseIdx134s, pauseIdx610s, pauseIdxNoGps)) {
            assertEquals("Pause boundary at index $pIdx must emit 0.0 m deltaDistanceMeters", 0.0, smoothed[pIdx].deltaDistanceMeters, 1e-12)
        }

        // Out-and-back Seg 3 must resolve initial heading WEST (~+-PI, NOT 0.0 East!)
        val seg3InitialHeadingErr = abs(UnicycleOdometryEkf.normalizeAngle(smoothed[pauseIdx134s].headingRad - PI))
        assertTrue(
            "Seg 3 turnaround heading must resolve West (~PI, not 180-deg inverted East; err=$seg3InitialHeadingErr rad)",
            seg3InitialHeadingErr < 0.35
        )
        assertTrue(
            "Seg 3 post-pause speed must stay healthy (~5 m/s, was ${smoothed[pauseIdx134s + 10].speedMps})",
            smoothed[pauseIdx134s + 10].speedMps in 4.0..6.0
        )

        // Zero cross-pause bleed: Seg 0 smoothed independently must match Seg 0 inside multi-pause output identically
        val seg0Independent = ExtendedRtsOdometrySmoother.smoothSamples(samples.subList(0, pauseIdx4s), initialHeadingRad = 0.0)
        for (i in seg0Independent.indices) {
            assertEquals("Seg 0 speed bleed at $i", seg0Independent[i].speedMps, smoothed[i].speedMps, 1e-9)
            assertEquals("Seg 0 deltaDist bleed at $i", seg0Independent[i].deltaDistanceMeters, smoothed[i].deltaDistanceMeters, 1e-9)
        }

        // 3. Direct smoothSnapshots() defense-in-depth with 134 s timestamp gap & explicit pauseGapMask
        val ekf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 5.0, initialHeadingRad = 0.0, recordSnapshots = false)
        val snaps = mutableListOf<OdometrySnapshot>()
        val snapTimes = longArrayOf(1_000L, 2_000L, 3_000L, 137_000L, 138_000L, 143_000L, 144_000L)
        for (i in snapTimes.indices) {
            val dt = if (i == 0) 1.0 else (snapTimes[i] - snapTimes[i - 1]) / 1000.0
            ekf.predict(dtSec = dt.coerceAtMost(1.0), gyroYawRateRadPerSec = 0.0, timestampMs = snapTimes[i])
            val priorS = doubleArrayOf(ekf.eastMeters, ekf.northMeters, ekf.speedMps, ekf.headingRad, ekf.gyroBiasRadPerSec)
            val priorC = ekf.getCovarianceCopy()
            val jac = ekf.getTransitionJacobianCopy()
            val spd = if (i < 3) 5.0 else 3.84
            ekf.updateSpeed(spd, 0.25, snapTimes[i])
            val postS = doubleArrayOf(ekf.eastMeters, ekf.northMeters, ekf.speedMps, ekf.headingRad, ekf.gyroBiasRadPerSec)
            val postC = ekf.getCovarianceCopy()
            snaps.add(OdometrySnapshot(snapTimes[i], dt, priorS, priorC, jac, postS, postC))
        }
        val explicitPauseMask = booleanArrayOf(false, false, false, false, false, true, false) // idx 5 is 5 s explicit pause
        val directSmoothed = ExtendedRtsOdometrySmoother.smoothSnapshots(snaps, originLat, originLon, pauseGapMask = explicitPauseMask)
        assertEquals("Direct smoothSnapshots() must emit 0.0 m at 134 s timestamp pause", 0.0, directSmoothed[3].deltaDistanceMeters, 1e-12)
        assertEquals("Direct smoothSnapshots() must emit 0.0 m at explicit pauseGapMask row", 0.0, directSmoothed[5].deltaDistanceMeters, 1e-12)
        // Decoupled backward RTS (C_k = 0): index 2 smoothed speed must equal its forward posterior exactly
        assertEquals("Direct smoothSnapshots() must decouple backward RTS across pause at idx 2", snaps[2].statePosterior[2], directSmoothed[2].speedMps, 1e-12)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
        return 2.0 * ExtendedRtsOdometrySmoother.EARTH_RADIUS_METERS * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    }
}
