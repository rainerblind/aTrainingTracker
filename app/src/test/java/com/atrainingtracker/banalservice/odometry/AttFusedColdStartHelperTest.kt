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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Unit tests for [AttFusedColdStartHelper] covering cold-start corroborated pin release,
 * indoor oscillating multipath rejection, raw-chord heading seeding, and calibrated bounded
 * Doppler speed variance (REQ-FIL-036.1, REQ-FIL-036.2, TST-FIL-036.1, TST-FIL-036.2, ATT-9158).
 */
class AttFusedColdStartHelperTest {

    // REQ-FIL-036.1 / TST-FIL-036.1
    @Test
    fun testDirectedOutdoorMotion_releasesColdStartPinAndSeedsSouthHeading_tstFil036_1() {
        val helper = AttFusedColdStartHelper()

        // t = 0: stationary anchor
        val d0 = helper.evaluateStep(
            eastM = 0.0, northM = 0.0, accuracyMeters = 18.9, timestampMs = 1_000L,
            hasTrustedGpsSpeed = true, gpsSpeedMps = 0.0, bearingDeg = null,
            isWheelMoving = false, isStationary = true, isStationaryByClassifier = false, activeMotionModeOrSteps = false
        )
        assertFalse(d0.shouldReleasePin)
        assertNull(d0.seedHeadingRad)

        // t = 1..3 s: steady running South at 2.0 m/s under 16 m cold-start accuracy
        val d1 = helper.evaluateStep(
            eastM = 0.0, northM = -2.0, accuracyMeters = 17.5, timestampMs = 2_000L,
            hasTrustedGpsSpeed = true, gpsSpeedMps = 2.0, bearingDeg = null,
            isWheelMoving = false, isStationary = false, isStationaryByClassifier = false, activeMotionModeOrSteps = true
        )
        assertFalse("1 moving fix must not release pin yet", d1.shouldReleasePin)
        assertTrue("Unanchored heading must decouple position-velocity before seed", d1.decouplePositionVelocity)

        val d2 = helper.evaluateStep(
            eastM = 0.1, northM = -4.0, accuracyMeters = 16.0, timestampMs = 3_000L,
            hasTrustedGpsSpeed = true, gpsSpeedMps = 2.0, bearingDeg = null,
            isWheelMoving = false, isStationary = false, isStationaryByClassifier = false, activeMotionModeOrSteps = true
        )
        assertFalse("2 samples (< 3) must wait for directed 2-interval confirmation", d2.shouldReleasePin)

        val d3 = helper.evaluateStep(
            eastM = -0.1, northM = -6.0, accuracyMeters = 15.0, timestampMs = 4_000L,
            hasTrustedGpsSpeed = true, gpsSpeedMps = 2.0, bearingDeg = null,
            isWheelMoving = false, isStationary = false, isStationaryByClassifier = false, activeMotionModeOrSteps = true
        )
        assertTrue("3 directed moving fixes (>= 3.0 m, straightness >= 0.55) must release cold-start pin", d3.shouldReleasePin)
        assertNotNull("Directed >= 4.0 m progression must emit one-shot raw-chord heading seed", d3.seedHeadingRad)
        assertEquals(-PI / 2.0, d3.seedHeadingRad!!, 0.12)
        assertFalse("Seeded tick must allow coupled updates", d3.decouplePositionVelocity)
    }

    // REQ-FIL-036.1 / TST-FIL-036.1
    @Test
    fun testIndoorHandheldOscillatingMultipath_rejectedByAntiOscillationGuard_tstFil036_1() {
        val helper = AttFusedColdStartHelper()

        // Oscillating indoor multipath around origin (±6 m East/West) with spurious 1.5 m/s Doppler
        val bounces = listOf(
            Pair(5.5, 0.5),
            Pair(-5.5, -0.5),
            Pair(5.0, 0.2),
            Pair(-5.0, -0.3),
            Pair(4.5, 0.4),
            Pair(-4.5, -0.2)
        )
        for ((idx, pos) in bounces.withIndex()) {
            val decision = helper.evaluateStep(
                eastM = pos.first,
                northM = pos.second,
                accuracyMeters = 15.0,
                timestampMs = 1_000L + idx * 1_000L,
                hasTrustedGpsSpeed = true,
                gpsSpeedMps = 1.5,
                bearingDeg = null,
                isWheelMoving = false,
                isStationary = false,
                isStationaryByClassifier = false,
                activeMotionModeOrSteps = true
            )
            assertFalse(
                "Oscillating indoor multipath at step $idx must NOT release cold-start pin",
                decision.shouldReleasePin
            )
            assertNull(
                "Oscillating indoor multipath at step $idx must NOT seed bogus heading",
                decision.seedHeadingRad
            )
        }
    }

    // REQ-FIL-036.2 / TST-FIL-036.2
    @Test
    fun testCalibratedDopplerSpeedVariance_boundsTrustedFixesAndPreservesUntrustedDistrust_tstFil036_2() {
        // Trusted GNSS fixes (0 < acc <= 20.0 m) stay bounded in [0.25, 2.25]
        assertEquals(0.25, AttFusedColdStartHelper.calibratedDopplerSpeedVariance(3.0), 1e-6)
        assertEquals(0.64, AttFusedColdStartHelper.calibratedDopplerSpeedVariance(10.0), 1e-6)
        val coldStartVar = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(18.9)
        assertTrue("18.9 m cold-start fix variance must stay <= 2.25 (was $coldStartVar)", coldStartVar in 2.0..2.25)
        assertEquals(2.25, AttFusedColdStartHelper.calibratedDopplerSpeedVariance(20.0), 1e-6)

        // Untrusted GNSS fixes (acc > 20.0 m) retain coarse distrust variance max(0.5, (acc * 0.25)^2)
        val degraded25 = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(25.0)
        assertEquals(39.0625, degraded25, 1e-6)
        val degraded40 = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(40.0)
        assertEquals(100.0, degraded40, 1e-6)

        // Non-finite / non-positive fallback
        assertEquals(1.0, AttFusedColdStartHelper.calibratedDopplerSpeedVariance(Double.NaN), 1e-6)
        assertEquals(1.0, AttFusedColdStartHelper.calibratedDopplerSpeedVariance(0.0), 1e-6)
        assertTrue(abs(AttFusedColdStartHelper.calibratedDopplerSpeedVariance(-5.0) - 1.0) < 1e-6)
    }
}
