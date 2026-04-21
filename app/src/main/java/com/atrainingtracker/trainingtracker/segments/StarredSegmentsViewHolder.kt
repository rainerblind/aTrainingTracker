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

package com.atrainingtracker.trainingtracker.segments

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.segments.SimpleSegmentMapViewModel
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

class StarredSegmentViewHolder(
    val rowView: View,
    activity: Activity,
    private val onSegmentClick: (Long) -> Unit
) {
    var segmentId: Long = -1

    // Text Views
    val tvClimbCategory: TextView = rowView.findViewById(R.id.textViewCategoryChip)
    val tvName: TextView = rowView.findViewById(R.id.textViewSegmentName)
    val layoutPr: View = rowView.findViewById(R.id.layout_pr)
    val tvPrTime: TextView = rowView.findViewById(R.id.textViewPrTime)
    val tvCity: TextView = rowView.findViewById(R.id.textViewCity)
    val tvDistance: TextView = rowView.findViewById(R.id.textViewDistance)
    val tvAverageGrade: TextView = rowView.findViewById(R.id.textViewAvgGrade)
    val tvMaxGrade: TextView = rowView.findViewById(R.id.textViewMaxGrade)
    val tvElevationGain: TextView = rowView.findViewById(R.id.textViewElevationGain)
    val tvElevationMin: TextView = rowView.findViewById(R.id.textViewElevationMin)
    val tvElevationMax: TextView = rowView.findViewById(R.id.textViewElevationMax)

    val mapComposeView: ComposeView? = rowView.findViewById(R.id.map_compose_segment)
    val elevationProfileComposeView: ComposeView? = rowView.findViewById(R.id.compose_elevation_profile)

    val viewModel: SimpleSegmentMapViewModel = SimpleSegmentMapViewModel(activity.application)

    init {
        mapComposeView?.setContent {
            ATrainingTrackerTheme {
                val state by viewModel.mapState.collectAsState()
                val segment = state.segments.firstOrNull()

                // 1. Create a CameraPositionState that we can control
                val cameraPositionState =
                    rememberCameraPositionState()

                // 2. Use the base GoogleMap for maximum layout control
                GoogleMap(
                    modifier = Modifier
                        .fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    // Disable all UI overlays that might shrink the map content
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = false,
                        mapToolbarEnabled = false,
                        myLocationButtonEnabled = false,
                        scrollGesturesEnabled = false, // Static look for list rows
                        zoomGesturesEnabled = false
                    ),
                    properties = MapProperties(mapType = MapType.TERRAIN),
                    onMapClick = {
                        if (segmentId != -1L) {
                            onSegmentClick(segmentId)
                        }
                    }
                ) {
                    // 3. Draw the segment path manually
                    segment?.let { seg ->
                        val segPoints = seg.path.map {it.latLng}
                        Polyline(
                            points = segPoints,
                            color = StravaOrange,
                            width = 8f
                        )

                        // 4. Update camera once the map is ready to fit the segment
                        LaunchedEffect(seg.path) {
                            if (seg.path.isNotEmpty()) {
                                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                                segPoints.forEach{ boundsBuilder.include(it) }
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
            }
        }

        elevationProfileComposeView?.setContent {
            val state by viewModel.mapState.collectAsState()
            val segmentPoints = state.segments.firstOrNull()?.path ?: emptyList()

            ATrainingTrackerTheme {
                // Wrap in a Box to make the entire profile area clickable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (segmentId != -1L) {
                                onSegmentClick(segmentId)
                            }
                        }
                ) {
                    ElevationProfile(
                        pathPoints = segmentPoints,
                        currentDistance = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    fun bindSegment(id: Long) {
        this.segmentId = id
        // Trigger data load through the repository/cache
        viewModel.loadSegment(id, false)
    }
}