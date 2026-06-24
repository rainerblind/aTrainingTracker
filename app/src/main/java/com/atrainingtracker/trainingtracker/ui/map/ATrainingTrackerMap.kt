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
import com.google.android.gms.maps.model.JointType
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.StateFlow

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun ATrainingTrackerMap(
    mapState: MapState,
    currentLocationFlow: StateFlow<LatLng?>,
    selectedDistance: Double? = null,
    modifier: Modifier = Modifier,
    onMapClick: (() -> Unit)? = null,
    onSegmentClick: (Long) -> Unit = {},
    onRouteClick: (Long) -> Unit = {},
    shouldTakeSnapshot: Boolean = false,
    onSnapshotReady: (Bitmap) -> Unit = {}
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

    LaunchedEffect(mapState.bSportType, isMapLoaded) {
        val iconRes = when (mapState.bSportType) {
            BSportType.RUN -> R.drawable.bsport_run
            BSportType.BIKE -> R.drawable.bsport_bike
            else -> R.drawable.ic_cross
        }
        scrubIcons = vectorToBitmap(context, iconRes, 32, false, primaryColor) to 
                     vectorToBitmap(context, iconRes, 32, true, primaryColor)
    }

    // 3. Behavioral Controllers
    MapBoundsController(mapState, currentLocation, cameraPositionState, isMapLoaded, context)
    val filteredBearing = followMeController(mapState, currentLocation, cameraPositionState)
    
    val activePath = remember(mapState) {
        when (mapState.zoomFocus) {
            MapZoomFocus.TRACK_AND_MARKERS -> mapState.tracks.firstOrNull()?.path
            MapZoomFocus.LOCAL_ROUTES -> mapState.routes.find { it.isSelected }?.path ?: mapState.routes.firstOrNull()?.path
            MapZoomFocus.LOCAL_SEGMENTS -> mapState.segments.firstOrNull()?.path
            MapZoomFocus.FOLLOW_ME -> null
        } ?: mapState.tracks.firstOrNull()?.path ?: mapState.routes.firstOrNull()?.path ?: mapState.segments.firstOrNull()?.path ?: emptyList()
    }
    ScrubberController(selectedDistance, activePath, cameraPositionState)

    // Render Preview Check
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Map Singleton (Renders on Device)")
        }
        return
    }

    // 4. THE MAP
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapClick = { onMapClick?.invoke() },
        properties = MapProperties(mapType = MapType.TERRAIN),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, tiltGesturesEnabled = true),
        onMapLoaded = { isMapLoaded = true }
    ) {
        MapEffect(shouldTakeSnapshot) { map ->
            if (shouldTakeSnapshot) {
                map.snapshot { onSnapshotReady(it!!) }
            }
        }

        // Layer 1: Data Layers
        SegmentLayer(
            segments = mapState.segments,
            activeLiveSegmentIds = mapState.activeLiveSegmentIds,
            zoomFocus = mapState.zoomFocus,
            currentZoom = cameraPositionState.position.zoom,
            context = context,
            directionIcons = directionIcons,
            onSegmentClick = onSegmentClick
        )

        TrackLayer(tracks = mapState.tracks)

        RouteLayer(
            routes = mapState.routes,
            zoomFocus = mapState.zoomFocus,
            activeSportType = mapState.bSportType,
            onRouteClick = onRouteClick
        )

        MarkerLayer(markers = mapState.markers, primaryColor = primaryColor, context = context)

        LiveTrackLayer(path = mapState.currentTrack)

        ScrubMarkerLayer(
            selectedDistance = selectedDistance,
            activePath = activePath,
            scrubIconLeft = scrubIcons.second,
            scrubIconRight = scrubIcons.first
        )

        // Layer 2: User Location
        currentLocation?.let { loc ->
            Marker(
                state = remember(loc) { MarkerState(position = loc) },
                icon = locationIcon,
                rotation = if (mapState.zoomFocus == MapZoomFocus.FOLLOW_ME) filteredBearing else mapState.bearing,
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                zIndex = MapVisualization.USER_LOCATION_Z_INDEX
            )
        }
    }
}
