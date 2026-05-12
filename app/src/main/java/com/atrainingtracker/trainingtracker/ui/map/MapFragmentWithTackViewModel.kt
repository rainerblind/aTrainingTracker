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
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*

class MapFragmentWithTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val banalRepository = BANALServiceRepository.getInstance(application)
    private val segmentsRepository = SegmentsRepository.getInstance(application)

    val liveSegments = segmentsRepository.allSegmentsWithPath

    val mapState: StateFlow<MapState> = combine(
        banalRepository.currentTrack,
        segmentsRepository.allSegmentsWithPath // Observe the repository instead of a one-time DB hit
    ) { currentTrack, liveSegments ->

        // Logic for Start Marker
        val markers = if (currentTrack.isNotEmpty()) {
            listOf(
                LocationMarker(
                    position = currentTrack.first(),
                    iconResId = R.drawable.start_logo_map,
                    title = application.getString(R.string.Start)
                )
            )
        } else {
            emptyList()
        }

        MapState(
            segments = liveSegments.map { liveSegment ->
                MapSegment(
                    stravaId = liveSegment.summary.stravaId,
                    name = liveSegment.summary.name,
                    bSportType = liveSegment.summary.bSportType,
                    path = liveSegment.path,
                    showStartAndFinishText = true
                )
            },
            currentTrack = currentTrack,
            bearing = 0f,
            speed = 0f,
            isFollowMeEnabled = false,
            markers = markers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapState()
    )

    val currentLocation: StateFlow<LatLng?> = banalRepository.currentLocation
}