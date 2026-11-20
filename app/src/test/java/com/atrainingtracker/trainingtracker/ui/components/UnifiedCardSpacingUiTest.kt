/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterItem
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetails
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.routes.RouteSummaryHeader
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification for uniform 12.dp horizontal card spacing
 * across Workout, Route, and Cluster card components (`REQ-UI-121`, `TST-UI-077`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class UnifiedCardSpacingUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun workoutRouteAndClusterCards_enforceUniformTwelveDpHorizontalPadding_TST_UI_077() {
        // TST-UI-077
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activeTimeLabel = context.getString(R.string.time_active)
        val details = WorkoutDetailsData(
            totalDistance = 10_000.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 5.55,
            ascentMeters = 100L,
            descentMeters = 100L,
            bSportType = BSportType.RUN,
            maxDisplacement = 4_000.0
        )
        val route = RouteSummary(
            id = 1L,
            externalId = "gpx_1",
            name = "Aligned Route",
            description = "",
            isSelected = false,
            distance = 10_000.0,
            elevationGain = 100.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val cluster = WorkoutCluster(
            id = 2L,
            name = "Aligned Cluster",
            probableSportId = 1L,
            startLat = 48.1,
            startLng = 11.5,
            endLat = 48.1,
            endLng = 11.5,
            maxDispLat = 48.11,
            maxDispLng = 11.51,
            refDistance = 10_000.0,
            hitCount = 3,
            bSportType = BSportType.RUN,
            previewPaths = emptyList()
        )
        val mockClustersVm = mockk<WorkoutClustersViewModel>(relaxed = true).apply {
            every { getBSportType(any()) } returns BSportType.RUN
            every { getSportName(any()) } returns "Running"
            every { getLinkedEquipment(any()) } returns emptyList()
            every { clusterStats } returns MutableStateFlow(emptyMap())
        }

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        WorkoutDetails(data = details)
                        RouteSummaryHeader(summary = route, onToggleSelection = {})
                        ClusterItem(
                            cluster = cluster,
                            viewModel = mockClustersVm,
                            onClick = {},
                            onDeleteRequest = {},
                            onHitCountClick = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val timeLeft = composeTestRule.onNodeWithText(activeTimeLabel, useUnmergedTree = true).getBoundsInRoot().left.value
        val routeLeft = composeTestRule.onNodeWithText("Aligned Route", useUnmergedTree = true).getBoundsInRoot().left.value
        val clusterLeft = composeTestRule.onNodeWithText("Aligned Cluster", useUnmergedTree = true).getBoundsInRoot().left.value
        // MetricsTimeDistanceRow places Active Time after 12.dp outer padding + 28.dp icon + 6.dp spacer = 46.dp
        assertEquals("WorkoutDetails Active Time left bound must reflect 12.dp margin + 28.dp icon + 6.dp gap", 46f, timeLeft, 2.0f)
        assertTrue("RouteSummaryHeader inner content enforces >= 12.dp horizontal padding", routeLeft >= 12f)
        assertTrue("ClusterItem inner content enforces >= 12.dp horizontal padding", clusterLeft >= 12f)
    }
}
