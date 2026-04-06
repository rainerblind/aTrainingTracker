package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.ViewGroup
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
import com.atrainingtracker.trainingtracker.activities.SegmentDetailsActivity
import com.atrainingtracker.trainingtracker.segments.MapSegment
import com.atrainingtracker.trainingtracker.segments.SegmentHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapViewModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
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

    // Cache the locationIcon bitmap
    var locationIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconSmall by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconMed by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var directionIconLarge by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var currentZoomState by remember { mutableFloatStateOf(0f) }

    // Initialize icons inside LaunchedEffect (Safe from IBitmapDescriptorFactory error)
    LaunchedEffect(primaryColor) {
        // This runs after the composition has started, ensuring Maps SDK is likely ready
        locationIcon = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 42, primaryColor)
        directionIconSmall = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 10, Color.White)
        directionIconMed = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 14, Color.White)
        directionIconLarge = bitmapDescriptorFromVectorInternal(context, R.drawable.ic_navigation_arrow, 20, Color.White)
    }

    // Prevents Render Issues in Android Studio Preview
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
            Text("Map Singleton (Renders on Device)")
        }
        return
    }

    LaunchedEffect(currentLocation, mapState.bearing, mapState.speed) {

        mapViewModel.sharedMapView.getMapAsync { googleMap ->
            googleMap.setOnCameraMoveListener {
                currentZoomState = googleMap.cameraPosition.zoom
            }

            if (currentLocation != null) {
                // SNAP to position if it's the very first time
                if (!mapViewModel.isInitialPositionSet) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 16f))
                    mapViewModel.isInitialPositionSet = true
                }

                if (mapState.isFollowMeEnabled && currentLocation != null) {
                    val targetZoom = (20f - 0.1f * mapState.speed).coerceIn(14f, 20f)

                    googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder()
                                .target(currentLocation!!)
                                .bearing(mapState.bearing)
                                .zoom(targetZoom)
                                .tilt(70f)
                                .build()
                        ),
                        400, // Reduced duration for snappier response
                        null
                    )
                }
            }
        }
    }

    // THE SINGLETON MAP LOGIC:
    // Instead of GoogleMap {}, we use AndroidView to "plug in" the shared MapView instance.
    AndroidView(
        modifier = modifier,
        factory = {
            val mapView = mapViewModel.sharedMapView
            (mapView.parent as? ViewGroup)?.removeView(mapView)
            mapView
        },
        update = { mapView ->
            // 3. Only draw if the icons are actually ready
            val currentLocIcon = locationIcon ?: return@AndroidView

            val zoomInt = currentZoomState.toInt()

            mapView.getMapAsync { googleMap ->

                val currentDataHash = mapState.segments.hashCode() +
                        mapState.markers.hashCode() +
                        zoomInt

                if (mapViewModel.staticDataHash != currentDataHash) {
                    // MANUALLY REMOVE EVERYTHING
                    mapViewModel.activeSegmentMarkers.forEach { it.remove() }
                    mapViewModel.activeSegmentMarkers.clear()

                    mapViewModel.activeSegmentPolylines.forEach { it.remove() }
                    mapViewModel.activeSegmentPolylines.clear()

                    // still call googleMap.clear()
                    googleMap.clear()                        // TODO: Unfortunately, this clear does not always remove all markers...

                    mapViewModel.userMarker = null
                    mapViewModel.trackPolyline = null

                    drawSegments(
                        googleMap,
                        mapState.segments,
                        context,
                        mapViewModel.activeSegmentMarkers,   // Pass list to store new markers
                        mapViewModel.activeSegmentPolylines,
                        directionIconSmall,
                        directionIconMed,
                        directionIconLarge
                    )

                    mapState.markers.forEach { markerData ->
                        googleMap.addMarker(MarkerOptions()
                            .position(markerData.position)
                            .icon(BitmapDescriptorFactory.fromResource(markerData.iconResId))
                            .rotation(markerData.rotation)
                            .flat(markerData.flat)
                            .anchor(markerData.anchor.x, markerData.anchor.y))
                    }
                    mapViewModel.staticDataHash = currentDataHash
                }

                // --- LAYER 2: SEMI-DYNAMIC (The Track) ---
                if (mapState.currentTrack.isNotEmpty()) {
                    val trackLine = mapViewModel.trackPolyline
                    if (trackLine == null) {
                        mapViewModel.trackPolyline = googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(mapState.currentTrack)
                                .color(Color.Blue.toArgb())
                                .width(10f)
                                .jointType(JointType.ROUND)
                        )
                    } else {
                        trackLine.points = mapState.currentTrack
                    }
                }

                // --- LAYER 3: DYNAMIC (User Location) ---
                currentLocation?.let { loc ->
                    val marker = mapViewModel.userMarker
                    if (marker == null) {
                        mapViewModel.userMarker = googleMap.addMarker(
                            MarkerOptions()
                                .position(loc)
                                .icon(currentLocIcon) // Use local ready icon
                                .rotation(mapState.bearing)
                                .flat(true)
                                .anchor(0.5f, 0.5f)
                                .zIndex(2.0f)
                        )
                    } else {
                        marker.position = loc
                        marker.rotation = mapState.bearing
                    }
                }
            }
        }
    )
}

private fun drawSegments(
    googleMap: GoogleMap,
    segments: List<MapSegment>,
    context: Context,
    markerList: MutableList<Marker>,
    polylineList: MutableList<Polyline>,
    directionSmall: BitmapDescriptor?, directionMed: BitmapDescriptor?, directionLarge: BitmapDescriptor?
) {
    googleMap.uiSettings.isZoomControlsEnabled = false
    val currentZoom = googleMap.cameraPosition.zoom

    // TODO: Don't start the activity from there.  This should be done by the fragment/viewModel instaead?
    googleMap.setOnPolylineClickListener { polyline ->
        val segmentId = polyline.tag as? Long
        if (segmentId != null) {
            val intent = Intent(context, SegmentDetailsActivity::class.java)
            intent.putExtra(SegmentsDatabaseManager.Segments.SEGMENT_ID, segmentId)
            context.startActivity(intent)
        }
    }

    segments.forEach { segment ->
        // Main Path
        val polyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(segment.path)
                .color(StravaOrange.copy(alpha = 0.7f).toArgb())
                .width(12f)
                .jointType(JointType.ROUND)
                .clickable(true)
        )
        polyline.tag = segment.id // Store ID for the click listener
        polylineList.add(polyline)

        // Select direction icon based on zoom level
        if (currentZoom > 14f) {
            val icon = when {
                currentZoom > 17f -> directionLarge
                currentZoom > 15f -> directionMed
                else -> directionSmall
            }

            segment.path.windowed(2, 20).forEach { pair ->
                googleMap.addMarker(MarkerOptions()
                    .position(LatLng((pair[0].latitude + pair[1].latitude) / 2.0, (pair[0].longitude + pair[1].longitude) / 2.0))
                    .icon(icon)
                    .rotation(calculateBearing(pair[0], pair[1]).toFloat())
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .alpha(0.4f))
            }
        }

        // Start/Finish Lines with name of the segment
        if (segment.path.size >= 6) {
            val startPt = segment.path[0]
            val startNext = segment.path[5]
            val endPt = segment.path.last()
            val endPrev = segment.path[segment.path.size - 6]

            // Orthogonal Lines
            googleMap.addPolyline(PolylineOptions().addAll(calculateOrthogonalLine(startPt, startNext)).color(StravaOrange.toArgb()).width(8f))
            googleMap.addPolyline(PolylineOptions().addAll(calculateOrthogonalLine(endPt, endPrev)).color(StravaOrange.toArgb()).width(8f))

            // Labels (Only at high zoom)
            if (currentZoom > 14f) {
                val startBearing = calculateBearing(startPt, startNext).toFloat()
                val endBearing = calculateBearing(endPrev, endPt).toFloat()

                val textSize = when {
                    currentZoom > 17f -> 22f
                    currentZoom > 15f -> 18f
                    else -> 14f
                }
                // START: Positioned "Below" (Behind) the line
                // Rotation (startBearing - 90) aligns the width of the text with the orthogonal line.
                // An anchor V of -0.2f pushes the bitmap "down" the path direction.
                val startMarker = googleMap.addMarker(MarkerOptions()
                    .position(startPt)
                    .icon(createTextMarkerBitmap(context, segment.name, "🚩", textSize))
                    // .rotation(startBearing)
                    .anchor(0.5f, -0.2f)
                    .flat(false)
                )
                startMarker?.let { markerList.add(it) }

                // FINISH: Positioned "Above" (Ahead) the line
                // An anchor V of 1.2f pushes the bitmap "up" further past the finish point.
                val finishMarker = googleMap.addMarker(MarkerOptions()
                    .position(endPt)
                    .icon(createTextMarkerBitmap(context, segment.name, "🏁", textSize))
                    // .rotation(endBearing)
                    .anchor(0.5f, 1.2f)
                    .flat(false)
                )
                finishMarker?.let { markerList.add(it) }
            }
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

/**
 * Generates a Bitmap containing an emoji and text to be used as a marker.
 */
private fun createTextMarkerBitmap(context: Context, text: String, emoji: String, textSizeIn: Float): BitmapDescriptor? {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = textSizeIn * context.resources.displayMetrics.density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }

    val fullText = "$emoji $text"
    val baseline = -paint.ascent()
    val width = (paint.measureText(fullText) + 0.5f).toInt()
    val height = (baseline + paint.descent() + 0.5f).toInt()

    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)

    // Draw white shadow/outline for better readability on map
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    paint.color = android.graphics.Color.WHITE
    canvas.drawText(fullText, 0f, baseline, paint)

    // Draw actual text
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.BLACK
    canvas.drawText(fullText, 0f, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(image)
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
