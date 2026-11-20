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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityStatus
import com.atrainingtracker.trainingtracker.database.hrv.CalibrationProgressState
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessResult
import com.atrainingtracker.trainingtracker.database.hrv.HrvSensorModality
import com.atrainingtracker.trainingtracker.database.hrv.ReadinessStatus
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvReadinessTrendCard
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvReadinessTrendMath
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.HrvTrendMetricMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.ln

/**
 * Behavioral Compose UI verification & Rule 8.5 Light/Dark PNG capture for Polar Verity Sense
 * optical PPI morning HRV guidance, provenance badges, and trend card segmented modality selector
 * (`REQ-HRV-049.2`, `REQ-HRV-049.4`, `TST-HRV-049.2`, `TST-HRV-049.4`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w860dp-h1000dp-xhdpi")
class MorningHrvOpticalPpiUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testOpticalPpiMorningHrv_showsGuidanceProvenanceAndOpticalArtifactAdvice_TST_HRV_049() {
        // REQ-HRV-049.2 / TST-HRV-049.2: Optical guidance banner, optical armband artifact copy, provenance badge, no smoothed ECG warning
        val opticalEntry = HrvReadinessEntry(
            timestampMs = 1732080000000L,
            dateIso = "2026-11-20",
            timezoneId = "UTC",
            restingHrBpm = 53,
            rmssdMs = 56.2,
            lnRmssd = 4.029,
            hrvScore100 = 80.6,
            sdnnMs = 63.0,
            artifactRatePct = 7.4,
            durationSec = 60,
            posture = HrvPosture.SEATED,
            sensorModality = HrvSensorModality.OPTICAL_PPI
        )
        val opticalReadiness = HrvReadinessResult(
            targetDateIso = "2026-11-20",
            posture = HrvPosture.SEATED,
            status = ReadinessStatus.CALIBRATING,
            mu7d = null,
            n7d = 1,
            mu60d = null,
            sigma60d = null,
            n60d = 1,
            swcLower = null,
            swcUpper = null,
            calibrationDaysCount = 1,
            calibrationProgressMessage = "Day 1 of 14 baseline calibration",
            todayEntry = opticalEntry,
            calibrationState = CalibrationProgressState.BASELINE_60D
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                MorningHrvCheckContent(
                    uiState = MorningHrvUiState(
                        phase = MorningHrvPhase.COMPLETED,
                        posture = HrvPosture.SEATED,
                        currentHeartRateBpm = 53,
                        isOpticalPpiSession = true,
                        artifactRatePct = 7.4,
                        highArtifactWarning = true,
                        signalQualityStatus = HrvSignalQualityStatus.HIGH_ARTIFACTS,
                        computedEntry = opticalEntry,
                        readinessResult = opticalReadiness
                    ),
                    onNavigateBack = {},
                    forceFullHrv = true
                )
            }
        }

        composeTestRule.onNodeWithTag("morning_hrv_optical_guidance_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_optical_guidance_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.morning_hrv_optical_artifact_warning_desc, 7.4))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("morning_hrv_smoothed_warning_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("morning_hrv_optical_provenance_badge").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testTrendCard_segmentedModalitySelectorSwitchesBetweenEcgAndOpticalCohorts_TST_HRV_049() {
        // REQ-HRV-049.4 / TST-HRV-049.4: SingleChoiceSegmentedButtonRow toggles isolated ECG vs Optical PPI cohorts cleanly
        val base = LocalDate.of(2026, 11, 1)
        val entries = (0 until 14).map { i ->
            HrvReadinessEntry(
                id = (i + 1).toLong(),
                timestampMs = 1730419200000L + i * 86_400_000L,
                dateIso = base.plusDays(i.toLong()).toString(),
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 64.0,
                lnRmssd = ln(64.0),
                hrvScore100 = 83.0,
                durationSec = 60,
                posture = HrvPosture.SUPINE,
                isPrimaryForDay = true,
                sensorModality = HrvSensorModality.ECG
            )
        } + (10 until 14).map { i ->
            HrvReadinessEntry(
                id = (100 + i).toLong(),
                timestampMs = 1730419201000L + i * 86_400_000L,
                dateIso = base.plusDays(i.toLong()).toString(),
                timezoneId = "UTC",
                restingHrBpm = 54,
                rmssdMs = 46.0,
                lnRmssd = ln(46.0),
                hrvScore100 = 76.5,
                durationSec = 60,
                posture = HrvPosture.SUPINE,
                isPrimaryForDay = true,
                sensorModality = HrvSensorModality.OPTICAL_PPI
            )
        }

        // Load present strictly on early ECG dates (Nov 1-5), absent on later Optical PPI range (Nov 11-14)
        val dailyLoads = mapOf(base.plusDays(1).toString() to 85.0)
        val series = HrvReadinessTrendMath.calculateTrendSeries(
            entries = entries,
            selectedPosture = HrvPosture.SUPINE,
            dailyTrainingLoads = dailyLoads
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                HrvReadinessTrendCard(
                    series = series,
                    selectedPosture = HrvPosture.SUPINE,
                    onSelectPosture = {},
                    metricMode = HrvTrendMetricMode.LN_RMSSD,
                    onSelectMetricMode = {},
                    showTrainingLoad = true,
                    onToggleTrainingLoad = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("hrv_trend_modality_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hrv_trend_optical_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hrv_trend_training_load_legend").assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.hrv_modality_optical_ppi_short)).performClick()
        composeTestRule.onNodeWithTag("hrv_trend_optical_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hrv_trend_training_load_legend").assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.hrv_modality_ecg_short)).performClick()
        composeTestRule.onNodeWithTag("hrv_trend_optical_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hrv_trend_training_load_legend").assertIsDisplayed()
    }

    @Test
    fun testCaptureOpticalPpiMorningHrvLightDarkPng_TST_HRV_049() {
        // REQ-HRV-049.2 / TST-HRV-049.2: Rule 8.5 deterministic side-by-side Light/Dark PNG strictly for ATT-9177
        val opticalEntry = HrvReadinessEntry(
            timestampMs = 1732080000000L,
            dateIso = "2026-11-20",
            timezoneId = "UTC",
            restingHrBpm = 53,
            rmssdMs = 58.4,
            lnRmssd = 4.067,
            hrvScore100 = 81.3,
            sdnnMs = 66.2,
            artifactRatePct = 1.4,
            durationSec = 60,
            posture = HrvPosture.SEATED,
            sensorModality = HrvSensorModality.OPTICAL_PPI
        )
        val readiness = HrvReadinessResult(
            targetDateIso = "2026-11-20",
            posture = HrvPosture.SEATED,
            status = ReadinessStatus.CALIBRATING,
            mu7d = null,
            n7d = 3,
            mu60d = null,
            sigma60d = null,
            n60d = 3,
            swcLower = null,
            swcUpper = null,
            calibrationDaysCount = 3,
            calibrationProgressMessage = "Day 3 of 14 baseline calibration",
            todayEntry = opticalEntry,
            calibrationState = CalibrationProgressState.BASELINE_60D
        )
        val uiState = MorningHrvUiState(
            phase = MorningHrvPhase.COMPLETED,
            posture = HrvPosture.SEATED,
            currentHeartRateBpm = 53,
            isOpticalPpiSession = true,
            artifactRatePct = 1.4,
            highArtifactWarning = false,
            signalQualityStatus = HrvSignalQualityStatus.VALID,
            computedEntry = opticalEntry,
            readinessResult = readiness
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("optical_ppi_light_dark"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Box(modifier = Modifier.weight(1f).height(940.dp)) {
                        MorningHrvCheckContent(uiState = uiState, onNavigateBack = {}, forceFullHrv = true)
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Box(modifier = Modifier.weight(1f).height(940.dp)) {
                        MorningHrvCheckContent(uiState = uiState, onNavigateBack = {}, forceFullHrv = true)
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("optical_ppi_light_dark")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-9177_morning_hrv_optical_ppi.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outFile.exists() && outFile.length() > 0)
    }
}
