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
 */

package com.atrainingtracker.trainingtracker.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.LatLng

/**
 * A DSL scope for defining the content of the ATrainingTrackerMap.
 */
interface MapContentScope {
    /**
     * Renders a generic mappable path (Track, Route, or Segment).
     */
    fun path(
        path: MappablePath,
        alpha: Float = 1.0f,
        onPathClick: (Long) -> Unit = {}
    )

    // Specialized helpers for common collections
    fun tracks(tracks: List<MapTrack>)
    fun segments(segments: List<MapSegment>, activeLiveSegmentIds: Set<Long> = emptySet(), onSegmentClick: (Long) -> Unit = {})
    fun routes(routes: List<MapRoute>, onRouteClick: (Long) -> Unit = {})
    
    /**
     * Renders a list of background paths (context) with tiered alpha based on sport type.
     * Contextual paths are ignored by automated bounds fitting.
     */
    fun contextualPaths(
        paths: List<MappablePath>,
        sameSportAlpha: Float = 0.5f,
        otherSportAlpha: Float = 0.2f
    )

    fun markers(markers: List<LocationMarker>)
    fun liveTrack(path: List<LatLng>)
    fun heatmap(allPaths: List<List<LatLng>>, opacity: Double = 0.8)

    @Composable
    fun Render(currentZoom: Float)
}

internal class MapContentScopeImpl(
    private val zoomFocus: MapZoomFocus,
    private val primaryColor: androidx.compose.ui.graphics.Color,
    private val context: android.content.Context,
    private val directionIcons: Triple<com.google.android.gms.maps.model.BitmapDescriptor?, com.google.android.gms.maps.model.BitmapDescriptor?, com.google.android.gms.maps.model.BitmapDescriptor?>,
    private val bSportType: BSportType
) : MapContentScope {

    private data class PathData(val path: MappablePath, val alpha: Float, val onClick: (Long) -> Unit)
    
    // Internal containers with rich metadata
    private val trackData = mutableStateListOf<PathData>()
    private val segmentData = mutableStateListOf<PathData>()
    private val routeData = mutableStateListOf<PathData>()

    // Exposed for Bounds Controller
    val tracks: List<MapTrack> get() = trackData.map { it.path as MapTrack }
    val segments: List<MapSegment> get() = segmentData.map { it.path as MapSegment }
    val routes: List<MapRoute> get() = routeData.map { it.path as MapRoute }
    
    val markers = mutableStateListOf<LocationMarker>()
    val currentTracks = mutableStateListOf<List<LatLng>>()

    private data class HeatmapData(val allPaths: List<List<LatLng>>, val opacity: Double)
    private val heatmaps = mutableStateListOf<HeatmapData>()

    private data class ContextualPathData(val path: MappablePath, val alpha: Float)
    private val contextualPaths = mutableStateListOf<ContextualPathData>()

    fun collect(block: MapContentScope.() -> Unit) {
        trackData.clear()
        segmentData.clear()
        routeData.clear()
        markers.clear()
        currentTracks.clear()
        heatmaps.clear()
        contextualPaths.clear()
        this.apply(block)
    }

    @Composable
    override fun Render(currentZoom: Float) {
        // 1. Contextual Paths (Background)
        contextualPaths.forEach { data ->
            MappablePathLayer(
                path = data.path,
                alpha = data.alpha,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons
            )
        }

        // 2. Segments
        segmentData.forEach { data ->
            MappablePathLayer(
                path = data.path,
                alpha = data.alpha,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons,
                onPathClick = data.onClick
            )
        }

        // 3. Routes
        routeData.forEach { data ->
            val style = LocalMapStyle.current
            val route = data.path as MapRoute
            val highlightRoute = zoomFocus != MapZoomFocus.FOLLOW_ME 
                    || (route.isSelected && route.bSportType == bSportType)

            MappablePathLayer(
                path = route,
                alpha = if (highlightRoute) data.alpha else style.routeUnselectedAlpha,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons,
                onPathClick = data.onClick
            )
        }

        // 4. Tracks
        trackData.forEach { data ->
            MappablePathLayer(
                path = data.path,
                alpha = data.alpha,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons,
                onPathClick = data.onClick
            )
        }

        // 5. Markers
        if (markers.isNotEmpty()) {
            MarkerLayer(markers, primaryColor, context)
        }

        // 6. Live Tracks
        currentTracks.forEach { path ->
            LiveTrackLayer(path)
        }

        // 7. Heatmaps
        heatmaps.forEach { data ->
            val provider = remember(data.allPaths, data.opacity) {
                createHeatmapProvider(data.allPaths, data.opacity)
            }
            provider?.let {
                com.google.maps.android.compose.TileOverlay(tileProvider = it)
            }
        }
    }

    override fun path(path: MappablePath, alpha: Float, onPathClick: (Long) -> Unit) {
        val data = PathData(path, alpha, onPathClick)
        when (path) {
            is MapTrack -> trackData.add(data)
            is MapSegment -> segmentData.add(data)
            is MapRoute -> routeData.add(data)
            else -> {}
        }
    }

    override fun tracks(tracks: List<MapTrack>) {
        tracks.filter { it.isVisible }.forEach { track ->
            val alpha = if (track.type == TrackType.BEST) 1.0f else 0.8f
            this.trackData.add(PathData(track, alpha, track.onClick ?: {}))
        }
    }

    override fun segments(
        segments: List<MapSegment>,
        activeLiveSegmentIds: Set<Long>,
        onSegmentClick: (Long) -> Unit
    ) {
        segments.forEach { segment ->
            this.segmentData.add(PathData(segment, 1.0f, onSegmentClick))
        }
    }

    override fun routes(routes: List<MapRoute>, onRouteClick: (Long) -> Unit) {
        routes.forEach { route ->
            this.routeData.add(PathData(route, 1.0f, onRouteClick))
        }
    }

    override fun contextualPaths(
        paths: List<MappablePath>,
        sameSportAlpha: Float,
        otherSportAlpha: Float
    ) {
        paths.forEach { path ->
            val alpha = if (path.bSportType == bSportType) sameSportAlpha else otherSportAlpha
            contextualPaths.add(ContextualPathData(path, alpha))
        }
    }

    override fun markers(markers: List<LocationMarker>) {
        this.markers.addAll(markers)
    }

    override fun liveTrack(path: List<LatLng>) {
        this.currentTracks.add(path)
    }

    override fun heatmap(allPaths: List<List<LatLng>>, opacity: Double) {
        this.heatmaps.add(HeatmapData(allPaths, opacity))
    }
}
