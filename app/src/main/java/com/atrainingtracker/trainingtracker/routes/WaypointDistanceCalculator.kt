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

import com.atrainingtracker.trainingtracker.geo.GeoMath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint

/**
 * Utility for calculating the cumulative distance offset along a route polyline for waypoints.
 *
 * Traceability:
 * - REQ-MAP-026: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.
 * - REQ-GEO-003: Consolidated polyline projection via [GeoMath].
 * - TST-MAP-028: Waypoint projection and distance calculation.
 */
object WaypointDistanceCalculator {

    /**
     * Projects a list of [RouteWaypoint] objects onto a polyline formed by [pathPoints]
     * via canonical [GeoMath.projectOntoPolyline] and assigns an accurate cumulative
     * `distanceFromStart` in meters.
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

        val originDist = pathPoints.first().distance
        val totalDist = pathPoints.last().distance

        val projected = waypoints.map { waypoint ->
            val proj = GeoMath.projectOntoPolyline(pathPoints, waypoint.latLng)
            val bestDistanceAlongRoute = if (proj != null) {
                originDist + proj.alongTrackDistance
            } else {
                0.0
            }
            waypoint.copy(
                distanceFromStart = bestDistanceAlongRoute.coerceIn(0.0, totalDist)
            )
        }

        return projected.sortedBy { it.distanceFromStart }
    }
}
