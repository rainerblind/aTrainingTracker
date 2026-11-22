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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.tracking.ElevationProfileScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private class SegmentIndexHolder(var index: Int? = null)

/**
 * Isolated recomposition scope for the live tracking elevation profile (`REQ-UI-2793`).
 *
 * Subscribes to [currentLocationFlow] and the distinct active navigated route from [mapStateFlow]
 * strictly inside this section so 1-Hz GPS updates never trigger recomposition of the parent
 * `SensorGridScreen` or sensor tiles.
 */
@Composable
internal fun TrackingElevationProfileSection(
    pathPoints: List<PathPoint>,
    scope: ElevationProfileScope,
    lookaheadMeters: Int,
    mapStateFlow: StateFlow<TrackingMapState>?,
    fallbackMapRoutes: List<MapRoute>,
    currentLocationFlow: StateFlow<LatLng?>,
    modifier: Modifier = Modifier
) {
    val currentLocation by currentLocationFlow.collectAsState()
    val activeNavigatedRoute = if (mapStateFlow != null) {
        val activeRouteFlow = remember(mapStateFlow) {
            mapStateFlow
                .map { mapState -> mapState.mapRoutes.firstOrNull { it.isActiveNavigation } }
                .distinctUntilChanged()
        }
        val initialRoute = remember(mapStateFlow) {
            mapStateFlow.value.mapRoutes.firstOrNull { it.isActiveNavigation }
        }
        val collected by activeRouteFlow.collectAsState(initial = initialRoute)
        collected
    } else {
        fallbackMapRoutes.firstOrNull { it.isActiveNavigation }
    }

    val segmentIndexHolder = remember(activeNavigatedRoute?.id) { SegmentIndexHolder() }
    val resolvedProfile = remember(
        activeNavigatedRoute,
        pathPoints,
        currentLocation,
        scope,
        lookaheadMeters
    ) {
        val res = TrackingElevationProfileResolver.resolve(
            activeNavigatedRoute = activeNavigatedRoute,
            recordedPathPoints = pathPoints,
            currentLocation = currentLocation,
            scope = scope,
            lookaheadDistanceMeters = lookaheadMeters,
            lastSegmentIndex = segmentIndexHolder.index
        )
        segmentIndexHolder.index = res.segmentIndex
        res
    }

    Surface(modifier = modifier) {
        ElevationProfile(
            pathPoints = resolvedProfile.pathPoints,
            currentDistance = resolvedProfile.markerDistance,
            climbs = resolvedProfile.climbs,
            showEmptyState = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
