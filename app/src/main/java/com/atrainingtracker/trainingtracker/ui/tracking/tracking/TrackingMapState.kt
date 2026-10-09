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

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.google.android.gms.maps.model.LatLng

/**
 * Immutable state model representing all parameters required to render the live tracking map.
 * Decoupled from high-frequency sensor telemetry to prevent recomposition churn (REQ-UI-326 / ATT-2944).
 */
@Immutable
data class TrackingMapState(
    val showMap: Boolean = false,
    val zoomFocus: MapZoomFocus = MapZoomFocus.TRACK_AND_MARKERS,
    val userBearing: Float = 0f,
    val userSpeed: Float = 0f,
    val bSportType: BSportType = BSportType.UNKNOWN,
    val currentTrack: List<LatLng> = emptyList(),
    val mapTracks: List<MapTrack> = emptyList(),
    val mapSegments: List<MapSegment> = emptyList(),
    val activeLiveSegmentIds: Set<Long> = emptySet(),
    val mapRoutes: List<MapRoute> = emptyList(),
    val mapMarkers: List<LocationMarker> = emptyList()
)
