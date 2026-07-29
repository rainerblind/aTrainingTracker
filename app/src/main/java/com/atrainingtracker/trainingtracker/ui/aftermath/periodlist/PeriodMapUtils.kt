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
                PeriodType.WEEK -> 0.6
                PeriodType.MONTH -> 0.8
                PeriodType.YEAR -> 1.0
                else -> 0.0
            }

            // ATT-310 & ATT-342: Adaptive densification based on scale and interaction
            val interval = if (zoom != null) {
                // Adaptive interval for interactive map
                when {
                    zoom < 10 -> 200.0
                    zoom < 12 -> 100.0
                    zoom < 14 -> 50.0
                    else -> 10.0
                }
            } else {
                // Static interval for summary cards
                when (periodType) {
                    PeriodType.WEEK -> 10.0
                    PeriodType.MONTH -> 50.0
                    PeriodType.YEAR -> 200.0
                    else -> 10.0
                }
            }

            // ATT-342 Refinement: Even tighter radius at low zoom to prevent bloating.
            // NOTE: HeatmapTileProvider requires radius between 10 and 50.
            val radius = if (zoom == null) {
                10 // Safe minimum for summary cards
            } else {
                // Adaptive formula: base 10 + drift from zoom 12.
                // It stays at 10px until zoom 12, then grows.
                // Clamped between 10 and 50 pixels to stay within HeatmapTileProvider bounds.
                (10 + (zoom - 12).coerceAtLeast(0f) * 4.0f).toInt().coerceIn(10, 50)
            }

            // ATT-342 OOM Fix: Define point caps to protect heap
            val maxPoints = if (zoom == null) 8000 else 100000

            // Use lower weight when zoomed out or in summary cards to reduce 'bloat' intensity.
            // This ensures only high-density areas (overlapping tracks) are prominent.
            val weight = if (zoom == null || zoom < 10) 0.3 else 1.0

            createHeatmapProvider(allPaths, opacity, radius = radius, densifyInterval = interval, maxPoints = maxPoints, weight = weight)
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
