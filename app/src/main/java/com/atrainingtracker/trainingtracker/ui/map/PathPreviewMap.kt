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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

internal const val PREVIEW_BOUNDS_PADDING_PX = 20
internal const val WORLD_TILE_DP = 256.0
internal const val MIN_ESTIMATED_ZOOM = 1f
internal const val MAX_ESTIMATED_ZOOM = 17f
internal const val SINGLE_POINT_DEFAULT_ZOOM = 15f
internal const val MAX_CAMERA_RETRY_ATTEMPTS = 3
internal const val CAMERA_RETRY_DELAY_MS = 16L
internal const val DEFAULT_PREVIEW_WIDTH_DP = 360f
internal const val DEFAULT_PREVIEW_HEIGHT_DP = 200f

private const val MAX_LATITUDE = 90.0
private const val MAX_MERCATOR_LATITUDE = 85.05112878
private const val MAX_LONGITUDE = 180.0
private const val FULL_WORLD_LONGITUDE_SPAN = 360.0
private const val ZERO_SPAN_EPSILON = 1e-6
private const val MERCATOR_Y_SCALE = 4.0 * Math.PI
private const val DOUBLE_FACTOR = 2.0
private const val CENTER_NORMALIZED = 0.5f
private const val MIN_POLYLINE_POINTS = 2
private const val DARK_LUMINANCE_THRESHOLD = 0.5f
private const val POLYLINE_STROKE_WIDTH_PX = 8f
private const val INSPECTION_PADDING_DP = 16f
private const val INSPECTION_STROKE_DP = 3f
private const val INSPECTION_MARKER_RADIUS_DP = 5f
private const val INSPECTION_GRID_STEPS = 4
private const val INSPECTION_GRID_ALPHA = 0.25f

private fun isValidLatitude(lat: Double?): Boolean =
    lat != null && lat.isFinite() && lat in -MAX_LATITUDE..MAX_LATITUDE

private fun isValidLongitude(lng: Double?): Boolean =
    lng != null && lng.isFinite() && lng in -MAX_LONGITUDE..MAX_LONGITUDE

private fun isValidCoordinate(latLng: LatLng): Boolean =
    isValidLatitude(latLng.latitude) && isValidLongitude(latLng.longitude)

/**
 * Validates raw bounding-box coordinates and constructs a [LatLngBounds].
 * Rejects nulls, non-finite values, out-of-range coordinates, or inverted latitudes (`minLat > maxLat`).
 * Antimeridian wraps (`minLng > maxLng`) are preserved natively by [LatLngBounds].
 */
internal fun buildValidatedBounds(
    minLat: Double?,
    minLng: Double?,
    maxLat: Double?,
    maxLng: Double?
): LatLngBounds? {
    val coordsValid = isValidLatitude(minLat) &&
        isValidLatitude(maxLat) &&
        isValidLongitude(minLng) &&
        isValidLongitude(maxLng)
    return if (coordsValid && minLat!! <= maxLat!!) {
        LatLngBounds(LatLng(minLat, minLng!!), LatLng(maxLat, maxLng!!))
    } else {
        null
    }
}

/**
 * Validates an existing [LatLngBounds] instance against finite and range constraints.
 */
internal fun validateBounds(bounds: LatLngBounds?): LatLngBounds? =
    bounds?.let {
        buildValidatedBounds(
            minLat = it.southwest.latitude,
            minLng = it.southwest.longitude,
            maxLat = it.northeast.latitude,
            maxLng = it.northeast.longitude
        )
    }

private fun resolveSinglePathBounds(
    allPaths: List<MappablePath>,
    hasExtraMarkers: Boolean
): LatLngBounds? {
    if (hasExtraMarkers || allPaths.size != 1) return null
    val singlePath = allPaths.first()
    return buildValidatedBounds(
        minLat = singlePath.minLat,
        minLng = singlePath.minLng,
        maxLat = singlePath.maxLat,
        maxLng = singlePath.maxLng
    )
}

private fun buildCompositePreviewBounds(
    allPaths: List<MappablePath>,
    start: LatLng?,
    end: LatLng?,
    apex: LatLng?
): LatLngBounds? {
    val boundsBuilder = LatLngBounds.Builder()
    var count = 0
    allPaths.forEach { p ->
        p.latLngs.forEach {
            if (isValidCoordinate(it)) {
                boundsBuilder.include(it)
                count++
            }
        }
    }
    listOfNotNull(start, end, apex).forEach { point ->
        if (isValidCoordinate(point)) {
            boundsBuilder.include(point)
            count++
        }
    }
    return if (count > 0) boundsBuilder.build() else null
}

/**
 * Synchronously resolves preview [LatLngBounds] prior to map composition in priority order:
 * 1) Validated explicit [targetBounds],
 * 2) Single-path precomputed bounds (`minLat..maxLng`),
 * 3) Composite bounds built across [allPaths] coordinates and [start]/[end]/[apex] markers.
 */
internal fun resolvePreviewBounds(
    targetBounds: LatLngBounds?,
    allPaths: List<MappablePath>,
    start: LatLng?,
    end: LatLng?,
    apex: LatLng?
): LatLngBounds? {
    val hasExtraMarkers = start != null || end != null || apex != null
    return validateBounds(targetBounds)
        ?: resolveSinglePathBounds(allPaths, hasExtraMarkers)
        ?: buildCompositePreviewBounds(allPaths, start, end, apex)
}

private fun mercatorYFraction(latDeg: Double): Double {
    val clamped = latDeg.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
    val sinLat = sin(Math.toRadians(clamped))
    return ln((1.0 + sinLat) / (1.0 - sinLat)) / MERCATOR_Y_SCALE
}

/**
 * Estimates an aspect-aware Web Mercator [CameraPosition] to seed Lite Mode maps before
 * Play Services completes layout/tile loading, avoiding initial `(0.0, 0.0), zoom = 0f` flashes.
 */
internal fun estimateInitialCameraPosition(
    bounds: LatLngBounds,
    widthDp: Float = DEFAULT_PREVIEW_WIDTH_DP,
    heightDp: Float = DEFAULT_PREVIEW_HEIGHT_DP,
    paddingPx: Int = PREVIEW_BOUNDS_PADDING_PX,
    density: Float = 1f
): CameraPosition {
    val swLat = bounds.southwest.latitude
    val neLat = bounds.northeast.latitude
    val swLng = bounds.southwest.longitude
    val neLng = bounds.northeast.longitude

    val centerLat = (swLat + neLat) / DOUBLE_FACTOR
    val lngSpan = if (neLng >= swLng) {
        neLng - swLng
    } else {
        (FULL_WORLD_LONGITUDE_SPAN - swLng) + neLng
    }
    val rawCenterLng = swLng + lngSpan / DOUBLE_FACTOR
    val centerLng = if (rawCenterLng > MAX_LONGITUDE) {
        rawCenterLng - FULL_WORLD_LONGITUDE_SPAN
    } else {
        rawCenterLng
    }
    val center = LatLng(centerLat, centerLng)

    val latSpan = abs(neLat - swLat)
    if (latSpan < ZERO_SPAN_EPSILON && abs(lngSpan) < ZERO_SPAN_EPSILON) {
        return CameraPosition.fromLatLngZoom(center, SINGLE_POINT_DEFAULT_ZOOM)
    }

    val safeDensity = if (density.isFinite() && density > 0f) density else 1f
    val paddingDp = (paddingPx.coerceAtLeast(0) * DOUBLE_FACTOR) / safeDensity
    val safeWidthDp = if (widthDp.isFinite() && widthDp > 0f) {
        widthDp.toDouble()
    } else {
        DEFAULT_PREVIEW_WIDTH_DP.toDouble()
    }
    val safeHeightDp = if (heightDp.isFinite() && heightDp > 0f) {
        heightDp.toDouble()
    } else {
        DEFAULT_PREVIEW_HEIGHT_DP.toDouble()
    }
    val effectiveWidthDp = (safeWidthDp - paddingDp).coerceAtLeast(1.0)
    val effectiveHeightDp = (safeHeightDp - paddingDp).coerceAtLeast(1.0)

    val latFraction = abs(mercatorYFraction(neLat) - mercatorYFraction(swLat))
    val lngFraction = lngSpan / FULL_WORLD_LONGITUDE_SPAN

    val latZoom = if (latFraction > ZERO_SPAN_EPSILON) {
        ln(effectiveHeightDp / WORLD_TILE_DP / latFraction) / ln(DOUBLE_FACTOR)
    } else {
        Double.POSITIVE_INFINITY
    }
    val lngZoom = if (lngFraction > ZERO_SPAN_EPSILON) {
        ln(effectiveWidthDp / WORLD_TILE_DP / lngFraction) / ln(DOUBLE_FACTOR)
    } else {
        Double.POSITIVE_INFINITY
    }

    val rawZoom = minOf(latZoom, lngZoom)
    val clampedZoom = if (rawZoom.isFinite()) {
        rawZoom.toFloat().coerceIn(MIN_ESTIMATED_ZOOM, MAX_ESTIMATED_ZOOM)
    } else {
        SINGLE_POINT_DEFAULT_ZOOM
    }
    return CameraPosition.fromLatLngZoom(center, clampedZoom)
}

/**
 * Builds Lite Mode [GoogleMapOptions] pre-seeded with [initialCameraPosition] when available.
 */
internal fun createLiteModeMapOptions(initialCameraPosition: CameraPosition?): GoogleMapOptions =
    GoogleMapOptions().liteMode(true).apply {
        if (initialCameraPosition != null) {
            camera(initialCameraPosition)
        }
    }

/**
 * Attempts a bounds camera update and returns `true` when successful, or `false` when a
 * transient zero-size [IllegalStateException] indicates layout is not yet ready for retry.
 */
internal fun tryApplyPreviewBounds(applyMove: () -> Unit): Boolean =
    try {
        applyMove()
        true
    } catch (_: IllegalStateException) {
        false
    }

/**
 * Static Lite-Mode map preview for tracks, routes, and workout thumbnails.
 */
@Composable
fun PathPreviewMap(
    path: MappablePath? = null,
    paths: List<MappablePath> = emptyList(),
    modifier: Modifier = Modifier,
    start: LatLng? = null,
    end: LatLng? = null,
    apex: LatLng? = null,
    startTitle: String? = null,
    endTitle: String? = null,
    apexTitle: String? = null,
    apexIconRes: Int = R.drawable.ic_distance,
    apexColor: Color = TTColor.ApexPoint,
    targetBounds: LatLngBounds? = null,
    onMapClick: () -> Unit = {}
) {
    val allPaths = remember(path, paths) {
        if (path != null) listOf(path) + paths else paths
    }
    val actualStart = start ?: allPaths.firstOrNull()?.latLngs?.firstOrNull()
    val actualEnd = end ?: allPaths.lastOrNull()?.latLngs?.lastOrNull()
    val resolvedBounds = remember(targetBounds, allPaths, actualStart, actualEnd, apex) {
        resolvePreviewBounds(targetBounds, allPaths, actualStart, actualEnd, apex)
    }

    val density = LocalDensity.current.density
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val widthDp = if (containerSize.width > 0) containerSize.width / density else DEFAULT_PREVIEW_WIDTH_DP
    val heightDp = if (containerSize.height > 0) containerSize.height / density else DEFAULT_PREVIEW_HEIGHT_DP
    val initialCameraPosition = remember(resolvedBounds, widthDp, heightDp, density) {
        resolvedBounds?.let {
            estimateInitialCameraPosition(it, widthDp, heightDp, PREVIEW_BOUNDS_PADDING_PX, density)
        }
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD

    Box(
        modifier = modifier
            .background(if (isDark) TTColor.DarkMapCanvas else Color.White)
            .onSizeChanged { containerSize = it }
    ) {
        if (LocalInspectionMode.current) {
            PathPreviewInspectionCanvas(
                allPaths = allPaths,
                resolvedBounds = resolvedBounds,
                actualStart = actualStart,
                actualEnd = actualEnd,
                actualApex = apex,
                apexColor = apexColor,
                isDark = isDark,
                onMapClick = onMapClick
            )
        } else {
            PathPreviewLiveMap(
                path = path,
                paths = paths,
                allPaths = allPaths,
                resolvedBounds = resolvedBounds,
                initialCameraPosition = initialCameraPosition,
                containerSize = containerSize,
                actualStart = actualStart,
                actualEnd = actualEnd,
                actualApex = apex,
                startTitle = startTitle,
                endTitle = endTitle,
                apexTitle = apexTitle,
                apexIconRes = apexIconRes,
                apexColor = apexColor,
                isDark = isDark,
                onMapClick = onMapClick
            )
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PathPreviewLiveMap(
    path: MappablePath?,
    paths: List<MappablePath>,
    allPaths: List<MappablePath>,
    resolvedBounds: LatLngBounds?,
    initialCameraPosition: CameraPosition?,
    containerSize: IntSize,
    actualStart: LatLng?,
    actualEnd: LatLng?,
    actualApex: LatLng?,
    startTitle: String?,
    endTitle: String?,
    apexTitle: String?,
    apexIconRes: Int,
    apexColor: Color,
    isDark: Boolean,
    onMapClick: () -> Unit
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        initialCameraPosition?.let { position = it }
    }
    LaunchedEffect(initialCameraPosition) {
        if (initialCameraPosition != null && cameraPositionState.position.zoom == 0f) {
            cameraPositionState.position = initialCameraPosition
        }
    }

    var isMapLoaded by remember { mutableStateOf(false) }
    var retryCount by remember(resolvedBounds) { mutableIntStateOf(0) }
    val mapProperties = remember(isDark, context) {
        DarkMapStyle.resolveMapProperties(isDark, context)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        googleMapOptionsFactory = { createLiteModeMapOptions(initialCameraPosition) },
        uiSettings = previewMapUiSettings(),
        properties = mapProperties,
        onMapLoaded = { isMapLoaded = true },
        onMapClick = { onMapClick() }
    ) {
        PathPreviewMapOverlays(
            path = path,
            paths = paths,
            allPaths = allPaths,
            actualStart = actualStart,
            actualEnd = actualEnd,
            actualApex = actualApex,
            startTitle = startTitle,
            endTitle = endTitle,
            apexTitle = apexTitle,
            apexIconRes = apexIconRes,
            apexColor = apexColor
        )

        MapEffect(resolvedBounds, containerSize, isMapLoaded, retryCount) { map ->
            if (resolvedBounds != null && containerSize.width > 0 && containerSize.height > 0) {
                val moved = tryApplyPreviewBounds {
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(resolvedBounds, PREVIEW_BOUNDS_PADDING_PX)
                    )
                }
                if (moved) {
                    retryCount = 0
                } else if (retryCount < MAX_CAMERA_RETRY_ATTEMPTS) {
                    delay(CAMERA_RETRY_DELAY_MS)
                    retryCount++
                }
            }
        }
    }

    DarkMapAntiFlashOverlay(
        isMapLoaded = isMapLoaded,
        isDark = isDark
    )
}

private fun previewMapUiSettings(): MapUiSettings = MapUiSettings(
    zoomControlsEnabled = false,
    compassEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    scrollGesturesEnabled = false,
    zoomGesturesEnabled = false
)

@Composable
@GoogleMapComposable
private fun PathPreviewMapOverlays(
    path: MappablePath?,
    paths: List<MappablePath>,
    allPaths: List<MappablePath>,
    actualStart: LatLng?,
    actualEnd: LatLng?,
    actualApex: LatLng?,
    startTitle: String?,
    endTitle: String?,
    apexTitle: String?,
    apexIconRes: Int,
    apexColor: Color
) {
    val context = LocalContext.current

    allPaths.forEach { p ->
        if (p.latLngs.isNotEmpty()) {
            Polyline(
                points = p.latLngs,
                color = p.color,
                width = POLYLINE_STROKE_WIDTH_PX
            )
        }
    }

    actualStart?.let {
        Marker(
            state = remember(it) { MarkerState(position = it) },
            title = startTitle,
            icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
        )
    }
    actualEnd?.let {
        Marker(
            state = remember(it) { MarkerState(position = it) },
            title = endTitle,
            icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) }
        )
    }
    actualApex?.let {
        Marker(
            state = remember(it) { MarkerState(position = it) },
            title = apexTitle,
            icon = remember(apexIconRes, apexColor) { createSensorMarker(context, apexIconRes, apexColor) }
        )
    }

    if (path is MapRoute && path.waypoints.isNotEmpty()) {
        RouteWaypointsLayer(waypoints = path.waypoints, context = context)
    }
    paths.filterIsInstance<MapRoute>().forEach { route ->
        if (route.waypoints.isNotEmpty()) {
            RouteWaypointsLayer(waypoints = route.waypoints, context = context)
        }
    }
}

private fun projectPointToCanvas(
    point: LatLng,
    bounds: LatLngBounds,
    rawLatSpan: Double,
    rawLngSpan: Double,
    padPx: Float,
    drawWidth: Float,
    drawHeight: Float
): Offset {
    val xNorm = if (rawLngSpan < ZERO_SPAN_EPSILON) {
        CENTER_NORMALIZED
    } else {
        val lngDelta = if (point.longitude >= bounds.southwest.longitude) {
            point.longitude - bounds.southwest.longitude
        } else {
            (FULL_WORLD_LONGITUDE_SPAN - bounds.southwest.longitude) + point.longitude
        }
        (lngDelta / rawLngSpan).toFloat().coerceIn(0f, 1f)
    }
    val yNorm = if (rawLatSpan < ZERO_SPAN_EPSILON) {
        CENTER_NORMALIZED
    } else {
        ((bounds.northeast.latitude - point.latitude) / rawLatSpan).toFloat().coerceIn(0f, 1f)
    }
    return Offset(padPx + xNorm * drawWidth, padPx + yNorm * drawHeight)
}

@Composable
private fun PathPreviewInspectionCanvas(
    allPaths: List<MappablePath>,
    resolvedBounds: LatLngBounds?,
    actualStart: LatLng?,
    actualEnd: LatLng?,
    actualApex: LatLng?,
    apexColor: Color,
    isDark: Boolean,
    onMapClick: () -> Unit
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = INSPECTION_GRID_ALPHA)
    val fallbackStrokeColor = if (isDark) TTColor.DarkMapTrackCyan else MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onMapClick)
    ) {
        val stepX = size.width / INSPECTION_GRID_STEPS
        val stepY = size.height / INSPECTION_GRID_STEPS
        for (i in 1 until INSPECTION_GRID_STEPS) {
            drawLine(gridColor, Offset(stepX * i, 0f), Offset(stepX * i, size.height), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, stepY * i), Offset(size.width, stepY * i), strokeWidth = 1f)
        }

        val bounds = resolvedBounds ?: return@Canvas
        val padPx = INSPECTION_PADDING_DP.dp.toPx()
        val drawWidth = (size.width - padPx * 2f).coerceAtLeast(1f)
        val drawHeight = (size.height - padPx * 2f).coerceAtLeast(1f)
        val rawLatSpan = bounds.northeast.latitude - bounds.southwest.latitude
        val rawLngSpan = if (bounds.northeast.longitude >= bounds.southwest.longitude) {
            bounds.northeast.longitude - bounds.southwest.longitude
        } else {
            (FULL_WORLD_LONGITUDE_SPAN - bounds.southwest.longitude) + bounds.northeast.longitude
        }

        val toOffset: (LatLng) -> Offset = { point ->
            projectPointToCanvas(point, bounds, rawLatSpan, rawLngSpan, padPx, drawWidth, drawHeight)
        }

        val strokePx = INSPECTION_STROKE_DP.dp.toPx()
        allPaths.forEach { mappablePath ->
            if (mappablePath.latLngs.size >= MIN_POLYLINE_POINTS) {
                val composePath = Path()
                mappablePath.latLngs.forEachIndexed { index, latLng ->
                    val offset = toOffset(latLng)
                    if (index == 0) composePath.moveTo(offset.x, offset.y) else composePath.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = composePath,
                    color = if (isDark) fallbackStrokeColor else mappablePath.color,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        val markerRadius = INSPECTION_MARKER_RADIUS_DP.dp.toPx()
        actualApex?.let { drawCircle(color = apexColor, radius = markerRadius, center = toOffset(it)) }
        actualStart?.let { drawCircle(color = TTColor.StartPoint, radius = markerRadius, center = toOffset(it)) }
        actualEnd?.let { drawCircle(color = TTColor.EndPoint, radius = markerRadius, center = toOffset(it)) }
    }
}

@Suppress("MagicNumber")
private fun samplePreviewTrack(): MapTrack {
    val points = listOf(
        PathPoint(0.0, LatLng(48.1351, 11.5820)),
        PathPoint(1200.0, LatLng(48.1450, 11.5920)),
        PathPoint(2400.0, LatLng(48.1520, 11.6100)),
        PathPoint(3600.0, LatLng(48.1410, 11.6050)),
        PathPoint(4800.0, LatLng(48.1360, 11.5840))
    )
    return MapTrack(
        id = 1L,
        type = TrackType.BEST,
        bSportType = BSportType.BIKE,
        path = points,
        minLat = 48.1351,
        minLng = 11.5820,
        maxLat = 48.1520,
        maxLng = 11.6100
    )
}

@Preview(name = "PathPreviewMap Light", showBackground = true)
@Composable
private fun PathPreviewMapLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        val track = samplePreviewTrack()
        PathPreviewMap(
            path = track,
            modifier = Modifier
                .fillMaxWidth()
                .height(DEFAULT_PREVIEW_HEIGHT_DP.dp),
            start = track.latLngs.first(),
            end = track.latLngs.last(),
            apex = track.latLngs[2]
        )
    }
}

@Preview(name = "PathPreviewMap Dark", showBackground = true)
@Composable
private fun PathPreviewMapDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true) {
        val track = samplePreviewTrack()
        PathPreviewMap(
            path = track,
            modifier = Modifier
                .fillMaxWidth()
                .height(DEFAULT_PREVIEW_HEIGHT_DP.dp),
            start = track.latLngs.first(),
            end = track.latLngs.last(),
            apex = track.latLngs[2]
        )
    }
}
