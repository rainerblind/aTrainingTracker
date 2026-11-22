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

package com.atrainingtracker.trainingtracker.dialogs

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Compose UI & contract tests verifying pure Compose [StartOrResumeAlertDialog]
 * and [GPSDisabledAlertDialog] rendering, callback dispatch, and excision of legacy
 * `StartOrResumeDialog` `DialogFragment`, `StartOrResumeInterface`, and `GPSDisabledDialog`
 * (REQ-STB-012, REQ-STB-003, REQ-UI-2801 / ATT-2079, ATT-8129).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class StartOrResumeDialogContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testLegacyDialogFragmentAndInterfaceWrappers_areEliminated() {
        val retiredClasses = listOf(
            "com.atrainingtracker.trainingtracker.dialogs.StartOrResumeDialog",
            "com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface",
            "com.atrainingtracker.trainingtracker.dialogs.GPSDisabledDialog"
        )
        for (className in retiredClasses) {
            try {
                Class.forName(className)
                fail("Retired legacy class $className must be eliminated from classpath (REQ-UI-2801.1)")
            } catch (_: ClassNotFoundException) {
                // Expected
            }
        }
    }

    @Test
    fun testStartOrResumeAlertDialog_batteryKill_dispatchesAllCallbacks() {
        var startNewClicked = false
        var resumeClicked = false
        var batterySettingsClicked = false

        val diagnosis = KillDiagnosis(
            reason = KillReason.BATTERY_KILL,
            escalationLevel = 1,
            isStravaActive = true,
            shouldShowBatteryButton = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                StartOrResumeAlertDialog(
                    diagnosis = diagnosis,
                    onDismissRequest = {},
                    onStartNew = { startNewClicked = true },
                    onResume = { resumeClicked = true },
                    onDisableBatteryOptimization = { batterySettingsClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.unfinished_workout_title_battery))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.start_or_resume_dialog_message))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.action_disable_battery_optimization))
            .assertIsDisplayed()
            .performClick()
        assertTrue("onDisableBatteryOptimization must be invoked", batterySettingsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.start_new_workout))
            .assertIsDisplayed()
            .performClick()
        assertTrue("onStartNew must be invoked", startNewClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.resume_workout))
            .assertIsDisplayed()
            .performClick()
        assertTrue("onResume must be invoked", resumeClicked)
    }

    @Test
    fun testGPSDisabledAlertDialog_rendersLocalizedStringsAndDispatchesCallbacks() {
        var openSettingsClicked = false
        var dismissClicked = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                GPSDisabledAlertDialog(
                    onOpenLocationSettings = { openSettingsClicked = true },
                    onDismiss = { dismissClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.gps_disabled_on_device))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.gps_goto_settings))
            .assertIsDisplayed()
            .performClick()
        assertTrue("onOpenLocationSettings must be invoked", openSettingsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.Cancel))
            .assertIsDisplayed()
            .performClick()
        assertTrue("onDismiss must be invoked", dismissClicked)
    }
}
