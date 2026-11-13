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

package com.atrainingtracker.banalservice.devices.hrv

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, stateless DSP analyzer detecting firmware-smoothed and quantized synthetic RR streams
 * pursuant to REQ-TRK-067.
 *
 * Employs three physiological and mathematical gates:
 * 1. Zero-Diff Synthetic Rate: Flags streams where > 55% of consecutive differences |ΔRR| < 1.0 ms
 *    over >= 30 beats (typical of integer-BPM synthetic streams).
 * 2. Discordance Rule: Flags streams where rMSSD < 10.0 ms AND DFA a1 > 1.35 (physiologically
 *    mutually exclusive in authentic human sinus rhythm).
 * 3. Turning-Point Ratio (TPR): Flags streams where TPR < 0.20 alongside DFA a1 > 1.30
 *    (capturing monotonic moving-average ramps where natural respiratory sinus arrhythmia turning points are lost).
 */
object HrvSignalQualityAnalyzer {

    const val MIN_BEATS: Int = 30
    const val MAX_ARTIFACT_RATE_PERCENT: Double = 5.0
    const val ZERO_DIFF_THRESHOLD_MS: Double = 1.0
    const val MAX_ZERO_DIFF_RATE: Double = 0.55
    const val SMOOTHED_RMSSD_THRESHOLD_MS: Double = 10.0
    const val SMOOTHED_DFA_A1_THRESHOLD: Double = 1.35
    const val SMOOTHED_TPR_THRESHOLD: Double = 0.20
    const val SMOOTHED_TPR_DFA_A1_THRESHOLD: Double = 1.30
    const val SMOOTHED_DISCORDANT_TPR_THRESHOLD: Double = 0.28
    const val SMOOTHED_DISCORDANT_MIN_RMSSD_MS: Double = 2.0

    /**
     * Analyzes an RR interval stream and classifies signal quality.
     *
     * @param rawRrIntervalsMs Sequence of raw beat intervals in milliseconds.
     * @param correctedRrIntervalsMs Optional artifact-corrected intervals (defaults to raw).
     * @param rmssdMs Optional precomputed rMSSD in milliseconds.
     * @param dfaAlpha1 Optional precomputed DFA alpha 1 exponent.
     * @param artifactRatePercent Optional precomputed artifact percentage (0.0 to 100.0).
     */
    @JvmStatic
    fun analyze(
        rawRrIntervalsMs: List<Double>,
        correctedRrIntervalsMs: List<Double>? = null,
        rmssdMs: Double? = null,
        dfaAlpha1: Double? = null,
        artifactRatePercent: Double? = null
    ): HrvSignalQualityResult {
        val n = rawRrIntervalsMs.size
        val effectiveArtifactRate = artifactRatePercent ?: 0.0

        if (n < MIN_BEATS) {
            return HrvSignalQualityResult(
                status = HrvSignalQualityStatus.INSUFFICIENT_DATA,
                details = "Insufficient data: $n beats (minimum $MIN_BEATS required)",
                rmssdMs = rmssdMs,
                dfaAlpha1 = dfaAlpha1,
                artifactRatePercent = effectiveArtifactRate,
                beatCount = n
            )
        }

        if (effectiveArtifactRate > MAX_ARTIFACT_RATE_PERCENT) {
            return HrvSignalQualityResult(
                status = HrvSignalQualityStatus.HIGH_ARTIFACTS,
                details = String.format(
                    Locale.US,
                    "High artifact rate: %.1f%% (exceeds threshold %.1f%%)",
                    effectiveArtifactRate,
                    MAX_ARTIFACT_RATE_PERCENT
                ),
                rmssdMs = rmssdMs,
                dfaAlpha1 = dfaAlpha1,
                artifactRatePercent = effectiveArtifactRate,
                beatCount = n
            )
        }

        val zeroDiffRate = computeZeroDiffRate(rawRrIntervalsMs)
        val tpr = computeTurningPointRatio(rawRrIntervalsMs)

        val computedRmssd = rmssdMs ?: computeRmssd(rawRrIntervalsMs)
        val computedDfa = dfaAlpha1 ?: run {
            val series = correctedRrIntervalsMs ?: rawRrIntervalsMs
            val alpha = DfaCalculator.computeDfaAlpha1(series.toDoubleArray())
            if (alpha.isFinite() && !alpha.isNaN()) alpha else null
        }

        // 1. Quantized Synthetic detection: > 55% zero differences (|ΔRR| < 1.0 ms)
        if (zeroDiffRate > MAX_ZERO_DIFF_RATE) {
            return HrvSignalQualityResult(
                status = HrvSignalQualityStatus.QUANTIZED_SYNTHETIC,
                details = String.format(
                    Locale.US,
                    "Quantized synthetic stream: %.1f%% zero-diffs (threshold > %.1f%%)",
                    zeroDiffRate * 100.0,
                    MAX_ZERO_DIFF_RATE * 100.0
                ),
                rmssdMs = computedRmssd,
                dfaAlpha1 = computedDfa,
                turningPointRatio = tpr,
                zeroDiffRate = zeroDiffRate,
                artifactRatePercent = effectiveArtifactRate,
                beatCount = n
            )
        }

        // 2. Smoothed Stream detection
        // ATT-7533: Authentic Polar H10 ECG during low-vagal/sympathetic/evening states has low rMSSD (< 10ms)
        // with baroreflex Mayer-wave drift (a1 > 1.35) but normal respiratory sign reversals (TPR >= 0.28).
        // Require either suppressed turning points (TPR < 0.28) or extreme variance collapse (rMSSD < 2.0ms).
        val hasDiscordantTprOrVariance = (tpr != null && tpr < SMOOTHED_DISCORDANT_TPR_THRESHOLD) ||
            (computedRmssd != null && computedRmssd < SMOOTHED_DISCORDANT_MIN_RMSSD_MS)
        val isDiscordant = computedRmssd != null && computedRmssd < SMOOTHED_RMSSD_THRESHOLD_MS &&
            computedDfa != null && computedDfa > SMOOTHED_DFA_A1_THRESHOLD &&
            hasDiscordantTprOrVariance
        val isTprSmoothed = tpr != null && tpr < SMOOTHED_TPR_THRESHOLD &&
            computedDfa != null && computedDfa > SMOOTHED_TPR_DFA_A1_THRESHOLD

        if (isDiscordant || isTprSmoothed) {
            val reason = when {
                isDiscordant && isTprSmoothed -> String.format(
                    Locale.US,
                    "Smoothed stream: rMSSD=%.1f ms (< 10 ms) with a1=%.2f (> 1.35) and TPR=%.2f (< 0.20)",
                    computedRmssd,
                    computedDfa,
                    tpr
                )
                isDiscordant -> String.format(
                    Locale.US,
                    "Smoothed stream (discordance): rMSSD=%.1f ms (< 10 ms) with a1=%.2f (> 1.35) and TPR=%s",
                    computedRmssd,
                    computedDfa,
                    tpr?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a"
                )
                else -> String.format(
                    Locale.US,
                    "Smoothed stream (low TPR): TPR=%.2f (< 0.20) with a1=%.2f (> 1.30)",
                    tpr,
                    computedDfa
                )
            }
            return HrvSignalQualityResult(
                status = HrvSignalQualityStatus.SMOOTHED_STREAM,
                details = reason,
                rmssdMs = computedRmssd,
                dfaAlpha1 = computedDfa,
                turningPointRatio = tpr,
                zeroDiffRate = zeroDiffRate,
                artifactRatePercent = effectiveArtifactRate,
                beatCount = n
            )
        }

        return HrvSignalQualityResult(
            status = HrvSignalQualityStatus.VALID,
            details = "Valid sinus rhythm",
            rmssdMs = computedRmssd,
            dfaAlpha1 = computedDfa,
            turningPointRatio = tpr,
            zeroDiffRate = zeroDiffRate,
            artifactRatePercent = effectiveArtifactRate,
            beatCount = n
        )
    }

    /**
     * Evaluates signal quality from a list of [FilteredRrBeat]s.
     */
    @JvmStatic
    fun analyzeBeats(
        beats: List<FilteredRrBeat>,
        artifactRatePercent: Double? = null,
        rmssdMs: Double? = null,
        dfaAlpha1: Double? = null
    ): HrvSignalQualityResult {
        val raw = beats.map { it.rawRrMs }
        val corrected = beats.map { it.correctedRrMs }
        val effectiveArtifactRate = artifactRatePercent ?: run {
            if (beats.isEmpty()) 0.0 else (beats.count { it.isArtifact }.toDouble() / beats.size) * 100.0
        }
        return analyze(
            rawRrIntervalsMs = raw,
            correctedRrIntervalsMs = corrected,
            rmssdMs = rmssdMs,
            dfaAlpha1 = dfaAlpha1,
            artifactRatePercent = effectiveArtifactRate
        )
    }

    /**
     * Computes the fraction of consecutive beat differences satisfying |ΔRR| < [ZERO_DIFF_THRESHOLD_MS].
     */
    fun computeZeroDiffRate(intervals: List<Double>): Double {
        if (intervals.size < 2) return 0.0
        var zeroDiffCount = 0
        for (i in 1 until intervals.size) {
            if (abs(intervals[i] - intervals[i - 1]) < ZERO_DIFF_THRESHOLD_MS) {
                zeroDiffCount++
            }
        }
        return zeroDiffCount.toDouble() / (intervals.size - 1)
    }

    /**
     * Computes the Turning-Point Ratio (TPR) of consecutive non-zero differences.
     * TPR = (count of sign reversals) / (number of non-zero difference transitions).
     */
    fun computeTurningPointRatio(intervals: List<Double>): Double? {
        if (intervals.size < 3) return null

        val nonZeroDiffs = mutableListOf<Double>()
        for (i in 1 until intervals.size) {
            val diff = intervals[i] - intervals[i - 1]
            if (abs(diff) >= 0.01) {
                nonZeroDiffs.add(diff)
            }
        }

        if (nonZeroDiffs.size < 2) {
            return 0.0
        }

        var signChanges = 0
        for (i in 0 until nonZeroDiffs.size - 1) {
            val d1 = nonZeroDiffs[i]
            val d2 = nonZeroDiffs[i + 1]
            if ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) {
                signChanges++
            }
        }

        return signChanges.toDouble() / (nonZeroDiffs.size - 1)
    }

    private fun computeRmssd(intervals: List<Double>): Double? {
        if (intervals.size < 2) return null
        var sumSquares = 0.0
        for (i in 1 until intervals.size) {
            val diff = intervals[i] - intervals[i - 1]
            sumSquares += diff * diff
        }
        return sqrt(sumSquares / (intervals.size - 1))
    }
}
