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

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral UI and bounds tests for multi-source overlays on [ElevationProfile] (`TST-PER-052.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ElevationProfileOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildPath(): List<PathPoint> = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(48.10, 11.50), altitude = 500.0, timeSec = 0L, slope = 1.0, hr = 132),
        PathPoint(distance = 250.0, latLng = LatLng(48.11, 11.51), altitude = 540.0, timeSec = 30L, slope = 4.5, hr = 148),
        PathPoint(distance = 500.0, latLng = LatLng(48.12, 11.52), altitude = 520.0, timeSec = 60L, slope = -2.0, hr = 142)
    )

    private fun buildAltitudeOverlays(): List<TelemetrySourceSeries> = listOf(
        TelemetrySourceSeries(
            metricType = TelemetryMetricType.ALTITUDE,
            sourceName = "ATT Fused (Alt/Slope)",
            columnName = "ALTITUDE (att_fused_altitude_slope)",
            points = listOf(
                TelemetrySourcePoint(0.0, 0L, 498.0),
                TelemetrySourcePoint(250.0, 30L, 542.0),
                TelemetrySourcePoint(500.0, 60L, 520.0)
            ),
            meanValue = 520.0
        ),
        TelemetrySourceSeries(
            metricType = TelemetryMetricType.ALTITUDE,
            sourceName = "Barometer",
            columnName = "ALTITUDE (Barometer)",
            points = listOf(
                TelemetrySourcePoint(0.0, 0L, 470.0),
                TelemetrySourcePoint(250.0, 30L, 595.0),
                TelemetrySourcePoint(500.0, 60L, 525.0)
            ),
            meanValue = 530.0
        )
    )

    @Test
    fun testSingleSourceAltitude_hidesOverlayControlsAndMeanLegend() {
        // TST-PER-052.3
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    ElevationProfile(
                        pathPoints = buildPath(),
                        currentDistance = 250.0,
                        showZoomControls = true,
                        bSportType = BSportType.BIKE,
                        overlaySeries = listOf(buildAltitudeOverlays().first())
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("multi_source_layers_button_ALTITUDE").assertDoesNotExist()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_ALTITUDE").assertDoesNotExist()
    }

    @Test
    fun testMultiSourceAltitude_togglesOverlayLegendAndUpdatesScrubBadge() {
        // TST-PER-052.3
        val overlays = buildAltitudeOverlays()
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    ElevationProfile(
                        pathPoints = buildPath(),
                        currentDistance = 250.0,
                        showZoomControls = true,
                        showScrubbingBadge = true,
                        bSportType = BSportType.BIKE,
                        overlaySeries = overlays
                    )
                }
            }
        }

        val layersBtn = composeTestRule.onNodeWithTag("multi_source_layers_button_ALTITUDE")
        layersBtn.assertIsDisplayed()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_ALTITUDE").assertDoesNotExist()

        layersBtn.performClick()
        composeTestRule.onNodeWithText("Barometer", substring = true).performClick()

        composeTestRule.onNodeWithTag("multi_source_mean_legend_ALTITUDE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barometer: Ø 530 m", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Barometer: 595 m", substring = true).assertIsDisplayed()
    }

    @Test
    fun testCalculateElevationBoundsWithOverlays_expandsEnvelopeWhenOverlayExceedsPrimary() {
        // TST-PER-052.3
        val path = buildPath()
        val overlays = buildAltitudeOverlays()
        val baseBounds = calculateElevationBounds(pathPoints = path)
        val expandedBounds = calculateElevationBoundsWithOverlays(
            pathPoints = path,
            minAltitudeOverride = null,
            maxAltitudeOverride = null,
            activeOverlaySeries = overlays
        )

        assertTrue(expandedBounds.min <= 470.0)
        assertTrue(expandedBounds.max >= 595.0)
        assertTrue(expandedBounds.range > baseBounds.range)

        val unchangedBounds = calculateElevationBoundsWithOverlays(
            pathPoints = path,
            minAltitudeOverride = null,
            maxAltitudeOverride = null,
            activeOverlaySeries = emptyList()
        )
        assertEquals(baseBounds, unchangedBounds)
    }

    @Test
    fun testMapDetailLayout_togglingAltitudeOverlayRendersOverlayReadoutInScrubbingBadge() {
        // TST-PER-052.3 (ATT-8080 Bounce 1 Fix 1)
        val overlays = buildAltitudeOverlays()
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    MapDetailLayout(
                        bSportType = BSportType.BIKE,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = buildPath(),
                        header = { Text("Workout Header") },
                        showMap = false,
                        showZoomControls = true,
                        showElevationProfile = true,
                        showTelemetryCharts = true,
                        externalScrubDistance = 250.0,
                        multiSensorOverlays = mapOf(TelemetryMetricType.ALTITUDE to overlays)
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barometer: 595 m", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("multi_source_layers_button_ALTITUDE").performClick()
        composeTestRule.onNodeWithText("Barometer", substring = true).performClick()

        composeTestRule.onNodeWithTag("multi_source_mean_legend_ALTITUDE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scrubbing_telemetry_badge").assert(
            hasAnyDescendant(hasText("Barometer: 595 m", substring = true))
        )
    }

    /**
     * TST-UI-051 (REQ-MAP-010): Tapping the info icon toggles the GradeLegend visibility.
     */
    @Test
    fun testElevationProfile_tappingInfoButton_togglesGradeLegendVisibility_TST_UI_051() {
        // TST-UI-051
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val legendDesc = context.getString(R.string.legend)
        val under2Label = context.getString(R.string.grade_legend_under_2)
        val over25Label = context.getString(R.string.grade_legend_over_25)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    ElevationProfile(
                        pathPoints = buildPath(),
                        currentDistance = 250.0,
                        showZoomControls = true,
                        bSportType = BSportType.BIKE
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(under2Label).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(legendDesc).performClick()
        composeTestRule.onNodeWithText(under2Label).assertIsDisplayed()
        composeTestRule.onNodeWithText(over25Label).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(legendDesc).performClick()
        composeTestRule.onNodeWithText(under2Label).assertDoesNotExist()
    }
}
