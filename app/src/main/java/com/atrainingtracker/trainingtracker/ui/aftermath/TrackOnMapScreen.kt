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

package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun TrackOnMapScreen(
    workoutData: WorkoutData,
    mapState: MapState
) {
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 3.dp,
            shape = RectangleShape
        ) {
            WorkoutHeader(
                modifier = Modifier.statusBarsPadding(),
                data = workoutData.headerData,
                onClicked = { },
                onExport = { },
                onDeleteConfirmed = { }
            )
        }

        ATrainingTrackerMap(
            mapState = mapState,
            currentLocationFlow = noLocation,
            selectedDistance = selectedDistance,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onSegmentClick = { } // since we do not show the segments here, nothing must be done here.
            // TODO: show Segments and show segment details...
        )

        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            // The Elevation Profile takes the bottom 30%
            // We extract the track points from the mapState
            ElevationProfile(
                pathPoints = mapState.tracks.firstOrNull()?.path ?: emptyList(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .height(150.dp),
                currentDistance = selectedDistance,
                // Callback when the user slides their finger
                onDistanceSelected = { dist ->
                    selectedDistance = dist
                }
            )
        }
    }
}