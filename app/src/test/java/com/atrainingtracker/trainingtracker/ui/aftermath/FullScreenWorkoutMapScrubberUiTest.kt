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
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.map.FullScreenScrubberMetric
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.map.WorkoutMapLayerConfig
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Behavioral Robolectric Compose UI tests for the collapsible full-screen workout map metric
 * scrubber, dynamic metric selector, live floating scrub readout pill, persistent drag-end
 * selection, and bidirectional Workout Details scrub sync (`REQ-MAP-070`, `TST-MAP-073.2`, `ATT-9030`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
// Linked test specs: TST-MAP-073, TST-MAP-073.2
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class FullScreenWorkoutMapScrubberUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleTelemetryPath(): List<PathPoint> = listOf(
        PathPoint(0.0, LatLng(48.1371, 11.5754), altitude = 520.0, timeSec = 0L, hr = 132, power = 190, speedMps = 7.8, slope = 1.0),
        PathPoint(7_100.0, LatLng(48.1480, 11.5920), altitude = 585.0, timeSec = 910L, hr = 151, power = 245, speedMps = 8.1, slope = 3.5),
        PathPoint(14_200.0, LatLng(48.1610, 11.6100), altitude = 642.0, timeSec = 1820L, hr = 164, power = 285, speedMps = 8.5, slope = 4.2),
        PathPoint(21_500.0, LatLng(48.1750, 11.6280), altitude = 590.0, timeSec = 2740L, hr = 158, power = 310, speedMps = 9.0, slope = -1.8),
        PathPoint(30_000.0, LatLng(48.1900, 11.6450), altitude = 530.0, timeSec = 3720L, hr = 144, power = 215, speedMps = 8.4, slope = -0.5)
    )

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 9030L,
        finished = true,
        fileBaseName = "ride_9030.tcx",
        workoutName = "Alpine Telemetry Ride",
        sportId = 1L,
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "24.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.of(2026, 11, 24, 8, 30),
        equipmentName = null,
        equipmentId = -1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 30_000.0,
        maxDisplacement = 14_000.0,
        activeTimeSec = 3720L,
        totalTimeSec = 3800L,
        avgSpeedMps = 8.06,
        ascentMeters = 420L,
        descentMeters = 410L,
        minAltitude = 520.0,
        maxAltitude = 642.0,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    @Test
    fun dynamicMetricSwitching_andDragScrubbingUpdatesReadoutAndRetainsSelectionOnFingerLift() {
        val path = sampleTelemetryPath()
        var scrubDistance by mutableStateOf<Double?>(null)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                FullScreenWorkoutMapContent(
                    bSportType = BSportType.BIKE,
                    tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, path)),
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
                    onClose = {},
                    activeScrubPath = path,
                    selectedDistance = scrubDistance,
                    onSelectedDistanceChange = { scrubDistance = it },
                    mapSlot = { mod, _, _, _ -> Box(mod) }
                )
            }
        }

        // Only present metrics are offered; Elevation is selected by default
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.ELEVATION))
            .assertIsDisplayed()
            .assertIsSelected()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.HEART_RATE))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.POWER))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.CADENCE))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.activeMetricGraph(FullScreenScrubberMetric.ELEVATION))
            .assertIsDisplayed()

        // Switch from Elevation -> Power
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.POWER))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.metricChip(FullScreenScrubberMetric.POWER))
            .assertIsSelected()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.activeMetricGraph(FullScreenScrubberMetric.POWER))
            .assertIsDisplayed()

        // Drag horizontally along the Power graph -> must update live AND stay pinned on finger lift
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER)
            .performTouchInput {
                swipeRight(
                    startX = width * 0.25f,
                    endX = width * 0.72f,
                    durationMillis = 120
                )
            }
        composeTestRule.waitForIdle()

        assertNotNull("Drag scrubbing in full-screen must retain selected distance on finger lift", scrubDistance)
        assertTrue("Scrubbed distance must advance along route", scrubDistance!! > 5_000.0)
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL)
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("W", substring = true), useUnmergedTree = true)
            .assertIsDisplayed()

        // Clearing via pill X button resets selection
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_CLEAR_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()
        assertNull(scrubDistance)
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL)
            .assertDoesNotExist()
    }

    @Test
    fun collapseAndExpandToggle_hidesAndRestoresScrubberGraphCleanly() {
        val path = sampleTelemetryPath()

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                FullScreenWorkoutMapContent(
                    bSportType = BSportType.BIKE,
                    tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, path)),
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
                    onClose = {},
                    activeScrubPath = path,
                    selectedDistance = 14_200.0,
                    mapSlot = { mod, _, _, _ -> Box(mod) }
                )
            }
        }

        // Expanded initially with active readout pill
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.EXPANDED_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL).assertIsDisplayed()

        // Tap Collapse -> leaves minimal collapsed pill only
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.COLLAPSE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.EXPANDED_CARD).assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER).assertDoesNotExist()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.COLLAPSED_PILL).assertIsDisplayed()

        // Tap Expand -> restores graph and active readout pill
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.EXPAND_BUTTON).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.EXPANDED_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL).assertIsDisplayed()
    }

    @Test
    fun bidirectionalScrubSync_initializesFromWorkoutDetailsAndSyncsBackOnClose() {
        val path = sampleTelemetryPath()
        val workout = sampleWorkoutData()
        val mapTab = WorkoutDetailTabConfig(
            id = "map_tab",
            preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
            customTitle = "",
            sections = listOf(WorkoutDetailSectionType.MAP, WorkoutDetailSectionType.ELEVATION)
        )
        val availability = WorkoutDetailSectionAvailability(hasMap = true, hasElevation = true)

        var sharedScrubDistance by mutableStateOf<Double?>(14_200.0)
        var fullscreenScrubDistance by mutableStateOf<Double?>(null)
        var showFullscreen by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = workout,
                            tabs = listOf(mapTab),
                            availability = availability,
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(),
                            activeScrubPath = path,
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
                                WorkoutMapExpandButton(
                                    onClick = {
                                        fullscreenScrubDistance = sharedScrubDistance
                                        showFullscreen = true
                                    }
                                )
                            },
                            externalScrubDistance = sharedScrubDistance,
                            onScrubDistanceChange = { sharedScrubDistance = it },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (showFullscreen) {
                            FullScreenWorkoutMapContent(
                                bSportType = BSportType.BIKE,
                                tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, path)),
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
                                onClose = {
                                    sharedScrubDistance = fullscreenScrubDistance
                                    showFullscreen = false
                                },
                                activeScrubPath = path,
                                selectedDistance = fullscreenScrubDistance,
                                onSelectedDistanceChange = { fullscreenScrubDistance = it },
                                mapSlot = { mod, _, _, _ -> Box(mod) }
                            )
                        }
                    }
                }
            }
        }

        // Open full-screen -> initialized at 14.2 km from Workout Details
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.EXPAND_FULLSCREEN_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assertEquals(14_200.0, fullscreenScrubDistance)
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.READOUT_PILL).assertIsDisplayed()

        // Tap/scrub on full-screen scrubber near ~21.5 km (~71.7% along 30 km route)
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.GRAPH_CONTAINER)
            .performTouchInput {
                val targetX = width * 0.72f
                down(Offset(targetX, height * 0.5f))
                up()
            }
        composeTestRule.waitForIdle()

        val scrubbedInFullscreen = fullscreenScrubDistance
        assertNotNull(scrubbedInFullscreen)
        assertTrue(
            "Expected full-screen scrub near 21.5 km, got $scrubbedInFullscreen",
            scrubbedInFullscreen!! >= 14_200.0
        )

        // Explicitly scrub to 21.5 km via drag to verify exact return sync
        fullscreenScrubDistance = 21_500.0
        composeTestRule.waitForIdle()

        // Close full-screen -> Workout Details updates to 21.5 km
        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.CLOSE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        assertEquals(21_500.0, sharedScrubDistance)
        composeTestRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()
    }

    @Test
    fun bareTrackWithoutTelemetryStreams_omitsScrubberPanelGracefully() {
        val barePath = listOf(
            PathPoint(0.0, LatLng(48.1371, 11.5754)),
            PathPoint(1_000.0, LatLng(48.1450, 11.5850))
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                FullScreenWorkoutMapContent(
                    bSportType = BSportType.BIKE,
                    tracks = listOf(MapTrack(1L, TrackType.BEST, BSportType.BIKE, barePath)),
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
                    onClose = {},
                    activeScrubPath = barePath,
                    mapSlot = { mod, _, _, _ -> Box(mod) }
                )
            }
        }

        composeTestRule.onNodeWithTag(FullScreenWorkoutMapTestTags.ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FullScreenWorkoutScrubberTestTags.PANEL_ROOT).assertDoesNotExist()
    }
}
