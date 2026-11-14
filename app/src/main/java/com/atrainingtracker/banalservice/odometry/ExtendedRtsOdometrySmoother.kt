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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Smoothed odometry state point output by the Extended Rauch-Tung-Striebel (ERTS/RTS) backward pass
 * (REQ-FIL-017.1, TST-FIL-017.1, ATT-7750).
 *
 * @property timestampMs Timestamp in milliseconds.
 * @property eastMeters Smoothed East displacement in meters from reference origin.
 * @property northMeters Smoothed North displacement in meters from reference origin.
 * @property speedMps Smoothed horizontal speed in meters per second.
 * @property headingRad Smoothed ENU heading angle in radians (0 = East, PI/2 = North).
 * @property gyroBiasRadPerSec Smoothed vertical gyroscope bias in radians per second.
 * @property latitude Smoothed WGS-84 latitude in degrees.
 * @property longitude Smoothed WGS-84 longitude in degrees.
 * @property bearingDeg Smoothed course-over-ground bearing in degrees (0 = North, clockwise).
 * @property deltaDistanceMeters Smoothed horizontal distance increment since previous step.
 */
data class SmoothedOdometryPoint(
    val timestampMs: Long,
    val eastMeters: Double,
    val northMeters: Double,
    val speedMps: Double,
    val headingRad: Double,
    val gyroBiasRadPerSec: Double,
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Double,
    val deltaDistanceMeters: Double
)

/**
 * Raw trajectory sample point ingested by [ExtendedRtsOdometrySmoother] for forward replay.
 */
data class RawOdometrySample(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val accuracyMeters: Double? = null,
    val worldYawRateRadPerSec: Double? = null,
    val wheelSpeedMps: Double? = null
)

/**
 * Post-workout Extended Rauch-Tung-Striebel (ERTS/RTS) backward smoother for 5-state unicycle odometry
 * (REQ-FIL-017.1, ATT-7750).
 *
 * Reuses the exact 5-state unicycle model $[E, N, v, \psi, b_{\omega_z}]^T$ from ATT-131.
 * Pure Kotlin implementation: zero external linear algebra dependencies, < 50ms for 14,400 samples.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object ExtendedRtsOdometrySmoother {

    const val STATE_DIM = 5
    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Runs backward RTS recursion on a recorded list of forward [OdometrySnapshot] points.
     *
     * Recursion:
     * $$C_k = P_{k|k} F_k^T (P_{k+1|k})^{-1}$$
     * $$x_{k|N} = x_{k|k} + C_k (x_{k+1|N} - x_{k+1|k})$$
     * $$P_{k|N} = P_{k|k} + C_k (P_{k+1|N} - P_{k+1|k}) C_k^T$$
     * with heading difference $\psi_{k+1|N} - \psi_{k+1|k}$ wrapped to $[-\pi, \pi)$.
     */
    fun smoothSnapshots(
        snapshots: List<OdometrySnapshot>,
        refLat: Double,
        refLon: Double
    ): List<SmoothedOdometryPoint> {
        val n = snapshots.size
        if (n == 0) return emptyList()
        if (n == 1) {
            val s0 = snapshots[0]
            val x0 = s0.statePosterior
            val (lat, lon) = enuToLatLng(x0[0], x0[1], refLat, refLon)
            return listOf(
                SmoothedOdometryPoint(
                    timestampMs = s0.timestampMs,
                    eastMeters = x0[0],
                    northMeters = x0[1],
                    speedMps = max(0.0, x0[2]),
                    headingRad = x0[3],
                    gyroBiasRadPerSec = x0[4],
                    latitude = lat,
                    longitude = lon,
                    bearingDeg = headingToBearingDeg(x0[3]),
                    deltaDistanceMeters = 0.0
                )
            )
        }

        // Smoothed state arrays
        val smoothedStates = Array(n) { DoubleArray(STATE_DIM) }
        val smoothedCovs = Array(n) { Array(STATE_DIM) { DoubleArray(STATE_DIM) } }

        // Step N-1 initialization: x^s_{N-1} = x_{N-1|N-1}, P^s_{N-1} = P_{N-1|N-1}
        val lastIdx = n - 1
        for (i in 0 until STATE_DIM) {
            smoothedStates[lastIdx][i] = snapshots[lastIdx].statePosterior[i]
            for (j in 0 until STATE_DIM) {
                smoothedCovs[lastIdx][i][j] = snapshots[lastIdx].covariancePosterior[i][j]
            }
        }

        // Pre-allocate scratch buffers to guarantee zero heap allocations in backward pass
        val aug = Array(STATE_DIM) { DoubleArray(2 * STATE_DIM) }
        val invPNextPrior = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val fNextTransposed = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val pFt = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val ck = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val deltaX = DoubleArray(STATE_DIM)
        val cDeltaX = DoubleArray(STATE_DIM)
        val deltaCov = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val cDeltaCov = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val ckTransposed = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val cDeltaCovCt = Array(STATE_DIM) { DoubleArray(STATE_DIM) }

        // Backward recursion from N-2 down to 0
        for (k in n - 2 downTo 0) {
            val curr = snapshots[k]
            val next = snapshots[k + 1]

            val pCurrPost = curr.covariancePosterior
            val fNext = next.transitionJacobian
            val pNextPrior = next.covariancePrior

            // Invert P_{k+1|k} into scratch buffer
            invert5x5(pNextPrior, invPNextPrior, aug)

            // C_k = P_{k|k} * F_k^T * (P_{k+1|k})^{-1}
            transpose5x5(fNext, fNextTransposed)
            matMul5x5(pCurrPost, fNextTransposed, pFt)
            matMul5x5(pFt, invPNextPrior, ck)

            // deltaX = x^s_{k+1} - x_{k+1|k}
            for (i in 0 until STATE_DIM) {
                deltaX[i] = smoothedStates[k + 1][i] - next.statePrior[i]
            }
            // Normalize heading angle difference
            deltaX[3] = UnicycleOdometryEkf.normalizeAngle(deltaX[3])

            // x^s_k = x_{k|k} + C_k * deltaX
            matVecMul5x5(ck, deltaX, cDeltaX)
            for (i in 0 until STATE_DIM) {
                smoothedStates[k][i] = curr.statePosterior[i] + cDeltaX[i]
            }
            smoothedStates[k][3] = UnicycleOdometryEkf.normalizeAngle(smoothedStates[k][3])
            smoothedStates[k][2] = max(0.0, smoothedStates[k][2]) // Speed non-negative

            // P^s_k = P_{k|k} + C_k * (P^s_{k+1} - P_{k+1|k}) * C_k^T
            matSub5x5(smoothedCovs[k + 1], pNextPrior, deltaCov)
            matMul5x5(ck, deltaCov, cDeltaCov)
            transpose5x5(ck, ckTransposed)
            matMul5x5(cDeltaCov, ckTransposed, cDeltaCovCt)

            for (i in 0 until STATE_DIM) {
                for (j in 0 until STATE_DIM) {
                    smoothedCovs[k][i][j] = pCurrPost[i][j] + cDeltaCovCt[i][j]
                }
            }
            symmetrize5x5(smoothedCovs[k])
        }

        // Build smoothed points with geographic coordinates and distance increments
        val result = ArrayList<SmoothedOdometryPoint>(n)
        for (k in 0 until n) {
            val xk = smoothedStates[k]
            val (lat, lon) = enuToLatLng(xk[0], xk[1], refLat, refLon)
            val bearing = headingToBearingDeg(xk[3])

            val deltaDist = if (k == 0) {
                0.0
            } else {
                val dt = (snapshots[k].timestampMs - snapshots[k - 1].timestampMs) / 1000.0
                if (dt > 0.0) max(0.0, xk[2] * dt) else 0.0
            }

            result.add(
                SmoothedOdometryPoint(
                    timestampMs = snapshots[k].timestampMs,
                    eastMeters = xk[0],
                    northMeters = xk[1],
                    speedMps = xk[2],
                    headingRad = xk[3],
                    gyroBiasRadPerSec = xk[4],
                    latitude = lat,
                    longitude = lon,
                    bearingDeg = bearing,
                    deltaDistanceMeters = deltaDist
                )
            )
        }
        return result
    }

    /**
     * Executes complete forward EKF replay followed by backward RTS smoothing on raw samples.
     */
    fun smoothSamples(
        samples: List<RawOdometrySample>,
        initialHeadingRad: Double = PI / 2.0
    ): List<SmoothedOdometryPoint> {
        if (samples.isEmpty()) return emptyList()

        val refLat = samples.first().latitude
        val refLon = samples.first().longitude

        // Initialize forward EKF
        val ekf = UnicycleOdometryEkf(
            initialEast = 0.0,
            initialNorth = 0.0,
            initialSpeed = samples.first().speedMps ?: 0.0,
            initialHeadingRad = initialHeadingRad
        )

        val snapshots = ArrayList<OdometrySnapshot>(samples.size)

        var lastTimeMs = samples.first().timestampMs
        for (i in samples.indices) {
            val sample = samples[i]
            val dtSec = if (i == 0) 1.0 else max(0.001, (sample.timestampMs - lastTimeMs) / 1000.0)
            lastTimeMs = sample.timestampMs

            // 1. Predict step
            val yawRate = sample.worldYawRateRadPerSec ?: 0.0
            ekf.predict(dtSec, yawRate, sample.timestampMs)

            // Capture prior state and transition Jacobian right after predict
            val priorState = DoubleArray(STATE_DIM) { j ->
                when (j) {
                    0 -> ekf.eastMeters
                    1 -> ekf.northMeters
                    2 -> ekf.speedMps
                    3 -> ekf.headingRad
                    else -> ekf.gyroBiasRadPerSec
                }
            }
            val priorCov = ekf.getCovarianceCopy()
            val transJac = ekf.getTransitionJacobianCopy()

            // 2. Ingest measurements
            val (eastM, northM) = latLngToEnu(sample.latitude, sample.longitude, refLat, refLon)
            val acc = sample.accuracyMeters ?: 10.0
            ekf.updateGnssPosition(eastM, northM, acc, sample.timestampMs)

            sample.wheelSpeedMps?.let { ws ->
                ekf.updateSpeed(ws, 0.5, sample.timestampMs)
            } ?: sample.speedMps?.let { s ->
                val sVar = max(0.5, (acc * 0.25) * (acc * 0.25))
                ekf.updateSpeed(s, sVar, sample.timestampMs)
            }

            if (sample.speedMps != null && sample.speedMps > UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS &&
                sample.bearingDeg != null
            ) {
                val enuHeadingRad = Math.toRadians((450.0 - sample.bearingDeg) % 360.0)
                ekf.updateDopplerHeading(enuHeadingRad, Math.toRadians(5.0) * Math.toRadians(5.0), sample.timestampMs)
            }

            // Capture posterior state and covariance after measurement updates
            val postState = DoubleArray(STATE_DIM) { j ->
                when (j) {
                    0 -> ekf.eastMeters
                    1 -> ekf.northMeters
                    2 -> ekf.speedMps
                    3 -> ekf.headingRad
                    else -> ekf.gyroBiasRadPerSec
                }
            }
            val postCov = ekf.getCovarianceCopy()

            snapshots.add(
                OdometrySnapshot(
                    timestampMs = sample.timestampMs,
                    dtSec = dtSec,
                    statePrior = priorState,
                    covariancePrior = priorCov,
                    transitionJacobian = transJac,
                    statePosterior = postState,
                    covariancePosterior = postCov
                )
            )
        }

        return smoothSnapshots(snapshots, refLat, refLon)
    }

    fun enuToLatLng(eastM: Double, northM: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val lat = refLat + Math.toDegrees(northM / EARTH_RADIUS_METERS)
        val cosRef = cos(Math.toRadians(refLat))
        val lon = if (abs(cosRef) > 1e-6) {
            refLon + Math.toDegrees(eastM / (EARTH_RADIUS_METERS * cosRef))
        } else {
            refLon
        }
        return Pair(lat, lon)
    }

    fun latLngToEnu(lat: Double, lon: Double, refLat: Double, refLon: Double): Pair<Double, Double> {
        val dLatRad = Math.toRadians(lat - refLat)
        val dLonRad = Math.toRadians(lon - refLon)
        val northMeters = dLatRad * EARTH_RADIUS_METERS
        val eastMeters = dLonRad * EARTH_RADIUS_METERS * cos(Math.toRadians(refLat))
        return Pair(eastMeters, northMeters)
    }

    fun headingToBearingDeg(headingRad: Double): Double {
        val deg = Math.toDegrees(PI / 2.0 - headingRad)
        var norm = deg % 360.0
        if (norm < 0.0) norm += 360.0
        return norm
    }

    /**
     * Pure Kotlin Gauss-Jordan 5x5 matrix inversion with partial pivoting.
     */
    fun invert5x5(m: Array<DoubleArray>): Array<DoubleArray> {
        val out = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        val aug = Array(STATE_DIM) { DoubleArray(2 * STATE_DIM) }
        invert5x5(m, out, aug)
        return out
    }

    fun invert5x5(m: Array<DoubleArray>, out: Array<DoubleArray>, aug: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val mi = m[i]
            val augi = aug[i]
            for (j in 0 until n) {
                augi[j] = mi[j]
                augi[j + n] = if (i == j) 1.0 else 0.0
            }
        }

        for (col in 0 until n) {
            var maxRow = col
            var maxVal = abs(aug[col][col])
            for (row in col + 1 until n) {
                val v = abs(aug[row][col])
                if (v > maxVal) {
                    maxVal = v
                    maxRow = row
                }
            }

            if (maxVal < 1e-12) {
                // Tikhonov regularization for near-singular covariance blocks
                aug[col][col] += 1e-6
            }

            if (maxRow != col) {
                val temp = aug[col]
                aug[col] = aug[maxRow]
                aug[maxRow] = temp
            }

            val pivot = aug[col][col]
            val invPivot = 1.0 / pivot
            val augCol = aug[col]
            for (j in 0 until 2 * n) {
                augCol[j] *= invPivot
            }

            for (r in 0 until n) {
                if (r != col) {
                    val factor = aug[r][col]
                    if (abs(factor) > 1e-15) {
                        val augR = aug[r]
                        for (j in 0 until 2 * n) {
                            augR[j] -= factor * augCol[j]
                        }
                    }
                }
            }
        }

        for (i in 0 until n) {
            val augi = aug[i]
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = augi[j + n]
            }
        }
    }

    fun matMul5x5(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        matMul5x5(a, b, res)
        return res
    }

    fun matMul5x5(a: Array<DoubleArray>, b: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = 0.0
            }
            for (k in 0 until n) {
                val aik = a[i][k]
                if (abs(aik) > 1e-15) {
                    val bk = b[k]
                    for (j in 0 until n) {
                        outi[j] += aik * bk[j]
                    }
                }
            }
        }
    }

    fun matVecMul5x5(a: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val res = DoubleArray(STATE_DIM)
        matVecMul5x5(a, v, res)
        return res
    }

    fun matVecMul5x5(a: Array<DoubleArray>, v: DoubleArray, out: DoubleArray) {
        val n = STATE_DIM
        for (i in 0 until n) {
            var sum = 0.0
            val ai = a[i]
            for (j in 0 until n) {
                sum += ai[j] * v[j]
            }
            out[i] = sum
        }
    }

    fun matSub5x5(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        matSub5x5(a, b, res)
        return res
    }

    fun matSub5x5(a: Array<DoubleArray>, b: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            val ai = a[i]
            val bi = b[i]
            val outi = out[i]
            for (j in 0 until n) {
                outi[j] = ai[j] - bi[j]
            }
        }
    }

    fun transpose5x5(a: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(STATE_DIM) { DoubleArray(STATE_DIM) }
        transpose5x5(a, res)
        return res
    }

    fun transpose5x5(a: Array<DoubleArray>, out: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            for (j in 0 until n) {
                out[i][j] = a[j][i]
            }
        }
    }

    fun symmetrize5x5(a: Array<DoubleArray>) {
        val n = STATE_DIM
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val avg = 0.5 * (a[i][j] + a[j][i])
                a[i][j] = avg
                a[j][i] = avg
            }
        }
    }
}
