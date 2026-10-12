package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.geo.PolylineProgressTracker
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng

data class ResolvedElevationProfileData(
    val pathPoints: List<PathPoint>,
    val climbs: List<Climb>,
    val markerDistance: Double?,
    val isRouteElevation: Boolean
)

/**
 * Pure domain resolver for tracking cockpit elevation profile data source selection,
 * categorized climbs overlay, and orthogonal map-matched position projection (REQ-UI-355 / ATT-3199).
 */
object TrackingElevationProfileResolver {

    fun resolve(
        activeNavigatedRoute: MapRoute?,
        recordedPathPoints: List<PathPoint>,
        currentLocation: LatLng?
    ): ResolvedElevationProfileData {
        if (activeNavigatedRoute != null &&
            activeNavigatedRoute.path.size >= 2 &&
            activeNavigatedRoute.path.any { it.altitude != 0.0 }
        ) {
            val alongTrackDist = currentLocation?.let { loc ->
                PolylineProgressTracker.projectLocation(activeNavigatedRoute.path, loc)?.alongTrackDistance
            } ?: 0.0
            return ResolvedElevationProfileData(
                pathPoints = activeNavigatedRoute.path,
                climbs = activeNavigatedRoute.climbs,
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
}
