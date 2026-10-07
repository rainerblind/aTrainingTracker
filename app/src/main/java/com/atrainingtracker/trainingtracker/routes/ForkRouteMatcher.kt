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

import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos

/**
 * Result of projecting a point onto a polyline.
 */
data class PolylineProjection(
    val distanceAlongRouteMeters: Double,
    val crossTrackDistanceMeters: Double,
    val nearestSegmentIndex: Int,
    val projectedPoint: LatLng
)

/**
 * Matches athlete trajectory against candidate routes along an outbound corridor.
 *
 * Conforms to REQ-MAP-031 clause 1.
 */
object ForkRouteMatcher {

    const val MAX_CORRIDOR_TOLERANCE_METERS = 50.0
    const val MIN_SHARED_PREFIX_METERS = 300.0

    /**
     * Projects a coordinate onto a polyline path and returns orthogonal cross-track distance
     * and cumulative distance along the path.
     */
    fun projectOntoPolyline(point: LatLng, path: List<PathPoint>): PolylineProjection {
        if (path.isEmpty()) {
            return PolylineProjection(0.0, Double.MAX_VALUE, 0, point)
        }
        if (path.size == 1) {
            val d = GeoUtils.haversineDistanceMeters(
                point.latitude, point.longitude,
                path[0].latLng.latitude, path[0].latLng.longitude
            )
            return PolylineProjection(0.0, d, 0, path[0].latLng)
        }

        var minDistance = Double.MAX_VALUE
        var bestSegmentIndex = 0
        var bestT = 0.0
        var bestProjected = path[0].latLng

        for (i in 0 until path.size - 1) {
            val p1 = path[i].latLng
            val p2 = path[i + 1].latLng

            val midLatRad = Math.toRadians((p1.latitude + p2.latitude) / 2.0)
            val cosMid = cos(midLatRad)
            val dx = (p2.longitude - p1.longitude) * cosMid
            val dy = p2.latitude - p1.latitude
            val vx = (point.longitude - p1.longitude) * cosMid
            val vy = point.latitude - p1.latitude

            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 0.0) ((vx * dx + vy * dy) / lenSq).coerceIn(0.0, 1.0) else 0.0

            val projLat = p1.latitude + t * (p2.latitude - p1.latitude)
            val projLng = p1.longitude + t * (p2.longitude - p1.longitude)

            val dist = GeoUtils.haversineDistanceMeters(
                point.latitude, point.longitude,
                projLat, projLng
            )

            if (dist < minDistance) {
                minDistance = dist
                bestSegmentIndex = i
                bestT = t
                bestProjected = LatLng(projLat, projLng)
            }
        }

        val segStartDist = path[bestSegmentIndex].distance
        val segEndDist = path[bestSegmentIndex + 1].distance
        val distAlong = segStartDist + bestT * (segEndDist - segStartDist)

        return PolylineProjection(
            distanceAlongRouteMeters = distAlong,
            crossTrackDistanceMeters = minDistance,
            nearestSegmentIndex = bestSegmentIndex,
            projectedPoint = bestProjected
        )
    }

    /**
     * Evaluates stored routes and filters down to candidates sharing the athlete's current outbound corridor.
     *
     * @param allRoutes All available routes in the database
     * @param currentPos Current GPS location of the athlete
     * @param recentHistory Optional recent positions from the workout tracking session
     * @return List of matching candidate routes
     */
    fun findCandidateRoutes(
        allRoutes: List<RouteWithPath>,
        currentPos: LatLng,
        recentHistory: List<LatLng>? = null
    ): List<RouteWithPath> {
        val candidates = mutableListOf<RouteWithPath>()

        for (route in allRoutes) {
            if (route.path.size < 2) continue

            val proj = projectOntoPolyline(currentPos, route.path)
            if (proj.crossTrackDistanceMeters > MAX_CORRIDOR_TOLERANCE_METERS) {
                continue
            }

            // Must have shared prefix of at least MIN_SHARED_PREFIX_METERS
            if (recentHistory != null && recentHistory.isNotEmpty()) {
                val oldestPoint = recentHistory.first()
                val projOldest = projectOntoPolyline(oldestPoint, route.path)
                if (projOldest.crossTrackDistanceMeters > MAX_CORRIDOR_TOLERANCE_METERS) {
                    continue
                }
                val sharedDistance = proj.distanceAlongRouteMeters - projOldest.distanceAlongRouteMeters
                if (sharedDistance < MIN_SHARED_PREFIX_METERS) {
                    // Check if current distance from route start satisfies prefix
                    if (proj.distanceAlongRouteMeters < MIN_SHARED_PREFIX_METERS) {
                        continue
                    }
                }
                // Check forward heading alignment
                if (sharedDistance <= 0.0 && proj.distanceAlongRouteMeters < MIN_SHARED_PREFIX_METERS) {
                    continue
                }
            } else {
                if (proj.distanceAlongRouteMeters < MIN_SHARED_PREFIX_METERS) {
                    continue
                }
            }

            candidates.add(route)
        }

        return candidates
    }
}
