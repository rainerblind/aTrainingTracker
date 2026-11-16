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
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.MorningCheckSection
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
 * Rule 8.5 Visual Compose Screenshot verification for ATT-7740 (TST-UI-525.6, REQ-UI-525.1).
 *
 * Renders [MorningCheckSection] in Light and Dark themes verifying:
 * - Default morning check cutoff hour 08:00 (`DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8`)
 * - Upper bound capped at 11:00 (`MAX_MORNING_CHECK_CUTOFF_HOUR == 11`, `steps = 4` -> 6 stops `06:00..11:00`)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h1000dp-xhdpi")
class MorningCheckCutoffScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureMorningCheckCutoffSliderScreenshot() {
        // TST-UI-525.6
        assertEquals(8, TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR)
        assertEquals(6, TuningPreferencesDefaults.MIN_MORNING_CHECK_CUTOFF_HOUR)
        assertEquals(11, TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR)

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(840.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("morning_cutoff_comparison"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        MorningCheckCutoffPreviewCard(title = "Light Theme (Default 08:00, Range 06:00-11:00)")
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        MorningCheckCutoffPreviewCard(title = "Dark Theme (Default 08:00, Range 06:00-11:00)")
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("morning_cutoff_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-7740_morning_cutoff.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }

    @Composable
    private fun MorningCheckCutoffPreviewCard(title: String) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            TuningAccordionSection(
                icon = Icons.Default.WbSunny,
                title = stringResource(R.string.tuning_cat_morning_check),
                subtitle = TuningSubtitleFormatter.formatMorningCheckSubtitle(
                    enableMorningSleepAndWellness = true,
                    showMorningCheckOnStartScreen = true,
                    morningCheckCutoffHour = TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR,
                    context = context
                ),
                isExpanded = true,
                onToggle = {}
            ) {
                MorningCheckSection(
                    enableMorningSleepAndWellness = true,
                    enableSleepDurationAndQuality = true,
                    enableMorningEnergy = true,
                    enableMuscleSoreness = false,
                    enableContextTags = true,
                    showMorningCheckOnStartScreen = true,
                    morningCheckCutoffHour = TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR
                )
            }
        }
    }
}
