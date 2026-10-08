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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.routes

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Encapsulates a candidate segment that spatially and directionally matches a route polyline.
 *
 * @property segment The matched segment with metadata and geometry.
 * @property startDistanceMeters The accumulated route distance (in meters) where this segment begins.
 * @property endDistanceMeters The accumulated route distance (in meters) where this segment ends.
 * @property startPathIndex The index in route [PathPoint] list closest to the segment's start coordinate.
 * @property endPathIndex The index in route [PathPoint] list closest to the segment's end coordinate.
 */
@Immutable
data class MatchedRouteSegment(
    val segment: SegmentWithPath,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val startPathIndex: Int,
    val endPathIndex: Int
)

/**
 * Geodesic spatial corridor and directional traversal engine that correlates saved and starred
 * Strava/local segments along planned routes (REQ-UI-302, ATT-2583).
 */
object RouteSegmentMatcher {

    /** Maximum allowed geodesic distance in meters from segment terminal point to route polyline. */
    const val CORRIDOR_TOLERANCE_METERS = 25.0

    /** Maximum allowed angular difference in degrees between segment traversal and route heading. */
    const val MAX_BEARING_DELTA_DEGREES = 45.0

    /** Maximum allowed geodesic distance in meters from segment midpoint to route points. */
    const val MIDPOINT_TOLERANCE_METERS = 50.0

    /**
     * Matches candidate segments along a route on [Dispatchers.Default] to avoid blocking UI composition.
     */
    suspend fun matchSegments(
        routePath: List<PathPoint>,
        candidateSegments: List<SegmentWithPath>,
        routeSportType: BSportType = BSportType.UNKNOWN,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): List<MatchedRouteSegment> = withContext(dispatcher) {
        matchSegmentsPure(routePath, candidateSegments, routeSportType)
    }

    /**
     * Pure synchronous matching algorithm for unit tests and headless evaluation.
     */
    fun matchSegmentsPure(
        routePath: List<PathPoint>,
        candidateSegments: List<SegmentWithPath>,
        routeSportType: BSportType = BSportType.UNKNOWN
    ): List<MatchedRouteSegment> {
        if (routePath.size < 2 || candidateSegments.isEmpty()) {
            return emptyList()
        }

        val matched = mutableListOf<MatchedRouteSegment>()

        for (candidate in candidateSegments) {
            val candidatePath = candidate.path
            if (candidatePath.size < 2) continue

            // 1. Sport Compatibility Check
            val candidateSport = candidate.summary.bSportType
            if (candidateSport != BSportType.UNKNOWN &&
                routeSportType != BSportType.UNKNOWN &&
                candidateSport != routeSportType
            ) {
                continue
            }

            // 2. Terminal Point Corridor Proximity Check
            val segStart = candidatePath.first()
            val segEnd = candidatePath.last()

            var bestStartIndex = -1
            var bestStartDist = Double.MAX_VALUE
            var bestEndIndex = -1
            var bestEndDist = Double.MAX_VALUE

            for (i in routePath.indices) {
                val routePt = routePath[i]
                val dStart = GeoUtils.haversineDistanceMeters(
                    segStart.latLng.latitude, segStart.latLng.longitude,
                    routePt.latLng.latitude, routePt.latLng.longitude
                )
                if (dStart < bestStartDist) {
                    bestStartDist = dStart
                    bestStartIndex = i
                }

                val dEnd = GeoUtils.haversineDistanceMeters(
                    segEnd.latLng.latitude, segEnd.latLng.longitude,
                    routePt.latLng.latitude, routePt.latLng.longitude
                )
                if (dEnd < bestEndDist) {
                    bestEndDist = dEnd
                    bestEndIndex = i
                }
            }

            if (bestStartDist > CORRIDOR_TOLERANCE_METERS || bestEndDist > CORRIDOR_TOLERANCE_METERS) {
                continue
            }

            // 3. Forward Progression Check
            if (bestEndIndex <= bestStartIndex) {
                continue
            }

            val routeStartDistMeters = routePath[bestStartIndex].distance
            val routeEndDistMeters = routePath[bestEndIndex].distance

            if (routeEndDistMeters <= routeStartDistMeters) {
                continue
            }

            // 4. Directional Heading / Bearing Alignment
            val segStartBearing = GeoUtils.calculateInitialBearing(
                segStart.latLng.latitude, segStart.latLng.longitude,
                candidatePath[1].latLng.latitude, candidatePath[1].latLng.longitude
            )
            val routeNextIndex = (bestStartIndex + 1).coerceAtMost(routePath.size - 1)
            val routeStartBearing = GeoUtils.calculateInitialBearing(
                routePath[bestStartIndex].latLng.latitude, routePath[bestStartIndex].latLng.longitude,
                routePath[routeNextIndex].latLng.latitude, routePath[routeNextIndex].latLng.longitude
            )

            val startBearingDiff = normalizeBearingDiff(segStartBearing, routeStartBearing)
            if (startBearingDiff > MAX_BEARING_DELTA_DEGREES) {
                continue
            }

            val segEndPrev = candidatePath[candidatePath.size - 2]
            val segEndBearing = GeoUtils.calculateInitialBearing(
                segEndPrev.latLng.latitude, segEndPrev.latLng.longitude,
                segEnd.latLng.latitude, segEnd.latLng.longitude
            )
            val routePrevIndex = (bestEndIndex - 1).coerceAtLeast(0)
            val routeEndBearing = GeoUtils.calculateInitialBearing(
                routePath[routePrevIndex].latLng.latitude, routePath[routePrevIndex].latLng.longitude,
                routePath[bestEndIndex].latLng.latitude, routePath[bestEndIndex].latLng.longitude
            )

            val endBearingDiff = normalizeBearingDiff(segEndBearing, routeEndBearing)
            if (endBearingDiff > MAX_BEARING_DELTA_DEGREES) {
                continue
            }

            // 5. Route Span Distance vs Segment Length Consistency
            val spanDistance = routeEndDistMeters - routeStartDistMeters
            val intrinsicDistance = if (candidate.summary.distance_raw > 0.0) {
                candidate.summary.distance_raw
            } else {
                candidatePath.last().distance
            }

            if (intrinsicDistance > 0.0) {
                val allowedTolerance = max(100.0, intrinsicDistance * 0.25)
                if (abs(spanDistance - intrinsicDistance) > allowedTolerance) {
                    continue
                }
            }

            // 6. Midpoint Corridor Validation for multi-point segments
            if (candidatePath.size >= 3) {
                val midPt = candidatePath[candidatePath.size / 2]
                var minMidDist = Double.MAX_VALUE
                for (k in bestStartIndex..bestEndIndex) {
                    val rPt = routePath[k]
                    val dMid = GeoUtils.haversineDistanceMeters(
                        midPt.latLng.latitude, midPt.latLng.longitude,
                        rPt.latLng.latitude, rPt.latLng.longitude
                    )
                    if (dMid < minMidDist) {
                        minMidDist = dMid
                    }
                }
                if (minMidDist > MIDPOINT_TOLERANCE_METERS) {
                    continue
                }
            }

            matched.add(
                MatchedRouteSegment(
                    segment = candidate,
                    startDistanceMeters = routeStartDistMeters,
                    endDistanceMeters = routeEndDistMeters,
                    startPathIndex = bestStartIndex,
                    endPathIndex = bestEndIndex
                )
            )
        }

        // Return sorted ascending by start distance along route
        return matched.sortedBy { it.startDistanceMeters }
    }

    private fun normalizeBearingDiff(b1: Double, b2: Double): Double {
        val diff = abs(b1 - b2)
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
