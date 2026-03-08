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

package com.atrainingtracker.trainingtracker.ui.components.map

import android.app.Activity
import android.util.Log
import android.view.View
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.fragments.mapFragments.Roughness
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapHelper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback

// 1. Define the types of content this component can display
enum class MapContentType {
    WORKOUT_TRACK,
    SEGMENT_TRACK
}

class MapComponent(
    private val mapView: MapView,
    private val activity: Activity,
    // 2. The listener now passes back the ID, regardless of type
    private val clickListener: (dataId: Long) -> Unit
) : OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private var currentDataId: Long = -1L
    private var currentContentType: MapContentType? = null // To know what to draw

    private val TAG = "MapComponent"
    private val DEBUG = TrainingApplication.getDebug(true)

    init {
        mapView.onCreate(null)
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        MapsInitializer.initialize(activity.applicationContext)
        this.googleMap = map
        // If bind() was called before the map was ready, draw the content now
        if (currentDataId != -1L) {
            showContentOnMap()
        }
    }

    /**
     * Binds data to this map component and updates the map.
     */
    fun bind(dataId: Long, contentType: MapContentType) {
        currentDataId = dataId
        currentContentType = contentType
        setVisible(true)

        // If map is already ready, draw the content. Otherwise, onMapReady will handle it.
        googleMap?.let {
            showContentOnMap()
        }
    }

    fun setVisible(isVisible: Boolean) {
        mapView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    // 3. This is our new, generalized drawing method
    private fun showContentOnMap() {
        val map = googleMap ?: run {
            Log.e(TAG, "showContentOnMap called but GoogleMap is null.")
            setVisible(false)
            return
        }

        // Configure map UI settings
        map.uiSettings.isMapToolbarEnabled = false
        map.setOnMapClickListener { clickListener(currentDataId) }

        // 4. Use a 'when' block to decide which helper to call
        when (currentContentType) {
            MapContentType.WORKOUT_TRACK -> showWorkoutTrack(map)
            MapContentType.SEGMENT_TRACK -> showSegmentTrack(map)
            null -> Log.e(TAG, "Content type not set, cannot draw on map.")
        }
    }

    // This was the original showTrackOnMap method, now private
    private fun showWorkoutTrack(map: GoogleMap) {
        if (DEBUG) Log.i(TAG, "showWorkoutTrack: workoutId=$currentDataId")
        (activity.application as TrainingApplication).trackOnMapHelper.showTrackOnMap(
            activity,
            mapView,
            map,
            currentDataId,
            Roughness.MEDIUM,
            TrackOnMapHelper.TrackType.BEST,
            true,
            false
        )
    }

    // This is the new method for showing segments
    private fun showSegmentTrack(map: GoogleMap) {
        if (DEBUG) Log.i(TAG, "showSegmentTrack: segmentId=$currentDataId")
        (activity.application as TrainingApplication).segmentOnMapHelper.showSegmentOnMap(
            activity,
            mapView,
            map,
            currentDataId,
            Roughness.ALL,
            true,
            false
        )
    }
}