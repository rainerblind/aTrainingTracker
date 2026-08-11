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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.StateFlow

/**
 * The primary Map Singleton for the application, providing a high-level DSL for rendering athletic data.
 *
 * This component orchestrates the Google Maps engine with standardized behaviors:
 * 1. **Modular Content (DSL)**: Data layers (Tracks, Routes, Heatmaps) are added via a [MapContentScope] block.
 * 2. **Intelligent Focus**: Managed by [MapBoundsController], ensuring the camera snaps to relevant data.
 * 3. **Shared Overlays**: Handles common UI elements like the user location arrow and the interactive scrubber.
 * 4. **Performance**: Offloads heavy processing (like heatmap generation) to background threads.
 *
 * @param zoomFocus Determines the camera's auto-fit strategy (e.g., center on user vs. fit all markers).
 * @param initialBounds Optional spatial boundaries to snap the camera to immediately upon creation.
 * @param currentLocationFlow Reactive stream of the user's GPS coordinates.
 * @param activeScrubPath The path used for the interactive scrubber (e.g., when sliding a finger on a chart).
 * @param content The DSL block defining what additional data layers to render.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun ATrainingTrackerMap(
    // Global State & Behavior
    zoomFocus: MapZoomFocus = MapZoomFocus.TRACK_AND_MARKERS,
    initialBounds: com.google.android.gms.maps.model.LatLngBounds? = null,
    userBearing: Float = 0f,
    userSpeed: Float = 0f,
    bSportType: BSportType = BSportType.UNKNOWN,
    currentLocationFlow: StateFlow<LatLng?>,
    
    // Scrutiny
    selectedDistance: Double? = null,
    activeScrubPath: List<PathPoint>? = null,

    // Visualization Context
    style: MapStyle = MapStyle(),

    // UI & Callbacks
    modifier: Modifier = Modifier,
    onMapClick: ((LatLng) -> Unit)? = null,
    shouldTakeSnapshot: Boolean = false,
    onSnapshotReady: (Bitmap) -> Unit = {},
    onSnapshotError: (() -> Unit)? = null,
    
    // Modular Data Layers (DSL)
    content: MapContentScope.() -> Unit
) {
    val context = LocalContext.current
    val currentLocation by currentLocationFlow.collectAsStateWithLifecycle()
    val primaryColor = MaterialTheme.colorScheme.primary

    // 1. Initial State & Controllers
    var isMapLoaded by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()

    // 2. Icon & Resource Cache
    var locationIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIcons by remember { mutableStateOf<Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>>(Triple(null, null, null)) }
    var scrubIcons by remember { mutableStateOf<Pair<BitmapDescriptor?, BitmapDescriptor?>>(null to null) }

    LaunchedEffect(primaryColor, isMapLoaded) {
        locationIcon = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 42, primaryColor)
        directionIcons = Triple(
            bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 12, primaryColor),
            bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 16, primaryColor),
            bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 22, primaryColor)
        )
    }

    LaunchedEffect(bSportType, isMapLoaded) {
        val iconRes = when (bSportType) {
            BSportType.RUN -> R.drawable.bsport_run
            BSportType.BIKE -> R.drawable.bsport_bike
            else -> R.drawable.ic_cross
        }
        scrubIcons = vectorToBitmap(context, iconRes, 32, false, primaryColor) to 
                     vectorToBitmap(context, iconRes, 32, true, primaryColor)
    }

    // 3. Behavioral Controllers
    // IMPORTANT: Removing zoom from remember key to allow manual zooming without reset
    val scope = remember(content, zoomFocus, primaryColor, context, directionIcons, bSportType) {
        MapContentScopeImpl(zoomFocus, primaryColor, context, directionIcons, bSportType)
    }
    scope.collect(content)

    val currentZoom = cameraPositionState.position.zoom
    MapBoundsController(scope.tracks, scope.markers, scope.segments, scope.routes, zoomFocus, initialBounds, currentLocation, cameraPositionState, isMapLoaded, context)
    
    // Render Preview Check
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Map Singleton (Renders on Device)")
        }
        return
    }

    // 4. THE MAP
    androidx.compose.runtime.CompositionLocalProvider(LocalMapStyle provides style) {
        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng -> onMapClick?.invoke(latLng) },
            properties = MapProperties(mapType = MapType.TERRAIN),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, tiltGesturesEnabled = true),
            onMapLoaded = { isMapLoaded = true }
        ) {
            // ATT-440: Instant Zoom Fit. 
            // We use MapEffect to fit bounds as soon as the map object is available, 
            // without waiting for all tiles to render (onMapLoaded).
            // ATT-469: Added shouldTakeSnapshot to keys to trigger callback immediately.
            MapEffect(initialBounds, isMapLoaded, shouldTakeSnapshot) { map ->
                if (zoomFocus == MapZoomFocus.EXPLICIT_BOUNDS && initialBounds != null && !isMapLoaded) {
                    val padding = (40 * context.resources.displayMetrics.density).toInt()
                    map.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(initialBounds, padding))
                }
                
                if (shouldTakeSnapshot) {
                    map.snapshot { bitmap ->
                        if (bitmap != null) {
                            onSnapshotReady(bitmap)
                        } else {
                            onSnapshotError?.invoke()
                        }
                    }
                }
            }

            // Render the DSL content
            scope.Render(currentZoom)

            // Render Shared Overlays (Scrubber, User Location)
            val scrubPath = activeScrubPath ?: emptyList()
            ScrubberController(selectedDistance, scrubPath, cameraPositionState)
            ScrubMarkerLayer(selectedDistance, scrubPath, scrubIcons.second, scrubIcons.first)

            val filteredBearing = followMeController(zoomFocus, userBearing, userSpeed, currentLocation, cameraPositionState)

            currentLocation?.let { loc ->
                Marker(
                    state = remember(loc) { MarkerState(position = loc) },
                    icon = locationIcon,
                    rotation = if (zoomFocus == MapZoomFocus.FOLLOW_ME) filteredBearing else userBearing,
                    flat = true,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = style.userLocationZIndex
                )
            }
        }
    }
}
