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
import com.google.maps.android.heatmaps.WeightedLatLng

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
    allPaths: List<List<LatLng>>,
    isHeatmapEnabled: Boolean = true
): PeriodMapVisuals {
    val heatmapProvider = run {
        if (allPaths.isEmpty() || periodType == PeriodType.DAY || !isHeatmapEnabled) {
            null
        } else {
            // Densify points for cycle/fast activities so the heatmap looks like a continuous trail
            // instead of disconnected blobs due to downsampling.
            // We use WeightedLatLng to give every point a base "intensity" boost so single
            // workouts are clearly visible even in long periods.
            val allPoints = allPaths.flatMap { path ->
                densifyPath(path, 10.0).map { WeightedLatLng(it, 2.0) }
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
                    .weightedData(allPoints)
                    .opacity(opacity)
                    .radius(20) // Balanced radius
                    .build()
            }
        }
    }

    val polylineAlpha = when {
        !isHeatmapEnabled -> 1.0f
        periodType == PeriodType.DAY -> 1.0f
        periodType == PeriodType.WEEK -> 0.9f
        periodType == PeriodType.MONTH -> 0.8f
        periodType == PeriodType.YEAR -> 0.6f
        else -> 1.0f
    }

    val polylineWidth = when {
        !isHeatmapEnabled -> 8f
        periodType == PeriodType.DAY -> 8f
        periodType == PeriodType.WEEK -> 8f
        periodType == PeriodType.MONTH -> 7f
        periodType == PeriodType.YEAR -> 5f
        else -> 8f
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
