package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.MapSegment
import com.atrainingtracker.trainingtracker.segments.SegmentHelper
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import kotlinx.coroutines.flow.StateFlow


data class LocationMarker(
    val position: LatLng,
    @DrawableRes val iconResId: Int,
    val title: String? = null,
    val rotation: Float = 0f,
    val flat: Boolean = false,
    val anchor: Offset = Offset(0.5f, 0.5f)
)

data class MapState(
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val isFollowMeEnabled: Boolean = true,
    val currentTrack: List<LatLng> = emptyList(),
    val segments: List<MapSegment> = emptyList(),
    val markers: List<LocationMarker> = emptyList()
)

@Composable
fun ATrainingTrackerMap(
    mapState: MapState,
    currentLocationFlow: StateFlow<LatLng?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Collect the location state with lifecycle awareness
    val currentLocation by currentLocationFlow.collectAsStateWithLifecycle()

    // CRITICAL: Initialize Maps SDK to prevent IBitmapDescriptorFactory crash
    var isMapInitialized by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.google.android.gms.maps.MapsInitializer.initialize(
            context,
            com.google.android.gms.maps.MapsInitializer.Renderer.LATEST
        ) {
            isMapInitialized = true // Now it is safe to use BitmapDescriptorFactory
        }
    }

    // Prevents Render Issues/Crashes in Android Studio Preview
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Map Placeholder (Renders on Device)")
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: LatLng(0.0, 0.0),
            16f
        )
    }


    // Dynamic zoom based on speed (m/s)
    //         max_zoom - gain * speed
    val targetZoom = (20f - 0.1f * mapState.speed).coerceIn(14f, 20f)

    // Auto-follow logic
    LaunchedEffect(currentLocation, mapState.bearing, targetZoom) {
        if (mapState.isFollowMeEnabled && currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder()
                        .target(currentLocation!!)
                        .bearing(mapState.bearing)
                        .tilt(70f)
                        .zoom(targetZoom)
                        .build()
                ),
                durationMs = 1000 // Matches our 1s polling interval for smooth movement
            )
        }
    }

    val iconCache = remember(isMapInitialized) { mutableMapOf<Int, BitmapDescriptor>() }

    if (isMapInitialized) {
        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.TERRAIN),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            // 1. Draw Current Track
            if (mapState.currentTrack.isNotEmpty()) {
                Polyline(
                    points = mapState.currentTrack,
                    color = Color.Blue,
                    width = 10f,
                    jointType = JointType.ROUND
                )
            }


            // 2. Draw Segments
            val currentZoom = cameraPositionState.position.zoom
            mapState.segments.forEach { segment ->
                // Segment Color based on Sport Type

                Polyline(
                    points = segment.path,
                    color = StravaOrange.copy(alpha = 0.7f),
                    width = 12f,
                    jointType = JointType.ROUND
                )

                // Only draw direction hints if zoomed in enough (e.g., zoom > 14)
                if (currentZoom > 14f) {
                    // Make segment arrows smaller as we zoom out
                    val hintSize = when {
                        currentZoom > 17f -> 20 // Large
                        currentZoom > 15f -> 14 // Medium
                        else -> 10              // Small
                    }

                    val hintIcon = bitmapDescriptorFromVector(
                        context,
                        R.drawable.ic_navigation_arrow,
                        isMapInitialized,
                        sizeDp = hintSize
                    )

                    segment.path.windowed(size = 2, step = 20).forEach { pair ->
                        val midPoint = LatLng(
                            (pair[0].latitude + pair[1].latitude) / 2.0,
                            (pair[0].longitude + pair[1].longitude) / 2.0
                        )
                        val segmentBearing = calculateBearing(pair[0], pair[1])

                        hintIcon?.let {
                            Marker(
                                state = MarkerState(position = midPoint),
                                icon = it,
                                rotation = segmentBearing.toFloat(),
                                flat = true,
                                anchor = Offset(0.5f, 0.5f),
                                alpha = 0.4f // Make hints slightly transparent
                            )
                        }
                    }
                }

                // Draw Start/Finish lines
                if (segment.path.size >= 6) {
                    val startLine = calculateOrthogonalLine(segment.path[0], segment.path[5])
                    Polyline(points = startLine, color = StravaOrange, width = 8f)

                    val lastIdx = segment.path.lastIndex
                    val finishLine = calculateOrthogonalLine(segment.path[lastIdx], segment.path[lastIdx - 5])
                    Polyline(points = finishLine, color = StravaOrange, width = 8f)
                }
            }

            // 2. Draw Generic Markers from the list
            mapState.markers.forEach { markerData ->
                val descriptor = iconCache.getOrPut(markerData.iconResId) {
                    BitmapDescriptorFactory.fromResource(markerData.iconResId)
                }
                Marker(
                    state = MarkerState(position = markerData.position),
                    icon = descriptor,
                    title = markerData.title,
                    rotation = markerData.rotation,
                    flat = markerData.flat,
                    anchor = markerData.anchor
                )
            }

            // 3. Current Location Marker
            currentLocation?.let {
                val locationIcon = bitmapDescriptorFromVector(
                    context,
                    R.drawable.ic_navigation_arrow, // High-contrast, large arrow
                    isMapInitialized,
                    sizeDp = 42,
                    tint = MaterialTheme.colorScheme.primary
                )

                Marker(
                    state = MarkerState(position = it),
                    icon = locationIcon,
                    rotation = mapState.bearing,
                    flat = true,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 1.0f // Ensure it's always on top of segments
                )
            }
        }
    }
}

@Composable
fun bitmapDescriptorFromVector(
    context: Context,
    vectorResId: Int,
    isInitialized: Boolean,
    sizeDp: Int = 32,
    tint: Color? = null
): BitmapDescriptor? {
    return remember(vectorResId, isInitialized, sizeDp, tint) {
        if (!isInitialized) return@remember null

        val drawable = ContextCompat.getDrawable(context, vectorResId)?.mutate() ?: return@remember null

        // Apply tint if provided
        tint?.let {
            drawable.setTint(it.toArgb())
        }

        // Scale based on device density and requested DP
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        drawable.setBounds(0, 0, px, px)

        val bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        drawable.draw(canvas)

        BitmapDescriptorFactory.fromBitmap(bm)
    }
}

private fun calculateOrthogonalLine(point: LatLng, nextPoint: LatLng): List<LatLng> {
    val halfLengthMeters = 7.5 // Total 15m line

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
