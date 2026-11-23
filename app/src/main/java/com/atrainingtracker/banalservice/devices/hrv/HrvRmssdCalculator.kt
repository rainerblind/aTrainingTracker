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

import kotlin.math.sqrt

/**
 * Pure-JVM canonical rMSSD (Root Mean Square of Successive Differences) calculator
 * shared across live cockpit HRV, centered post-workout recomputation, guided morning
 * resting HRV checks, and RR signal quality analysis (`REQ-MIG-061.2`).
 *
 * Caller minimum valid pair thresholds are intentionally differentiated by physiological context:
 * - [LIVE_ROLLING_MIN_VALID_PAIRS] (`10`): Causal 60s live cockpit rolling window (`HrvRollingEngine`)
 *   suppresses noisy early-window spikes until at least 10 clean successive NN pairs (~10s) exist.
 * - [POST_WORKOUT_MIN_VALID_PAIRS] (`3`): Non-causal centered ±30s post-workout window
 *   (`HrvPostWorkoutRecomputer`) preserves short start/end boundary segments down to 3 clean pairs.
 * - [RESTING_CHECK_MIN_VALID_PAIRS] (`1`): Fixed 60s/120s resting morning check
 *   (`MorningHrvCheckViewModel`), which already gates session validity upstream on beat count/duration.
 */
object HrvRmssdCalculator {

    const val LIVE_ROLLING_MIN_VALID_PAIRS: Int = 10
    const val POST_WORKOUT_MIN_VALID_PAIRS: Int = 3
    const val RESTING_CHECK_MIN_VALID_PAIRS: Int = 1

    /**
     * Computes bias-free rMSSD (ms) over [beats], excluding any successive pair where either
     * adjacent beat is flagged as an artifact (`REQ-HRV-050.2`, `REQ-PER-032.4`, `REQ-MIG-061.2`).
     *
     * @param beats Chronologically ordered artifact-filtered RR beats.
     * @param minValidPairs Minimum required number of valid successive pairs (coerced to `>= 1`
     *   to prevent `0.0 / 0.0 = NaN` division).
     * @return rMSSD in milliseconds, or `null` when fewer than `max(1, minValidPairs)` valid pairs exist.
     */
    @JvmStatic
    @JvmOverloads
    fun computeBiasFreeRmssd(
        beats: List<FilteredRrBeat>,
        minValidPairs: Int = LIVE_ROLLING_MIN_VALID_PAIRS
    ): Double? {
        val effectiveMinPairs = minValidPairs.coerceAtLeast(1)
        if (beats.size < 2) return null

        var sumSquaredDiffs = 0.0
        var validPairCount = 0

        for (i in 0 until beats.size - 1) {
            val b1 = beats[i]
            val b2 = beats[i + 1]
            if (!b1.isArtifact && !b2.isArtifact) {
                val diff = b2.rawRrMs - b1.rawRrMs
                sumSquaredDiffs += diff * diff
                validPairCount++
            }
        }

        if (validPairCount < effectiveMinPairs) {
            return null
        }

        val rmssd = sqrt(sumSquaredDiffs / validPairCount.toDouble())
        return if (rmssd.isFinite()) rmssd else null
    }

    /**
     * Computes raw rMSSD (ms) directly over unfiltered RR [intervals] (used by [HrvSignalQualityAnalyzer]).
     *
     * @param intervals Raw RR intervals in milliseconds.
     * @param minValidPairs Minimum required successive pairs (coerced to `>= 1`).
     */
    @JvmStatic
    @JvmOverloads
    fun computeRawRmssd(
        intervals: List<Double>,
        minValidPairs: Int = 1
    ): Double? {
        val effectiveMinPairs = minValidPairs.coerceAtLeast(1)
        val pairCount = intervals.size - 1
        if (pairCount < effectiveMinPairs) return null

        var sumSquares = 0.0
        for (i in 1 until intervals.size) {
            val diff = intervals[i] - intervals[i - 1]
            sumSquares += diff * diff
        }
        val rmssd = sqrt(sumSquares / pairCount.toDouble())
        return if (rmssd.isFinite()) rmssd else null
    }
}
