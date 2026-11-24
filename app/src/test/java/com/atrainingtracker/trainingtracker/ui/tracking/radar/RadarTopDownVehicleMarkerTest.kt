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

package com.atrainingtracker.trainingtracker.ui.tracking.radar

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.radar.LaneState
import com.atrainingtracker.banalservice.devices.radar.RadarConnectionState
import com.atrainingtracker.banalservice.devices.radar.RadarSource
import com.atrainingtracker.banalservice.devices.radar.RadarState
import com.atrainingtracker.banalservice.devices.radar.RadarTarget
import com.atrainingtracker.banalservice.devices.radar.ThreatLevel
import com.atrainingtracker.banalservice.devices.radar.VehicleSizeCategory
import com.atrainingtracker.trainingtracker.settings.RadarClearMode
import com.atrainingtracker.trainingtracker.settings.RadarEdgeSide
import com.atrainingtracker.trainingtracker.settings.RadarFullScreenMarkerStyle
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.util.safeEnumValueOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Unit and Robolectric Compose UI tests for top-down ("GTA-1 style") full-screen radar vehicle
 * markers and style selection invariants (REQ-TRK-098, TST-TRK-098, ATT-8959).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class RadarTopDownVehicleMarkerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun markerStyleEnum_defaultsToCompactBadgeAndDeserializesSafely() {
        assertEquals(
            RadarFullScreenMarkerStyle.COMPACT_BADGE,
            TuningPreferencesDefaults.RADAR_FULL_SCREEN_MARKER_STYLE
        )
        assertEquals(
            RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES,
            safeEnumValueOf("TOP_DOWN_VEHICLES", TuningPreferencesDefaults.RADAR_FULL_SCREEN_MARKER_STYLE)
        )
        assertEquals(
            RadarFullScreenMarkerStyle.COMPACT_BADGE,
            safeEnumValueOf("UNKNOWN_LEGACY_STYLE", TuningPreferencesDefaults.RADAR_FULL_SCREEN_MARKER_STYLE)
        )
    }

    @Test
    fun resolveTopDownVehicleVisualSpec_enforcesNonMotorcycleCompactCarAndDistinctProportions() {
        val compactSpec = resolveTopDownVehicleVisualSpec(VehicleSizeCategory.UNKNOWN_OR_LIGHT)
        val carSpec = resolveTopDownVehicleVisualSpec(VehicleSizeCategory.CAR)
        val truckSpec = resolveTopDownVehicleVisualSpec(VehicleSizeCategory.TRUCK_OR_BUS)

        assertEquals(TopDownSilhouetteKind.COMPACT_VEHICLE, compactSpec.silhouette)
        assertEquals(TopDownSilhouetteKind.SEDAN, carSpec.silhouette)
        assertEquals(TopDownSilhouetteKind.TRUCK_OR_BUS, truckSpec.silhouette)

        assertTrue("CAR canvas must exceed UNKNOWN_OR_LIGHT dimensions", carSpec.canvasWidth > compactSpec.canvasWidth && carSpec.canvasHeight > compactSpec.canvasHeight)
        assertTrue("TRUCK_OR_BUS canvas must exceed CAR dimensions", truckSpec.canvasWidth > carSpec.canvasWidth && truckSpec.canvasHeight > carSpec.canvasHeight)
        assertTrue("Compact vehicle font size must be >= 18.sp", compactSpec.fontSize >= 18.sp)
        assertTrue("Sedan font size must be >= 19.sp", carSpec.fontSize >= 19.sp)
        assertTrue("Truck font size must be >= 20.sp", truckSpec.fontSize >= 20.sp)
    }

    @Test
    fun topDownMarkers_renderDistinctSilhouettesUnclippedAndClampedOnFrame1() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lightDesc = context.getString(R.string.radar_vehicle_size_light)
        val carDesc = context.getString(R.string.radar_vehicle_size_car)
        val truckDesc = context.getString(R.string.radar_vehicle_size_truck)

        var range by mutableStateOf(99f)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(520.dp)) {
                    RadarEdgeOverlay(
                        radarState = RadarState(
                            connectionState = RadarConnectionState.CONNECTED,
                            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                            targets = listOf(
                                RadarTarget(
                                    targetId = 1,
                                    rangeMeters = 15f,
                                    approachSpeedMps = 22f,
                                    threatLevel = ThreatLevel.FAST_APPROACHING,
                                    lateralOffsetMeters = -4.0f, // clamps left
                                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                                ),
                                RadarTarget(
                                    targetId = 2,
                                    rangeMeters = range,
                                    approachSpeedMps = 12f,
                                    threatLevel = ThreatLevel.APPROACHING,
                                    lateralOffsetMeters = 4.0f, // clamps right
                                    vehicleSizeCategory = VehicleSizeCategory.CAR
                                ),
                                RadarTarget(
                                    targetId = 3,
                                    rangeMeters = 160f,
                                    approachSpeedMps = 10f,
                                    threatLevel = ThreatLevel.APPROACHING,
                                    laneState = LaneState.IN_RIDER_LANE,
                                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                                )
                            ),
                            source = RadarSource.BLE
                        ),
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        val overlayBounds = composeTestRule
            .onNodeWithTag("RadarFullScreenThreatOverlay", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        val b1 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val b2 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val b3 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_3", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue("Marker 1 left must stay inside overlay on Frame 1", b1.left.value >= overlayBounds.left.value - 0.5f)
        assertTrue("Marker 2 right must stay inside overlay on Frame 1", b2.right.value <= overlayBounds.right.value + 0.5f)
        assertTrue("Marker 3 bottom must stay inside overlay on Frame 1", b3.bottom.value <= overlayBounds.bottom.value + 0.5f)
        assertTrue("CAR marker height must exceed UNKNOWN_OR_LIGHT", b2.height > b1.height)
        assertTrue("TRUCK_OR_BUS marker height must exceed CAR", b3.height > b2.height)

        composeTestRule.onNodeWithTag("RadarTopDownCanvas_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarTopDownCanvas_2", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarTopDownCanvas_3", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("$lightDesc, 15m").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("$carDesc, 99m").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("$truckDesc, 160m").assertIsDisplayed()

        // Dynamic 99m -> 100m width growth stays clamped inside right boundary
        composeTestRule.runOnUiThread { range = 100f }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("100m", useUnmergedTree = true).assertIsDisplayed()
        val b2Grown = composeTestRule.onNodeWithTag("RadarFullScreenMarker_2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("99m -> 100m grown marker must stay inside overlay right edge", b2Grown.right.value <= overlayBounds.right.value + 0.5f)
    }

    @Test
    fun fullScreenStyleSwitch_togglesBetweenCompactBadgeAndTopDown_andRespectsV1LaneGuideGate() {
        var style by mutableStateOf(RadarFullScreenMarkerStyle.COMPACT_BADGE)
        val v1State = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 80f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING)
            ),
            source = RadarSource.BLE
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    RadarEdgeOverlay(
                        radarState = v1State,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        fullScreenMarkerStyle = style
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarTopDownCanvas_1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("RadarFullScreenLaneGuides", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.runOnIdle { style = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("RadarTopDownCanvas_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("RadarFullScreenLaneGuides", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun topDownMarkers_preserveTouchPassThroughAndOpaqueNonPulsingHysteresis() {
        var receivedPress = 0
        var receivedRelease = 0
        var dragDeltaX = 0f
        var activeState by mutableStateOf(
            RadarState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.FAST_APPROACHING,
                targets = listOf(
                    RadarTarget(
                        targetId = 1,
                        rangeMeters = 65f,
                        approachSpeedMps = 22f,
                        threatLevel = ThreatLevel.FAST_APPROACHING,
                        laneState = LaneState.IN_RIDER_LANE,
                        vehicleSizeCategory = VehicleSizeCategory.CAR
                    )
                ),
                lastUpdateRealtimeMs = 1000L
            )
        )
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Box(modifier = Modifier.width(360.dp).height(480.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Press -> receivedPress++
                                            PointerEventType.Release -> receivedRelease++
                                            PointerEventType.Move -> {
                                                event.changes.forEach { change ->
                                                    dragDeltaX += (change.position.x - change.previousPosition.x)
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                    )
                    RadarEdgeOverlay(
                        radarState = activeState,
                        edgeSide = RadarEdgeSide.FULL_SCREEN,
                        clearMode = RadarClearMode.SLIM_RAIL,
                        fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(200L)

        // 1. Non-pulsing solid opaque FAST_APPROACHING pixels across clock ticks
        val bmpT0 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        composeTestRule.mainClock.advanceTimeBy(175L)
        val bmpT1 = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        assertFalse("Top-down FAST_APPROACHING marker must not alpha-pulse", bitmapsDiffer(bmpT0, bmpT1))

        // 2. Touch pass-through directly over top-down marker bounds
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true).assertHasNoClickAction()
        val markerBounds = composeTestRule.onNodeWithTag("RadarFullScreenMarker_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val centerPx = with(composeTestRule.density) {
            Offset(
                x = ((markerBounds.left + markerBounds.right) / 2f).toPx(),
                y = ((markerBounds.top + markerBounds.bottom) / 2f).toPx()
            )
        }
        composeTestRule.onRoot().performTouchInput { click(centerPx) }
        composeTestRule.waitForIdle()
        assertEquals(1, receivedPress)
        assertEquals(1, receivedRelease)

        composeTestRule.onRoot().performTouchInput {
            down(centerPx)
            advanceEventTime(16L)
            moveTo(Offset((centerPx.x - 110f).coerceAtLeast(10f), centerPx.y))
            advanceEventTime(16L)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(2, receivedPress)
        assertEquals(2, receivedRelease)
        assertTrue("Underlying sibling must receive horizontal drag (< -50px), got $dragDeltaX", dragDeltaX < -50f)
    }

    @Test
    fun captureAtt8959TopDownVehiclesPreviewPng() {
        val v2LightState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 18f,
                    approachSpeedMps = 24f,
                    threatLevel = ThreatLevel.FAST_APPROACHING,
                    lateralOffsetMeters = -2.2f,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 105f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 0.0f,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                ),
                RadarTarget(
                    targetId = 3,
                    rangeMeters = 160f,
                    approachSpeedMps = 9f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = -1.6f,
                    laneState = LaneState.CHANGING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.UNKNOWN_OR_LIGHT
                )
            ),
            source = RadarSource.BLE
        )
        val v1DarkState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(
                RadarTarget(targetId = 1, rangeMeters = 35f, approachSpeedMps = 22f, threatLevel = ThreatLevel.FAST_APPROACHING),
                RadarTarget(targetId = 2, rangeMeters = 123f, approachSpeedMps = 12f, threatLevel = ThreatLevel.APPROACHING)
            ),
            source = RadarSource.BLE
        )
        val mapState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(
                RadarTarget(
                    targetId = 1,
                    rangeMeters = 42f,
                    approachSpeedMps = 12f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = 2.5f,
                    laneState = LaneState.IN_RIDER_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.CAR
                ),
                RadarTarget(
                    targetId = 2,
                    rangeMeters = 138f,
                    approachSpeedMps = 11f,
                    threatLevel = ThreatLevel.APPROACHING,
                    lateralOffsetMeters = -2.5f,
                    laneState = LaneState.PASSING_LANE,
                    vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
                )
            ),
            source = RadarSource.BLE
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(756.dp)
                    .background(TTColor.RadarRailTrack)
                    .padding(12.dp)
                    .testTag("Att8959TopDownPreviewComposite"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(236.dp).height(420.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("Top-Down V2 Light", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(
                                    radarState = v2LightState,
                                    edgeSide = RadarEdgeSide.FULL_SCREEN,
                                    fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES
                                )
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(color = Color.Black, modifier = Modifier.width(236.dp).height(420.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("Top-Down V1 Dark (No Guides)", color = Color.White, fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                RadarEdgeOverlay(
                                    radarState = v1DarkState,
                                    edgeSide = RadarEdgeSide.FULL_SCREEN,
                                    fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES
                                )
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(color = TTColor.RadarFullScreenMapBackdrop, modifier = Modifier.width(236.dp).height(420.dp)) {
                        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            Text("Top-Down Map Backdrop", fontSize = 11.sp)
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        color = TTColor.RadarFullScreenMapPark,
                                        topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
                                        size = Size(size.width * 0.36f, size.height * 0.28f)
                                    )
                                    drawLine(
                                        color = TTColor.RadarFullScreenMapRoad,
                                        start = Offset(0f, size.height * 0.36f),
                                        end = Offset(size.width, size.height * 0.24f),
                                        strokeWidth = 10.dp.toPx()
                                    )
                                    drawLine(
                                        color = TTColor.RouteSelected,
                                        start = Offset(size.width * 0.5f, size.height),
                                        end = Offset(size.width * 0.56f, 0f),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                }
                                RadarEdgeOverlay(
                                    radarState = mapState,
                                    edgeSide = RadarEdgeSide.FULL_SCREEN,
                                    fullScreenMarkerStyle = RadarFullScreenMarkerStyle.TOP_DOWN_VEHICLES
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val bmp = composeTestRule.onNodeWithTag("Att8959TopDownPreviewComposite")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8959_radar_top_down_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("ATT-8959 preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }

    private fun bitmapsDiffer(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return true
            }
        }
        return false
    }
}
