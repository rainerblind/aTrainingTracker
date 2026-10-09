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
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
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
 * Encapsulates a candidate route that spatially and directionally contains a target segment (REQ-UI-305, ATT-2585).
 *
 * @property route The containing route with metadata and polyline.
 * @property startDistanceMeters The accumulated route distance (in meters) where the segment begins.
 * @property endDistanceMeters The accumulated route distance (in meters) where the segment ends.
 */
@Immutable
data class SegmentMatchedRoute(
    val route: RouteWithPath,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double
)

/**
 * Geodesic spatial corridor and directional traversal engine that correlates saved and starred
 * Strava/local segments along planned routes using orthogonal polyline projection (REQ-UI-302, REQ-UI-317, ATT-2862).
 */
object RouteSegmentMatcher {

    /** Maximum allowed geodesic distance in meters from segment terminal point to route polyline. */
    const val CORRIDOR_TOLERANCE_METERS = 35.0

    /** Maximum allowed angular difference in degrees between segment traversal and route heading. */
    const val MAX_BEARING_DELTA_DEGREES = 45.0

    /** Maximum allowed geodesic distance in meters from segment midpoint to route polyline. */
    const val MIDPOINT_TOLERANCE_METERS = 60.0

    /** Internal representation of a coordinate orthogonally projected onto a route polyline segment. */
    private data class PathProjection(
        val distanceAlongRouteMeters: Double,
        val crossTrackDistanceMeters: Double,
        val segmentIndex: Int,
        val t: Double,
        val segmentBearing: Double
    )

    /** Candidate forward traversal interval matching a segment along the route. */
    private data class CandidateInterval(
        val startProj: PathProjection,
        val endProj: PathProjection,
        val totalDeviation: Double
    )

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

            val segStart = candidatePath.first()
            val segEnd = candidatePath.last()

            // 2. Orthogonal Polyline Projection Candidates for Start and End Terminals
            val startCandidates = findCandidateProjections(segStart.latLng, routePath, CORRIDOR_TOLERANCE_METERS)
            if (startCandidates.isEmpty()) continue

            val endCandidates = findCandidateProjections(segEnd.latLng, routePath, CORRIDOR_TOLERANCE_METERS)
            if (endCandidates.isEmpty()) continue

            // Compute intrinsic segment bearings
            val segStartBearing = GeoUtils.calculateInitialBearing(
                segStart.latLng.latitude, segStart.latLng.longitude,
                candidatePath[1].latLng.latitude, candidatePath[1].latLng.longitude
            )
            val segEndPrev = candidatePath[candidatePath.size - 2]
            val segEndBearing = GeoUtils.calculateInitialBearing(
                segEndPrev.latLng.latitude, segEndPrev.latLng.longitude,
                segEnd.latLng.latitude, segEnd.latLng.longitude
            )

            val intrinsicDistance = if (candidate.summary.distance_raw > 0.0) {
                candidate.summary.distance_raw
            } else {
                candidatePath.last().distance
            }
            val allowedSpanTolerance = if (intrinsicDistance > 0.0) {
                max(100.0, intrinsicDistance * 0.25)
            } else {
                Double.MAX_VALUE
            }

            // 3. Find Best Forward-Progressing Interval (Supports Circuits, Loops & Out-and-Back Routes)
            var bestInterval: CandidateInterval? = null

            for (sProj in startCandidates) {
                // Directional Heading Alignment at start
                val startBearingDiff = normalizeBearingDiff(segStartBearing, sProj.segmentBearing)
                if (startBearingDiff > MAX_BEARING_DELTA_DEGREES) continue

                for (eProj in endCandidates) {
                    // Forward progression along route distance
                    if (eProj.distanceAlongRouteMeters <= sProj.distanceAlongRouteMeters) continue

                    // Directional Heading Alignment at end
                    val endBearingDiff = normalizeBearingDiff(segEndBearing, eProj.segmentBearing)
                    if (endBearingDiff > MAX_BEARING_DELTA_DEGREES) continue

                    // Span Distance vs Segment Length Consistency
                    val spanDistance = eProj.distanceAlongRouteMeters - sProj.distanceAlongRouteMeters
                    if (intrinsicDistance > 0.0 && abs(spanDistance - intrinsicDistance) > allowedSpanTolerance) {
                        continue
                    }

                    // Midpoint Corridor Validation for multi-point segments
                    if (candidatePath.size >= 3) {
                        val midPt = candidatePath[candidatePath.size / 2]
                        var minMidDist = Double.MAX_VALUE
                        val searchStartSeg = sProj.segmentIndex
                        val searchEndSeg = (eProj.segmentIndex + 1).coerceAtMost(routePath.size - 1)
                        for (k in searchStartSeg until searchEndSeg) {
                            val midProj = projectPointOntoSegment(midPt.latLng, routePath[k], routePath[k + 1], k)
                            if (midProj.crossTrackDistanceMeters < minMidDist) {
                                minMidDist = midProj.crossTrackDistanceMeters
                            }
                        }
                        if (minMidDist > MIDPOINT_TOLERANCE_METERS) {
                            continue
                        }
                    }

                    val deviation = sProj.crossTrackDistanceMeters + eProj.crossTrackDistanceMeters
                    if (bestInterval == null || deviation < bestInterval.totalDeviation) {
                        bestInterval = CandidateInterval(sProj, eProj, deviation)
                    }
                }
            }

            if (bestInterval != null) {
                val s = bestInterval.startProj
                val e = bestInterval.endProj
                val startPathIdx = if (s.t >= 0.5) {
                    (s.segmentIndex + 1).coerceAtMost(routePath.size - 1)
                } else {
                    s.segmentIndex
                }
                val endPathIdx = if (e.t >= 0.5) {
                    (e.segmentIndex + 1).coerceAtMost(routePath.size - 1)
                } else {
                    e.segmentIndex
                }

                matched.add(
                    MatchedRouteSegment(
                        segment = candidate,
                        startDistanceMeters = s.distanceAlongRouteMeters,
                        endDistanceMeters = e.distanceAlongRouteMeters,
                        startPathIndex = startPathIdx,
                        endPathIndex = endPathIdx
                    )
                )
            }
        }

        // Return sorted ascending by start distance along route
        return matched.sortedBy { it.startDistanceMeters }
    }

    /**
     * Projects a coordinate onto the entire route polyline and returns candidate local-minimum
     * projections within [maxTolerance] meters.
     */
    private fun findCandidateProjections(
        point: LatLng,
        routePath: List<PathPoint>,
        maxTolerance: Double
    ): List<PathProjection> {
        val allProjections = mutableListOf<PathProjection>()
        for (i in 0 until routePath.size - 1) {
            val proj = projectPointOntoSegment(point, routePath[i], routePath[i + 1], i)
            if (proj.crossTrackDistanceMeters <= maxTolerance) {
                allProjections.add(proj)
            }
        }
        if (allProjections.isEmpty()) return emptyList()

        // Cluster contiguous segment indices and pick the local minimum within each cluster
        val clusters = mutableListOf<PathProjection>()
        var currentBest = allProjections.first()

        for (k in 1 until allProjections.size) {
            val proj = allProjections[k]
            if (proj.segmentIndex == allProjections[k - 1].segmentIndex + 1) {
                if (proj.crossTrackDistanceMeters < currentBest.crossTrackDistanceMeters) {
                    currentBest = proj
                }
            } else {
                clusters.add(currentBest)
                currentBest = proj
            }
        }
        clusters.add(currentBest)
        return clusters
    }

    /**
     * Projects a coordinate orthogonally onto an individual route polyline segment between [p1] and [p2].
     */
    private fun projectPointOntoSegment(
        point: LatLng,
        p1: PathPoint,
        p2: PathPoint,
        segmentIndex: Int
    ): PathProjection {
        val aLat = p1.latLng.latitude
        val aLng = p1.latLng.longitude
        val bLat = p2.latLng.latitude
        val bLng = p2.latLng.longitude

        val midLatRad = Math.toRadians((aLat + bLat) / 2.0)
        val cosMid = cos(midLatRad)

        val dx = (bLng - aLng) * cosMid
        val dy = bLat - aLat
        val segLenSq = dx * dx + dy * dy

        val t: Double = if (segLenSq < 1e-14) {
            0.0
        } else {
            val wx = (point.longitude - aLng) * cosMid
            val wy = point.latitude - aLat
            val proj = (wx * dx + wy * dy) / segLenSq
            proj.coerceIn(0.0, 1.0)
        }

        val qLat = aLat + t * (bLat - aLat)
        val qLng = aLng + t * (bLng - aLng)

        val dist = GeoUtils.haversineDistanceMeters(point.latitude, point.longitude, qLat, qLng)
        val segDist = (p2.distance - p1.distance).coerceAtLeast(0.0)
        val distAlong = p1.distance + t * segDist
        val bearing = GeoUtils.calculateInitialBearing(aLat, aLng, bLat, bLng)

        return PathProjection(
            distanceAlongRouteMeters = distAlong,
            crossTrackDistanceMeters = dist,
            segmentIndex = segmentIndex,
            t = t,
            segmentBearing = bearing
        )
    }

    private fun normalizeBearingDiff(b1: Double, b2: Double): Double {
        val diff = abs(b1 - b2)
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /**
     * Identifies which routes from [candidateRoutes] contain the target [segment] in the forward
     * traversal direction within spatial and directional tolerances (REQ-UI-305, ATT-2585).
     *
     * Executes asynchronously on the specified [dispatcher] (default: [Dispatchers.Default]).
     */
    suspend fun findRoutesContainingSegment(
        segment: SegmentWithPath,
        candidateRoutes: List<RouteWithPath>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): List<SegmentMatchedRoute> = withContext(dispatcher) {
        findRoutesContainingSegmentPure(segment, candidateRoutes)
    }

    /**
     * Pure functional evaluation of candidate routes containing the target segment.
     */
    fun findRoutesContainingSegmentPure(
        segment: SegmentWithPath,
        candidateRoutes: List<RouteWithPath>
    ): List<SegmentMatchedRoute> {
        if (segment.path.size < 2 || candidateRoutes.isEmpty()) return emptyList()

        val matchedRoutes = mutableListOf<SegmentMatchedRoute>()

        for (candidateRoute in candidateRoutes) {
            val matches = matchSegmentsPure(
                routePath = candidateRoute.path,
                candidateSegments = listOf(segment),
                routeSportType = candidateRoute.summary.bSportType
            )
            if (matches.isNotEmpty()) {
                val match = matches.first()
                matchedRoutes.add(
                    SegmentMatchedRoute(
                        route = candidateRoute,
                        startDistanceMeters = match.startDistanceMeters,
                        endDistanceMeters = match.endDistanceMeters
                    )
                )
            }
        }

        return matchedRoutes
    }
}
