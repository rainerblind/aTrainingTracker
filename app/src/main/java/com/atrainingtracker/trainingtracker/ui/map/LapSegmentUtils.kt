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

import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Pure mathematical and geometrical utility functions for extracting lap segments
 * from full workout track polylines and calculating camera bounding boxes.
 *
 * Designed with zero disk and database IO overhead to ensure fluid UI interactions
 * during workout list scrolling and bottom sheet animations.
 */
object LapSegmentUtils {

    /**
     * Calculates the cumulative start and end distance range in meters for a specific lap
     * within a workout session.
     *
     * @param laps The complete ordered list of recorded laps for the workout.
     * @param targetLapNr The sequential lap number of the target lap.
     * @return A [Pair] representing (startDistanceMeters, endDistanceMeters).
     */
    fun calculateLapDistanceRange(laps: List<LapData>, targetLapNr: Long): Pair<Double, Double> {
        var cumulativeDist = 0.0
        var startDist = 0.0
        var endDist = 0.0
        var found = false

        for (lap in laps) {
            if (lap.lapNr == targetLapNr) {
                startDist = cumulativeDist
                endDist = cumulativeDist + lap.distanceTotalM.coerceAtLeast(0.0)
                found = true
                break
            }
            cumulativeDist += lap.distanceTotalM.coerceAtLeast(0.0)
        }

        return if (found) Pair(startDist, endDist) else Pair(0.0, 0.0)
    }

    /**
     * Slices a subset of coordinates corresponding to a lap segment from the full polyline
     * points and distance stream.
     *
     * Includes adjacent boundary anchor points before and after the interval to ensure
     * continuous, seamless polyline rendering without visual gaps.
     *
     * @param points The complete list of decoded [LatLng] coordinates for the workout.
     * @param dists The matching list of cumulative distance samples in meters.
     * @param startDistM The starting distance of the lap segment in meters.
     * @param endDistM The ending distance of the lap segment in meters.
     * @return A list of [LatLng] coordinates representing the lap segment.
     */
    fun sliceLapSegment(
        points: List<LatLng>,
        dists: List<Double>,
        startDistM: Double,
        endDistM: Double
    ): List<LatLng> {
        if (points.isEmpty() || dists.isEmpty() || startDistM > endDistM) {
            return emptyList()
        }

        val size = minOf(points.size, dists.size)
        var firstIdx = -1
        var lastIdx = -1

        for (i in 0 until size) {
            val d = dists[i]
            if (d >= startDistM && firstIdx == -1) {
                firstIdx = i
            }
            if (d <= endDistM) {
                lastIdx = i
            }
        }

        if (firstIdx == -1) {
            // All points are before startDistM or after endDistM
            return emptyList()
        }

        if (lastIdx < firstIdx) {
            lastIdx = firstIdx
        }

        // Add boundary anchor points before and after for continuous polyline connectivity
        val anchorStart = (firstIdx - 1).coerceAtLeast(0)
        val anchorEnd = (lastIdx + 1).coerceAtMost(size - 1)

        return points.subList(anchorStart, anchorEnd + 1)
    }

    /**
     * Calculates a [LatLngBounds] bounding box that encloses all points of a lap segment.
     *
     * If the segment contains a single point or points with zero spatial variance,
     * the bounds are slightly expanded to prevent camera animation crashes on zero-area bounds.
     *
     * @param points The coordinates to enclose.
     * @return A valid [LatLngBounds], or `null` if the point list is empty.
     */
    fun calculateLapBounds(points: List<LatLng>): LatLngBounds? {
        if (points.isEmpty()) return null

        val builder = LatLngBounds.Builder()
        for (p in points) {
            builder.include(p)
        }
        val bounds = builder.build()

        // Guard against zero-area bounding boxes (e.g. single coordinate)
        return if (bounds.northeast.latitude == bounds.southwest.latitude &&
            bounds.northeast.longitude == bounds.southwest.longitude
        ) {
            LatLngBounds(
                LatLng(bounds.southwest.latitude - 0.001, bounds.southwest.longitude - 0.001),
                LatLng(bounds.northeast.latitude + 0.001, bounds.northeast.longitude + 0.001)
            )
        } else {
            bounds
        }
    }
}
