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

package com.atrainingtracker.trainingtracker.ui.segments.segmentlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun SegmentOnMap(
    pathPoints: List<PathPoint>,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {}
) {
    val cameraPositionState = rememberCameraPositionState()

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = false, // Static look for list rows
            zoomGesturesEnabled = false
        ),
        properties = MapProperties(mapType = MapType.TERRAIN),
        onMapClick = { onMapClick() }
    ) {
        if (pathPoints.isNotEmpty()) {
            val latLngs = pathPoints.map { it.latLng }

            Polyline(
                points = latLngs,
                color = StravaOrange,
                width = 8f
            )

            // Auto-zoom to fit the segment whenever pathPoints change
            LaunchedEffect(pathPoints) {
                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                latLngs.forEach { boundsBuilder.include(it) }
                cameraPositionState.move(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(
                        boundsBuilder.build(),
                        20 // padding in px
                    )
                )
            }
        }
    }
}