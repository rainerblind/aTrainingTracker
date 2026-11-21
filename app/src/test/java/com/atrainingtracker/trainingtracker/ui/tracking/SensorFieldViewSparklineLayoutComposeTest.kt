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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
 * Behavioral Compose UI layout & Rule 8.5 Light/Dark visual verification for ATT-8995
 * (`REQ-UI-2788`, `TST-UI-2788.2`, `TST-UI-2788.3`).
 */
// Linked test specs: TST-UI-2788, TST-UI-2788.2, TST-UI-2788.3
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1200dp-xhdpi")
class SensorFieldViewSparklineLayoutComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plainZoneOptions = ZoneDisplayOptions(
        showBackground = false,
        showLeftBar = false,
        showRightBar = false,
        showTextColor = false
    )

    private fun sampleFieldState(
        id: Long,
        viewSize: ViewSize = ViewSize.NORMAL,
        label: String = "RR Interval",
        value: String = "782",
        units: String = "ms",
        showValue: Boolean = true,
        showBar: Boolean = false,
        showGraph: Boolean = true,
        spanSeconds: Int = 120,
        samples: List<Float> = listOf(720f, 760f, 810f, 750f, 830f, 770f, 790f, 740f, 782f),
        sensorType: SensorType = SensorType.RR_INTERVAL,
        zoneGaugeState: ZoneGaugeState? = null,
        graphColor: String = "THEME_DEFAULT",
        sparklineZoneThresholds: SparklineZoneThresholds? = null
    ) = SensorFieldState(
        configHash = id.toInt(),
        sensorFieldId = id,
        rowNr = 0,
        colNr = 0,
        viewSize = viewSize,
        label = label,
        filterDescription = "Live",
        value = value,
        units = units,
        zoneColor = if (sparklineZoneThresholds != null) TTColor.Zone3 else Color.Transparent,
        zoneDisplayOptions = plainZoneOptions,
        sensorType = sensorType,
        showValue = showValue,
        showBar = showBar,
        showGraph = showGraph,
        zoneGaugeState = zoneGaugeState,
        sparklineSamples = samples,
        sparklineSpanSeconds = spanSeconds,
        graphColor = graphColor,
        sparklineZoneThresholds = sparklineZoneThresholds
    )

    @Test
    fun testNormalValueAndGraphInIntrinsicMinRow_canvasHeightAtLeast34dpAndLabelsVisible_tstUi2788_2() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(360.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag("tile_normal_vg")) {
                            SensorFieldView(
                                fieldState = sampleFieldState(id = 1L, viewSize = ViewSize.NORMAL, spanSeconds = 120),
                                modifier = Modifier.fillMaxHeight(),
                                screenMode = ScreenMode.TRACKING
                            )
                        }
                    }
                }
            }
        }

        val tileBounds = composeTestRule.onNodeWithTag("tile_normal_vg", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val canvasBounds = composeTestRule
            .onNode(
                hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("tile_normal_vg")),
                useUnmergedTree = true
            )
            .getUnclippedBoundsInRoot()

        assertTrue(
            "NORMAL Value+Graph sparkline Canvas height (${canvasBounds.height}) must be >= 34.dp (>= 28.dp net curve amplitude after 6.dp vertical padding)",
            canvasBounds.height >= 34.dp
        )

        val leftLabel = composeTestRule.onNode(hasText("-2:00") and hasAnyAncestor(hasTestTag("tile_normal_vg")), useUnmergedTree = true)
        val midLabel = composeTestRule.onNode(hasText("-1:00") and hasAnyAncestor(hasTestTag("tile_normal_vg")), useUnmergedTree = true)
        val rightLabel = composeTestRule.onNode(hasText("Now") and hasAnyAncestor(hasTestTag("tile_normal_vg")), useUnmergedTree = true)

        leftLabel.assertIsDisplayed()
        midLabel.assertIsDisplayed()
        rightLabel.assertIsDisplayed()
        assertTrue(
            "X-axis label bottom must stay within tile bounds",
            leftLabel.getUnclippedBoundsInRoot().bottom <= tileBounds.bottom + 1.dp
        )
    }

    @Test
    fun testMultiColumnRowWithTallerSibling_valueAndGraphSparklineExpandsIntoSurplusHeight_tstUi2788_2() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(360.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Baseline single NORMAL Value+Graph tile
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag("tile_single")) {
                                SensorFieldView(
                                    fieldState = sampleFieldState(id = 10L, viewSize = ViewSize.NORMAL),
                                    modifier = Modifier.fillMaxHeight(),
                                    screenMode = ScreenMode.TRACKING
                                )
                            }
                        }

                        // Multi-column row pairing an identical NORMAL Value+Graph tile beside a taller XLARGE Value+Graph tile
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag("tile_paired")) {
                                SensorFieldView(
                                    fieldState = sampleFieldState(id = 11L, viewSize = ViewSize.NORMAL),
                                    modifier = Modifier.fillMaxHeight(),
                                    screenMode = ScreenMode.TRACKING
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag("tile_tall_neighbor")) {
                                SensorFieldView(
                                    fieldState = sampleFieldState(
                                        id = 12L,
                                        viewSize = ViewSize.HUGE,
                                        label = "HR",
                                        value = "172",
                                        units = "bpm",
                                        showGraph = true
                                    ),
                                    modifier = Modifier.fillMaxHeight(),
                                    screenMode = ScreenMode.TRACKING
                                )
                            }
                        }
                    }
                }
            }
        }

        val singleCanvasHeight = composeTestRule
            .onNode(
                hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("tile_single")),
                useUnmergedTree = true
            )
            .getUnclippedBoundsInRoot().height
        val pairedCanvasHeight = composeTestRule
            .onNode(
                hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("tile_paired")),
                useUnmergedTree = true
            )
            .getUnclippedBoundsInRoot().height

        assertTrue(
            "Paired NORMAL Value+Graph Canvas ($pairedCanvasHeight) must expand beyond single-tile baseline ($singleCanvasHeight) when beside a taller sibling",
            pairedCanvasHeight > singleCanvasHeight + 10.dp
        )
    }

    @Test
    fun testFixedHeightCompactCards_preserveUnclippedValueAndXAxisLabels_tstUi2788_2() {
        val hrGauge = ZoneGaugeState(
            segmentColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5),
            needleFraction = 0.72f,
            activeZoneIndex = 4
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface(modifier = Modifier.width(360.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(132.dp).testTag("fixed_normal_132")) {
                            SensorFieldView(
                                fieldState = sampleFieldState(
                                    id = 20L,
                                    viewSize = ViewSize.NORMAL,
                                    label = "HR Fixed 132dp",
                                    value = "164",
                                    units = "bpm",
                                    spanSeconds = 300
                                ),
                                modifier = Modifier.fillMaxHeight(),
                                screenMode = ScreenMode.TRACKING
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(116.dp).testTag("fixed_small_116")) {
                            SensorFieldView(
                                fieldState = sampleFieldState(
                                    id = 21L,
                                    viewSize = ViewSize.SMALL,
                                    label = "Power Fixed 116dp",
                                    value = "285",
                                    units = "W",
                                    spanSeconds = 60
                                ),
                                modifier = Modifier.fillMaxHeight(),
                                screenMode = ScreenMode.TRACKING
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("intrinsic_vbg_row")) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                SensorFieldView(
                                    fieldState = sampleFieldState(
                                        id = 22L,
                                        viewSize = ViewSize.NORMAL,
                                        label = "HR Value+Bar+Graph",
                                        value = "168",
                                        units = "bpm",
                                        showBar = true,
                                        zoneGaugeState = hrGauge,
                                        spanSeconds = 300
                                    ),
                                    modifier = Modifier.fillMaxHeight(),
                                    screenMode = ScreenMode.TRACKING
                                )
                            }
                        }
                    }
                }
            }
        }

        val normalCard = composeTestRule.onNodeWithTag("fixed_normal_132", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val normalVal = composeTestRule.onNode(hasText("164") and hasAnyAncestor(hasTestTag("fixed_normal_132")), useUnmergedTree = true)
        val normalAxis = composeTestRule.onNode(hasText("-5:00") and hasAnyAncestor(hasTestTag("fixed_normal_132")), useUnmergedTree = true)
        val normalCanvas = composeTestRule
            .onNode(hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("fixed_normal_132")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        normalVal.assertIsDisplayed()
        normalAxis.assertIsDisplayed()
        assertTrue("132dp NORMAL card must preserve positive Canvas height", normalCanvas.height >= 16.dp)
        assertTrue("132dp NORMAL card X-axis label must not clip below card bottom", normalAxis.getUnclippedBoundsInRoot().bottom <= normalCard.bottom + 1.dp)

        val smallCard = composeTestRule.onNodeWithTag("fixed_small_116", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val smallVal = composeTestRule.onNode(hasText("285") and hasAnyAncestor(hasTestTag("fixed_small_116")), useUnmergedTree = true)
        val smallAxis = composeTestRule.onNode(hasText("-1:00") and hasAnyAncestor(hasTestTag("fixed_small_116")), useUnmergedTree = true)
        val smallCanvas = composeTestRule
            .onNode(hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("fixed_small_116")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        smallVal.assertIsDisplayed()
        smallAxis.assertIsDisplayed()
        assertTrue("116dp SMALL card must preserve positive Canvas height", smallCanvas.height >= 14.dp)
        assertTrue("116dp SMALL card X-axis label must not clip below card bottom", smallAxis.getUnclippedBoundsInRoot().bottom <= smallCard.bottom + 1.dp)

        val vbgCard = composeTestRule.onNodeWithTag("intrinsic_vbg_row", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val vbgVal = composeTestRule.onNode(hasText("168") and hasAnyAncestor(hasTestTag("intrinsic_vbg_row")), useUnmergedTree = true)
        val vbgAxis = composeTestRule.onNode(hasText("-5:00") and hasAnyAncestor(hasTestTag("intrinsic_vbg_row")), useUnmergedTree = true)
        val vbgCanvas = composeTestRule
            .onNode(hasTestTag("sensor_sparkline_canvas") and hasAnyAncestor(hasTestTag("intrinsic_vbg_row")), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        vbgVal.assertIsDisplayed()
        vbgAxis.assertIsDisplayed()
        assertTrue("IntrinsicSize.Min Value+Bar+Graph must preserve >= 34.dp Canvas height", vbgCanvas.height >= 34.dp)
        assertTrue("Value+Bar+Graph X-axis label must stay within card bounds", vbgAxis.getUnclippedBoundsInRoot().bottom <= vbgCard.bottom + 1.dp)
    }

    @Test
    fun testCaptureSparklineValueGraphComparisonScreenshot_tstUi2788_3() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(740.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("att8995_sparkline_layout_comparison"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f)
                    ) {
                        SparklineLayoutShowcaseColumn(title = "Light Theme (ATT-8995)")
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    ) {
                        SparklineLayoutShowcaseColumn(title = "Dark / AMOLED (ATT-8995)")
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("att8995_sparkline_layout_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8995_sparkline_value_graph_layout.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Rule 8.5 PNG screenshot must exist", outFile.exists())
        assertTrue("Rule 8.5 PNG screenshot must be non-empty", outFile.length() > 0)
    }

    @Composable
    private fun SparklineLayoutShowcaseColumn(title: String) {
        val dfaThresholds = SparklineZoneThresholds(
            cutoffs = listOf(0.50f, 0.75f),
            zoneColors = listOf(TTColor.Zone4, TTColor.Zone3, TTColor.Zone2),
            invertedPolarity = true
        )
        val hrGauge = ZoneGaugeState(
            segmentColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5),
            needleFraction = 0.68f,
            activeZoneIndex = 4
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

            // 1. Stacked Test RR NORMAL Value+Graph tile (IntrinsicSize.Min)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 101L,
                            viewSize = ViewSize.NORMAL,
                            label = "RR Interval (NORMAL)",
                            value = "782",
                            units = "ms",
                            spanSeconds = 120,
                            samples = listOf(720f, 785f, 830f, 745f, 840f, 760f, 815f, 735f, 782f)
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }

            // 2. Stacked DFA alpha 1 Inverted Dynamic Zone (NORMAL Value+Graph)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 102L,
                            viewSize = ViewSize.NORMAL,
                            label = "DFA alpha 1 (NORMAL)",
                            value = "0.58",
                            units = "",
                            spanSeconds = 300,
                            samples = listOf(0.92f, 0.84f, 0.72f, 0.64f, 0.46f, 0.52f, 0.58f),
                            sensorType = SensorType.HRV_DFA_A1,
                            graphColor = "DYNAMIC_ZONE",
                            sparklineZoneThresholds = dfaThresholds
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }

            // 3. Multi-column surplus expansion: NORMAL Value+Graph next to taller LARGE Value+Graph
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 103L,
                            viewSize = ViewSize.NORMAL,
                            label = "RMSSD (Surplus)",
                            value = "42",
                            units = "ms",
                            spanSeconds = 120,
                            samples = listOf(35f, 48f, 38f, 52f, 40f, 46f, 42f),
                            sensorType = SensorType.HRV_RMSSD
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 104L,
                            viewSize = ViewSize.XLARGE,
                            label = "Power (XLARGE)",
                            value = "285",
                            units = "W",
                            spanSeconds = 120,
                            samples = listOf(220f, 248f, 276f, 260f, 298f, 272f, 285f),
                            sensorType = SensorType.POWER
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }

            // 4. Compact SMALL Value+Graph vs Graph-only NORMAL comparison
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 105L,
                            viewSize = ViewSize.SMALL,
                            label = "SMALL V+G",
                            value = "285",
                            units = "W",
                            spanSeconds = 60,
                            samples = listOf(220f, 260f, 310f, 240f, 295f, 285f),
                            sensorType = SensorType.POWER
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 106L,
                            viewSize = ViewSize.NORMAL,
                            label = "Graph-Only",
                            value = "285",
                            units = "W",
                            showValue = false,
                            spanSeconds = 60,
                            samples = listOf(220f, 260f, 310f, 240f, 295f, 285f),
                            sensorType = SensorType.POWER
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }

            // 5. Value + Bar + Graph in real IntrinsicSize.Min cockpit row
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SensorFieldView(
                        fieldState = sampleFieldState(
                            id = 107L,
                            viewSize = ViewSize.NORMAL,
                            label = "HR Value+Bar+Graph",
                            value = "168",
                            units = "bpm",
                            showBar = true,
                            zoneGaugeState = hrGauge,
                            spanSeconds = 300,
                            samples = listOf(140f, 152f, 160f, 171f, 164f, 168f),
                            sensorType = SensorType.HR
                        ),
                        modifier = Modifier.fillMaxHeight(),
                        screenMode = ScreenMode.TRACKING
                    )
                }
            }
        }
    }
}
