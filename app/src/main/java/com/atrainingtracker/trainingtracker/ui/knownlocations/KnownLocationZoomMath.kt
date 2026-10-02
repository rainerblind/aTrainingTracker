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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import kotlin.math.cos
import kotlin.math.ln

/**
 * Pure mathematical calculations for map preview thumbnail camera zoom levels on [KnownLocationCard]
 * (REQ-UI-224 / ATT-1878).
 *
 * Calibrates camera zoom to ensure the circular geofence boundary is fully contained without edge clipping
 * and displays rich regional neighborhood context (~1.4 km - 5.8 km visible span) across all geofence radii (50m - 1000m).
 */
object KnownLocationZoomMath {
    const val DEFAULT_THUMBNAIL_ZOOM: Float = 12.0f
    const val MIN_ZOOM: Float = 10.5f
    const val MAX_ZOOM: Float = 12.5f
    const val TARGET_GEOFENCE_DIAMETER_RATIO: Float = 0.35f

    /**
     * Calculates the optimal camera zoom level for an 80dp square map preview thumbnail.
     *
     * @param radiusMeters The geofence radius in meters (typically 50m - 1000m).
     * @param latitude The geodetic latitude in degrees (used to scale Web Mercator resolution).
     * @return The clamped camera zoom level strictly within [MIN_ZOOM, MAX_ZOOM].
     */
    fun calculateThumbnailZoom(radiusMeters: Int, latitude: Double = 0.0): Float {
        val safeRadius = radiusMeters.coerceAtLeast(10)
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val cosLat = cos(latRad).coerceAtLeast(0.01)

        // Web Mercator circumference constant at zoom 0 across 80dp:
        // 80dp * 156543.03392 m/dp = 12,523,442.7136 m
        val targetSpanMeters = (2.0 * safeRadius) / TARGET_GEOFENCE_DIAMETER_RATIO
        val numerator = 80.0 * 156543.03392 * cosLat
        val rawZoom = (ln(numerator / targetSpanMeters) / ln(2.0)).toFloat()

        return rawZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
}
