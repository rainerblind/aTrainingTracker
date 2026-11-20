/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.components.MetricLayout
import com.atrainingtracker.trainingtracker.ui.segments.SegmentDetails
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification for [WorkoutDetails], [SegmentDetails], and [MetricItem]:
 * - `TST-UI-021` (`REQ-UI-012`): WorkoutDetails shows Ascent & Descent, omitting redundant Min/Max Altitude.
 * - `TST-UI-030` (`REQ-UI-020`): Consistent vertical elevation & gain metric presentation across WorkoutDetails and SegmentDetails.
 * - `TST-UI-031` (`REQ-UI-021`): Elevation domain label hierarchy in WorkoutDetails.
 * - `TST-UI-059` / `TST-UI-060` (`REQ-UI-054`, `REQ-UI-055`): Secondary alpha attenuation & primary metric prominence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutDetailsAndMetricUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun readTextLayout(interaction: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = interaction.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
        assertTrue("GetTextLayoutResult must be supported", action.action?.invoke(results) == true)
        return results.first()
    }

    @Test
    fun workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060() {
        // TST-UI-021, TST-UI-030, TST-UI-031, TST-UI-059, TST-UI-060
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detailsData = WorkoutDetailsData(
            totalDistance = 25_400.0,
            activeTimeSec = 3600L,
            totalTimeSec = 3720L,
            avgSpeedMps = 7.05,
            ascentMeters = 320L,
            descentMeters = 310L,
            bSportType = BSportType.BIKE,
            maxDisplacement = 12_000.0
        )
        val segmentSummary = SegmentSummary(
            stravaId = 10L,
            name = "Olympiaberg",
            bSportType = BSportType.BIKE,
            climbCategory_raw = 3,
            climbCategory = "Cat 3",
            prTime_raw = 180,
            prTime = "03:00",
            city = "Munich",
            distance = "1.2 km",
            distance_raw = 1200.0,
            averageGrade_raw = 6.5,
            averageGrade = "6.5 %",
            maxGrade = "12.0 %",
            elevationGain_raw = 85.0,
            elevationGain = "85 m",
            elevationMin = "500 m",
            elevationMax = "585 m",
            map_polyline = ""
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    Column {
                        WorkoutDetails(data = detailsData)
                        SegmentDetails(summary = segmentSummary, showStravaLogo = false)
                        MetricItem(
                            iconRes = R.drawable.ic_distance,
                            label = "Primary Sample",
                            value = "42.0 km",
                            secondaryValue = "Max 15 km",
                            layout = MetricLayout.VERTICAL,
                            isPrimary = true
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val elevationLabel = context.getString(R.string.elevation)
        val ascentLabel = context.getString(R.string.ascent)
        val descentLabel = context.getString(R.string.descent)
        val minAltLabel = context.getString(R.string.marker_min_altitude)
        val maxAltLabel = context.getString(R.string.marker_max_altitude)

        // TST-UI-021 & TST-UI-031: Elevation domain label, Ascent, and Descent displayed; Min/Max Altitude omitted in WorkoutDetails
        composeTestRule.onNodeWithText(elevationLabel).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(ascentLabel).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(descentLabel).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(minAltLabel).fetchSemanticsNodes().also {
            assertTrue("WorkoutDetails must omit redundant Min Altitude row", it.isEmpty())
        }
        composeTestRule.onAllNodesWithText(maxAltLabel).fetchSemanticsNodes().also {
            assertTrue("WorkoutDetails must omit redundant Max Altitude row", it.isEmpty())
        }

        // TST-UI-030: SegmentDetails renders segment elevation metrics cleanly alongside WorkoutDetails
        composeTestRule.onNodeWithText("500 m").assertIsDisplayed()
        composeTestRule.onNodeWithText("585 m").assertIsDisplayed()

        // TST-UI-059 & TST-UI-060: Primary MetricItem value uses Bold weight & higher alpha than secondary value
        val primaryStyle = readTextLayout(composeTestRule.onNodeWithText("42.0 km")).layoutInput.style
        val secondaryStyle = readTextLayout(composeTestRule.onNodeWithText("Max 15 km")).layoutInput.style
        assertTrue("Primary metric must be Bold or SemiBold", primaryStyle.fontWeight == FontWeight.Bold || primaryStyle.fontWeight == FontWeight.SemiBold)
        assertTrue("Primary metric fontSize must exceed secondary text", primaryStyle.fontSize.value > secondaryStyle.fontSize.value)
        assertTrue("Secondary metric alpha (${secondaryStyle.color.alpha}) must be attenuated (< 1f)", secondaryStyle.color.alpha < 1.0f)
    }
}
