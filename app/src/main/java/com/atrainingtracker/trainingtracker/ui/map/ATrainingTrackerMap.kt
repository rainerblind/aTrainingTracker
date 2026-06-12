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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.location.Location
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentHelper
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected
import com.atrainingtracker.trainingtracker.ui.theme.RouteColorUnselected
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
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
    // Take a snapshot
    shouldTakeSnapshot: Boolean = false,
    onSnapshotReady: (Bitmap) -> Unit = {}
) {
    val context = LocalContext.current
    val currentLocation by currentLocationFlow.collectAsStateWithLifecycle()
    val primaryColor = MaterialTheme.colorScheme.primary

    // Track map readiness
    var isMapLoaded by remember { mutableStateOf(false) }

    //  Camera State management
    val cameraPositionState = rememberCameraPositionState()

    // Cache the locationIcon bitmap
    var locationIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconSmall by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconMed by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconLarge by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var currentZoomState by remember { mutableFloatStateOf(0f) }

    var scrubIconRight by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var scrubIconLeft by remember { mutableStateOf<BitmapDescriptor?>(null) }

    // Initialize icons inside LaunchedEffect
    LaunchedEffect(primaryColor, isMapLoaded) {
        // This runs after the composition has started, ensuring Maps SDK is likely ready
        locationIcon = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 42, primaryColor)
        directionIconSmall = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 12, primaryColor)
        directionIconMed = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 16, primaryColor)
        directionIconLarge = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 22, primaryColor)
    }

    LaunchedEffect(mapState.bSportType, isMapLoaded) {
        // Map sport type to drawable
        val iconRes = when (mapState.bSportType) {
            BSportType.RUN -> R.drawable.bsport_run
            BSportType.BIKE -> R.drawable.bsport_bike
            else -> R.drawable.ic_cross
        }

        // Pre-calculate both versions
        scrubIconRight = vectorToBitmap(context, iconRes, 32, false, primaryColor)
        scrubIconLeft = vectorToBitmap(context, iconRes, 32, true, primaryColor)
    }

    // Prevents Render Issues in Android Studio Preview
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Map Singleton (Renders on Device)")
        }
        return
    }

    // Automated Bounds Fitting (Optimized for Local Area)
    LaunchedEffect(mapState.tracks, mapState.markers, mapState.segments, isMapLoaded) {
        if (!isMapLoaded) return@LaunchedEffect

        if (mapState.zoomFocus == MapZoomFocus.TRACK_AND_MARKERS || mapState.zoomFocus == MapZoomFocus.LOCAL_SEGMENTS || mapState.zoomFocus == MapZoomFocus.LOCAL_ROUTES) {
            val userPos = currentLocation
            val builder = LatLngBounds.Builder()
            var hasPoints = false

            // Limit for "Local" content
            val maxDistanceMeters = 1000.0  // 1 km

            fun isLocal(target: LatLng): Boolean {
                if (userPos == null) return true // If we don't know where user is, include everything
                val results = FloatArray(1)
                Location.distanceBetween(
                    userPos.latitude, userPos.longitude,
                    target.latitude, target.longitude,
                    results
                )
                return results[0] < maxDistanceMeters
            }

            if (mapState.zoomFocus == MapZoomFocus.TRACK_AND_MARKERS) {
                // Include all track points
                mapState.tracks.forEach { track ->
                    track.path.forEach { builder.include(it.latLng); hasPoints = true }
                }

                // Include all sensor markers
                mapState.markers.forEach { marker -> builder.include(marker.position); hasPoints = true }
            }

            if (mapState.zoomFocus == MapZoomFocus.LOCAL_SEGMENTS) {
                // 2. Include only local segments
                mapState.segments.forEach { segment ->
                    val firstPoint = segment.path.firstOrNull()
                    if (firstPoint != null && isLocal(firstPoint.latLng)) {  // The segment is 'local' iff the first point is local
                        hasPoints = true
                        segment.path.forEach {
                            builder.include(it.latLng);
                        }
                    }
                }
            }

            if (mapState.zoomFocus == MapZoomFocus.LOCAL_ROUTES) {
                // 3. Include only local routes
                mapState.routes.forEach { route ->
                    val firstPoint = route.path.firstOrNull()
                    if (firstPoint != null && isLocal(firstPoint.latLng)) {  // The route is 'local' iff the first point is local
                        hasPoints = true
                        route.path.forEach {
                            builder.include(it.latLng);
                        }
                    }
                }
            }


            if (hasPoints) {
                // Fit to the local cluster of data
                val padding = (40 * context.resources.displayMetrics.density).toInt()
                try {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
                } catch (e: Exception) {
                    // Map not laid out yet or size is 0
                }
            } else if (userPos != null) {
                // If no points are there, just zoom to the user's current city/area
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(userPos, 12f)
                )
            }
        }
    }

    // Follow Me Logic
    LaunchedEffect(currentLocation, mapState.bearing, mapState.speed) {
        if (mapState.zoomFocus == MapZoomFocus.FOLLOW_ME && currentLocation != null) {
            val targetZoom = (20f - 0.1f * mapState.speed).coerceIn(14f, 20f)
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.builder()
                            .target(currentLocation!!)
                            .bearing(mapState.bearing)
                            .zoom(targetZoom)
                            .tilt(70f)
                            .build()
                    ),
                    400,
                )
            } catch (e: Exception) {
                Log.e("ATrainingTrackerMap", "Error updating camera position", e)
            }
        }
    }

    // TODO: add marker here.
    // --- Auto-center Map on Scrubber Icon ---
    LaunchedEffect(selectedDistance) {
        selectedDistance?.let { targetDist ->
            // Find the point associated with the distance
            val activePath = if (mapState.tracks.isNotEmpty()) {
                mapState.tracks.firstOrNull()?.path
            }
            else if (mapState.segments.isNotEmpty()) {
                mapState.segments.firstOrNull()?.path
            }
            else {
                mapState.routes.firstOrNull()?.path
            } ?: emptyList()

            val scrubPoint = activePath!!.find { it.distance >= targetDist }

            scrubPoint?.let { point ->
                /*
                // Option A: Always center (Smooth tracking)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLng(point.latLng),
                    200 // Fast animation for responsiveness
                )
                 */

                // Option B: Smart Margin centering
                val projection = cameraPositionState.projection
                val bounds = projection?.visibleRegion?.latLngBounds

                if (bounds != null) {
                    // Define a 2% margin padding
                    val latPadding = (bounds.northeast.latitude - bounds.southwest.latitude) * 0.2
                    val lngPadding = (bounds.northeast.longitude - bounds.southwest.longitude) * 0.2

                    val safeBounds = LatLngBounds(
                        LatLng(bounds.southwest.latitude + latPadding, bounds.southwest.longitude + lngPadding),
                        LatLng(bounds.northeast.latitude - latPadding, bounds.northeast.longitude - lngPadding)
                    )

                    // If the icon is outside the 20% safety margin, center it
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

    // THE MAP
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapClick = { onMapClick?.invoke() },
        properties = MapProperties(mapType = MapType.TERRAIN),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, tiltGesturesEnabled = true),
        onMapLoaded = { isMapLoaded = true }
    ) {
        // make snapshot
        MapEffect(shouldTakeSnapshot) { map ->
            if (shouldTakeSnapshot) {
                map.snapshot { onSnapshotReady(it!!) }
            }
        }

        // --- Layer 1: Segments ---
        mapState.segments.forEach { segment ->
            SegmentLayer(
                segment = segment,
                isLive = mapState.activeLiveSegmentIds.contains(segment.stravaId),
                isFollowMeEnabled = mapState.zoomFocus == MapZoomFocus.FOLLOW_ME,
                currentZoom = cameraPositionState.position.zoom,
                context = context,
                icons = Triple(directionIconSmall, directionIconMed, directionIconLarge),
                onSegmentClick = onSegmentClick
            )
        }

        // Tracks
        val trackOverlayPattern = listOf(Dot(), Gap(MapVisualization.TRACK_DOT_GAP))
        mapState.tracks.forEach { track ->
            if (track.isVisible) {
                // 1. Solid Base (Bottom)
                Polyline(
                    points = track.path.map { it.latLng },
                    color = track.color,
                    width = MapVisualization.TRACK_WIDTH,
                    zIndex = MapVisualization.TRACK_BASE_Z_INDEX
                )
                // 2. Dotted Overlay (Top)
                Polyline(
                    points = track.path.map { it.latLng },
                    color = track.color,
                    width = MapVisualization.TRACK_WIDTH,
                    zIndex = MapVisualization.TRACK_OVERLAY_Z_INDEX,
                    pattern = trackOverlayPattern
                )
            }
        }

        // Routes
        val routeOverlayPattern = listOf(Dash(MapVisualization.ROUTE_DASH_LENGTH), Gap(MapVisualization.ROUTE_GAP_LENGTH))
        mapState.routes.forEach { route ->
            val alpha = if (route.isSelected || route.bSportType == mapState.bSportType) 1.0f else MapVisualization.ROUTE_UNSELECTED_ALPHA
            val routeColor = if (route.isSelected) RouteColorSelected else RouteColorUnselected

            // 1. Solid Base
            Polyline(
                points = route.path.map { it.latLng },
                color = routeColor.copy(alpha = alpha),
                width = if (route.isSelected) MapVisualization.ROUTE_WIDTH else MapVisualization.ROUTE_UNSELECTED_WIDTH,
                zIndex = if (route.isSelected) MapVisualization.ROUTE_BASE_Z_INDEX else MapVisualization.ROUTE_UNSELECTED_Z_INDEX,
                clickable = true,
                onClick = { onRouteClick(route.id) }
            )
            
            // 2. Dashed Overlay (Only for Selected Route)
            if (route.isSelected) {
                Polyline(
                    points = route.path.map { it.latLng },
                    color = routeColor,
                    width = MapVisualization.ROUTE_WIDTH,
                    zIndex = MapVisualization.ROUTE_OVERLAY_Z_INDEX,
                    pattern = routeOverlayPattern
                )
            }
        }

        // show a marker for the selected distance
        selectedDistance?.let { targetDist ->
            // 1. Identify the active path (either from tracks or segments)
            val activePath = if (mapState.tracks.isNotEmpty()) {
                mapState.tracks.firstOrNull()?.path
            }
            else if (mapState.segments.isNotEmpty()) {
                mapState.segments.firstOrNull()?.path
            }
            else {
                mapState.routes.firstOrNull()?.path
            } ?: emptyList()

            val index = activePath!!.indexOfFirst { it.distance >= targetDist }

            if (index != -1) {
                val point = activePath[index]

                // Determine direction by looking for the next point with a different longitude
                val isWestbound = run {
                    // 1. Look forward for the first point that actually moves East or West
                    val nextSignificantPoint = activePath.drop(index + 1).firstOrNull {
                        it.latLng.longitude != point.latLng.longitude
                    }

                    if (nextSignificantPoint != null) {
                        nextSignificantPoint.latLng.longitude < point.latLng.longitude
                    } else {
                        // 2. If we are at the end of the track, look backward to see which way we were moving
                        val prevSignificantPoint = activePath.take(index).lastOrNull {
                            it.latLng.longitude != point.latLng.longitude
                        }
                        if (prevSignificantPoint != null) {
                            point.latLng.longitude < prevSignificantPoint.latLng.longitude
                        } else {
                            false // Default to East if the entire track is perfectly vertical
                        }
                    }
                }

                Marker(
                    state = MarkerState(position = point.latLng),
                    // Switch icon based on movement direction
                    icon = if (isWestbound) scrubIconLeft else scrubIconRight,
                    // anchor = Offset(0.5f, 0.5f), // Center silhouette on the line
                    zIndex = 5.0f,
                    flat = true
                )
            }
        }


        // --- Layer 2: Sensor Markers ---
        mapState.markers.forEach { markerData ->
            val icon = remember(markerData.iconResId, primaryColor) {
                createSensorMarker(context, markerData.iconResId, primaryColor, Color.White)
            }
            Marker(
                state = MarkerState(position = markerData.position),
                icon = icon,
                title = markerData.title,
                rotation = markerData.rotation,
                flat = markerData.flat,
                anchor = markerData.anchor
            )
        }


        // --- Layer 3: Live Session Track ---
        if (mapState.currentTrack.isNotEmpty()) {
            // Solid Base
            Polyline(
                points = mapState.currentTrack,
                color = Color.Blue,
                width = MapVisualization.TRACK_WIDTH,
                zIndex = MapVisualization.TRACK_BASE_Z_INDEX,
                jointType = JointType.ROUND
            )
            // Dotted Overlay
            Polyline(
                points = mapState.currentTrack,
                color = Color.Blue,
                width = MapVisualization.TRACK_WIDTH,
                zIndex = MapVisualization.TRACK_OVERLAY_Z_INDEX,
                pattern = trackOverlayPattern,
                jointType = JointType.ROUND
            )
        }
        // --- Layer 4: User Location ---
        currentLocation?.let { loc ->
            Marker(
                state = MarkerState(position = loc),
                icon = locationIcon,
                rotation = mapState.bearing,
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                zIndex = MapVisualization.USER_LOCATION_Z_INDEX
            )
        }
    }
}

fun createSensorMarker(
    context: Context,
    @DrawableRes iconResId: Int,
    pinColor: Color,
    iconColor: Color = Color.White
): BitmapDescriptor? {
    val density = context.resources.displayMetrics.density
    val size = (36 * density).toInt() // Total size of the marker
    val iconSize = (18 * density).toInt() // Size of the sensor icon inside

    // 1. Create the Bitmap
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 2. Draw the "Pin" Background (The teardrop shape)
    val pinDrawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin_base) // You need a teardrop XML
    pinDrawable?.let {
        it.setTint(pinColor.toArgb())
        it.setBounds(0, 0, size, size)
        it.draw(canvas)
    }

    // 3. Draw the Sensor Icon on top
    val sensorDrawable = ContextCompat.getDrawable(context, iconResId)
    sensorDrawable?.let {
        it.setTint(iconColor.toArgb())
        // Center the icon in the upper part of the pin
        val left = (size - iconSize) / 2
        val top = (size - iconSize) / 3
        it.setBounds(left, top, left + iconSize, top + iconSize)
        it.draw(canvas)
    }

    return saveBitmapDescriptorFactoryFromBitmap(bitmap)
}

@Composable
private fun SegmentLayer(
    segment: MapSegment,
    isLive: Boolean,
    isFollowMeEnabled: Boolean,
    currentZoom: Float,
    context: Context,
    icons: Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>,
    onSegmentClick: (Long) -> Unit
) {
    val alpha = if (!isFollowMeEnabled || isLive) 1.0f else MapVisualization.SEGMENT_UNSELECTED_ALPHA
    val strokeWidth = MapVisualization.SEGMENT_WIDTH
    val zIndex = MapVisualization.SEGMENT_Z_INDEX
    val segmentColor = StravaOrange.copy(alpha = alpha)

    // 1. Main Segment Path
    Polyline(
        points = segment.path.map { it.latLng },
        color = segmentColor,
        width = strokeWidth,
        zIndex = zIndex,
        clickable = true,
        onClick = { onSegmentClick(segment.stravaId) }
    )

    // 2. Direction Arrows (Performance check: only at high zoom or if Live)
    if (isLive || currentZoom > 15f) {
        val arrowIcon = when {
            currentZoom > 17f -> icons.third
            currentZoom > 15.5f -> icons.second
            else -> icons.first
        }

        // windowed(2, 20) mimics your skip-logic for performance
        segment.path.windowed(2, 20).forEach { pair ->
            val midPos = LatLng(
                (pair[0].latLng.latitude + pair[1].latLng.latitude) / 2.0,
                (pair[0].latLng.longitude + pair[1].latLng.longitude) / 2.0
            )
            Marker(
                state = MarkerState(position = midPos),
                icon = arrowIcon,
                rotation = calculateBearing(pair[0].latLng, pair[1].latLng).toFloat(),
                flat = true,
                anchor = Offset(0.5f, 0.5f),
                alpha = alpha,
                zIndex = zIndex
            )
        }
    }

    // 3. Start / Finish Lines and Labels
    if (segment.path.size >= 6) {
        val startPt = segment.path[0].latLng
        val startNext = segment.path[5].latLng
        val endPt = segment.path.last().latLng
        val endPrev = segment.path[segment.path.size - 6].latLng

        // Orthogonal Lines
        Polyline(points = calculateOrthogonalLine(startPt, startNext), color = segmentColor, width = strokeWidth, zIndex = zIndex)
        Polyline(points = calculateOrthogonalLine(endPt, endPrev), color = segmentColor, width = strokeWidth, zIndex = zIndex)

        // Text Labels (Only at high zoom)
        if (currentZoom > 14f && segment.showStartAndFinishText) {
            val textSize = when {
                currentZoom > 17f -> 22f
                currentZoom > 15f -> 18f
                else -> 14f
            }
            val sportIcon = if (segment.bSportType == BSportType.RUN) R.drawable.bsport_run else R.drawable.bsport_bike

            // Start Label
            Marker(
                state = MarkerState(position = startPt),
                icon = remember(segment.name, textSize) {
                    createTextMarkerBitmap(context, segment.name, "🚩", textSize, sportIcon)
                },
                anchor = Offset(0.5f, -0.2f),
                alpha = alpha
            )

            // Finish Label
            Marker(
                state = MarkerState(position = endPt),
                icon = remember(segment.name, textSize) {
                    createTextMarkerBitmap(context, segment.name, "🏁", textSize, sportIcon)
                },
                anchor = Offset(0.5f, 1.2f),
                alpha = alpha
            )
        }
    }

}


/**
 * Internal helper for icon generation outside of Composable scope
 */
private fun bitmapDescriptorFromVectorInternal(context: Context, resId: Int, sizeDp: Int, tint: Color?): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
    tint?.let { drawable.setTint(it.toArgb()) }
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    drawable.setBounds(0, 0, px, px)
    val bm = createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.draw(Canvas(bm))
    return saveBitmapDescriptorFactoryFromBitmap(bm)
}

fun vectorToBitmap(
    context: Context,
    @DrawableRes resId: Int,
    sizeDp: Int,
    mirror: Boolean = false,
    tint: Color, // only for the default marker
): BitmapDescriptor? {
    if (resId == -1) {
        // Convert Compose Color to HSV to get the Hue
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0]) // hsv[0] is the Hue
    }

    val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        ?: return BitmapDescriptorFactory.defaultMarker()

    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (mirror) {
        // Flip the canvas horizontally around the center
        canvas.scale(-1f, 1f, sizePx / 2f, sizePx / 2f)
    }

    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)

    return saveBitmapDescriptorFactoryFromBitmap(bitmap)
}

/**
 * Generates a Bitmap containing a Sport Icon, Text, and an Emoji.
 * Format: [Icon] [Text] [Emoji]
 */
private fun createTextMarkerBitmap(
    context: Context,
    text: String,
    emoji: String,
    textSizeIn: Float,
    @DrawableRes iconResId: Int// ? = null
): BitmapDescriptor? {
    val density = context.resources.displayMetrics.density
    val scaledTextSize = textSizeIn * density

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = scaledTextSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    // 1. Prepare the Sport Icon
    val iconSize = (scaledTextSize * 1.2f).toInt() // Slightly larger than text
    val iconBitmap = iconResId?.let { res ->
        ContextCompat.getDrawable(context, res)?.let { drawable ->
            val bm = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            drawable.setBounds(0, 0, iconSize, iconSize)
            // drawable.setTint(android.graphics.Color.BLACK) // Match text color
            drawable.draw(canvas)
            bm
        }
    }

    // 2. Calculate Dimensions
    val fullText = "$text $emoji" // Emoji moved to the end
    val textWidth = paint.measureText(fullText)
    val iconPadding = if (iconBitmap != null) 3f * density else 0f
    val totalWidth = (iconSize.toFloat() + iconPadding + textWidth + 4f).toInt()

    val fontMetrics = paint.fontMetrics
    val height = (fontMetrics.bottom - fontMetrics.top + 0.5f).toInt()
    val baseline = -fontMetrics.top

    // 3. Create Result Bitmap
    val resultImage = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultImage)

    // 4. Draw Shadow/Outline for visibility
    val shadowPaint = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = android.graphics.Color.WHITE
    }

    val textX = if (iconBitmap != null) iconSize + iconPadding else 0f

    // Draw Outline
    canvas.drawText(fullText, textX, baseline, shadowPaint)

    // 5. Draw Icon
    iconBitmap?.let {
        // Draw a small white glow behind the icon
        val glowPaint = Paint().apply {
            colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(it, 2f, (height - iconSize) / 2f, glowPaint) // Background glow
        canvas.drawBitmap(it, 0f, (height - iconSize) / 2f, null)
    }

    // 6. Draw Actual Text
    canvas.drawText(fullText, textX, baseline, paint)

    return saveBitmapDescriptorFactoryFromBitmap(resultImage)
}

private fun calculateOrthogonalLine(point: LatLng, nextPoint: LatLng): List<LatLng> {
    val halfLengthMeters = 10 // Total 20m line

    val latDegreeInMeters = SegmentHelper.LatitudeDegreeInMeters(point)
    val lonDegreeInMeters = SegmentHelper.LongitudeDegreeInMeters(point)

    val deltaLatM = (nextPoint.latitude - point.latitude) * latDegreeInMeters
    val deltaLonM = (nextPoint.longitude - point.longitude) * lonDegreeInMeters

    val length = Math.sqrt(deltaLatM * deltaLatM + deltaLonM * deltaLonM)
    if (length == 0.0) return emptyList()

    val scaledDeltaLat = halfLengthMeters * deltaLatM / length
    val scaledDeltaLon = halfLengthMeters * deltaLonM / length

    return listOf(
        LatLng(point.latitude + scaledDeltaLon / latDegreeInMeters,
            point.longitude - scaledDeltaLat / lonDegreeInMeters),
        LatLng(point.latitude - scaledDeltaLon / latDegreeInMeters,
            point.longitude + scaledDeltaLat / lonDegreeInMeters)
    )
}

private fun calculateBearing(start: LatLng, end: LatLng): Double {
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon2 = Math.toRadians(end.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
}

private fun saveBitmapDescriptorFactoryFromBitmap(bm: Bitmap): BitmapDescriptor? {
    try {
        return BitmapDescriptorFactory.fromBitmap(bm)
    } catch (e: Exception) {
        return null
    }
}
