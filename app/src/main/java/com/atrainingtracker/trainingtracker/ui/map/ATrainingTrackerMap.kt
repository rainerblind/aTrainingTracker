package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.MapSegment
import com.atrainingtracker.trainingtracker.segments.SegmentHelper
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
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
    mapViewModel: TrackingMapViewModel,
    currentLocationFlow: StateFlow<LatLng?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLocation by currentLocationFlow.collectAsStateWithLifecycle()
    val primaryColor = MaterialTheme.colorScheme.primary

    // Prevents Render Issues in Android Studio Preview
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Map Singleton (Renders on Device)")
        }
        return
    }

    // THE SINGLETON MAP LOGIC:
    // Instead of GoogleMap {}, we use AndroidView to "plug in" the shared MapView instance.
    AndroidView(
        modifier = modifier,
        factory = {
            // 1. Get the shared MapView from the ViewModel
            val mapView = mapViewModel.sharedMapView

            // 2. Re-parenting: If the view was attached to a previous tab, remove it
            (mapView.parent as? ViewGroup)?.removeView(mapView)

            mapView
        },
        update = { mapView ->
            // 3. Update the content of the singleton map
            mapView.getMapAsync { googleMap ->
                updateGoogleMapContent(
                    googleMap,
                    mapState,
                    currentLocation,
                    context,
                    mapState.segments,
                    primaryColor
                )
            }
        }
    )
}

/**
 * Helper to draw all layers onto the singleton GoogleMap instance.
 */
private fun updateGoogleMapContent(
    googleMap: GoogleMap,
    state: MapState,
    currentLocation: LatLng?,
    context: Context,
    segments: List<MapSegment>,
    primaryColor: Color
) {
    googleMap.clear() // Remove old state before redrawing current frame

    // Disable default UI for a clean "Navigation" look
    googleMap.uiSettings.isZoomControlsEnabled = false
    googleMap.setMapStyle(null) // Reset to default Terrain if needed

    // 1. Draw Current Track
    if (state.currentTrack.isNotEmpty()) {
        googleMap.addPolyline(
            PolylineOptions()
                .addAll(state.currentTrack)
                .color(Color.Blue.toArgb())
                .width(10f)
                .jointType(JointType.ROUND)
        )
    }

    // 2. Draw Segments
    val currentZoom = googleMap.cameraPosition.zoom
    segments.forEach { segment ->

        // Segment Path
        googleMap.addPolyline(
            PolylineOptions()
                .addAll(segment.path)
                .color(StravaOrange.copy(alpha = 0.7f).toArgb())
                .width(12f)
                .jointType(JointType.ROUND)
        )

        // Direction Hints
        if (currentZoom > 14f) {
            val hintSize = if (currentZoom > 17f) 20 else if (currentZoom > 15f) 14 else 10
            val hintIcon = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, hintSize, Color.White)

            segment.path.windowed(2, 20).forEach { pair ->
                val midPoint = LatLng((pair[0].latitude + pair[1].latitude) / 2.0, (pair[0].longitude + pair[1].longitude) / 2.0)
                googleMap.addMarker(
                    MarkerOptions()
                    .position(midPoint)
                    .icon(hintIcon)
                    .rotation(calculateBearing(pair[0], pair[1]).toFloat())
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .alpha(0.4f))
            }
        }

        // Start/Finish Lines
        if (segment.path.size >= 6) {
            googleMap.addPolyline(PolylineOptions().addAll(calculateOrthogonalLine(segment.path[0], segment.path[5])).color(StravaOrange.toArgb()).width(8f))
            val last = segment.path.lastIndex
            googleMap.addPolyline(PolylineOptions().addAll(calculateOrthogonalLine(segment.path[last], segment.path[last-5])).color(StravaOrange.toArgb()).width(8f))
        }
    }

    // 3. Draw Generic Markers
    state.markers.forEach { marker ->
        googleMap.addMarker(MarkerOptions()
            .position(marker.position)
            .icon(BitmapDescriptorFactory.fromResource(marker.iconResId))
            .rotation(marker.rotation)
            .flat(marker.flat)
            .anchor(marker.anchor.x, marker.anchor.y))
    }

    // 4. Current Location Marker & Camera Follow
    currentLocation?.let { loc ->
        val locationIcon = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 42, primaryColor)

        googleMap.addMarker(MarkerOptions()
            .position(loc)
            .icon(locationIcon)
            .rotation(state.bearing)
            .flat(true)
            .anchor(0.5f, 0.5f)
            .zIndex(1.0f))

        if (state.isFollowMeEnabled) {
            val targetZoom = (20f - 0.1f * state.speed).coerceIn(14f, 20f)
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.builder().target(loc).bearing(state.bearing).tilt(70f).zoom(targetZoom).build()
            ), 1000, null)
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
    val bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.draw(Canvas(bm))
    return BitmapDescriptorFactory.fromBitmap(bm)
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
