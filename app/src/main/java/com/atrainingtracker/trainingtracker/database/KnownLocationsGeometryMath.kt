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

package com.atrainingtracker.trainingtracker.database

import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric calculation result of merging two known location geofences (REQ-SET-095.4).
 *
 * @param center Combined or pinned geodetic center coordinate.
 * @param radius Resulting enclosing geofence radius in meters.
 * @param isPinned True if target was home base or user-locked and coordinate remained pinned.
 * @param distanceBetweenMeters Geodetic distance in meters between source and target centers.
 */
data class MergedGeofence(
    val center: LatLng,
    val radius: Int,
    val isPinned: Boolean,
    val distanceBetweenMeters: Double
)

/**
 * Pure mathematical geometry engine for calculating hybrid and minimum bounding circle (MBC)
 * geofences for Known Locations (Lieblingsorte) (REQ-SET-095.4).
 *
 * Implementation Invariants:
 * - Pure JVM Math: Uses self-contained Haversine and spherical linear interpolation (slerp)
 *   to avoid external dependencies and permit deterministic JVM unit testing without mocks.
 * - Pinned-Target Invariant (AC 2): When target is designated home base (`isHome == true`) or
 *   has user-calibrated altitude (`isLocked == true`), target center coordinate is strictly
 *   preserved and radius expands to enclose the source circle.
 * - Minimum Bounding Circle (AC 1): When target is unlocked and non-home, computes the minimum
 *   enclosing circle of both geofences.
 */
object KnownLocationsGeometryMath {

    private const val EARTH_RADIUS_METERS = 6371009.0

    /**
     * Computes the geodetic distance in meters between two coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(p1: LatLng, p2: LatLng): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Interpolates along the geodesic from [from] toward [to] by fractional distance [fraction]
     * using spherical linear interpolation (slerp).
     */
    fun interpolateGeodesic(from: LatLng, to: LatLng, fraction: Double): LatLng {
        if (fraction <= 0.0) return from
        if (fraction >= 1.0) return to

        val d = calculateDistanceMeters(from, to) / EARTH_RADIUS_METERS
        if (d < 1e-7) return from

        val fromLat = Math.toRadians(from.latitude)
        val fromLng = Math.toRadians(from.longitude)
        val toLat = Math.toRadians(to.latitude)
        val toLng = Math.toRadians(to.longitude)

        val sinD = sin(d)
        val a = sin((1.0 - fraction) * d) / sinD
        val b = sin(fraction * d) / sinD

        val x = a * cos(fromLat) * cos(fromLng) + b * cos(toLat) * cos(toLng)
        val y = a * cos(fromLat) * sin(fromLng) + b * cos(toLat) * sin(toLng)
        val z = a * sin(fromLat) + b * sin(toLat)

        val lat = atan2(z, sqrt(x * x + y * y))
        val lng = atan2(y, x)
        return LatLng(Math.toDegrees(lat), Math.toDegrees(lng))
    }

    /**
     * Calculates the merged geofence when merging [source] into [target] (REQ-SET-095.4).
     */
    fun calculateMergedGeofence(source: KnownLocationItem, target: KnownLocationItem): MergedGeofence {
        val d = calculateDistanceMeters(target.latLng, source.latLng)
        val rT = target.radius.toDouble()
        val rS = source.radius.toDouble()

        // Pinned-Target Mode: when target is home base or has locked altitude
        if (target.isHome || target.isLocked) {
            val newRadius = max(rT, ceil(d + rS)).toInt()
            return MergedGeofence(
                center = target.latLng,
                radius = newRadius,
                isPinned = true,
                distanceBetweenMeters = d
            )
        }

        // Minimum Bounding Circle Mode (unlocked, non-home target)
        // 1. Target already encloses source
        if (d + rS <= rT) {
            return MergedGeofence(
                center = target.latLng,
                radius = target.radius,
                isPinned = false,
                distanceBetweenMeters = d
            )
        }

        // 2. Source already encloses target
        if (d + rT <= rS) {
            return MergedGeofence(
                center = source.latLng,
                radius = source.radius,
                isPinned = false,
                distanceBetweenMeters = d
            )
        }

        // 3. General MBC between two intersecting or disjoint circles
        val newRadius = ceil((d + rT + rS) / 2.0).toInt()
        val fraction = if (d > 0.0) {
            ((d + rS - rT) / (2.0 * d)).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val newCenter = interpolateGeodesic(target.latLng, source.latLng, fraction)

        return MergedGeofence(
            center = newCenter,
            radius = newRadius,
            isPinned = false,
            distanceBetweenMeters = d
        )
    }
}
