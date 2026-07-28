/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.*
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun InteractivePeriodMap(
    summary: PeriodSummary,
    mapState: PeriodMapState, // ATT-440: Adoption of discrete MapState
    isHeatmapEnabled: Boolean = true,
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    shouldTakeSnapshot: Boolean = false,
    onSnapshotReady: (Bitmap) -> Unit = {}
) {
    val context = LocalContext.current
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }
    
    // --- 1. INSTANT MAP SETUP (ATT-346 Relational) ---
    // Immediately fit the map to pre-calculated bounds from the database
    // ATT-440 Refinement: Added safety check to ignore invalid (0.0) coordinates (Ocean Trap).
    // ATT-440 Refinement: Key the remember block by stable period IDs to prevent zoom reset 
    // when background paths arrive.
    val periodBounds = remember(summary.periodType, summary.startTimestampS) {
        if (summary.minLat < 90.0 && summary.minLat != 0.0) {
            LatLngBounds(LatLng(summary.minLat, summary.minLng), LatLng(summary.maxLat, summary.maxLng))
        } else null
    }

    // --- 2. PROGRESSIVE CONTENT ---
    // Anchor routes are already in summary.polylines (enriched by Repository RAM scan)
    val anchorPaths = remember(summary.polylines) {
        summary.polylines.map { PolyUtil.decode(it) }
    }
    
    val fallbackColor = MaterialTheme.colorScheme.primary

    ATrainingTrackerMap(
        zoomFocus = if (periodBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = periodBounds,
        currentLocationFlow = noLocation,
        modifier = modifier,
        shouldTakeSnapshot = shouldTakeSnapshot,
        onSnapshotReady = onSnapshotReady,
        content = {
            // 1. Render Anchor Tracks (North/South/East/West/Longest) instantly
            anchorPaths.forEach { path ->
                path(MapTrack(-1, TrackType.BEST, BSportType.UNKNOWN, path.map { PathPoint(0.0, it, 0.0) }), alpha = 0.5f)
            }

            // 2. Extrema Markers (Markers for anchors are already in summary.extremaMarkers)
            val markersList = summary.extremaMarkers.map { marker ->
                val color = when (marker.markerType) {
                    PeriodMarkerType.START -> TTColor.StartPoint
                    PeriodMarkerType.END -> TTColor.EndPoint
                    PeriodMarkerType.DISTANCE -> TTColor.ApexPoint
                    else -> fallbackColor
                }
                LocationMarker(
                    position = marker.pos, iconResId = marker.iconResId, title = marker.title,
                    iconDescriptor = createSensorMarker(context, marker.iconResId, color, Color.White),
                    alpha = 0.5f, // ATT-440: Align alpha with member markers for consistent zoom blending
                    onClick = { onWorkoutClick(marker.workoutId); true }
                )
            }
            markers(markersList)
            
            // 3. Render Additional Detail from MapState (ATT-440 Cluster algorithm)
            if (!mapState.isLoading) {
                // Add member traces
                mapState.tracks.forEach { track ->
                    path(track, alpha = 0.3f, onPathClick = { onWorkoutClick(it) })
                }
                
                // Add member markers
                markers(mapState.memberMarkers)
            }

            // 4. Render full heatmap as it becomes ready in the background
            if (isHeatmapEnabled && mapState.heatmapPaths.isNotEmpty()) {
                heatmap(mapState.heatmapPaths, opacity = 0.8)
            }
        }
    )
}
