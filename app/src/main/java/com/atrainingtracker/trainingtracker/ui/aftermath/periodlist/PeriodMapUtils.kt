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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.google.maps.android.heatmaps.HeatmapTileProvider

/**
 * Encapsulates the visual styling for period-based maps.
 */
data class PeriodMapVisuals(
    val heatmapProvider: HeatmapTileProvider?,
    val polylineAlpha: Float,
    val polylineWidth: Float
)

/**
 * Centralized logic to determine how workouts are visualized based on the period level.
 * Transitions from clear polylines (Day) to prominent heatmaps (Year).
 */
fun getPeriodMapVisuals(
    periodType: PeriodType,
    allPaths: List<List<LatLng>>
): PeriodMapVisuals {
    val heatmapProvider = run {
        if (allPaths.isEmpty() || periodType == PeriodType.DAY) {
            null
        } else {
            // Densify points for cycle/fast activities so the heatmap looks like a continuous trail
            // instead of disconnected blobs due to downsampling.
            val allPoints = allPaths.flatMap { path ->
                densifyPath(path, 10.0)
            }

            if (allPoints.isEmpty()) null
            else {
                val opacity = when (periodType) {
                    PeriodType.WEEK -> 0.6
                    PeriodType.MONTH -> 0.8
                    PeriodType.YEAR -> 1.0
                    else -> 0.0
                }
                HeatmapTileProvider.Builder()
                    .data(allPoints)
                    .opacity(opacity)
                    .radius(20) // Increased radius for more "glow"
                    .build()
            }
        }
    }

    val polylineAlpha = when (periodType) {
        PeriodType.DAY -> 1.0f
        PeriodType.WEEK -> 0.9f
        PeriodType.MONTH -> 0.8f
        PeriodType.YEAR -> 0.6f
    }

    val polylineWidth = when (periodType) {
        PeriodType.DAY -> 8f
        PeriodType.WEEK -> 8f
        PeriodType.MONTH -> 7f
        PeriodType.YEAR -> 5f
    }

    return PeriodMapVisuals(heatmapProvider, polylineAlpha, polylineWidth)
}

/**
 * Adds intermediate points to a path if the distance between consecutive points
 * exceeds [maxDistanceMeters]. This improves heatmap quality for sparse data.
 */
private fun densifyPath(path: List<LatLng>, maxDistanceMeters: Double): List<LatLng> {
    if (path.isEmpty()) return emptyList()
    val result = mutableListOf<LatLng>()

    for (i in 0 until path.size - 1) {
        val start = path[i]
        val end = path[i + 1]
        result.add(start)

        val distance = SphericalUtil.computeDistanceBetween(start, end)
        if (distance > maxDistanceMeters) {
            val numSegments = (distance / maxDistanceMeters).toInt()
            for (j in 1..numSegments) {
                val fraction = j.toDouble() / (numSegments + 1)
                result.add(SphericalUtil.interpolate(start, end, fraction))
            }
        }
    }
    result.add(path.last())
    return result
}
