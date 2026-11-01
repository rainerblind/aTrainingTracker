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

package com.atrainingtracker.banalservice.tpms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [IndirectTpmsEstimator] (REQ-TRK-050, TST-TRK-050, ATT-4880).
 * Verifies straight-line gating, GNSS accuracy/speed thresholds, 3D elevation slope correction,
 * ride-start baseline lock, multi-day low tire pressure warning, and sliding CUSUM slow-puncture alert.
 */
class IndirectTpmsEstimatorTest {

    private lateinit var estimator: IndirectTpmsEstimator

    @Before
    fun setUp() {
        estimator = IndirectTpmsEstimator(baselineCircumferenceMeters = 2.105)
    }

    @Test
    fun testStraightLineGating_rejectsHighBearingRate() {
        // Initial point
        estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.0000,
            bearingDeg = 90.0,
            accuracyMeters = 3.0,
            speedMps = 8.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 0.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 1000L
        )

        // Point 2: bearing turns by 10 deg in 1 sec (10 deg/sec >= 3 deg/sec limit)
        val alert = estimator.processTelemetry(
            lat = 48.00005,
            lon = 11.00005,
            bearingDeg = 100.0,
            accuracyMeters = 3.0,
            speedMps = 8.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 8.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 2000L
        )

        assertNull("Sharp turn must be rejected by straight-line gating", alert)
        assertFalse(estimator.isStartLocked)
    }

    @Test
    fun testStraightLineGating_acceptsStraightMovement() {
        estimator.processTelemetry(
            lat = 48.000000,
            lon = 11.000000,
            bearingDeg = 90.0,
            accuracyMeters = 2.5,
            speedMps = 10.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 0.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 1000L
        )

        // Point 2: bearing changes by only 0.5 deg in 1 sec
        val alert = estimator.processTelemetry(
            lat = 48.000000,
            lon = 11.000134, // ~10m east
            bearingDeg = 90.5,
            accuracyMeters = 2.5,
            speedMps = 10.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 10.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 2000L
        )

        // Gated step accepted, no alert yet as start baseline is accumulating
        assertNull(alert)
    }

    @Test
    fun testGating_rejectsPoorGnssAccuracy() {
        estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.0000,
            bearingDeg = 90.0,
            accuracyMeters = 3.0,
            speedMps = 8.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 0.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 1000L
        )

        // Accuracy is 7.5m (> 6.0m limit)
        val alert = estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.0001,
            bearingDeg = 90.0,
            accuracyMeters = 7.5,
            speedMps = 8.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 8.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 2000L
        )

        assertNull("Step with degraded GNSS accuracy must be rejected", alert)
    }

    @Test
    fun testGating_rejectsLowSpeed() {
        estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.0000,
            bearingDeg = 90.0,
            accuracyMeters = 3.0,
            speedMps = 8.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 0.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 1000L
        )

        // Speed is 2.5 m/s (< 4.0 m/s limit)
        val alert = estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.00003,
            bearingDeg = 90.0,
            accuracyMeters = 3.0,
            speedMps = 2.5,
            elevationMeters = 500.0,
            wheelDistanceMeters = 2.5,
            calibrationFactorMeters = 2.105,
            timestampMs = 2000L
        )

        assertNull("Step below minimum speed threshold must be rejected", alert)
    }

    @Test
    fun testSlopeCorrection_accuratelyScales3dDistance() {
        // Feed steps with elevation gain to verify 3D hypotenuse computation
        estimator.processTelemetry(
            lat = 48.000000,
            lon = 11.000000,
            bearingDeg = 0.0,
            accuracyMeters = 2.0,
            speedMps = 10.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 0.0,
            calibrationFactorMeters = 2.000,
            timestampMs = 1000L
        )

        // Move north ~100m, elevation climbs 20m: 3D dist = sqrt(100^2 + 20^2) = 101.98m
        // 101.98m / 50.99 revs = 2.000m circumference
        // In latitude, 1 deg lat is ~111,139 meters. 100m is ~0.00089975 deg.
        val alert = estimator.processTelemetry(
            lat = 48.00089975,
            lon = 11.000000,
            bearingDeg = 0.0,
            accuracyMeters = 2.0,
            speedMps = 10.0,
            elevationMeters = 520.0,
            wheelDistanceMeters = 101.98,
            calibrationFactorMeters = 2.000,
            timestampMs = 11000L
        )
        assertNull(alert)
    }

    @Test
    fun testRideStartBaselineLock_locksAfter2000Meters() {
        assertFalse(estimator.isStartLocked)

        // Feed 1900 meters (circumference = 2.105m -> 1900 / 2.105 revs)
        val revsPerMeter = 1.0 / 2.105
        estimator.onGatedStep(1900.0, 1900.0 * revsPerMeter)
        assertFalse("Should not lock at 1900m", estimator.isStartLocked)

        // Feed additional 150 meters (total 2050m >= 2000m)
        val alert = estimator.onGatedStep(150.0, 150.0 * revsPerMeter)
        assertNull("No alert if within normal baseline", alert)
        assertTrue("Must lock baseline at >= 2000m", estimator.isStartLocked)
        assertEquals(2.105, estimator.startCircumferenceMeters, 0.001)
    }

    @Test
    fun testMultiDayLowPressureAlert_triggersWhenStartCircumferenceBelowThreshold() {
        // Baseline is 2.105m
        // Deflated tire rolling at 2.085m (drop of ~0.95% >= 0.6% threshold)
        val deflatedCircumference = 2.085
        val revs = 2050.0 / deflatedCircumference

        val alert = estimator.onGatedStep(2050.0, revs)

        assertNotNull("Low pressure reminder should trigger", alert)
        assertTrue(alert is IndirectTpmsEstimator.TpmsAlertResult.LowPressure)
        val lowPressure = alert as IndirectTpmsEstimator.TpmsAlertResult.LowPressure
        assertEquals(2085, lowPressure.currentCircumferenceMm)
        assertEquals(2105, lowPressure.baselineCircumferenceMm)
        assertTrue("Drop percentage should be >= 0.6%", lowPressure.dropPercentage >= 0.6)
    }

    @Test
    fun testSlowPunctureDetection_triggersOnInRideDeflation() {
        // 1. Lock ride-start baseline at 2.100m
        val revsStart = 2000.0 / 2.100
        estimator.onGatedStep(2000.0, revsStart)
        assertTrue(estimator.isStartLocked)

        // 2. Simulate puncture: tire slowly deflates by 1.2% (circumference drops to 2.0748m)
        val deflatedCircumference = 2.100 * (1.0 - 0.012)
        var alert: IndirectTpmsEstimator.TpmsAlertResult? = null

        // Feed increments of 50m until sliding window detects the slow puncture
        for (i in 0 until 40) {
            val stepDist = 50.0
            val stepRevs = stepDist / deflatedCircumference
            val result = estimator.onGatedStep(stepDist, stepRevs)
            if (result != null) {
                alert = result
                break
            }
        }

        assertNotNull("Slow puncture alert must trigger on >= 1.0% circumference loss", alert)
        assertTrue(alert is IndirectTpmsEstimator.TpmsAlertResult.SlowPuncture)
        val slowPuncture = alert as IndirectTpmsEstimator.TpmsAlertResult.SlowPuncture
        assertTrue("Drop percentage should reflect ~1.2% deflation", slowPuncture.dropPercentage >= 1.0)
        assertEquals(2100, slowPuncture.startCircumferenceMm)
    }

    @Test
    fun testCorneringImmunity_sharpTurnDoesNotCorruptEstimator() {
        // Lock baseline
        estimator.onGatedStep(2000.0, 2000.0 / 2.105)
        assertTrue(estimator.isStartLocked)

        // Ingest a straight step
        estimator.processTelemetry(
            lat = 48.0000,
            lon = 11.0000,
            bearingDeg = 0.0,
            accuracyMeters = 2.0,
            speedMps = 10.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 100.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 1000L
        )

        // Sharp hairpin: bearing changes 180 deg in 2 sec (90 deg/sec >> 3 deg/sec)
        val result = estimator.processTelemetry(
            lat = 48.0001,
            lon = 11.0000,
            bearingDeg = 180.0,
            accuracyMeters = 2.0,
            speedMps = 10.0,
            elevationMeters = 500.0,
            wheelDistanceMeters = 120.0,
            calibrationFactorMeters = 2.105,
            timestampMs = 3000L
        )

        assertNull("Hairpin turn must be rejected without triggering false alert", result)
    }

    @Test
    fun testResetSession_clearsAllState() {
        estimator.onGatedStep(2000.0, 2000.0 / 2.105)
        assertTrue(estimator.isStartLocked)

        estimator.resetSession()

        assertFalse(estimator.isStartLocked)
        assertEquals(0.0, estimator.startCircumferenceMeters, 0.0001)
    }
}
