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
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabPreset
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSection
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI tests for [WorkoutMasksAndCardsSection] verifying that the Details
 * Zone Card Display Mode selector follows [WorkoutDetailTabConfig] (`ZONES` / `GRADE_DISTRIBUTION`)
 * rather than legacy `WorkoutDetailPreferences.showZoneAnalysis`
 * (`ATT-8079` / `REQ-UI-2787.4` / `TST-UI-2787.4`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutMasksAndCardsBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TST-UI-2787_4 detailZoneSelectorFollowsDetailTabsRegardlessOfLegacyShowZoneAnalysis`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zoneLabel = context.getString(R.string.settings_zone_card_display_mode_title)

        val tabsWithZones = listOf(
            WorkoutDetailTabConfig(
                id = "tab_zones",
                preset = WorkoutDetailTabPreset.ZONES,
                sections = listOf(WorkoutDetailSectionType.ZONES)
            )
        )
        val tabsWithGradeOnly = listOf(
            WorkoutDetailTabConfig(
                id = "tab_zones",
                preset = WorkoutDetailTabPreset.ZONES,
                sections = listOf(WorkoutDetailSectionType.GRADE_DISTRIBUTION)
            )
        )
        val tabsWithoutZonesOrGrade = listOf(
            WorkoutDetailTabConfig(
                id = "tab_overview",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                sections = listOf(WorkoutDetailSectionType.DETAILS)
            )
        )

        var currentTabs by mutableStateOf(tabsWithZones)
        var currentDetailPrefs by mutableStateOf(WorkoutDetailPreferences(showZoneAnalysis = false))

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WorkoutMasksAndCardsSection(
                    workoutCardPrefs = WorkoutCardSectionPreferences(showZoneAnalysis = false),
                    onWorkoutCardPrefsChange = {},
                    workoutDetailPrefs = currentDetailPrefs,
                    onWorkoutDetailPrefsChange = {},
                    detailTabs = currentTabs
                )
            }
        }

        // Legacy showZoneAnalysis = false, but ZONES is in detailTabs -> selector MUST be visible
        composeTestRule.onNodeWithText(zoneLabel).assertIsDisplayed()

        // Only GRADE_DISTRIBUTION is in detailTabs -> selector MUST still be visible
        currentTabs = tabsWithGradeOnly
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(zoneLabel).assertIsDisplayed()

        // Legacy showZoneAnalysis = true, but neither ZONES nor GRADE_DISTRIBUTION is in detailTabs -> hidden
        currentTabs = tabsWithoutZonesOrGrade
        currentDetailPrefs = WorkoutDetailPreferences(showZoneAnalysis = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(zoneLabel).assertDoesNotExist()
    }
}
