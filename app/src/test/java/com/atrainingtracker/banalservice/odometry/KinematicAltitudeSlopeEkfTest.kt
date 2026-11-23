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

    // TST-FIL-023.2 (Bounce 1/3): Standstill slope decay symmetrically decays p12/p21 in 3-state mode
    @Test
    fun testStandstillSlopeDecay_decaysP12SymmetricallyInThreeStateMode_tstFil023_2() {
        ekf.setMode(true)
        var alt = 450.0
        ekf.updateReferenceAltitude(alt, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(alt + 50.0, isStandstill = false, speedMps = 6.0)

        repeat(15) {
            alt += 0.6
            ekf.predict(1.0, 6.0)
            ekf.updateBarometer(alt + 50.0, isStandstill = false, speedMps = 6.0)
        }
        val p12BeforeStop = ekf.p12
        assertTrue("Moving 3-state climb should produce non-zero p12 (was $p12BeforeStop)", kotlin.math.abs(p12BeforeStop) > 1e-6)
        assertEquals(p12BeforeStop, ekf.p21, 1e-12)

        repeat(15) {
            ekf.predict(1.0, 0.0)
        }
        assertEquals(0.0, ekf.slope, 1e-6)
        assertEquals("p12 and p21 must remain symmetric after standstill decay", ekf.p12, ekf.p21, 1e-12)
        assertTrue(
            "p12 must decay symmetrically during standstill slope decay (before=$p12BeforeStop, after=${ekf.p12})",
            kotlin.math.abs(ekf.p12) < kotlin.math.abs(p12BeforeStop) * 0.1
        )
    }

    // TST-FIL-026.2: Cyclic sign-reversing ±1.2 m jitter inflates adaptive varBaro to damp slope <= ±2%, while steady +0.83 m/s climbs stay unattenuated
    @Test
    fun testAdaptiveJitterVariance_dampsCyclicOscillationsAndPreservesSteadyClimb_tstFil026_2() {
        assertEquals(0.25, KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, 1e-9)

        // 1. Cyclic sign-reversing ±1.2 m oscillations at 6.0 m/s (2-State mode)
        ekf.setMode(false)
        ekf.updateBarometer(438.0, isStandstill = false, speedMps = 6.0)
        assertEquals(0.25, ekf.p00, 1e-6)

        var maxAbsSlopeAfterAttack = 0.0
        for (i in 1..40) {
            ekf.predict(1.0, 6.0)
            val sign = if (i % 2 == 0) 1.0 else -1.0
            ekf.updateBarometer(438.0 + 1.2 * sign, isStandstill = false, speedMps = 6.0)
            if (i >= 10) {
                maxAbsSlopeAfterAttack = maxOf(maxAbsSlopeAfterAttack, kotlin.math.abs(ekf.slopePercent))
            }
        }
        assertTrue("Adaptive jitter variance must scale up under cyclic reversals (was ${ekf.adaptiveBaroJitterVar})", ekf.adaptiveBaroJitterVar > 5.0)
        assertTrue("Post-attack slope under ±1.2 m oscillations must stay <= 2% (max was $maxAbsSlopeAfterAttack%)", maxAbsSlopeAfterAttack <= 2.0)

        // 2. Steady monotonic +0.83 m/s climb at 5.0 m/s must NOT trigger jitter inflation
        ekf.reset()
        ekf.setMode(false)
        var alt = 438.0
        ekf.updateBarometer(alt, isStandstill = false, speedMps = 5.0)
        repeat(30) {
            alt += 0.83
            ekf.predict(1.0, 5.0)
            ekf.updateBarometer(alt, isStandstill = false, speedMps = 5.0)
        }
        assertEquals("Steady monotonic climb must keep adaptiveBaroJitterVar at 0.0", 0.0, ekf.adaptiveBaroJitterVar, 1e-6)
        assertEquals("Steady climb altitude must converge without lag", 438.0 + 30 * 0.83, ekf.altitude, 1.5)
        assertTrue("Steady +16.6% climb slope must converge accurately (was ${ekf.slopePercent}%)", ekf.slopePercent in 13.5..19.5)
    }

    // REQ-FIL-037.1 / TST-FIL-037.1: Coherent DEM/GPS variance hierarchy, baro/standstill slope decoupling, 3x3 Joseph SPD, and DEM-only slope preservation
    @Test
    fun testCoherentVarianceRetuningAndBaroSlopeDecouplingWithJosephSpd_tstFil037_1() {
        assertEquals(25.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM, 1e-9)
        assertEquals(64.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS, 1e-9)
        assertTrue(
            "DEFAULT_VAR_GPS must exceed DEFAULT_VAR_DEM to preserve REQ-FIL-020.1 hierarchy",
            KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS > KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM
        )
        assertEquals(0.25, KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, 1e-9)

        // 1. 3-State moving flat road with barometer + DEM: abrupt 13m & 21m DEM jumps decouple slope and preserve symmetric SPD covariance
        ekf.setMode(true)
        ekf.updateReferenceAltitude(440.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)

        // Guard against invalid refAltM / varRef inputs
        assertFalse(ekf.updateReferenceAltitude(Double.NaN, 25.0))
        assertFalse(ekf.updateReferenceAltitude(440.0, 0.0))
        assertFalse(ekf.updateReferenceAltitude(440.0, -5.0))
        assertFalse(ekf.updateReferenceAltitude(440.0, Double.POSITIVE_INFINITY))

        repeat(20) {
            ekf.predict(1.0, 2.0)
            ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
            ekf.updateReferenceAltitude(440.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        }
        val altBeforeJump = ekf.altitude
        val slopeBeforeJump = ekf.slope

        // Inject -13 m and +21 m DEM jumps while barometer stays flat
        ekf.predict(1.0, 2.0)
        ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
        ekf.updateReferenceAltitude(427.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertEquals("Slope must stay decoupled from DEM jump when barometer is active", slopeBeforeJump, ekf.slope, 1e-6)
        assertTrue("Altitude pull on -13 m DEM jump must be heavily damped (delta=${ekf.altitude - altBeforeJump})", kotlin.math.abs(ekf.altitude - altBeforeJump) < 0.5)
        assertSymmetricSpd3x3(ekf)

        ekf.predict(1.0, 2.0)
        ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
        ekf.updateReferenceAltitude(448.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue("Slope must stay within +/-1% on flat terrain despite +21 m DEM jump (was ${ekf.slopePercent}%)", kotlin.math.abs(ekf.slopePercent) < 1.0)
        assertTrue("Altitude must stay within 0.5 m of 440 m (was ${ekf.altitude})", kotlin.math.abs(ekf.altitude - 440.0) < 0.5)
        assertSymmetricSpd3x3(ekf)

        // 2. Barometer-free DEM-only climb (!hasBarometerSample) must still estimate positive slope
        ekf.reset()
        ekf.setMode(true)
        var demClimb = 440.0
        ekf.updateReferenceAltitude(demClimb, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        repeat(40) {
            demClimb += 0.4 // +8% climb at 5.0 m/s
            ekf.predict(1.0, 5.0)
            ekf.updateReferenceAltitude(demClimb, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            assertSymmetricSpd3x3(ekf)
        }
        assertFalse(ekf.hasBarometerSample)
        assertTrue("Barometer-free DEM-only climb must produce positive slope > 3% (was ${ekf.slopePercent}%)", ekf.slopePercent > 3.0)
    }

    // REQ-FIL-037.2 / TST-FIL-037.2: Post-anchor step-discrepancy gating, tracker advancement, Huber variance inflation, Ref-before-Baro/standstill tracking, and high-precision anchor bypass
    @Test
    fun testStepDiscrepancyGatingTrackerAdvancementHuberAndHighPrecisionGuard_tstFil037_2() {
        // 1. Transient 440 -> 427 -> 440 m and 440 -> 461 -> 440 m DEM bounces with flat barometer
        ekf.setMode(true)
        ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
        assertTrue(ekf.updateReferenceAltitude(440.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM))

        repeat(15) {
            ekf.predict(1.0, 2.0)
            ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
            ekf.updateReferenceAltitude(440.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        }
        val p00Baseline = ekf.p00

        // Bounce sequence: -13 m, back to 440, +21 m for 2 ticks (tests both step gate & Huber variance inflation), back to 440
        val demBounces = listOf(427.0, 440.0, 461.0, 461.0, 440.0)
        for (demVal in demBounces) {
            ekf.predict(1.0, 2.0)
            ekf.updateBarometer(570.4, isStandstill = false, speedMps = 2.0)
            ekf.updateReferenceAltitude(demVal, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            assertTrue("Bounce DEM=$demVal must keep altitude within 0.5 m of 440 m (was ${ekf.altitude})", kotlin.math.abs(ekf.altitude - 440.0) < 0.5)
            assertTrue("Bounce DEM=$demVal must not cause slope spike (was ${ekf.slopePercent}%)", kotlin.math.abs(ekf.slopePercent) < 1.0)
            assertSymmetricSpd3x3(ekf)
        }
        assertTrue(
            "p00 must not collapse during multi-tick DEM outliers (baseline=$p00Baseline, after=${ekf.p00})",
            ekf.p00 >= p00Baseline * 0.5
        )

        // 2. Ref-before-Baro seeding & standstill baro drift tracking: first post-anchor DEM jump must gate cleanly
        ekf.reset()
        ekf.setMode(true)
        ekf.updateReferenceAltitude(440.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(570.4, isStandstill = true, speedMps = 0.0) // Ref-before-Baro seeds lastRefBaroAltM
        // Standstill baro drifts +0.3 m while stationary
        repeat(5) { i ->
            ekf.predict(1.0, 0.0)
            ekf.updateBarometer(570.4 + 0.06 * (i + 1), isStandstill = true, speedMps = 0.0)
        }
        // First post-anchor DEM sample jumps -15 m while baro only moved +0.3 m -> must gate!
        ekf.predict(1.0, 2.0)
        ekf.updateBarometer(570.7, isStandstill = false, speedMps = 2.0)
        ekf.updateReferenceAltitude(425.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue(
            "Ref-before-Baro first post-anchor -15 m DEM jump must be gated (alt=${ekf.altitude})",
            kotlin.math.abs(ekf.altitude - 440.0) < 0.5
        )

        // 3. High-precision known-location/manual calibration anchor (varRef = 0.5 <= 1.0) bypasses step gating
        val altBeforeCalibration = ekf.altitude
        ekf.updateReferenceAltitude(452.0, varRef = 0.5)
        assertTrue(
            "High-precision anchor (varRef=0.5 <= 1.0) must bypass step gating and pull altitude immediately (before=$altBeforeCalibration, after=${ekf.altitude})",
            ekf.altitude - altBeforeCalibration > 6.0
        )
    }

    // REQ-FIL-040.2 / TST-FIL-040.2: Corrupted high-dispersion barometer (varBaro > 16 m^2) never vetoes true flat DEM,
    // clears lastRefBaroAltM so post-recovery ticks stay clean, and preserves healthy-baro REQ-FIL-037.2 step gating.
    @Test
    fun testCorruptedBaroDispersion_doesNotVetoFlatDemAndRecoversCleanly_TST_FIL_040_2() {
        val ekf = KinematicAltitudeSlopeEkf(is3StateMode = true)
        ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        ekf.updateBarometer(473.0, varBaro = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, isStandstill = false, speedMps = 6.0)

        // Establish steady flat baseline on healthy barometer
        repeat(5) {
            ekf.predict(1.0, 6.0)
            ekf.updateBarometer(473.0, varBaro = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, isStandstill = false, speedMps = 6.0)
            ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        }

        // Inject +10 m monotonic pocket-aliasing baro bias with high dispersion varBaro = 1000.0 (> 16.0)
        // on flat 438.0 m DEM: DEM step-gate MUST NOT inflate rDem to veto flat 438.0 m reference.
        for (sec in 1..20) {
            ekf.predict(1.0, 6.0)
            ekf.updateBarometer(473.0 + 0.5 * sec, varBaro = 1000.0, isStandstill = false, speedMps = 6.0)
            ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            assertTrue(
                "Flat DEM must stabilize altitude within 1.5 m despite high-var biased baro (sec=$sec, alt=${ekf.altitude})",
                kotlin.math.abs(ekf.altitude - 438.0) <= 1.5
            )
            assertSymmetricSpd3x3(ekf)
        }

        // Recovery tick: barometer drops back to clean 473.0 m with healthy varBaro = 0.25.
        // Because lastRefBaroAltM was cleared during corruption, this recovery tick MUST NOT trigger
        // a false step discrepancy against the corrupted 483.0 m baseline.
        ekf.predict(1.0, 6.0)
        ekf.updateBarometer(473.0, varBaro = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, isStandstill = false, speedMps = 6.0)
        ekf.updateReferenceAltitude(438.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue(
            "Post-recovery altitude must remain anchored near 438.0 m (alt=${ekf.altitude})",
            kotlin.math.abs(ekf.altitude - 438.0) <= 1.5
        )

        // Subsequent healthy tick: abrupt -15 m DEM grid jump while healthy baro stays flat MUST still be gated (REQ-FIL-037.2)
        val altBeforeDemJump = ekf.altitude
        ekf.predict(1.0, 6.0)
        ekf.updateBarometer(473.0, varBaro = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO, isStandstill = false, speedMps = 6.0)
        ekf.updateReferenceAltitude(423.0, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
        assertTrue(
            "Healthy barometer after recovery must restore REQ-FIL-037.2 DEM jump gating (before=$altBeforeDemJump, after=${ekf.altitude})",
            kotlin.math.abs(ekf.altitude - altBeforeDemJump) < 0.8
        )
    }

    private fun assertSymmetricSpd3x3(filter: KinematicAltitudeSlopeEkf) {
        assertEquals("p01/p10 must be symmetric", filter.p01, filter.p10, 1e-12)
        assertEquals("p02/p20 must be symmetric", filter.p02, filter.p20, 1e-12)
        assertEquals("p12/p21 must be symmetric", filter.p12, filter.p21, 1e-12)
        assertTrue("p00 must be positive (${filter.p00})", filter.p00 > 0.0)
        assertTrue("p11 must be positive (${filter.p11})", filter.p11 > 0.0)
        assertTrue("p22 must be positive (${filter.p22})", filter.p22 > 0.0)
        val minor01 = filter.p00 * filter.p11 - filter.p01 * filter.p10
        val minor02 = filter.p00 * filter.p22 - filter.p02 * filter.p20
        val minor12 = filter.p11 * filter.p22 - filter.p12 * filter.p21
        val det = filter.p00 * (filter.p11 * filter.p22 - filter.p12 * filter.p21) -
            filter.p01 * (filter.p10 * filter.p22 - filter.p12 * filter.p20) +
            filter.p02 * (filter.p10 * filter.p21 - filter.p11 * filter.p20)
        assertTrue("2x2 minor01 must be positive ($minor01)", minor01 > 0.0)
        assertTrue("2x2 minor02 must be positive ($minor02)", minor02 > 0.0)
        assertTrue("2x2 minor12 must be positive ($minor12)", minor12 > 0.0)
        assertTrue("3x3 determinant must be positive ($det)", det > 0.0)
    }
}
