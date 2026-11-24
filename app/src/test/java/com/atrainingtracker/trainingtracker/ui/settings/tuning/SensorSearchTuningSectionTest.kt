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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchPreferences
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorSearchTuningSection
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
 * Behavioral Compose UI tests for [SensorSearchTuningSection] verifying periodic re-search
 * toggle and `15s/30s/1m/2m` steady-state period segmented selector (`ATT-8965`, `ATT-9178`,
 * `REQ-CON-052.3`, `TST-CON-052.3`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class SensorSearchTuningSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPeriodicResearchControls_selectAndDisableUpdatePreferences() {
        var latestPrefs = SensorSearchPreferences()
        composeRule.setContent {
            ATrainingTrackerTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    var prefs by remember { mutableStateOf(SensorSearchPreferences()) }
                    SensorSearchTuningSection(
                        prefs = prefs,
                        onPrefsChange = {
                            prefs = it
                            latestPrefs = it
                        }
                    )
                }
            }
        }

        assertTrue(latestPrefs.periodicResearchEnabled)
        assertEquals(60, latestPrefs.periodicResearchFinalPeriodSec)

        composeRule.onNodeWithText(context.getString(R.string.prefs_periodic_research_enabled_title))
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_1m))
            .performScrollTo()
            .assertIsSelected()
            .assertIsEnabled()

        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_15s))
            .performScrollTo()
            .performClick()
        assertEquals(15, latestPrefs.periodicResearchFinalPeriodSec)
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_15s)).assertIsSelected()

        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_2m))
            .performScrollTo()
            .performClick()
        assertEquals(120, latestPrefs.periodicResearchFinalPeriodSec)
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_2m)).assertIsSelected()

        composeRule.onNode(
            isToggleable() and hasAnyAncestor(hasTestTag("tuning_periodic_research_toggle"))
        ).performScrollTo().assertIsOn().performClick()
        composeRule.onNode(
            isToggleable() and hasAnyAncestor(hasTestTag("tuning_periodic_research_toggle"))
        ).assertIsOff()
        assertFalse(latestPrefs.periodicResearchEnabled)
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_15s)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_30s)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_1m)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.prefs_research_period_2m)).assertIsNotEnabled()
    }
}
