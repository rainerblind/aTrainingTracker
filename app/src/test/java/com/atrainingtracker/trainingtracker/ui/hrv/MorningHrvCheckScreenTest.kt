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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun testForceFullHrvWithWellnessEnabled_preservesFullHrvUiAndOpensInfoSheet_TST_HRV_045() {
        var startClicked = 0
        var saveWellnessClicked = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true,
                        currentHeartRateBpm = 54
                    ),
                    onNavigateBack = {},
                    forceFullHrv = true,
                    onStart = { startClicked++ },
                    onSaveStandaloneWellness = { saveWellnessClicked++ }
                )
            }
        }

        // Full HRV TopAppBar metadata & info action preserved despite showMorningCheckOnStartScreen = false
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_info_button").assertIsDisplayed()

        // Full HRV cards + Wellness card + primary Start Measurement + secondary Save Morning Wellness
        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_start_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button").assertDoesNotExist()
        assertEquals(1, startClicked)
        assertEquals(1, saveWellnessClicked)

        // Clicking info button opens HrvInfoBottomSheet with HRV info content
        composeTestRule.onNodeWithTag("morning_hrv_info_button").performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.hrv_info_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.hrv_info_trailing_lag_title)).assertIsDisplayed()
    }

    @Test
    fun testForceFullHrvWithWellnessDisabled_showsFullHrvWithoutWellnessCard_TST_HRV_045() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = false,
                        currentHeartRateBpm = 56
                    ),
                    onNavigateBack = {},
                    forceFullHrv = true
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
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button").assertDoesNotExist()
    }

    @Test
    fun testStartScreenEntry_forceFullHrvFalseRetainsPureWellnessMode_TST_HRV_045() {
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
                    forceFullHrv = false
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.hrv_morning_check_start_button)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_wellness_subtitle)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_wellness_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_primary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_protocol_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_pacing_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_info_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_start_button").assertDoesNotExist()
    }

    @Test
    fun testCaptureForceFullHrvLightAndDarkScreenshot_TST_HRV_045() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("force_full_hrv_light_dark"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(940.dp)
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
                                currentHeartRateBpm = 54
                            ),
                            onNavigateBack = {},
                            forceFullHrv = true
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(940.dp)
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
                                currentHeartRateBpm = 54
                            ),
                            onNavigateBack = {},
                            forceFullHrv = true
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("force_full_hrv_light_dark")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8846_morning_hrv_force_full.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outFile.exists() && outFile.length() > 0)
    }

    @Test
    fun testNoHrSensorConnected_disablesStartShowsGuidanceAndKeepsStandaloneWellnessEnabled_TST_HRV_052() {
        // REQ-HRV-052.3 / TST-HRV-052.2
        var startClicked = 0
        var saveWellnessClicked = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = true,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true,
                        currentHeartRateBpm = null,
                        isHrSensorAvailable = false
                    ),
                    onNavigateBack = {},
                    onStart = { startClicked++ },
                    onSaveStandaloneWellness = { saveWellnessClicked++ }
                )
            }
        }

        // Disconnected status badge and connection hint are displayed; misleading Ready badge is hidden
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_state_no_sensor))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_no_sensor_guidance")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_no_sensor_hint))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_state_ready))
            .assertDoesNotExist()

        // Start Measurement button is disabled and does not fire onStart
        composeTestRule.onNodeWithTag("morning_hrv_start_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, startClicked)

        // Standalone Morning Wellness save remains enabled and clickable without HR sensor
        composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, saveWellnessClicked)
    }

    @Test
    fun testHrSensorAvailable_enablesStartAndHidesDisconnectedGuidance_TST_HRV_052() {
        // REQ-HRV-052.3 / TST-HRV-052.2
        var startClicked = 0
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = true,
                        enableMorningSleepAndWellness = true,
                        currentHeartRateBpm = 56,
                        isHrSensorAvailable = true
                    ),
                    onNavigateBack = {},
                    onStart = { startClicked++ }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_state_ready))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_state_no_sensor))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_no_sensor_guidance")
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag("morning_hrv_start_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, startClicked)
    }

    @Test
    fun testHrSensorConnectedBeforeFirstBeat_enablesStartWithoutDisconnectedGuidance_TST_HRV_052() {
        // REQ-HRV-052.1 / REQ-HRV-052.3 / TST-HRV-052.2
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = true,
                        enableMorningSleepAndWellness = false,
                        currentHeartRateBpm = null,
                        isHrSensorAvailable = true
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_state_ready))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_no_sensor_guidance")
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_start_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun testFullHrvLayoutOrder_keepsHrvBlockCohesiveAboveMorningWellnessSection_ATT_9318() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.READY,
                        showMorningCheckOnStartScreen = false,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true,
                        currentHeartRateBpm = 54,
                        isHrSensorAvailable = true
                    ),
                    onNavigateBack = {},
                    forceFullHrv = true
                )
            }
        }

        val protocolBounds = composeTestRule.onNodeWithTag("morning_hrv_protocol_card").getUnclippedBoundsInRoot()
        val postureBounds = composeTestRule.onNodeWithTag("morning_hrv_posture_duration_card").getUnclippedBoundsInRoot()
        val pacingBounds = composeTestRule.onNodeWithTag("morning_hrv_pacing_card").getUnclippedBoundsInRoot()
        val startBtnBounds = composeTestRule.onNodeWithTag("morning_hrv_start_button").getUnclippedBoundsInRoot()
        val wellnessBounds = composeTestRule.onNodeWithTag("morning_hrv_wellness_card").getUnclippedBoundsInRoot()
        val saveWellnessBounds = composeTestRule.onNodeWithTag("morning_hrv_save_wellness_secondary_button").getUnclippedBoundsInRoot()

        assertTrue(
            "Protocol (${protocolBounds.top}) must sit above Posture/Duration (${postureBounds.top})",
            protocolBounds.bottom <= postureBounds.top
        )
        assertTrue(
            "Posture/Duration (${postureBounds.bottom}) must sit directly above Pacing/Ready (${pacingBounds.top})",
            postureBounds.bottom <= pacingBounds.top
        )
        assertTrue(
            "Pacing/Ready (${pacingBounds.bottom}) must sit directly above Start Measurement CTA (${startBtnBounds.top})",
            pacingBounds.bottom <= startBtnBounds.top
        )
        assertTrue(
            "Start Measurement CTA (${startBtnBounds.bottom}) must sit above Morning Sleep & Wellness (${wellnessBounds.top})",
            startBtnBounds.bottom <= wellnessBounds.top
        )
        assertTrue(
            "Morning Sleep & Wellness (${wellnessBounds.bottom}) must sit above Save Morning Wellness CTA (${saveWellnessBounds.top})",
            wellnessBounds.bottom <= saveWellnessBounds.top
        )
    }

    @Test
    fun testMeasuringPhaseLayoutOrder_keepsCancelButtonAboveWellnessCard_ATT_9318() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.MEASURING,
                        remainingSec = 42,
                        currentHeartRateBpm = 54,
                        beatCount = 18,
                        showMorningCheckOnStartScreen = true,
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true
                    ),
                    onNavigateBack = {}
                )
            }
        }

        val pacingBounds = composeTestRule.onNodeWithTag("morning_hrv_pacing_card").getUnclippedBoundsInRoot()
        val cancelBounds = composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_btn_cancel))
            .getUnclippedBoundsInRoot()
        val wellnessBounds = composeTestRule.onNodeWithTag("morning_hrv_wellness_card").getUnclippedBoundsInRoot()

        assertTrue(
            "Pacing card (${pacingBounds.bottom}) must sit above Cancel button (${cancelBounds.top})",
            pacingBounds.bottom <= cancelBounds.top
        )
        assertTrue(
            "Cancel button (${cancelBounds.bottom}) must sit above Wellness card (${wellnessBounds.top})",
            cancelBounds.bottom <= wellnessBounds.top
        )
    }
}
