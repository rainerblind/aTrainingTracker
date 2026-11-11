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

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Detrended Fluctuation Analysis (DFA) calculation engine for short-term fractal scaling (alpha 1)
 * pursuant to REQ-TRK-060.4 and REQ-TRK-060.6.
 *
 * Standard Kubios / NeuroKit2 0.2.10 algorithm:
 * - Scale range n in [minBox, maxBox] (default 4 to 16 beats).
 * - Linear detrending per box (order 1).
 * - Forward and backward non-overlapping segments (2K segments).
 * - Log-log linear regression slope yields alpha 1.
 */
object DfaCalculator {

    const val DEFAULT_MIN_BOX = 4
    const val DEFAULT_MAX_BOX = 16

    /**
     * Computes the short-term DFA alpha 1 exponent from an array of NN/RR intervals in milliseconds.
     *
     * @param nnIntervalsMs Array of beat intervals.
     * @param minBox Minimum box scale (default 4).
     * @param maxBox Maximum box scale (default 16).
     * @return DFA alpha 1 exponent, or [Double.NaN] if sample size is insufficient.
     */
    fun computeDfaAlpha1(
        nnIntervalsMs: DoubleArray,
        minBox: Int = DEFAULT_MIN_BOX,
        maxBox: Int = DEFAULT_MAX_BOX
    ): Double {
        val nPoints = nnIntervalsMs.size
        // Require at least 2 * maxBox points for reliable scaling
        if (nPoints < 2 * maxBox) {
            return Double.NaN
        }

        // 1. Mean-centered cumulative integration: Y(k) = sum_{i=0..k} (y_i - mean_y)
        var sum = 0.0
        for (v in nnIntervalsMs) {
            sum += v
        }
        val mean = sum / nPoints

        val integratedProfile = DoubleArray(nPoints)
        var runningSum = 0.0
        for (i in 0 until nPoints) {
            runningSum += (nnIntervalsMs[i] - mean)
            integratedProfile[i] = runningSum
        }

        // 2. Compute fluctuation F(n) for each box size n in [minBox..maxBox] with 50% overlap (NeuroKit2 0.2.10)
        val logN = ArrayList<Double>()
        val logF = ArrayList<Double>()

        for (boxSize in minBox..maxBox) {
            val step = max(1, boxSize / 2)
            var totalVar = 0.0
            var count = 0

            var start = 0
            while (start < nPoints - boxSize) {
                val rss = computeSegmentSquaredResiduals(integratedProfile, start, boxSize)
                val v = rss / boxSize
                if (v > 1e-8) {
                    totalVar += v
                    count++
                }
                start += step
            }

            if (count > 0) {
                val fn = sqrt(totalVar / count)
                if (fn > 0.0) {
                    logN.add(log10(boxSize.toDouble()))
                    logF.add(log10(fn))
                }
            }
        }

        if (logN.size < 3) {
            return Double.NaN
        }

        // 3. Ordinary least-squares regression of log10(F(n)) vs log10(n)
        return computeLinearSlope(logN, logF)
    }

    private fun computeSegmentSquaredResiduals(
        profile: DoubleArray,
        start: Int,
        n: Int
    ): Double {
        val tMean = (n - 1) / 2.0
        val tVar = n * (n * n - 1.0) / 12.0 // sum_{t=0..n-1} (t - tMean)^2

        var ySum = 0.0
        for (t in 0 until n) {
            ySum += profile[start + t]
        }
        val yMean = ySum / n

        var covTY = 0.0
        var ySumSquares = 0.0
        for (t in 0 until n) {
            val yDiff = profile[start + t] - yMean
            val tDiff = t - tMean
            covTY += tDiff * yDiff
            ySumSquares += yDiff * yDiff
        }

        val slope = covTY / tVar
        // Residual sum of squares: sum (y_i - (slope * t_i + intercept))^2 = ySumSquares - slope * covTY
        val rss = ySumSquares - slope * covTY
        return if (rss > 0.0) rss else 0.0
    }

    private fun computeLinearSlope(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        var xSum = 0.0
        var ySum = 0.0
        for (i in 0 until n) {
            xSum += x[i]
            ySum += y[i]
        }
        val xMean = xSum / n
        val yMean = ySum / n

        var covXY = 0.0
        var varX = 0.0
        for (i in 0 until n) {
            val xDiff = x[i] - xMean
            val yDiff = y[i] - yMean
            covXY += xDiff * yDiff
            varX += xDiff * xDiff
        }

        return if (varX > 0.0) covXY / varX else Double.NaN
    }
}
