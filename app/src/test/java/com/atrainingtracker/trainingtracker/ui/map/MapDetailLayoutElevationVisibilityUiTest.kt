/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI test verifying that [MapDetailLayout] hides
 * [ElevationProfile] completely when `showElevationProfile = false` (`REQ-UI-058`, `TST-UI-066`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MapDetailLayoutElevationVisibilityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mapDetailLayout_hidesElevationProfileWhenDisabledAndShowsWhenEnabled_TST_UI_066() {
        // TST-UI-066
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legendDesc = context.getString(R.string.legend)
        val path = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.10, 11.50), altitude = 500.0, timeSec = 0L),
            PathPoint(distance = 500.0, latLng = LatLng(48.11, 11.51), altitude = 540.0, timeSec = 60L)
        )
        var showElevation by mutableStateOf(false)

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    MapDetailLayout(
                        bSportType = BSportType.BIKE,
                        zoomFocus = MapZoomFocus.FIT_PRIMARY,
                        activeScrubPath = path,
                        header = { Text("Cluster Heatmap Header") },
                        showMap = false,
                        showZoomControls = true,
                        showElevationProfile = showElevation,
                        showTelemetryCharts = false
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Hidden when showElevationProfile = false
        composeTestRule.onNodeWithContentDescription(legendDesc).assertDoesNotExist()

        // Rendered when showElevationProfile = true
        composeTestRule.runOnIdle { showElevation = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(legendDesc).assertIsDisplayed()
    }
}
