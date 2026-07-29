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
    // Flag to ensure we only fit the bounds once per session/focus change.
    // ATT-440 Refinement: We key this by initialBounds so that if they arrive late 
    // (via enrichment), the camera will try to fit them even if it previously gave up.
    var hasFittedInitialBounds by remember(zoomFocus, initialBounds != null) { mutableStateOf(false) }

    LaunchedEffect(tracks, markers, segments, routes, isMapLoaded, hasFittedInitialBounds, initialBounds) {
        if (hasFittedInitialBounds) return@LaunchedEffect

        // --- ATT-352 Refinement: Use persisted bounds if available ---
        if (zoomFocus == MapZoomFocus.EXPLICIT_BOUNDS && initialBounds != null) {
            // Accelerated fitting: Don't wait for isMapLoaded if we have explicit bounds
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(initialBounds, (40 * context.resources.displayMetrics.density).toInt()))
            hasFittedInitialBounds = true
            return@LaunchedEffect
        }
        
        if (!isMapLoaded) return@LaunchedEffect

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
                    // Optimized: Use persisted bounds where available
                    tracks.forEach { track ->
                        if (track.minLat != null && track.maxLat != null && track.minLng != null && track.maxLng != null) {
                            builder.include(LatLng(track.minLat, track.minLng))
                            builder.include(LatLng(track.maxLat, track.maxLng))
                            hasPoints = true
                        } else {
                            // Fallback for legacy
                            track.path.forEach { builder.include(it.latLng); hasPoints = true }
                        }
                    }
                    markers.forEach { marker -> builder.include(marker.position); hasPoints = true }
                    
                    if (zoomFocus == MapZoomFocus.FIT_PRIMARY) {
                        segments.forEach { segment ->
                            if (segment.minLat != null && segment.maxLat != null && segment.minLng != null && segment.maxLng != null) {
                                builder.include(LatLng(segment.minLat, segment.minLng))
                                builder.include(LatLng(segment.maxLat, segment.maxLng))
                                hasPoints = true
                            } else {
                                segment.path.forEach { builder.include(it.latLng); hasPoints = true }
                            }
                        }
                        routes.forEach { route ->
                            if (route.minLat != null && route.maxLat != null && route.minLng != null && route.maxLng != null) {
                                builder.include(LatLng(route.minLat, route.minLng))
                                builder.include(LatLng(route.maxLat, route.maxLng))
                                hasPoints = true
                            } else {
                                route.path.forEach { builder.include(it.latLng); hasPoints = true }
                            }
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
