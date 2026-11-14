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

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Smoothed vertical elevation and terrain slope point output by the Extended RTS backward pass
 * (REQ-FIL-017.2, TST-FIL-017.2, ATT-7750).
 *
 * @property timestampMs Timestamp in milliseconds.
 * @property altitudeM Smoothed zero-phase elevation in meters.
 * @property slopePercent Smoothed terrain slope grade in percent (e.g. 8.0 for +8%).
 * @property verticalSpeedMps Smoothed vertical speed in meters per second.
 * @property ascentM Cumulative ascent climb in meters.
 * @property descentM Cumulative descent drop in meters.
 */
data class SmoothedAltitudeSlopePoint(
    val timestampMs: Long,
    val altitudeM: Double,
    val slopePercent: Double,
    val verticalSpeedMps: Double,
    val ascentM: Double,
    val descentM: Double
)

/**
 * Raw altitude sample point ingested by [ExtendedRtsAltitudeSlopeSmoother].
 */
data class RawAltitudeSample(
    val timestampMs: Long,
    val rawAltitudeM: Double,
    val referenceAltitudeM: Double? = null,
    val smoothedSpeedMps: Double = 0.0,
    val isStandstill: Boolean = smoothedSpeedMps < 0.3
)

/**
 * Post-workout Extended Rauch-Tung-Striebel (ERTS/RTS) backward smoother for kinematic altitude & slope
 * (REQ-FIL-017.2, ATT-7750).
 *
 * Reuses the coupled kinematic model $dh = g \cdot ds$ from ATT-6279, driven by smoothed horizontal
 * speeds $v_{k|N}$ from [ExtendedRtsOdometrySmoother].
 */
@Suppress("TooManyFunctions", "MagicNumber")
object ExtendedRtsAltitudeSlopeSmoother {

    const val STATE_DIM = 3 // [h, g, b_baro]

    private const val DEFAULT_QH = 0.005       // Elevation random walk m^2 / s
    private const val DEFAULT_QG = 1e-4        // Spatial slope grade curvature per meter
    private const val DEFAULT_Q_BIAS = 1e-5    // Thermal/weather drift m^2 / s
    private const val DEFAULT_VAR_BARO = 1.0   // Barometer variance m^2
    private const val DEFAULT_VAR_DEM = 4.0    // DEM anchor variance m^2
    private const val HUBER_K = 2.5
    private const val STANDSTILL_SPEED_THRESHOLD_MPS = 0.3

    /**
     * Executes forward-backward ERTS pass over raw altitude samples driven by smoothed speeds.
     */
    fun smooth(samples: List<RawAltitudeSample>): List<SmoothedAltitudeSlopePoint> {
        val n = samples.size
        if (n == 0) return emptyList()

        if (n == 1) {
            val s0 = samples[0]
            val alt = s0.referenceAltitudeM ?: s0.rawAltitudeM
            return listOf(
                SmoothedAltitudeSlopePoint(
                    timestampMs = s0.timestampMs,
                    altitudeM = alt,
                    slopePercent = 0.0,
                    verticalSpeedMps = 0.0,
                    ascentM = 0.0,
                    descentM = 0.0
                )
            )
        }

        // Determine if reference altitude (DEM / GPS) is present across samples
        val hasReference = samples.any { it.referenceAltitudeM != null && !it.referenceAltitudeM.isNaN() }

        // State vector: [h, g, b_baro]
        val x = DoubleArray(STATE_DIM)
        val p = Array(STATE_DIM) { DoubleArray(STATE_DIM) }

        // Initialize state
        val firstSample = samples.first()
        val hasInitialRef = firstSample.referenceAltitudeM != null && !firstSample.referenceAltitudeM.isNaN()
        val initialAlt = if (hasInitialRef) firstSample.referenceAltitudeM else firstSample.rawAltitudeM
        x[0] = initialAlt
        x[1] = 0.0
        x[2] = 0.0
        p[0][0] = if (hasInitialRef) DEFAULT_VAR_DEM else 400.0
        p[1][1] = 0.01
        p[2][2] = if (hasReference) 400.0 else 1e-4

        val priors = ArrayList<DoubleArray>(n)
        val priorCovs = ArrayList<Array<DoubleArray>>(n)
        val jacobians = ArrayList<Array<DoubleArray>>(n)
        val posteriors = ArrayList<DoubleArray>(n)
        val postCovs = ArrayList<Array<DoubleArray>>(n)

        var lastTimeMs = samples.first().timestampMs

        for (k in 0 until n) {
            val sample = samples[k]
            val dtSec = if (k == 0) 1.0 else max(0.001, (sample.timestampMs - lastTimeMs) / 1000.0)
            lastTimeMs = sample.timestampMs

            val isStandstill = sample.isStandstill || sample.smoothedSpeedMps < STANDSTILL_SPEED_THRESHOLD_MPS
            val deltaDist = if (isStandstill) 0.0 else max(0.0, sample.smoothedSpeedMps * dtSec)

            // 1. Predict
            if (k > 0) {
                if (isStandstill) {
                    // Standstill clamping: dh = 0, Qg = 0
                    p[2][2] += DEFAULT_Q_BIAS * dtSec
                } else {
                    x[0] += x[1] * deltaDist
                    val qgVal = DEFAULT_QG * deltaDist
                    val qhVal = DEFAULT_QH * dtSec

                    val p00New = p[0][0] + 2.0 * deltaDist * p[0][1] + deltaDist * deltaDist * p[1][1] + qhVal
                    val p01New = p[0][1] + deltaDist * p[1][1]
                    val p11New = p[1][1] + qgVal
                    val p02New = p[0][2] + deltaDist * p[1][2]

                    p[0][0] = p00New
                    p[0][1] = p01New
                    p[1][0] = p01New
                    p[1][1] = p11New
                    p[0][2] = p02New
                    p[2][0] = p02New
                    p[2][2] += DEFAULT_Q_BIAS * dtSec
                }
            }

            // Save prior state & transition Jacobian
            val fMat = Array(STATE_DIM) { i ->
                DoubleArray(STATE_DIM) { j ->
                    if (i == j) 1.0 else if (i == 0 && j == 1 && !isStandstill) deltaDist else 0.0
                }
            }
            priors.add(x.clone())
            priorCovs.add(Array(STATE_DIM) { i -> p[i].clone() })
            jacobians.add(fMat)

            // 2. Ingest Barometer
            if (!sample.rawAltitudeM.isNaN()) {
                val zBaro = sample.rawAltitudeM
                val y = zBaro - (x[0] + x[2])

                if (isStandstill) {
                    val sBias = p[2][2] + DEFAULT_VAR_BARO
                    val kBias = p[2][2] / sBias
                    x[2] += kBias * applyHuber(y, sBias)
                    p[2][2] = max(1e-6, (1.0 - kBias) * p[2][2])
                } else {
                    val s = p[0][0] + 2.0 * p[0][2] + p[2][2] + DEFAULT_VAR_BARO
                    val yH = applyHuber(y, s)
                    val k0 = (p[0][0] + p[0][2]) / s
                    val k1 = (p[1][0] + p[1][2]) / s
                    val k2 = (p[2][0] + p[2][2]) / s

                    x[0] += k0 * yH
                    x[1] += k1 * yH
                    x[2] += k2 * yH

                    val v0 = p[0][0] + p[2][0]
                    val v1 = p[0][1] + p[2][1]
                    val v2 = p[0][2] + p[2][2]

                    p[0][0] = max(1e-6, p[0][0] - k0 * v0)
                    p[0][1] -= k0 * v1
                    p[0][2] -= k0 * v2
                    p[1][0] = p[0][1]
                    p[1][1] = max(1e-8, p[1][1] - k1 * v1)
                    p[1][2] -= k1 * v2
                    p[2][0] = p[0][2]
                    p[2][1] = p[1][2]
                    p[2][2] = max(1e-6, p[2][2] - k2 * v2)
                }
            }

            // 3. Ingest DEM reference anchor (if available)
            val refAlt = sample.referenceAltitudeM
            if (refAlt != null && !refAlt.isNaN()) {
                val yRef = refAlt - x[0]
                val sRef = p[0][0] + DEFAULT_VAR_DEM
                val yRefH = applyHuber(yRef, sRef)

                val k0 = p[0][0] / sRef
                val k1 = p[1][0] / sRef
                val k2 = p[2][0] / sRef

                x[0] += k0 * yRefH
                x[1] += k1 * yRefH
                x[2] += k2 * yRefH

                val v0 = p[0][0]
                val v1 = p[0][1]
                val v2 = p[0][2]

                p[0][0] = max(1e-6, p[0][0] - k0 * v0)
                p[0][1] -= k0 * v1
                p[0][2] -= k0 * v2
                p[1][0] = p[0][1]
                p[1][1] = max(1e-8, p[1][1] - k1 * v1)
                p[1][2] -= k1 * v2
                p[2][0] = p[0][2]
                p[2][1] = p[1][2]
                p[2][2] = max(1e-6, p[2][2] - k2 * v2)
            }

            symmetrize3x3(p)
            posteriors.add(x.clone())
            postCovs.add(Array(STATE_DIM) { i -> p[i].clone() })
        }

        // Backward RTS Smoothing pass
        val smoothedStates = Array(n) { DoubleArray(STATE_DIM) }
        val smoothedCovs = Array(n) { Array(STATE_DIM) { DoubleArray(STATE_DIM) } }

        val lastIdx = n - 1
        for (i in 0 until STATE_DIM) {
            smoothedStates[lastIdx][i] = posteriors[lastIdx][i]
            for (j in 0 until STATE_DIM) {
                smoothedCovs[lastIdx][i][j] = postCovs[lastIdx][i][j]
            }
        }

        for (k in n - 2 downTo 0) {
            val pCurrPost = postCovs[k]
            val fNext = jacobians[k + 1]
            val pNextPrior = priorCovs[k + 1]

            val invPNextPrior = invert3x3(pNextPrior)
            val fNextT = transpose3x3(fNext)
            val pFt = matMul3x3(pCurrPost, fNextT)
            val ck = matMul3x3(pFt, invPNextPrior)

            val deltaX = DoubleArray(STATE_DIM) { i ->
                smoothedStates[k + 1][i] - priors[k + 1][i]
            }

            val cDeltaX = matVecMul3x3(ck, deltaX)
            for (i in 0 until STATE_DIM) {
                smoothedStates[k][i] = posteriors[k][i] + cDeltaX[i]
            }

            val deltaCov = matSub3x3(smoothedCovs[k + 1], pNextPrior)
            val cDeltaCov = matMul3x3(ck, deltaCov)
            val ckTransposed = transpose3x3(ck)
            val cDeltaCovCt = matMul3x3(cDeltaCov, ckTransposed)

            for (i in 0 until STATE_DIM) {
                for (j in 0 until STATE_DIM) {
                    smoothedCovs[k][i][j] = pCurrPost[i][j] + cDeltaCovCt[i][j]
                }
            }
            symmetrize3x3(smoothedCovs[k])
        }

        // Output points and compute metrics
        val result = ArrayList<SmoothedAltitudeSlopePoint>(n)
        var cumulativeAscent = 0.0
        var cumulativeDescent = 0.0
        var previousAlt = Double.NaN

        for (k in 0 until n) {
            val state = smoothedStates[k]
            val alt = state[0]
            val grade = state[1]
            val sample = samples[k]
            val isStandstill = sample.isStandstill || sample.smoothedSpeedMps < STANDSTILL_SPEED_THRESHOLD_MPS

            val slopePct = if (isStandstill) 0.0 else grade * 100.0

            val vertSpeedMps = if (k == 0 || isStandstill) {
                0.0
            } else {
                val dt = (sample.timestampMs - samples[k - 1].timestampMs) / 1000.0
                if (dt > 0.0) {
                    val rawVz = (alt - previousAlt) / dt
                    max(-3.0, min(3.0, rawVz))
                } else {
                    0.0
                }
            }

            if (!previousAlt.isNaN() && !isStandstill) {
                val deltaAlt = alt - previousAlt
                if (deltaAlt > 0.0) {
                    cumulativeAscent += deltaAlt
                } else if (deltaAlt < 0.0) {
                    cumulativeDescent += -deltaAlt
                }
            }
            previousAlt = alt

            result.add(
                SmoothedAltitudeSlopePoint(
                    timestampMs = sample.timestampMs,
                    altitudeM = alt,
                    slopePercent = slopePct,
                    verticalSpeedMps = vertSpeedMps,
                    ascentM = cumulativeAscent,
                    descentM = cumulativeDescent
                )
            )
        }

        return result
    }

    private fun applyHuber(y: Double, s: Double): Double {
        val std = sqrt(max(1e-9, s))
        val normRes = abs(y) / std
        return if (normRes <= HUBER_K) {
            y
        } else {
            val sign = if (y >= 0.0) 1.0 else -1.0
            sign * HUBER_K * std
        }
    }

    /**
     * Exact analytical 3x3 matrix inversion via cofactor formula.
     */
    fun invert3x3(m: Array<DoubleArray>): Array<DoubleArray> {
        val a00 = m[0][0]
        val a01 = m[0][1]
        val a02 = m[0][2]
        val a10 = m[1][0]
        val a11 = m[1][1]
        val a12 = m[1][2]
        val a20 = m[2][0]
        val a21 = m[2][1]
        val a22 = m[2][2]

        val c00 = a11 * a22 - a12 * a21
        val c01 = -(a10 * a22 - a12 * a20)
        val c02 = a10 * a21 - a11 * a20

        val det = a00 * c00 + a01 * c01 + a02 * c02
        if (abs(det) < 1e-12) {
            // Tikhonov diagonal regularization for near-singular matrices
            return invert3x3(Array(3) { i ->
                DoubleArray(3) { j -> if (i == j) m[i][j] + 1e-6 else m[i][j] }
            })
        }

        val invDet = 1.0 / det
        val c10 = -(a01 * a22 - a02 * a21)
        val c11 = a00 * a22 - a02 * a20
        val c12 = -(a00 * a21 - a01 * a20)

        val c20 = a01 * a12 - a02 * a11
        val c21 = -(a00 * a12 - a02 * a10)
        val c22 = a00 * a11 - a01 * a10

        return arrayOf(
            doubleArrayOf(c00 * invDet, c10 * invDet, c20 * invDet),
            doubleArrayOf(c01 * invDet, c11 * invDet, c21 * invDet),
            doubleArrayOf(c02 * invDet, c12 * invDet, c22 * invDet)
        )
    }

    fun matMul3x3(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (k in 0 until 3) {
                val aik = a[i][k]
                if (abs(aik) > 1e-15) {
                    for (j in 0 until 3) {
                        res[i][j] += aik * b[k][j]
                    }
                }
            }
        }
        return res
    }

    fun matVecMul3x3(a: Array<DoubleArray>, v: DoubleArray): DoubleArray {
        val res = DoubleArray(3)
        for (i in 0 until 3) {
            res[i] = a[i][0] * v[0] + a[i][1] * v[1] + a[i][2] * v[2]
        }
        return res
    }

    fun matSub3x3(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                res[i][j] = a[i][j] - b[i][j]
            }
        }
        return res
    }

    fun transpose3x3(a: Array<DoubleArray>): Array<DoubleArray> {
        val res = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                res[i][j] = a[j][i]
            }
        }
        return res
    }

    fun symmetrize3x3(a: Array<DoubleArray>) {
        for (i in 0 until 3) {
            for (j in i + 1 until 3) {
                val avg = 0.5 * (a[i][j] + a[j][i])
                a[i][j] = avg
                a[j][i] = avg
            }
        }
    }
}
