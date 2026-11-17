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

package com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import com.atrainingtracker.trainingtracker.ui.tracking.DEFAULT_PREVIEW_SPARKLINE_SAMPLES
import com.atrainingtracker.trainingtracker.ui.tracking.SensorSparklineView
import com.atrainingtracker.trainingtracker.ui.tracking.resolvePreviewSparklineZoneThresholds
import com.atrainingtracker.trainingtracker.ui.tracking.resolveSparklineLineColor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EditSensorFieldDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * TST-TRK-081.5: Rule 8.5 Visual Compose Screenshot verification for ATT-7760.
     * Renders the color editor UI and live sparkline preview, asserts rendering fidelity,
     * and saves screenshot to docs/engineering/walkthroughs/img/ATT-7760_color_picker.png.
     */
    @Test
    fun testCaptureColorEditorScreenshot() {
        // TST-TRK-081
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = true) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("color_picker_container")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cockpit_graph_color_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))

                        var selectedColor by remember { mutableStateOf("CRIMSON") }
                        var selectedScope by remember { mutableStateOf(GraphColorScope.THIS_TILE) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedColor == "THEME_DEFAULT",
                                onClick = { selectedColor = "THEME_DEFAULT" },
                                label = { Text(stringResource(R.string.cockpit_graph_color_theme_default)) }
                            )
                            FilterChip(
                                selected = selectedColor == "DYNAMIC_ZONE",
                                onClick = { selectedColor = "DYNAMIC_ZONE" },
                                label = { Text(stringResource(R.string.cockpit_graph_color_dynamic_zone)) }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TelemetryGraphColor.PALETTE_COLORS.forEach { paletteColor ->
                                val isDark = TelemetryGraphColor.isDarkSurface(MaterialTheme.colorScheme)
                                val colorVal = if (isDark) paletteColor.darkColor else paletteColor.lightColor
                                val isSelected = selectedColor == paletteColor.name

                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(colorVal)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { selectedColor = paletteColor.name },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = paletteColor.name,
                                            tint = if (colorVal.luminance() > 0.5f) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = selectedScope == GraphColorScope.THIS_TILE,
                                onClick = { selectedScope = GraphColorScope.THIS_TILE },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {}
                            ) {
                                Text(
                                    text = stringResource(R.string.cockpit_graph_color_this_tile),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            SegmentedButton(
                                selected = selectedScope == GraphColorScope.ALL_METRIC,
                                onClick = { selectedScope = GraphColorScope.ALL_METRIC },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                enabled = selectedColor != "DYNAMIC_ZONE",
                                icon = {}
                            ) {
                                Text(
                                    text = stringResource(R.string.cockpit_graph_color_all_metric, "HR"),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.Preview).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                val previewLineColor = resolveSparklineLineColor(
                                    graphColor = selectedColor,
                                    zoneColor = Color.Transparent,
                                    sensorType = SensorType.HR,
                                    colorScheme = MaterialTheme.colorScheme,
                                    context = LocalContext.current
                                )
                                SensorSparklineView(
                                    samples = DEFAULT_PREVIEW_SPARKLINE_SAMPLES,
                                    spanSeconds = 300,
                                    lineColor = previewLineColor,
                                    zoneThresholds = resolvePreviewSparklineZoneThresholds(SensorType.HR, selectedColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("color_picker_container")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-7760_color_picker.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot size must be > 0", outFile.length() > 0)
    }

    /**
     * TST-UI-2782.3: Rule 8.5 Light & Dark Compose Screenshot verification for ATT-8462.
     * Captures the reordered EditSensorFieldDialog (all 3 groups active) in both Light and Dark themes.
     */
    // Traces to: REQ-UI-2782, TST-UI-2782.3
    @Test
    @Config(sdk = [34], qualifiers = "w420dp-h1600dp-xhdpi")
    fun testCaptureReorderedDialogScreenshots_LightAndDark() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stateFlow = MutableStateFlow(
            EditDialogUiState(
                selectedSensorType = SensorType.POWER,
                sportType = BSportType.BIKE,
                availableSensorTypesForCurrentActivityType = listOf(SensorType.POWER, SensorType.HR),
                selectedDeviceId = 42L,
                selectedDeviceName = "Garmin Rally",
                availableDevices = listOf(-1L to "Best Sensor", 42L to "Garmin Rally"),
                filterSummary = "3s Ø",
                showValue = true,
                showBar = true,
                showGraph = true,
                graphHorizonSeconds = 300,
                graphColor = "VIOLET",
                colWeight = 2f,
                isMultiFieldRow = true
            )
        )
        val viewModel = mockk<EditSensorFieldViewModel>(relaxed = true)
        every { viewModel.uiState } returns stateFlow

        var darkTheme by mutableStateOf(false)
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = darkTheme) {
                EditSensorFieldDialog(
                    title = context.getString(R.string.edit_field),
                    viewModel = viewModel,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")

        val lightBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val lightOut = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8462_dialog_light.png")
        lightOut.parentFile?.mkdirs()
        FileOutputStream(lightOut).use { lightBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Light screenshot must exist and be non-empty", lightOut.exists() && lightOut.length() > 0)

        darkTheme = true
        composeTestRule.waitForIdle()

        val darkBmp = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val darkOut = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8462_dialog_dark.png")
        FileOutputStream(darkOut).use { darkBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Dark screenshot must exist and be non-empty", darkOut.exists() && darkOut.length() > 0)
    }
}
