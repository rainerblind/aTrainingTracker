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

    /**
     * Finding 5: dtSec clamping to MAX_DT_SEC (5.0s).
     * Even if a huge dtSec (e.g. 1800s after pause) is passed, predict() clamps propagation step.
     */
    @Test
    fun testPredict_clampsLargeDtSec() {
        val initialAlt = 200.0
        ekf.updateBarometer(initialAlt, isStandstill = false)

        // Predict with 1800s dtSec at 5 m/s
        ekf.predict(1800.0, 5.0)

        // Without clamp, dtSec=1800 at 5 m/s would give deltaDist=9000m and p00 > 800,000.
        // With MAX_DT_SEC (5.0s), deltaDist is 25m, so p00 is bounded (< 10.0).
        assertTrue("p00 growth must be bounded by MAX_DT_SEC (was ${ekf.p00})", ekf.p00 < 10.0)
    }

    /**
     * Finding 2: Thread-safety across concurrent predict and update operations.
     */
    @Test
    fun testConcurrency_predictAndUpdateWithoutCorruption() {
        ekf.updateBarometer(100.0, isStandstill = false)
        val threads = mutableListOf<Thread>()
        var errorOccurred = false

        repeat(4) { t ->
            val thread = Thread {
                try {
                    repeat(200) { i ->
                        if (t % 2 == 0) {
                            ekf.predict(1.0, 5.0)
                        } else {
                            ekf.updateBarometer(100.0 + (i % 10), isStandstill = false)
                        }
                    }
                } catch (e: Exception) {
                    errorOccurred = true
                }
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }
        assertFalse("Concurrent EKF predict and update must not throw exceptions", errorOccurred)
        assertTrue("Altitude must be a valid finite number", !ekf.altitude.isNaN() && !ekf.altitude.isInfinite())
        assertTrue("Covariance p00 must be positive", ekf.p00 > 0.0)
    }

    /**
     * TST-FIL-022.1: 3-State Baro-before-Ref +130.4 m weather-pressure offset at standstill (ATT-8575).
     */
    @Test
    fun testThreeStateBaroBeforeRef_anchorsAndAbsorbs130mWeatherOffsetAtStandstill() {
        ekf.setMode(true)

        // Barometer arrives first at standstill with +130.4 m synoptic offset (568.4 m vs 438.0 m true)
        repeat(10) {
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(568.4, isStandstill = true)
        }
        assertTrue("Unanchored 3-state p00 must exceed 0.25 (was ${ekf.p00})", ekf.p00 > 0.25)

        // Single DEM anchor arrives at 438.0 m
        val reanchored = ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue("Initial DEM arrival after barometer must report reanchored=true", reanchored)
        assertEquals("Altitude must anchor at 438.0 m DEM", 438.0, ekf.altitude, 0.5)
        assertEquals("baroBias must absorb +130.4 m weather offset", 130.4, ekf.baroBias, 0.5)

        // Subsequent 5-Hz standstill & moving barometer readings at 568.4 m must NOT pull altitude back
        repeat(10) {
            ekf.predict(0.2, 0.0)
            ekf.updateBarometer(568.4, isStandstill = true)
        }
        repeat(10) {
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(568.4, isStandstill = false)
        }

        assertEquals("Altitude must stay anchored at 438.0 m", 438.0, ekf.altitude, 0.5)
        assertEquals("baroBias must remain ~130.4 m", 130.4, ekf.baroBias, 0.5)
    }

    /**
     * TST-FIL-022.2: 3-State Ref-before-Baro +130.4 m offset, 2-State p00 preservation, and reset/setMode hygiene.
     */
    @Test
    fun testThreeStateRefBeforeBaro_andTwoStateGuard_preservesP00AndClearsFlags() {
        // 1. Ref-before-Baro in 3-state mode
        ekf.setMode(true)
        val initialRefReanchored = ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertFalse("First-ever initialization via DEM has no prior jump", initialRefReanchored)
        assertTrue(ekf.hasReferenceAnchor)

        repeat(10) {
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(568.4, isStandstill = false)
        }
        repeat(10) {
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(568.4, isStandstill = true)
        }
        assertEquals("Altitude must remain near 438.0 m without Huber pull", 438.0, ekf.altitude, 0.5)
        assertEquals("baroBias must absorb +130.4 m weather offset", 130.4, ekf.baroBias, 0.5)

        // 2. reset() & setMode() clear flags cleanly; 2-state preserves p00 = varBaro
        ekf.reset()
        assertFalse(ekf.isInitialized)
        assertFalse(ekf.hasReferenceAnchor)
        assertFalse(ekf.hasBarometerSample)

        ekf.setMode(false)
        ekf.updateBarometer(200.0, isStandstill = false)
        assertEquals("2-state mode must preserve p00 = varBaro (0.25)", KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, ekf.p00, 1e-6)
        ekf.predict(1800.0, 5.0)
        assertTrue("2-state p00 must stay < 10.0 after MAX_DT_SEC clamp", ekf.p00 < 10.0)
    }

    /**
     * Stage 6 Bounce 1/3 regression: GPS-before-DEM coarse initial anchor followed by standstill baro warmup
     * must NOT freeze baroBias against subsequent async DEM fixes.
     */
    @Test
    fun testThreeStateGpsBeforeDem_recoversFromCoarseGpsAnchorWithoutBiasFreeze() {
        ekf.setMode(true)

        // 1. Standstill baro warmup (+130.4 m weather offset)
        repeat(5) {
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(568.4, isStandstill = true)
        }

        // 2. Coarse GPS anchor arrives (+15 m error above true 438.0 m)
        val reanchored = ekf.updateReferenceAltitude(453.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS)
        assertTrue(reanchored)
        assertEquals(453.0, ekf.altitude, 0.5)

        // 3. 15s standstill baro updates must NOT collapse p22 below p00
        repeat(15) {
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(568.4, isStandstill = true)
        }
        assertTrue("p22 (${ekf.p22}) must not collapse below p00 (${ekf.p00}) during standstill", ekf.p22 >= ekf.p00)

        // 4. Async DEM fixes arrive at 438.0 m across standstill & moving phases
        repeat(5) {
            ekf.predict(1.0, 0.0)
            ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            ekf.updateBarometer(568.4, isStandstill = true)
        }
        repeat(15) {
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(568.4, isStandstill = false)
            if (it % 3 == 0) {
                ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            }
        }

        assertEquals("Altitude must recover from coarse GPS anchor to 438.0 m DEM", 438.0, ekf.altitude, 1.0)
        assertEquals("baroBias must converge to +130.4 m", 130.4, ekf.baroBias, 1.0)
    }

    /**
     * Stage 6 Bounce 1/3 regression: Initialized 2-State -> 3-State transition via setMode(true)
     * must restore unanchored p00 = DEFAULT_UNANCHORED_ALT_VAR, zero p01/p10, and re-anchor cleanly.
     */
    @Test
    fun testTwoStateToThreeStateTransition_resetsUnanchoredP00AndReanchorsCleanly() {
        ekf.setMode(false)
        ekf.updateBarometer(200.0, isStandstill = false)
        repeat(10) { i ->
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(201.0 + i, isStandstill = false)
        }
        assertTrue("2-state climb must produce non-zero p01 covariance", ekf.p01 != 0.0)

        // Mid-ride switch from 2-State to 3-State
        ekf.setMode(true)
        assertEquals(KinematicAltitudeSlopeEkf.DEFAULT_UNANCHORED_ALT_VAR, ekf.p00, 1e-6)
        assertEquals(0.0, ekf.p01, 1e-9)
        assertEquals(0.0, ekf.p10, 1e-9)
        assertEquals(0.0, ekf.baroBias, 1e-9)
        assertEquals(100.0, ekf.p22, 1e-6)
        assertFalse(ekf.hasReferenceAnchor)

        val currentBaro = ekf.altitude
        val reanchored = ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue(reanchored)
        assertEquals(438.0, ekf.altitude, 1e-6)
        assertEquals(currentBaro - 438.0, ekf.baroBias, 1e-6)
        assertEquals(0.0, ekf.p12, 1e-9)
        assertEquals(0.0, ekf.p21, 1e-9)
    }

    // TST-FIL-023.2: Pre-stop -21% slope decays cleanly to 0% during sustained standstill while preserving baroBias
    @Test
    fun testStandstillSlopeDecay_clearsPreStopSlopeAndPreservesBaroBias_tstFil023_2() {
        ekf.setMode(true)
        var trueAlt = 480.0
        val weatherOffset = 130.4
        ekf.updateReferenceAltitude(trueAlt, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(trueAlt + weatherOffset, isStandstill = false, speedMps = 6.0)

        // Ride downhill -20% at 6.0 m/s for 20s
        repeat(20) {
            trueAlt += -0.20 * 6.0
            ekf.predict(1.0, 6.0)
            ekf.updateBarometer(trueAlt + weatherOffset, isStandstill = false, speedMps = 6.0)
        }
        assertTrue("Pre-stop slope must be strongly negative (was ${ekf.slopePercent}%)", ekf.slopePercent < -12.0)

        val altAtStop = ekf.altitude
        val biasAtStop = ekf.baroBias

        // Sustained 15s bakery stop (speed = 0.0 m/s)
        repeat(15) {
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(trueAlt + weatherOffset, isStandstill = true, speedMps = 0.0)
        }

        assertEquals("Altitude must stay clamped during stop", altAtStop, ekf.altitude, 1e-6)
        assertEquals("baroBias must remain preserved during stop", biasAtStop, ekf.baroBias, 0.5)
        assertTrue("Standstill slope must decay near 0% (was ${ekf.slopePercent}%)", kotlin.math.abs(ekf.slopePercent) < 1.0)
    }

    // TST-FIL-023.3: Low-speed roll-away (0.85 m/s) over flat DEM damps baro chatter without +29% spike
    @Test
    fun testLowSpeedBaroChatter_dampedWithout29PercentSpike_tstFil023_3() {
        ekf.setMode(true)
        val flatAlt = 442.0
        ekf.updateReferenceAltitude(flatAlt, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(flatAlt, isStandstill = false, speedMps = 5.0)

        // Simulate 1000m prior riding so p11 would have inflated without MAX_SLOPE_VARIANCE
        repeat(200) {
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(flatAlt, isStandstill = false, speedMps = 5.0)
        }

        // Roll away at walking speed (0.85 m/s) for 30s with +/- 0.35 m 5-Hz baro noise
        var maxAbsSlopePct = 0.0
        for (sec in 0 until 30) {
            ekf.predict(1.0, 0.85)
            repeat(5) { sub ->
                val noise = 0.35 * kotlin.math.sin((sec * 5 + sub) * 0.9)
                ekf.updateBarometer(flatAlt + noise, isStandstill = false, speedMps = 0.85)
                maxAbsSlopePct = maxOf(maxAbsSlopePct, kotlin.math.abs(ekf.slopePercent))
            }
        }

        assertTrue("Low-speed slope must not spike to +/-29% (max was $maxAbsSlopePct%)", maxAbsSlopePct < 8.0)
        assertTrue("Flat altitude must remain stable (was ${ekf.altitude})", kotlin.math.abs(ekf.altitude - flatAlt) < 1.5)
    }
}
