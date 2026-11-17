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

package com.atrainingtracker.trainingtracker.ui.hrv

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Compose UI & Rule 8.5 Visual Screenshot tests for [MorningHrvCheckContent]
 * verifying pure wellness mode gating, HRV fallbacks, and empty-state rendering
 * (`REQ-HRV-043`, `TST-HRV-043.2`..`TST-HRV-043.4`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h1000dp-xhdpi")
class MorningHrvCheckScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testPureWellnessMode_hidesHrvCardsAndPromotesSaveWellness_TST_HRV_043() {
        var saveClicked = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true
                    ),
                    onNavigateBack = {},
                    onSaveStandaloneWellness = { saveClicked++ }
                )
            }
        }

        // Wellness TopAppBar title & subtitle + card title shown
        composeTestRule.onNodeWithText(context.getString(R.string.hrv_morning_check_start_button))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_wellness_subtitle))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_wellness_title))
            .assertIsDisplayed()

        // Wellness card & primary Save Morning Wellness CTA shown
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, saveClicked)

        // All HRV measurement UI & info button hidden
        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_info_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_start_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button").assertDoesNotExist()
    }

    @Test
    fun testPureWellnessMode_savedStateAndEmptyHint_TST_HRV_043() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = false,
                        enableMorningEnergy = false,
                        enableMuscleSoreness = false,
                        enableContextTags = false,
                        isWellnessSaved = true
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("morning_hrv_wellness_empty_hint").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_wellness_empty_hint))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_btn_saved))
            .assertIsDisplayed()
    }

    @Test
    fun testEnabledHrvMode_keepsFullHrvAndWellnessLayout_TST_HRV_043() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = true,
                        enableMorningSleepAndWellness = true
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_info_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_start_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button").assertDoesNotExist()
    }

    @Test
    fun testDirectProfileFallback_keepsHrvLayoutWhenBothTogglesFalse_TST_HRV_043() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = false
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_title)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_info_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_start_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").assertDoesNotExist()
    }

    @Test
    fun testActiveMeasurement_keepsHrvLayoutEvenIfStartToggleFalse_TST_HRV_043() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.MEASURING,
                        remainingSec = 42,
                        currentHeartRateBpm = 54,
                        beatCount = 18,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = true
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_btn_cancel))
            .assertIsDisplayed()
    }

    @Test
    fun testCapturePureWellnessModeLightAndDarkScreenshot_TST_HRV_043() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("pure_wellness_light_dark"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(740.dp)
                    ) {
                        MorningHrvCheckContent(
                            uiState = MorningHrvUiState(
                                phase = MorningHrvPhase.READY,
                                showMorningCheckOnStartScreen = false,
                                enableMorningSleepAndWellness = true,
                                enableSleepDurationAndQuality = true,
                                enableMorningEnergy = true,
                                enableMuscleSoreness = true,
                                enableContextTags = false
                            ),
                            onNavigateBack = {}
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(740.dp)
                    ) {
                        MorningHrvCheckContent(
                            uiState = MorningHrvUiState(
                                phase = MorningHrvPhase.READY,
                                showMorningCheckOnStartScreen = false,
                                enableMorningSleepAndWellness = true,
                                enableSleepDurationAndQuality = true,
                                enableMorningEnergy = true,
                                enableMuscleSoreness = false,
                                enableContextTags = false,
                                isWellnessSaved = true
                            ),
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("pure_wellness_light_dark")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8598_morning_wellness_mode.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outFile.exists() && outFile.length() > 0)
    }
}
