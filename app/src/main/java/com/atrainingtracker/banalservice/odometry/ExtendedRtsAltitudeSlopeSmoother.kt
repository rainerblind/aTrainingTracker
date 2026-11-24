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
import kotlin.math.exp
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
    val isStandstill: Boolean = smoothedSpeedMps < 0.3,
    val carriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN,
    val baroResetStep: Boolean = false,
    val isPauseGap: Boolean = false
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
    const val MAX_ACTIVE_SAMPLE_GAP_MS = 5_000L
    const val ASCENT_DESCENT_HYSTERESIS_METERS = 2.0
    const val MAX_PHYSICAL_SLOPE = 0.35 // +/-35% grade clamp (REQ-FIL-023.3, REQ-FIL-033.1)
    const val LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2 // Low-speed slope damping ceiling (REQ-FIL-033.1)
    const val DEFAULT_INITIAL_SLOPE_VAR = 0.0025 // (5% grade)^2 realistic flat-start prior (REQ-FIL-033.1)
    const val MAX_SLOPE_VARIANCE = 0.015 // Prevents unbounded p11 growth over long rides (REQ-FIL-033.1)
    const val STANDSTILL_SLOPE_GRACE_SEC = 2.0
    const val STANDSTILL_SLOPE_DECAY_TAU_SEC = 2.5
    const val BRAKING_DECEL_THRESHOLD_MPS2 = 0.25 // Deceleration threshold for low-speed braking protection (REQ-FIL-040.4)
    const val BRAKING_HOLD_DURATION_SEC = 4.0
    const val BRAKING_LOW_SPEED_CEILING_MPS = 2.5
    const val BRAKING_BARO_VAR_MULTIPLIER = 8.0
    const val BRAKING_BARO_VAR_NO_REF_MULTIPLIER = 4.0

    private const val BRAKING_ACCEL_MIN_DT_SEC = 0.1
    private const val BRAKING_ACCEL_MAX_DT_SEC = 5.0

    private const val DEFAULT_QH = 0.005       // Elevation random walk m^2 / s
    private const val DEFAULT_QG = 1e-4        // Spatial slope grade curvature per meter
    private const val DEFAULT_Q_BIAS = 1e-5    // Thermal/weather drift m^2 / s
    private const val BARO_RESET_Q_BIAS_IMPULSE = 1000.0 // Process-noise impulse on sustained barometer reset step (REQ-STB-025.4)
    internal const val BARO_RESET_STEP_MIN_M = 6.0
    private const val BARO_RESET_LOOKAHEAD_SAMPLES = 4
    private const val BARO_RESET_MIN_SUSTAINED_SAMPLES = 3
    private const val DEFAULT_VAR_DEM = 4.0    // DEM anchor variance m^2
    private const val HUBER_K = 2.5
    private const val STANDSTILL_SPEED_THRESHOLD_MPS = 0.3

    /**
     * Smoothly damps slope process noise and innovation gain below [LOW_SPEED_SLOPE_THRESHOLD_MPS]
     * (REQ-FIL-023.3, REQ-FIL-033.1), returning `0.0` for non-finite or standstill speeds.
     */
    internal fun lowSpeedSlopeGainFactor(speedMps: Double): Double {
        if (!speedMps.isFinite() || speedMps < STANDSTILL_SPEED_THRESHOLD_MPS) return 0.0
        if (speedMps >= LOW_SPEED_SLOPE_THRESHOLD_MPS) return 1.0
        val ratio = (speedMps - STANDSTILL_SPEED_THRESHOLD_MPS) /
            (LOW_SPEED_SLOPE_THRESHOLD_MPS - STANDSTILL_SPEED_THRESHOLD_MPS)
        return ratio * ratio
    }

    /**
     * Clamps dimensionless terrain slope grade `g = dh/ds` to physical bounds `[-MAX_PHYSICAL_SLOPE, +MAX_PHYSICAL_SLOPE]`
     * (`±35%`), sanitizing non-finite values to `0.0` (REQ-FIL-033.1).
     */
    internal fun clampPhysicalSlope(rawSlope: Double): Double {
        if (!rawSlope.isFinite()) return 0.0
        return rawSlope.coerceIn(-MAX_PHYSICAL_SLOPE, MAX_PHYSICAL_SLOPE)
    }

    private data class AltitudeSegmentResult(
        val points: List<SmoothedAltitudeSlopePoint>,
        val endAltitudeM: Double,
        val endBaroBiasM: Double,
        val endAscentM: Double,
        val endDescentM: Double
    )

    /**
     * Overflow-safe check for whether two consecutive altitude sample timestamps span a pause gap
     * (`dt > 5 s`, REQ-FIL-045.3).
     */
    internal fun isTimestampPauseGap(prevTimestampMs: Long, currTimestampMs: Long): Boolean {
        return currTimestampMs > prevTimestampMs &&
            prevTimestampMs <= Long.MAX_VALUE - MAX_ACTIVE_SAMPLE_GAP_MS &&
            currTimestampMs > prevTimestampMs + MAX_ACTIVE_SAMPLE_GAP_MS
    }

    /**
     * Executes forward-backward ERTS pass over raw altitude samples driven by smoothed speeds,
     * partitioning independent contiguous active segments across every pause boundary
     * (REQ-FIL-017.2, REQ-FIL-033.1, REQ-FIL-033.3, REQ-FIL-045.3).
     */
    fun smooth(samples: List<RawAltitudeSample>): List<SmoothedAltitudeSlopePoint> {
        val n = samples.size
        if (n == 0) return emptyList()

        var globalFallbackAlt = 0.0
        for (s in samples) {
            val candidate = s.referenceAltitudeM?.takeIf { it.isFinite() }
                ?: s.rawAltitudeM.takeIf { it.isFinite() }
            if (candidate != null) {
                globalFallbackAlt = candidate
                break
            }
        }

        val result = ArrayList<SmoothedAltitudeSlopePoint>(n)
        var segStart = 0
        var fallbackAlt = globalFallbackAlt
        var inheritedBaroBias = Double.NaN
        var runningAscent = 0.0
        var runningDescent = 0.0

        while (segStart < n) {
            var segEnd = segStart + 1
            while (segEnd < n) {
                val curr = samples[segEnd]
                val prev = samples[segEnd - 1]
                if (curr.isPauseGap || isTimestampPauseGap(prev.timestampMs, curr.timestampMs)) {
                    break
                }
                segEnd++
            }

            val segResult = smoothContiguousSegment(
                samples = samples.subList(segStart, segEnd),
                fallbackInitialAlt = fallbackAlt,
                inheritedBaroBias = inheritedBaroBias,
                ascentOffset = runningAscent,
                descentOffset = runningDescent
            )
            result.addAll(segResult.points)
            fallbackAlt = segResult.endAltitudeM
            inheritedBaroBias = segResult.endBaroBiasM
            runningAscent = segResult.endAscentM
            runningDescent = segResult.endDescentM
            segStart = segEnd
        }

        return result
    }

    private fun smoothContiguousSegment(
        samples: List<RawAltitudeSample>,
        fallbackInitialAlt: Double,
        inheritedBaroBias: Double,
        ascentOffset: Double,
        descentOffset: Double
    ): AltitudeSegmentResult {
        val n = samples.size
        if (n == 1) {
            val s0 = samples[0]
            val alt = s0.referenceAltitudeM?.takeIf { it.isFinite() }
                ?: s0.rawAltitudeM.takeIf { it.isFinite() }?.let { raw ->
                    if (inheritedBaroBias.isFinite()) raw - inheritedBaroBias else raw
                }
                ?: fallbackInitialAlt
            val pt = SmoothedAltitudeSlopePoint(
                timestampMs = s0.timestampMs,
                altitudeM = alt,
                slopePercent = 0.0,
                verticalSpeedMps = 0.0,
                ascentM = ascentOffset,
                descentM = descentOffset
            )
            return AltitudeSegmentResult(
                points = listOf(pt),
                endAltitudeM = alt,
                endBaroBiasM = inheritedBaroBias,
                endAscentM = ascentOffset,
                endDescentM = descentOffset
            )
        }

        // Determine sensor availability across samples (REQ-FIL-033.1, REQ-FIL-033.3)
        val hasBaroData = samples.any { it.rawAltitudeM.isFinite() }
        val hasReference = samples.any { it.referenceAltitudeM?.isFinite() == true }
        val directResetSteps = if (hasBaroData) detectDirectBaroResetSteps(samples) else emptySet()

        // State vector: [h, g, b_baro]
        val x = DoubleArray(STATE_DIM)
        val p = Array(STATE_DIM) { DoubleArray(STATE_DIM) }

        // Sequential finite-scan initialization preserving k=0 hasInitialRef for delayed-anchor convergence
        val firstSample = samples.first()
        val hasInitialRef = firstSample.referenceAltitudeM?.isFinite() == true
        if (!hasReference && hasBaroData && inheritedBaroBias.isFinite()) {
            val firstRawBaro = samples.first { it.rawAltitudeM.isFinite() }.rawAltitudeM
            x[0] = firstRawBaro - inheritedBaroBias
            x[1] = 0.0
            x[2] = inheritedBaroBias
        } else {
            var initialAlt = fallbackInitialAlt
            for (s in samples) {
                val candidate = s.referenceAltitudeM?.takeIf { it.isFinite() }
                    ?: s.rawAltitudeM.takeIf { it.isFinite() }
                if (candidate != null) {
                    initialAlt = candidate
                    break
                }
            }
            x[0] = initialAlt
            x[1] = 0.0
            x[2] = 0.0
        }
        p[0][0] = if (hasInitialRef) DEFAULT_VAR_DEM else 400.0
        p[1][1] = DEFAULT_INITIAL_SLOPE_VAR
        p[2][2] = if (hasReference) 400.0 else 1e-4

        val priors = ArrayList<DoubleArray>(n)
        val priorCovs = ArrayList<Array<DoubleArray>>(n)
        val jacobians = ArrayList<Array<DoubleArray>>(n)
        val posteriors = ArrayList<DoubleArray>(n)
        val postCovs = ArrayList<Array<DoubleArray>>(n)

        var lastTimeMs = samples.first().timestampMs
        var standstillDurationSec = 0.0
        var prevSpeedMps = 0.0
        var brakingHoldSec = 0.0

        for (k in 0 until n) {
            val sample = samples[k]
            val dtSec = if (k == 0) 1.0 else max(0.001, (sample.timestampMs - lastTimeMs) / 1000.0)
            lastTimeMs = sample.timestampMs

            val speedMps = if (sample.smoothedSpeedMps.isFinite() && sample.smoothedSpeedMps > 0.0) {
                sample.smoothedSpeedMps
            } else {
                0.0
            }
            val isStandstill = sample.isStandstill ||
                speedMps < STANDSTILL_SPEED_THRESHOLD_MPS ||
                (sample.carriageMode == PhoneCarriageMode.STATIONARY && speedMps < 0.35)
            val deltaDist = if (isStandstill) 0.0 else max(0.0, speedMps * dtSec)
            val gainScale = if (isStandstill) 0.0 else lowSpeedSlopeGainFactor(speedMps)

            // Detect sustained braking deceleration without disturbing steady climbing or TST-FIL-033.1 (REQ-FIL-040.4)
            if (isStandstill) {
                brakingHoldSec = 0.0
            } else if (k > 0 &&
                dtSec in BRAKING_ACCEL_MIN_DT_SEC..BRAKING_ACCEL_MAX_DT_SEC &&
                prevSpeedMps >= STANDSTILL_SPEED_THRESHOLD_MPS
            ) {
                val accelMps2 = (speedMps - prevSpeedMps) / dtSec
                if (accelMps2 <= -BRAKING_DECEL_THRESHOLD_MPS2) {
                    brakingHoldSec = BRAKING_HOLD_DURATION_SEC
                } else if (brakingHoldSec > 0.0) {
                    brakingHoldSec = max(0.0, brakingHoldSec - dtSec)
                }
            } else if (brakingHoldSec > 0.0) {
                brakingHoldSec = max(0.0, brakingHoldSec - dtSec)
            }
            prevSpeedMps = speedMps

            val brakingGainScale = if (!isStandstill &&
                brakingHoldSec > 0.0 &&
                speedMps < BRAKING_LOW_SPEED_CEILING_MPS
            ) {
                val ratio = (speedMps / BRAKING_LOW_SPEED_CEILING_MPS).coerceIn(0.0, 1.0)
                ratio * ratio
            } else {
                1.0
            }
            val effectiveSlopeGainScale = gainScale * brakingGainScale

            // 1. Predict (exact linear transition matching saved jacobians[k])
            val isBaroResetStep = k > 0 && sample.rawAltitudeM.isFinite() &&
                (sample.baroResetStep || directResetSteps.contains(k))
            var slopeTransition = 1.0
            if (k > 0) {
                if (isStandstill) {
                    // Standstill clamping (dh = 0) + contractive Ornstein-Uhlenbeck slope decay (REQ-FIL-033.1)
                    standstillDurationSec += dtSec
                    if (standstillDurationSec >= STANDSTILL_SLOPE_GRACE_SEC) {
                        slopeTransition = exp(-dtSec / STANDSTILL_SLOPE_DECAY_TAU_SEC)
                        x[1] *= slopeTransition
                        val targetSlopeVar = max(DEFAULT_INITIAL_SLOPE_VAR, p[1][1])
                        p[0][1] *= slopeTransition
                        p[1][0] = p[0][1]
                        p[1][2] *= slopeTransition
                        p[2][1] = p[1][2]
                        val alphaSq = slopeTransition * slopeTransition
                        p[1][1] = alphaSq * p[1][1] + (1.0 - alphaSq) * targetSlopeVar
                    }
                    p[2][2] += DEFAULT_Q_BIAS * dtSec
                } else {
                    standstillDurationSec = 0.0
                    x[0] += x[1] * deltaDist
                    val qgVal = DEFAULT_QG * deltaDist * effectiveSlopeGainScale
                    val qhVal = DEFAULT_QH * dtSec

                    val prevP11 = p[1][1]
                    val p00New = p[0][0] + 2.0 * deltaDist * p[0][1] + deltaDist * deltaDist * prevP11 + qhVal
                    val p01New = p[0][1] + deltaDist * prevP11
                    val p11New = min(MAX_SLOPE_VARIANCE, prevP11 + qgVal)
                    val p02New = p[0][2] + deltaDist * p[1][2]

                    p[0][0] = p00New
                    p[0][1] = p01New
                    p[1][0] = p01New
                    p[1][1] = p11New
                    p[0][2] = p02New
                    p[2][0] = p02New
                    p[2][2] += DEFAULT_Q_BIAS * dtSec
                }
                if (isBaroResetStep) {
                    // Sustained barometer calibration reset step: inject bias process-noise impulse
                    // before saving priorCovs[k] so forward update absorbs the jump into b_baro
                    // and backward RTS gain decouples h & slope across the reset (REQ-STB-025.4).
                    p[2][2] += BARO_RESET_Q_BIAS_IMPULSE
                }
            }

            // Save prior state & transition Jacobian
            val fMat = Array(STATE_DIM) { i ->
                DoubleArray(STATE_DIM) { j ->
                    when {
                        i == 1 && j == 1 -> slopeTransition
                        i == j -> 1.0
                        i == 0 && j == 1 && !isStandstill -> deltaDist
                        else -> 0.0
                    }
                }
            }
            priors.add(x.clone())
            priorCovs.add(Array(STATE_DIM) { i -> p[i].clone() })
            jacobians.add(fMat)

            // 2. Ingest Barometer (with carriage-mode-scaled variance & Joseph SPD covariance update)
            if (sample.rawAltitudeM.isFinite()) {
                val zBaro = sample.rawAltitudeM
                val y = zBaro - (x[0] + x[2])
                val varBaro = sample.carriageMode.profile.ertsBaroVarianceM2

                if (isStandstill) {
                    val sBias = p[2][2] + varBaro
                    val kBias = p[2][2] / sBias
                    val yStep = if (isBaroResetStep) y else applyHuber(y, sBias)
                    x[2] += kBias * yStep
                    josephCovarianceUpdate3x3(
                        p = p,
                        kVec = doubleArrayOf(0.0, 0.0, kBias),
                        hVec = doubleArrayOf(0.0, 0.0, 1.0),
                        rVar = varBaro
                    )
                } else {
                    val brakingVarScale = when {
                        hasReference && brakingHoldSec > 0.0 -> BRAKING_BARO_VAR_MULTIPLIER
                        !hasReference && brakingHoldSec > 0.0 && speedMps < BRAKING_LOW_SPEED_CEILING_MPS ->
                            BRAKING_BARO_VAR_NO_REF_MULTIPLIER
                        else -> 1.0
                    }
                    val effectiveVarBaro = varBaro * brakingVarScale
                    val s = p[0][0] + 2.0 * p[0][2] + p[2][2] + effectiveVarBaro
                    val yH = if (isBaroResetStep) y else applyHuber(y, s)
                    val k0 = (p[0][0] + p[0][2]) / s
                    val k1 = ((p[1][0] + p[1][2]) / s) * effectiveSlopeGainScale
                    val k2 = (p[2][0] + p[2][2]) / s

                    x[0] += k0 * yH
                    x[1] = clampPhysicalSlope(x[1] + k1 * yH)
                    x[2] += k2 * yH

                    josephCovarianceUpdate3x3(
                        p = p,
                        kVec = doubleArrayOf(k0, k1, k2),
                        hVec = doubleArrayOf(1.0, 0.0, 1.0),
                        rVar = effectiveVarBaro
                    )
                }
            }

            // 3. Ingest DEM/GPS reference anchor (decouple slope when barometer is present; REQ-FIL-033.1/.3)
            val refAlt = sample.referenceAltitudeM
            if (refAlt != null && refAlt.isFinite()) {
                val yRef = refAlt - x[0]
                val sRef = p[0][0] + DEFAULT_VAR_DEM
                val yRefH = applyHuber(yRef, sRef)

                val k0 = p[0][0] / sRef
                val k1 = if (hasBaroData || isStandstill) 0.0 else (p[1][0] / sRef) * gainScale
                val k2 = p[2][0] / sRef

                x[0] += k0 * yRefH
                x[1] = clampPhysicalSlope(x[1] + k1 * yRefH)
                x[2] += k2 * yRefH

                josephCovarianceUpdate3x3(
                    p = p,
                    kVec = doubleArrayOf(k0, k1, k2),
                    hVec = doubleArrayOf(1.0, 0.0, 0.0),
                    rVar = DEFAULT_VAR_DEM
                )
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
        smoothedStates[lastIdx][1] = clampPhysicalSlope(smoothedStates[lastIdx][1])

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
            smoothedStates[k][1] = clampPhysicalSlope(smoothedStates[k][1])

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
        var cumulativeAscent = ascentOffset
        var cumulativeDescent = descentOffset
        var previousAlt = Double.NaN
        var anchorAlt = Double.NaN

        for (k in 0 until n) {
            val state = smoothedStates[k]
            val alt = state[0]
            val grade = clampPhysicalSlope(state[1])
            val sample = samples[k]
            val speedMps = if (sample.smoothedSpeedMps.isFinite() && sample.smoothedSpeedMps > 0.0) {
                sample.smoothedSpeedMps
            } else {
                0.0
            }
            val isStandstill = sample.isStandstill ||
                speedMps < STANDSTILL_SPEED_THRESHOLD_MPS ||
                (sample.carriageMode == PhoneCarriageMode.STATIONARY && speedMps < 0.35)

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

            if (isStandstill || anchorAlt.isNaN()) {
                anchorAlt = alt
            } else {
                val deltaFromAnchor = alt - anchorAlt
                if (deltaFromAnchor >= ASCENT_DESCENT_HYSTERESIS_METERS) {
                    cumulativeAscent += deltaFromAnchor
                    anchorAlt = alt
                } else if (deltaFromAnchor <= -ASCENT_DESCENT_HYSTERESIS_METERS) {
                    cumulativeDescent += -deltaFromAnchor
                    anchorAlt = alt
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

        val endAlt = result.last().altitudeM.takeIf { it.isFinite() } ?: fallbackInitialAlt
        val endBias = if (hasBaroData && smoothedStates.last()[2].isFinite()) {
            smoothedStates.last()[2]
        } else {
            inheritedBaroBias
        }
        return AltitudeSegmentResult(
            points = result,
            endAltitudeM = endAlt,
            endBaroBiasM = endBias,
            endAscentM = cumulativeAscent,
            endDescentM = cumulativeDescent
        )
    }

    /**
     * Detects sustained barometric reset/calibration step discontinuities directly on un-slewed
     * [RawAltitudeSample.rawAltitudeM] series (e.g. crash recovery barometer offset jump) while
     * ignoring transient `<= 2` sample pocket spikes (`REQ-TRK-057.1`) and genuine climbs
     * (`REQ-FIL-033.2`, `REQ-STB-025.4`).
     */
    internal fun detectDirectBaroResetSteps(samples: List<RawAltitudeSample>): Set<Int> {
        val validIndices = samples.indices.filter { samples[it].rawAltitudeM.isFinite() }
        if (validIndices.size < BARO_RESET_MIN_SUSTAINED_SAMPLES + 1) return emptySet()

        val steps = mutableSetOf<Int>()
        for (pos in 1 until validIndices.size) {
            val prevIdx = validIndices[pos - 1]
            val currIdx = validIndices[pos]
            val dtSec = max(1.0, (samples[currIdx].timestampMs - samples[prevIdx].timestampMs) / 1000.0)
            val vPrev = samples[prevIdx].smoothedSpeedMps.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val vCurr = samples[currIdx].smoothedSpeedMps.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val refSpeed = max(max(vPrev, vCurr), 2.0)
            val thresholdM = max(BARO_RESET_STEP_MIN_M, MAX_PHYSICAL_SLOPE * refSpeed * min(dtSec, 15.0) + 3.0)

            val jump = samples[currIdx].rawAltitudeM - samples[prevIdx].rawAltitudeM
            if (abs(jump) < thresholdM) continue

            val preStart = max(0, pos - 5)
            val preVals = (preStart until pos).map { samples[validIndices[it]].rawAltitudeM }.sorted()
            val preMedian = (preVals[(preVals.size - 1) / 2] + preVals[preVals.size / 2]) / 2.0
            val preStableCount = preVals.count { abs(it - preMedian) <= 0.5 * thresholdM }
            if (preStableCount < min(3, preVals.size) || abs(samples[prevIdx].rawAltitudeM - preMedian) > 0.5 * thresholdM) {
                continue
            }

            val postEnd = min(validIndices.size, pos + BARO_RESET_LOOKAHEAD_SAMPLES)
            if (postEnd - pos < BARO_RESET_MIN_SUSTAINED_SAMPLES) continue
            val postVals = (pos until postEnd).map { samples[validIndices[it]].rawAltitudeM }
            val sortedPost = postVals.sorted()
            val postMedian = (sortedPost[(sortedPost.size - 1) / 2] + sortedPost[sortedPost.size / 2]) / 2.0
            val stepOffset = postMedian - preMedian

            val sustainedCount = postVals.count { v ->
                (v - preMedian) * stepOffset > 0.0 && abs(v - preMedian) >= 0.5 * abs(stepOffset)
            }
            if (abs(stepOffset) >= thresholdM && stepOffset * jump > 0.0 && sustainedCount >= BARO_RESET_MIN_SUSTAINED_SAMPLES) {
                steps.add(currIdx)
            }
        }
        return steps
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
     * Exact symmetric positive-definite 3x3 Joseph-form covariance update
     * $P^+ = (I - K H) P^- (I - K H)^T + K R K^T$, safe for damped or zeroed suboptimal gains
     * (REQ-FIL-033.1, REQ-FIL-033.3).
     */
    internal fun josephCovarianceUpdate3x3(
        p: Array<DoubleArray>,
        kVec: DoubleArray,
        hVec: DoubleArray,
        rVar: Double
    ) {
        val a = Array(STATE_DIM) { i ->
            DoubleArray(STATE_DIM) { j ->
                (if (i == j) 1.0 else 0.0) - kVec[i] * hVec[j]
            }
        }
        val ap = matMul3x3(a, p)
        val aT = transpose3x3(a)
        val apAt = matMul3x3(ap, aT)
        for (i in 0 until STATE_DIM) {
            for (j in 0 until STATE_DIM) {
                p[i][j] = apAt[i][j] + rVar * kVec[i] * kVec[j]
            }
        }
        p[0][0] = max(1e-6, p[0][0])
        p[1][1] = max(1e-8, p[1][1])
        p[2][2] = max(1e-6, p[2][2])
        symmetrize3x3(p)
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
