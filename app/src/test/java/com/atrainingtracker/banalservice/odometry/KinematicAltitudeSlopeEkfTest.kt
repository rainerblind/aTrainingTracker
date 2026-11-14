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

/**
 * Unit test suite for [KinematicAltitudeSlopeEkf] verifying 2-State constant slope tracking,
 * standstill drift clamping ($dh = 0, Q_g = 0$), 3-State thermal drift absorption, and Huber gating
 * (REQ-FIL-016.1, REQ-FIL-016.2, TST-FIL-016.1, ATT-6279).
 */
class KinematicAltitudeSlopeEkfTest {

    private lateinit var ekf: KinematicAltitudeSlopeEkf

    @Before
    fun setUp() {
        ekf = KinematicAltitudeSlopeEkf()
    }

    /**
     * TST-FIL-016.1a: Constant +8% slope tracking at 5 m/s.
     * Over 60 seconds (total distance = 300m, climb = 24m), estimated altitude and slope converge cleanly.
     */
    @Test
    fun testConstantSlopeTracking_reachesTargetSlopeAndAltitude() {
        val initialAlt = 100.0
        val targetSlope = 0.08 // +8%
        val speedMps = 5.0
        val dtSec = 1.0

        var trueAlt = initialAlt
        ekf.updateBarometer(trueAlt, isStandstill = false)

        repeat(60) {
            trueAlt += targetSlope * speedMps * dtSec
            ekf.predict(dtSec, speedMps)
            ekf.updateBarometer(trueAlt, isStandstill = false)
        }

        assertEquals("Final altitude should match 124m within 0.2m", 124.0, ekf.altitude, 0.2)
        assertEquals("Estimated slope should match 8% within 0.5%", targetSlope, ekf.slope, 0.005)
        assertEquals("Slope percent should be ~8%", 8.0, ekf.slopePercent, 0.5)
        assertEquals("Vertical speed should be ~0.4 m/s", 0.4, ekf.getVerticalSpeedMps(speedMps), 0.03)
        assertEquals("VAM should be ~1440 m/h", 1440.0, ekf.getVamMph(speedMps), 100.0)
    }

    /**
     * TST-FIL-016.1b: Standstill drift clamping ($v = 0$).
     * When stationary, weather front / temperature drift (+10m over 60s) must NOT accumulate false climb.
     */
    @Test
    fun testStandstillClamping_producesZeroClimbAndNoSlopeOscillation() {
        val initialAlt = 500.0
        ekf.updateBarometer(initialAlt, isStandstill = true)

        var driftingBaro = initialAlt
        repeat(60) {
            driftingBaro += 0.166 // +10m total drift
            ekf.predict(1.0, 0.0) // Standstill v=0
            ekf.updateBarometer(driftingBaro, isStandstill = true)

            assertEquals("Ground altitude must remain clamped to initial altitude", initialAlt, ekf.altitude, 1e-6)
            assertEquals("Slope must remain 0 at standstill", 0.0, ekf.slope, 1e-6)
            assertEquals("Vertical speed must be 0 m/s at standstill", 0.0, ekf.getVerticalSpeedMps(0.0), 1e-6)
            assertEquals("VAM must be 0 m/h at standstill", 0.0, ekf.getVamMph(0.0), 1e-6)
        }
    }

    /**
     * TST-FIL-016.1c: 3-State thermal drift absorption anchored by DEM.
     * In 3-State mode on flat terrain (0%), pocket warming drift (+0.2 m/min) is absorbed into baroBias,
     * while elevation remains smooth and anchored to 200m DEM reference.
     */
    @Test
    fun testThreeStateMode_isolatesThermalWeatherDrift_anchoredByDem() {
        ekf.setMode(true)
        val trueAlt = 200.0
        val speedMps = 5.0
        val dtSec = 1.0

        ekf.updateReferenceAltitude(trueAlt)
        ekf.updateBarometer(trueAlt, isStandstill = false)

        var baroDriftAlt = trueAlt
        val driftRateMps = 0.2 / 60.0 // +0.2 m/min

        for (sec in 1..120) {
            baroDriftAlt += driftRateMps
            ekf.predict(dtSec, speedMps)
            ekf.updateBarometer(baroDriftAlt, isStandstill = false)

            if (sec % 10 == 0) {
                // Periodic 10s DEM anchor
                ekf.updateReferenceAltitude(trueAlt)
            }
        }

        // True altitude was constant 200m; total drift was 0.4m
        assertEquals("Estimated altitude should remain close to 200m DEM", trueAlt, ekf.altitude, 0.3)
        assertTrue("baroBias should absorb positive drift", ekf.baroBias > 0.1)
        assertEquals("Slope should remain near 0% on flat road", 0.0, ekf.slope, 0.01)
    }

    /**
     * TST-FIL-016.1d: Huber innovation down-weighting on gross outlier spike.
     */
    @Test
    fun testHuberDownweighting_rejectsGrossBarometricSpike() {
        val baseline = 300.0
        ekf.updateBarometer(baseline, isStandstill = false)

        // 10s warmup
        repeat(10) {
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(baseline, isStandstill = false)
        }

        // Sudden +50m transient pressure spike (e.g. pocket slam)
        ekf.predict(1.0, 5.0)
        ekf.updateBarometer(baseline + 50.0, isStandstill = false)

        // Huber gating should prevent altitude from jumping +50m
        assertTrue(
            "Altitude after +50m spike should be heavily damped (was ${ekf.altitude})",
            ekf.altitude < baseline + 5.0
        )
    }
}
