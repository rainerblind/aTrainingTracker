package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.geo.PolylineProgressTracker
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.ElevationProfileScope
import com.google.android.gms.maps.model.LatLng
import kotlin.math.max
import kotlin.math.min

data class ResolvedElevationProfileData(
    val pathPoints: List<PathPoint>,
    val climbs: List<Climb>,
    val markerDistance: Double?,
    val isRouteElevation: Boolean
)

/**
 * Pure domain resolver for tracking cockpit elevation profile data source selection,
 * categorized climbs overlay, rolling window scoping with 15% athlete position centering,
 * and orthogonal map-matched position projection (REQ-UI-355 / ATT-3199 / ATT-3317).
 */
object TrackingElevationProfileResolver {

    fun resolve(
        activeNavigatedRoute: MapRoute?,
        recordedPathPoints: List<PathPoint>,
        currentLocation: LatLng?,
        scope: ElevationProfileScope = ElevationProfileScope.FULL_ROUTE,
        lookaheadDistanceMeters: Int = 2000
    ): ResolvedElevationProfileData {
        if (activeNavigatedRoute != null &&
            activeNavigatedRoute.path.size >= 2 &&
            activeNavigatedRoute.path.any { it.altitude != 0.0 }
        ) {
            val alongTrackDist = currentLocation?.let { loc ->
                PolylineProgressTracker.projectLocation(activeNavigatedRoute.path, loc)?.alongTrackDistance
            } ?: 0.0

            if (scope == ElevationProfileScope.FULL_ROUTE) {
                return ResolvedElevationProfileData(
                    pathPoints = activeNavigatedRoute.path,
                    climbs = activeNavigatedRoute.climbs,
                    markerDistance = alongTrackDist,
                    isRouteElevation = true
                )
            }

            // ROLLING_WINDOW mode:
            // Forward lookahead D_forward = lookaheadDistanceMeters
            // Behind distance D_behind = (3.0 / 17.0) * D_forward (centers athlete at exactly 15% of window)
            // Total window span D_window = D_behind + D_forward = (20.0 / 17.0) * D_forward
            val dForward = lookaheadDistanceMeters.toDouble().coerceAtLeast(500.0)
            val dBehind = (3.0 / 17.0) * dForward
            val dWindow = dBehind + dForward
            val dRoute = activeNavigatedRoute.path.last().distance

            val (wStart, wEnd) = when {
                dRoute <= dWindow -> 0.0 to dRoute
                alongTrackDist - dBehind < 0.0 -> 0.0 to min(dWindow, dRoute)
                alongTrackDist + dForward > dRoute -> max(0.0, dRoute - dWindow) to dRoute
                else -> (alongTrackDist - dBehind) to (alongTrackDist + dForward)
            }

            val slicedPath = slicePath(activeNavigatedRoute.path, wStart, wEnd)
            val filteredClimbs = activeNavigatedRoute.climbs.filter { climb ->
                val climbStart = climb.pathPoints.firstOrNull()?.distance ?: 0.0
                val climbEnd = climb.pathPoints.lastOrNull()?.distance ?: (climbStart + climb.distanceMeters)
                climbEnd >= wStart && climbStart <= wEnd
            }

            return ResolvedElevationProfileData(
                pathPoints = slicedPath,
                climbs = filteredClimbs,
                markerDistance = alongTrackDist,
                isRouteElevation = true
            )
        }

        return ResolvedElevationProfileData(
            pathPoints = recordedPathPoints,
            climbs = emptyList(),
            markerDistance = recordedPathPoints.lastOrNull()?.distance,
            isRouteElevation = false
        )
    }

    /**
     * Slices [path] points between [startDist] and [endDist], interpolating exact coordinates and altitude
     * at boundary distances to ensure smooth, non-jagged edge-to-edge profile rendering.
     */
    fun slicePath(path: List<PathPoint>, startDist: Double, endDist: Double): List<PathPoint> {
        if (path.isEmpty()) return emptyList()
        if (startDist <= path.first().distance && endDist >= path.last().distance) return path

        val result = mutableListOf<PathPoint>()

        // 1. Interpolate start point at startDist
        val startIdx = path.indexOfLast { it.distance <= startDist }
        if (startIdx >= 0) {
            val p1 = path[startIdx]
            if (p1.distance == startDist || startIdx == path.size - 1) {
                result.add(p1)
            } else {
                val p2 = path[startIdx + 1]
                val ratio = if (p2.distance > p1.distance) (startDist - p1.distance) / (p2.distance - p1.distance) else 0.0
                val lat = p1.latLng.latitude + ratio * (p2.latLng.latitude - p1.latLng.latitude)
                val lng = p1.latLng.longitude + ratio * (p2.latLng.longitude - p1.latLng.longitude)
                val alt = p1.altitude + ratio * (p2.altitude - p1.altitude)
                val time = p1.timeSec + (ratio * (p2.timeSec - p1.timeSec)).toLong()
                result.add(PathPoint(distance = startDist, latLng = LatLng(lat, lng), altitude = alt, timeSec = time))
            }
        } else {
            result.add(path.first())
        }

        // 2. Add all points strictly inside (startDist, endDist)
        for (pt in path) {
            if (pt.distance > startDist && pt.distance < endDist) {
                result.add(pt)
            }
        }

        // 3. Interpolate end point at endDist
        val endIdx = path.indexOfFirst { it.distance >= endDist }
        if (endIdx > 0) {
            val p2 = path[endIdx]
            if (p2.distance == endDist) {
                if (result.lastOrNull()?.distance != p2.distance) {
                    result.add(p2)
                }
            } else {
                val p1 = path[endIdx - 1]
                val ratio = if (p2.distance > p1.distance) (endDist - p1.distance) / (p2.distance - p1.distance) else 0.0
                val lat = p1.latLng.latitude + ratio * (p2.latLng.latitude - p1.latLng.latitude)
                val lng = p1.latLng.longitude + ratio * (p2.latLng.longitude - p1.latLng.longitude)
                val alt = p1.altitude + ratio * (p2.altitude - p1.altitude)
                val time = p1.timeSec + (ratio * (p2.timeSec - p1.timeSec)).toLong()
                result.add(PathPoint(distance = endDist, latLng = LatLng(lat, lng), altitude = alt, timeSec = time))
            }
        } else if (endIdx == 0) {
            if (result.isEmpty()) result.add(path.first())
        } else {
            if (result.lastOrNull()?.distance != path.last().distance) {
                result.add(path.last())
            }
        }

        return if (result.size >= 2) result else path
    }
}
