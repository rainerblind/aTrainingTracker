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
        val allPoints = allPaths.flatten()
        if (allPoints.isEmpty() || periodType == PeriodType.DAY) {
            null
        } else {
            val opacity = when (periodType) {
                PeriodType.WEEK -> 0.6
                PeriodType.MONTH -> 0.8
                PeriodType.YEAR -> 1.0
                else -> 0.0
            }
            HeatmapTileProvider.Builder()
                .data(allPoints)
                .opacity(opacity)
                .radius(30) // Increased radius for more "glow"
                .build()
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
