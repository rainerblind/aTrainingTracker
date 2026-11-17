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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Robolectric Compose UI verification suite for [RadarTrafficSummaryCard]
 * (REQ-UI-2784, TST-UI-2784.1..TST-UI-2784.3, ATT-8440).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class RadarTrafficSummaryCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
    }

    private fun sampleSummary() = RadarTrafficSummary(
        totalVehicles = 12,
        vehiclesPerKm = 1.8,
        vehiclesPerHour = 24.5,
        maxApproachSpeedMps = 10.0,
        maxVehicleSpeedMps = 20.0,
        highThreatPassCount = 2,
        closePassCount = 1,
        minPassingDistanceM = 1.4,
        heavyVehicleCount = 3
    )

    /**
     * TST-UI-2784.1 / REQ-UI-2784.1: Formats max approach and vehicle speeds using
     * LocalMetricFormatter.current.speed.format_with_units() under MyUnits.METRIC.
     */
    @Test
    fun testCard_formatsSpeedsUnderMetricUnits() {
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarTrafficSummaryCard(summary = sampleSummary())
            }
        }

        val metricApproachFound = composeTestRule.onAllNodesWithText("36.0 km/h").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("36,0 km/h").fetchSemanticsNodes().isNotEmpty()
        val metricVehicleFound = composeTestRule.onAllNodesWithText("72.0 km/h").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("72,0 km/h").fetchSemanticsNodes().isNotEmpty()
        assertTrue("METRIC must display 36.0 km/h", metricApproachFound)
        assertTrue("METRIC must display 72.0 km/h", metricVehicleFound)
        assertEquals(0, composeTestRule.onAllNodesWithText("mile/h", substring = true).fetchSemanticsNodes().size)
    }

    /**
     * TST-UI-2784.1 / REQ-UI-2784.1: Formats max approach and vehicle speeds using
     * LocalMetricFormatter.current.speed.format_with_units() under MyUnits.IMPERIAL.
     */
    @Test
    fun testCard_formatsSpeedsUnderImperialUnits() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarTrafficSummaryCard(summary = sampleSummary())
            }
        }

        val imperialApproachFound = composeTestRule.onAllNodesWithText("22.4 mile/h").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("22,4 mile/h").fetchSemanticsNodes().isNotEmpty()
        val imperialVehicleFound = composeTestRule.onAllNodesWithText("44.7 mile/h").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("44,7 mile/h").fetchSemanticsNodes().isNotEmpty()
        assertTrue("IMPERIAL must display 22.4 mile/h", imperialApproachFound)
        assertTrue("IMPERIAL must display 44.7 mile/h", imperialVehicleFound)
        assertEquals(
            "IMPERIAL speed cells must not render hardcoded km/h",
            0,
            composeTestRule.onAllNodesWithText("km/h", substring = true).fetchSemanticsNodes().size
        )
    }

    /**
     * TST-UI-2784.2 / REQ-UI-2784.2: Preserves null/zero-vehicle early return.
     */
    @Test
    fun testCard_nullAndZeroVehicles_rendersNothing() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.testTag("EmptyHost")) {
                    RadarTrafficSummaryCard(summary = null)
                    RadarTrafficSummaryCard(summary = RadarTrafficSummary(totalVehicles = 0))
                }
            }
        }
        assertEquals(0, composeTestRule.onAllNodesWithText("12").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("mile/h", substring = true).fetchSemanticsNodes().size)
    }

    /**
     * TST-UI-2784.2 / REQ-UI-2784.2: Preserves non-speed metrics when totalVehicles > 0.
     */
    @Test
    fun testCard_preservesNonSpeedMetrics() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                RadarTrafficSummaryCard(summary = sampleSummary())
            }
        }
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        val clearancePresent = composeTestRule.onAllNodesWithText("1.4 m").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("1,4 m").fetchSemanticsNodes().isNotEmpty()
        assertTrue("Clearance metric must remain displayed in meters", clearancePresent)
    }

    /**
     * TST-UI-2784.3 / Rule 8.5: Captures side-by-side Light & Dark IMPERIAL card PNG in build/reports/
     * for Stage 4 visual inspection without polluting git working tree.
     */
    @Test
    fun testCard_captureLightAndDarkImperialPreview() {
        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("RadarSummaryCardLightDarkPreview"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            RadarTrafficSummaryCard(summary = sampleSummary())
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            RadarTrafficSummaryCard(summary = sampleSummary())
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("RadarSummaryCardLightDarkPreview")
            .captureToImage()
            .asAndroidBitmap()

        val outFile = File("build/reports/ATT-8440_summary_card_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }
}
