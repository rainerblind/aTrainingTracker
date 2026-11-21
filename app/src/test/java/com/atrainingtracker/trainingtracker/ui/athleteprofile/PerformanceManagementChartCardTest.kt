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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.atrainingtracker.trainingtracker.physiology.AcwrZone
import com.atrainingtracker.trainingtracker.physiology.PmcDailyPoint
import com.atrainingtracker.trainingtracker.physiology.PmcSummaryResult
import com.atrainingtracker.trainingtracker.physiology.TsbReadinessZone
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

/**
 * Behavioral Compose UI & Rule 8.5 Light/Dark visual verification tests for
 * [PerformanceManagementChartCard] and [PerformanceManagementChartCanvas] (`REQ-UI-2791`, `TST-UI-2791`).
 */
// Linked test specs: TST-UI-2791, TST-UI-2791.2, TST-UI-2791.3, TST-UI-2792, TST-UI-2792.1, TST-UI-2792.3
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "de-rDE-w800dp-h1200dp-xhdpi")
class PerformanceManagementChartCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pixel10SampleSummary(): PmcSummaryResult = PmcSummaryResult(
        dailyPoints = emptyList(),
        currentCtl = 7.0,
        currentAtl = 22.7,
        currentTsb = -13.4,
        currentAcwr = 3.26,
        currentRampRate7d = 4.2,
        currentTsbZone = TsbReadinessZone.OPTIMAL_OVERLOAD,
        currentAcwrZone = AcwrZone.DANGER_ZONE,
        isAggressiveRampUp = false
    )

    private fun pixel10ZeroCtlNegativeTsbPoints(): List<PmcDailyPoint> {
        val startMs = 1_787_443_200_000L
        val dayMs = 86_400_000L
        val startEpochDay = startMs / dayMs
        return List(90) { day ->
            val ts = startMs + day * dayMs
            val (tss, ctl, atl) = when {
                day < 50 -> Triple(0.0, 0.0, 0.0)
                day == 50 -> Triple(150.0, 3.6, 21.4)
                day < 80 -> {
                    val k = day - 50
                    Triple(0.0, 3.6 * exp(-k / 42.0), 21.4 * exp(-k / 7.0))
                }
                else -> {
                    val k = day - 80
                    Triple(40.0, 2.0 + k * 0.55, 3.0 + k * 2.1)
                }
            }
            val tsb = ctl - atl
            val acwr = if (ctl > 0.0) atl / ctl else null
            PmcDailyPoint(
                dayEpochIndex = startEpochDay + day,
                dayStartTimestampMs = ts,
                dailyTss = tss,
                ctl = ctl,
                atl = atl,
                tsb = tsb,
                acwr = acwr,
                rampRate7d = 0.0,
                tsbZone = TsbReadinessZone.OPTIMAL_OVERLOAD,
                acwrZone = if (acwr != null && acwr > 1.5) AcwrZone.DANGER_ZONE else null,
                isAggressiveRampUp = false
            )
        }
    }

    @Test
    fun testGerman360dpViewport_optimalerAufbauUnclippedSwatchesLegendAndUniformRowHeights_tstUi2791_2() {
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(360.dp).padding(16.dp)) {
                        PerformanceManagementChartCard(
                            pmcSummary = pixel10SampleSummary(),
                            pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                        )
                    }
                }
            }
        }

        // 1. Optimaler Aufbau & all 3 Row 1 curve headers render completely without ellipsis truncation
        val readinessNode = composeTestRule.onNodeWithTag("pmc_tsb_readiness_text", useUnmergedTree = true)
        readinessNode.assertIsDisplayed()
        readinessNode.assertTextEquals("Optimaler Aufbau")

        for (tag in listOf("pmc_tsb_readiness_text", "pmc_label_ctl", "pmc_label_atl", "pmc_label_tsb")) {
            val layoutResults = mutableListOf<TextLayoutResult>()
            composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults)
            assertTrue("Expected TextLayoutResult for $tag", layoutResults.isNotEmpty())
            val lr = layoutResults.first()
            assertFalse(
                "Text '$tag' must NOT ellipsize on 360dp DE viewport (lines=${lr.lineCount}, size=${lr.size})",
                lr.isLineEllipsized(lr.lineCount - 1)
            )
            assertFalse(
                "Text '$tag' must NOT clip vertically on 360dp DE viewport (lines=${lr.lineCount}, size=${lr.size})",
                lr.didOverflowHeight
            )
        }

        // 2. Thick top-only accent bars are removed (ATT-9558); compact 3-curve legend & Row 1 cards remain displayed
        for (swatchTag in listOf("pmc_swatch_ctl", "pmc_swatch_atl", "pmc_swatch_tsb")) {
            composeTestRule.onNodeWithTag(swatchTag, useUnmergedTree = true).assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag("pmc_card_ctl", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_card_atl", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_card_tsb", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_chart_legend", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_legend_ctl", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_legend_atl", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("pmc_legend_tsb", useUnmergedTree = true).assertIsDisplayed()

        // 3. Uniform equal heights across Row 1 (IntrinsicSize.Min + fillMaxHeight)
        val ctlHeight = composeTestRule.onNodeWithTag("pmc_card_ctl", useUnmergedTree = true).getUnclippedBoundsInRoot().height
        val atlHeight = composeTestRule.onNodeWithTag("pmc_card_atl", useUnmergedTree = true).getUnclippedBoundsInRoot().height
        val tsbHeight = composeTestRule.onNodeWithTag("pmc_card_tsb", useUnmergedTree = true).getUnclippedBoundsInRoot().height
        assertTrue("Row 1 card heights must align (ctl=$ctlHeight, atl=$atlHeight, tsb=$tsbHeight)", abs(ctlHeight.value - tsbHeight.value) < 1f && abs(atlHeight.value - tsbHeight.value) < 1f)

        // 4. ACWR uses currentLocale (de-DE -> comma separator "3,26")
        composeTestRule.onNodeWithTag("pmc_acwr_value_text", useUnmergedTree = true).assertTextEquals("3,26")
    }

    @Test
    fun testAcwrFormatting_respectsCurrentLocaleOverrideIndependentOfJvmDefault_tstUi2791_2() {
        val origDefault = Locale.getDefault()
        try {
            // Set JVM default to GERMANY, but provide en-US via LocalConfiguration -> ACWR must use dot "3.26"
            Locale.setDefault(Locale.GERMANY)
            composeTestRule.setContent {
                val baseConfig = LocalConfiguration.current
                val usConfig = Configuration(baseConfig).apply { setLocale(Locale.US) }
                CompositionLocalProvider(LocalConfiguration provides usConfig) {
                    ATrainingTrackerTheme(darkTheme = false) {
                        Surface(modifier = Modifier.width(360.dp).padding(16.dp)) {
                            PerformanceManagementChartCard(
                                pmcSummary = pixel10SampleSummary(),
                                pmcPlotPoints = emptyList(),
                                selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                            )
                        }
                    }
                }
            }
            composeTestRule.onNodeWithTag("pmc_acwr_value_text", useUnmergedTree = true).assertTextEquals("3.26")
            composeTestRule.onNodeWithText("Noch keine Trainingsbelastungsdaten vorhanden.").assertIsDisplayed()
        } finally {
            Locale.setDefault(origDefault)
        }
    }

    @Test
    fun testCapturePmcChartLightAndDarkComparisonPng_tstUi2791_3() {
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                Row(
                    modifier = Modifier
                        .testTag("pmc_light_dark_comparison")
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ATrainingTrackerTheme(darkTheme = false) {
                        Surface(modifier = Modifier.width(360.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Light (360dp DE)")
                                PerformanceManagementChartCard(
                                    pmcSummary = pixel10SampleSummary(),
                                    pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                                    selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                                )
                            }
                        }
                    }
                    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
                        Surface(modifier = Modifier.width(360.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Dark AMOLED (360dp DE)")
                                PerformanceManagementChartCard(
                                    pmcSummary = pixel10SampleSummary(),
                                    pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                                    selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                                )
                            }
                        }
                    }
                }
            }
        }

        val bitmap = composeTestRule
            .onNodeWithTag("pmc_light_dark_comparison")
            .captureToImage()
            .asAndroidBitmap()

        assertTrue("Captured PMC screenshot must have positive dimensions", bitmap.width > 100 && bitmap.height > 100)

        val outDir = File("/tmp/att9357_verify")
        outDir.mkdirs()
        val outFile = File(outDir, "ATT-9357_pmc_chart_light_dark.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Screenshot PNG must be written and non-empty", outFile.exists() && outFile.length() > 1024L)
    }

    @Test
    fun testAcwrBuildingBaseHint_shownNeutrallyWhenLowCtlAndOmittedWhenEmpty_tstUi2792_1() {
        val lowCtlSummary = pixel10SampleSummary().copy(
            currentCtl = 7.0,
            currentAtl = 22.7,
            currentAcwr = null,
            currentAcwrZone = null
        )
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(360.dp).padding(16.dp)) {
                        PerformanceManagementChartCard(
                            pmcSummary = lowCtlSummary,
                            pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                        )
                    }
                }
            }
        }

        // Low-CTL with populated plot points displays "—" + unclipped localized "Aufbauphase" (no Risikobereich!)
        composeTestRule.onNodeWithTag("pmc_acwr_value_text", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("—")
        val zoneNode = composeTestRule.onNodeWithTag("pmc_acwr_zone_text", useUnmergedTree = true)
        zoneNode.assertIsDisplayed()
        zoneNode.assertTextEquals("Aufbauphase")

        val layoutResults = mutableListOf<TextLayoutResult>()
        zoneNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults)
        assertTrue("Expected TextLayoutResult for pmc_acwr_zone_text", layoutResults.isNotEmpty())
        val lr = layoutResults.first()
        assertFalse("Building base badge must not ellipsize", lr.isLineEllipsized(lr.lineCount - 1))
        assertFalse("Building base badge must not clip vertically", lr.didOverflowHeight)
        composeTestRule.onNodeWithText("Risikobereich").assertDoesNotExist()
    }

    @Test
    fun testAcwrBuildingBaseHint_omittedWhenPmcHistoryCompletelyEmpty_tstUi2792_1() {
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.width(360.dp).padding(16.dp)) {
                        PerformanceManagementChartCard(
                            pmcSummary = PmcSummaryResult(),
                            pmcPlotPoints = emptyList(),
                            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("pmc_acwr_value_text", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("—")
        composeTestRule.onNodeWithTag("pmc_acwr_zone_text", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun testCapturePmcBuildingBaseLightAndDarkComparisonPng_tstUi2792_3() {
        val lowCtlSummary = pixel10SampleSummary().copy(
            currentCtl = 7.0,
            currentAtl = 22.7,
            currentAcwr = null,
            currentAcwrZone = null
        )
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                Row(
                    modifier = Modifier
                        .testTag("pmc_building_base_light_dark")
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ATrainingTrackerTheme(darkTheme = false) {
                        Surface(modifier = Modifier.width(360.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Light — Building Base (360dp DE)")
                                PerformanceManagementChartCard(
                                    pmcSummary = lowCtlSummary,
                                    pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                                    selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                                )
                            }
                        }
                    }
                    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
                        Surface(modifier = Modifier.width(360.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Dark AMOLED — Building Base (360dp DE)")
                                PerformanceManagementChartCard(
                                    pmcSummary = lowCtlSummary,
                                    pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                                    selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                                )
                            }
                        }
                    }
                }
            }
        }

        val bitmap = composeTestRule
            .onNodeWithTag("pmc_building_base_light_dark")
            .captureToImage()
            .asAndroidBitmap()

        assertTrue("Captured building-base PMC PNG must have positive dimensions", bitmap.width > 100 && bitmap.height > 100)

        val outDir = File("/tmp/att9360_verify")
        outDir.mkdirs()
        val outFile = File(outDir, "ATT-9360_pmc_building_base_light_dark.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Building-base PNG must be written and non-empty", outFile.exists() && outFile.length() > 1024L)
    }

    private fun verifyUniformColoredBordersWithoutTopAccentBar(darkMode: Boolean) {
        fun colorDistance(a: Int, b: Int): Int {
            val dr = android.graphics.Color.red(a) - android.graphics.Color.red(b)
            val dg = android.graphics.Color.green(a) - android.graphics.Color.green(b)
            val db = android.graphics.Color.blue(a) - android.graphics.Color.blue(b)
            return abs(dr) + abs(dg) + abs(db)
        }

        fun assertUniformColoredBorderAndNoThickTopBar(cardTag: String, expectedCurveArgb: Int) {
            val bmp = composeTestRule
                .onNodeWithTag(cardTag, useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap()
            val midX = bmp.width / 2
            val midY = bmp.height / 2
            val topEdge = bmp.getPixel(midX, 1)
            val bottomEdge = bmp.getPixel(midX, bmp.height - 2)
            val leftEdge = bmp.getPixel(1, midY)
            val rightEdge = bmp.getPixel(bmp.width - 2, midY)
            // 1.5dp border at xhdpi (2px/dp) = 3px -> pixel at y=6 is inside the card background (not a thick 3dp+ top bar)
            val belowTopBorder = bmp.getPixel(midX, 6)
            val aboveBottomBorder = bmp.getPixel(midX, bmp.height - 7)

            assertTrue(
                "$cardTag top and bottom border edges must match uniformly (top=${Integer.toHexString(topEdge)}, bottom=${Integer.toHexString(bottomEdge)})",
                colorDistance(topEdge, bottomEdge) <= 18
            )
            assertTrue(
                "$cardTag left and right border edges must match top border uniformly",
                colorDistance(topEdge, leftEdge) <= 18 && colorDistance(topEdge, rightEdge) <= 18
            )
            assertTrue(
                "$cardTag must NOT have a thick top-only bar extending into interior (y=6 should match bottom interior)",
                colorDistance(belowTopBorder, aboveBottomBorder) <= 18
            )
            assertTrue(
                "$cardTag border must visibly differ from its interior background",
                colorDistance(topEdge, belowTopBorder) >= 40
            )
            assertTrue(
                "$cardTag border must stay anchored to its curve color",
                colorDistance(topEdge, expectedCurveArgb) < colorDistance(belowTopBorder, expectedCurveArgb)
            )
        }

        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val deConfig = Configuration(baseConfig).apply { setLocale(Locale.GERMANY) }
            CompositionLocalProvider(LocalConfiguration provides deConfig) {
                ATrainingTrackerTheme(darkTheme = darkMode, amoled = darkMode) {
                    Surface(modifier = Modifier.width(360.dp).padding(16.dp)) {
                        PerformanceManagementChartCard(
                            pmcSummary = pixel10SampleSummary(),
                            pmcPlotPoints = pixel10ZeroCtlNegativeTsbPoints(),
                            selectedPmcTimeRange = MetricsTimeRange.THREE_MONTHS
                        )
                    }
                }
            }
        }

        assertUniformColoredBorderAndNoThickTopBar("pmc_card_ctl", TTColor.PmcCtlBlue.toArgb())
        assertUniformColoredBorderAndNoThickTopBar("pmc_card_atl", TTColor.PmcAtlMagenta.toArgb())
        assertUniformColoredBorderAndNoThickTopBar("pmc_card_tsb", TTColor.PmcTsbAmber.toArgb())

        // Row 2 ACWR border must remain visually distinct from Row 1 CTL blue border
        val ctlBmp = composeTestRule.onNodeWithTag("pmc_card_ctl", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val acwrBmp = composeTestRule.onNodeWithTag("pmc_card_acwr", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val ctlBorder = ctlBmp.getPixel(ctlBmp.width / 2, 1)
        val acwrBorder = acwrBmp.getPixel(acwrBmp.width / 2, 1)
        assertTrue(
            "Row 1 CTL colored border must remain distinct from Row 2 neutral ACWR border (darkMode=$darkMode)",
            colorDistance(ctlBorder, acwrBorder) >= 60
        )
    }

    @Test
    fun testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInLightTheme_att9558() {
        verifyUniformColoredBordersWithoutTopAccentBar(darkMode = false)
    }

    @Test
    fun testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInDarkAmoledTheme_att9558() {
        verifyUniformColoredBordersWithoutTopAccentBar(darkMode = true)
    }
}
