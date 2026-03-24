package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
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

data class MapSegment(
    val id: Long,
    val path: List<LatLng>,
)

data class MapState(
    val showMap: Boolean = false,
    val currentLocation: LatLng? = null,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val isFollowMeEnabled: Boolean = true,
    val mainTrack: List<LatLng> = emptyList(),
    val segments: List<MapSegment> = emptyList(),
    val showSegments: Boolean = true
)

@Composable
fun ATrainingTrackerMap(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState()

    val context = LocalContext.current
    val arrowIcon = bitmapDescriptorFromVector(context, R.drawable.ic_navigation_arrow)
    val currentLocationIcon = BitmapDescriptorFactory.fromResource(R.drawable.arrowhead)


    // Dynamic zoom based on speed (m/s)
    val targetZoom = when {
        mapState.speed > 8.0f -> 14f // Fast (Cycling)
        mapState.speed > 3.0f -> 16f // Running
        else -> 18f                 // Walking
    }

    // Auto-follow logic
    LaunchedEffect(mapState.currentLocation, mapState.bearing, targetZoom) {
        if (mapState.isFollowMeEnabled && mapState.currentLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.builder()
                        .target(mapState.currentLocation)
                        .bearing(mapState.bearing)
                        .zoom(targetZoom)
                        .build()
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.TERRAIN),
        uiSettings = MapUiSettings(zoomControlsEnabled = false)
    ) {
        // 1. Draw Main Track
        if (mapState.mainTrack.isNotEmpty()) {
            Polyline(
                points = mapState.mainTrack,
                color = Color.Blue,
                width = 10f,
                jointType = JointType.ROUND
            )
        }


        // 2. Draw Segments with Start/Finish Orthogonal Lines and Direction Markers
        if (mapState.showSegments) {
            mapState.segments.forEach { segment ->
                // Draw the segment path itself (Strava Orange)
                Polyline(
                    points = segment.path,
                    color = Color(0xFFFC4C02),
                    width = 12f,
                    jointType = JointType.ROUND
                )

                if (segment.path.size >= 2) {
                    // Draw Start Orthogonal Line
                    val startLine = calculateOrthogonalLine(segment.path[0], segment.path[1])
                    Polyline(points = startLine, color = Color(0xFFFC4C02), width = 8f)

                    // Draw Finish Orthogonal Line
                    val lastIdx = segment.path.lastIndex
                    val finishLine = calculateOrthogonalLine(segment.path[lastIdx], segment.path[lastIdx - 1])
                    Polyline(points = finishLine, color = Color(0xFFFC4C02), width = 8f)
                }

                // Draw Direction Markers (Arrows) every 20 points
                segment.path.windowed(size = 2, step = 20).forEach { pair ->
                    val start = pair[0]
                    val end = pair[1]
                    val midPoint = LatLng(
                        (start.latitude + end.latitude) / 2.0,
                        (start.longitude + end.longitude) / 2.0
                    )
                    val segmentBearing = calculateBearing(start, end)

                    Marker(
                        state = MarkerState(position = midPoint),
                        icon = arrowIcon,
                        rotation = segmentBearing.toFloat(),
                        flat = true,
                        anchor = Offset(0.5f, 0.5f)
                    )
                }
            }
        }

        // 3. Current Location Marker
        mapState.currentLocation?.let {
            Marker(
                state = MarkerState(position = it),
                icon = currentLocationIcon,
                rotation = mapState.bearing,
                flat = true,
                anchor = Offset(0.5f, 0.5f)
            )
        }
    }
}

@Composable
fun bitmapDescriptorFromVector(
    context: Context,
    vectorResId: Int
): BitmapDescriptor? {
    return remember(vectorResId) {
        val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return@remember null
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val bm = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        drawable.draw(canvas)
        BitmapDescriptorFactory.fromBitmap(bm)
    }
}

private fun calculateOrthogonalLine(point: LatLng, nextPoint: LatLng): List<LatLng> {
    val halfLengthMeters = 7.5 // Total 15m line

    val latDegreeInMeters = 111132.0
    val lonDegreeInMeters = 111132.0 * Math.cos(Math.toRadians(point.latitude))

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


@Preview(showBackground = true)
@Composable
fun ATrainingTrackerMapPreview() {
    val dummyState = MapState(
        showMap = true,
        currentLocation = LatLng(48.1351, 11.5820), // Munich
        speed = 5.0f,
        mainTrack = listOf(
            LatLng(48.1351, 11.5820),
            LatLng(48.1360, 11.5830)
        )
    )

    // This will show a placeholder in the IDE, but verify layout
    ATrainingTrackerMap(
        mapState = dummyState,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}
