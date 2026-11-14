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
class UnicycleOdometryEkf(
    initialEast: Double = 0.0,
    initialNorth: Double = 0.0,
    initialSpeed: Double = 0.0,
    initialHeadingRad: Double = PI / 2.0, // Default pointing North
    initialGyroBias: Double = 0.0,
    initialPositionStdDev: Double = 5.0,
    initialSpeedStdDev: Double = 2.0,
    initialHeadingStdDev: Double = 0.5,
    initialGyroBiasStdDev: Double = 0.03
) {
    companion object {
        const val STATE_DIM = 5
        const val MAX_GNSS_ACCURACY_METERS = 20.0
        const val MIN_DOPPLER_SPEED_MPS = 3.0
        const val HUBER_THRESHOLD_GAMMA = 2.5

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
        this[2] = initialSpeed
        this[3] = normalizeAngle(initialHeadingRad)
        this[4] = initialGyroBias
    }

    // 5x5 error covariance matrix P
    private val p = Array(STATE_DIM) { DoubleArray(STATE_DIM) }.apply {
        this[0][0] = initialPositionStdDev * initialPositionStdDev
        this[1][1] = initialPositionStdDev * initialPositionStdDev
        this[2][2] = initialSpeedStdDev * initialSpeedStdDev
        this[3][3] = initialHeadingStdDev * initialHeadingStdDev
        this[4][4] = initialGyroBiasStdDev * initialGyroBiasStdDev
    }

    private var currentTimestampMs: Long = 0L

    // Snapshot buffer for offline RTS backward smoother
    private val snapshotList = mutableListOf<OdometrySnapshot>()

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
     */
    fun predict(dtSec: Double, gyroYawRateRadPerSec: Double, timestampMs: Long = currentTimestampMs) {
        if (dtSec <= 0.0) return
        currentTimestampMs = timestampMs
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
        x[0] += speed * cosMid * dtSec
        x[1] += speed * sinMid * dtSec
        x[3] = normalizeAngle(heading + omegaCorrected * dtSec)
        // speed x[2] and bias x[4] maintain constant values under random walk

        // 3. Construct state transition Jacobian F_k (5x5)
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                transitionJacobian[i][j] = if (i == j) 1.0 else 0.0
            }
        }
        transitionJacobian[0][2] = cosMid * dtSec
        transitionJacobian[0][3] = -speed * sinMid * dtSec
        transitionJacobian[0][4] = 0.5 * speed * sinMid * dtSec * dtSec

        transitionJacobian[1][2] = sinMid * dtSec
        transitionJacobian[1][3] = speed * cosMid * dtSec
        transitionJacobian[1][4] = -0.5 * speed * cosMid * dtSec * dtSec

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
        p[0][0] += Q_POS * dtSec
        p[1][1] += Q_POS * dtSec
        p[2][2] += Q_ACC * dtSec
        p[3][3] += Q_GYRO * dtSec
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
        if (variance <= 0.0) return false
        currentTimestampMs = timestampMs

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
     * Updates 2D geographic position [E, N] via high-confidence GNSS fixes.
     *
     * Fixes with accuracy > [MAX_GNSS_ACCURACY_METERS] are strictly gated out to prevent
     * multipath and degraded GPS jumps from pulling the odometry trajectory.
     */
    fun updateGnssPosition(
        eastMeters: Double,
        northMeters: Double,
        accuracyMeters: Double,
        timestampMs: Long = currentTimestampMs
    ): Boolean {
        if (accuracyMeters <= 0.0 || accuracyMeters > MAX_GNSS_ACCURACY_METERS) {
            return false
        }
        currentTimestampMs = timestampMs

        val rVar = accuracyMeters * accuracyMeters
        // Innovation y = z - H*x where H = [1 0 0 0 0; 0 1 0 0 0]
        val y0 = eastMeters - x[0]
        val y1 = northMeters - x[1]

        // Innovation covariance S (2x2):
        // S_00 = P_00 + rVar, S_01 = P_01
        // S_10 = P_10,        S_11 = P_11 + rVar
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

        // State update: x += K * y
        for (i in 0 until STATE_DIM) {
            x[i] += k[i][0] * y0 + k[i][1] * y1
        }
        x[3] = normalizeAngle(x[3])

        // Covariance update: P = (I - K*H) * P => P[i][j] -= K[i][0]*P[0][j] + K[i][1]*P[1][j]
        val pNew = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                pNew[i][j] = p[i][j] - (k[i][0] * p[0][j] + k[i][1] * p[1][j])
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
        currentTimestampMs = timestampMs

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
     * Returns an unmodifiable view of all recorded step snapshots.
     */
    fun getSnapshots(): List<OdometrySnapshot> {
        return Collections.unmodifiableList(snapshotList)
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
        if (lastDtSec <= 0.0) return
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
        snapshotList.add(snapshot)
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
