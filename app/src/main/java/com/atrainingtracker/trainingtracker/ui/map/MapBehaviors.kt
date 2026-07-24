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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState

/**
 * Manages automated bounds fitting when tracks, segments, or routes change.
 */
@Composable
fun MapBoundsController(
    tracks: List<MapTrack>,
    markers: List<LocationMarker>,
    segments: List<MapSegment>,
    routes: List<MapRoute>,
    zoomFocus: MapZoomFocus,
    initialBounds: LatLngBounds? = null,
    currentLocation: LatLng?,
    cameraPositionState: CameraPositionState,
    isMapLoaded: Boolean,
    context: Context
) {
    // Flag to ensure we only fit the bounds once per session/focus change
    var hasFittedInitialBounds by remember(zoomFocus) { mutableStateOf(false) }

    LaunchedEffect(tracks, markers, segments, routes, isMapLoaded, hasFittedInitialBounds, initialBounds) {
        if (!isMapLoaded || hasFittedInitialBounds) return@LaunchedEffect

        if (zoomFocus == MapZoomFocus.EXPLICIT_BOUNDS && initialBounds != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(initialBounds, (40 * context.resources.displayMetrics.density).toInt()))
            hasFittedInitialBounds = true
            return@LaunchedEffect
        }

        if (zoomFocus == MapZoomFocus.TRACK_AND_MARKERS || 
            zoomFocus == MapZoomFocus.LOCAL_SEGMENTS || 
            zoomFocus == MapZoomFocus.LOCAL_ROUTES ||
            zoomFocus == MapZoomFocus.FIT_PRIMARY) {
            
            val userPos = currentLocation
            val builder = LatLngBounds.Builder()
            var hasPoints = false
            val maxDistanceMeters = 1000.0

            fun isLocal(target: LatLng): Boolean {
                if (userPos == null) return true
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    userPos.latitude, userPos.longitude,
                    target.latitude, target.longitude,
                    results
                )
                return results[0] < maxDistanceMeters
            }

            when (zoomFocus) {
                MapZoomFocus.TRACK_AND_MARKERS, MapZoomFocus.FIT_PRIMARY -> {
                    tracks.forEach { track ->
                        track.path.forEach { builder.include(it.latLng); hasPoints = true }
                    }
                    markers.forEach { marker -> builder.include(marker.position); hasPoints = true }
                    
                    if (zoomFocus == MapZoomFocus.FIT_PRIMARY) {
                        segments.forEach { segment ->
                            segment.path.forEach { builder.include(it.latLng); hasPoints = true }
                        }
                        routes.forEach { route ->
                            route.path.forEach { builder.include(it.latLng); hasPoints = true }
                        }
                    }
                }
                MapZoomFocus.LOCAL_SEGMENTS -> {
                    segments.forEach { segment ->
                        val firstPoint = segment.path.firstOrNull()
                        if (firstPoint != null && isLocal(firstPoint.latLng)) {
                            hasPoints = true
                            segment.path.forEach { builder.include(it.latLng) }
                        }
                    }
                }
                MapZoomFocus.LOCAL_ROUTES -> {
                    routes.forEach { route ->
                        val firstPoint = route.path.firstOrNull()
                        if (firstPoint != null && isLocal(firstPoint.latLng)) {
                            hasPoints = true
                            route.path.forEach { builder.include(it.latLng) }
                        }
                    }
                }
                else -> {}
            }

            if (hasPoints) {
                val padding = (40 * context.resources.displayMetrics.density).toInt()
                try {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
                    hasFittedInitialBounds = true
                } catch (e: Exception) {
                    // Silently fail if map not laid out
                }
            } else if (userPos != null) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userPos, 12f))
                hasFittedInitialBounds = true
            }
        }
    }
}

/**
 * Manages the "Follow Me" camera behavior with smoothed bearing and tilt.
 * Returns the smoothed bearing for use in the user location marker.
 */
@Composable
fun followMeController(
    zoomFocus: MapZoomFocus,
    bearing: Float,
    speed: Float,
    currentLocation: LatLng?,
    cameraPositionState: CameraPositionState
): Float {
    var filteredBearing by remember { mutableFloatStateOf(bearing) }

    LaunchedEffect(zoomFocus) {
        if (zoomFocus == MapZoomFocus.FOLLOW_ME) {
            filteredBearing = bearing
        }
    }

    LaunchedEffect(currentLocation, bearing, speed) {
        if (zoomFocus == MapZoomFocus.FOLLOW_ME && currentLocation != null) {
            val alpha = 0.15f
            var diff = bearing - filteredBearing
            while (diff < -180f) diff += 360f
            while (diff > 180f) diff -= 360f

            filteredBearing += alpha * diff
            filteredBearing = (filteredBearing + 360f) % 360f

            val targetZoom = (20f - 0.1f * speed).coerceIn(14f, 20f)
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.builder()
                            .target(currentLocation)
                            .bearing(filteredBearing)
                            .zoom(targetZoom)
                            .tilt(70f)
                            .build()
                    ),
                    400
                )
            } catch (e: Exception) {
                Log.e("FollowMeController", "Error updating camera position", e)
            }
        }
    }
    
    return filteredBearing
}

/**
 * Manages auto-centering the map on the distance scrubber with a safety margin.
 */
@Composable
fun ScrubberController(
    selectedDistance: Double?,
    activePath: List<PathPoint>,
    cameraPositionState: CameraPositionState
) {
    LaunchedEffect(selectedDistance, activePath) {
        selectedDistance?.let { targetDist ->
            val scrubPoint = activePath.find { it.distance >= targetDist }

            scrubPoint?.let { point ->
                val projection = cameraPositionState.projection
                val bounds = projection?.visibleRegion?.latLngBounds

                if (bounds != null) {
                    val latPadding = (bounds.northeast.latitude - bounds.southwest.latitude) * 0.2
                    val lngPadding = (bounds.northeast.longitude - bounds.southwest.longitude) * 0.2

                    val safeBounds = LatLngBounds(
                        LatLng(bounds.southwest.latitude + latPadding, bounds.southwest.longitude + lngPadding),
                        LatLng(bounds.northeast.latitude - latPadding, bounds.northeast.longitude - lngPadding)
                    )

                    if (!safeBounds.contains(point.latLng)) {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLng(point.latLng),
                            300
                        )
                    }
                }
            }
        }
    }
}
