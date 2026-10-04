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
import android.util.Log
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Intelligent background detector that identifies whether the athlete is currently
 * riding or running on a saved route (REQ-MAP-024 / ATT-1835).
 */
class RouteAutoDetector(
    private val startProximityThresholdMeters: Float = 100f,
    private val pathProximityThresholdMeters: Float = 50f,
    private val headingThresholdDegrees: Float = 45f,
    private val dismissalCooldownMillis: Long = 15 * 60 * 1000L // 15 minutes
) {

    private val dismissedRoutes = mutableMapOf<Long, Long>()

    /**
     * Evaluates user's current location against available routes.
     * Returns the best candidate RouteWithPath if detected, or null.
     */
    fun evaluate(
        location: Location,
        routes: List<RouteWithPath>,
        currentlyActiveRouteId: Long?,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): RouteWithPath? {
        if (routes.isEmpty()) return null

        for (route in routes) {
            val routeId = route.summary.id
            if (currentlyActiveRouteId != null && currentlyActiveRouteId == routeId) {
                continue
            }

            val dismissedAt = dismissedRoutes[routeId]
            if (dismissedAt != null && (currentTimeMillis - dismissedAt) < dismissalCooldownMillis) {
                continue
            }

            val path = route.path
            if (path.isEmpty()) continue

            // 1. Check proximity to start point
            val startPoint = path.first()
            val distToStart = computeDistanceMeters(
                location.latitude, location.longitude,
                startPoint.latLng.latitude, startPoint.latLng.longitude
            )

            if (distToStart <= startProximityThresholdMeters) {
                // If moving with sufficient speed, check heading alignment with start segment
                if (location.hasBearing() && location.speed >= 1.5f && path.size > 1) {
                    val segmentBearing = computeBearing(startPoint.latLng, path[1].latLng)
                    val angleDiff = computeAngleDifference(location.bearing, segmentBearing)
                    if (angleDiff <= headingThresholdDegrees) {
                        Log.d(TAG, "Candidate detected at start: ${route.summary.name} (dist=$distToStart m, angleDiff=$angleDiff)")
                        return route
                    }
                } else {
                    Log.d(TAG, "Candidate detected at start (low speed/no bearing): ${route.summary.name} (dist=$distToStart m)")
                    return route
                }
            }

            // 2. Check proximity along the path segments
            for (i in 0 until (path.size - 1)) {
                val p1 = path[i].latLng
                val p2 = path[i + 1].latLng
                val distToSegment = distanceToSegmentMeters(location.latitude, location.longitude, p1, p2)
                if (distToSegment <= pathProximityThresholdMeters) {
                    if (location.hasBearing() && location.speed >= 1.5f) {
                        val segmentBearing = computeBearing(p1, p2)
                        val angleDiff = computeAngleDifference(location.bearing, segmentBearing)
                        if (angleDiff <= headingThresholdDegrees) {
                            Log.d(TAG, "Candidate detected on path segment: ${route.summary.name} (dist=$distToSegment m, angleDiff=$angleDiff)")
                            return route
                        }
                    } else {
                        Log.d(TAG, "Candidate detected on path segment (low speed): ${route.summary.name}")
                        return route
                    }
                }
            }
        }

        return null
    }

    /**
     * Suppresses a route candidate from being suggested again during the cooldown window.
     */
    fun dismissRoute(routeId: Long, currentTimeMillis: Long = System.currentTimeMillis()) {
        dismissedRoutes[routeId] = currentTimeMillis
    }

    /**
     * Clears all cooldown dismissals.
     */
    fun clearDismissals() {
        dismissedRoutes.clear()
    }

    companion object {
        private const val TAG = "RouteAutoDetector"

        fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val earthRadius = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2.0) * sin(dLon / 2.0)
            val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
            return (earthRadius * c).toFloat()
        }

        fun computeBearing(from: LatLng, to: LatLng): Float {
            val lat1 = Math.toRadians(from.latitude)
            val lon1 = Math.toRadians(from.longitude)
            val lat2 = Math.toRadians(to.latitude)
            val lon2 = Math.toRadians(to.longitude)

            val dLon = lon2 - lon1
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            var bearing = Math.toDegrees(atan2(y, x)).toFloat()
            return (bearing + 360f) % 360f
        }

        fun computeAngleDifference(angle1: Float, angle2: Float): Float {
            val diff = abs(angle1 - angle2) % 360f
            return if (diff > 180f) 360f - diff else diff
        }

        fun distanceToSegmentMeters(
            pointLat: Double, pointLng: Double,
            p1: LatLng, p2: LatLng
        ): Float {
            // Simplified projection to segment
            val d1 = computeDistanceMeters(pointLat, pointLng, p1.latitude, p1.longitude)
            val d2 = computeDistanceMeters(pointLat, pointLng, p2.latitude, p2.longitude)
            val segmentLen = computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            if (segmentLen < 1e-4) return d1

            val r = ((pointLat - p1.latitude) * (p2.latitude - p1.latitude) +
                    (pointLng - p1.longitude) * (p2.longitude - p1.longitude)) /
                    ((p2.latitude - p1.latitude) * (p2.latitude - p1.latitude) +
                            (p2.longitude - p1.longitude) * (p2.longitude - p1.longitude))

            if (r <= 0.0) return d1
            if (r >= 1.0) return d2

            val projLat = p1.latitude + r * (p2.latitude - p1.latitude)
            val projLng = p1.longitude + r * (p2.longitude - p1.longitude)
            return computeDistanceMeters(pointLat, pointLng, projLat, projLng)
        }
    }
}
