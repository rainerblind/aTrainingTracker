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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric Compose UI contract tests verifying multi-sensor overlay disclosure,
 * Layers dropdown toggling, and mean comparison legend (REQ-PER-042.3 / TST-PER-050.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryMetricGraphMultiSourceUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun samplePath(): List<PathPoint> {
        val origin = LatLng(48.13, 11.57)
        return (0..10).map { i ->
            PathPoint(
                distance = i * 100.0,
                latLng = origin,
                timeSec = i * 15L,
                hr = 135 + i * 2,
                power = 210 + i * 5,
                cadence = 86 + (i % 6)
            )
        }
    }

    @Test
    fun singleSourceWorkout_hidesLayersButtonAndMeanLegend() {
        // TST-PER-050.3
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                TelemetryMetricGraph(
                    pathPoints = samplePath(),
                    metricType = TelemetryMetricType.HEART_RATE,
                    currentDistance = null,
                    onDistanceSelected = {},
                    bSportType = BSportType.BIKE,
                    overlaySeries = emptyList()
                )
            }
        }

        composeTestRule.onNodeWithTag("multi_source_layers_button_HEART_RATE").assertDoesNotExist()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_HEART_RATE").assertDoesNotExist()
    }

    @Test
    fun multiSourceWorkout_displaysLayersToggleMenuAndMeanLegendWhenEnabled() {
        // TST-PER-050.3
        val overlays = listOf(
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.POWER,
                sourceName = "KICKR v5.0",
                columnName = "POWER (KICKR v5.0)",
                points = (0..10).map { i ->
                    TelemetrySourcePoint(distance = i * 100.0, timeSec = i * 15L, value = 210.0 + i * 5)
                },
                meanValue = 235.0
            ),
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.POWER,
                sourceName = "Assioma Duo",
                columnName = "POWER (Assioma Duo)",
                points = (0..10).map { i ->
                    TelemetrySourcePoint(distance = i * 100.0, timeSec = i * 15L, value = 216.0 + i * 5)
                },
                meanValue = 241.0
            )
        )
        var toggledId: String? = null

        composeTestRule.setContent {
            var enabledIds by remember { mutableStateOf(emptySet<String>()) }
            ATrainingTrackerTheme(darkTheme = false) {
                Column {
                    TelemetryMetricGraph(
                        pathPoints = samplePath(),
                        metricType = TelemetryMetricType.POWER,
                        currentDistance = null,
                        onDistanceSelected = {},
                        bSportType = BSportType.BIKE,
                        overlaySeries = overlays,
                        enabledOverlayIds = enabledIds,
                        onToggleOverlay = { id ->
                            toggledId = id
                            enabledIds = if (id in enabledIds) enabledIds - id else enabledIds + id
                        }
                    )
                }
            }
        }

        // Initially overlays are off -> Layers button visible, mean legend hidden
        composeTestRule.onNodeWithTag("multi_source_layers_button_POWER").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_POWER").assertDoesNotExist()

        // Open Layers menu and toggle Assioma Duo
        composeTestRule.onNodeWithTag("multi_source_layers_button_POWER").performClick()
        composeTestRule.onNodeWithText("Assioma Duo", substring = true).performClick()

        assertTrue(toggledId == "POWER (Assioma Duo)")
        composeTestRule.onNode(
            hasTestTag("multi_source_mean_legend_POWER") and
                hasAnyDescendant(hasText("Ø 241 W", substring = true))
        ).assertIsDisplayed()

        // Toggling off hides the legend again
        composeTestRule.onNodeWithText("Assioma Duo (Ø 241 W)").performClick()
        composeTestRule.onNodeWithTag("multi_source_mean_legend_POWER").assertDoesNotExist()
    }
}
