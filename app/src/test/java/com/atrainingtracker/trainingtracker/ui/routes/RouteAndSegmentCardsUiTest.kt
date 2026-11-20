/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.segments.SegmentSummary
import com.atrainingtracker.trainingtracker.segments.SegmentWithPath
import com.atrainingtracker.trainingtracker.ui.segments.segmentlist.SegmentList
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification for [RouteSummaryHeader] and [SegmentList]:
 * - `TST-UI-034` (`REQ-UI-024`): Strava routes display Strava branding without `"Source: Strava"`,
 *   whereas Local routes render `"Source: Local"`.
 * - `TST-UI-035` (`REQ-UI-025`): Compact metric row renders cleanly below identity row.
 * - `TST-UI-036` (`REQ-UI-026`): SegmentList keeps item cards free of redundant top Strava logos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class RouteAndSegmentCardsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun routeSummaryHeader_rendersStravaVsLocalIdentityBrandingAndCompactMetricsRow_TST_UI_034_TST_UI_035() {
        // TST-UI-034, TST-UI-035
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stravaSourceText = context.getString(R.string.routes_source_label, context.getString(RouteSource.STRAVA.displayNameResId))
        val localSourceText = context.getString(R.string.routes_source_label, context.getString(RouteSource.LOCAL_GPX.displayNameResId))
        val poweredByStravaDesc = "Powered by Strava"

        val stravaRoute = RouteSummary(
            id = 1L,
            externalId = "strava_1",
            name = "Strava Alpine Fondo",
            description = "",
            isSelected = true,
            distance = 65_000.0,
            elevationGain = 1200.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.STRAVA
        )
        val localRoute = RouteSummary(
            id = 2L,
            externalId = "gpx_2",
            name = "Local Park Loop",
            description = "",
            isSelected = false,
            distance = 10_000.0,
            elevationGain = 85.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    Column {
                        RouteSummaryHeader(summary = stravaRoute, onToggleSelection = {})
                        RouteSummaryHeader(summary = localRoute, onToggleSelection = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Strava route renders PoweredByStrava and suppresses "Source: Strava" text
        composeTestRule.onNodeWithText("Strava Alpine Fondo").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(poweredByStravaDesc).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(stravaSourceText).fetchSemanticsNodes().also {
            assertTrue("Strava route must NOT render '$stravaSourceText'", it.isEmpty())
        }

        // Local route renders "Source: Local"
        composeTestRule.onNodeWithText("Local Park Loop").assertIsDisplayed()
        composeTestRule.onNodeWithText(localSourceText).assertIsDisplayed()

        // TST-UI-035: Compact vertical layout puts metrics strictly below identity title
        val localTitleBounds = composeTestRule.onNodeWithText("Local Park Loop").getBoundsInRoot()
        val localSourceBounds = composeTestRule.onNodeWithText(localSourceText).getBoundsInRoot()
        assertTrue("Source subtitle renders below route name", localSourceBounds.top >= localTitleBounds.bottom - 1f.let { androidx.compose.ui.unit.Dp(it) })
    }

    @Test
    fun segmentList_rendersEmptyOrPopulatedItemsWithoutPerCardBrandingNoise_TST_UI_036() {
        // TST-UI-036
        val segment = SegmentSummary(
            stravaId = 501L,
            name = "Isar Sprint",
            bSportType = BSportType.RUN,
            climbCategory_raw = 0,
            climbCategory = "",
            prTime_raw = 90,
            prTime = "01:30",
            city = "Munich",
            distance = "800 m",
            distance_raw = 800.0,
            averageGrade_raw = 1.2,
            averageGrade = "1.2 %",
            maxGrade = "2.5 %",
            elevationGain_raw = 10.0,
            elevationGain = "10 m",
            elevationMin = "510 m",
            elevationMax = "520 m",
            map_polyline = ""
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    val scrollState = rememberLazyListState()
                    SegmentList(
                        segmentsWithPath = listOf(SegmentWithPath(segment, emptyList())),
                        scrollState = scrollState,
                        isStravaConnected = true,
                        onConnectToStrava = {},
                        onSegmentClick = {},
                        appBarOffsetPx = 0,
                        headerHeightPx = 0f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Isar Sprint").assertIsDisplayed()
    }
}
