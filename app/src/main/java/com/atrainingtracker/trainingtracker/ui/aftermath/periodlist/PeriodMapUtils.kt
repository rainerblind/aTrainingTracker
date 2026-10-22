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
import com.google.android.gms.maps.model.TileProvider
import com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProvider

/**
 * Encapsulates the visual styling for period-based maps.
 */
data class PeriodMapVisuals(
    val heatmapProvider: TileProvider?,
    val polylineAlpha: Float,
    val polylineWidth: Float
)

/**
 * Centralized logic to determine how workouts are visualized based on the period level.
 * Transitions from clear polylines (Day) to prominent line-density heatmaps (Year).
 * @param zoom The current map zoom level. If null, a default summary radius is used.
 */
fun getPeriodMapVisuals(
    periodType: PeriodType,
    allPaths: List<List<LatLng>>,
    isInteractive: Boolean = false,
    zoom: Float? = null
): PeriodMapVisuals {
    val heatmapProvider = run {
        if (allPaths.isEmpty() || periodType == PeriodType.DAY) {
            null
        } else {
            val opacity = when (periodType) {
                PeriodType.WEEK -> 0.4
                PeriodType.MONTH -> 0.5
                PeriodType.YEAR -> 0.6
                else -> 0.5
            }

            val strokeWidth = when {
                zoom != null -> (4f + (zoom - 10f).coerceAtLeast(0f) * 0.5f).coerceIn(3.5f, 7.5f)
                periodType == PeriodType.WEEK -> 6f
                periodType == PeriodType.MONTH -> 5f
                periodType == PeriodType.YEAR -> 4f
                else -> 5f
            }

            PolylineHeatmapTileProvider(
                allPaths = allPaths,
                opacity = opacity,
                strokeWidthPx = strokeWidth
            )
        }
    }

    val polylineAlpha = when {
        periodType == PeriodType.DAY -> 1.0f
        periodType == PeriodType.WEEK -> 0.9f
        periodType == PeriodType.MONTH -> 0.8f
        periodType == PeriodType.YEAR -> 0.6f
        else -> 1.0f
    }

    val polylineWidth = when {
        periodType == PeriodType.DAY -> 8f
        periodType == PeriodType.WEEK -> 8f
        periodType == PeriodType.MONTH -> 7f
        periodType == PeriodType.YEAR -> 5f
        else -> 8f
    }

    return PeriodMapVisuals(heatmapProvider, polylineAlpha, polylineWidth)
}
