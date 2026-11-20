/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import android.app.Application
import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification for [PeriodSummaryCard]:
 * - `TST-UI-038` (`REQ-UI-028`): Detailed sub-sport rows and highlighted Longest Workout section.
 * - `TST-UI-053` (`REQ-UI-052`): Clicking Longest Workout triggers navigation with exact workout ID.
 * - `TST-UI-074` (`REQ-UI-116`): Weekly period card renders explicit date range subtitle below title.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class PeriodSummaryCardUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun periodSummaryCard_rendersSubSportsDateRangeAndTriggersLongestWorkoutNavigation_TST_UI_038_053_074() {
        // TST-UI-038, TST-UI-053, TST-UI-074
        val context = ApplicationProvider.getApplicationContext<Context>()
        val longestTargetId = 777L
        val summary = PeriodSummary(
            periodLabel = "Week 20, 2026",
            periodDateRange = "May 11 - May 17",
            periodType = PeriodType.WEEK,
            startTimestampS = 0L,
            endTimestampS = 0L,
            totalWorkouts = 4,
            totalDurationSec = 7200L,
            totalDistance = 65_000.0,
            sportStats = mapOf(
                BSportType.BIKE to SportStats(
                    count = 4,
                    totalDurationSec = 7200L,
                    totalDistanceMeters = 65_000.0,
                    totalAscentMeters = 640L,
                    detailedSportStats = mapOf(
                        "Road Cycling" to DetailedStats(count = 3, totalDurationSec = 5400L, totalDistanceMeters = 50_000.0, totalAscentMeters = 440L),
                        "MTB" to DetailedStats(count = 1, totalDurationSec = 1800L, totalDistanceMeters = 15_000.0, totalAscentMeters = 200L)
                    ),
                    longestWorkout = LongestWorkout(
                        id = longestTargetId,
                        name = "Alpine Fondo",
                        durationSec = 3600L,
                        distanceMeters = 35_000.0,
                        ascentMeters = 400L
                    )
                )
            ),
            sortKey = "2026-20"
        )

        var clickedLongestId: Long? = null
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    PeriodSummaryCard(
                        summary = summary,
                        isPlayServiceAvailable = false,
                        onHeaderClick = {},
                        onMapClick = { _ -> },
                        onSportClick = { _, _ -> },
                        onLongestWorkoutClick = { _, _, id -> clickedLongestId = id }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // TST-UI-074: Explicit weekly date range subtitle rendered directly below title
        val titleNode = composeTestRule.onNodeWithText("Week 20, 2026", useUnmergedTree = true).assertIsDisplayed()
        val dateRangeNode = composeTestRule.onNodeWithText("May 11 - May 17", useUnmergedTree = true).assertIsDisplayed()
        val titleBounds = titleNode.getBoundsInRoot()
        val dateRangeBounds = dateRangeNode.getBoundsInRoot()
        assertTrue(
            "Date range subtitle must render below week title (title.bottom=${titleBounds.bottom}, date.top=${dateRangeBounds.top})",
            dateRangeBounds.top >= titleBounds.bottom
        )

        // TST-UI-038: Sub-sport breakdown & Longest Workout highlight
        composeTestRule.onNodeWithText("Road Cycling", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("MTB", substring = true).assertIsDisplayed()
        val longestHeader = context.getString(R.string.workout_periods__longest_workout)
        composeTestRule.onNodeWithText(longestHeader, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Alpine Fondo", substring = true).assertIsDisplayed()

        // TST-UI-053: Clicking Longest Workout navigates with exact workout ID
        composeTestRule.onNodeWithText("Alpine Fondo", substring = true).performClick()
        assertEquals(longestTargetId, clickedLongestId)
    }
}
