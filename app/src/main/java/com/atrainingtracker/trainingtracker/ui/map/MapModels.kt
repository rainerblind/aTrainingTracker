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
import com.atrainingtracker.trainingtracker.database.RouteWithPath
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


/**
 * Central point for tuning the map visual hierarchy.
 */
object MapVisualization {
    // User Location (always on top)
    const val USER_LOCATION_Z_INDEX = 100.0f

    // Visual Constants
    const val TRACK_WIDTH = 10f
    const val TRACK_BASE_Z_INDEX = 10.0f
    const val TRACK_OVERLAY_Z_INDEX = 50.0f
    const val TRACK_DOT_GAP = 15f

    const val ROUTE_WIDTH = 10f
    const val ROUTE_BASE_Z_INDEX = 20.0f
    const val ROUTE_OVERLAY_Z_INDEX = 40.0f
    const val ROUTE_UNSELECTED_Z_INDEX = 5.0f
    const val ROUTE_UNSELECTED_WIDTH = 6f
    const val ROUTE_UNSELECTED_ALPHA = 0.3f
    const val ROUTE_DASH_LENGTH = 15f
    const val ROUTE_GAP_LENGTH = 15f

    const val SEGMENT_WIDTH = 10f
    const val SEGMENT_Z_INDEX = 30.0f
    const val SEGMENT_UNSELECTED_ALPHA = 0.3f
}


data class MapState(
    val zoomFocus: MapZoomFocus,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val currentTrack: List<LatLng> = emptyList(),
    val tracks: List<MapTrack> = emptyList(),
    val segments: List<MapSegment> = emptyList(),
    val activeLiveSegmentIds: Set<Long> = emptySet(),
    val routes: List<MapRoute> = emptyList(),
    val markers: List<LocationMarker> = emptyList(),
    val bSportType: BSportType = BSportType.UNKNOWN
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

/* Data class to encapsulate a single point in a track */
data class PathPoint(
    val distance: Double,
    val latLng: LatLng,
    val altitude: Double
)

/**
 * Base interface for anything that can be drawn as a path on the map.
 */
interface MappablePath {
    val path: List<PathPoint>
    val color: Color
    val width: Float
    val zIndex: Float
}

/**
 * Encapsulates a single track polyline with its metadata.
 */
data class MapTrack(
    val id: Long,
    val type: TrackType,
    override val path: List<PathPoint>,
    val isVisible: Boolean = true
) : MappablePath
{
    override val zIndex: Float get() = MapVisualization.TRACK_BASE_Z_INDEX
    override val color: Color get() = type.color
    override val width: Float get() = MapVisualization.TRACK_WIDTH
    
    val overlayZIndex: Float get() = MapVisualization.TRACK_OVERLAY_Z_INDEX
    val pattern: List<com.google.android.gms.maps.model.PatternItem> 
        get() = listOf(com.google.android.gms.maps.model.Dot(), com.google.android.gms.maps.model.Gap(MapVisualization.TRACK_DOT_GAP))
}

data class MapSegment(
    val stravaId: Long,
    val name: String,
    val bSportType: BSportType,
    override val path: List<PathPoint>,
    val showStartAndFinishText: Boolean = true
) : MappablePath {
    override val color: Color get() = com.atrainingtracker.trainingtracker.ui.theme.StravaOrange
    override val width: Float get() = MapVisualization.SEGMENT_WIDTH
    override val zIndex: Float get() = MapVisualization.SEGMENT_Z_INDEX
}

data class MapRoute(
    val id: Long,
    val name: String,
    val isSelected: Boolean,
    val bSportType: BSportType,
    override val path: List<PathPoint>
) : MappablePath {
    override val color: Color get() = if (isSelected) com.atrainingtracker.trainingtracker.ui.theme.RouteColorSelected else com.atrainingtracker.trainingtracker.ui.theme.RouteColorUnselected
    override val width: Float get() = if (isSelected) MapVisualization.ROUTE_WIDTH else MapVisualization.ROUTE_UNSELECTED_WIDTH
    override val zIndex: Float get() = if (isSelected) MapVisualization.ROUTE_BASE_Z_INDEX else MapVisualization.ROUTE_UNSELECTED_Z_INDEX

    val overlayZIndex: Float get() = MapVisualization.ROUTE_OVERLAY_Z_INDEX
    val pattern: List<com.google.android.gms.maps.model.PatternItem> 
        get() = listOf(com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH), com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH))
}
/**
 * Extension function to convert a Database Route (RouteWithPath)
 * into a Map-ready Route (MapRoute).
 */
fun RouteWithPath.toMapRoute(): MapRoute {

    return MapRoute(
        id = this.summary.id,
        name = this.summary.name,
        isSelected = this.summary.isSelected,
        path = this.path,
        bSportType = this.summary.bSportType
    )
}

enum class MapZoomFocus {
    TRACK_AND_MARKERS,
    LOCAL_SEGMENTS,
    LOCAL_ROUTES,
    FOLLOW_ME
}
