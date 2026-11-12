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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlin.math.abs

/**
 * Protocol provenance for post-workout HRV threshold analysis (REQ-PER-036.1, REQ-PER-036.5).
 */
enum class HrvProtocolType {
    FREE_WORKOUT,
    GUIDED_STEP,
    GUIDED_RAMP
}

/**
 * Three-tier statistical confidence classification for estimated thresholds (REQ-PER-036.4).
 */
enum class ThresholdConfidence {
    HIGH,
    MEDIUM,
    LOW,
    INSUFFICIENT_SPAN
}

/**
 * Decimated 60-second window sample or step average used for decorrelated regression (REQ-PER-036.2).
 */
data class DecimatedHrvPoint(
    val intensity: Double,
    val alpha1: Double,
    val artifactRate: Double,
    val sampleCount: Int,
    val timeSec: Long
)

/**
 * Single estimated threshold (HRVT1 or HRVT2) with independent span and confidence gating (REQ-PER-036.3).
 */
data class SingleThresholdEstimate(
    val thresholdTarget: Double,
    val intensity: Double?,
    val confidence: ThresholdConfidence,
    val spanSatisfied: Boolean,
    @StringRes val diagnosticReasonRes: Int?
)

/**
 * Complete evaluation result of confidence-gated DFA alpha 1 threshold regression (REQ-PER-036).
 */
data class HrvThresholdEstimateResult(
    val hrvt1: SingleThresholdEstimate,
    val hrvt2: SingleThresholdEstimate,
    val overallConfidence: ThresholdConfidence,
    val rSquared: Double?,
    val slope: Double?,
    val intercept: Double?,
    val effectiveSampleSize: Int,
    val meanArtifactRate: Double,
    val protocolType: HrvProtocolType,
    val intensityMode: HrvScatterIntensityMode,
    val decimatedPoints: List<DecimatedHrvPoint>
)

/**
 * Lag-aligned, confidence-gated estimator for HRVT1 (aerobic) and HRVT2 (anaerobic) thresholds
 * via Ordinary Least Squares linear regression against decimated DFA alpha 1 telemetry (REQ-PER-036).
 */
object HrvThresholdEstimator {

    const val WARMUP_DURATION_SEC = 600L // 10 minutes
    const val FRESH_WINDOW_MAX_SEC = 3600L // 60 minutes
    const val MAX_ARTIFACT_RATE = 5.0 // 5.0%
    const val MIN_ALPHA1 = 0.35
    const val MAX_ALPHA1 = 1.15
    const val DECIMATION_BIN_SEC = 60L // 60 seconds
    const val RAMP_MRT_LAG_SEC = 45L // 45 seconds Mean Response Time

    /**
     * Estimates lag-aligned, confidence-gated HRVT1 and HRVT2 thresholds from workout trackpoints.
     *
     * @param points Raw list of [PathPoint] trackpoints enriched with post-workout centered HRV.
     * @param mode Intensity mode (Heart Rate, Cycling Power, or Running Pace).
     * @param protocolType Protocol provenance (Free Workout, Guided Step, or Guided Continuous Ramp).
     * @param dfaA1ThresholdT1 Configured target DFA alpha 1 for HRVT1 (default 0.75).
     * @param dfaA1ThresholdT2 Configured target DFA alpha 1 for HRVT2 (default 0.50).
     */
    fun estimateThresholds(
        points: List<PathPoint>,
        mode: HrvScatterIntensityMode = HrvScatterIntensityMode.HEART_RATE,
        protocolType: HrvProtocolType = HrvProtocolType.FREE_WORKOUT,
        dfaA1ThresholdT1: Double = 0.75,
        dfaA1ThresholdT2: Double = 0.50
    ): HrvThresholdEstimateResult {
        val emptyResult = HrvThresholdEstimateResult(
            hrvt1 = SingleThresholdEstimate(dfaA1ThresholdT1, null, ThresholdConfidence.INSUFFICIENT_SPAN, false, R.string.hrv_threshold_diag_steady),
            hrvt2 = SingleThresholdEstimate(dfaA1ThresholdT2, null, ThresholdConfidence.INSUFFICIENT_SPAN, false, R.string.hrv_threshold_diag_steady),
            overallConfidence = ThresholdConfidence.INSUFFICIENT_SPAN,
            rSquared = null,
            slope = null,
            intercept = null,
            effectiveSampleSize = 0,
            meanArtifactRate = 0.0,
            protocolType = protocolType,
            intensityMode = mode,
            decimatedPoints = emptyList()
        )

        if (points.isEmpty()) return emptyResult

        // 1. Fresh Window Selection (REQ-PER-036.1)
        val hasTimestamps = points.size >= 2 && points.any { it.timeSec > 0L }
        val rawFiltered = mutableListOf<RawPoint>()

        for (pt in points) {
            val a1 = pt.hrvDfaA1 ?: continue
            if (a1 < MIN_ALPHA1 || a1 > MAX_ALPHA1) continue

            val art = pt.hrvArtifactRate ?: 0.0
            if (art > MAX_ARTIFACT_RATE) continue

            if (hasTimestamps && protocolType == HrvProtocolType.FREE_WORKOUT) {
                if (pt.timeSec < WARMUP_DURATION_SEC || pt.timeSec > FRESH_WINDOW_MAX_SEC) {
                    continue
                }
            }

            val intensity = when (mode) {
                HrvScatterIntensityMode.HEART_RATE -> pt.hr?.takeIf { it > 0 }?.toDouble()
                HrvScatterIntensityMode.POWER -> pt.power?.takeIf { it > 0 }?.toDouble()
                HrvScatterIntensityMode.PACE -> pt.speedMps?.takeIf { it >= 0.55 }?.let { 1.0 / it }
            } ?: continue

            rawFiltered.add(RawPoint(intensity = intensity, alpha1 = a1, artifactRate = art, timeSec = pt.timeSec))
        }

        if (rawFiltered.isEmpty()) return emptyResult

        // 2. Mean Response Time Lag Compensation for Continuous Ramps (REQ-PER-036.2)
        val lagAlignedPoints = if (protocolType == HrvProtocolType.GUIDED_RAMP && hasTimestamps) {
            applyContinuousRampMrtLag(rawFiltered, RAMP_MRT_LAG_SEC)
        } else {
            rawFiltered
        }

        // 3. Decimated Grid Binning (60s bins or 12-sample chunks) (REQ-PER-036.2)
        val decimated = decimatePoints(lagAlignedPoints, hasTimestamps)
        if (decimated.size < 2) {
            val meanArt = if (decimated.isNotEmpty()) decimated.map { it.artifactRate }.average() else 0.0
            return emptyResult.copy(
                effectiveSampleSize = decimated.size,
                meanArtifactRate = meanArt,
                decimatedPoints = decimated
            )
        }

        // 4. Decimated Grid Linear Regression
        val n = decimated.size.toDouble()
        val meanX = decimated.sumOf { it.intensity } / n
        val meanY = decimated.sumOf { it.alpha1 } / n

        var ssXx = 0.0
        var ssYy = 0.0
        var ssXy = 0.0

        for (p in decimated) {
            val dx = p.intensity - meanX
            val dy = p.alpha1 - meanY
            ssXx += dx * dx
            ssYy += dy * dy
            ssXy += dx * dy
        }

        if (ssXx < 1e-6) {
            val meanArt = decimated.map { it.artifactRate }.average()
            return emptyResult.copy(effectiveSampleSize = decimated.size, meanArtifactRate = meanArt, decimatedPoints = decimated)
        }

        val slope = ssXy / ssXx
        val intercept = meanY - slope * meanX
        val rSquared = if (ssYy > 1e-6) {
            ((ssXy * ssXy) / (ssXx * ssYy)).coerceIn(0.0, 1.0)
        } else 0.0

        val meanArtifactRate = decimated.map { it.artifactRate }.average()
        val effectiveSampleSize = decimated.size

        // 5. Slope Direction Check (REQ-PER-036 / Physiological Invariants)
        // For Heart Rate and Power: as workload increases, alpha 1 decreases (slope < -1e-6).
        // For Pace (s/m): as s/m increases (slower speed/lower workload), alpha 1 increases (slope > 1e-6).
        val isSlopeDirectionValid = when (mode) {
            HrvScatterIntensityMode.PACE -> slope > 1e-6
            HrvScatterIntensityMode.HEART_RATE, HrvScatterIntensityMode.POWER -> slope < -1e-6
        }

        // 6. Independent Span Gating (REQ-PER-036.3)
        val minAlpha1 = decimated.minOf { it.alpha1 }
        val maxAlpha1 = decimated.maxOf { it.alpha1 }

        val t1SpanSatisfied = (maxAlpha1 >= dfaA1ThresholdT1 + 0.10) && (minAlpha1 <= dfaA1ThresholdT1 - 0.10)
        val t2SpanSatisfied = (maxAlpha1 >= dfaA1ThresholdT2 + 0.10) && (minAlpha1 <= dfaA1ThresholdT2 - 0.05)

        // 7. Three-Tier Confidence Evaluation (REQ-PER-036.4)
        val minHighN = if (protocolType == HrvProtocolType.GUIDED_STEP) 5 else 10
        val minMedN = if (protocolType == HrvProtocolType.GUIDED_STEP) 4 else 8

        fun evaluateThreshold(
            target: Double,
            spanSatisfied: Boolean
        ): SingleThresholdEstimate {
            if (!spanSatisfied) {
                return SingleThresholdEstimate(
                    thresholdTarget = target,
                    intensity = null,
                    confidence = ThresholdConfidence.INSUFFICIENT_SPAN,
                    spanSatisfied = false,
                    diagnosticReasonRes = R.string.hrv_threshold_diag_steady
                )
            }

            val rawIntensity = if (isSlopeDirectionValid) (target - intercept) / slope else null
            val intensityValid = rawIntensity?.let { isIntensityPlausible(it, mode) } == true
            val intensity = if (intensityValid) rawIntensity else null

            val confidence = when {
                intensity == null || !isSlopeDirectionValid -> ThresholdConfidence.LOW
                rSquared >= 0.70 && meanArtifactRate <= 3.0 && effectiveSampleSize >= minHighN -> ThresholdConfidence.HIGH
                rSquared >= 0.55 && meanArtifactRate <= 5.0 && effectiveSampleSize >= minMedN -> ThresholdConfidence.MEDIUM
                else -> ThresholdConfidence.LOW
            }

            val diagnostic = when (confidence) {
                ThresholdConfidence.HIGH, ThresholdConfidence.MEDIUM -> null
                ThresholdConfidence.LOW -> when {
                    !isSlopeDirectionValid -> R.string.hrv_threshold_diag_low_r2
                    meanArtifactRate > 5.0 -> R.string.hrv_threshold_diag_high_artifact
                    effectiveSampleSize < minMedN -> R.string.hrv_threshold_diag_short_duration
                    rSquared < 0.55 -> R.string.hrv_threshold_diag_low_r2
                    else -> R.string.hrv_threshold_diag_low_r2
                }
                ThresholdConfidence.INSUFFICIENT_SPAN -> R.string.hrv_threshold_diag_steady
            }

            return SingleThresholdEstimate(
                thresholdTarget = target,
                intensity = intensity,
                confidence = confidence,
                spanSatisfied = true,
                diagnosticReasonRes = diagnostic
            )
        }

        var estT1 = evaluateThreshold(dfaA1ThresholdT1, t1SpanSatisfied)
        var estT2 = evaluateThreshold(dfaA1ThresholdT2, t2SpanSatisfied)

        // Enforce physiological monotonicity: HRVT1 must be lower workload than HRVT2
        val isInverted = if (estT1.intensity != null && estT2.intensity != null) {
            when (mode) {
                HrvScatterIntensityMode.PACE -> estT1.intensity <= estT2.intensity
                HrvScatterIntensityMode.HEART_RATE, HrvScatterIntensityMode.POWER -> estT1.intensity >= estT2.intensity
            }
        } else false

        if (isInverted) {
            estT1 = estT1.copy(intensity = null, confidence = ThresholdConfidence.LOW, diagnosticReasonRes = R.string.hrv_threshold_diag_low_r2)
            estT2 = estT2.copy(intensity = null, confidence = ThresholdConfidence.LOW, diagnosticReasonRes = R.string.hrv_threshold_diag_low_r2)
        }

        val overallConfidence = when {
            estT1.confidence == ThresholdConfidence.HIGH && estT2.confidence == ThresholdConfidence.HIGH -> ThresholdConfidence.HIGH
            estT1.confidence == ThresholdConfidence.HIGH && !estT2.spanSatisfied -> ThresholdConfidence.HIGH
            estT2.confidence == ThresholdConfidence.HIGH && !estT1.spanSatisfied -> ThresholdConfidence.HIGH
            estT1.confidence == ThresholdConfidence.MEDIUM || estT2.confidence == ThresholdConfidence.MEDIUM -> ThresholdConfidence.MEDIUM
            estT1.confidence == ThresholdConfidence.LOW || estT2.confidence == ThresholdConfidence.LOW -> ThresholdConfidence.LOW
            else -> ThresholdConfidence.INSUFFICIENT_SPAN
        }

        return HrvThresholdEstimateResult(
            hrvt1 = estT1,
            hrvt2 = estT2,
            overallConfidence = overallConfidence,
            rSquared = rSquared,
            slope = slope,
            intercept = intercept,
            effectiveSampleSize = effectiveSampleSize,
            meanArtifactRate = meanArtifactRate,
            protocolType = protocolType,
            intensityMode = mode,
            decimatedPoints = decimated
        )
    }

    private fun isIntensityPlausible(intensity: Double, mode: HrvScatterIntensityMode): Boolean {
        return when (mode) {
            HrvScatterIntensityMode.PACE -> intensity in 0.05..2.0 // 0.83 min/km to 33.3 min/km
            else -> intensity in 30.0..600.0 // bpm or Watts
        }
    }

    private fun applyContinuousRampMrtLag(points: List<RawPoint>, lagSec: Long): List<RawPoint> {
        if (points.size < 2) return points
        val sorted = points.sortedBy { it.timeSec }
        val result = mutableListOf<RawPoint>()

        for (pt in sorted) {
            val targetTime = pt.timeSec - lagSec
            val alignedIntensity = if (targetTime <= sorted.first().timeSec) {
                sorted.first().intensity
            } else {
                val prior = sorted.lastOrNull { it.timeSec <= targetTime } ?: sorted.first()
                val next = sorted.firstOrNull { it.timeSec > targetTime } ?: prior
                if (next.timeSec == prior.timeSec) {
                    prior.intensity
                } else {
                    val ratio = (targetTime - prior.timeSec).toDouble() / (next.timeSec - prior.timeSec).toDouble()
                    prior.intensity + ratio * (next.intensity - prior.intensity)
                }
            }
            result.add(pt.copy(intensity = alignedIntensity))
        }
        return result
    }

    private fun decimatePoints(points: List<RawPoint>, hasTimestamps: Boolean): List<DecimatedHrvPoint> {
        if (hasTimestamps) {
            val grouped = points.groupBy { it.timeSec / DECIMATION_BIN_SEC }
            return grouped.map { (binKey, binPoints) ->
                DecimatedHrvPoint(
                    intensity = binPoints.map { it.intensity }.average(),
                    alpha1 = binPoints.map { it.alpha1 }.average(),
                    artifactRate = binPoints.map { it.artifactRate }.average(),
                    sampleCount = binPoints.size,
                    timeSec = binKey * DECIMATION_BIN_SEC
                )
            }.sortedBy { it.timeSec }
        } else {
            // Decimate by 12-sample chunks (12 x 5s = 60s)
            return points.chunked(12).mapIndexed { index, chunk ->
                DecimatedHrvPoint(
                    intensity = chunk.map { it.intensity }.average(),
                    alpha1 = chunk.map { it.alpha1 }.average(),
                    artifactRate = chunk.map { it.artifactRate }.average(),
                    sampleCount = chunk.size,
                    timeSec = index * DECIMATION_BIN_SEC
                )
            }
        }
    }

    private data class RawPoint(
        val intensity: Double,
        val alpha1: Double,
        val artifactRate: Double,
        val timeSec: Long
    )
}
