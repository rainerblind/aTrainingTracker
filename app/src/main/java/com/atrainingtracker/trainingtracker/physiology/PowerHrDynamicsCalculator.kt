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

package com.atrainingtracker.trainingtracker.physiology

import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 1st-Order Dynamical Power-to-Heart-Rate System Identification Engine (REQ-REV-009 / ATT-3904).
 *
 * Models mechanical cycling power P(t) to heart rate HR(t) via a 1st-order Output-Error (OE)
 * low-pass dynamical model:
 * ```
 * tau * dHR(t)/dt + HR(t) = HR_0 + K * P(t)
 * x_k(tau) = exp(-dt / tau) * x_{k-1}(tau) + (1 - exp(-dt / tau)) * P_k
 * HR_hat_k = HR_0 + K * x_k(tau)
 * ```
 * Extracts steady-state reference HR@200W (HR_0 + 200*K), time constant tau, aerobic gain K
 * (and slope 1/K), and cardiac drift delta_HR@200W between fresh and fatigued windows.
 */
object PowerHrDynamicsCalculator {

    const val MIN_QUALIFYING_DURATION_SEC = 1200 // 20 minutes
    const val FRESH_WINDOW_START_SEC = 480      // Minute 8 (post warm-up)
    const val FRESH_WINDOW_END_SEC = 3300       // Minute 55
    const val END_WINDOW_DURATION_SEC = 2700     // Final 45 minutes
    const val MIN_WINDOW_DURATION_SEC = 600     // 10 minutes minimum for window fitting
    const val MIN_POWER_VARIANCE = 1.0          // Minimum 1 W^2 active power variance

    data class FitResult(
        val tau: Double,
        val hr0: Double,
        val k: Double,
        val hr200: Double,
        val r2: Double,
        val rmse: Double
    )

    /**
     * Identifies 1st-order OE dynamical parameters from paired Power and Heart Rate telemetry streams.
     *
     * @param powerSamples Sequence of instantaneous mechanical power readings (Watts).
     * @param hrSamples Sequence of instantaneous heart rate readings (bpm).
     * @param workoutId Optional database ID of the workout.
     * @param dt Sampling interval in seconds (defaults to 1.0 s).
     * @return [CardioPowerDynamicsRecord] with fresh and end window fits, or `null` if qualifying
     *         criteria are not met (duration < 20 min or missing streams).
     */
    @JvmStatic
    @JvmOverloads
    fun calculate(
        powerSamples: List<Double?>?,
        hrSamples: List<Double?>?,
        workoutId: Long = 0L,
        dt: Double = 1.0
    ): CardioPowerDynamicsRecord? {
        if (powerSamples == null || hrSamples == null) return null
        val n = min(powerSamples.size, hrSamples.size)
        if (n < MIN_QUALIFYING_DURATION_SEC) return null

        // Filter and impute valid paired series
        val cleanPower = DoubleArray(n)
        val cleanHr = DoubleArray(n)
        var validPairs = 0
        var lastP = 0.0
        var lastHr = 100.0

        for (i in 0 until n) {
            val p = powerSamples[i]
            val h = hrSamples[i]
            if (p != null && p >= 0.0) lastP = p
            if (h != null && h > 30.0 && h < 240.0) {
                lastHr = h
                if (p != null && p >= 0.0) validPairs++
            }
            cleanPower[i] = lastP
            cleanHr[i] = lastHr
        }

        // Must have sufficient paired telemetry
        if (validPairs < MIN_QUALIFYING_DURATION_SEC) return null

        // 1. Fresh Calibration Window (active minutes 8 to 55, or up to midpoint for shorter rides)
        val freshStart = FRESH_WINDOW_START_SEC
        val freshEnd = if (n >= 6000) {
            FRESH_WINDOW_END_SEC
        } else {
            min(FRESH_WINDOW_END_SEC, max(freshStart + MIN_WINDOW_DURATION_SEC, n / 2))
        }
        val freshFit = fitWindow(cleanPower, cleanHr, freshStart, freshEnd, dt = dt) ?: return null

        // 2. End-of-Workout Window (final 45 min, non-overlapping with fresh window)
        var endFit: FitResult? = null
        if (n >= 1800) {
            val endStart = if (n >= 6000) {
                n - END_WINDOW_DURATION_SEC
            } else {
                max(freshEnd, n - END_WINDOW_DURATION_SEC)
            }
            val endEnd = n
            if (endEnd - endStart >= MIN_WINDOW_DURATION_SEC) {
                endFit = fitWindow(cleanPower, cleanHr, endStart, endEnd, dt = dt)
            }
        }

        return CardioPowerDynamicsRecord(
            workoutId = workoutId,
            tauStart = freshFit.tau,
            hr0Start = freshFit.hr0,
            kStart = freshFit.k,
            hr200Start = freshFit.hr200,
            r2Start = freshFit.r2,
            rmseStart = freshFit.rmse,
            tauEnd = endFit?.tau,
            hr0End = endFit?.hr0,
            kEnd = endFit?.k,
            hr200End = endFit?.hr200,
            r2End = endFit?.r2,
            rmseEnd = endFit?.rmse
        )
    }

    /**
     * Fits 1st-order Output-Error model over a discrete sample interval [startIndex, endIndex).
     *
     * Performs grid search over tau in [tauMin, tauMax] with Ordinary Least Squares estimation
     * of (HR_0, K) minimizing RSS.
     */
    @JvmStatic
    @JvmOverloads
    fun fitWindow(
        power: DoubleArray,
        hr: DoubleArray,
        startIndex: Int,
        endIndex: Int,
        tauMin: Int = 8,
        tauMax: Int = 180,
        dt: Double = 1.0
    ): FitResult? {
        val n = endIndex - startIndex
        if (n < MIN_WINDOW_DURATION_SEC || startIndex < 0 || endIndex > power.size || endIndex > hr.size) {
            return null
        }

        // Calculate power mean and variance
        var sumP = 0.0
        for (i in startIndex until endIndex) sumP += power[i]
        val meanP = sumP / n

        var varP = 0.0
        for (i in startIndex until endIndex) {
            val dp = power[i] - meanP
            varP += dp * dp
        }
        varP /= n
        if (varP < MIN_POWER_VARIANCE) {
            return null // Insufficient dynamic power variation for identification
        }

        // Calculate HR mean and total sum of squares
        var sumHr = 0.0
        for (i in startIndex until endIndex) sumHr += hr[i]
        val meanHr = sumHr / n

        var tssHr = 0.0
        for (i in startIndex until endIndex) {
            val dHr = hr[i] - meanHr
            tssHr += dHr * dHr
        }
        if (tssHr <= 1e-4) return null

        val xBuffer = DoubleArray(n)
        var bestRss = Double.MAX_VALUE
        var bestTau = 0.0
        var bestHr0 = 0.0
        var bestK = 0.0
        var found = false

        for (tauCandidate in tauMin..tauMax) {
            val tau = tauCandidate.toDouble()
            val alpha = exp(-dt / tau)
            val oneMinusAlpha = 1.0 - alpha

            xBuffer[0] = power[startIndex]
            var sumX = xBuffer[0]
            for (i in 1 until n) {
                xBuffer[i] = alpha * xBuffer[i - 1] + oneMinusAlpha * power[startIndex + i]
                sumX += xBuffer[i]
            }
            val meanX = sumX / n

            var cov = 0.0
            var varX = 0.0
            for (i in 0 until n) {
                val dx = xBuffer[i] - meanX
                val dy = hr[startIndex + i] - meanHr
                cov += dx * dy
                varX += dx * dx
            }

            if (varX < 1e-9) continue
            val k = cov / varX
            val hr0 = meanHr - k * meanX

            // Physiological plausibility: mechanical power must positively drive HR
            if (k <= 0.0) continue

            var rss = 0.0
            for (i in 0 until n) {
                val pred = hr0 + k * xBuffer[i]
                val err = hr[startIndex + i] - pred
                rss += err * err
            }

            if (rss < bestRss) {
                bestRss = rss
                bestTau = tau
                bestHr0 = hr0
                bestK = k
                found = true
            }
        }

        if (!found) return null

        val r2 = max(0.0, 1.0 - (bestRss / tssHr))
        val rmse = sqrt(bestRss / n)
        val hr200 = bestHr0 + 200.0 * bestK

        return FitResult(
            tau = bestTau,
            hr0 = bestHr0,
            k = bestK,
            hr200 = hr200,
            r2 = r2,
            rmse = rmse
        )
    }

    /**
     * Simulates the 1st-order Output-Error heart rate trajectory HR_fresh(t) from input power.
     */
    @JvmStatic
    @JvmOverloads
    fun simulateTrajectory(
        power: DoubleArray,
        tau: Double,
        hr0: Double,
        k: Double,
        dt: Double = 1.0
    ): DoubleArray {
        if (power.isEmpty() || tau <= 0.0) return DoubleArray(0)
        val n = power.size
        val result = DoubleArray(n)
        val alpha = exp(-dt / tau)
        val oneMinusAlpha = 1.0 - alpha

        var x = power[0]
        result[0] = hr0 + k * x
        for (i in 1 until n) {
            x = alpha * x + oneMinusAlpha * power[i]
            result[i] = hr0 + k * x
        }
        return result
    }

    /**
     * Timestamp-aware primitive simulation overload across non-uniform trackpoints (ATT-6723 / REQ-REV-010.3).
     * Computes step-wise \Delta t_i = (timestampsSec[i] - timestampsSec[i-1]).coerceIn(1L, 60L).toDouble().
     */
    @JvmStatic
    fun simulateTrajectory(
        power: DoubleArray,
        timestampsSec: LongArray,
        tau: Double,
        hr0: Double,
        k: Double
    ): DoubleArray {
        if (power.isEmpty() || tau <= 0.0) return DoubleArray(0)
        val n = power.size
        val result = DoubleArray(n)

        var x = power[0]
        result[0] = hr0 + k * x
        for (i in 1 until n) {
            val dt = if (i < timestampsSec.size) {
                (timestampsSec[i] - timestampsSec[i - 1]).coerceIn(1L, 60L).toDouble()
            } else 1.0
            val alpha = exp(-dt / tau)
            val oneMinusAlpha = 1.0 - alpha
            x = alpha * x + oneMinusAlpha * power[i]
            result[i] = hr0 + k * x
        }
        return result
    }

    /**
     * List-based simulation overload for UI chart overlays (ATT-3905).
     */
    @JvmStatic
    @JvmOverloads
    fun simulateTrajectory(
        powerSamples: List<Double?>,
        tau: Double,
        hr0: Double,
        k: Double,
        dt: Double = 1.0
    ): List<Double?> {
        if (powerSamples.isEmpty() || tau <= 0.0) return emptyList()
        val n = powerSamples.size
        val alpha = exp(-dt / tau)
        val oneMinusAlpha = 1.0 - alpha

        val result = ArrayList<Double?>(n)
        var x = (powerSamples[0] ?: 0.0).coerceAtLeast(0.0)
        result.add(hr0 + k * x)

        for (i in 1 until n) {
            val p = (powerSamples[i] ?: 0.0).coerceAtLeast(0.0)
            x = alpha * x + oneMinusAlpha * p
            result.add(hr0 + k * x)
        }
        return result
    }

    /**
     * Timestamp-aware simulation overload for UI chart overlays across decimated trackpoints (ATT-6723 / REQ-REV-010.3).
     * Computes step-wise \Delta t_i = (timestampsSec[i] - timestampsSec[i-1]).coerceIn(1L, 60L).toDouble().
     */
    @JvmStatic
    fun simulateTrajectory(
        powerSamples: List<Double?>,
        timestampsSec: List<Long>,
        tau: Double,
        hr0: Double,
        k: Double
    ): List<Double?> {
        if (powerSamples.isEmpty() || tau <= 0.0) return emptyList()
        val n = powerSamples.size
        val result = ArrayList<Double?>(n)
        var x = (powerSamples[0] ?: 0.0).coerceAtLeast(0.0)
        result.add(hr0 + k * x)

        for (i in 1 until n) {
            val dt = if (i < timestampsSec.size) {
                (timestampsSec[i] - timestampsSec[i - 1]).coerceIn(1L, 60L).toDouble()
            } else 1.0
            val alpha = exp(-dt / tau)
            val oneMinusAlpha = 1.0 - alpha
            val p = (powerSamples[i] ?: 0.0).coerceAtLeast(0.0)
            x = alpha * x + oneMinusAlpha * p
            result.add(hr0 + k * x)
        }
        return result
    }
}
