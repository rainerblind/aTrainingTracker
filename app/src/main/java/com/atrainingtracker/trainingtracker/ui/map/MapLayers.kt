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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline

/**
 * Renders a list of workout tracks with the "X-Ray" dotted pattern.
 */
@Composable
fun TrackLayer(tracks: List<MapTrack>) {
    tracks.forEach { track ->
        if (track.isVisible) {
            XRayPolyline(
                points = track.path.map { it.latLng },
                color = track.color,
                width = track.width,
                baseZIndex = track.zIndex,
                overlayZIndex = track.overlayZIndex,
                pattern = track.pattern
            )
        }
    }
}

/**
 * Renders a list of routes with the "X-Ray" dashed pattern.
 * Highlighted routes (active or focused) are shown with full opacity and dashes.
 */
@Composable
fun RouteLayer(
    routes: List<MapRoute>,
    zoomFocus: MapZoomFocus,
    activeSportType: BSportType,
    onRouteClick: (Long) -> Unit
) {
    routes.forEach { route ->
        val highlightRoute = zoomFocus != MapZoomFocus.FOLLOW_ME 
                || (route.isSelected && route.bSportType == activeSportType)

        val alpha = if (highlightRoute) 1.0f else MapVisualization.ROUTE_UNSELECTED_ALPHA

        XRayPolyline(
            points = route.path.map { it.latLng },
            color = route.color.copy(alpha = alpha),
            width = route.width,
            baseZIndex = route.zIndex,
            overlayZIndex = route.overlayZIndex,
            pattern = if (highlightRoute) route.pattern else null,
            clickable = true,
            onClick = { onRouteClick(route.id) }
        )
    }
}

/**
 * Renders a list of Strava segments with direction arrows and labels.
 */
@Composable
fun SegmentLayer(
    segments: List<MapSegment>,
    activeLiveSegmentIds: Set<Long>,
    zoomFocus: MapZoomFocus,
    currentZoom: Float,
    context: Context,
    directionIcons: Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>,
    onSegmentClick: (Long) -> Unit
) {
    segments.forEach { segment ->
        val isLive = activeLiveSegmentIds.contains(segment.stravaId)
        val isFollowMeEnabled = zoomFocus == MapZoomFocus.FOLLOW_ME
        val alpha = if (!isFollowMeEnabled || isLive) 1.0f else MapVisualization.SEGMENT_UNSELECTED_ALPHA
        val segmentColor = StravaOrange.copy(alpha = alpha)

        // 1. Path
        Polyline(
            points = segment.path.map { it.latLng },
            color = segmentColor,
            width = MapVisualization.SEGMENT_WIDTH,
            zIndex = MapVisualization.SEGMENT_Z_INDEX,
            clickable = true,
            onClick = { onSegmentClick(segment.stravaId) }
        )

        // 2. Direction Arrows (Performance check: only at high zoom or if Live)
        if (isLive || currentZoom > 15f) {
            val arrowIcon = when {
                currentZoom > 17f -> directionIcons.third
                currentZoom > 15.5f -> directionIcons.second
                else -> directionIcons.first
            }

            segment.path.windowed(2, 20).forEach { pair ->
                val midPos = LatLng(
                    (pair[0].latLng.latitude + pair[1].latLng.latitude) / 2.0,
                    (pair[0].latLng.longitude + pair[1].latLng.longitude) / 2.0
                )
                Marker(
                    state = remember(midPos) { MarkerState(position = midPos) },
                    icon = arrowIcon,
                    rotation = calculateBearing(pair[0].latLng, pair[1].latLng).toFloat(),
                    flat = true,
                    anchor = Offset(0.5f, 0.5f),
                    alpha = alpha,
                    zIndex = MapVisualization.SEGMENT_Z_INDEX
                )
            }
        }

        // 3. Start / Finish Lines and Labels
        if (segment.path.size >= 6) {
            val startPt = segment.path[0].latLng
            val startNext = segment.path[5].latLng
            val endPt = segment.path.last().latLng
            val endPrev = segment.path[segment.path.size - 6].latLng

            Polyline(
                points = calculateOrthogonalLine(startPt, startNext),
                color = segmentColor,
                width = MapVisualization.SEGMENT_WIDTH,
                zIndex = MapVisualization.SEGMENT_Z_INDEX
            )
            Polyline(
                points = calculateOrthogonalLine(endPt, endPrev),
                color = segmentColor,
                width = MapVisualization.SEGMENT_WIDTH,
                zIndex = MapVisualization.SEGMENT_Z_INDEX
            )

            if (currentZoom > 14f && segment.showStartAndFinishText) {
                val textSize = when {
                    currentZoom > 17f -> 22f
                    currentZoom > 15f -> 18f
                    else -> 14f
                }
                val sportIcon = if (segment.bSportType == BSportType.RUN) R.drawable.bsport_run else R.drawable.bsport_bike

                Marker(
                    state = remember(startPt) { MarkerState(position = startPt) },
                    icon = remember(segment.name, textSize) {
                        createTextMarkerBitmap(context, segment.name, "🚩", textSize, sportIcon)
                    },
                    anchor = Offset(0.5f, -0.2f),
                    alpha = alpha
                )

                Marker(
                    state = remember(endPt) { MarkerState(position = endPt) },
                    icon = remember(segment.name, textSize) {
                        createTextMarkerBitmap(context, segment.name, "🏁", textSize, sportIcon)
                    },
                    anchor = Offset(0.5f, 1.2f),
                    alpha = alpha
                )
            }
        }
    }
}

/**
 * Renders the live session track as it is being recorded.
 */
@Composable
fun LiveTrackLayer(path: List<LatLng>) {
    if (path.isEmpty()) return
    
    XRayPolyline(
        points = path,
        color = Color.Blue,
        width = MapVisualization.TRACK_WIDTH,
        baseZIndex = MapVisualization.TRACK_BASE_Z_INDEX,
        overlayZIndex = MapVisualization.TRACK_OVERLAY_Z_INDEX,
        pattern = listOf(Dot(), Gap(MapVisualization.TRACK_DOT_GAP)),
        jointType = JointType.ROUND
    )
}

/**
 * Renders static sensor markers (Start, End, Lap, etc.)
 */
@Composable
fun MarkerLayer(
    markers: List<LocationMarker>,
    primaryColor: Color,
    context: Context
) {
    markers.forEach { markerData ->
        val icon = remember(markerData.iconResId, primaryColor) {
            createSensorMarker(context, markerData.iconResId, primaryColor, Color.White)
        }
        Marker(
            state = remember(markerData.position) { MarkerState(position = markerData.position) },
            icon = icon,
            title = markerData.title,
            rotation = markerData.rotation,
            flat = markerData.flat,
            anchor = markerData.anchor
        )
    }
}

/**
 * Renders the specialized marker that follows the distance scrubber.
 */
@Composable
fun ScrubMarkerLayer(
    selectedDistance: Double?,
    activePath: List<PathPoint>,
    scrubIconLeft: BitmapDescriptor?,
    scrubIconRight: BitmapDescriptor?
) {
    selectedDistance?.let { targetDist ->
        val index = activePath.indexOfFirst { it.distance >= targetDist }

        if (index != -1) {
            val point = activePath[index]
            val isWestbound = run {
                val nextSignificantPoint = activePath.drop(index + 1).firstOrNull {
                    it.latLng.longitude != point.latLng.longitude
                }
                if (nextSignificantPoint != null) {
                    nextSignificantPoint.latLng.longitude < point.latLng.longitude
                } else {
                    val prevSignificantPoint = activePath.take(index).lastOrNull {
                        it.latLng.longitude != point.latLng.longitude
                    }
                    if (prevSignificantPoint != null) {
                        point.latLng.longitude < prevSignificantPoint.latLng.longitude
                    } else {
                        false
                    }
                }
            }

            Marker(
                state = remember(point.latLng) { MarkerState(position = point.latLng) },
                icon = if (isWestbound) scrubIconLeft else scrubIconRight,
                zIndex = 5.0f,
                flat = true
            )
        }
    }
}

/**
 * Shared helper to render a dual-polyline with a patterned overlay for high visibility.
 */
@Composable
private fun XRayPolyline(
    points: List<LatLng>,
    color: Color,
    width: Float,
    baseZIndex: Float,
    overlayZIndex: Float,
    pattern: List<PatternItem>? = null,
    jointType: Int = JointType.DEFAULT,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    // 1. Solid Base
    Polyline(
        points = points,
        color = color,
        width = width,
        zIndex = baseZIndex,
        clickable = clickable,
        onClick = { if (clickable) onClick() },
        jointType = jointType
    )
    
    // 2. Patterned Overlay
    if (pattern != null) {
        Polyline(
            points = points,
            color = color,
            width = width,
            zIndex = overlayZIndex,
            pattern = pattern,
            jointType = jointType
        )
    }
}
