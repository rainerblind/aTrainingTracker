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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*


class MapFragmentWithTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BANALServiceRepository.getInstance(application)

    // Segments from DB
    private val starredSegments: List<MapSegment> = SegmentsDatabaseManager.getInstance(application).allSegments

    // --- Simplified Reactive State ---
    val mapState: StateFlow<MapState> = repository.currentTrack
        .map { track ->
            // --- Logic for Start Marker ---
            val markers = if (track.isNotEmpty()) {
                listOf(
                    LocationMarker(
                        position = track.first(),
                        iconResId = R.drawable.start_logo_map,
                        title = application.getString(R.string.Start)
                    )
                )
            } else {
                emptyList()
            }

            MapState(
                segments = starredSegments,
                currentTrack = track,
                bearing = 0f,           // Forced zero
                speed = 0f,             // Forced zero
                isFollowMeEnabled = false, // Always off
                markers = markers
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MapState(
                segments = starredSegments,
                currentTrack = emptyList(),
                bearing = 0f,
                speed = 0f,
                isFollowMeEnabled = false,
                markers = emptyList()
            )
        )

    // Exposed for the User Marker specifically (blue dot still moves, camera does not)
    val currentLocation: StateFlow<LatLng?> = repository.currentLocation
}