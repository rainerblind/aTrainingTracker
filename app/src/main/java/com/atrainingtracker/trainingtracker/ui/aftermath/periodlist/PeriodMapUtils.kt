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
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.atrainingtracker.trainingtracker.ui.map.createHeatmapProvider

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
    isHeatmapEnabled: Boolean = true,
    isInteractive: Boolean = false
): PeriodMapVisuals {
    val heatmapProvider = run {
        if (allPaths.isEmpty() || periodType == PeriodType.DAY || !isHeatmapEnabled) {
            null
        } else {
            val opacity = when (periodType) {
                PeriodType.WEEK -> 0.6
                PeriodType.MONTH -> 0.8
                PeriodType.YEAR -> 1.0
                else -> 0.0
            }

            // ATT-310: Adaptive densification based on scale and interaction
            val interval = if (isInteractive) {
                10.0 // High quality for zoomed interactive map
            } else {
                when (periodType) {
                    PeriodType.WEEK -> 10.0
                    PeriodType.MONTH -> 50.0
                    PeriodType.YEAR -> 200.0
                    else -> 10.0
                }
            }

            createHeatmapProvider(allPaths, opacity, densifyInterval = interval)
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
