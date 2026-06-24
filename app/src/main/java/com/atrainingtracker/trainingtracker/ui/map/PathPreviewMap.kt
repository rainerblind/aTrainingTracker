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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun PathPreviewMap(
    latLngs: List<LatLng>? = null,
    polyline: String? = null,
    color: Color,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {}
) {
    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }
    var safeLatLngs = latLngs

    if (safeLatLngs == null) {
        if (polyline != null) {

            // Decode the polyline into LatLngs.
            // We 'remember' it so it doesn't re-decode on every recomposition.
            safeLatLngs = remember(polyline) {
                val decoded = PolyUtil.decode(polyline)
                // If the path is huge, simplify it for the preview map to save GPU memory
                if (decoded.size > 100) {
                    PolyUtil.simplify(decoded, 10.0) // 10 meter tolerance
                } else {
                    decoded
                }
            }
        }
        else {
            safeLatLngs = emptyList()
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        // lite mode is disabled.  When enabled, the second and third path of the workouts are not shown.
        // googleMapOptionsFactory = {
        //     com.google.android.gms.maps.GoogleMapOptions().liteMode(true)
        // },
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
        if (safeLatLngs.isNotEmpty()) {

            Polyline(
                points = safeLatLngs,
                color = color,
                width = 8f
            )

            // Auto-zoom to fit the segment whenever pathPoints change
            LaunchedEffect(safeLatLngs, isMapLoaded) {
                if (isMapLoaded && safeLatLngs.isNotEmpty()) {
                    val boundsBuilder = LatLngBounds.Builder()
                    safeLatLngs.forEach { boundsBuilder.include(it) }
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