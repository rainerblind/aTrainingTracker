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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * A unified layer that can render any MappablePath (Track, Route, or Segment).
 * It automatically applies the correct styling (Solid vs X-Ray) and
 * adds specialized decorations like segment arrows or start/finish markers.
 */
@Composable
fun MappablePathLayer(
    path: MappablePath,
    alpha: Float = 1.0f,
    currentZoom: Float = 0f,
    context: Context? = null,
    directionIcons: Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>? = null,
    onPathClick: (Long) -> Unit = {}
) {
    // 1. Core Path Rendering (Handles both Solid and X-Ray styles)
    // Only apply the X-Ray pattern if the path is primary (alpha = 1.0)
    // to avoid visual artifacts with overlapping transparent layers.
    XRayPolyline(
        points = path.latLngs,
        color = path.color.copy(alpha = alpha),
        width = path.width,
        baseZIndex = path.zIndex,
        overlayZIndex = path.overlayZIndex ?: path.zIndex,
        pattern = if (alpha >= 1.0f) path.pattern else null,
        clickable = true,
        onClick = { onPathClick(path.id) }
    )

    // 2. Specialized Decorations (Segments only)
    if (path is MapSegment && context != null && directionIcons != null) {
        SegmentDecorations(
            segment = path,
            alpha = alpha,
            currentZoom = currentZoom,
            context = context,
            directionIcons = directionIcons
        )
    }
}

/**
 * Specialized decorations for Strava segments: Arrows and Labels.
 */
@Composable
private fun SegmentDecorations(
    segment: MapSegment,
    alpha: Float,
    currentZoom: Float,
    context: Context,
    directionIcons: Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>
) {
    val style = LocalMapStyle.current
    
    // 1. Direction Arrows
    if (currentZoom > 15f) {
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
                zIndex = style.segmentZIndex
            )
        }
    }

    // 2. Start / Finish Lines and Labels
    if (segment.path.size >= 6) {
        val startPt = segment.path[0].latLng
        val startNext = segment.path[5].latLng
        val endPt = segment.path.last().latLng
        val endPrev = segment.path[segment.path.size - 6].latLng

        Polyline(
            points = calculateOrthogonalLine(startPt, startNext),
            color = TTColor.StravaOrange.copy(alpha = alpha),
            width = style.segmentWidth,
            zIndex = style.segmentZIndex
        )
        Polyline(
            points = calculateOrthogonalLine(endPt, endPrev),
            color = TTColor.StravaOrange.copy(alpha = alpha),
            width = style.segmentWidth,
            zIndex = style.segmentZIndex
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

/**
 * Renders the live session track as it is being recorded.
 */
@Composable
fun LiveTrackLayer(path: List<LatLng>) {
    if (path.isEmpty()) return
    val style = LocalMapStyle.current
    
    XRayPolyline(
        points = path,
        color = Color.Blue,
        width = style.trackWidth,
        baseZIndex = style.trackBaseZIndex,
        overlayZIndex = style.trackOverlayZIndex,
        pattern = listOf(Dot(), Gap(style.trackDotGap)),
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
        key(markerData.title, markerData.iconResId) {
            val icon = markerData.iconDescriptor ?: remember(markerData.iconResId, primaryColor) {
                val color = when (markerData.iconResId) {
                    R.drawable.control_start -> TTColor.StartPoint
                    R.drawable.control_stop -> TTColor.EndPoint
                    R.drawable.ic_distance -> TTColor.ApexPoint
                    else -> primaryColor
                }
                createSensorMarker(context, markerData.iconResId, color, Color.White)
            }
            // Use a composite key for marker identity
            val markerState = remember(markerData.title, markerData.iconResId) { MarkerState(position = markerData.position) }
            val haptic = LocalHapticFeedback.current

            // Sync marker position with external state changes (e.g. Cancel)
            // IMPORTANT: Only sync if not dragging, to avoid snapping back during movement
            LaunchedEffect(markerData.position) {
                if (!markerState.isDragging && markerState.position != markerData.position) {
                    markerState.position = markerData.position
                }
            }

            // Haptic feedback when dragging starts
            LaunchedEffect(markerState.isDragging) {
                if (markerState.isDragging) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            Marker(
                state = markerState,
                icon = icon,
                title = markerData.title,
                rotation = markerData.rotation,
                flat = markerData.flat,
                anchor = markerData.anchor,
                draggable = markerData.draggable,
                alpha = markerData.alpha,
                onClick = { markerData.onClick() }
            )

            // Notify the caller when dragging stops
            val currentOnDragEnd by rememberUpdatedState(markerData.onDragEnd)
            LaunchedEffect(markerState) {
                var wasDragging = false
                snapshotFlow { markerState.isDragging }
                    .collect { isDragging ->
                        if (wasDragging && !isDragging) {
                            currentOnDragEnd(markerState.position)
                        }
                        wasDragging = isDragging
                    }
            }
        }
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
