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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.settings.RawSensorRetentionPolicy
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
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
 * Rule 8.5 Visual Compose Screenshot verification for ATT-8019 (TST-SET-119.5, REQ-SET-117.4).
 *
 * Renders [SensorsGpsFilterSection] with raw telemetry retention selector across Light and Dark themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h1200dp-xhdpi")
class SensorsGpsFilterScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TST-SET-119.5
    @Test
    fun testCaptureSensorsGpsFilterRetentionScreenshot() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("sensors_filter_comparison"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Light Theme (Raw Telemetry Retention: 30 days)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SensorsGpsFilterSection(
                                gpsAccuracy = 50.0f,
                                onGpsAccuracyChange = {},
                                altitudeWindowSec = 21,
                                onAltitudeWindowChange = {},
                                slopeMinSpeed = 0.5f,
                                onSlopeMinSpeedChange = {},
                                postWorkoutErtsSmoothingEnabled = false,
                                onPostWorkoutErtsSmoothingChange = {},
                                hrvArtifactGateThresholdPercent = 20.0f,
                                onHrvArtifactGateThresholdPercentChange = {},
                                rawSensorRetentionPolicy = RawSensorRetentionPolicy.DAYS_30,
                                onRawSensorRetentionPolicyChange = {}
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Dark Theme (Raw Telemetry Retention: 30 days)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SensorsGpsFilterSection(
                                gpsAccuracy = 50.0f,
                                onGpsAccuracyChange = {},
                                altitudeWindowSec = 21,
                                onAltitudeWindowChange = {},
                                slopeMinSpeed = 0.5f,
                                onSlopeMinSpeedChange = {},
                                postWorkoutErtsSmoothingEnabled = false,
                                onPostWorkoutErtsSmoothingChange = {},
                                hrvArtifactGateThresholdPercent = 20.0f,
                                onHrvArtifactGateThresholdPercentChange = {},
                                rawSensorRetentionPolicy = RawSensorRetentionPolicy.DAYS_30,
                                onRawSensorRetentionPolicyChange = {}
                            )
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("sensors_filter_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist at ${outFile.absolutePath}", outFile.exists())
        assertTrue("Screenshot file size must be non-empty", outFile.length() > 0)
    }
}
