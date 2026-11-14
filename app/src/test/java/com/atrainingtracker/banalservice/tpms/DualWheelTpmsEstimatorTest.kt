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

import com.atrainingtracker.trainingtracker.database.WheelPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DualWheelTpmsEstimator] (REQ-SEN-030.1, TST-SEN-030.1, ATT-7753).
 */
class DualWheelTpmsEstimatorTest {

    private lateinit var estimator: DualWheelTpmsEstimator

    @Before
    fun setUp() {
        estimator = DualWheelTpmsEstimator()
    }

    @Test
    fun testBaselineLock_at300m_locksCorrectRatio() {
        assertFalse(estimator.isStartLocked)

        // Feed 140 steps of 2.0 m (280 m total)
        for (i in 0 until 140) {
            val alert = estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 5.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
            assertNull("No alert during baseline acquisition", alert)
            assertFalse(estimator.isStartLocked)
        }

        // Feed 15 more steps (30 m -> 310 m total)
        for (i in 0 until 15) {
            estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 5.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
        }

        assertTrue("Baseline ratio must lock at >= 300m", estimator.isStartLocked)
        assertEquals(1.0, estimator.startRatio, 0.001)
    }

    @Test
    fun testDualWheelRatio_rearAndFrontPunctures_detectedWithin250m() {
        // 1. Lock baseline at 300 m with rho_start = 1.0
        for (i in 0 until 150) {
            estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 6.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
        }
        assertTrue(estimator.isStartLocked)

        // 2. Simulate rear puncture: rear circumference drops by 1.0%, so rear revs = 1.01
        var rearAlert: DualWheelTpmsEstimator.DualWheelTpmsAlert? = null
        var distanceToAlert = 0.0

        for (i in 0 until 125) { // up to 250 m (125 * 2.0 m)
            val alert = estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.010, // +1.0% revolution increase on rear
                speedMps = 6.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
            distanceToAlert += 2.0
            if (alert != null) {
                rearAlert = alert
                break
            }
        }

        assertNotNull("Rear puncture alert must trigger within 250m", rearAlert)
        assertTrue(rearAlert is DualWheelTpmsEstimator.DualWheelTpmsAlert.RearPuncture)
        assertEquals(WheelPosition.REAR, rearAlert!!.position)
        assertTrue("Detection distance ($distanceToAlert m) must be <= 250m", distanceToAlert <= 250.0)

        // 3. Reset session and test Front Puncture
        estimator.resetSession()
        assertFalse(estimator.isStartLocked)

        // Lock baseline again
        for (i in 0 until 150) {
            estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 6.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
        }
        assertTrue(estimator.isStartLocked)

        // Simulate front puncture: front circumference drops by 1.0%, so front revs = 1.010, rear = 1.0
        var frontAlert: DualWheelTpmsEstimator.DualWheelTpmsAlert? = null
        distanceToAlert = 0.0

        for (i in 0 until 125) {
            val alert = estimator.processStep(
                deltaRevsFront = 1.010, // +1.0% revs on front -> ratio drops
                deltaRevsRear = 1.0,
                speedMps = 6.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
            distanceToAlert += 2.0
            if (alert != null) {
                frontAlert = alert
                break
            }
        }

        assertNotNull("Front puncture alert must trigger within 250m", frontAlert)
        assertTrue(frontAlert is DualWheelTpmsEstimator.DualWheelTpmsAlert.FrontPuncture)
        assertEquals(WheelPosition.FRONT, frontAlert!!.position)
        assertTrue("Detection distance ($distanceToAlert m) must be <= 250m", distanceToAlert <= 250.0)
    }

    @Test
    fun testGating_corneringTurnSamples_rejectedWithZeroAlerts() {
        // Lock baseline
        for (i in 0 until 150) {
            estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 6.0,
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
        }
        assertTrue(estimator.isStartLocked)

        // Simulate high yaw rate (15 deg/s cornering / hairpin) with severe differential revolutions
        for (i in 0 until 100) {
            val alert = estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.05, // 5% turn differential
                speedMps = 6.0,
                yawRateRadPerSec = Math.toRadians(15.0), // 15 deg/s turn
                stepDistanceMeters = 2.0
            )
            assertNull("Turn samples with high yaw-rate must be discarded with zero alerts", alert)
        }
        assertFalse(estimator.rearAlertFired)
        assertFalse(estimator.frontAlertFired)
    }

    @Test
    fun testGating_lowSpeed_rejectedWithZeroAlerts() {
        // Under 3.0 m/s (e.g. 2.0 m/s) should be rejected
        for (i in 0 until 200) {
            val alert = estimator.processStep(
                deltaRevsFront = 1.0,
                deltaRevsRear = 1.0,
                speedMps = 2.0, // Low speed
                yawRateRadPerSec = 0.0,
                stepDistanceMeters = 2.0
            )
            assertNull(alert)
        }
        assertFalse("Baseline should not lock at speeds < 3.0 m/s", estimator.isStartLocked)
    }

    @Test
    fun testProcessTelemetry_cumulativeDistanceTracking() {
        // Ingest telemetry via cumulative distances
        var alert: DualWheelTpmsEstimator.DualWheelTpmsAlert? = null
        var frontDist = 1000.0
        var rearDist = 1000.0

        // Lock baseline (300m / 2m = 150 ticks)
        for (i in 0 until 160) {
            frontDist += 2.105
            rearDist += 2.105
            alert = estimator.processTelemetry(
                frontDistanceMeters = frontDist,
                rearDistanceMeters = rearDist,
                speedMps = 5.0,
                yawRateRadPerSec = 0.0
            )
            assertNull(alert)
        }
        assertTrue(estimator.isStartLocked)

        // Inject rear slow puncture
        for (i in 0 until 120) {
            frontDist += 2.105
            rearDist += 2.105 * 1.010 // Rear wheel rotates 1% faster
            alert = estimator.processTelemetry(
                frontDistanceMeters = frontDist,
                rearDistanceMeters = rearDist,
                speedMps = 5.0,
                yawRateRadPerSec = 0.0
            )
            if (alert != null) break
        }

        assertNotNull(alert)
        assertTrue(alert is DualWheelTpmsEstimator.DualWheelTpmsAlert.RearPuncture)
    }
}
