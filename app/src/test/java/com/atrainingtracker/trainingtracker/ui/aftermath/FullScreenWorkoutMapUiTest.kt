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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.map.LocationMarker
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.ResolvedFullscreenWorkoutMapContent
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.WorkoutBaseMapType
import com.atrainingtracker.trainingtracker.ui.map.WorkoutMapLayerConfig
import com.atrainingtracker.trainingtracker.ui.map.WorkoutMapSpatialLayer
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Behavioral Robolectric Compose UI tests for full-screen workout map mode entry/exit, system
 * BackHandler navigation, and [WorkoutMapLayersSheet] spatial & base-map layer toggling
 * (`REQ-MAP-069`, `TST-MAP-072.2`, `ATT-9029`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
// Linked test specs: TST-MAP-072, TST-MAP-072.2
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class FullScreenWorkoutMapUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 9029L,
        finished = true,
        fileBaseName = "ride_9029.tcx",
        workoutName = "Alpine Loop",
        sportId = 1L,
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "24.11.2026",
        formattedTime = "08:00",
        localDateTime = LocalDateTime.of(2026, 11, 24, 8, 0),
        equipmentName = null,
        equipmentId = -1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 42_000.0,
        maxDisplacement = 18_000.0,
        activeTimeSec = 5400L,
        totalTimeSec = 5500L,
        avgSpeedMps = 7.77,
        ascentMeters = 680L,
        descentMeters = 680L,
        minAltitude = 510.0,
        maxAltitude = 920.0,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    private fun samplePoints(): List<PathPoint> = listOf(
        PathPoint(0.0, LatLng(48.1371, 11.5754), altitude = 520.0, timeSec = 0L),
        PathPoint(500.0, LatLng(48.1410, 11.5800), altitude = 555.0, timeSec = 70L)
    )

    @Test
    fun expandButtonOpensFullscreen_andCloseButtonExitsCleanly() {
        var isFullscreen by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutMapExpandButton(onClick = { isFullscreen = true })

                    if (isFullscreen) {
                        FullScreenWorkoutMapContent(
                            bSportType = BSportType.BIKE,
                            tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, samplePoints())),
                            availableTrackTypes = setOf(TrackType.BEST),
                            enabledTrackTypes = setOf(TrackType.BEST),
                            onToggleTrackType = {},
                            showTechnicalTracks = true,
                            segments = emptyList(),
                            routes = emptyList(),
                            markers = emptyList(),
                            knownLocations = emptyList(),
                            climbs = emptyList(),
                            lapSegment = emptyList(),
                            layerConfig = WorkoutMapLayerConfig(),
                            onToggleSpatialLayer = {},
                            onSelectBaseMapType = {},
                            onClose = { isFullscreen = false },
                            mapSlot = { mod, _, _, _ -> Box(mod) }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(TrackOnMapTestTags.EXPAND_FULLSCREEN_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.CLOSE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_BUTTON).assertIsDisplayed()

        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.CLOSE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assertFalse("Close button must exit full-screen workout map mode", isFullscreen)
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun systemBack_closesLayersSheetFirstThenExitsFullscreen() {
        var isFullscreen by mutableStateOf(true)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                if (isFullscreen) {
                    FullScreenWorkoutMapContent(
                        bSportType = BSportType.BIKE,
                        tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, samplePoints())),
                        availableTrackTypes = setOf(TrackType.BEST),
                        enabledTrackTypes = setOf(TrackType.BEST),
                        onToggleTrackType = {},
                        showTechnicalTracks = false,
                        segments = emptyList(),
                        routes = emptyList(),
                        markers = emptyList(),
                        knownLocations = emptyList(),
                        climbs = emptyList(),
                        lapSegment = emptyList(),
                        layerConfig = WorkoutMapLayerConfig(),
                        onToggleSpatialLayer = {},
                        onSelectBaseMapType = {},
                        onClose = { isFullscreen = false },
                        mapSlot = { mod, _, _, _ -> Box(mod) }
                    )
                }
            }
        }

        // Open Layers sheet
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_BUTTON).performClick()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_SHEET).assertIsDisplayed()

        // First system Back dismisses only the Layers sheet (keeps full-screen open)
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertTrue("First Back must dismiss only Layers sheet, keeping full-screen open", isFullscreen)
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_SHEET).assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.ROOT).assertIsDisplayed()

        // Second system Back exits full-screen cleanly
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertFalse("Second Back must exit full-screen workout map mode", isFullscreen)
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.ROOT).assertDoesNotExist()
    }

    @Test
    fun layersSheet_togglesBaseMapSpatialOverlaysAndTechnicalTracksImmediately() {
        val points = samplePoints()
        val bestTrack = MapTrack(1L, TrackType.BEST, BSportType.BIKE, points)
        val gpsTrack = MapTrack(2L, TrackType.GPS, BSportType.BIKE, points)
        val segment = MapSegment(10L, "River Sprint", BSportType.BIKE, points)
        val route = MapRoute(
            id = 20L,
            name = "Weekend Loop",
            isSelected = true,
            bSportType = BSportType.BIKE,
            path = points
        )
        val marker = LocationMarker(points.first().latLng, R.drawable.control_start, "Start")
        val climb = Climb(
            id = 40L,
            name = "Summit Climb",
            routeId = 20L,
            startLat = points.first().latLng.latitude,
            startLng = points.first().latLng.longitude,
            endLat = points.last().latLng.latitude,
            endLng = points.last().latLng.longitude,
            distanceMeters = 500.0,
            elevationGainMeters = 35.0,
            avgGradePercent = 7.0,
            maxGradePercent = 9.0,
            category = ClimbCategory.CAT_4,
            pathPoints = points
        )

        var layerConfig by mutableStateOf(WorkoutMapLayerConfig())
        var enabledTrackTypes by mutableStateOf(setOf(TrackType.BEST))
        var observedBaseType: WorkoutBaseMapType? = null
        var observedContent: ResolvedFullscreenWorkoutMapContent? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                FullScreenWorkoutMapContent(
                    bSportType = BSportType.BIKE,
                    tracks = listOf(bestTrack, gpsTrack),
                    availableTrackTypes = setOf(TrackType.BEST, TrackType.GPS),
                    enabledTrackTypes = enabledTrackTypes,
                    onToggleTrackType = { type ->
                        enabledTrackTypes = if (type in enabledTrackTypes) {
                            enabledTrackTypes - type
                        } else {
                            enabledTrackTypes + type
                        }
                    },
                    showTechnicalTracks = true,
                    segments = listOf(segment),
                    routes = listOf(route),
                    markers = listOf(marker),
                    knownLocations = emptyList(),
                    climbs = listOf(climb),
                    lapSegment = emptyList(),
                    layerConfig = layerConfig,
                    onToggleSpatialLayer = { layer ->
                        val next = if (layer in layerConfig.enabledLayers) {
                            layerConfig.enabledLayers - layer
                        } else {
                            layerConfig.enabledLayers + layer
                        }
                        layerConfig = layerConfig.copy(enabledLayers = next)
                    },
                    onSelectBaseMapType = { baseType ->
                        layerConfig = layerConfig.copy(baseMapType = baseType)
                    },
                    onClose = {},
                    mapSlot = { mod, baseMapType, resolved, _ ->
                        observedBaseType = baseMapType
                        observedContent = resolved
                        Box(mod)
                    }
                )
            }
        }

        // Initial default state: TERRAIN, Segments ON, Climbs OFF, GPS OFF
        assertEquals(WorkoutBaseMapType.TERRAIN, observedBaseType)
        assertEquals(1, observedContent?.segments?.size)
        assertEquals(0, observedContent?.climbs?.size)
        assertEquals(listOf(TrackType.BEST), observedContent?.tracks?.map { it.type })

        // Open Layers sheet
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_BUTTON).performClick()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_SHEET).assertIsDisplayed()

        // Switch base map to SATELLITE
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.TERRAIN))
            .assertIsSelected()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.SATELLITE))
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(WorkoutBaseMapType.SATELLITE, observedBaseType)
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.SATELLITE))
            .assertIsSelected()

        // Toggle Climbs ON, Segments OFF, GPS Technical Track ON
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.spatialLayerRow(WorkoutMapSpatialLayer.CLIMBS))
            .assertIsOff()
            .performClick()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.spatialLayerRow(WorkoutMapSpatialLayer.SEGMENTS))
            .assertIsOn()
            .performClick()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.technicalTrackRow(TrackType.GPS))
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()

        assertNotNull(observedContent)
        assertEquals(1, observedContent?.climbs?.size)
        assertEquals(0, observedContent?.segments?.size)
        assertEquals(listOf(TrackType.BEST, TrackType.GPS), observedContent?.tracks?.map { it.type })
    }

    @Test
    fun layersSheet_omitsTechnicalTracksSectionWhenOnlyBestTrackAvailable() {
        val context: Context = ApplicationProvider.getApplicationContext()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutMapLayersSheetBody(
                    layerConfig = WorkoutMapLayerConfig(),
                    availableTrackTypes = setOf(TrackType.BEST),
                    enabledTrackTypes = setOf(TrackType.BEST),
                    showTechnicalTracks = true,
                    onToggleSpatialLayer = {},
                    onToggleTrackType = {},
                    onSelectBaseMapType = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.workout_map_layers_technical_tracks_header))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.technicalTrackRow(TrackType.GPS))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.spatialLayerRow(WorkoutMapSpatialLayer.PRIMARY_TRACK))
            .assertIsDisplayed()
    }

    @Test
    fun embeddedWorkoutDetailTabbedContent_rendersExpandButtonAndTriggersFullscreenCallback() {
        var expandedClicked = false
        val workout = sampleWorkoutData()
        val mapTab = WorkoutDetailTabConfig(
            id = "map_tab",
            preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
            customTitle = "",
            sections = listOf(WorkoutDetailSectionType.MAP, WorkoutDetailSectionType.ELEVATION)
        )
        val availability = WorkoutDetailSectionAvailability(
            hasMap = true,
            hasElevation = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    WorkoutDetailTabbedContent(
                        workoutData = workout,
                        tabs = listOf(mapTab),
                        availability = availability,
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(),
                        activeScrubPath = samplePoints(),
                        hasAltitudeData = true,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = null,
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.HISTOGRAM,
                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.HISTOGRAM,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        mapTopEndActions = {
                            WorkoutMapExpandButton(onClick = { expandedClicked = true })
                        },
                        onMapClick = { expandedClicked = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(TrackOnMapTestTags.EXPAND_FULLSCREEN_BUTTON)
            .assertIsDisplayed()
            .performClick()

        assertTrue("Tapping Expand button on embedded Workout Details map must trigger full-screen callback", expandedClicked)
    }

    @Test
    fun darkModeUnsetBaseMap_defaultsToNormalAndHonorsExplicitTerrainSelection() {
        var layerConfig by mutableStateOf(WorkoutMapLayerConfig(baseMapType = null))
        var observedBaseType: WorkoutBaseMapType? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true, setWindowColors = false) {
                FullScreenWorkoutMapContent(
                    bSportType = BSportType.BIKE,
                    tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, samplePoints())),
                    availableTrackTypes = setOf(TrackType.BEST),
                    enabledTrackTypes = setOf(TrackType.BEST),
                    onToggleTrackType = {},
                    showTechnicalTracks = false,
                    segments = emptyList(),
                    routes = emptyList(),
                    markers = emptyList(),
                    knownLocations = emptyList(),
                    climbs = emptyList(),
                    lapSegment = emptyList(),
                    layerConfig = layerConfig,
                    onToggleSpatialLayer = {},
                    onSelectBaseMapType = { layerConfig = layerConfig.copy(baseMapType = it) },
                    onClose = {},
                    mapSlot = { mod, baseMapType, _, _ ->
                        observedBaseType = baseMapType
                        Box(mod)
                    }
                )
            }
        }

        // Unset base-map in Dark Mode defaults to NORMAL (dark vector tiles, avoiding daytime TERRAIN glare)
        assertEquals(WorkoutBaseMapType.NORMAL, observedBaseType)

        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.LAYERS_BUTTON).performClick()
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.NORMAL))
            .assertIsSelected()

        // Explicit TERRAIN selection in Dark Mode must override the dark-mode NORMAL default
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.TERRAIN))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(WorkoutBaseMapType.TERRAIN, observedBaseType)
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.baseMapOption(WorkoutBaseMapType.TERRAIN))
            .assertIsSelected()
    }
}
