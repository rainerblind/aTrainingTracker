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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.HeatmapTileProvider

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun InteractivePeriodMap(
    // Map of WorkoutID to its Polyline String
    workouts: Map<Long, String>,
    periodType: PeriodType,
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    shouldTakeSnapshot: Boolean = false, // Trigger a snapshot
    onSnapshotReady: (Bitmap) -> Unit = {}
) {
    val allPaths = remember(workouts) {
        workouts.mapValues { PolyUtil.decode(it.value) }
    }

    val visuals = remember(allPaths, periodType) {
        getPeriodMapVisuals(periodType, allPaths.values.toList())
    }

    var isMapLoaded by remember { mutableStateOf(false) }

    // 2. Calculate the Bounds for all points in all paths
    val bounds = remember(allPaths) {
        val builder = LatLngBounds.Builder()
        var hasPoints = false
        allPaths.values.forEach { path ->
            path.forEach { point ->
                builder.include(point)
                hasPoints = true
            }
        }
        if (hasPoints) builder.build() else null
    }

    // Apply the zoom as soon as the map is loaded or bounds change
    LaunchedEffect(bounds, isMapLoaded) {
        if (isMapLoaded) {
            bounds?.let {
                try {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngBounds(it, 50) // 50dp padding
                    )
                } catch (e: Exception) {
                    // Map size might still be 0
                }
            }
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.TERRAIN),
        onMapLoaded = { isMapLoaded = true },
        // Ensure UI stays clean during snapshot if needed
        uiSettings = com.google.maps.android.compose.MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    ) {
        // Heatmap Layer
        visuals.heatmapProvider?.let {
            TileOverlay(tileProvider = it)
        }

        // Snapshot Logic
        MapEffect(shouldTakeSnapshot) { map ->
            if (shouldTakeSnapshot) {
                map.snapshot { bitmap ->
                    if (bitmap != null) {
                        onSnapshotReady(bitmap)
                    }
                }
            }
        }

        allPaths.forEach { (workoutId, path) ->
            Polyline(
                points = path,
                clickable = true, // each workout can be clicked
                color = TrackType.BEST.color.copy(alpha = visuals.polylineAlpha),
                width = visuals.polylineWidth,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND,
                onClick = { onWorkoutClick(workoutId) }
            )
        }
    }
}