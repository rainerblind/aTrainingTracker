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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
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

/**
 * Behavioral Robolectric Compose UI verification for [MapDetailLayout] `showHeader = false`
 * offscreen snapshot header isolation and frame-synchronized capture (`REQ-REV-015`, `TST-REV-016`, `ATT-9168`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MapDetailLayoutOffscreenHeaderUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleRunningPath(): List<PathPoint> = listOf(
        PathPoint(
            distance = 0.0,
            latLng = LatLng(48.1371, 11.5754),
            timeSec = 0L,
            hr = 142,
            speedMps = 3.2
        ),
        PathPoint(
            distance = 120.0,
            latLng = LatLng(48.1380, 11.5764),
            timeSec = 36L,
            hr = 148,
            speedMps = 3.4
        ),
        PathPoint(
            distance = 240.0,
            latLng = LatLng(48.1390, 11.5774),
            timeSec = 72L,
            hr = 152,
            speedMps = 3.3
        )
    )

    private fun sampleSpeedOverlays(): Map<TelemetryMetricType, List<TelemetrySourceSeries>> = mapOf(
        TelemetryMetricType.SPEED to listOf(
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.SPEED,
                sourceName = "GPS",
                columnName = "SPEED_mps (GPS)",
                points = listOf(
                    TelemetrySourcePoint(0.0, 0L, 3.1),
                    TelemetrySourcePoint(120.0, 36L, 3.3),
                    TelemetrySourcePoint(240.0, 72L, 3.2)
                ),
                meanValue = 3.2
            ),
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.SPEED,
                sourceName = "ATT Fused",
                columnName = "SPEED_mps (ATT Fused)",
                points = listOf(
                    TelemetrySourcePoint(0.0, 0L, 3.2),
                    TelemetrySourcePoint(120.0, 36L, 3.4),
                    TelemetrySourcePoint(240.0, 72L, 3.3)
                ),
                meanValue = 3.3
            )
        )
    )

    // REQ-REV-015 / TST-REV-016
    @Test
    fun idleShowHeaderFalse_allowsTempoLayersClickAndSuppressesDuplicateHeaderSemantics_tstRev016_1() {
        var headerClicks = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                MapDetailLayout(
                    bSportType = BSportType.RUN,
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    activeScrubPath = sampleRunningPath(),
                    showHeader = false,
                    showMap = false,
                    showElevationProfile = false,
                    showTelemetryCharts = true,
                    multiSensorOverlays = sampleSpeedOverlays(),
                    header = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable { headerClicks++ }
                                .testTag("snapshot_header_node")
                        ) {
                            Text("Snapshot Header Title")
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Invisible snapshot header must NOT exist in accessibility/semantics tree while idle
        composeTestRule.onNodeWithTag("snapshot_header_node").assertDoesNotExist()
        composeTestRule.onNodeWithText("Snapshot Header Title").assertDoesNotExist()

        // Tapping Tempo multi-source layers icon in top viewport opens source selector cleanly
        composeTestRule.onNodeWithTag("multi_source_layers_button_PACE").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GPS", substring = true).assertIsDisplayed()
        assertEquals(0, headerClicks)
    }

    // REQ-REV-015 / TST-REV-016
    @Test
    fun idleShowHeaderFalse_allowsSichtbareDiagrammeClick_tstRev016_1() {
        var headerClicks = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                MapDetailLayout(
                    bSportType = BSportType.RUN,
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    activeScrubPath = sampleRunningPath(),
                    showHeader = false,
                    showMap = false,
                    showElevationProfile = false,
                    showTelemetryCharts = true,
                    multiSensorOverlays = sampleSpeedOverlays(),
                    header = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clickable { headerClicks++ }
                                .testTag("snapshot_header_node")
                        ) {
                            Text("Snapshot Header Title")
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("charts_visibility_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charts_visibility_menu_item_SPEED_PACE").assertIsDisplayed()
        assertEquals(0, headerClicks)
    }

    // REQ-REV-015 / TST-REV-016
    @Test
    fun onDemandSnapshotTrigger_recordsFullWidthHeaderInsideClippedContainerWithoutSemanticsLeak_tstRev016_2() {
        var triggerSnapshot by mutableStateOf(false)
        var measuredHeaderSize: IntSize? = null
        var headerDrawCount = 0
        var headerClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                // Clipped container simulates HorizontalPager bounds
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    MapDetailLayout(
                        bSportType = BSportType.RUN,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = sampleRunningPath(),
                        showHeader = false,
                        showMap = false,
                        showElevationProfile = false,
                        showTelemetryCharts = true,
                        externalSnapshotTrigger = triggerSnapshot,
                        header = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .onGloballyPositioned { measuredHeaderSize = it.size }
                                    .drawWithContent {
                                        headerDrawCount++
                                        drawContent()
                                    }
                                    .clickable { headerClicks++ }
                                    .testTag("snapshot_header_node")
                            ) {
                                Text("Snapshot Header Title")
                            }
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertNull("Offscreen header must not be composed while idle", measuredHeaderSize)
        composeTestRule.onNodeWithTag("snapshot_header_node").assertDoesNotExist()

        triggerSnapshot = true
        composeTestRule.waitForIdle()
        // Robolectric GraphicsMode.Mode.NATIVE executes the actual View draw pass on captureToImage()
        composeTestRule.onRoot().captureToImage()
        composeTestRule.waitForIdle()

        assertNotNull("Offscreen header must compose and lay out on snapshot trigger", measuredHeaderSize)
        assertTrue("Header width must match full device width (> 0)", (measuredHeaderSize?.width ?: 0) > 0)
        assertTrue("Header height must be > 0", (measuredHeaderSize?.height ?: 0) > 0)
        assertTrue("headerLayer.record must draw offscreen header inside clipped container", headerDrawCount > 0)
        // Semantics must remain completely suppressed even while offscreen snapshotHeader is active
        composeTestRule.onNodeWithTag("snapshot_header_node").assertDoesNotExist()
        composeTestRule.onNodeWithText("Snapshot Header Title").assertDoesNotExist()
        // Foreground Sichtbare Diagramme button remains clickable even while offscreen header is active
        composeTestRule.onNodeWithTag("charts_visibility_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charts_visibility_menu_item_SPEED_PACE").assertIsDisplayed()
        assertEquals(0, headerClicks)
    }

    // REQ-REV-015 / TST-REV-016
    @Test
    fun activeSnapshotWithTransparentForeground_blocksPointerClicksOnOffscreenHeader_tstRev016_2() {
        var measuredHeaderSize: IntSize? = null
        var headerDrawCount = 0
        var headerClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MapDetailLayout(
                        bSportType = BSportType.RUN,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = sampleRunningPath(),
                        showHeader = false,
                        showMap = false,
                        showElevationProfile = false,
                        showTelemetryCharts = false,
                        externalSnapshotTrigger = true,
                        header = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .onGloballyPositioned { measuredHeaderSize = it.size }
                                    .drawWithContent {
                                        headerDrawCount++
                                        drawContent()
                                    }
                                    .clickable { headerClicks++ }
                                    .testTag("snapshot_header_node")
                            ) {
                                Text("Snapshot Header Title")
                            }
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureToImage()
        composeTestRule.waitForIdle()

        assertNotNull("Offscreen header must lay out when snapshot is active", measuredHeaderSize)
        assertTrue("Header width must be > 0", (measuredHeaderSize?.width ?: 0) > 0)
        assertTrue("Header height must be > 0", (measuredHeaderSize?.height ?: 0) > 0)
        assertTrue("Offscreen header must draw into headerLayer", headerDrawCount > 0)

        // Tap directly at the center of header()'s (0..width, 0..120.dp) region over transparent foreground
        composeTestRule.onRoot().performTouchInput {
            click(Offset(centerX, 60.dp.toPx()))
        }
        composeTestRule.waitForIdle()
        assertEquals("Offscreen header must never receive clicks even over transparent foreground", 0, headerClicks)
    }
}
