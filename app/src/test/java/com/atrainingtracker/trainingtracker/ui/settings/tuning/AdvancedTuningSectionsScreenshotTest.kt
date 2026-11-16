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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.MorningCheckSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.PhysiologyModelsSection
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.SensorsGpsFilterSection
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

/**
 * Rule 8.5 Visual Compose Screenshot verification for ATT-8117 (TST-SET-115.5, REQ-SET-113.4).
 *
 * Renders expanded Light and Dark rows across all 3 affected accordion sections side-by-side:
 * - Sensors, GPS & Filtering (hosting relocated HRV Artifact Gate slider)
 * - Physiology & Training Models (trimmed of Morning Check & HRV Artifact Gate, retaining RPE & Feel + HRV link)
 * - Morning Checks & Wellness (dedicated TuningSection.MORNING_CHECK with all 7 Morning Check controls)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h2200dp-xhdpi")
class AdvancedTuningSectionsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureTuningSectionsComparisonScreenshot() {
        // TST-SET-115
        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(1200.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("tuning_sections_comparison"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TuningSectionsPreviewRow(themeTitle = "Light Theme")
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TuningSectionsPreviewRow(themeTitle = "Dark Theme")
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("tuning_sections_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }

    @Composable
    private fun TuningSectionsPreviewRow(themeTitle: String) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = themeTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TuningAccordionSection(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.tuning_cat_sensors_gps),
                    subtitle = TuningSubtitleFormatter.formatSensorsGpsSubtitle(20f, 21, 0.5f, context),
                    isExpanded = true,
                    onToggle = {},
                    modifier = Modifier.weight(1f)
                ) {
                    SensorsGpsFilterSection(
                        gpsAccuracy = 20f,
                        onGpsAccuracyChange = {},
                        altitudeWindowSec = 21,
                        onAltitudeWindowChange = {},
                        slopeMinSpeed = 0.5f,
                        onSlopeMinSpeedChange = {},
                        postWorkoutErtsSmoothingEnabled = true,
                        onPostWorkoutErtsSmoothingChange = {},
                        hrvArtifactGateThresholdPercent = 4.5f,
                        onHrvArtifactGateThresholdPercentChange = {}
                    )
                }

                TuningAccordionSection(
                    icon = Icons.Default.MonitorHeart,
                    title = stringResource(R.string.tuning_cat_physiology_models),
                    subtitle = TuningSubtitleFormatter.formatPhysiologyModelsSubtitle(
                        enableCogganPowerMetrics = true,
                        enablePmcModel = false,
                        enableAerobicEfficiencyModel = true,
                        enableCriticalPowerModel = false,
                        enableCardioPowerDynamics = false,
                        context = context
                    ),
                    isExpanded = true,
                    onToggle = {},
                    modifier = Modifier.weight(1f)
                ) {
                    PhysiologyModelsSection(
                        enableCogganPowerMetrics = true,
                        onEnableCogganPowerMetricsChange = {},
                        enablePmcModel = false,
                        onEnablePmcModelChange = {},
                        enableAerobicEfficiencyModel = true,
                        onEnableAerobicEfficiencyModelChange = {},
                        enableCriticalPowerModel = false,
                        onEnableCriticalPowerModelChange = {},
                        showRpeAndFeel = true,
                        onShowRpeAndFeelChange = {}
                    )
                }

                TuningAccordionSection(
                    icon = Icons.Default.WbSunny,
                    title = stringResource(R.string.tuning_cat_morning_check),
                    subtitle = TuningSubtitleFormatter.formatMorningCheckSubtitle(
                        enableMorningSleepAndWellness = true,
                        showMorningCheckOnStartScreen = true,
                        morningCheckCutoffHour = 9,
                        context = context
                    ),
                    isExpanded = true,
                    onToggle = {},
                    modifier = Modifier.weight(1f)
                ) {
                    MorningCheckSection(
                        enableMorningSleepAndWellness = true,
                        enableSleepDurationAndQuality = true,
                        enableMorningEnergy = true,
                        enableMuscleSoreness = false,
                        enableContextTags = true,
                        showMorningCheckOnStartScreen = true,
                        morningCheckCutoffHour = 9
                    )
                }
            }
        }
    }
}
