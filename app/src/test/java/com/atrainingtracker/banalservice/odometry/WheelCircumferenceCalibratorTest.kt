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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [WheelCircumferenceCalibrator] (REQ-FIL-010, TST-FUSION-003.1, ATT-131).
 *
 * Verifies:
 * 1. Gating rejection: GNSS accuracy > 6m, speed < 4 m/s, yaw-rate >= 3 deg/s.
 * 2. Interval accumulation threshold (Delta s >= 20m).
 * 3. Dynamic convergence within < 0.3% error over 1,000m of qualified straight riding.
 * 4. Reset functionality and calibrated status flag.
 */
class WheelCircumferenceCalibratorTest {

    private lateinit var calibrator: WheelCircumferenceCalibrator

    @Before
    fun setUp() {
        calibrator = WheelCircumferenceCalibrator(
            initialCircumferenceMeters = 2.000, // 5% initial error from true 2.105m
            initialVariance = 0.05
        )
    }

    @Test
    fun testGatingRejection_accuracyGreaterThan6Meters_rejected() {
        val updated = calibrator.processStep(
            deltaDistance3d = 25.0,
            deltaRevolutions = 25.0 / 2.105,
            gnssAccuracyMeters = 7.5, // Exceeds 6.0m limit
            speedMps = 8.0,
            yawRateRadPerSec = 0.0
        )

        assertFalse("Update must be rejected when GNSS accuracy > 6.0m", updated)
        assertEquals(0, calibrator.updateCount)
        assertEquals(2.000, calibrator.circumferenceMeters, 1e-6)
    }

    @Test
    fun testGatingRejection_speedLessThan4Mps_rejected() {
        val updated = calibrator.processStep(
            deltaDistance3d = 25.0,
            deltaRevolutions = 25.0 / 2.105,
            gnssAccuracyMeters = 3.0,
            speedMps = 3.5, // Below 4.0 m/s limit
            yawRateRadPerSec = 0.0
        )

        assertFalse("Update must be rejected when speed < 4.0 m/s", updated)
        assertEquals(0, calibrator.updateCount)
        assertEquals(2.000, calibrator.circumferenceMeters, 1e-6)
    }

    @Test
    fun testGatingRejection_yawRateGreaterThanOrEqual3DegPerSec_rejected() {
        val updated = calibrator.processStep(
            deltaDistance3d = 25.0,
            deltaRevolutions = 25.0 / 2.105,
            gnssAccuracyMeters = 3.0,
            speedMps = 8.0,
            yawRateRadPerSec = Math.toRadians(3.5) // Exceeds 3.0 deg/s
        )

        assertFalse("Update must be rejected during turns >= 3.0 deg/s", updated)
        assertEquals(0, calibrator.updateCount)
        assertEquals(2.000, calibrator.circumferenceMeters, 1e-6)
    }

    @Test
    fun testIntervalAccumulation_requiresAtLeast20Meters() {
        // Step 1: 15 meters (less than 20m)
        val updated1 = calibrator.processStep(
            deltaDistance3d = 15.0,
            deltaRevolutions = 15.0 / 2.105,
            gnssAccuracyMeters = 3.0,
            speedMps = 8.0,
            yawRateRadPerSec = 0.0
        )
        assertFalse("First step < 20m must accumulate without triggering update", updated1)
        assertEquals(0, calibrator.updateCount)

        // Step 2: 10 meters (total 25m >= 20m)
        val updated2 = calibrator.processStep(
            deltaDistance3d = 10.0,
            deltaRevolutions = 10.0 / 2.105,
            gnssAccuracyMeters = 3.0,
            speedMps = 8.0,
            yawRateRadPerSec = 0.0
        )
        assertTrue("Cumulative distance >= 20m must trigger update", updated2)
        assertEquals(1, calibrator.updateCount)
        assertEquals(25.0, calibrator.totalQualifiedDistanceMeters, 1e-6)
    }

    @Test
    fun testConvergence_within0Point3PercentOver1000Meters() {
        val trueCircumference = 2.105 // Ground truth
        val stepDistance = 10.0 // 10m per step at 8m/s (1.25s)
        val stepRevs = stepDistance / trueCircumference
        val totalDistance = 1000.0
        val steps = (totalDistance / stepDistance).toInt()

        for (i in 0 until steps) {
            calibrator.processStep(
                deltaDistance3d = stepDistance,
                deltaRevolutions = stepRevs,
                gnssAccuracyMeters = 2.5,
                speedMps = 8.0,
                yawRateRadPerSec = 0.0
            )
        }

        val estimated = calibrator.circumferenceMeters
        val errorRatio = abs(estimated - trueCircumference) / trueCircumference

        assertTrue(
            "Estimated circumference ($estimated m) must be within < 0.3% error of true ($trueCircumference m). Actual error: ${errorRatio * 100}%",
            errorRatio < 0.003
        )
        assertTrue(calibrator.isCalibrated)
        assertEquals(1000.0, calibrator.totalQualifiedDistanceMeters, 1e-6)
    }

    @Test
    fun testReset_clearsAccumulatorsAndRestoresState() {
        calibrator.processStep(
            deltaDistance3d = 30.0,
            deltaRevolutions = 30.0 / 2.105,
            gnssAccuracyMeters = 2.0,
            speedMps = 8.0,
            yawRateRadPerSec = 0.0
        )
        assertTrue(calibrator.updateCount > 0)

        calibrator.reset(newCircumferenceMeters = 2.100, newVariance = 0.04)
        assertEquals(2.100, calibrator.circumferenceMeters, 1e-6)
        assertEquals(0.04, calibrator.variance, 1e-6)
        assertEquals(0, calibrator.updateCount)
        assertEquals(0.0, calibrator.totalQualifiedDistanceMeters, 1e-6)
    }
}
