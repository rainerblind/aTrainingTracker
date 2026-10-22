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

package com.atrainingtracker.trainingtracker.climbs

import android.content.Context
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import java.util.Locale
import kotlin.math.max

/**
 * Pure mathematical engine for detecting and categorizing sustained ascents from path elevation profiles
 * (REQ-MAP-027, TST-MAP-029).
 */
object ClimbDetector {

    private const val DEFAULT_MIN_LENGTH_METERS = 500.0
    private const val DEFAULT_MIN_GRADE_PERCENT = 3.0
    private const val DEFAULT_MIN_GAIN_METERS = 20.0
    private const val DEFAULT_MAX_DIP_DESCENT_METERS = 15.0
    private const val DEFAULT_MAX_DIP_DISTANCE_METERS = 150.0

    /**
     * Detects qualifying climbs from a list of sequential route [points].
     *
     * @param points List of sequential [PathPoint] items along the route.
     * @param minLengthMeters Minimum climb length in meters (default 500m).
     * @param minGradePercent Minimum average gradient in percent (default 3.0%).
     * @param minGainMeters Minimum vertical ascent in meters (default 20m).
     * @param routeId Optional route ID to associate with detected climbs.
     * @return List of detected [Climb] instances.
     */
    fun detectClimbs(
        points: List<PathPoint>,
        minLengthMeters: Double = DEFAULT_MIN_LENGTH_METERS,
        minGradePercent: Double = DEFAULT_MIN_GRADE_PERCENT,
        minGainMeters: Double = DEFAULT_MIN_GAIN_METERS,
        routeId: Long? = null,
        context: Context? = null
    ): List<Climb> {
        val validPoints = points.filter { it.altitude != null }
        if (validPoints.size < 2) return emptyList()

        val climbs = mutableListOf<Climb>()
        var i = 0

        while (i < validPoints.size - 1) {
            // Find start of ascent (where altitude starts increasing)
            if (validPoints[i + 1].altitude!! <= validPoints[i].altitude!!) {
                i++
                continue
            }

            val startIdx = i
            var peakIdx = i + 1
            var maxAlt = validPoints[startIdx].altitude!!
            var dipDescent = 0.0
            var dipDistance = 0.0
            var j = i + 1

            while (j < validPoints.size) {
                val currentAlt = validPoints[j].altitude!!
                val prevAlt = validPoints[j - 1].altitude!!
                val deltaAlt = currentAlt - prevAlt
                val deltaDist = validPoints[j].distance - validPoints[j - 1].distance

                if (currentAlt > maxAlt) {
                    // New peak reached within climb
                    maxAlt = currentAlt
                    peakIdx = j
                    dipDescent = 0.0
                    dipDistance = 0.0
                } else {
                    // Descending or flat within candidate climb
                    if (deltaAlt < 0) {
                        dipDescent += -deltaAlt
                    }
                    dipDistance += deltaDist

                    // Check if dip exceeds tolerance
                    if (dipDescent > DEFAULT_MAX_DIP_DESCENT_METERS || dipDistance > DEFAULT_MAX_DIP_DISTANCE_METERS) {
                        break
                    }
                }
                j++
            }

            // Evaluate candidate from startIdx to peakIdx
            val dist = validPoints[peakIdx].distance - validPoints[startIdx].distance
            val gain = validPoints[peakIdx].altitude!! - validPoints[startIdx].altitude!!
            val avgGrade = if (dist > 0) (gain / dist) * 100.0 else 0.0

            if (dist >= minLengthMeters && gain >= minGainMeters && avgGrade >= minGradePercent) {
                climbs.add(createClimb(validPoints, startIdx, peakIdx, routeId, context))
                // Resume scanning after the climb summit
                i = peakIdx
            } else if (gain >= minGainMeters && dist >= minLengthMeters) {
                // Total ascent had enough gain and length, but overall average grade was diluted below minGradePercent.
                // Search for an embedded steep climb within [startIdx, peakIdx] by trimming gentle boundaries.
                var s = startIdx
                var e = peakIdx

                while (s < e && localGradeForward(validPoints, s, e) < minGradePercent) {
                    s++
                }
                while (e > s && localGradeBackward(validPoints, e, s) < minGradePercent) {
                    e--
                }

                val subDist = validPoints[e].distance - validPoints[s].distance
                val subGain = validPoints[e].altitude!! - validPoints[s].altitude!!
                val subGrade = if (subDist > 0) (subGain / subDist) * 100.0 else 0.0

                if (subDist >= minLengthMeters && subGain >= minGainMeters && subGrade >= minGradePercent) {
                    climbs.add(createClimb(validPoints, s, e, routeId, context))
                    i = e
                } else {
                    i = maxOf(i + 1, peakIdx)
                }
            } else {
                // Insufficient bump (gain < minGainMeters or dist < minLengthMeters)
                i = maxOf(i + 1, peakIdx)
            }
        }

        return climbs
    }

    private fun createClimb(
        points: List<PathPoint>,
        startIdx: Int,
        peakIdx: Int,
        routeId: Long?,
        context: Context?
    ): Climb {
        val dist = points[peakIdx].distance - points[startIdx].distance
        val gain = points[peakIdx].altitude!! - points[startIdx].altitude!!
        val avgGrade = if (dist > 0) (gain / dist) * 100.0 else 0.0
        val climbPoints = points.subList(startIdx, peakIdx + 1)
        val maxGrade = calculateMaxGrade(climbPoints)
        val score = dist * avgGrade
        val category = ClimbCategory.fromScore(score)
        val appContext = context ?: TrainingApplication.getAppContext()
        val climbTitle = appContext?.getString(R.string.climb_title) ?: "Climb"
        val formattedDistance = DistanceFormatter().format_with_units(dist)
        val name = "$climbTitle $formattedDistance @ ${String.format(Locale.US, "%.1f%%", avgGrade)}"

        return Climb(
            name = name,
            routeId = routeId,
            startLat = points[startIdx].latLng.latitude,
            startLng = points[startIdx].latLng.longitude,
            endLat = points[peakIdx].latLng.latitude,
            endLng = points[peakIdx].latLng.longitude,
            distanceMeters = dist,
            elevationGainMeters = gain,
            avgGradePercent = avgGrade,
            maxGradePercent = maxGrade,
            category = category,
            pathPoints = climbPoints
        )
    }

    private fun localGradeForward(points: List<PathPoint>, fromIdx: Int, maxIdx: Int): Double {
        if (fromIdx >= maxIdx || fromIdx >= points.size - 1) return 0.0
        var j = minOf(fromIdx + 1, points.size - 1)
        val limit = minOf(maxIdx, points.size - 1)
        while (j < limit && points[j].distance - points[fromIdx].distance < 20.0) {
            j++
        }
        val d = points[j].distance - points[fromIdx].distance
        val g = points[j].altitude!! - points[fromIdx].altitude!!
        return if (d > 0) (g / d) * 100.0 else 0.0
    }

    private fun localGradeBackward(points: List<PathPoint>, toIdx: Int, minIdx: Int): Double {
        if (toIdx <= minIdx || toIdx <= 0) return 0.0
        var j = maxOf(toIdx - 1, 0)
        val limit = maxOf(minIdx, 0)
        while (j > limit && points[toIdx].distance - points[j].distance < 20.0) {
            j--
        }
        val d = points[toIdx].distance - points[j].distance
        val g = points[toIdx].altitude!! - points[j].altitude!!
        return if (d > 0) (g / d) * 100.0 else 0.0
    }

    /**
     * Calculates the maximum sustained gradient over a rolling window (or consecutive segments).
     */
    private fun calculateMaxGrade(points: List<PathPoint>): Double {
        if (points.size < 2) return 0.0
        var maxGrade = 0.0
        for (k in 0 until points.size - 1) {
            val altNext = points[k + 1].altitude ?: continue
            val altCurr = points[k].altitude ?: continue
            val dDist = points[k + 1].distance - points[k].distance
            val dAlt = altNext - altCurr
            if (dDist > 5.0 && dAlt > 0) {
                val grade = (dAlt / dDist) * 100.0
                if (grade > maxGrade) {
                    maxGrade = grade
                }
            }
        }
        return max(maxGrade, 0.0)
    }
}
