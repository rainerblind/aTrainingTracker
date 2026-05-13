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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.segments.SegmentSummaryHeader
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun RouteOnMapScreen(
    routeSummary: RouteSummary?,
    mapState: MapState,
    modifier: Modifier
) {
    // Shared state for the "seeker" position on both Map and Profile
    var selectedDistance by remember { mutableStateOf<Double?>(null) }
    val noLocation = remember { MutableStateFlow<LatLng?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        routeSummary?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                // tonalElevation = 3.dp,
                shape = RectangleShape
            ) {
                Column(modifier = modifier) {
                    RouteSummaryHeader(
                        summary = it,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. MAP (Main content)
        // We use weight(1f) to take up all available middle space
        ATrainingTrackerMap(
            mapState = mapState,
            currentLocationFlow = noLocation,
            selectedDistance = selectedDistance,
            modifier = Modifier.weight(1f),
            onSegmentClick = { /* nothing to do here. */ }
        )

        // 3. ELEVATION PROFILE with Navigation Bar Padding
        mapState.routes.firstOrNull()?.let { segment ->
            Surface(
                color = MaterialTheme.colorScheme.surface
            ) {
                ElevationProfile(
                    pathPoints = segment.path,
                    currentDistance = selectedDistance,
                    onDistanceSelected = { dist ->
                        selectedDistance = dist
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
        }
    }
}