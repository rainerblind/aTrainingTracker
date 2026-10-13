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

package com.atrainingtracker.trainingtracker.ui.map

import kotlin.math.exp

/**
 * Pure mathematical engine providing distance-weighted Gaussian kernel elevation smoothing
 * and continuous slope grade evaluation for [ElevationProfile]. (REQ-UI-297 / ATT-2512)
 *
 * Implements a linear-time O(N) sliding two-pointer window over monotonically distance-sorted
 * [PathPoint] streams using local linear kernel regression (Loess with Gaussian weights),
 * providing exact first-order boundary bias correction and eliminating quantization staircase artifacts.
 */
object ElevationSmoothingMath {

    const val DEFAULT_SIGMA: Double = 21.0 // Spatial kernel radius in meters (ATT-2746)
    const val DEFAULT_CUTOFF: Double = 63.0 // 3 * sigma cutoff radius in meters (ATT-2746)

    /**
     * Smooths an elevation series using local linear regression with a distance-weighted Gaussian kernel:
     *
     *   w_ij = exp(-0.5 * ((d_j - d_i) / sigma)^2)
     *
     * Minimizing the locally weighted linear fit:
     *   sum_j w_ij * (alt_j - (alpha_i + beta_i * (d_j - d_i)))^2
     *
     * In the symmetric interior, alpha_i reduces to the standard Gaussian weighted average.
     * At asymmetric boundaries (d=0, d=D_total), local linear correction mathematically eliminates
     * boundary sag and peaking by accounting for the local terrain slope.
     *
     * @param pathPoints Monotonically distance-ordered path points.
     * @param sigma Gaussian kernel spatial standard deviation in meters (default 21.0m).
     * @param cutoff Spatial cutoff radius beyond which weights are truncated (default 3 * sigma = 63.0m).
     * @return Primitive [DoubleArray] of smoothed altitudes matching the size of [pathPoints].
     */
    fun smoothAltitudes(
        pathPoints: List<PathPoint>,
        sigma: Double = DEFAULT_SIGMA,
        cutoff: Double = 3.0 * sigma
    ): DoubleArray {
        val n = pathPoints.size
        if (n == 0) return DoubleArray(0)
        if (n == 1) return doubleArrayOf(pathPoints[0].altitude)

        val distances = DoubleArray(n)
        val altitudes = DoubleArray(n)
        for (idx in 0 until n) {
            val pt = pathPoints[idx]
            distances[idx] = pt.distance
            altitudes[idx] = pt.altitude
        }

        val result = DoubleArray(n)
        val invTwoSigmaSq = 1.0 / (2.0 * sigma * sigma)

        var left = 0
        var right = 0

        for (i in 0 until n) {
            val centerDist = distances[i]

            // Advance left boundary while points are further than cutoff behind center
            while (left < n && centerDist - distances[left] > cutoff) {
                left++
            }

            // Advance right boundary while points are within cutoff ahead of center
            while (right < n && distances[right] - centerDist <= cutoff) {
                right++
            }

            var s0 = 0.0 // sum(w)
            var s1 = 0.0 // sum(w * dx)
            var s2 = 0.0 // sum(w * dx^2)
            var t0 = 0.0 // sum(w * y)
            var t1 = 0.0 // sum(w * y * dx)

            for (j in left until right) {
                val dx = distances[j] - centerDist
                val w = exp(-(dx * dx) * invTwoSigmaSq)
                val y = altitudes[j]

                s0 += w
                s1 += w * dx
                s2 += w * dx * dx
                t0 += w * y
                t1 += w * y * dx
            }

            val det = s0 * s2 - s1 * s1
            result[i] = if (det > 1e-9) {
                // Local linear regression solution for intercept alpha (altitude at centerDist)
                (t0 * s2 - t1 * s1) / det
            } else if (s0 > 0.0) {
                t0 / s0
            } else {
                altitudes[i]
            }
        }

        return result
    }

    /**
     * Calculates the slope grade in percent between two smoothed points.
     * Returns 0.0 if the horizontal distance delta is less than or equal to [minDistDiff].
     */
    fun calculateGrade(
        dist1: Double,
        alt1: Double,
        dist2: Double,
        alt2: Double,
        minDistDiff: Double = 1.0
    ): Double {
        val distDiff = dist2 - dist1
        return if (distDiff > minDistDiff) {
            ((alt2 - alt1) / distDiff) * 100.0
        } else {
            0.0
        }
    }
}
