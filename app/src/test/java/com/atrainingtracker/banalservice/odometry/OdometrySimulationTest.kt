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
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-fidelity deterministic simulation tests for [UnicycleOdometryEkf]
 * (REQ-FIL-009, TST-FUSION-002.1, ATT-131).
 *
 * Scenarios:
 * 1. 45-second curved tunnel GNSS outage dead-reckoning (< 1.0% distance error, smooth exit).
 * 2. Dense canopy switchback immunity (gating degraded sigma = 30m fixes, preserving true hairpin curve).
 * 3. Multi-source wheel speed fusion under rear-wheel sprint slip and brake lockup.
 */
class OdometrySimulationTest {

    private lateinit var ekf: UnicycleOdometryEkf

    @Before
    fun setUp() {
        ekf = UnicycleOdometryEkf(
            initialEast = 0.0,
            initialNorth = 0.0,
            initialSpeed = 8.0,
            initialHeadingRad = 0.0, // Moving East
            initialGyroBias = 0.0
        )
    }

    @Test
    fun testCurvedTunnelOutage_deadReckoning_accumulatedDistanceErrorLessThan1Percent() {
        // Tunnel parameters:
        // Speed = 8.0 m/s constant.
        // Duration = 45 seconds total (360 meters ground truth distance).
        // 0 to 15s: straight East (heading = 0.0) -> 120m
        // 15 to 30s: 90 deg left curve (turn rate = (PI/2)/15 rad/s) -> 120m arc
        // 30 to 45s: straight North (heading = PI/2) -> 120m
        val speedTrue = 8.0
        val dt = 0.10 // 10 Hz telemetry loop
        val totalDurationSec = 45.0
        val totalSteps = (totalDurationSec / dt).toInt()

        var previousEast = ekf.eastMeters
        var previousNorth = ekf.northMeters
        var accumulatedOdoDistance = 0.0

        for (step in 0 until totalSteps) {
            val t = step * dt

            // Determine true yaw-rate
            val yawRate = if (t in 15.0..30.0) {
                (PI / 2.0) / 15.0 // 90 deg turn over 15 seconds
            } else {
                0.0
            }

            // Time propagation with gyro yaw-rate
            ekf.predict(dtSec = dt, gyroYawRateRadPerSec = yawRate)

            // Speed update from wheel sensor (no GPS in tunnel!)
            ekf.updateSpeed(
                SpeedMeasurement(
                    speedMps = speedTrue,
                    variance = 0.05,
                    timestampMs = (t * 1000).toLong(),
                    source = SpeedSource.REAR_WHEEL
                )
            )

            val dE = ekf.eastMeters - previousEast
            val dN = ekf.northMeters - previousNorth
            accumulatedOdoDistance += sqrt(dE * dE + dN * dN)
            previousEast = ekf.eastMeters
            previousNorth = ekf.northMeters
        }

        val groundTruthDistance = 360.0
        val distanceErrorRatio = abs(accumulatedOdoDistance - groundTruthDistance) / groundTruthDistance

        assertTrue(
            "Curved tunnel distance error (${distanceErrorRatio * 100}%) must be < 1.0%. Accumulated: $accumulatedOdoDistance m, Truth: $groundTruthDistance m",
            distanceErrorRatio < 0.010
        )

        // Verify exit heading is North (PI / 2 radians)
        val headingErrorRad = abs(UnicycleOdometryEkf.normalizeAngle(ekf.headingRad - PI / 2.0))
        val headingErrorDeg = Math.toDegrees(headingErrorRad)
        assertTrue(
            "Exit heading error ($headingErrorDeg deg) must be < 1.0 deg",
            headingErrorDeg < 1.0
        )

        // Exit transition: At t = 45.1s, first GNSS fix re-acquired at true exit
        // True exit coordinates:
        // Segment 1 (East 120m) -> (120, 0)
        // Segment 2 (Radius R = 120 / (PI/2) = 76.394m arc towards North) -> East = 120 + R, North = R
        // Segment 3 (North 120m) -> East = 120 + R, North = R + 120
        val rTurn = 120.0 / (PI / 2.0)
        val trueExitEast = 120.0 + rTurn
        val trueExitNorth = rTurn + 120.0

        val eastBeforeGps = ekf.eastMeters
        val northBeforeGps = ekf.northMeters

        // Ingest first GNSS fix
        val gpsAccepted = ekf.updateGnssPosition(
            eastMeters = trueExitEast,
            northMeters = trueExitNorth,
            accuracyMeters = 3.0
        )
        assertTrue("Exit GNSS fix must be accepted", gpsAccepted)

        val teleportJumpMeters = sqrt(
            (ekf.eastMeters - eastBeforeGps) * (ekf.eastMeters - eastBeforeGps) +
                (ekf.northMeters - northBeforeGps) * (ekf.northMeters - northBeforeGps)
        )

        // Verify smooth transition without teleportation spikes
        assertTrue(
            "Exit transition jump ($teleportJumpMeters m) must remain smooth and bounded (< 3.0 m)",
            teleportJumpMeters < 3.0
        )
    }

    @Test
    fun testDenseCanopySwitchbacks_gatesNoisyGps_preservesHairpinCurve() {
        // Simulate a sharp 180 deg hairpin turn at 5 m/s
        ekf.reset(east = 0.0, north = 0.0, speed = 5.0, headingRad = 0.0) // East

        val dt = 0.2
        val hairpinDuration = 6.0
        val yawRate = PI / hairpinDuration // 180 deg in 6s
        val steps = (hairpinDuration / dt).toInt()

        for (i in 0 until steps) {
            ekf.predict(dtSec = dt, gyroYawRateRadPerSec = yawRate)
            ekf.updateSpeed(5.0, variance = 0.05)

            // Dense canopy multipath: GNSS reports noisy coordinates with accuracy = 30m
            // Degraded fixes must be gated out
            val gpsGated = !ekf.updateGnssPosition(
                eastMeters = 10.0, // GPS cuts the corner
                northMeters = 5.0,
                accuracyMeters = 30.0 // > 20m threshold
            )
            assertTrue("Degraded canopy GNSS fix must be gated out", gpsGated)
        }

        // Final heading must be reversed (West, ~ PI rad)
        val finalHeading = abs(UnicycleOdometryEkf.normalizeAngle(ekf.headingRad))
        assertEquals(PI, finalHeading, 0.05)
    }

    @Test
    fun testSprintSlipAndBrakeLockup_huberWeightingRejectsOutliers() {
        ekf.reset(speed = 8.0)
        // Pre-warm speed with dual consistent sensors
        ekf.updateSpeed(8.0, variance = 0.10)
        ekf.updateSpeed(8.0, variance = 0.10)

        val normalSpeedVar = ekf.speedVariance

        // Phase 1: Sprint Slip on rear wheel (14 m/s vs 8 m/s true speed) for 15 steps
        for (i in 0 until 15) {
            ekf.predict(dtSec = 0.1, gyroYawRateRadPerSec = 0.0)
            // Front wheel (ground truth 8 m/s)
            ekf.updateSpeed(SpeedMeasurement(8.0, 0.10, i * 100L, SpeedSource.FRONT_WHEEL))
            // Rear wheel slip (14 m/s)
            ekf.updateSpeed(SpeedMeasurement(14.0, 0.10, i * 100L, SpeedSource.REAR_WHEEL))
        }

        // Speed estimate must stay protected against the slip (< 8.8 m/s)
        assertTrue(
            "Estimated speed during slip (${ekf.speedMps} m/s) must remain close to 8 m/s",
            ekf.speedMps < 8.8
        )

        // Phase 2: Brake lockup on rear wheel (0 m/s vs 8 m/s true speed) for 15 steps
        for (i in 0 until 15) {
            ekf.predict(dtSec = 0.1, gyroYawRateRadPerSec = 0.0)
            // Front wheel (ground truth 8 m/s)
            ekf.updateSpeed(SpeedMeasurement(8.0, 0.10, 2000L + i * 100L, SpeedSource.FRONT_WHEEL))
            // Rear wheel lockup (0 m/s)
            ekf.updateSpeed(SpeedMeasurement(0.0, 0.10, 2000L + i * 100L, SpeedSource.REAR_WHEEL))
        }

        // Speed estimate must stay protected against lockup (> 7.2 m/s)
        assertTrue(
            "Estimated speed during lockup (${ekf.speedMps} m/s) must remain close to 8 m/s",
            ekf.speedMps > 7.2
        )
    }
}
