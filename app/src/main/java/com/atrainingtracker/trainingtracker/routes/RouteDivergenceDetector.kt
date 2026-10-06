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

/**
 * Detects upcoming route divergence coordinates where candidate paths split.
 *
 * Conforms to REQ-MAP-031 clause 2.
 */
object RouteDivergenceDetector {

    const val DIVERGENCE_SEPARATION_THRESHOLD_METERS = 40.0
    const val DIVERGENCE_INITIATION_THRESHOLD_METERS = 8.0
    const val ALERT_WINDOW_MAX_METERS = 300.0
    const val ALERT_WINDOW_MIN_METERS = 0.0
    const val SAMPLE_STEP_METERS = 10.0
    const val LOOKAHEAD_METERS = 600.0

    /**
     * Interpolates the GPS coordinate along a polyline at a specific cumulative distance from start.
     */
    fun pointAtDistance(path: List<PathPoint>, targetDistance: Double): LatLng {
        if (path.isEmpty()) return LatLng(0.0, 0.0)
        if (path.size == 1 || targetDistance <= path.first().distance) {
            return path.first().latLng
        }
        if (targetDistance >= path.last().distance) {
            return path.last().latLng
        }

        for (i in 0 until path.size - 1) {
            val d1 = path[i].distance
            val d2 = path[i + 1].distance
            if (targetDistance in d1..d2) {
                val segLen = d2 - d1
                val t = if (segLen > 0.0) (targetDistance - d1) / segLen else 0.0
                val p1 = path[i].latLng
                val p2 = path[i + 1].latLng
                val lat = p1.latitude + t * (p2.latitude - p1.latitude)
                val lng = p1.longitude + t * (p2.longitude - p1.longitude)
                return LatLng(lat, lng)
            }
        }

        return path.last().latLng
    }

    /**
     * Computes the relative bearing classification (Left, Straight, Right) from an angle difference.
     */
    fun classifyRelativeDirection(bearingDeltaDegrees: Double): ForkDirection {
        return when {
            bearingDeltaDegrees < -20.0 -> ForkDirection.LEFT
            bearingDeltaDegrees > 20.0 -> ForkDirection.RIGHT
            else -> ForkDirection.STRAIGHT
        }
    }

    /**
     * Computes the signed angle difference in degrees in range [-180.0, 180.0].
     */
    fun normalizeAngleDelta(targetBearing: Double, referenceBearing: Double): Double {
        return ((targetBearing - referenceBearing + 540.0) % 360.0) - 180.0
    }

    /**
     * Detects an upcoming fork between candidate routes and computes distance and branch directions.
     *
     * @param candidateRoutes Candidate routes currently matching the outbound corridor
     * @param currentPos Current GPS location of the athlete
     * @return [ForkDecisionState] if a fork is within the 300m alert window, or null otherwise
     */
    fun detectDivergence(
        candidateRoutes: List<RouteWithPath>,
        currentPos: LatLng
    ): ForkDecisionState? {
        if (candidateRoutes.size < 2) return null

        val validCandidates = candidateRoutes.filter { route ->
            val proj = ForkRouteMatcher.projectOntoPolyline(currentPos, route.path)
            proj.crossTrackDistanceMeters <= ForkRouteMatcher.MAX_CORRIDOR_TOLERANCE_METERS
        }

        if (validCandidates.size < 2) return null

        val refRoute = validCandidates[0]
        val refProj = ForkRouteMatcher.projectOntoPolyline(currentPos, refRoute.path)
        val d0 = refProj.distanceAlongRouteMeters
        val maxDist = (d0 + LOOKAHEAD_METERS).coerceAtMost(refRoute.path.last().distance)

        var firstDivergenceDist = -1.0
        var confirmedDivergenceDist = -1.0

        var s = d0 + SAMPLE_STEP_METERS
        while (s <= maxDist) {
            val samplePt = pointAtDistance(refRoute.path, s)

            var maxSep = 0.0
            for (i in 1 until validCandidates.size) {
                val otherRoute = validCandidates[i]
                val projOther = ForkRouteMatcher.projectOntoPolyline(samplePt, otherRoute.path)
                if (projOther.crossTrackDistanceMeters > maxSep) {
                    maxSep = projOther.crossTrackDistanceMeters
                }
            }

            if (maxSep > DIVERGENCE_INITIATION_THRESHOLD_METERS && firstDivergenceDist < 0.0) {
                firstDivergenceDist = (s - SAMPLE_STEP_METERS).coerceAtLeast(d0)
            }

            if (maxSep > DIVERGENCE_SEPARATION_THRESHOLD_METERS) {
                confirmedDivergenceDist = s
                break
            }

            s += SAMPLE_STEP_METERS
        }

        if (confirmedDivergenceDist < 0.0) {
            return null
        }

        val junctionDist = if (firstDivergenceDist > 0.0) firstDivergenceDist else (confirmedDivergenceDist - SAMPLE_STEP_METERS).coerceAtLeast(d0)
        val divergencePoint = pointAtDistance(refRoute.path, junctionDist)
        val distanceToFork = (junctionDist - d0).coerceAtLeast(0.0)

        // Must be within alert window (0m < distanceToFork <= 300m)
        if (distanceToFork <= ALERT_WINDOW_MIN_METERS || distanceToFork > ALERT_WINDOW_MAX_METERS) {
            return null
        }

        // Approach bearing: vector along shared corridor approaching the fork point
        val approachPtBefore = pointAtDistance(refRoute.path, (junctionDist - 30.0).coerceAtLeast(0.0))
        val approachBearing = if (GeoUtils.haversineDistanceMeters(
                approachPtBefore.latitude, approachPtBefore.longitude,
                divergencePoint.latitude, divergencePoint.longitude
            ) > 5.0
        ) {
            GeoUtils.calculateInitialBearing(
                approachPtBefore.latitude, approachPtBefore.longitude,
                divergencePoint.latitude, divergencePoint.longitude
            )
        } else {
            GeoUtils.calculateInitialBearing(
                currentPos.latitude, currentPos.longitude,
                divergencePoint.latitude, divergencePoint.longitude
            )
        }

        val branchOptions = validCandidates.map { route ->
            val routeProj = ForkRouteMatcher.projectOntoPolyline(divergencePoint, route.path)
            val branchLookaheadDist = (routeProj.distanceAlongRouteMeters + 40.0).coerceAtMost(route.path.last().distance)
            val branchPtAfter = pointAtDistance(route.path, branchLookaheadDist)

            val branchBearing = GeoUtils.calculateInitialBearing(
                divergencePoint.latitude, divergencePoint.longitude,
                branchPtAfter.latitude, branchPtAfter.longitude
            )

            val deltaAngle = normalizeAngleDelta(branchBearing, approachBearing)
            val dir = classifyRelativeDirection(deltaAngle)

            ForkBranchOption(
                routeId = route.summary.id,
                routeName = route.summary.name,
                totalDistanceMeters = route.summary.distance,
                totalElevationMeters = route.summary.elevationGain,
                direction = dir,
                bearingDiffDegrees = deltaAngle
            )
        }.sortedBy { it.bearingDiffDegrees }

        return ForkDecisionState(
            divergenceCoordinate = divergencePoint,
            distanceToForkMeters = distanceToFork,
            branches = branchOptions,
            isApproaching = true
        )
    }
}
