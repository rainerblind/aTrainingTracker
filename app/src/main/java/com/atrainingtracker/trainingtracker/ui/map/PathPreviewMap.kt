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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun PathPreviewMap(
    path: MappablePath?,
    modifier: Modifier = Modifier,
    start: LatLng? = null,
    end: LatLng? = null,
    apex: LatLng? = null,
    onMapClick: () -> Unit = {}
) {
    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
        onMapLoaded = { isMapLoaded = true },
        onMapClick = { onMapClick() }
    ) {
        if (path != null && path.latLngs.isNotEmpty()) {

            Polyline(
                points = path.latLngs,
                color = path.color,
                width = 8f
            )

            // --- START, END, APEX MARKERS (SCRUM-233) ---
            val actualStart = start ?: path.latLngs.firstOrNull()
            val actualEnd = end ?: path.latLngs.lastOrNull()
            val actualApex = apex

            actualStart?.let {
                Marker(
                    state = remember(it) { MarkerState(position = it) },
                    icon = remember { createSensorMarker(context, R.drawable.control_start, TTColor.StartPoint) }
                )
            }
            actualEnd?.let {
                Marker(
                    state = remember(it) { MarkerState(position = it) },
                    icon = remember { createSensorMarker(context, R.drawable.control_stop, TTColor.EndPoint) }
                )
            }
            actualApex?.let {
                Marker(
                    state = remember(it) { MarkerState(position = it) },
                    icon = remember { createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint) }
                )
            }

            // Auto-zoom to fit the segment whenever pathPoints change
            LaunchedEffect(path, actualStart, actualEnd, actualApex, isMapLoaded) {
                if (isMapLoaded && path.latLngs.isNotEmpty()) {
                    val boundsBuilder = LatLngBounds.Builder()
                    path.latLngs.forEach { boundsBuilder.include(it) }
                    actualStart?.let { boundsBuilder.include(it) }
                    actualEnd?.let { boundsBuilder.include(it) }
                    actualApex?.let { boundsBuilder.include(it) }

                    try {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(
                                boundsBuilder.build(),
                                20 // padding in px
                            )
                        )
                    } catch (e: Exception) {
                        // Map not laid out yet or size is 0
                    }
                }
            }
        }
    }
}
