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

package com.atrainingtracker.trainingtracker.routes

import android.location.Location

/**
 * Android WGS-84 ellipsoidal point-to-point distance helper with deterministic JVM fallback.
 *
 * Separated from [GeoUtils] to preserve [GeoUtils]'s zero-Android-import invariant while
 * consolidating [Location.distanceBetween] + `catch (RuntimeException)` fallback across
 * clustering, spatial region, and ViewModel callers (`ATT-6035`, `REQ-PRO-137.1`).
 */
object AndroidGeoDistance {

    /**
     * Computes WGS-84 ellipsoidal distance in meters via [Location.distanceBetween], falling back
     * to [GeoUtils.haversineDistanceMeters] when Android framework methods are unmocked in plain
     * JVM unit tests (`RuntimeException`).
     */
    @JvmStatic
    fun ellipsoidalDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return try {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            results[0].toDouble()
        } catch (_: RuntimeException) {
            GeoUtils.haversineDistanceMeters(lat1, lon1, lat2, lon2)
        }
    }
}
