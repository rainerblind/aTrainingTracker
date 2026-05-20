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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun InteractivePeriodMap(
    // Map of WorkoutID to its Polyline String
    workouts: Map<Long, String>,
    onWorkoutClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val allPaths = remember(workouts) {
        workouts.mapValues { PolyUtil.decode(it.value) }
    }

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

    val cameraPositionState = rememberCameraPositionState()

    // 3. Apply the zoom as soon as the map is loaded or bounds change
    LaunchedEffect(bounds) {
        bounds?.let {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngBounds(it, 50) // 50dp padding
            )
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState
    ) {
        allPaths.forEach { (workoutId, path) ->
            Polyline(
                points = path,
                clickable = true, // each workout can be clicked
                color = TrackType.BEST.color,
                width = 8f,
                startCap = RoundCap(),
                endCap = RoundCap(),
                jointType = JointType.ROUND,
                onClick = { onWorkoutClick(workoutId) }
            )
        }
    }
}