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
import kotlin.math.PI
import kotlin.math.abs

/**
 * Unit tests for [UnicycleOdometryEkf] (REQ-FIL-007, TST-FUSION-001.2, ATT-131).
 *
 * Verifies:
 * 1. Unicycle kinematics prediction (straight and curved trajectories).
 * 2. Multi-source speed updates and Huber innovation outlier down-weighting.
 * 3. 2D GNSS position update gating (accuracy <= 20.0m).
 * 4. Doppler course-over-ground heading anchoring gating (speed > 3.0 m/s).
 * 5. Offline snapshot capture for RTS backward smoothing (ATT-7750).
 */
class UnicycleOdometryEkfTest {

    private lateinit var ekf: UnicycleOdometryEkf

    @Before
    fun setUp() {
        ekf = UnicycleOdometryEkf(
            initialEast = 0.0,
            initialNorth = 0.0,
            initialSpeed = 0.0,
            initialHeadingRad = PI / 2.0, // Pointing North
            initialGyroBias = 0.0
        )
    }

    @Test
    fun testInitialization_setsCorrectInitialStateAndVariances() {
        assertEquals(0.0, ekf.eastMeters, 1e-6)
        assertEquals(0.0, ekf.northMeters, 1e-6)
        assertEquals(0.0, ekf.speedMps, 1e-6)
        assertEquals(PI / 2.0, ekf.headingRad, 1e-6)
        assertEquals(0.0, ekf.bearingDeg, 1e-6) // 0 deg is North
        assertEquals(0.0, ekf.gyroBiasRadPerSec, 1e-6)
        assertTrue(ekf.positionVariance > 0.0)
        assertTrue(ekf.speedVariance > 0.0)
    }

    @Test
    fun testPrediction_straightMotion_propagatesPositionAlongHeading() {
        // Heading is North (pi/2). Set speed = 10 m/s.
        ekf.reset(east = 0.0, north = 0.0, speed = 10.0, headingRad = PI / 2.0)

        // Predict 1.0 second with 0 yaw rate
        ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0)

        // North should be ~10 meters, East should be ~0 meters
        assertEquals(0.0, ekf.eastMeters, 1e-3)
        assertEquals(10.0, ekf.northMeters, 1e-3)
        assertEquals(PI / 2.0, ekf.headingRad, 1e-6)
    }

    @Test
    fun testPrediction_curvedMotion_updatesHeadingAndArc() {
        // Start pointing East (heading = 0.0). Speed = 10 m/s.
        ekf.reset(east = 0.0, north = 0.0, speed = 10.0, headingRad = 0.0)

        // Counter-clockwise turn at 0.1 rad/s for 5 steps of 1.0s (total 0.5 rad)
        val yawRate = 0.1
        for (i in 0 until 5) {
            ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = yawRate)
        }

        // Heading should be 0.5 rad
        assertEquals(0.5, ekf.headingRad, 1e-4)
        // Bike turned left towards North, so North position must be > 0
        assertTrue(ekf.northMeters > 0.0)
        assertTrue(ekf.eastMeters > 0.0)
    }

    @Test
    fun testSpeedUpdate_sequentialFusion_reducesVariance() {
        ekf.reset(speed = 8.0)
        val initialVar = ekf.speedVariance

        // Front wheel speed update
        val frontMeas = SpeedMeasurement(
            speedMps = 8.0,
            variance = 0.20,
            timestampMs = 1000L,
            source = SpeedSource.FRONT_WHEEL
        )
        ekf.updateSpeed(frontMeas)
        val varAfterFront = ekf.speedVariance
        assertTrue(varAfterFront < initialVar)

        // Rear wheel speed update (consistent speed)
        val rearMeas = SpeedMeasurement(
            speedMps = 8.0,
            variance = 0.20,
            timestampMs = 1000L,
            source = SpeedSource.REAR_WHEEL
        )
        ekf.updateSpeed(rearMeas)
        val varAfterRear = ekf.speedVariance

        // Sequential updates should halve remaining measurement uncertainty
        assertTrue(varAfterRear < varAfterFront)
        assertEquals(8.0, ekf.speedMps, 1e-4)
    }

    @Test
    fun testSpeedUpdate_huberRejection_neutralizesSprintSlip() {
        ekf.reset(speed = 8.0)
        // High confidence prior speed 8.0 m/s
        ekf.updateSpeed(8.0, variance = 0.05)
        val speedBeforeSlip = ekf.speedMps

        // Sudden rear wheel slip spike to 18.0 m/s (10 m/s discrepancy)
        val slipMeas = SpeedMeasurement(
            speedMps = 18.0,
            variance = 0.10,
            timestampMs = 2000L,
            source = SpeedSource.REAR_WHEEL
        )
        ekf.updateSpeed(slipMeas)

        // Huber weighting should heavily down-weight the slip outlier
        val speedAfterSlip = ekf.speedMps
        assertTrue(
            "Estimated speed ($speedAfterSlip) must remain close to true speed ($speedBeforeSlip) during slip",
            abs(speedAfterSlip - speedBeforeSlip) < 1.0
        )
    }

    @Test
    fun testGnssPositionUpdate_accuracyGating_rejectsDegradedFixes() {
        ekf.reset(east = 100.0, north = 200.0)

        // Degraded GPS with 35m accuracy (> 20.0m limit)
        val acceptedDegraded = ekf.updateGnssPosition(
            eastMeters = 150.0,
            northMeters = 250.0,
            accuracyMeters = 35.0
        )
        assertFalse("GNSS fixes with accuracy > 20m must be gated out", acceptedDegraded)
        assertEquals(100.0, ekf.eastMeters, 1e-6)
        assertEquals(200.0, ekf.northMeters, 1e-6)

        // High accuracy GPS fix (3.0m <= 20.0m)
        val acceptedGood = ekf.updateGnssPosition(
            eastMeters = 105.0,
            northMeters = 205.0,
            accuracyMeters = 3.0
        )
        assertTrue("GNSS fix with accuracy <= 20m must be accepted", acceptedGood)
        // Position must be pulled towards fix
        assertTrue(ekf.eastMeters > 100.0)
        assertTrue(ekf.northMeters > 200.0)
    }

    @Test
    fun testDopplerHeadingAnchoring_gatedAtLowSpeed() {
        // Stationary / low speed: 1.5 m/s (<= 3.0 m/s threshold)
        ekf.reset(speed = 1.5, headingRad = 0.0)

        val updatedLowSpeed = ekf.updateDopplerHeading(headingRad = PI / 2.0)
        assertFalse("Doppler heading must be gated when speed <= 3.0 m/s", updatedLowSpeed)
        assertEquals(0.0, ekf.headingRad, 1e-6)

        // Qualified speed: 8.0 m/s (> 3.0 m/s threshold)
        ekf.reset(speed = 8.0, headingRad = 0.0)
        val updatedHighSpeed = ekf.updateDopplerHeading(headingRad = PI / 2.0, varianceRad = 0.05)
        assertTrue("Doppler heading must be accepted when speed > 3.0 m/s", updatedHighSpeed)
        // Heading pulled towards PI/2
        assertTrue(ekf.headingRad > 0.0)
    }

    @Test
    fun testSnapshotCapture_recordsValidSnapshotsForRtsSmoother() {
        ekf.reset(speed = 10.0, headingRad = 0.0)
        ekf.clearSnapshots()
        assertEquals(0, ekf.getSnapshots().size)

        ekf.predict(dtSec = 0.1, gyroYawRateRadPerSec = 0.05, timestampMs = 1000L)
        val snapshots = ekf.getSnapshots()
        assertTrue("Snapshots list must not be empty after predict", snapshots.isNotEmpty())

        val snap = snapshots.last()
        assertEquals(1000L, snap.timestampMs)
        assertEquals(0.1, snap.dtSec, 1e-6)
        assertEquals(5, snap.statePrior.size)
        assertEquals(5, snap.covariancePrior.size)
        assertEquals(5, snap.transitionJacobian.size)
        assertEquals(5, snap.statePosterior.size)
        assertEquals(5, snap.covariancePosterior.size)
    }

    // TST-FIL-023.1: ZUPT & stationary GNSS jump gating blocks cold-fix velocity leak
    @Test
    fun testZeroVelocityUpdate_blocksStationaryGnssJumpVelocityLeak_tstFil023_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 0.0, headingRad = PI / 2.0)

        // Simulate 7s of 50-Hz IMU predictions building covariance before cold GNSS fix tightens
        repeat(350) { i ->
            ekf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.0, timestampMs = (i + 1) * 20L)
        }

        ekf.applyZeroVelocityUpdate(timestampMs = 7000L)
        assertEquals(0.0, ekf.speedMps, 1e-9)

        // 25.2 m jump at 16.0 m accuracy while stationary must be rejected
        val acceptedJump = ekf.updateGnssPosition(
            eastMeters = 25.2,
            northMeters = 0.0,
            accuracyMeters = 16.0,
            timestampMs = 7000L,
            isStationary = true
        )
        assertFalse("Stationary 25.2 m cold GNSS jump at 16.0 m accuracy must be gated out", acceptedJump)
        assertEquals(0.0, ekf.eastMeters, 1e-6)
        assertEquals(0.0, ekf.speedMps, 1e-9)

        // Small within-gate stationary convergence fix (4.0 m) must NOT inject speed
        val acceptedSmall = ekf.updateGnssPosition(
            eastMeters = 4.0,
            northMeters = 0.0,
            accuracyMeters = 16.0,
            timestampMs = 8000L,
            isStationary = true
        )
        assertTrue("Small within-gate stationary update should be accepted", acceptedSmall)
        assertEquals("Stationary GNSS update must never leak velocity into speedMps", 0.0, ekf.speedMps, 1e-9)
    }

    // TST-FIL-023.1: Moving GNSS Huber down-weighting preserves tunnel exit while damping gross outliers
    @Test
    fun testGnssPositionUpdate_movingOutlierHuberDamped_tstFil023_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 6.0, headingRad = 0.0)
        ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 1000L)
        val eastBeforeSpike = ekf.eastMeters

        // Gross 120 m multipath outlier with small reported 3.0 m accuracy while moving
        val accepted = ekf.updateGnssPosition(
            eastMeters = eastBeforeSpike + 120.0,
            northMeters = 0.0,
            accuracyMeters = 3.0,
            timestampMs = 2000L,
            isStationary = false
        )
        assertTrue(accepted)
        assertTrue(
            "Huber down-weighting must prevent 120 m outlier teleport (delta=${ekf.eastMeters - eastBeforeSpike})",
            ekf.eastMeters - eastBeforeSpike < 20.0
        )
    }

    // TST-FIL-023.1 (Bounce 1/3): Stationary recovery from off-target 18 m anchor on tight 4 m cluster
    @Test
    fun testStationaryGnssCluster_recoversFromOffTargetAnchorWithoutVelocityLeak_tstFil023_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 0.0, headingRad = PI / 2.0)
        assertTrue(ekf.updateGnssPosition(0.0, 0.0, accuracyMeters = 18.0, timestampMs = 1000L, isStationary = true))

        // Single transient 25 m spike must remain gated out
        assertFalse(ekf.updateGnssPosition(25.0, 0.0, accuracyMeters = 16.0, timestampMs = 2000L, isStationary = true))

        // Two agreeing tight 4.0 m fixes 20 m away (significant tightening <= 0.5 * 18 m) trigger stationary recovery
        assertFalse(ekf.updateGnssPosition(20.0, 0.0, accuracyMeters = 4.0, timestampMs = 3000L, isStationary = true))
        assertTrue(ekf.updateGnssPosition(20.2, 0.1, accuracyMeters = 4.0, timestampMs = 4000L, isStationary = true))
        assertEquals(20.2, ekf.eastMeters, 1e-6)
        assertEquals(0.1, ekf.northMeters, 1e-6)
        assertEquals(0.0, ekf.speedMps, 1e-9)
    }

    // TST-FIL-023.1 (Bounce 1/3): Post-outage moving corridor recovers rapidly after >= 15 s gap without 3-min Huber stall
    @Test
    fun testPostOutageMovingRecovery_convergesRapidlyAfter15sGap_tstFil023_1() {
        val preOutageEkf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 8.0, initialHeadingRad = 0.0)
        assertTrue(preOutageEkf.updateGnssPosition(0.0, 0.0, accuracyMeters = 3.0, timestampMs = 1000L, isStationary = false))

        // Simulate 30s tunnel dead-reckoning with wheel speed keeping p00 small
        for (sec in 1..30) {
            val ts = 1000L + sec * 1000L
            preOutageEkf.predict(1.0, 0.0, timestampMs = ts)
            preOutageEkf.updateSpeed(8.0, variance = 0.05, timestampMs = ts)
        }
        // True exit is 100m West of dead-reckoned estimate at t=32s, 33s
        val trueEast1 = preOutageEkf.eastMeters - 100.0
        // Fix 1 after 31s gap is still Huber-damped (protects against single-epoch multipath spike)
        assertTrue(preOutageEkf.updateGnssPosition(trueEast1, 0.0, accuracyMeters = 3.0, timestampMs = 32_000L, isStationary = false))
        assertTrue(
            "Single first fix after outage should be Huber-damped (< 25m step)",
            abs(preOutageEkf.eastMeters - trueEast1) > 70.0
        )

        // Fix 2 (1s later, moving 8m East along real corridor) confirms post-outage corridor & recovers immediately
        preOutageEkf.predict(1.0, 0.0, timestampMs = 33_000L)
        val trueEast2 = trueEast1 + 8.0
        assertTrue(preOutageEkf.updateGnssPosition(trueEast2, 0.0, accuracyMeters = 3.0, timestampMs = 33_000L, isStationary = false))
        assertTrue(
            "Confirmed post-outage moving cluster must converge rapidly (< 15m error, was ${abs(preOutageEkf.eastMeters - trueEast2)})",
            abs(preOutageEkf.eastMeters - trueEast2) < 15.0
        )
    }

    // TST-FIL-023.1 (Bounce 2/3): reanchorPosition resets position & cross-covariances without collapsing p33/p44
    @Test
    fun testReanchorPosition_resetsCovarianceWithoutCollapsingHeadingOrGyroBias_tstFil023_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 6.0, headingRad = 0.0)
        // 20s of 50-Hz pre-anchor dead-reckoning builds p[0][3] / p[0][4] cross-covariances
        for (i in 1..1000) {
            ekf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.05, timestampMs = i * 20L)
        }
        val covBeforeReanchor = ekf.getCovarianceCopy()
        assertTrue("20s dead-reckoning should couple position and heading", abs(covBeforeReanchor[0][3]) > 1e-3 || abs(covBeforeReanchor[1][3]) > 1e-3)
        val p33Before = covBeforeReanchor[3][3]
        val p44Before = covBeforeReanchor[4][4]

        ekf.reanchorPosition(0.0, 0.0)
        assertTrue(ekf.updateGnssPosition(0.0, 0.0, accuracyMeters = 5.0, timestampMs = 20_000L, isStationary = false))

        val covAfter = ekf.getCovarianceCopy()
        assertEquals("Heading variance p33 must NOT collapse on origin re-anchor", p33Before, covAfter[3][3], 1e-9)
        assertEquals("Gyro bias variance p44 must NOT collapse on origin re-anchor", p44Before, covAfter[4][4], 1e-12)
    }

    // TST-FIL-023.1 (Bounce 2/3): Temporal adjacency guard (> 5s gap) rejects stale stationary glitches & clears moving outliers on snap
    @Test
    fun testStationaryOutlierTemporalGuard_rejectsSeparatedGlitchesAndClearsMovingOutliers_tstFil023_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 0.0, headingRad = PI / 2.0)
        assertTrue(ekf.updateGnssPosition(0.0, 0.0, accuracyMeters = 18.0, timestampMs = 1000L, isStationary = true))
        assertFalse(ekf.consumeStationaryClusterSnapped())

        // Two stationary outliers at (25, 0) separated by 7s (> GNSS_CLUSTER_MAX_STEP_GAP_MS = 5000 ms) must NOT snap
        assertFalse(ekf.updateGnssPosition(25.0, 0.0, accuracyMeters = 4.0, timestampMs = 2000L, isStationary = true))
        assertFalse(ekf.updateGnssPosition(25.0, 0.0, accuracyMeters = 4.0, timestampMs = 9000L, isStationary = true))
        assertFalse(ekf.consumeStationaryClusterSnapped())
        assertEquals(0.0, ekf.eastMeters, 1e-6)

        // Induce moving outlier streak, then stationary cluster snap must clear consecutiveMovingOutliers
        ekf.updateSpeed(6.0, variance = 0.1, timestampMs = 9500L)
        assertTrue(ekf.updateGnssPosition(150.0, 0.0, accuracyMeters = 3.0, timestampMs = 10_000L, isStationary = false))
        assertFalse(ekf.updateGnssPosition(25.0, 0.0, accuracyMeters = 4.0, timestampMs = 11_000L, isStationary = true))
        assertTrue(ekf.updateGnssPosition(25.1, 0.0, accuracyMeters = 4.0, timestampMs = 12_000L, isStationary = true))
        assertTrue("Stationary cluster snap must signal consumeStationaryClusterSnapped()", ekf.consumeStationaryClusterSnapped())
        assertFalse("consumeStationaryClusterSnapped() must be one-shot", ekf.consumeStationaryClusterSnapped())

        // Subsequent single moving outlier at (180, 0) must still be Huber-damped (streak was reset on snap)
        ekf.updateSpeed(6.0, variance = 0.1, timestampMs = 12_500L)
        assertTrue(ekf.updateGnssPosition(180.0, 0.0, accuracyMeters = 3.0, timestampMs = 13_000L, isStationary = false))
        assertTrue("Single moving outlier after stationary snap must be Huber-damped (east=${ekf.eastMeters})", ekf.eastMeters < 60.0)
    }

    // TST-FIL-025.1: Offline heading anchoring at running pace preserves live MIN_DOPPLER_SPEED_MPS = 3.0 gate
    @Test
    fun testOfflineHeading_updatesAtRunningPaceWhileLiveDopplerRemainsGated_tstFil025_1() {
        ekf.reset(east = 0.0, north = 0.0, speed = 2.0, headingRad = PI / 2.0)

        // Live Doppler gate (ATT-8576 / REQ-FIL-023.1) must still reject <= 3.0 m/s
        assertFalse(
            "Live updateDopplerHeading must stay gated at <= 3.0 m/s",
            ekf.updateDopplerHeading(headingRad = -PI / 2.0, varianceRad = 0.05, timestampMs = 1000L)
        )
        assertEquals(PI / 2.0, ekf.headingRad, 1e-6)

        // Offline ERTS heading anchoring must update heading at running pace (~2.0 m/s)
        assertTrue(
            "Offline heading update must succeed at running pace",
            ekf.updateOfflineHeading(headingRad = 0.0, varianceRad = 0.05, timestampMs = 2000L)
        )
        assertTrue(
            "Heading must pull from PI/2 toward 0.0 (was ${ekf.headingRad})",
            ekf.headingRad < PI / 2.0 - 0.1
        )

        // Opposing GNSS position update must never drive forward speed x[2] negative
        ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 3000L)
        ekf.updateGnssPosition(eastMeters = -50.0, northMeters = 0.0, accuracyMeters = 3.0, timestampMs = 3000L, isStationary = false)
        assertTrue("Forward speed x[2] must never collapse negative (was ${ekf.speedMps})", ekf.speedMps >= 0.0)
    }

    // TST-FIL-027.3 (Bounce 1/3): updateSpeed and constructor sanitize non-finite inputs
    @Test
    fun testUpdateSpeed_rejectsNonFiniteInputsWithoutPoisoningState_tstFil027_3() {
        val nanInitEkf = UnicycleOdometryEkf(initialSpeed = Double.NaN)
        assertEquals(0.0, nanInitEkf.speedMps, 1e-9)

        ekf.reset(east = 10.0, north = 20.0, speed = 5.0, headingRad = 0.0)
        assertFalse(ekf.updateSpeed(Double.NaN, 0.5, timestampMs = 1000L))
        assertFalse(ekf.updateSpeed(Double.POSITIVE_INFINITY, 0.5, timestampMs = 1000L))
        assertFalse(ekf.updateSpeed(4.0, Double.NaN, timestampMs = 1000L))
        assertFalse(ekf.updateSpeed(4.0, Double.POSITIVE_INFINITY, timestampMs = 1000L))

        assertEquals(5.0, ekf.speedMps, 1e-9)
        assertEquals(10.0, ekf.eastMeters, 1e-9)
        assertEquals(20.0, ekf.northMeters, 1e-9)
        assertTrue(ekf.speedVariance.isFinite())
    }

    // TST-FIL-032.3: Stationary predict freezes [E, N], F[0..1, 2..4], and Q_POS while keeping P_{k+1|k} SPD/invertible
    @Test
    fun testPredict_stationaryFreezesPositionAndCovarianceWhileRemainingInvertible_tstFil032_3() {
        ekf.reset(east = 120.0, north = -45.0, speed = 5.0, headingRad = 0.3)
        ekf.applyZeroVelocityUpdate(timestampMs = 1000L)
        assertEquals(0.0, ekf.speedMps, 1e-9)
        val initialPosVar = ekf.positionVariance
        assertTrue("Initial position variance must be positive", initialPosVar > 0.0)

        var ts = 2000L
        for (i in 0 until 118) {
            ekf.predict(
                dtSec = 1.0,
                gyroYawRateRadPerSec = 0.02,
                timestampMs = ts,
                gyroNoiseScale = 1.0,
                isStationary = true
            )
            ekf.applyZeroVelocityUpdate(timestampMs = ts)
            ts += 1000L
        }

        // [E, N] and P[0][0] + P[1][1] must stay completely frozen over 118 stationary seconds
        assertEquals(120.0, ekf.eastMeters, 1e-9)
        assertEquals(-45.0, ekf.northMeters, 1e-9)
        assertEquals(initialPosVar, ekf.positionVariance, 1e-9)

        val jac = ekf.getTransitionJacobianCopy()
        assertEquals(1.0, jac[0][0], 1e-12)
        assertEquals(1.0, jac[1][1], 1e-12)
        for (col in 2 until UnicycleOdometryEkf.STATE_DIM) {
            assertEquals("F[0][$col] must be zero while stationary", 0.0, jac[0][col], 1e-12)
            assertEquals("F[1][$col] must be zero while stationary", 0.0, jac[1][col], 1e-12)
        }

        // Covariance must remain strictly SPD and invertible for backward RTS
        val cov = ekf.getCovarianceCopy()
        val inv = ExtendedRtsOdometrySmoother.invert5x5(cov)
        val prod = ExtendedRtsOdometrySmoother.matMul5x5(cov, inv)
        for (r in 0 until UnicycleOdometryEkf.STATE_DIM) {
            for (c in 0 until UnicycleOdometryEkf.STATE_DIM) {
                assertTrue("Inverse entry [$r][$c] must be finite", inv[r][c].isFinite())
                val expected = if (r == c) 1.0 else 0.0
                assertEquals("P * P^-1 [$r][$c]", expected, prod[r][c], 1e-6)
            }
        }

        // Default isStationary = false must continue propagating position & Q_POS normally (live EKF unchanged)
        ekf.reset(east = 0.0, north = 0.0, speed = 6.0, headingRad = 0.0)
        val preMovingPosVar = ekf.positionVariance
        ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = ts)
        assertEquals(6.0, ekf.eastMeters, 1e-6)
        assertTrue("Moving predict must propagate position variance", ekf.positionVariance > preMovingPosVar)
    }

    // REQ-FIL-036.2 / TST-FIL-036.2
    @Test
    fun testSeedInitialHeadingAndVelocityDecoupling_snaps180DegAndBlocksOutageSpeedKick_tstFil036_2() {
        // 1. One-shot seedInitialHeading snaps 180 deg (default North +PI/2 -> South -PI/2) and zeroes cross-covariances
        val ekf = UnicycleOdometryEkf(initialSpeed = 2.0, initialHeadingRad = Math.PI / 2.0, recordSnapshots = false)
        ekf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 1_000L)

        // While unanchored, decoupleVelocity = true must prevent Southward GNSS update from crushing x[2]
        val preSpeed = ekf.speedMps
        assertTrue(ekf.updateGnssPosition(0.0, -4.0, 12.0, timestampMs = 1_000L, isStationary = false, decoupleVelocity = true))
        assertEquals("Decoupled GNSS update must not crush speed x[2]", preSpeed, ekf.speedMps, 1e-9)

        assertTrue(ekf.seedInitialHeading(-Math.PI / 2.0, varianceRad = 0.20, timestampMs = 2_000L))
        assertEquals(-Math.PI / 2.0, ekf.headingRad, 1e-9)
        assertEquals(0.20, ekf.headingVariance, 1e-9)
        val seededCov = ekf.getCovarianceCopy()
        for (idx in 0 until UnicycleOdometryEkf.STATE_DIM) {
            if (idx != 3) {
                assertEquals("Heading cross-cov [$idx][3] must be zeroed on seed", 0.0, seededCov[idx][3], 1e-12)
            }
        }

        // 2. confirmedPostOutage snap (3 consecutive moving outliers) must re-converge [E, N] without velocity spike
        val snapEkf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 2.0, initialHeadingRad = 0.0, recordSnapshots = false)
        snapEkf.updateGnssPosition(0.0, 0.0, 4.0, timestampMs = 1_000L, isStationary = false)
        for (step in 1..3) {
            val tMs = 1_000L + step * 1_000L
            snapEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = tMs)
            snapEkf.updateSpeed(2.0, 0.5, timestampMs = tMs)
            // 35 m East offset triggers moving outlier cluster & confirmedPostOutage on step 3
            snapEkf.updateGnssPosition(35.0 + step * 2.0, 0.0, 5.0, timestampMs = tMs, isStationary = false)
        }
        assertTrue("Post-outage snap must pull East toward ~41 m (was ${snapEkf.eastMeters})", snapEkf.eastMeters > 25.0)
        assertTrue("Post-outage snap must NOT inject synthetic velocity spike into x[2] (was ${snapEkf.speedMps})", snapEkf.speedMps < 3.0)
    }

    // REQ-STB-023.1 / TST-STB-023.1
    @Test
    fun testSnapshotRingBuffer_capsAtMaxSnapshotsAndDisablesCleanly_TST_STB_023_1() {
        // 1. Default recordSnapshots = true preserves snapshot API while enforcing drop-oldest MAX_SNAPSHOTS cap
        val boundedEkf = UnicycleOdometryEkf(initialSpeed = 3.0, initialHeadingRad = 0.0)
        val totalSteps = UnicycleOdometryEkf.MAX_SNAPSHOTS + 500
        for (i in 1..totalSteps) {
            boundedEkf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.01, timestampMs = i * 20L)
        }
        val retained = boundedEkf.getSnapshots()
        assertEquals(
            "Snapshot history must be strictly bounded at MAX_SNAPSHOTS",
            UnicycleOdometryEkf.MAX_SNAPSHOTS,
            retained.size
        )
        assertEquals(
            "Oldest snapshots must be evicted first (drop-oldest ring)",
            501L * 20L,
            retained.first().timestampMs
        )
        assertEquals(
            "Newest snapshot must be retained at tail",
            totalSteps.toLong() * 20L,
            retained.last().timestampMs
        )

        // 2. recordSnapshots = false retains zero snapshots across 60 min of 50 Hz predictions
        val liveEkf = UnicycleOdometryEkf(initialSpeed = 3.0, initialHeadingRad = 0.0, recordSnapshots = false)
        for (i in 1..180_000) {
            liveEkf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.0, timestampMs = i * 20L)
        }
        assertTrue("Live EKF with recordSnapshots=false must retain 0 snapshots", liveEkf.getSnapshots().isEmpty())
    }

    // REQ-FIL-041.2 / TST-FIL-041.2: Post-pause / post-standstill 3-fix decoupling blocks +19.1 m snap velocity spike,
    // arms even on stationary accuracy-gated calls, preserves 0->9 m/s fast starts, and re-couples on fix 4.
    @Test
    fun testPostPauseAndPostStandstillDecoupling_blocksSnapSpikeAndRecouplesOnFix4_TST_FIL_041_2() {
        // 1. Pre-stop moving baseline -> 159 s indoor pause with 50-Hz predict -> row 228 & row 229 (+19.1 m snap)
        val pauseEkf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 5.0, initialHeadingRad = 0.0, recordSnapshots = false)
        assertTrue(pauseEkf.updateGnssPosition(0.0, 0.0, accuracyMeters = 4.0, timestampMs = 1_000L, isStationary = false))

        // Simulate 159 s stop with coarse rejected indoor fixes & 1-Hz predictions building P[pos, v]
        pauseEkf.applyZeroVelocityUpdate(timestampMs = 2_000L)
        for (sec in 3..160) {
            pauseEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = sec * 1_000L)
            assertFalse(pauseEkf.updateGnssPosition(2.0, 0.0, accuracyMeters = 45.9, timestampMs = sec * 1_000L, isStationary = true))
        }

        // Row 228 (t = 161 s): first outdoor moving fix (Doppler 2.61 m/s)
        pauseEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 161_000L)
        pauseEkf.updateSpeed(2.61, variance = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(12.75), timestampMs = 161_000L)
        assertTrue(pauseEkf.updateGnssPosition(5.0, 0.0, accuracyMeters = 12.75, timestampMs = 161_000L, isStationary = false))
        assertTrue("Row 228 speed must track Doppler ~2.61 m/s (was ${pauseEkf.speedMps})", abs(pauseEkf.speedMps - 2.61) <= 1.0)

        // Row 229 (t = 162 s): +19.1 m outdoor position snap in 1 s with Doppler 3.51 m/s -> must NOT spike
        pauseEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 162_000L)
        pauseEkf.updateSpeed(3.51, variance = AttFusedColdStartHelper.calibratedDopplerSpeedVariance(11.73), timestampMs = 162_000L)
        val speedBeforeSnap = pauseEkf.speedMps
        assertTrue(pauseEkf.updateGnssPosition(24.1, 0.0, accuracyMeters = 11.73, timestampMs = 162_000L, isStationary = false))
        assertEquals("Decoupled post-pause snap must inject 0.0 m/s velocity leak", speedBeforeSnap, pauseEkf.speedMps, 1e-9)
        assertTrue("Row 229 speed must stay within 1.5 m/s of 3.51 m/s (was ${pauseEkf.speedMps})", abs(pauseEkf.speedMps - 3.51) <= 1.5)

        // 2. Stationary accuracy-gated call WITHOUT ZUPT still arms 3-fix decoupling (Gate 3 Guardrail #1),
        //    and fix 4 resumes coupled position-velocity updates cleanly.
        val directEkf = UnicycleOdometryEkf(initialEast = 0.0, initialNorth = 0.0, initialSpeed = 3.0, initialHeadingRad = 0.0, recordSnapshots = false)
        assertTrue(directEkf.updateGnssPosition(0.0, 0.0, accuracyMeters = 4.0, timestampMs = 1_000L, isStationary = false))
        assertFalse(directEkf.updateGnssPosition(0.0, 0.0, accuracyMeters = 45.9, timestampMs = 2_000L, isStationary = true))

        // Fixes 1..3 after stationary arming must remain decoupled (0.0 m/s speed change from position innovation)
        for (fixIdx in 1..3) {
            val tMs = 2_000L + fixIdx * 1_000L
            directEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = tMs)
            val before = directEkf.speedMps
            assertTrue(directEkf.updateGnssPosition(directEkf.eastMeters + 10.0, 0.0, accuracyMeters = 6.0, timestampMs = tMs, isStationary = false))
            assertEquals("Post-standstill fix #$fixIdx must stay velocity-decoupled", before, directEkf.speedMps, 1e-9)
        }
        // Fix 4: decoupling window expired -> coupled GNSS update now influences x[2] again
        directEkf.predict(dtSec = 1.0, gyroYawRateRadPerSec = 0.0, timestampMs = 6_000L)
        val beforeFix4 = directEkf.speedMps
        assertTrue(directEkf.updateGnssPosition(directEkf.eastMeters + 8.0, 0.0, accuracyMeters = 5.0, timestampMs = 6_000L, isStationary = false))
        assertTrue("Fix #4 must resume normal position-velocity coupling (before=$beforeFix4, after=${directEkf.speedMps})", directEkf.speedMps > beforeFix4 + 0.05)
    }

    // REQ-FIL-042.3 / TST-FIL-042.3: Interleaved lagged GNSS timestamps must not rewind currentTimestampMs,
    // while reset(timestampMs) directly restores currentTimestampMs for fresh sessions.
    @Test
    fun testInterleavedLaggedGnssTimestamp_keepsCurrentTimestampMonotonicAndResetClearsIt_TST_FIL_042_3() {
        val ekf = UnicycleOdometryEkf(initialSpeed = 4.0, initialHeadingRad = 0.0, recordSnapshots = true)
        ekf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.0, timestampMs = 685_756_550L)
        assertEquals(685_756_550L, ekf.getState().timestampMs)

        // Lagged GNSS fix (-460 ms behind latest 50-Hz gyro prediction) must NOT rewind currentTimestampMs
        assertTrue(ekf.updateSpeed(4.1, variance = 0.25, timestampMs = 685_756_090L))
        assertEquals(685_756_550L, ekf.getState().timestampMs)
        assertTrue(ekf.updateGnssPosition(1.0, 0.0, accuracyMeters = 4.0, timestampMs = 685_756_090L, isStationary = false))
        assertEquals(685_756_550L, ekf.getState().timestampMs)

        // Forward prediction advances cleanly
        ekf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.0, timestampMs = 685_756_570L)
        assertEquals(685_756_570L, ekf.getState().timestampMs)

        // reset() directly restores currentTimestampMs to 0L (or requested baseline) without sticky high-water mark
        ekf.reset(timestampMs = 0L)
        assertEquals(0L, ekf.getState().timestampMs)
        ekf.predict(dtSec = 0.02, gyroYawRateRadPerSec = 0.0, timestampMs = 1_000L)
        assertEquals(1_000L, ekf.getState().timestampMs)
    }
}
