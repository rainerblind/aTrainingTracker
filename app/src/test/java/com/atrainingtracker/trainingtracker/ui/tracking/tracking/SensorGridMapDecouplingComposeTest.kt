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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification suite for `ATT-6037` (`REQ-UI-2793`, `REQ-UI-326`,
 * `TST-UI-2793`, `TST-UI-286`):
 * - Verifies `SensorGridScreen` renders sensor tiles, map container, and elevation profile via the
 *   dedicated `mapStateFlow` without recomposing parent/sensor grid state on 1-Hz GPS updates.
 * - Verifies `TrackingElevationProfileSection` reads the active navigated route from `mapStateFlow`
 *   even when `TrackingScreenState.mapRoutes` is empty.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class SensorGridMapDecouplingComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noOpGridActions = object : GridActions {
        override fun onEditField(fieldState: SensorFieldState) {}
        override fun onDeleteField(fieldState: SensorFieldState) {}
        override fun onAddRow(beforeRow: Int) {}
        override fun onAddCol(atRow: Int, beforeCol: Int) {}
    }

    @Test
    fun testSensorGridScreen_mapStateFlowAndLocationTicksDoNotRecomposeParentHost_tstUi2793_2() {
        val powerField = SensorFieldState(
            configHash = 1,
            sensorFieldId = 1L,
            rowNr = 1,
            colNr = 1,
            viewSize = ViewSize.LARGE,
            label = "Power",
            value = "245",
            units = "W",
            zoneColor = Color.Transparent,
            filterDescription = ""
        )
        val screenState = TrackingScreenState(
            showMap = true,
            showElevationProfile = false,
            fields = listOf(powerField)
        )
        val mapStateFlow = MutableStateFlow(
            TrackingMapState(
                showMap = true,
                zoomFocus = MapZoomFocus.FOLLOW_ME,
                userSpeed = 5.0f,
                userBearing = 90.0f,
                bSportType = BSportType.BIKE
            )
        )
        val locationFlow = MutableStateFlow<LatLng?>(LatLng(48.137, 11.575))
        val liveSegmentsFlow = MutableStateFlow<List<LiveSegment>>(emptyList())

        var hostRecompositions = 0
        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    SideEffect { hostRecompositions++ }
                    SensorGridScreen(
                        state = screenState,
                        screenMode = ScreenMode.TRACKING,
                        gridActions = noOpGridActions,
                        currentLocationFlow = locationFlow,
                        liveSegments = liveSegmentsFlow,
                        mapStateFlow = mapStateFlow
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("245").assertIsDisplayed()
        composeRule.onNodeWithText("W").assertIsDisplayed()

        val baselineRecompositions = hostRecompositions

        // Emit 10 high-frequency GPS speed/bearing/track and location updates
        repeat(10) { idx ->
            composeRule.runOnIdle {
                mapStateFlow.value = mapStateFlow.value.copy(
                    userSpeed = 6.0f + idx,
                    userBearing = (idx * 15f) % 360f,
                    currentTrack = mapStateFlow.value.currentTrack + LatLng(48.137 + idx * 0.0002, 11.575)
                )
                locationFlow.value = LatLng(48.137 + idx * 0.0002, 11.575)
            }
            composeRule.waitForIdle()
        }

        assertEquals(
            "1-Hz GPS mapStateFlow and currentLocationFlow ticks must not recompose SensorGridScreen host",
            baselineRecompositions,
            hostRecompositions
        )
        composeRule.onNodeWithText("245").assertIsDisplayed()
    }

    @Test
    fun testSensorGridScreen_elevationProfileConsumesActiveNavigatedRouteFromMapStateFlow_tstUi2793_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emptyStateText = context.getString(R.string.elevation_profile_empty_state)
        val navigatedRoute = MapRoute(
            id = 42L,
            name = "Alpine Loop",
            isSelected = true,
            bSportType = BSportType.BIKE,
            isActiveNavigation = true,
            path = listOf(
                PathPoint(distance = 0.0, latLng = LatLng(47.500, 11.200), altitude = 500.0, timeSec = 0L),
                PathPoint(distance = 1000.0, latLng = LatLng(47.509, 11.200), altitude = 610.0, timeSec = 180L),
                PathPoint(distance = 2000.0, latLng = LatLng(47.518, 11.200), altitude = 540.0, timeSec = 320L)
            )
        )
        // Intentionally leave TrackingScreenState.mapRoutes & pathPoints empty to verify migration to mapStateFlow
        val screenState = TrackingScreenState(
            showMap = false,
            showElevationProfile = true,
            fields = listOf(
                SensorFieldState(
                    configHash = 2,
                    sensorFieldId = 2L,
                    rowNr = 1,
                    colNr = 1,
                    viewSize = ViewSize.NORMAL,
                    label = "Speed",
                    value = "28.4",
                    units = "km/h",
                    zoneColor = Color.Transparent,
                    filterDescription = ""
                )
            ),
            pathPoints = emptyList(),
            mapRoutes = emptyList()
        )
        val mapStateFlow = MutableStateFlow(
            TrackingMapState(
                showMap = false,
                mapRoutes = emptyList()
            )
        )
        val locationFlow = MutableStateFlow<LatLng?>(LatLng(47.509, 11.200))

        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    SensorGridScreen(
                        state = screenState,
                        screenMode = ScreenMode.TRACKING,
                        gridActions = noOpGridActions,
                        currentLocationFlow = locationFlow,
                        liveSegments = MutableStateFlow(emptyList()),
                        mapStateFlow = mapStateFlow
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Before an active navigated route arrives on mapStateFlow, the empty-state placeholder is displayed
        composeRule.onNodeWithText("28.4").assertIsDisplayed()
        composeRule.onNodeWithText("km/h").assertIsDisplayed()
        composeRule.onNodeWithText(emptyStateText).assertIsDisplayed()

        // 2. Emit active navigated route strictly via mapStateFlow (state.mapRoutes stays empty) ->
        //    TrackingElevationProfileSection resolves the route and replaces the empty-state placeholder
        composeRule.runOnIdle {
            mapStateFlow.value = mapStateFlow.value.copy(mapRoutes = listOf(navigatedRoute))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(emptyStateText).assertDoesNotExist()
        composeRule.onNodeWithText("28.4").assertIsDisplayed()

        // 3. Clearing the navigated route on mapStateFlow restores the empty-state placeholder
        composeRule.runOnIdle {
            mapStateFlow.value = mapStateFlow.value.copy(mapRoutes = emptyList())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(emptyStateText).assertIsDisplayed()
    }
}
