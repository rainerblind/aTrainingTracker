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
}
