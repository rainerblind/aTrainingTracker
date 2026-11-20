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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Behavioral Robolectric Compose UI tests for Workout Details map round Layers button gesture
 * isolation and [TrackLayersMenuButton] dropdown toggling (`REQ-REV-016`, `TST-REV-017`, `ATT-9067`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TrackOnMapLayersUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 9067L,
        finished = true,
        fileBaseName = "run_9067.tcx",
        workoutName = "Morning Loop",
        sportId = 1L,
        sportName = "Running",
        bSportType = BSportType.RUN,
        startTimeS = 1_760_000_000L,
        formattedDate = "20.11.2026",
        formattedTime = "07:30",
        localDateTime = LocalDateTime.of(2026, 11, 20, 7, 30),
        equipmentName = null,
        equipmentId = -1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 5_000.0,
        maxDisplacement = 2_000.0,
        activeTimeSec = 1500L,
        totalTimeSec = 1520L,
        avgSpeedMps = 3.33,
        ascentMeters = 45L,
        descentMeters = 45L,
        minAltitude = 510.0,
        maxAltitude = 540.0,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    private fun samplePath(): List<PathPoint> = listOf(
        PathPoint(
            distance = 0.0,
            latLng = LatLng(48.1371, 11.5754),
            timeSec = 0L,
            altitude = 515.0,
            hr = 140,
            speedMps = 3.3
        ),
        PathPoint(
            distance = 150.0,
            latLng = LatLng(48.1382, 11.5766),
            timeSec = 45L,
            altitude = 522.0,
            hr = 146,
            speedMps = 3.4
        ),
        PathPoint(
            distance = 300.0,
            latLng = LatLng(48.1394, 11.5778),
            timeSec = 90L,
            altitude = 518.0,
            hr = 149,
            speedMps = 3.3
        )
    )

    // REQ-REV-016 / TST-REV-017
    @Test
    fun tabbedMap_trackLayersButtonTapOpensMenuAndTogglesTrackWithoutLockingPager_tstRev017_1() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val availableTypes = setOf(TrackType.BEST, TrackType.GPS, TrackType.ATT_FUSED)
        var enabledTypes by mutableStateOf(setOf(TrackType.BEST))
        val tabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_map_graphs",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = sampleWorkoutData(),
                            tabs = tabs,
                            availability = WorkoutDetailSectionAvailability(
                                hasMap = true,
                                hasElevation = true,
                                hasCharts = true
                            ),
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(),
                            activeScrubPath = samplePath(),
                            hasAltitudeData = true,
                            splitChartData = null,
                            selectedLapNr = null,
                            onSelectLapNr = {},
                            hrZoneDistribution = null,
                            powerZoneDistribution = null,
                            effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
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
                                TrackLayersMenuButton(
                                    availableTrackTypes = availableTypes,
                                    enabledTrackTypes = enabledTypes,
                                    onToggleTrackType = { type ->
                                        enabledTypes = if (type in enabledTypes) {
                                            enabledTypes - type
                                        } else {
                                            enabledTypes + type
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val pagerScrollEnabledMatcher = SemanticsMatcher("HorizontalPager ScrollBy enabled") { node ->
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null &&
                node.config.getOrNull(SemanticsActions.ScrollBy) != null
        }

        // Before press: HorizontalPager allows horizontal scrolling
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertExists()

        // Press and hold on the inner-left half of the round Layers button (< width - 32.dp)
        // to verify HorizontalPager(userScrollEnabled) stays constant across DOWN and UP.
        val buttonNode = composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_BUTTON)
        buttonNode.assertIsDisplayed()
        buttonNode.performTouchInput {
            down(Offset(width * 0.2f, height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertExists()

        buttonNode.performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertExists()

        // DropdownMenu must open with all 4 TrackType entries
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_MENU).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.track_type_best)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.track_type_gps)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.track_type_fused)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.track_type_att_fused)).assertIsDisplayed()

        // Unavailable FUSED is disabled; available GPS & ATT_FUSED toggle cleanly
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.FUSED)).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.GPS))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf(TrackType.BEST, TrackType.GPS), enabledTypes)

        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.ATT_FUSED))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf(TrackType.BEST, TrackType.GPS, TrackType.ATT_FUSED), enabledTypes)
    }

    // REQ-REV-016 / TST-REV-017
    @Test
    fun mapDetailLayout_isolatesFloatingControlsFromCanvasTouchCallback_tstRev017_1() {
        val availableTypes = setOf(TrackType.BEST, TrackType.GPS, TrackType.ATT_FUSED)
        var enabledTypes by mutableStateOf(setOf(TrackType.BEST))
        val canvasTouchEvents = mutableListOf<Boolean>()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MapDetailLayout(
                        bSportType = BSportType.RUN,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = samplePath(),
                        workoutData = sampleWorkoutData(),
                        showHeader = false,
                        showMap = true,
                        showElevationProfile = false,
                        showTelemetryCharts = false,
                        useStatusBarsPadding = false,
                        onInteractiveCanvasTouch = { canvasTouchEvents.add(it) },
                        header = { Text("Snapshot Header") },
                        topEndActions = {
                            TrackLayersMenuButton(
                                availableTrackTypes = availableTypes,
                                enabledTrackTypes = enabledTypes,
                                onToggleTrackType = { type ->
                                    enabledTypes = if (type in enabledTypes) {
                                        enabledTypes - type
                                    } else {
                                        enabledTypes + type
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Tapping the round Layers button must NEVER emit onInteractiveCanvasTouch(true)
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertFalse(
            "Floating Layers button tap must not trigger onInteractiveCanvasTouch(true)",
            canvasTouchEvents.any { it }
        )
        assertTrue(
            "Floating Controls topEndActions Column must emit onInteractiveCanvasTouch(false)",
            canvasTouchEvents.any { !it }
        )
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_MENU).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.GPS)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf(TrackType.BEST, TrackType.GPS), enabledTypes)

        // Dismiss menu before touching map canvas
        composeTestRule.onNodeWithText("Map Singleton (Renders on Device)").performTouchInput {
            down(center)
            up()
        }
        composeTestRule.waitForIdle()
        canvasTouchEvents.clear()

        // 2. Touching the map canvas outside FLOATING CONTROLS MUST still emit onInteractiveCanvasTouch(true)
        composeTestRule.onNodeWithText("Map Singleton (Renders on Device)").performTouchInput {
            down(center)
            moveBy(Offset(24f, 0f))
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Map canvas pan must still emit onInteractiveCanvasTouch(true) for REQ-UI-408.4",
            canvasTouchEvents.any { it }
        )
    }

    // REQ-REV-016 / TST-REV-017
    @Test
    fun nonTabbedFallback_trackLayersButtonOpensMenuAndTogglesTrack_tstRev017_2() {
        val availableTypes = setOf(TrackType.BEST, TrackType.ATT_FUSED)
        var enabledTypes by mutableStateOf(setOf(TrackType.BEST))

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MapDetailLayout(
                        bSportType = BSportType.RUN,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = samplePath(),
                        workoutData = sampleWorkoutData(),
                        showHeader = true,
                        showMap = true,
                        showElevationProfile = false,
                        showTelemetryCharts = false,
                        useStatusBarsPadding = true,
                        header = { Text("Workout Header") },
                        topEndActions = {
                            TrackLayersMenuButton(
                                availableTrackTypes = availableTypes,
                                enabledTrackTypes = enabledTypes,
                                onToggleTrackType = { type ->
                                    enabledTypes = if (type in enabledTypes) {
                                        enabledTypes - type
                                    } else {
                                        enabledTypes + type
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TrackOnMapTestTags.LAYERS_MENU).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.GPS)).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TrackOnMapTestTags.layerMenuItem(TrackType.ATT_FUSED))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(setOf(TrackType.BEST, TrackType.ATT_FUSED), enabledTypes)
    }
}
