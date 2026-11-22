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

package com.atrainingtracker.trainingtracker.ui.components.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetBody
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose WindowInsets tests verifying:
 * 1. [FilterBottomSheetBody] applies both `navigationBarsPadding()` and `imePadding()` so filter
 *    text fields lift above the software keyboard (`REQ-UI-2799.3`, `TST-UI-2799.3`).
 * 2. [AppBottomSheetContent] applies `navigationBarsPadding()` and `imePadding()` strictly once
 *    (1x delta, zero double-padding) when hosted without an outer `windowInsetsPadding` wrapper
 *    (`ATrainingTrackerApp`, `REQ-UI-2799.3`, `ATT-8125`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class BottomSheetInsetsComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFilterBottomSheetBody_shiftsInputUpwardBySingleImeInset_tstUi2799_3() {
        lateinit var rootView: android.view.View

        composeTestRule.setContent {
            rootView = LocalView.current
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    FilterBottomSheetBody(
                        title = "Filter",
                        onDismissRequest = {},
                        onClearAll = {},
                        onApply = {}
                    ) {
                        OutlinedTextField(
                            value = "Climb",
                            onValueChange = {},
                            label = { Text("Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("filter_search_input")
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val baselineInputBottom = composeTestRule.onNodeWithTag("filter_search_input")
            .getUnclippedBoundsInRoot().bottom

        // Dispatch synthetic WindowInsets: navigationBars = 48px (16dp at xxhdpi/3.0), ime = 300px (100dp at xxhdpi/3.0)
        val navBarPx = 48
        val imePx = 300
        composeTestRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rootView, insets)
        }
        composeTestRule.waitForIdle()

        val liftedInputBottom = composeTestRule.onNodeWithTag("filter_search_input")
            .getUnclippedBoundsInRoot().bottom
        val shiftDp = baselineInputBottom - liftedInputBottom

        // At xxhdpi (density = 3.0), 300px IME lifts content by ~100dp (strictly 1x IME, never 0dp or 2x = 200dp)
        assertTrue(
            "Filter input must shift upward by ~100dp when 300px IME is shown (actual shift=$shiftDp)",
            shiftDp in 90.dp..115.dp
        )
    }

    @Test
    fun testAppBottomSheetContent_appliesBottomNavigationAndImeInsetsStrictlyOnce_tstUi2799_3() {
        lateinit var rootView: android.view.View

        composeTestRule.setContent {
            rootView = LocalView.current
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AppBottomSheetContent(
                        title = "Settings",
                        onDismissRequest = {}
                    ) {
                        Text(
                            text = "Settings Field",
                            modifier = Modifier.testTag("settings_sheet_content_marker")
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val baselineBottom = composeTestRule.onNodeWithTag("settings_sheet_content_marker")
            .getUnclippedBoundsInRoot().bottom

        // Dispatch synthetic WindowInsets: navigationBars = 60px (20dp), ime = 300px (100dp)
        // With single navigationBarsPadding().imePadding() (no outer double wrapper), total bottom inset = max(navBar, ime) = 100dp
        composeTestRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 60))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rootView, insets)
        }
        composeTestRule.waitForIdle()

        val shiftedBottom = composeTestRule.onNodeWithTag("settings_sheet_content_marker")
            .getUnclippedBoundsInRoot().bottom
        val deltaDp = baselineBottom - shiftedBottom

        // Strictly 1x inset (100dp), confirming no double padding (which would be ~200dp)
        assertEquals(
            "AppBottomSheetContent must apply IME/navigationBars padding strictly once (~100dp)",
            100f,
            deltaDp.value,
            10f
        )
    }
}
