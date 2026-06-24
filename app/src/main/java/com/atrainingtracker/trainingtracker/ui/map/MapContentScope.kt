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

import androidx.compose.runtime.Composable
import com.google.android.gms.maps.model.LatLng

/**
 * A DSL scope for defining the content of the ATrainingTrackerMap.
 */
interface MapContentScope {
    fun tracks(tracks: List<MapTrack>)
    fun segments(segments: List<MapSegment>, activeLiveSegmentIds: Set<Long> = emptySet(), onSegmentClick: (Long) -> Unit = {})
    fun routes(routes: List<MapRoute>, onRouteClick: (Long) -> Unit = {})
    fun markers(markers: List<LocationMarker>)
    fun liveTrack(path: List<LatLng>)
}

internal class MapContentScopeImpl(
    private val zoomFocus: MapZoomFocus,
    private val currentZoom: Float,
    private val primaryColor: androidx.compose.ui.graphics.Color,
    private val context: android.content.Context,
    private val directionIcons: Triple<com.google.android.gms.maps.model.BitmapDescriptor?, com.google.android.gms.maps.model.BitmapDescriptor?, com.google.android.gms.maps.model.BitmapDescriptor?>,
    private val bSportType: com.atrainingtracker.banalservice.BSportType
) : MapContentScope {

    private val composables = mutableListOf<@Composable () -> Unit>()
    
    // Collected data for Bounds fitting
    val tracks = mutableListOf<MapTrack>()
    val segments = mutableListOf<MapSegment>()
    val routes = mutableListOf<MapRoute>()
    val markers = mutableListOf<LocationMarker>()
    val currentTracks = mutableListOf<List<LatLng>>()

    fun collect(block: MapContentScope.() -> Unit) {
        composables.clear()
        tracks.clear()
        segments.clear()
        routes.clear()
        markers.clear()
        currentTracks.clear()
        this.apply(block)
    }

    @Composable
    fun Render() {
        composables.forEach { it() }
    }

    override fun tracks(tracks: List<MapTrack>) {
        this.tracks.addAll(tracks)
        composables.add { TrackLayer(tracks) }
    }

    override fun segments(
        segments: List<MapSegment>,
        activeLiveSegmentIds: Set<Long>,
        onSegmentClick: (Long) -> Unit
    ) {
        this.segments.addAll(segments)
        composables.add {
            SegmentLayer(
                segments = segments,
                activeLiveSegmentIds = activeLiveSegmentIds,
                zoomFocus = zoomFocus,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons,
                onSegmentClick = onSegmentClick
            )
        }
    }

    override fun routes(routes: List<MapRoute>, onRouteClick: (Long) -> Unit) {
        this.routes.addAll(routes)
        composables.add {
            RouteLayer(
                routes = routes,
                zoomFocus = zoomFocus,
                activeSportType = bSportType,
                onRouteClick = onRouteClick
            )
        }
    }

    override fun markers(markers: List<LocationMarker>) {
        this.markers.addAll(markers)
        composables.add { MarkerLayer(markers, primaryColor, context) }
    }

    override fun liveTrack(path: List<LatLng>) {
        this.currentTracks.add(path)
        composables.add { LiveTrackLayer(path) }
    }
}
