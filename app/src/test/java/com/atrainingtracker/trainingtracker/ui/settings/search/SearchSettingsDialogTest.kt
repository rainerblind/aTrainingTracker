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

package com.atrainingtracker.trainingtracker.ui.settings.search

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Compose UI tests for [SearchSettingsDialog] verifying periodic re-search toggle,
 * final steady-state period selection, disabled state, and SharedPreferences persistence
 * (`ATT-8965`, `REQ-CON-052.3`, `TST-CON-052.3`, `REQ-PRO-132`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SearchSettingsDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun testDefaultRender_selectsFiveMinutes_andPersistsPeriodChangeOnSave() {
        var dismissed = false
        composeRule.setContent {
            ATrainingTrackerTheme {
                SearchSettingsDialog(onDismiss = { dismissed = true })
            }
        }

        composeRule.onNodeWithTag("periodic_research_toggle").performScrollTo()
        composeRule.onNodeWithText(context.getString(R.string.prefs_periodic_research_enabled_title)).assertExists()
        composeRule.onNodeWithTag("periodic_research_period_300").performScrollTo().assertIsSelected().assertIsEnabled()

        composeRule.onNodeWithTag("periodic_research_period_120").performClick()
        composeRule.onNodeWithTag("periodic_research_period_120").assertIsSelected()

        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()
        assertTrue(dismissed)
        assertTrue(SensorSearchSettings.isPeriodicResearchEnabled(context))
        assertEquals(120, SensorSearchSettings.getPeriodicResearchFinalPeriodSec(context))
    }

    @Test
    fun testDisablingPeriodicResearch_disablesPeriodButtons_andPersistsFalseOnSave() {
        composeRule.setContent {
            ATrainingTrackerTheme {
                SearchSettingsDialog(onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("periodic_research_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("periodic_research_period_300").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("periodic_research_period_60").assertIsNotEnabled()

        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()
        assertFalse(SensorSearchSettings.isPeriodicResearchEnabled(context))
    }
}
