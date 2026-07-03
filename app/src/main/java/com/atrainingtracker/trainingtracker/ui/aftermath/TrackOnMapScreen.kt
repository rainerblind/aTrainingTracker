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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import com.atrainingtracker.trainingtracker.helpers.combineWorkoutAndShare
import kotlinx.coroutines.launch

@Composable
fun TrackOnMapScreen(
    workoutData: WorkoutData,
    modifier: Modifier = Modifier,
    tracks: List<MapTrack> = emptyList(),
    segments: List<MapSegment> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    markers: List<LocationMarker> = emptyList(),
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true
) {
    MapDetailLayout(
        bSportType = workoutData.bSportType,
        zoomFocus = MapZoomFocus.FIT_PRIMARY,
        activeScrubPath = tracks.firstOrNull()?.path,
        minAltitudeOverride = workoutData.minAltitude,
        maxAltitudeOverride = workoutData.maxAltitude,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        header = {
            WorkoutHeader(
                modifier = modifier,
                data = workoutData.headerData,
                menuEnabled = false,
                onClicked = { },
                onExport = { },
                onSaveAsRoute = { },
                onDeleteRequest = { }
            )
        },
        mapContent = {
            tracks(tracks)
            contextualPaths(segments)
            contextualPaths(routes)
            markers(markers)
        },
        modifier = modifier
    )
}
