/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
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
 * Visual Compose screenshot test for [SensorDiagnosticShareSheet] (Rule 8.5, TST-EXP-044.5).
 * Generates side-by-side Light and Dark theme renderings for inspection in docs/engineering/walkthroughs/img/ATT-8020_diagnostic_share_sheet.png.
 */
// TST-EXP-044.5
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h1200dp-xhdpi")
class SensorDiagnosticShareSheetScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureDiagnosticShareSheetScreenshot() {
        // TST-EXP-044.5
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(960.dp)
                    .background(Color(0xFFE0E0E0))
                    .padding(24.dp)
                    .testTag("diagnostic_sheet_screenshot_container"),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Light Theme
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Light Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ATrainingTrackerTheme(darkTheme = false) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.diagnostic_share_sheet_title),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                SensorDiagnosticShareSheetContent(
                                    fileBaseName = "2026-11-16_14-30-00",
                                    includeGps = true,
                                    onIncludeGpsChange = {},
                                    notes = "Polar H10 dropouts at 42:15 when approaching high-voltage line",
                                    onNotesChange = {},
                                    isPackaging = false,
                                    onShareClick = {}
                                )
                            }
                        }
                    }
                }

                // Dark Theme
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dark Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ATrainingTrackerTheme(darkTheme = true) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.diagnostic_share_sheet_title),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                SensorDiagnosticShareSheetContent(
                                    fileBaseName = "2026-11-16_14-30-00",
                                    includeGps = false,
                                    onIncludeGpsChange = {},
                                    notes = "Heart rate spike without GPS coordinates (privacy opted out)",
                                    onNotesChange = {},
                                    isPackaging = false,
                                    onShareClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("diagnostic_sheet_screenshot_container")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8020_diagnostic_share_sheet.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist at ${outFile.absolutePath}", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }
}
