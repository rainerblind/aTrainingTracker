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
        routeId: Long? = null
    ): List<Climb> {
        if (points.size < 2) return emptyList()

        val climbs = mutableListOf<Climb>()
        var i = 0

        while (i < points.size - 1) {
            // Find start of ascent (where altitude starts increasing)
            if (points[i + 1].altitude <= points[i].altitude) {
                i++
                continue
            }

            val startIdx = i
            var peakIdx = i + 1
            var maxAlt = points[startIdx].altitude
            var dipDescent = 0.0
            var dipDistance = 0.0
            var j = i + 1

            while (j < points.size) {
                val currentAlt = points[j].altitude
                val prevAlt = points[j - 1].altitude
                val deltaAlt = currentAlt - prevAlt
                val deltaDist = points[j].distance - points[j - 1].distance

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
            val dist = points[peakIdx].distance - points[startIdx].distance
            val gain = points[peakIdx].altitude - points[startIdx].altitude
            val avgGrade = if (dist > 0) (gain / dist) * 100.0 else 0.0

            if (dist >= minLengthMeters && gain >= minGainMeters && avgGrade >= minGradePercent) {
                val climbPoints = points.subList(startIdx, peakIdx + 1)
                val maxGrade = calculateMaxGrade(climbPoints)
                val score = dist * avgGrade
                val category = ClimbCategory.fromScore(score)
                val name = String.format(Locale.US, "Anstieg %.1f km @ %.1f%%", dist / 1000.0, avgGrade)

                climbs.add(
                    Climb(
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
                )
                // Resume scanning after the climb summit
                i = peakIdx
            } else {
                i++
            }
        }

        return climbs
    }

    /**
     * Calculates the maximum sustained gradient over a rolling window (or consecutive segments).
     */
    private fun calculateMaxGrade(points: List<PathPoint>): Double {
        if (points.size < 2) return 0.0
        var maxGrade = 0.0
        for (k in 0 until points.size - 1) {
            val dDist = points[k + 1].distance - points[k].distance
            val dAlt = points[k + 1].altitude - points[k].altitude
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
