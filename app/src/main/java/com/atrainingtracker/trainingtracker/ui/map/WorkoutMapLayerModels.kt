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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType

/**
 * Toggleable spatial overlay layers available in the full-screen workout map mode (`REQ-MAP-069.3`).
 */
enum class WorkoutMapSpatialLayer {
    PRIMARY_TRACK,
    SEGMENTS,
    ROUTES,
    MARKERS,
    CLIMBS,
    LAP_HIGHLIGHTS;

    companion object {
        /**
         * Sensible default spatial overlays for fresh installations or unconfigured preferences (`REQ-MAP-069.3`).
         * Excludes [CLIMBS] by default (mirroring [GeneralMapLayer.DEFAULT_ENABLED]) to prevent visual clutter
         * until explicitly toggled on by the athlete.
         */
        @JvmField
        val DEFAULT_ENABLED: Set<WorkoutMapSpatialLayer> = setOf(
            PRIMARY_TRACK,
            SEGMENTS,
            ROUTES,
            MARKERS,
            LAP_HIGHLIGHTS
        )
    }
}

/**
 * Mutually exclusive base-map tile styles available in the full-screen workout map mode (`REQ-MAP-069.3`).
 */
enum class WorkoutBaseMapType(val mapType: MapType) {
    NORMAL(MapType.NORMAL),
    TERRAIN(MapType.TERRAIN),
    SATELLITE(MapType.SATELLITE),
    HYBRID(MapType.HYBRID);

    companion object {
        val DEFAULT: WorkoutBaseMapType = TERRAIN

        /**
         * Returns the theme-appropriate default base-map style when no explicit user override is
         * persisted (`REQ-MAP-021`, `REQ-MAP-069.3`): [NORMAL] (with `DarkMapStyle`) in dark/AMOLED
         * mode to prevent bright daytime glare, and [TERRAIN] in light mode.
         */
        fun defaultForTheme(isDark: Boolean): WorkoutBaseMapType =
            if (isDark) NORMAL else TERRAIN
    }
}

/**
 * Persisted configuration for full-screen workout map spatial overlays and base-map type (`REQ-MAP-069.3`).
 *
 * When [baseMapType] is `null` (unset), [effectiveBaseMapType] follows the active theme default
 * ([WorkoutBaseMapType.defaultForTheme]). Once the athlete explicitly selects a base map type,
 * that explicit choice takes precedence in both light and dark modes.
 */
data class WorkoutMapLayerConfig(
    val enabledLayers: Set<WorkoutMapSpatialLayer> = WorkoutMapSpatialLayer.DEFAULT_ENABLED,
    val baseMapType: WorkoutBaseMapType? = null
) {
    /**
     * Resolves the active [WorkoutBaseMapType] respecting explicit persisted selections and
     * falling back to the theme-appropriate default when [baseMapType] is unset (`REQ-MAP-021`).
     */
    fun effectiveBaseMapType(isDark: Boolean): WorkoutBaseMapType =
        baseMapType ?: WorkoutBaseMapType.defaultForTheme(isDark)

    /**
     * Returns true when any spatial layer, base-map type, or technical track differs from defaults,
     * used to highlight the floating Layers action button (`REQ-MAP-069.3`).
     */
    fun hasCustomOverrides(
        enabledTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
        showTechnicalTracks: Boolean = false,
        isDark: Boolean = false
    ): Boolean {
        val hasExtraTechnicalTracks = showTechnicalTracks && enabledTrackTypes.any { it != TrackType.BEST }
        val effectiveBase = effectiveBaseMapType(isDark)
        return enabledLayers != WorkoutMapSpatialLayer.DEFAULT_ENABLED ||
            effectiveBase != WorkoutBaseMapType.defaultForTheme(isDark) ||
            hasExtraTechnicalTracks
    }
}

/**
 * Deterministic resolved layer payload rendered inside full-screen workout map mode (`REQ-MAP-069.3`).
 */
data class ResolvedFullscreenWorkoutMapContent(
    val tracks: List<MapTrack>,
    val segments: List<MapSegment>,
    val routes: List<MapRoute>,
    val markers: List<LocationMarker>,
    val knownLocations: List<KnownLocationItem>,
    val climbs: List<Climb>,
    val lapHighlight: List<LatLng>
)

/**
 * Pure, unit-testable resolver filtering all full-screen workout map overlays according to the
 * persisted [WorkoutMapLayerConfig] and technical [enabledTrackTypes] (`REQ-MAP-069.3`).
 */
fun resolveFullscreenWorkoutMapContent(
    tracks: List<MapTrack>,
    segments: List<MapSegment>,
    routes: List<MapRoute>,
    markers: List<LocationMarker>,
    knownLocations: List<KnownLocationItem>,
    climbs: List<Climb>,
    lapSegment: List<LatLng>,
    lapStartMarkerTitle: String,
    lapEndMarkerTitle: String,
    layerConfig: WorkoutMapLayerConfig,
    enabledTrackTypes: Set<TrackType>,
    showTechnicalTracks: Boolean
): ResolvedFullscreenWorkoutMapContent {
    val enabled = layerConfig.enabledLayers

    val primaryTrack = tracks.find { it.type == TrackType.BEST } ?: tracks.firstOrNull()
    val addedPrimaryTrack = if (WorkoutMapSpatialLayer.PRIMARY_TRACK in enabled) primaryTrack else null
    val resolvedTracks = buildList {
        if (addedPrimaryTrack != null) {
            add(addedPrimaryTrack)
        }
        if (showTechnicalTracks) {
            tracks.forEach { track ->
                val alreadyIncluded = addedPrimaryTrack != null && track === addedPrimaryTrack
                if (!alreadyIncluded && track.type != TrackType.BEST && track.type in enabledTrackTypes) {
                    add(track)
                }
            }
        }
    }

    val resolvedSegments = if (WorkoutMapSpatialLayer.SEGMENTS in enabled) segments else emptyList()
    val resolvedRoutes = if (WorkoutMapSpatialLayer.ROUTES in enabled) routes else emptyList()
    val resolvedKnownLocations = if (WorkoutMapSpatialLayer.MARKERS in enabled) knownLocations else emptyList()
    val resolvedClimbs = if (WorkoutMapSpatialLayer.CLIMBS in enabled) climbs else emptyList()

    val showLapHighlight = WorkoutMapSpatialLayer.LAP_HIGHLIGHTS in enabled && lapSegment.isNotEmpty()
    val resolvedLapHighlight = if (showLapHighlight) lapSegment else emptyList()

    val resolvedMarkers = buildList {
        if (WorkoutMapSpatialLayer.MARKERS in enabled) {
            addAll(markers)
        }
        if (showLapHighlight) {
            lapSegment.firstOrNull()?.let { startPoint ->
                add(
                    LocationMarker(
                        position = startPoint,
                        iconResId = R.drawable.control_start,
                        title = lapStartMarkerTitle
                    )
                )
            }
            lapSegment.lastOrNull()?.let { endPoint ->
                add(
                    LocationMarker(
                        position = endPoint,
                        iconResId = R.drawable.control_stop,
                        title = lapEndMarkerTitle
                    )
                )
            }
        }
    }

    return ResolvedFullscreenWorkoutMapContent(
        tracks = resolvedTracks,
        segments = resolvedSegments,
        routes = resolvedRoutes,
        markers = resolvedMarkers,
        knownLocations = resolvedKnownLocations,
        climbs = resolvedClimbs,
        lapHighlight = resolvedLapHighlight
    )
}
