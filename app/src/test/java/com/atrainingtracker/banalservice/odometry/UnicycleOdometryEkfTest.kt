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
}
