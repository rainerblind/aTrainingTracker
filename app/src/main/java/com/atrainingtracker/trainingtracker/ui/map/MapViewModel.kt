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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polyline

open class MapViewModel(application: Application) : AndroidViewModel(application) {

    var userMarker: Marker? = null
    var trackPolyline: Polyline? = null

    // Track markers manually to ensure they are removed
    val activeSegmentMarkers = mutableListOf<Marker>()

    // If you also want to manage polylines manually
    val activeSegmentPolylines = mutableListOf<Polyline>()

    // has for the 'static' segments and markers.
    var staticDataHash: Int =0

    // The single MapView instance shared by all fragments
    val sharedMapView: MapView by lazy {
        MapView(application, GoogleMapOptions().apply {
            mapType(GoogleMap.MAP_TYPE_TERRAIN)
        }).apply {
            onCreate(null) // Initialize the lifecycle
            onStart()
            onResume()
        }
    }

    // 2. Persistent state to prevent "Zoom from Space" on every swipe
    // This allows the map to "remember" where it was even when not visible
    var isInitialPositionSet: Boolean = false

    // Clean up when the activity finally dies
    override fun onCleared() {
        super.onCleared()
        sharedMapView.onPause()
        sharedMapView.onStop()
        sharedMapView.onDestroy()
    }
}


class MapViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}