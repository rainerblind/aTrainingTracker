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
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlin.math.cos

/**
 * Result of snapping athlete coordinates to a return navigation corridor.
 */
data class CorridorSnapResult(
    val destinationName: String,
    val isHomeDestination: Boolean,
    val remainingDistanceMeters: Double,
    val remainingClimbMeters: Double,
    val isReverseReturn: Boolean = false,
    val snappedRouteId: Long? = null,
    val crossTrackDistanceMeters: Double = 0.0,
    val nearestSegmentIndex: Int = 0,
    val path: List<PathPoint> = emptyList()
)

/**
 * Evaluates athlete GPS coordinates against the active route, candidate saved routes,
 * or direct geodesic home vector to compute remaining return metrics (REQ-MAP-029).
 */
object ReturnCorridorSnapper {

    const val MAX_HOME_SEARCH_RADIUS_METERS = 500.0
    const val MAX_CORRIDOR_MATCH_RADIUS_METERS = 200.0

    /**
     * Calculates the return navigation state given athlete coordinates,
     * optional active route, home destination, candidate saved routes,
     * and whether reverse return is forced.
     */
    @JvmStatic
    fun snapToCorridor(
        currentLat: Double,
        currentLng: Double,
        currentAltitude: Double = 0.0,
        activeRoute: RouteWithPath? = null,
        homeDestination: HomeDestination? = null,
        savedRoutes: List<RouteWithPath> = emptyList(),
        forceReverseReturn: Boolean = false
    ): CorridorSnapResult? {
        // Case 1: Active route is present
        if (activeRoute != null && activeRoute.path.isNotEmpty()) {
            return snapActiveRoute(
                currentLat = currentLat,
                currentLng = currentLng,
                route = activeRoute,
                isReverseReturn = forceReverseReturn,
                homeDestination = homeDestination
            )
        }

        // Case 2: Candidate saved routes ending or starting near Home (<= 500m)
        if (homeDestination != null && savedRoutes.isNotEmpty()) {
            val matchingRoute = findBestCandidateRouteToHome(
                currentLat = currentLat,
                currentLng = currentLng,
                homeDestination = homeDestination,
                savedRoutes = savedRoutes
            )
            if (matchingRoute != null) {
                return matchingRoute
            }
        }

        // Case 3: Direct geodesic fallback to Home destination
        if (homeDestination != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLat,
                currentLng,
                homeDestination.latLng.latitude,
                homeDestination.latLng.longitude,
                results
            )
            val dist = results[0].toDouble()
            val climb = (homeDestination.altitude - currentAltitude).coerceAtLeast(0.0)

            return CorridorSnapResult(
                destinationName = homeDestination.name,
                isHomeDestination = true,
                remainingDistanceMeters = dist,
                remainingClimbMeters = climb,
                isReverseReturn = false,
                snappedRouteId = null,
                crossTrackDistanceMeters = 0.0
            )
        }

        return null
    }

    /**
     * Snaps current location to route path and computes remaining distance & climb.
     */
    @JvmStatic
    fun snapActiveRoute(
        currentLat: Double,
        currentLng: Double,
        route: RouteWithPath,
        isReverseReturn: Boolean,
        homeDestination: HomeDestination? = null
    ): CorridorSnapResult {
        val path = route.path
        if (path.isEmpty()) {
            return CorridorSnapResult(
                destinationName = route.summary.name,
                isHomeDestination = false,
                remainingDistanceMeters = 0.0,
                remainingClimbMeters = 0.0,
                snappedRouteId = route.summary.id
            )
        }

        var minDistanceToSegment = Double.MAX_VALUE
        var bestDistanceAlongRoute = 0.0
        var bestSegmentIndex = 0
        var bestT = 0.0

        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]

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
                val wx = (currentLng - aLng) * cosLat
                val wy = currentLat - aLat
                val proj = (wx * dx + wy * dy) / segLenSq
                proj.coerceIn(0.0, 1.0)
            }

            val qLat = aLat + t * (bLat - aLat)
            val qLng = aLng + t * (bLng - aLng)

            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLng, qLat, qLng, results)
            val distToSegment = results[0].toDouble()

            if (distToSegment < minDistanceToSegment) {
                minDistanceToSegment = distToSegment
                val segDist = (p2.distance - p1.distance).coerceAtLeast(0.0)
                bestDistanceAlongRoute = p1.distance + t * segDist
                bestSegmentIndex = i
                bestT = t
            }
        }

        val totalDist = path.last().distance
        val currentDistAlongRoute = bestDistanceAlongRoute.coerceIn(0.0, totalDist)

        val remainingDist: Double
        val remainingClimb: Double
        val destName: String
        val isHome: Boolean

        if (isReverseReturn) {
            remainingDist = currentDistAlongRoute
            remainingClimb = ElevationAwareEtaCalculator.calculateReverseClimbAlongPath(path, bestSegmentIndex, bestT)

            val startLoc = path.first().latLng
            val distToHome = if (homeDestination != null) {
                val res = FloatArray(1)
                Location.distanceBetween(
                    startLoc.latitude, startLoc.longitude,
                    homeDestination.latLng.latitude, homeDestination.latLng.longitude,
                    res
                )
                res[0].toDouble()
            } else Double.MAX_VALUE

            if (distToHome <= MAX_HOME_SEARCH_RADIUS_METERS && homeDestination != null) {
                destName = homeDestination.name
                isHome = true
            } else {
                destName = "${route.summary.name} (Start)"
                isHome = false
            }
        } else {
            remainingDist = (totalDist - currentDistAlongRoute).coerceAtLeast(0.0)
            remainingClimb = ElevationAwareEtaCalculator.calculateClimbAlongPath(path, bestSegmentIndex, bestT)

            val endLoc = path.last().latLng
            val distToHome = if (homeDestination != null) {
                val res = FloatArray(1)
                Location.distanceBetween(
                    endLoc.latitude, endLoc.longitude,
                    homeDestination.latLng.latitude, homeDestination.latLng.longitude,
                    res
                )
                res[0].toDouble()
            } else Double.MAX_VALUE

            if (distToHome <= MAX_HOME_SEARCH_RADIUS_METERS && homeDestination != null) {
                destName = homeDestination.name
                isHome = true
            } else {
                destName = route.summary.name
                isHome = false
            }
        }

        return CorridorSnapResult(
            destinationName = destName,
            isHomeDestination = isHome,
            remainingDistanceMeters = remainingDist,
            remainingClimbMeters = remainingClimb,
            isReverseReturn = isReverseReturn,
            snappedRouteId = route.summary.id,
            crossTrackDistanceMeters = minDistanceToSegment,
            nearestSegmentIndex = bestSegmentIndex,
            path = path
        )
    }

    private fun findBestCandidateRouteToHome(
        currentLat: Double,
        currentLng: Double,
        homeDestination: HomeDestination,
        savedRoutes: List<RouteWithPath>
    ): CorridorSnapResult? {
        var bestResult: CorridorSnapResult? = null
        var minCrossTrack = Double.MAX_VALUE

        for (candidate in savedRoutes) {
            if (candidate.path.size < 2) continue

            // 1. Candidate ending near home
            val endPoint = candidate.path.last().latLng
            val resEnd = FloatArray(1)
            Location.distanceBetween(
                endPoint.latitude, endPoint.longitude,
                homeDestination.latLng.latitude, homeDestination.latLng.longitude,
                resEnd
            )
            val distEndToHome = resEnd[0].toDouble()

            if (distEndToHome <= MAX_HOME_SEARCH_RADIUS_METERS) {
                val snap = snapActiveRoute(
                    currentLat = currentLat,
                    currentLng = currentLng,
                    route = candidate,
                    isReverseReturn = false,
                    homeDestination = homeDestination
                )
                if (snap.crossTrackDistanceMeters <= MAX_CORRIDOR_MATCH_RADIUS_METERS &&
                    snap.crossTrackDistanceMeters < minCrossTrack
                ) {
                    minCrossTrack = snap.crossTrackDistanceMeters
                    bestResult = snap
                }
            }

            // 2. Candidate starting near home (can be reversed)
            val startPoint = candidate.path.first().latLng
            val resStart = FloatArray(1)
            Location.distanceBetween(
                startPoint.latitude, startPoint.longitude,
                homeDestination.latLng.latitude, homeDestination.latLng.longitude,
                resStart
            )
            val distStartToHome = resStart[0].toDouble()

            if (distStartToHome <= MAX_HOME_SEARCH_RADIUS_METERS) {
                val snap = snapActiveRoute(
                    currentLat = currentLat,
                    currentLng = currentLng,
                    route = candidate,
                    isReverseReturn = true,
                    homeDestination = homeDestination
                )
                if (snap.crossTrackDistanceMeters <= MAX_CORRIDOR_MATCH_RADIUS_METERS &&
                    snap.crossTrackDistanceMeters < minCrossTrack
                ) {
                    minCrossTrack = snap.crossTrackDistanceMeters
                    bestResult = snap
                }
            }
        }

        return bestResult
    }
}
