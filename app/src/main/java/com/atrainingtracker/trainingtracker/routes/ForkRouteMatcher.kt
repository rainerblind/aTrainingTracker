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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.geo.GeoMath
import com.atrainingtracker.trainingtracker.geo.PolylineProjection
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.routes.RouteProximityRanker
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos

/**
 * Matches athlete trajectory against candidate routes along an outbound corridor.
 *
 * Conforms to REQ-MAP-031 clause 1, REQ-MAP-038 clauses 1 & 2, and REQ-GEO-003.
 */
object ForkRouteMatcher {

    const val MAX_CORRIDOR_TOLERANCE_METERS = 50.0
    const val MIN_SHARED_PREFIX_METERS = 300.0
    const val BOUNDING_BOX_CORRIDOR_MARGIN_METERS = 100.0

    /**
     * Checks if a geographic coordinate falls within a bounding box expanded by a corridor margin.
     *
     * Conforms to REQ-MAP-038 clause 1.
     */
    fun isPointWithinBoundingBox(
        point: LatLng,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        marginMeters: Double = BOUNDING_BOX_CORRIDOR_MARGIN_METERS
    ): Boolean {
        val deltaLat = marginMeters / 111_000.0
        val cosLat = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
        val deltaLng = marginMeters / (111_000.0 * cosLat)

        return point.latitude >= (minLat - deltaLat) &&
                point.latitude <= (maxLat + deltaLat) &&
                point.longitude >= (minLng - deltaLng) &&
                point.longitude <= (maxLng + deltaLng)
    }

    /**
     * Projects a coordinate onto a polyline path via canonical [GeoMath.projectOntoPolyline]
     * and returns the canonical [PolylineProjection] (REQ-GEO-003).
     */
    fun projectOntoPolyline(point: LatLng, path: List<PathPoint>): PolylineProjection {
        return GeoMath.projectOntoPolyline(path, point) ?: PolylineProjection(
            projectedLatLng = point,
            segmentIndex = 0,
            t = 0.0,
            alongTrackDistance = 0.0,
            crossTrackDistanceMeters = Double.MAX_VALUE,
            progressFraction = 0f,
            interpolatedAltitude = null,
            instantaneousGradePercent = null
        )
    }

    /**
     * Evaluates stored routes and filters down to candidates sharing the athlete's current outbound corridor.
     * Applies O(1) spatial bounding-box rejection and sport-type pre-filtering before polyline projections.
     *
     * Conforms to REQ-MAP-031 clause 1 and REQ-MAP-038 clauses 1 & 2.
     *
     * @param allRoutes All available routes in the database
     * @param currentPos Current GPS location of the athlete
     * @param recentHistory Optional recent positions from the workout tracking session
     * @param activeSportType Optional active workout sport discipline for sport-type pre-filtering
     * @param requireSelected Optional parameter to restrict evaluation strictly to routes selected by the athlete (REQ-MAP-041)
     * @return List of matching candidate routes
     */
    fun findCandidateRoutes(
        allRoutes: List<RouteWithPath>,
        currentPos: LatLng,
        recentHistory: List<LatLng>? = null,
        activeSportType: BSportType? = null,
        requireSelected: Boolean = false
    ): List<RouteWithPath> {
        val candidates = mutableListOf<RouteWithPath>()

        val deltaLat = BOUNDING_BOX_CORRIDOR_MARGIN_METERS / 111_000.0
        val cosLat = cos(Math.toRadians(currentPos.latitude)).coerceAtLeast(0.01)
        val deltaLng = BOUNDING_BOX_CORRIDOR_MARGIN_METERS / (111_000.0 * cosLat)

        for (route in allRoutes) {
            if (route.path.size < 2) continue

            // 0. Active/Selected Route Filter (REQ-MAP-041)
            if (requireSelected && !route.summary.isSelected) {
                continue
            }

            // 1. Sport-Type Pre-Filtering (REQ-MAP-038 clause 2)
            if (activeSportType != null && !RouteProximityRanker.matchesSport(route.summary.bSportType, activeSportType)) {
                continue
            }

            // 2. Spatial Bounding-Box Pre-Filtering (REQ-MAP-038 clause 1)
            val minLat = route.summary.minLat ?: route.path.minOf { it.latLng.latitude }
            val maxLat = route.summary.maxLat ?: route.path.maxOf { it.latLng.latitude }
            val minLng = route.summary.minLng ?: route.path.minOf { it.latLng.longitude }
            val maxLng = route.summary.maxLng ?: route.path.maxOf { it.latLng.longitude }

            if (currentPos.latitude < (minLat - deltaLat) ||
                currentPos.latitude > (maxLat + deltaLat) ||
                currentPos.longitude < (minLng - deltaLng) ||
                currentPos.longitude > (maxLng + deltaLng)
            ) {
                continue
            }

            // 3. Fine-grained projection onto polyline
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
                val sharedDistance = proj.alongTrackDistance - projOldest.alongTrackDistance
                if (sharedDistance < MIN_SHARED_PREFIX_METERS) {
                    // Check if current distance from route start satisfies prefix
                    if (proj.alongTrackDistance < MIN_SHARED_PREFIX_METERS) {
                        continue
                    }
                }
                // Check forward heading alignment
                if (sharedDistance <= 0.0 && proj.alongTrackDistance < MIN_SHARED_PREFIX_METERS) {
                    continue
                }
            } else {
                if (proj.alongTrackDistance < MIN_SHARED_PREFIX_METERS) {
                    continue
                }
            }

            candidates.add(route)
        }

        return candidates
    }
}
