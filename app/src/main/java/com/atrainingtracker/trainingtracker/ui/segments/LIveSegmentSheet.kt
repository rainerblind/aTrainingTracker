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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow


@Composable
fun LiveSegmentSheetContent(
    liveSegment: LiveSegment,
    mapState: MapState,
    mapViewModel: MapViewModel,
    currentLocationFlow: StateFlow<LatLng?>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // 1. Header (The "Peek" part)
        SegmentSummaryHeader(
            summary = liveSegment.summary,
            liveSegmentData = liveSegment.liveData
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 2. Map (2/3 of remaining space)
        ATrainingTrackerMap(
            mapState = mapState,
            mapViewModel = mapViewModel,
            currentLocationFlow = currentLocationFlow,
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f)
        )

        // 3. Elevation Profile (1/3 of remaining space)
        ElevationProfile(
            pathPoints = liveSegment.path,
            // currentDistance = liveSegment.liveData.distanceOnSegment,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        )
    }
}