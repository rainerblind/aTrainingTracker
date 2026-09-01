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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.heatmaps.HeatmapTileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Defines the available operations for adding data layers to the [ATrainingTrackerMap].
 *
 * This DSL-style interface decouples the "what to draw" from the "how to draw", allowing
 * different screens (e.g., Live Tracking vs. Period Summaries) to share the same map engine.
 */
interface MapContentScope {
    /**
     * Renders a generic mappable path (Track, Route, or Segment) with a specific alpha.
     */
    fun path(
        path: MappablePath,
        alpha: Float = 1.0f,
        onPathClick: (Long) -> Unit = {}
    )

    /**
     * Renders a list of producción tracks, typically used for workout history.
     */
    fun tracks(tracks: List<MapTrack>)

    /**
     * Renders Strava segments with optional highlighting for active live segments.
     */
    fun segments(segments: List<MapSegment>, activeLiveSegmentIds: Set<Long> = emptySet(), onSegmentClick: (Long) -> Unit = {})

    /**
     * Renders planned routes with standardized selected/unselected styling.
     */
    fun routes(routes: List<MapRoute>, onRouteClick: (Long) -> Unit = {})
    
    /**
     * Renders background context paths (e.g., other routes in the area) using tiered alpha.
     * @param sameSportAlpha Opacity for paths matching the current activity type (default 0.5).
     * @param otherSportAlpha Opacity for paths of a different activity type (default 0.2).
     */
    fun contextualPaths(
        paths: List<MappablePath>,
        sameSportAlpha: Float = 0.5f,
        otherSportAlpha: Float = 0.2f
    )

    /**
     * Renders a collection of technical [LocationMarker] objects (Start, End, Apex).
     */
    fun markers(markers: List<LocationMarker>)

    /**
     * Renders a high-frequency live track, typically used during active recording.
     */
    fun liveTrack(path: List<LatLng>)

    /**
     * Renders a density-based heatmap using a Cyan -> Indigo sequential gradient.
     *
     * Implementation: Uses [HeatmapTileProvider] and offloads point densification/thinning
     * to a background thread to prevent UI jank.
     *
     * @param opacity The overall transparency of the heatmap layer.
     * @param radius The blur radius of each point in pixels.
     * @param maxPoints A technical budget to prevent OOM. If exceeded, the data will be thinned.
     */
    fun heatmap(
        allPaths: List<List<LatLng>>, 
        opacity: Double = 0.8, 
        radius: Int? = null,
        densifyInterval: Double? = null,
        maxPoints: Int? = null
    )

    /**
     * Internal implementation of the rendering loop. Called by [ATrainingTrackerMap].
     */
    @Composable
    fun Render(currentZoom: Float)
}

/**
 * Concrete implementation of the map content orchestration logic.
 *
 * This class handles the complex blending schedule that makes the map "zoom-adaptive":
 * - **Low Zoom**: Heatmaps are broad and tracks are muted. Markers are culled for clarity.
 * - **High Zoom**: Heatmaps recede into a density shadow, and tracks become fully opaque.
 */
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

    private data class HeatmapData(
        val allPaths: List<List<LatLng>>, 
        val opacity: Double, 
        val radius: Int?,
        val densifyInterval: Double?,
        val maxPoints: Int?
    )
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
        val steppedZoom = remember(currentZoom) { currentZoom.toInt().toFloat() }

        // ATT-500 Refinement: Calibrated zoom-dependent weights and intervals.
        // At low zoom (zoomed out), low weights (0.15-0.3) prevent the Gaussian blur from bloating sideways,
        // keeping the heatmap line tight, narrow, and crisp over the routes.
        val trackAlpha: Float
        val markerAlphaMult: Float
        val heatmapWeight: Double
        val heatmapStartIntensity: Float
        val heatmapMaxIntensity: Double?

        when {
            steppedZoom < 10 -> {
                // Zoomed far out (country/region): Low weight keeps heatmap narrow and tight
                trackAlpha = 0.4f; markerAlphaMult = 0.0f; heatmapWeight = 0.15; heatmapStartIntensity = 0.2f; heatmapMaxIntensity = null
            }
            steppedZoom < 13 -> {
                // Zoomed moderately out (city level): Crisp, narrow thread over route lines
                trackAlpha = 0.6f; markerAlphaMult = 0.0f; heatmapWeight = 0.3; heatmapStartIntensity = 0.2f; heatmapMaxIntensity = null
            }
            steppedZoom <= 15 -> {
                // Detailed zoom (district level): Clear, light density overlay
                trackAlpha = 0.8f; markerAlphaMult = 0.2f; heatmapWeight = 0.6; heatmapStartIntensity = 0.2f; heatmapMaxIntensity = null
            }
            else -> {
                trackAlpha = 1.0f; markerAlphaMult = 1.0f; heatmapWeight = 0.8; heatmapStartIntensity = 0.2f; heatmapMaxIntensity = null
            }
        }

        var anyHeatmapLoading = false
        val providers = heatmaps.map { data ->
            // Async generation of the heatmap provider to keep UI responsive.
            val provider by produceState<HeatmapTileProvider?>(
                initialValue = null, 
                data.allPaths, 
                data.opacity, 
                steppedZoom, 
                data.radius, 
                data.densifyInterval, 
                data.maxPoints,
                heatmapWeight,
                heatmapStartIntensity,
                heatmapMaxIntensity
            ) {
                // ATT-500 Refinement: Strict radius clamping (10-15px) prevents the heatmap from bloating into a wide band at high zoom levels.
                val effectiveRadius = data.radius ?: (10 + (steppedZoom - 14).coerceAtLeast(0f) * 1.0f).toInt().coerceIn(10, 15)
                val effectiveInterval = data.densifyInterval ?: when {
                    steppedZoom < 10 -> 500.0
                    steppedZoom < 13 -> 250.0
                    steppedZoom < 15 -> 50.0
                    else -> 10.0
                }
                val effectiveMaxPoints = data.maxPoints ?: 15000
                
                value = withContext(Dispatchers.Default) {
                    createHeatmapProvider(
                        data.allPaths,
                        data.opacity,
                        radius = effectiveRadius,
                        densifyInterval = effectiveInterval,
                        maxPoints = effectiveMaxPoints,
                        weight = heatmapWeight,
                        startIntensity = heatmapStartIntensity,
                        maxIntensity = heatmapMaxIntensity
                    )
                }
            }
            if (provider == null) anyHeatmapLoading = true
            provider
        }

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
            // ATT-342 Refinement: Use dynamic trackAlpha for better blending as we zoom in.
            val effectiveAlpha = if (anyHeatmapLoading && heatmaps.isNotEmpty()) {
                (data.alpha * 2.5f).coerceAtMost(0.9f)
            } else {
                data.alpha * trackAlpha
            }

            MappablePathLayer(
                path = data.path,
                alpha = effectiveAlpha,
                currentZoom = currentZoom,
                context = context,
                directionIcons = directionIcons,
                onPathClick = data.onClick
            )
        }

        // 5. Markers
        if (markers.isNotEmpty()) {
            // ATT-342 Refinement: Dynamically adjust member marker visibility
            val adjustedMarkers = markers.map { marker ->
                if (marker.alpha < 1.0f) {
                    marker.copy(alpha = marker.alpha * markerAlphaMult)
                } else marker
            }
            MarkerLayer(adjustedMarkers, primaryColor, context)
        }

        // 6. Live Tracks
        currentTracks.forEach { path ->
            LiveTrackLayer(path)
        }

        // 7. Heatmaps
        providers.forEach { provider ->
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

    override fun heatmap(allPaths: List<List<LatLng>>, opacity: Double, radius: Int?, densifyInterval: Double?, maxPoints: Int?) {
        this.heatmaps.add(HeatmapData(allPaths, opacity, radius, densifyInterval, maxPoints))
    }
}
