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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.routes.RouteAutoDetector
import com.google.android.gms.maps.model.LatLng

/**
 * Multi-stage ranking and tie-breaking engine for route proximity selection (REQ-MAP-024 / ATT-1835).
 *
 * Implements 5-tier tie-breaking especially designed for "Home Hub" scenarios where multiple
 * routes share the identical starting location (d <= 250m):
 * - Tier 1: Proximity Group (< 250m from athlete's location)
 * - Tier 2: Active Sport Profile Match (e.g. Cycling vs Running)
 * - Tier 3: Movement Heading Alignment (delta bearing <= 45° departing initial segment)
 * - Tier 4: Recency & Frequency (syncedAt timestamp descending)
 * - Tier 5: Fallback sorting by ascending distance, then name
 *
 * Routes starting > 250m away are sorted strictly by ascending geodesic distance to start point.
 */
object RouteProximityRanker {

    const val HOME_PROXIMITY_RADIUS_METERS = 250.0f
    const val HEADING_ALIGNMENT_TOLERANCE_DEG = 45.0f

    /**
     * Ranks a collection of routes according to proximity, active sport, heading, and recency.
     */
    fun rankRoutes(
        routes: List<RouteWithPath>,
        currentLocation: LatLng?,
        currentBearing: Float?,
        activeSport: BSportType?
    ): List<RouteWithPath> {
        if (routes.isEmpty()) return emptyList()

        if (currentLocation == null) {
            // Null location fallback: sort by recency (descending), then length (ascending), then name
            return routes.sortedWith(
                compareByDescending<RouteWithPath> { it.summary.syncedAt }
                    .thenBy { it.summary.distance }
                    .thenBy { it.summary.name }
            )
        }

        // Calculate start point distances
        val routesWithDistance = routes.map { route ->
            val startPoint = route.path.firstOrNull()?.latLng
            val dist = if (startPoint != null) {
                RouteAutoDetector.computeDistanceMeters(
                    currentLocation.latitude, currentLocation.longitude,
                    startPoint.latitude, startPoint.longitude
                )
            } else {
                Float.MAX_VALUE
            }
            Pair(route, dist)
        }

        val inRadius = routesWithDistance.filter { it.second <= HOME_PROXIMITY_RADIUS_METERS }
        val outOfRadius = routesWithDistance.filter { it.second > HOME_PROXIMITY_RADIUS_METERS }

        // Tier 1 group sorting with Tiers 2-5
        val sortedInRadius = inRadius.map { it.first }.sortedWith { r1, r2 ->
            // Tier 2: Sport profile match
            val sportMatch1 = activeSport != null && r1.summary.bSportType == activeSport
            val sportMatch2 = activeSport != null && r2.summary.bSportType == activeSport
            if (sportMatch1 != sportMatch2) {
                return@sortedWith if (sportMatch1) -1 else 1
            }

            // Tier 3: Movement heading alignment (if currentBearing is present)
            if (currentBearing != null) {
                val aligns1 = checkHeadingAlignment(r1, currentBearing)
                val aligns2 = checkHeadingAlignment(r2, currentBearing)
                if (aligns1 != aligns2) {
                    return@sortedWith if (aligns1) -1 else 1
                }
            }

            // Tier 4: Recency & frequency (syncedAt descending)
            if (r1.summary.syncedAt != r2.summary.syncedAt) {
                return@sortedWith r2.summary.syncedAt.compareTo(r1.summary.syncedAt)
            }

            // Tier 5: Fallback ascending distance, then alphabetical name
            val distCompare = r1.summary.distance.compareTo(r2.summary.distance)
            if (distCompare != 0) {
                return@sortedWith distCompare
            }
            r1.summary.name.compareTo(r2.summary.name)
        }

        // Out-of-radius routes: sort by distance to start point ascending
        val sortedOutOfRadius = outOfRadius.sortedBy { it.second }.map { it.first }

        return sortedInRadius + sortedOutOfRadius
    }

    private fun checkHeadingAlignment(route: RouteWithPath, currentBearing: Float): Boolean {
        val path = route.path
        if (path.size < 2) return false

        val p1 = path.first().latLng
        // Find first point at least 50m away, or fall back to second point
        val targetPoint = path.drop(1).firstOrNull {
            RouteAutoDetector.computeDistanceMeters(p1.latitude, p1.longitude, it.latLng.latitude, it.latLng.longitude) >= 50f
        }?.latLng ?: path[1].latLng

        val departureBearing = RouteAutoDetector.computeBearing(p1, targetPoint)
        val angleDiff = RouteAutoDetector.computeAngleDifference(currentBearing, departureBearing)
        return angleDiff <= HEADING_ALIGNMENT_TOLERANCE_DEG
    }

    /**
     * Checks if a route's sport type is compatible with the active sport discipline (REQ-UI-310 / ATT-2668).
     *
     * Invariants:
     * - If activeSport is null, UNKNOWN, or CONFLICT: all routes are compatible.
     * - If activeSport is BIKE or RUN: only routes matching activeSport or UNKNOWN (untagged) are compatible.
     */
    fun matchesSport(routeSport: BSportType, activeSport: BSportType?): Boolean {
        if (activeSport == null || activeSport == BSportType.UNKNOWN || activeSport == BSportType.CONFLICT) {
            return true
        }
        return routeSport == activeSport || routeSport == BSportType.UNKNOWN
    }

    /**
     * Filters routes strictly within the configurable radius (in meters) from the current GPS position,
     * matching the active sport discipline (REQ-UI-310), and sorts qualifying routes strictly by
     * recency (syncedAt descending - "Zuletzt gefahren"), breaking ties by distance to start point
     * ascending, then name (REQ-UI-281).
     *
     * If currentLocation is null or routes is empty, returns emptyList() since distance cannot be evaluated.
     */
    fun filterAndRankRoutes(
        routes: List<RouteWithPath>,
        currentLocation: LatLng?,
        radiusMeters: Float = 1000.0f,
        activeSport: BSportType? = null
    ): List<RouteWithPath> {
        if (routes.isEmpty() || currentLocation == null) return emptyList()

        val qualifyingRoutes = mutableListOf<Pair<RouteWithPath, Float>>()
        for (route in routes) {
            if (!matchesSport(route.summary.bSportType, activeSport)) {
                continue
            }
            val startPoint = route.path.firstOrNull()?.latLng ?: continue
            val dist = RouteAutoDetector.computeDistanceMeters(
                currentLocation.latitude, currentLocation.longitude,
                startPoint.latitude, startPoint.longitude
            )
            if (dist <= radiusMeters) {
                qualifyingRoutes.add(Pair(route, dist))
            }
        }

        return qualifyingRoutes
            .sortedWith(
                compareByDescending<Pair<RouteWithPath, Float>> { it.first.summary.syncedAt }
                    .thenBy { it.second }
                    .thenBy { it.first.summary.name }
            )
            .map { it.first }
    }
}

