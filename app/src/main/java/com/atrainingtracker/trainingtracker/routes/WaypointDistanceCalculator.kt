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
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlin.math.cos

/**
 * Utility for calculating the cumulative distance offset along a route polyline for waypoints.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - TST-MAP-028: Waypoint projection and distance calculation.
 */
object WaypointDistanceCalculator {

    /**
     * Projects a list of [RouteWaypoint] objects onto a polyline formed by [pathPoints]
     * and assigns an accurate cumulative `distanceFromStart` in meters.
     *
     * Returns the waypoints sorted in ascending order of `distanceFromStart`.
     */
    fun projectWaypoints(
        waypoints: List<RouteWaypoint>,
        pathPoints: List<PathPoint>
    ): List<RouteWaypoint> {
        if (waypoints.isEmpty()) return emptyList()
        if (pathPoints.isEmpty()) return waypoints
        if (pathPoints.size == 1) {
            return waypoints.map { it.copy(distanceFromStart = 0.0) }
        }

        val totalDist = pathPoints.last().distance

        val projected = waypoints.map { waypoint ->
            val wLat = waypoint.latLng.latitude
            val wLng = waypoint.latLng.longitude

            var minDistanceToSegment = Double.MAX_VALUE
            var bestDistanceAlongRoute = 0.0

            for (i in 0 until pathPoints.size - 1) {
                val p1 = pathPoints[i]
                val p2 = pathPoints[i + 1]

                val aLat = p1.latLng.latitude
                val aLng = p1.latLng.longitude
                val bLat = p2.latLng.latitude
                val bLng = p2.latLng.longitude

                val latAvgRad = Math.toRadians((aLat + bLat) / 2.0)
                val cosLat = cos(latAvgRad)

                val dx = (bLng - aLng) * cosLat
                val dy = bLat - aLat
                val segLenSq = dx * dx + dy * dy

                val t: Double = if (segLenSq < 1e-14) {
                    0.0
                } else {
                    val wx = (wLng - aLng) * cosLat
                    val wy = wLat - aLat
                    val proj = (wx * dx + wy * dy) / segLenSq
                    proj.coerceIn(0.0, 1.0)
                }

                val qLat = aLat + t * (bLat - aLat)
                val qLng = aLng + t * (bLng - aLng)

                val results = FloatArray(1)
                Location.distanceBetween(wLat, wLng, qLat, qLng, results)
                val distToSegment = results[0].toDouble()

                if (distToSegment < minDistanceToSegment) {
                    minDistanceToSegment = distToSegment
                    val segDist = (p2.distance - p1.distance).coerceAtLeast(0.0)
                    bestDistanceAlongRoute = p1.distance + t * segDist
                }
            }

            waypoint.copy(
                distanceFromStart = bestDistanceAlongRoute.coerceIn(0.0, totalDist)
            )
        }

        return projected.sortedBy { it.distanceFromStart }
    }
}
