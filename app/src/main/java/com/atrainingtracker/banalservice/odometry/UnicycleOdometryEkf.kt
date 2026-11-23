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

import java.util.ArrayDeque
import java.util.Collections
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 5-State Kinematically Coupled Unicycle Odometry Extended Kalman Filter Engine
 * (REQ-FIL-007, REQ-FIL-009, TST-FUSION-001.2, TST-FUSION-002.1, ATT-131).
 *
 * State vector:
 * x = [E, N, v, \psi, b_{\omega_z}]^T
 * 0: East position (meters)
 * 1: North position (meters)
 * 2: Forward linear speed (m/s)
 * 3: World yaw heading (radians, ENU convention: 0 = East, \pi/2 = North, counter-clockwise)
 * 4: Vertical gyroscope bias b_{\omega_z} (rad/s)
 *
 * Key Architectural Invariants:
 * 1. Zero external matrix dependencies (pure Kotlin closed-form 5x5 algebra).
 * 2. Unicycle prediction driven by world yaw-rate \omega_{ENU,z} corrected by estimated bias.
 * 3. Sequential scalar speed updates with Huber innovation down-weighting for wheel slip / lockup immunity.
 * 4. 2D GNSS position updates gated by accuracy <= 20.0 m.
 * 5. Doppler course-over-ground heading anchoring gated by speed > 3.0 m/s.
 * 6. Forward step snapshot exposure for post-workout Rauch-Tung-Striebel (RTS) backward smoothing (ATT-7750).
 */
class UnicycleOdometryEkf @JvmOverloads constructor(
    initialEast: Double = 0.0,
    initialNorth: Double = 0.0,
    initialSpeed: Double = 0.0,
    initialHeadingRad: Double = PI / 2.0, // Default pointing North
    initialGyroBias: Double = 0.0,
    initialPositionStdDev: Double = 5.0,
    initialSpeedStdDev: Double = 2.0,
    initialHeadingStdDev: Double = 0.5,
    initialGyroBiasStdDev: Double = 0.03,
    private val recordSnapshots: Boolean = true
) {
    companion object {
        const val STATE_DIM = 5
        const val MAX_GNSS_ACCURACY_METERS = 20.0
        const val MIN_DOPPLER_SPEED_MPS = 3.0
        const val HUBER_THRESHOLD_GAMMA = 2.5
        const val GNSS_HUBER_GAMMA = 3.0
        const val MIN_STATIONARY_GNSS_JUMP_GATE_METERS = 8.0
        const val STATIONARY_GNSS_ACCURACY_GATE_FACTOR = 0.75
        const val ZUPT_SPEED_VARIANCE = 2.0
        const val GNSS_OUTAGE_RECOVERY_GAP_MS = 15_000L
        const val GNSS_CLUSTER_MAX_STEP_GAP_MS = 5_000L
        /** GNSS/moving gap threshold arming post-pause position-velocity decoupling (REQ-FIL-041.2). */
        const val POST_PAUSE_DECOUPLE_GAP_MS = 5_000L
        /** Accepted moving GNSS fixes decoupled after pause, outage, or standstill (REQ-FIL-041.2). */
        const val POST_PAUSE_DECOUPLE_FIXES = 3
        /** Hard drop-oldest snapshot capacity ceiling preventing unbounded heap growth (`REQ-STB-023.1`). */
        const val MAX_SNAPSHOTS = 4096

        // Process noise intensities per second
        private const val Q_POS = 0.01 // m^2 / s
        private const val Q_ACC = 0.80 // (m/s^2)^2 * s
        private const val Q_GYRO = 0.0004 // (rad/s)^2 * s
        private const val Q_BIAS = 1e-6 // (rad/s^2)^2 * s

        /**
         * Normalizes an angle into the symmetric interval [-PI, PI).
         */
        fun normalizeAngle(rad: Double): Double {
            var a = (rad + PI) % (2.0 * PI)
            if (a < 0.0) a += 2.0 * PI
            return a - PI
        }
    }

    // 5-state vector: [E, N, v, psi, b_wz]
    private val x = DoubleArray(STATE_DIM).apply {
        this[0] = initialEast
        this[1] = initialNorth
        this[2] = if (initialSpeed.isFinite()) max(0.0, initialSpeed) else 0.0
        this[3] = normalizeAngle(initialHeadingRad)
        this[4] = initialGyroBias
    }

    // 5x5 error covariance matrix P
    private val initialPosVar = initialPositionStdDev * initialPositionStdDev
    private val p = Array(STATE_DIM) { DoubleArray(STATE_DIM) }.apply {
        this[0][0] = initialPosVar
        this[1][1] = initialPosVar
        this[2][2] = initialSpeedStdDev * initialSpeedStdDev
        this[3][3] = initialHeadingStdDev * initialHeadingStdDev
        this[4][4] = initialGyroBiasStdDev * initialGyroBiasStdDev
    }

    private var currentTimestampMs: Long = 0L

    private fun advanceTimestamp(timestampMs: Long) {
        if (timestampMs > currentTimestampMs) {
            currentTimestampMs = timestampMs
        }
    }
    private var lastAcceptedGnssTimestampMs: Long = 0L
    private var lastAcceptedMovingGnssTimestampMs: Long = 0L
    private var postPauseDecoupleRemainingFixes: Int = 0
    private var lastAcceptedGnssAccuracyMeters: Double = Double.POSITIVE_INFINITY
    private var consecutiveStationaryOutliers: Int = 0
    private var lastStationaryOutlierEast: Double = 0.0
    private var lastStationaryOutlierNorth: Double = 0.0
    private var lastStationaryOutlierTimestampMs: Long = 0L
    private var lastStationaryOutlierAccuracyMeters: Double = Double.POSITIVE_INFINITY
    private var stationaryClusterSnapped: Boolean = false
    private var consecutiveMovingOutliers: Int = 0
    private var lastMovingOutlierEast: Double = 0.0
    private var lastMovingOutlierNorth: Double = 0.0
    private var lastMovingOutlierTimestampMs: Long = 0L
    private var postOutageClusterGapMs: Long = 0L

    // Bounded drop-oldest snapshot ring buffer for offline RTS backward smoother (`REQ-STB-023.1`)
    private val snapshotList = ArrayDeque<OdometrySnapshot>()

    // Current step working buffers for snapshot capture
    private val priorState = DoubleArray(STATE_DIM)
    private val priorCovariance = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
    private val transitionJacobian = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
    private var lastDtSec: Double = 0.0

    val eastMeters: Double get() = x[0]
    val northMeters: Double get() = x[1]
    val speedMps: Double get() = x[2]
    val headingRad: Double get() = x[3]
    val gyroBiasRadPerSec: Double get() = x[4]

    val bearingDeg: Double
        get() {
            val deg = Math.toDegrees(PI / 2.0 - x[3])
            val normalized = deg % 360.0
            return if (normalized < 0.0) normalized + 360.0 else normalized
        }

    val speedVariance: Double get() = p[2][2]
    val positionVariance: Double get() = p[0][0] + p[1][1]
    val headingVariance: Double get() = p[3][3]

    /**
     * Obtains an immutable snapshot of the current odometry state and covariance.
     */
    fun getState(timestampMs: Long = currentTimestampMs): OdometryState {
        val covCopy = Array(STATE_DIM) { i -> p[i].clone() }
        return OdometryState(
            eastMeters = x[0],
            northMeters = x[1],
            speedMps = x[2],
            headingRad = x[3],
            gyroBiasRadPerSec = x[4],
            covariance = covCopy,
            timestampMs = timestampMs
        )
    }

    /**
     * Executes the EKF time-propagation / prediction step.
     *
     * @param dtSec Time delta in seconds.
     * @param gyroYawRateRadPerSec Quaternion-projected ENU yaw-rate \omega_{ENU,z} (rad/s).
     * @param timestampMs Epoch timestamp of this prediction step.
     * @param gyroNoiseScale Optional multiplier on gyro process noise [Q_GYRO] (e.g., > 1.0 for 1-Hz offline replay).
     * @param isStationary When `true` (offline ERTS stationary epoch, `REQ-FIL-032.3`), freezes `[E, N]`
     *   state propagation, zeroes position coupling in `F` (`F[0..1, 2..4] = 0`), and zeroes `Q_POS` so
     *   stationary position covariance does not inflate via `ZUPT_SPEED_VARIANCE` while preserving
     *   heading/bias propagation and positive-definite invertibility of `P_{k+1|k}`.
     */
    @JvmOverloads
    fun predict(
        dtSec: Double,
        gyroYawRateRadPerSec: Double,
        timestampMs: Long = currentTimestampMs,
        gyroNoiseScale: Double = 1.0,
        isStationary: Boolean = false
    ) {
        if (dtSec <= 0.0) return
        advanceTimestamp(timestampMs)
        lastDtSec = dtSec

        // 1. Save prior state and covariance for snapshot
        System.arraycopy(x, 0, priorState, 0, STATE_DIM)
        for (i in 0 until STATE_DIM) {
            System.arraycopy(p[i], 0, priorCovariance[i], 0, STATE_DIM)
        }

        val speed = x[2]
        val heading = x[3]
        val bias = x[4]

        // Turn rate corrected by current gyro bias estimate
        val omegaCorrected = gyroYawRateRadPerSec - bias
        val halfDeltaAngle = 0.5 * omegaCorrected * dtSec
        val psiMid = heading + halfDeltaAngle
        val cosMid = cos(psiMid)
        val sinMid = sin(psiMid)

        // 2. Propagate state vector
        if (!isStationary) {
            x[0] += speed * cosMid * dtSec
            x[1] += speed * sinMid * dtSec
        }
        x[3] = normalizeAngle(heading + omegaCorrected * dtSec)
        // speed x[2] and bias x[4] maintain constant values under random walk

        // 3. Construct state transition Jacobian F_k (5x5)
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                transitionJacobian[i][j] = if (i == j) 1.0 else 0.0
            }
        }
        if (!isStationary) {
            transitionJacobian[0][2] = cosMid * dtSec
            transitionJacobian[0][3] = -speed * sinMid * dtSec
            transitionJacobian[0][4] = 0.5 * speed * sinMid * dtSec * dtSec

            transitionJacobian[1][2] = sinMid * dtSec
            transitionJacobian[1][3] = speed * cosMid * dtSec
            transitionJacobian[1][4] = -0.5 * speed * cosMid * dtSec * dtSec
        }

        transitionJacobian[3][4] = -dtSec

        // 4. Propagate covariance: P = F * P * F^T + Q
        // Temp = F * P
        val temp = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                var sum = 0.0
                for (k in 0 until STATE_DIM) {
                    sum += transitionJacobian[i][k] * p[k][j]
                }
                temp[i][j] = sum
            }
        }

        // P_new = Temp * F^T + Q
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                var sum = 0.0
                for (k in 0 until STATE_DIM) {
                    sum += temp[i][k] * transitionJacobian[j][k]
                }
                p[i][j] = sum
            }
        }

        // Add process noise Q * dt
        val effectiveGyroNoiseScale = if (gyroNoiseScale.isFinite() && gyroNoiseScale > 0.0) gyroNoiseScale else 1.0
        if (!isStationary) {
            p[0][0] += Q_POS * dtSec
            p[1][1] += Q_POS * dtSec
        }
        p[2][2] += Q_ACC * dtSec
        p[3][3] += Q_GYRO * effectiveGyroNoiseScale * dtSec
        p[4][4] += Q_BIAS * dtSec

        symmetrizeCovariance()
        commitSnapshot()
    }

    /**
     * Sequentially updates the filter with a scalar speed measurement from any active source
     * (Front Wheel, Rear Wheel, or GNSS Doppler).
     *
     * Applies robust Huber down-weighting:
     * When innovation Mahalanobis distance exceeds [HUBER_THRESHOLD_GAMMA], effective measurement
     * variance is inflated quadratically to neutralize wheel slip spikes and brake lockups.
     */
    fun updateSpeed(measurement: SpeedMeasurement): Boolean {
        return updateSpeed(measurement.speedMps, measurement.variance, measurement.timestampMs)
    }

    /**
     * Scalar speed update with Huber innovation down-weighting.
     */
    fun updateSpeed(speedMps: Double, variance: Double, timestampMs: Long = currentTimestampMs): Boolean {
        if (!speedMps.isFinite() || !variance.isFinite() || variance <= 0.0) return false
        advanceTimestamp(timestampMs)

        val innovation = speedMps - x[2]
        val sBase = p[2][2] + variance
        val mahalanobis = abs(innovation) / sqrt(max(1e-12, sBase))

        // Huber weighting
        val effectiveVariance = if (mahalanobis > HUBER_THRESHOLD_GAMMA) {
            val scale = (mahalanobis / HUBER_THRESHOLD_GAMMA)
            variance * (scale * scale)
        } else {
            variance
        }

        val s = p[2][2] + effectiveVariance
        if (s <= 1e-12) return false

        // Kalman gain K = P * H^T / S where H = [0, 0, 1, 0, 0]
        val k = DoubleArray(STATE_DIM)
        for (i in 0 until STATE_DIM) {
            k[i] = p[i][2] / s
        }

        // State update
        for (i in 0 until STATE_DIM) {
            x[i] += k[i] * innovation
        }
        if (x[2] < 0.0) {
            x[2] = 0.0
        }
        x[3] = normalizeAngle(x[3])

        // Covariance update: P = (I - K*H) * P => P[i][j] -= K[i] * P[2][j]
        val pNew = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                pNew[i][j] = p[i][j] - k[i] * p[2][j]
            }
        }
        for (i in 0 until STATE_DIM) {
            System.arraycopy(pNew[i], 0, p[i], 0, STATE_DIM)
        }

        symmetrizeCovariance()
        commitSnapshot()
        return true
    }

    /**
     * Returns and clears whether the most recent [updateGnssPosition] call executed a stationary
     * cluster snap re-anchoring `[E, N]` (REQ-FIL-023.1, ATT-8576).
     */
    fun consumeStationaryClusterSnapped(): Boolean {
        val snapped = stationaryClusterSnapped
        stationaryClusterSnapped = false
        return snapped
    }

    /**
     * Re-anchors the local ENU position [E, N], resets position variances to [initialPosVar],
     * and zeroes all position cross-covariances without disturbing speed, heading, or gyro bias
     * (REQ-FIL-023.1, ATT-8576). Used when pre-anchor dead-reckoning precedes the first valid GNSS fix.
     */
    fun reanchorPosition(eastMeters: Double = 0.0, northMeters: Double = 0.0) {
        x[0] = eastMeters
        x[1] = northMeters
        resetPositionCovariance(initialPosVar)
        resetGnssTrackingState()
        symmetrizeCovariance()
    }

    /**
     * Applies a Zero-Velocity Update (ZUPT) clamping forward speed to 0.0 m/s and decoupling
     * position-velocity cross-covariances so subsequent GNSS position fixes cannot leak velocity
     * spikes into [speedMps] (REQ-FIL-023.1, ATT-8576).
     */
    fun applyZeroVelocityUpdate(timestampMs: Long = currentTimestampMs) {
        advanceTimestamp(timestampMs)
        x[2] = 0.0
        p[2][2] = max(p[2][2], ZUPT_SPEED_VARIANCE)
        postPauseDecoupleRemainingFixes = POST_PAUSE_DECOUPLE_FIXES
        decoupleVelocityCovariance(p)
        symmetrizeCovariance()
        commitSnapshot()
    }

    private fun resetPositionCovariance(posVar: Double) {
        p[0][0] = posVar
        p[1][1] = posVar
        p[0][1] = 0.0
        p[1][0] = 0.0
        for (j in 2 until STATE_DIM) {
            p[0][j] = 0.0
            p[j][0] = 0.0
            p[1][j] = 0.0
            p[j][1] = 0.0
        }
    }

    private fun resetGnssTrackingState() {
        lastAcceptedGnssTimestampMs = 0L
        lastAcceptedMovingGnssTimestampMs = 0L
        postPauseDecoupleRemainingFixes = 0
        lastAcceptedGnssAccuracyMeters = Double.POSITIVE_INFINITY
        consecutiveStationaryOutliers = 0
        lastStationaryOutlierEast = 0.0
        lastStationaryOutlierNorth = 0.0
        lastStationaryOutlierTimestampMs = 0L
        lastStationaryOutlierAccuracyMeters = Double.POSITIVE_INFINITY
        stationaryClusterSnapped = false
        consecutiveMovingOutliers = 0
        lastMovingOutlierEast = 0.0
        lastMovingOutlierNorth = 0.0
        lastMovingOutlierTimestampMs = 0L
        postOutageClusterGapMs = 0L
    }

    private fun decoupleVelocityCovariance(matrix: Array<DoubleArray>) {
        for (idx in 0 until STATE_DIM) {
            if (idx != 2) {
                matrix[idx][2] = 0.0
                matrix[2][idx] = 0.0
            }
        }
    }

    /**
     * Updates 2D geographic position [E, N] via high-confidence GNSS fixes.
     *
     * Fixes with accuracy > [MAX_GNSS_ACCURACY_METERS] are strictly gated out to prevent
     * multipath and degraded GPS jumps from pulling the odometry trajectory.
     * When [isStationary] is true, single-epoch jumps exceeding `max(8.0 m, 0.75 * accuracy)` are
     * rejected, while temporally and spatially adjacent stationary clusters (>= 3 fixes, or >= 2 fixes
     * after prolonged >= 15 s outage / significant accuracy tightening) cleanly re-anchor `[E, N]`
     * with `x[2] = 0.0`. While moving, single-epoch outliers exceeding [GNSS_HUBER_GAMMA] sigma are
     * Huber down-weighted, while confirmed post-outage clusters (>= 2 agreeing fixes after >= 15 s gap,
     * or >= 3 agreeing fixes) inflate position covariance and recover rapidly (REQ-FIL-009, REQ-FIL-023.1).
     */
    @JvmOverloads
    fun updateGnssPosition(
        eastMeters: Double,
        northMeters: Double,
        accuracyMeters: Double,
        timestampMs: Long = currentTimestampMs,
        isStationary: Boolean = false,
        decoupleVelocity: Boolean = false
    ): Boolean {
        stationaryClusterSnapped = false
        if (isStationary) {
            // Arm post-standstill velocity decoupling before accuracy-gate / stationary early returns (REQ-FIL-041.2)
            postPauseDecoupleRemainingFixes = POST_PAUSE_DECOUPLE_FIXES
        }
        if (accuracyMeters <= 0.0 || accuracyMeters > MAX_GNSS_ACCURACY_METERS) {
            return false
        }
        advanceTimestamp(timestampMs)
        var decoupleSpeedUpdate = isStationary || decoupleVelocity

        val outageMs = if (lastAcceptedGnssTimestampMs > 0L && timestampMs >= lastAcceptedGnssTimestampMs) {
            timestampMs - lastAcceptedGnssTimestampMs
        } else {
            0L
        }
        if (!isStationary) {
            val movingGapMs = if (lastAcceptedMovingGnssTimestampMs > 0L && timestampMs >= lastAcceptedMovingGnssTimestampMs) {
                timestampMs - lastAcceptedMovingGnssTimestampMs
            } else {
                0L
            }
            if (outageMs >= POST_PAUSE_DECOUPLE_GAP_MS || movingGapMs >= POST_PAUSE_DECOUPLE_GAP_MS) {
                postPauseDecoupleRemainingFixes = POST_PAUSE_DECOUPLE_FIXES
            }
            if (postPauseDecoupleRemainingFixes > 0) {
                decoupleSpeedUpdate = true
            }
        }

        // Innovation y = z - H*x where H = [1 0 0 0 0; 0 1 0 0 0]
        val y0 = eastMeters - x[0]
        val y1 = northMeters - x[1]

        if (isStationary) {
            val jumpMeters = sqrt(y0 * y0 + y1 * y1)
            val maxStationaryJump = max(MIN_STATIONARY_GNSS_JUMP_GATE_METERS, accuracyMeters * STATIONARY_GNSS_ACCURACY_GATE_FACTOR)
            if (jumpMeters > maxStationaryJump) {
                val clusterRadius = max(6.0, accuracyMeters)
                val dtStationaryMs = if (lastStationaryOutlierTimestampMs > 0L && timestampMs >= lastStationaryOutlierTimestampMs) {
                    timestampMs - lastStationaryOutlierTimestampMs
                } else {
                    0L
                }
                val distFromPrev = sqrt(
                    (eastMeters - lastStationaryOutlierEast) * (eastMeters - lastStationaryOutlierEast) +
                        (northMeters - lastStationaryOutlierNorth) * (northMeters - lastStationaryOutlierNorth)
                )
                val withinClusterWindow = consecutiveStationaryOutliers > 0 &&
                    lastStationaryOutlierTimestampMs > 0L &&
                    dtStationaryMs <= GNSS_CLUSTER_MAX_STEP_GAP_MS &&
                    distFromPrev <= clusterRadius
                consecutiveStationaryOutliers = if (withinClusterWindow) {
                    consecutiveStationaryOutliers + 1
                } else {
                    1
                }
                lastStationaryOutlierEast = eastMeters
                lastStationaryOutlierNorth = northMeters
                lastStationaryOutlierTimestampMs = timestampMs

                val significantTightening = lastAcceptedGnssAccuracyMeters.isFinite() &&
                    accuracyMeters <= lastAcceptedGnssAccuracyMeters * 0.5 &&
                    lastStationaryOutlierAccuracyMeters <= lastAcceptedGnssAccuracyMeters * 0.5
                lastStationaryOutlierAccuracyMeters = accuracyMeters
                val prolongedOutage = outageMs >= GNSS_OUTAGE_RECOVERY_GAP_MS
                val shouldReanchor = consecutiveStationaryOutliers >= 3 ||
                    (consecutiveStationaryOutliers >= 2 && (prolongedOutage || significantTightening))

                if (!shouldReanchor) {
                    return false
                }

                // Confirmed stationary recovery: snap [E, N] cleanly without leaking velocity into x[2]
                x[0] = eastMeters
                x[1] = northMeters
                x[2] = 0.0
                resetPositionCovariance(accuracyMeters * accuracyMeters)
                decoupleVelocityCovariance(p)
                consecutiveStationaryOutliers = 0
                lastStationaryOutlierTimestampMs = 0L
                consecutiveMovingOutliers = 0
                postOutageClusterGapMs = 0L
                lastMovingOutlierTimestampMs = 0L
                stationaryClusterSnapped = true
                lastAcceptedGnssTimestampMs = timestampMs
                lastAcceptedGnssAccuracyMeters = accuracyMeters
                symmetrizeCovariance()
                commitSnapshot()
                return true
            }
            consecutiveStationaryOutliers = 0
            lastStationaryOutlierTimestampMs = 0L
        } else {
            consecutiveStationaryOutliers = 0
            lastStationaryOutlierTimestampMs = 0L
        }

        val rVarBase = accuracyMeters * accuracyMeters
        val s00Base = p[0][0] + rVarBase
        val s01Base = p[0][1]
        val s10Base = p[1][0]
        val s11Base = p[1][1] + rVarBase
        val detBase = s00Base * s11Base - s01Base * s10Base
        if (abs(detBase) <= 1e-12) return false

        val mahalanobisSq = max(0.0, (y0 * y0 * s11Base - 2.0 * y0 * y1 * s01Base + y1 * y1 * s00Base) / detBase)
        val mahalanobis = sqrt(mahalanobisSq)
        val rVar: Double
        if (!isStationary && mahalanobis > GNSS_HUBER_GAMMA) {
            val dtOutlierMs = if (lastMovingOutlierTimestampMs > 0L && timestampMs >= lastMovingOutlierTimestampMs) {
                timestampMs - lastMovingOutlierTimestampMs
            } else {
                0L
            }
            val distFromPrevOutlier = sqrt(
                (eastMeters - lastMovingOutlierEast) * (eastMeters - lastMovingOutlierEast) +
                    (northMeters - lastMovingOutlierNorth) * (northMeters - lastMovingOutlierNorth)
            )
            val dtOutlierSec = dtOutlierMs / 1000.0
            val travelTolerance = max(15.0, abs(x[2]) * dtOutlierSec + 2.0 * accuracyMeters)
            if (consecutiveMovingOutliers > 0 &&
                dtOutlierMs <= GNSS_CLUSTER_MAX_STEP_GAP_MS &&
                distFromPrevOutlier <= travelTolerance
            ) {
                consecutiveMovingOutliers += 1
            } else {
                consecutiveMovingOutliers = 1
                postOutageClusterGapMs = outageMs
            }
            lastMovingOutlierEast = eastMeters
            lastMovingOutlierNorth = northMeters
            lastMovingOutlierTimestampMs = timestampMs

            val confirmedPostOutage = consecutiveMovingOutliers >= 3 ||
                (consecutiveMovingOutliers >= 2 && postOutageClusterGapMs >= GNSS_OUTAGE_RECOVERY_GAP_MS)
            if (confirmedPostOutage) {
                // Confirmed moving corridor after outage: inflate position variance to re-converge in 1-2 fixes
                // without leaking the position snap into scalar speed x[2] (REQ-FIL-036.2).
                p[0][0] = max(p[0][0], y0 * y0)
                p[1][1] = max(p[1][1], y1 * y1)
                decoupleSpeedUpdate = true
                decoupleVelocityCovariance(p)
                rVar = rVarBase
                consecutiveMovingOutliers = 0
                postOutageClusterGapMs = 0L
            } else {
                val scale = mahalanobis / GNSS_HUBER_GAMMA
                rVar = rVarBase * scale * scale
            }
        } else {
            consecutiveMovingOutliers = 0
            postOutageClusterGapMs = 0L
            rVar = rVarBase
        }

        // Innovation covariance S (2x2) with Huber-weighted measurement variance:
        val s00 = p[0][0] + rVar
        val s01 = p[0][1]
        val s10 = p[1][0]
        val s11 = p[1][1] + rVar

        val det = s00 * s11 - s01 * s10
        if (abs(det) <= 1e-12) return false

        val invDet = 1.0 / det
        val invS00 = s11 * invDet
        val invS01 = -s01 * invDet
        val invS10 = -s10 * invDet
        val invS11 = s00 * invDet

        // Kalman gain K (5x2) = P * H^T * inv(S)
        // where (P * H^T) is the 5x2 matrix composed of columns 0 and 1 of P
        val k = Array(STATE_DIM) { DoubleArray(2) }
        for (i in 0 until STATE_DIM) {
            val p0 = p[i][0]
            val p1 = p[i][1]
            k[i][0] = p0 * invS00 + p1 * invS10
            k[i][1] = p0 * invS01 + p1 * invS11
        }
        if (decoupleSpeedUpdate) {
            k[2][0] = 0.0
            k[2][1] = 0.0
        }

        // State update: x += K * y
        for (i in 0 until STATE_DIM) {
            x[i] += k[i][0] * y0 + k[i][1] * y1
        }
        if (isStationary || x[2] < 0.0) {
            x[2] = 0.0
        }
        x[3] = normalizeAngle(x[3])

        // Covariance update: P = (I - K*H) * P => P[i][j] -= K[i][0]*P[0][j] + K[i][1]*P[1][j]
        val pNew = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                pNew[i][j] = p[i][j] - (k[i][0] * p[0][j] + k[i][1] * p[1][j])
            }
        }
        if (decoupleSpeedUpdate) {
            decoupleVelocityCovariance(pNew)
        }
        for (i in 0 until STATE_DIM) {
            System.arraycopy(pNew[i], 0, p[i], 0, STATE_DIM)
        }

        if (consecutiveMovingOutliers == 0) {
            lastAcceptedGnssTimestampMs = timestampMs
            lastAcceptedGnssAccuracyMeters = accuracyMeters
            if (!isStationary) {
                lastAcceptedMovingGnssTimestampMs = timestampMs
                if (postPauseDecoupleRemainingFixes > 0) {
                    postPauseDecoupleRemainingFixes--
                }
            }
        }
        symmetrizeCovariance()
        commitSnapshot()
        return true
    }

    /**
     * Anchors the unicycle heading \psi using GNSS Doppler course-over-ground.
     *
     * Strictly gated when forward speed <= [MIN_DOPPLER_SPEED_MPS] (3.0 m/s), as Doppler
     * course headings become indeterminate and noisy at low speeds or while stationary.
     *
     * @param headingRad ENU heading in radians (0 = East, \pi/2 = North, counter-clockwise).
     * @param varianceRad Heading measurement error variance (rad^2).
     */
    fun updateDopplerHeading(
        headingRad: Double,
        varianceRad: Double = 0.05,
        timestampMs: Long = currentTimestampMs
    ): Boolean {
        if (x[2] <= MIN_DOPPLER_SPEED_MPS || varianceRad <= 0.0) {
            return false
        }
        return applyHeadingMeasurement(headingRad, varianceRad, timestampMs)
    }

    /**
     * One-shot initial heading seed for cold-start movement before Doppler or chord heading has
     * been anchored (REQ-FIL-036.2, ATT-9158). Snaps `x[3]` directly to [headingRad], sets
     * `p[3][3] = varianceRad`, and zeroes heading cross-covariances so 180-degree opposite starts
     * (e.g., South against default North `PI/2`) align cleanly in one step.
     */
    @JvmOverloads
    fun seedInitialHeading(
        headingRad: Double,
        varianceRad: Double = 0.16,
        timestampMs: Long = currentTimestampMs
    ): Boolean {
        if (!headingRad.isFinite() || !varianceRad.isFinite() || varianceRad <= 0.0) {
            return false
        }
        advanceTimestamp(timestampMs)
        x[3] = normalizeAngle(headingRad)
        for (idx in 0 until STATE_DIM) {
            if (idx != 3) {
                p[idx][3] = 0.0
                p[3][idx] = 0.0
            }
        }
        p[3][3] = varianceRad
        symmetrizeCovariance()
        commitSnapshot()
        return true
    }

    /**
     * Anchors the unicycle heading \psi during offline post-workout ERTS replay using
     * verified GNSS displacement-chord or moving bearing measurements (REQ-FIL-025.1, ATT-8705).
     *
     * Unlike [updateDopplerHeading], this helper does not enforce [MIN_DOPPLER_SPEED_MPS] (3.0 m/s)
     * so running-pace ERTS replay (~2.0 m/s) can anchor heading without weakening live
     * stationary/low-speed protection (REQ-FIL-023.1, ATT-8576).
     */
    fun updateOfflineHeading(
        headingRad: Double,
        varianceRad: Double = 0.08,
        timestampMs: Long = currentTimestampMs
    ): Boolean {
        return applyHeadingMeasurement(headingRad, varianceRad, timestampMs)
    }

    private fun applyHeadingMeasurement(
        headingRad: Double,
        varianceRad: Double,
        timestampMs: Long
    ): Boolean {
        if (!headingRad.isFinite() || varianceRad <= 0.0 || !varianceRad.isFinite()) {
            return false
        }
        advanceTimestamp(timestampMs)

        val innovation = normalizeAngle(headingRad - x[3])
        val s = p[3][3] + varianceRad
        if (s <= 1e-12) return false

        // Kalman gain K = P * H^T / S where H = [0, 0, 0, 1, 0]
        val k = DoubleArray(STATE_DIM)
        for (i in 0 until STATE_DIM) {
            k[i] = p[i][3] / s
        }

        // State update
        for (i in 0 until STATE_DIM) {
            x[i] += k[i] * innovation
        }
        if (x[2] < 0.0) {
            x[2] = 0.0
        }
        x[3] = normalizeAngle(x[3])

        // Covariance update: P[i][j] -= K[i] * P[3][j]
        val pNew = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                pNew[i][j] = p[i][j] - k[i] * p[3][j]
            }
        }
        for (i in 0 until STATE_DIM) {
            System.arraycopy(pNew[i], 0, p[i], 0, STATE_DIM)
        }

        symmetrizeCovariance()
        commitSnapshot()
        return true
    }

    /**
     * Convenience method to ingest GNSS Doppler telemetry (speed and course).
     *
     * @param speedMps Doppler ground speed (m/s).
     * @param bearingDeg Geographic course-over-ground in degrees [0, 360) clockwise from North.
     * @param speedVariance Error variance of the Doppler speed ((m/s)^2).
     * @param courseVarianceRad Error variance of the Doppler heading (rad^2).
     */
    fun updateGnssDoppler(
        speedMps: Double,
        bearingDeg: Double,
        speedVariance: Double = 0.25,
        courseVarianceRad: Double = 0.05,
        timestampMs: Long = currentTimestampMs
    ) {
        updateSpeed(speedMps, speedVariance, timestampMs)
        val enuHeadingRad = normalizeAngle(Math.toRadians(90.0 - bearingDeg))
        updateDopplerHeading(enuHeadingRad, courseVarianceRad, timestampMs)
    }

    /**
     * Resets filter state and covariances to given initial parameters.
     */
    fun reset(
        east: Double = 0.0,
        north: Double = 0.0,
        speed: Double = 0.0,
        headingRad: Double = PI / 2.0,
        gyroBias: Double = 0.0,
        timestampMs: Long = 0L
    ) {
        x[0] = east
        x[1] = north
        x[2] = speed
        x[3] = normalizeAngle(headingRad)
        x[4] = gyroBias
        currentTimestampMs = timestampMs
        resetGnssTrackingState()

        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                p[i][j] = 0.0
            }
        }
        p[0][0] = 25.0
        p[1][1] = 25.0
        p[2][2] = 4.0
        p[3][3] = 0.25
        p[4][4] = 0.001

        snapshotList.clear()
    }

    /**
     * Returns an unmodifiable view of all recorded step snapshots (bounded to [MAX_SNAPSHOTS]).
     */
    fun getSnapshots(): List<OdometrySnapshot> {
        return Collections.unmodifiableList(ArrayList(snapshotList))
    }

    fun getCovarianceCopy(): Array<DoubleArray> = Array(STATE_DIM) { i -> p[i].clone() }

    fun getTransitionJacobianCopy(): Array<DoubleArray> = Array(STATE_DIM) { i -> transitionJacobian[i].clone() }

    /**
     * Clears recorded step snapshots.
     */
    fun clearSnapshots() {
        snapshotList.clear()
    }

    private fun commitSnapshot() {
        if (!recordSnapshots || lastDtSec <= 0.0) return
        val priorStateCopy = priorState.clone()
        val priorCovCopy = Array(STATE_DIM) { i -> priorCovariance[i].clone() }
        val transJacCopy = Array(STATE_DIM) { i -> transitionJacobian[i].clone() }
        val postStateCopy = x.clone()
        val postCovCopy = Array(STATE_DIM) { i -> p[i].clone() }

        val snapshot = OdometrySnapshot(
            timestampMs = currentTimestampMs,
            dtSec = lastDtSec,
            statePrior = priorStateCopy,
            covariancePrior = priorCovCopy,
            transitionJacobian = transJacCopy,
            statePosterior = postStateCopy,
            covariancePosterior = postCovCopy
        )
        if (snapshotList.size >= MAX_SNAPSHOTS) {
            snapshotList.removeFirst()
        }
        snapshotList.addLast(snapshot)
    }

    private fun symmetrizeCovariance() {
        for (i in 0 until STATE_DIM) {
            for (j in i + 1 until STATE_DIM) {
                val avg = 0.5 * (p[i][j] + p[j][i])
                p[i][j] = avg
                p[j][i] = avg
            }
            if (p[i][i] < 1e-12) {
                p[i][i] = 1e-12
            }
        }
    }
}
