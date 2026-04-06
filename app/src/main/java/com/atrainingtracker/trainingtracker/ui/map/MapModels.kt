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

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng

enum class TrackType(
    val color: Color,
    val zIndex: Float,
    val sourceSuffix: String?
) {
    BEST(Color.Blue, 5f,null),
    GPS(Color.Green, 4f, "gps"),
    FUSED(Color.Yellow, 3f, "google_fused"),
    NETWORK(Color.Magenta, 2f, "network");

    /**
     * Returns the database column name for Latitude for this specific source.
     */
    val latitudeColumn: String
        get() = if (sourceSuffix != null) {
            "${SensorType.LATITUDE.name}_$sourceSuffix"
        } else {
            SensorType.LATITUDE.name
        }

    /**
     * Returns the database column name for Longitude for this specific source.
     */
    val longitudeColumn: String
        get() = if (sourceSuffix != null) {
            "${SensorType.LONGITUDE.name}_$sourceSuffix"
        } else {
            SensorType.LONGITUDE.name
        }
}


data class MapState(
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val isFollowMeEnabled: Boolean = true,
    val currentTrack: List<LatLng> = emptyList(),
    val tracks: List<MapTrack> = emptyList(),
    val segments: List<MapSegment> = emptyList(),
    val markers: List<LocationMarker> = emptyList()
)

data class LocationMarker(
    val position: LatLng,
    @DrawableRes val iconResId: Int,
    val title: String? = null,
    val rotation: Float = 0f,
    val flat: Boolean = false,
    val anchor: Offset = Offset(0.5f, 0.5f),
    val iconDescriptor: BitmapDescriptor? = null
)

/**
 * Encapsulates a single track polyline with its metadata.
 */
data class MapTrack(
    val id: Long,
    val type: TrackType,
    val path: List<LatLng>,
    val isVisible: Boolean = true
)
{
    // Helper to access the zIndex defined in the TrackType
    val zIndex: Float get() = type.zIndex
    val color: Color get() = type.color
}

data class MapSegment(
    val id: Long,
    val name: String,
    val bSportType: BSportType,
    val path: List<LatLng>,
)

enum class Roughness(val stepSize: Int) {
    ALL(1),
    MEDIUM(60),  // one minute
    LOW(5*60)   // 5 minutes
}


