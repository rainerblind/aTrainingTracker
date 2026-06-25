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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MapFragmentUIState(
    val segments: List<MapSegment> = emptyList(),
    val routes: List<MapRoute> = emptyList(),
    val markers: List<LocationMarker> = emptyList(),
    val currentTrack: List<LatLng> = emptyList(),
    val bSportType: BSportType = BSportType.UNKNOWN
)

class MapFragmentWithTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val banalRepository = BANALServiceRepository.getInstance(application)
    private val segmentsRepository = SegmentsRepository.getInstance(application)
    private val routesRepository = RoutesRepository.getInstance(application)

    val liveSegments = segmentsRepository.allSegmentsWithPath
    val allRoutes = routesRepository.allRoutes

    val uiState: StateFlow<MapFragmentUIState> = combine(
        banalRepository.bSportType,
        banalRepository.currentTrack,
        segmentsRepository.allSegmentsWithPath,
        routesRepository.allRoutes
    ) { bSportType, currentTrack, liveSegments, allRoutes ->

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

        MapFragmentUIState(
            segments = liveSegments.map { liveSegment ->
                MapSegment(
                    stravaId = liveSegment.summary.stravaId,
                    name = liveSegment.summary.name,
                    bSportType = liveSegment.summary.bSportType,
                    path = liveSegment.path,
                    showStartAndFinishText = true
                )
            },
            routes = allRoutes.map { it.toMapRoute() },
            bSportType = bSportType,
            currentTrack = currentTrack,
            markers = markers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapFragmentUIState()
    )

    val currentLocation: StateFlow<LatLng?> = banalRepository.currentLocation

    fun onToggleRoute(id: Long, selected: Boolean) {
        viewModelScope.launch {
            routesRepository.toggleRouteSelection(routeId = id, isSelected = selected)
        }
    }
}
