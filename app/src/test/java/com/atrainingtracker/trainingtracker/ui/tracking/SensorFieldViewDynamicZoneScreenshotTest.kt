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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
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
 * Rule 8.5 Visual Compose Screenshot verification for ATT-7761 (TST-TRK-082.4, REQ-TRK-082.4).
 *
 * Captures Light and Dark/AMOLED rendering across:
 * - HR 5-zone Dynamic Zone sparkline (showTextColor = false)
 * - Power 7-zone Dynamic Zone sparkline reaching Zone 7 (dark-surface contrast fallback)
 * - HRV DFA alpha 1 3-zone inverted polarity Dynamic Zone sparkline
 * - Decoupled fixed Crimson sparkline with showTextColor = true (Zone 4 text)
 * - Placeholder (dashed) and flat-line fallback tiles
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp-xhdpi")
class SensorFieldViewDynamicZoneScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureDynamicZoneSparklineComparisonScreenshot() {
        // TST-TRK-082
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(720.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("dynamic_zone_sparkline_comparison"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        DynamicZoneTileColumn(title = "Light Theme")
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    ) {
                        DynamicZoneTileColumn(title = "Dark / AMOLED")
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("dynamic_zone_sparkline_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }

    @Composable
    private fun DynamicZoneTileColumn(title: String) {
        val hrThresholds = SparklineZoneThresholds(
            cutoffs = listOf(132f, 145f, 158f, 171f),
            zoneColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5)
        )
        val pwrThresholds = SparklineZoneThresholds(
            cutoffs = listOf(149f, 203f, 243f, 284f, 324f, 405f),
            zoneColors = listOf(
                TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4,
                TTColor.Zone5, TTColor.Zone6, TTColor.Zone7
            )
        )
        val dfaThresholds = SparklineZoneThresholds(
            cutoffs = listOf(0.50f, 0.75f),
            zoneColors = listOf(TTColor.Zone4, TTColor.Zone3, TTColor.Zone2),
            invertedPolarity = true
        )
        val plainOptions = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = false,
            showRightBar = false,
            showTextColor = false
        )
        val textZoneOnlyOptions = ZoneDisplayOptions(
            showBackground = false,
            showLeftBar = false,
            showRightBar = false,
            showTextColor = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 1. HR 5-Zone Dynamic Sparkline (showTextColor = false)
            SensorFieldView(
                fieldState = SensorFieldState(
                    configHash = 1,
                    sensorFieldId = 1,
                    rowNr = 0,
                    colNr = 0,
                    viewSize = ViewSize.NORMAL,
                    label = "HR (Dynamic Z1-5)",
                    filterDescription = "Inst.",
                    value = "175",
                    units = "bpm",
                    zoneColor = TTColor.Zone5,
                    zoneDisplayOptions = plainOptions,
                    sensorType = SensorType.HR,
                    showGraph = true,
                    sparklineSamples = listOf(124f, 134f, 147f, 160f, 175f, 168f, 176f),
                    sparklineSpanSeconds = 300,
                    graphColor = "DYNAMIC_ZONE",
                    sparklineZoneThresholds = hrThresholds
                ),
                screenMode = ScreenMode.TRACKING,
                modifier = Modifier.height(132.dp)
            )

            // 2. Power 7-Zone Dynamic Sparkline reaching Zone 7
            SensorFieldView(
                fieldState = SensorFieldState(
                    configHash = 2,
                    sensorFieldId = 2,
                    rowNr = 1,
                    colNr = 0,
                    viewSize = ViewSize.NORMAL,
                    label = "Power (Dynamic Z1-7)",
                    filterDescription = "3s",
                    value = "430",
                    units = "W",
                    zoneColor = TTColor.Zone7,
                    zoneDisplayOptions = plainOptions,
                    sensorType = SensorType.POWER,
                    showGraph = true,
                    sparklineSamples = listOf(130f, 190f, 250f, 305f, 360f, 430f),
                    sparklineSpanSeconds = 300,
                    graphColor = "DYNAMIC_ZONE",
                    sparklineZoneThresholds = pwrThresholds
                ),
                screenMode = ScreenMode.TRACKING,
                modifier = Modifier.height(132.dp)
            )

            // 3. HRV DFA alpha 1 (inverted polarity: >0.75 Green top .. <0.50 Red bottom)
            SensorFieldView(
                fieldState = SensorFieldState(
                    configHash = 3,
                    sensorFieldId = 3,
                    rowNr = 2,
                    colNr = 0,
                    viewSize = ViewSize.NORMAL,
                    label = "DFA alpha 1 (Inverted)",
                    filterDescription = "Live",
                    value = "0.42",
                    units = "",
                    zoneColor = TTColor.Zone4,
                    zoneDisplayOptions = plainOptions,
                    sensorType = SensorType.HRV_DFA_A1,
                    showGraph = true,
                    sparklineSamples = listOf(0.95f, 0.82f, 0.68f, 0.54f, 0.42f),
                    sparklineSpanSeconds = 300,
                    graphColor = "DYNAMIC_ZONE",
                    sparklineZoneThresholds = dfaThresholds
                ),
                screenMode = ScreenMode.TRACKING,
                modifier = Modifier.height(132.dp)
            )

            // 4. Decoupled Fixed Crimson Sparkline with showTextColor = true (Zone 4 text)
            SensorFieldView(
                fieldState = SensorFieldState(
                    configHash = 4,
                    sensorFieldId = 4,
                    rowNr = 3,
                    colNr = 0,
                    viewSize = ViewSize.NORMAL,
                    label = "HR (Fixed Crimson + Z4 Text)",
                    filterDescription = "Inst.",
                    value = "164",
                    units = "bpm",
                    zoneColor = TTColor.Zone4,
                    zoneDisplayOptions = textZoneOnlyOptions,
                    sensorType = SensorType.HR,
                    showGraph = true,
                    sparklineSamples = listOf(140f, 152f, 160f, 164f, 158f, 165f),
                    sparklineSpanSeconds = 300,
                    graphColor = "CRIMSON"
                ),
                screenMode = ScreenMode.TRACKING,
                modifier = Modifier.height(132.dp)
            )

            // 5 & 6. Placeholder & Flat-line fallbacks side-by-side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SensorFieldView(
                    fieldState = SensorFieldState(
                        configHash = 5,
                        sensorFieldId = 5,
                        rowNr = 4,
                        colNr = 0,
                        viewSize = ViewSize.SMALL,
                        label = "Placeholder",
                        filterDescription = "",
                        value = "--",
                        units = "bpm",
                        zoneColor = Color.Transparent,
                        zoneDisplayOptions = plainOptions,
                        sensorType = SensorType.HR,
                        showGraph = true,
                        sparklineSamples = emptyList(),
                        graphColor = "DYNAMIC_ZONE",
                        sparklineZoneThresholds = hrThresholds
                    ),
                    screenMode = ScreenMode.TRACKING,
                    modifier = Modifier.weight(1f).height(116.dp)
                )
                SensorFieldView(
                    fieldState = SensorFieldState(
                        configHash = 6,
                        sensorFieldId = 6,
                        rowNr = 4,
                        colNr = 1,
                        viewSize = ViewSize.SMALL,
                        label = "Flat Line Z3",
                        filterDescription = "",
                        value = "150",
                        units = "bpm",
                        zoneColor = TTColor.Zone3,
                        zoneDisplayOptions = plainOptions,
                        sensorType = SensorType.HR,
                        showGraph = true,
                        sparklineSamples = listOf(150f, 150f, 150f),
                        sparklineSpanSeconds = 60,
                        graphColor = "DYNAMIC_ZONE",
                        sparklineZoneThresholds = hrThresholds
                    ),
                    screenMode = ScreenMode.TRACKING,
                    modifier = Modifier.weight(1f).height(116.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
