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

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying full-screen workout map layer persistence, pure spatial/technical overlay
 * filtering, and explicit base-map [DarkMapStyle] resolution (`REQ-MAP-069.3`, `TST-MAP-072.1`, `ATT-9029`).
 */
// Linked test specs: TST-MAP-072, TST-MAP-072.1
class WorkoutMapLayerPreferencesTest {

    private fun createPreferenceManager(
        dataStore: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore()
    ): Pair<MyPreferenceManager, InMemoryPreferencesDataStore> {
        val mockContext = mockk<Context>(relaxed = true)
        return MyPreferenceManager.createForTesting(mockContext, dataStore) to dataStore
    }

    @Test
    fun workoutMapLayerConfigFlow_returnsThemeAwareDefaultsWhenUnset() = runTest {
        val (prefManager, _) = createPreferenceManager()

        val config = prefManager.workoutMapLayerConfigFlow.first()

        assertEquals(WorkoutMapSpatialLayer.DEFAULT_ENABLED, config.enabledLayers)
        assertTrue(WorkoutMapSpatialLayer.PRIMARY_TRACK in config.enabledLayers)
        assertTrue(WorkoutMapSpatialLayer.SEGMENTS in config.enabledLayers)
        assertTrue(WorkoutMapSpatialLayer.ROUTES in config.enabledLayers)
        assertTrue(WorkoutMapSpatialLayer.MARKERS in config.enabledLayers)
        assertTrue(WorkoutMapSpatialLayer.LAP_HIGHLIGHTS in config.enabledLayers)
        assertFalse(WorkoutMapSpatialLayer.CLIMBS in config.enabledLayers)
        assertNull("Unset baseMapType must remain null to follow active theme default", config.baseMapType)
        assertEquals(WorkoutBaseMapType.TERRAIN, config.effectiveBaseMapType(isDark = false))
        assertEquals(WorkoutBaseMapType.NORMAL, config.effectiveBaseMapType(isDark = true))
    }

    @Test
    fun toggleAndSetSpatialLayers_persistAcrossReadsIncludingEmptySet() = runTest {
        val (prefManager, _) = createPreferenceManager()

        // Enable climbs (off by default) and disable segments (on by default)
        prefManager.toggleWorkoutMapSpatialLayer(WorkoutMapSpatialLayer.CLIMBS)
        prefManager.toggleWorkoutMapSpatialLayer(WorkoutMapSpatialLayer.SEGMENTS)

        val updated = prefManager.workoutMapLayerConfigFlow.first()
        assertTrue(WorkoutMapSpatialLayer.CLIMBS in updated.enabledLayers)
        assertFalse(WorkoutMapSpatialLayer.SEGMENTS in updated.enabledLayers)

        // Explicitly disable every spatial layer and confirm emptySet persists (never silently resets to defaults)
        WorkoutMapSpatialLayer.entries.forEach { layer ->
            prefManager.setWorkoutMapSpatialLayerEnabled(layer, enabled = false)
        }
        val emptyConfig = prefManager.workoutMapLayerConfigFlow.first()
        assertTrue("Disabling all layers must persist emptySet", emptyConfig.enabledLayers.isEmpty())
    }

    @Test
    fun setWorkoutMapBaseType_persistsAllFourSupportedBaseTypes() = runTest {
        val (prefManager, _) = createPreferenceManager()

        WorkoutBaseMapType.entries.forEach { baseType ->
            prefManager.setWorkoutMapBaseType(baseType)
            assertEquals(baseType, prefManager.workoutMapLayerConfigFlow.first().baseMapType)
        }
    }

    @Test
    fun workoutMapLayerConfigFlow_safelyIgnoresUnknownFutureEnumTokens() = runTest {
        val (prefManager, dataStore) = createPreferenceManager()

        dataStore.edit { prefs ->
            prefs[MyPreferenceManager.FULLSCREEN_WORKOUT_MAP_ENABLED_LAYERS] = setOf(
                WorkoutMapSpatialLayer.PRIMARY_TRACK.name,
                "FUTURE_HEATMAP_V9"
            )
            prefs[MyPreferenceManager.FULLSCREEN_WORKOUT_MAP_BASE_TYPE] = "FUTURE_3D_TOPO"
        }

        val config = prefManager.workoutMapLayerConfigFlow.first()
        assertEquals(setOf(WorkoutMapSpatialLayer.PRIMARY_TRACK), config.enabledLayers)
        assertNull("Unknown future base map token must fall back to theme-aware default", config.baseMapType)
        assertEquals(WorkoutBaseMapType.TERRAIN, config.effectiveBaseMapType(isDark = false))
        assertEquals(WorkoutBaseMapType.NORMAL, config.effectiveBaseMapType(isDark = true))
    }

    @Test
    fun resolveFullscreenWorkoutMapContent_filtersSpatialAndTechnicalLayersAccurately() {
        val p1 = LatLng(48.1371, 11.5754)
        val p2 = LatLng(48.1390, 11.5780)
        val bestTrack = MapTrack(1L, TrackType.BEST, BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(250.0, p2)))
        val gpsTrack = MapTrack(2L, TrackType.GPS, BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(250.0, p2)))
        val fusedTrack = MapTrack(3L, TrackType.FUSED, BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(250.0, p2)))
        val segment = MapSegment(10L, "Sprint Hill", BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(200.0, p2)))
        val route = MapRoute(
            id = 20L,
            name = "Sunday Loop",
            isSelected = true,
            bSportType = BSportType.BIKE,
            path = listOf(PathPoint(0.0, p1), PathPoint(250.0, p2))
        )
        val marker = LocationMarker(position = p1, iconResId = R.drawable.control_start, title = "Start")
        val knownLocation = KnownLocationItem(
            id = 30L,
            name = "Home",
            altitude = 520.0,
            radius = 50,
            latLng = p1,
            hitCount = 12,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED
        )
        val climb = Climb(
            id = 40L,
            name = "Col du Test",
            routeId = 20L,
            startLat = p1.latitude,
            startLng = p1.longitude,
            endLat = p2.latitude,
            endLng = p2.longitude,
            distanceMeters = 250.0,
            elevationGainMeters = 25.0,
            avgGradePercent = 10.0,
            maxGradePercent = 12.0,
            category = ClimbCategory.CAT_4,
            pathPoints = listOf(PathPoint(0.0, p1, altitude = 520.0), PathPoint(250.0, p2, altitude = 545.0))
        )
        val lapSegment = listOf(p1, p2)

        // Default config: Primary + GPS technical track enabled, Climbs OFF
        val defaultResolved = resolveFullscreenWorkoutMapContent(
            tracks = listOf(bestTrack, gpsTrack, fusedTrack),
            segments = listOf(segment),
            routes = listOf(route),
            markers = listOf(marker),
            knownLocations = listOf(knownLocation),
            climbs = listOf(climb),
            lapSegment = lapSegment,
            lapStartMarkerTitle = "Lap Start",
            lapEndMarkerTitle = "Lap End",
            layerConfig = WorkoutMapLayerConfig(),
            enabledTrackTypes = setOf(TrackType.BEST, TrackType.GPS),
            showTechnicalTracks = true
        )
        assertEquals(listOf(TrackType.BEST, TrackType.GPS), defaultResolved.tracks.map { it.type })
        assertEquals(1, defaultResolved.segments.size)
        assertEquals(1, defaultResolved.routes.size)
        assertEquals(1, defaultResolved.knownLocations.size)
        assertTrue("Climbs must be hidden when CLIMBS layer is disabled", defaultResolved.climbs.isEmpty())
        assertEquals(2, defaultResolved.lapHighlight.size)
        assertEquals(3, defaultResolved.markers.size) // 1 workout marker + 2 lap boundary markers

        // Toggle PRIMARY_TRACK, SEGMENTS, ROUTES, MARKERS, LAP_HIGHLIGHTS off; enable CLIMBS + FUSED technical track
        val customResolved = resolveFullscreenWorkoutMapContent(
            tracks = listOf(bestTrack, gpsTrack, fusedTrack),
            segments = listOf(segment),
            routes = listOf(route),
            markers = listOf(marker),
            knownLocations = listOf(knownLocation),
            climbs = listOf(climb),
            lapSegment = lapSegment,
            lapStartMarkerTitle = "Lap Start",
            lapEndMarkerTitle = "Lap End",
            layerConfig = WorkoutMapLayerConfig(enabledLayers = setOf(WorkoutMapSpatialLayer.CLIMBS)),
            enabledTrackTypes = setOf(TrackType.FUSED),
            showTechnicalTracks = true
        )
        assertEquals(listOf(TrackType.FUSED), customResolved.tracks.map { it.type })
        assertTrue(customResolved.segments.isEmpty())
        assertTrue(customResolved.routes.isEmpty())
        assertTrue(customResolved.markers.isEmpty())
        assertTrue(customResolved.knownLocations.isEmpty())
        assertTrue(customResolved.lapHighlight.isEmpty())
        assertEquals(1, customResolved.climbs.size)
    }

    @Test
    fun resolveExplicitMapProperties_appliesDarkStyleOnlyOnDarkNormal() {
        val mockDarkOptions = mockk<MapStyleOptions>()

        val darkNormal = DarkMapStyle.resolveExplicitMapProperties(MapType.NORMAL, isDark = true, darkMapStyleOptions = mockDarkOptions)
        assertEquals(MapType.NORMAL, darkNormal.mapType)
        assertSame(mockDarkOptions, darkNormal.mapStyleOptions)

        val lightNormal = DarkMapStyle.resolveExplicitMapProperties(MapType.NORMAL, isDark = false, darkMapStyleOptions = mockDarkOptions)
        assertEquals(MapType.NORMAL, lightNormal.mapType)
        assertNull(lightNormal.mapStyleOptions)

        listOf(MapType.TERRAIN, MapType.SATELLITE, MapType.HYBRID).forEach { type ->
            val darkProps = DarkMapStyle.resolveExplicitMapProperties(type, isDark = true, darkMapStyleOptions = mockDarkOptions)
            assertEquals(type, darkProps.mapType)
            assertNull("Non-NORMAL map types must not carry dark vector style overrides", darkProps.mapStyleOptions)

            val lightProps = DarkMapStyle.resolveExplicitMapProperties(type, isDark = false, darkMapStyleOptions = mockDarkOptions)
            assertEquals(type, lightProps.mapType)
            assertNull(lightProps.mapStyleOptions)
        }
    }

    @Test
    fun hasCustomOverrides_reflectsThemeAwareDefaultsAndExplicitOverrides() {
        val defaultConfig = WorkoutMapLayerConfig()
        assertFalse(defaultConfig.hasCustomOverrides(setOf(TrackType.BEST), showTechnicalTracks = true, isDark = false))
        assertFalse(defaultConfig.hasCustomOverrides(setOf(TrackType.BEST), showTechnicalTracks = true, isDark = true))
        assertTrue(defaultConfig.hasCustomOverrides(setOf(TrackType.BEST, TrackType.GPS), showTechnicalTracks = true))
        assertFalse(defaultConfig.hasCustomOverrides(setOf(TrackType.BEST, TrackType.GPS), showTechnicalTracks = false))
        assertTrue(defaultConfig.copy(baseMapType = WorkoutBaseMapType.SATELLITE).hasCustomOverrides(isDark = false))
        assertTrue(defaultConfig.copy(baseMapType = WorkoutBaseMapType.TERRAIN).hasCustomOverrides(isDark = true))
        assertFalse(defaultConfig.copy(baseMapType = WorkoutBaseMapType.NORMAL).hasCustomOverrides(isDark = true))
        assertTrue(
            defaultConfig.copy(enabledLayers = WorkoutMapSpatialLayer.DEFAULT_ENABLED + WorkoutMapSpatialLayer.CLIMBS)
                .hasCustomOverrides()
        )
    }

    @Test
    fun resolveFullscreenWorkoutMapContent_whenBestAbsentAllowsTechnicalTrackToggleWithoutDuplication() {
        val p1 = LatLng(48.1371, 11.5754)
        val p2 = LatLng(48.1390, 11.5780)
        val gpsTrack = MapTrack(2L, TrackType.GPS, BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(250.0, p2)))
        val fusedTrack = MapTrack(3L, TrackType.FUSED, BSportType.BIKE, listOf(PathPoint(0.0, p1), PathPoint(250.0, p2)))

        // Case 1: PRIMARY_TRACK ON + GPS technical track ON -> GPS rendered once (no duplicate)
        val withPrimaryAndGps = resolveFullscreenWorkoutMapContent(
            tracks = listOf(gpsTrack, fusedTrack),
            segments = emptyList(),
            routes = emptyList(),
            markers = emptyList(),
            knownLocations = emptyList(),
            climbs = emptyList(),
            lapSegment = emptyList(),
            lapStartMarkerTitle = "Lap Start",
            lapEndMarkerTitle = "Lap End",
            layerConfig = WorkoutMapLayerConfig(enabledLayers = setOf(WorkoutMapSpatialLayer.PRIMARY_TRACK)),
            enabledTrackTypes = setOf(TrackType.GPS),
            showTechnicalTracks = true
        )
        assertEquals(listOf(TrackType.GPS), withPrimaryAndGps.tracks.map { it.type })

        // Case 2: PRIMARY_TRACK OFF + GPS technical track ON -> GPS STILL rendered (checkbox is not dead!)
        val withoutPrimaryWithGps = resolveFullscreenWorkoutMapContent(
            tracks = listOf(gpsTrack, fusedTrack),
            segments = emptyList(),
            routes = emptyList(),
            markers = emptyList(),
            knownLocations = emptyList(),
            climbs = emptyList(),
            lapSegment = emptyList(),
            lapStartMarkerTitle = "Lap Start",
            lapEndMarkerTitle = "Lap End",
            layerConfig = WorkoutMapLayerConfig(enabledLayers = emptySet()),
            enabledTrackTypes = setOf(TrackType.GPS),
            showTechnicalTracks = true
        )
        assertEquals(listOf(TrackType.GPS), withoutPrimaryWithGps.tracks.map { it.type })

        // Case 3: PRIMARY_TRACK OFF + GPS OFF + FUSED ON -> only FUSED rendered
        val onlyFused = resolveFullscreenWorkoutMapContent(
            tracks = listOf(gpsTrack, fusedTrack),
            segments = emptyList(),
            routes = emptyList(),
            markers = emptyList(),
            knownLocations = emptyList(),
            climbs = emptyList(),
            lapSegment = emptyList(),
            lapStartMarkerTitle = "Lap Start",
            lapEndMarkerTitle = "Lap End",
            layerConfig = WorkoutMapLayerConfig(enabledLayers = emptySet()),
            enabledTrackTypes = setOf(TrackType.FUSED),
            showTechnicalTracks = true
        )
        assertEquals(listOf(TrackType.FUSED), onlyFused.tracks.map { it.type })
    }
}
