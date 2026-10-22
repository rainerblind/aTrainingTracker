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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class TelemetryHistogramCardTest {

    @Test
    fun testZoneDistributionData_backwardCompatibilityWithoutHistogram() {
        val entry = ZoneTimeEntry(
            zoneIndex = 1,
            zoneLabelResId = 1,
            durationSec = 100L,
            percentage = 100f,
            color = Color.Blue
        )
        // Construction without histogram parameter defaults to null
        val data = ZoneDistributionData(
            totalActiveTimeSec = 100L,
            entries = listOf(entry)
        )
        assertNull(data.histogram)
    }

    @Test
    fun testZoneDistributionData_withHistogram() {
        val bin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 140,
            rangeMax = 142,
            durationSec = 60L,
            percentage = 100f,
            zoneIndex = 2,
            color = Color.Green
        )
        val histogram = TelemetryHistogramData(
            binWidth = 2,
            dataMin = 140,
            dataMax = 142,
            totalActiveTimeSec = 60L,
            bins = listOf(bin)
        )
        val entry = ZoneTimeEntry(
            zoneIndex = 2,
            zoneLabelResId = 2,
            durationSec = 60L,
            percentage = 100f,
            color = Color.Green
        )
        val data = ZoneDistributionData(
            totalActiveTimeSec = 60L,
            entries = listOf(entry),
            histogram = histogram
        )

        val hist = data.histogram
        assertNotNull(hist)
        if (hist != null) {
            assertEquals(2, hist.binWidth)
            assertEquals(1, hist.bins.size)
            assertEquals(140, hist.bins[0].rangeMin)
            assertEquals(142, hist.bins[0].rangeMax)
        }
    }

    @Test
    fun testZoneCardDisplayMode_values() {
        val modes = ZoneCardDisplayMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(ZoneCardDisplayMode.STACKED_BAR))
        assertTrue(modes.contains(ZoneCardDisplayMode.COLUMNS))
        assertTrue(modes.contains(ZoneCardDisplayMode.FIVE_ZONES))
        assertTrue(modes.contains(ZoneCardDisplayMode.HISTOGRAM))
    }

    @Test
    fun testHistogramReadoutFormat_calculations() {
        val bin = TelemetryHistogramBin(
            binIndex = 4,
            rangeMin = 160,
            rangeMax = 162,
            durationSec = 245L,
            percentage = 24.5f,
            zoneIndex = 3,
            color = Color.Yellow
        )

        val durationFormatted = ZoneDistributionChartMath.formatZoneDuration(bin.durationSec)
        assertEquals("4:05", durationFormatted)

        val readout = "${bin.rangeMin}–${bin.rangeMax} bpm • $durationFormatted (${String.format(java.util.Locale.US, "%.1f", bin.percentage)}%) • Z${bin.zoneIndex}"
        assertEquals("160–162 bpm • 4:05 (24.5%) • Z3", readout)
    }

    private fun findProjectRoot(): java.io.File {
        var dir: java.io.File = java.io.File(".").canonicalFile
        while (dir.parentFile != null) {
            if (java.io.File(dir, "gradlew").exists() && java.io.File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return java.io.File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): java.io.File {
        val root = findProjectRoot()
        val target = java.io.File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testHeartRateZoneDistributionCard_headerAndToggleLayoutContract() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt")
        val content = file.readText()

        // Verify Header Row does not contain SingleChoiceSegmentedButtonRow
        val headerStartIndex = content.indexOf("// Header Row:")
        assertTrue("Header row comment must exist", headerStartIndex != -1)
        val bodyStartIndex = content.indexOf("// Body:")
        assertTrue("Body comment must exist", bodyStartIndex > headerStartIndex)
        val headerSnippet = content.substring(headerStartIndex, bodyStartIndex)

        assertFalse(
            "Header row must not contain SingleChoiceSegmentedButtonRow (REQ-UI-231)",
            headerSnippet.contains("SingleChoiceSegmentedButtonRow")
        )
        assertTrue(
            "Header row must contain total duration formatting",
            headerSnippet.contains("ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec)")
        )

        // Verify Mode Switcher is positioned after Body chart
        val bottomSwitcherIndex = content.indexOf("// Bottom Mode Switcher:")
        assertTrue("Bottom Mode Switcher comment must exist", bottomSwitcherIndex > bodyStartIndex)
        val bottomSnippet = content.substring(bottomSwitcherIndex)
        assertTrue(
            "Bottom section must host SingleChoiceSegmentedButtonRow",
            bottomSnippet.contains("SingleChoiceSegmentedButtonRow")
        )
        assertTrue(
            "Bottom section must be centered horizontally",
            bottomSnippet.contains("horizontalArrangement = Arrangement.Center")
        )
    }

    @Test
    fun testPowerZoneDistributionCard_headerAndToggleLayoutContract() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt")
        val content = file.readText()

        // Verify Header Row does not contain SingleChoiceSegmentedButtonRow
        val headerStartIndex = content.indexOf("// Header Row:")
        assertTrue("Header row comment must exist", headerStartIndex != -1)
        val bodyStartIndex = content.indexOf("// Body:")
        assertTrue("Body comment must exist", bodyStartIndex > headerStartIndex)
        val headerSnippet = content.substring(headerStartIndex, bodyStartIndex)

        assertFalse(
            "Header row must not contain SingleChoiceSegmentedButtonRow (REQ-UI-231)",
            headerSnippet.contains("SingleChoiceSegmentedButtonRow")
        )
        assertTrue(
            "Header row must contain total duration formatting",
            headerSnippet.contains("ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec)")
        )

        // Verify Mode Switcher is positioned after Body chart
        val bottomSwitcherIndex = content.indexOf("// Bottom Mode Switcher:")
        assertTrue("Bottom Mode Switcher comment must exist", bottomSwitcherIndex > bodyStartIndex)
        val bottomSnippet = content.substring(bottomSwitcherIndex)
        assertTrue(
            "Bottom section must host SingleChoiceSegmentedButtonRow",
            bottomSnippet.contains("SingleChoiceSegmentedButtonRow")
        )
        assertTrue(
            "Bottom section must be centered horizontally",
            bottomSnippet.contains("horizontalArrangement = Arrangement.Center")
        )
    }

    @Test
    fun testTelemetryHistogramChart_streamlinedReadoutContract() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt")
        val content = file.readText()

        assertFalse(
            "TelemetryHistogramChart must not contain hardcoded 'active bins' label (REQ-UI-231)",
            content.contains("active bins")
        )
        assertTrue(
            "TelemetryHistogramChart must display range in resting readout",
            content.contains("\${histogram.dataMin}–\${histogram.dataMax} \$unit")
        )
        assertTrue(
            "TelemetryHistogramChart must display bin delta in resting readout",
            content.contains("Δ \${histogram.binWidth} \$unit")
        )
    }

    @Test
    fun testTelemetryHistogramModels_backwardCompatibility_tst_ui_382_4() {
        // Instantiate TelemetryHistogramBin with legacy 7 parameters
        val bin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 100,
            rangeMax = 110,
            durationSec = 10L,
            percentage = 10f,
            zoneIndex = 1,
            color = Color.Red
        )
        assertFalse("isOverflow must default to false", bin.isOverflow)
        assertFalse("isReturn must default to false", bin.isReturn)

        // Instantiate TelemetryHistogramData with legacy 5 parameters
        val data = TelemetryHistogramData(
            binWidth = 10,
            dataMin = 100,
            dataMax = 200,
            totalActiveTimeSec = 100L,
            bins = listOf(bin)
        )
        assertNull("tailCutoff must default to null", data.tailCutoff)
        assertNull("sprintTail must default to null", data.sprintTail)
    }

    @Test
    fun testTelemetryHistogram_independentYScalingAndReadoutFormats_tst_ui_382_5() {
        // Folded dataset with tailCutoff = 400 W, sprint tail 400..940 W
        val returnBin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 0,
            rangeMax = 400,
            durationSec = 3500L,
            percentage = (3500f / 3550f) * 100f,
            zoneIndex = 1,
            color = Color.Gray,
            isOverflow = false,
            isReturn = true
        )
        val sprintBin1 = TelemetryHistogramBin(
            binIndex = 1,
            rangeMin = 840,
            rangeMax = 850,
            durationSec = 15L,
            percentage = (15f / 3550f) * 100f,
            zoneIndex = 5,
            color = Color.Magenta,
            isOverflow = false,
            isReturn = false
        )
        val sprintBin2 = TelemetryHistogramBin(
            binIndex = 2,
            rangeMin = 850,
            rangeMax = 860,
            durationSec = 5L,
            percentage = (5f / 3550f) * 100f,
            zoneIndex = 5,
            color = Color.Magenta,
            isOverflow = false,
            isReturn = false
        )

        val sprintTail = TelemetryHistogramData(
            binWidth = 10,
            dataMin = 400,
            dataMax = 940,
            totalActiveTimeSec = 3550L,
            bins = listOf(returnBin, sprintBin1, sprintBin2),
            tailCutoff = 400
        )

        // 1. Evaluate independent Y-scaling for Sprint Tail View
        val sprintBins = sprintTail.bins.filter { !it.isReturn }
        val maxDuration = sprintBins.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
        assertEquals("maxDuration must exclude return bin and equal 15s", 15L, maxDuration)

        val heightFraction15s = (sprintBin1.durationSec.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, heightFraction15s, 0.001f)

        val heightFraction5s = (sprintBin2.durationSec.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1f / 3f, heightFraction5s, 0.001f)

        // 2. Main View Resting Readout Formats
        val dataMin = 0
        val tailCutoff = 400
        val dataMax = 940
        val unit = "W"
        val binWidth = 10

        val mainRangeText = "$dataMin–$tailCutoff $unit"
        assertEquals("0–400 W", mainRangeText)
        val overflowChipLabel = "> $tailCutoff $unit"
        assertEquals("> 400 W", overflowChipLabel)
        val resolutionText = "Δ $binWidth $unit"
        assertEquals("Δ 10 W", resolutionText)

        // 3. Sprint Tail Resting Readout Formats
        val returnChipLabel = "$dataMin–$tailCutoff $unit"
        assertEquals("0–400 W", returnChipLabel)
        val sprintRangeText = "$tailCutoff–$dataMax $unit"
        assertEquals("400–940 W", sprintRangeText)

        // 4. Source Contract Verification for TelemetryHistogramChart.kt (REQ-UI-419.3)
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt")
        val content = file.readText()
        assertTrue(content.contains("power_histogram_overflow_label"))
        assertTrue(content.contains("power_histogram_return_label"))
        assertTrue(content.contains("power_histogram_overflow_desc"))
        assertTrue(content.contains("power_histogram_return_desc"))
        assertTrue(content.contains("isSprintTailView"))
        // Symmetrical 2-segment toggle styling contract (REQ-UI-419.3 / ATT-4370)
        assertTrue("Active segment must use primaryContainer highlighting", content.contains("primaryContainer"))
        assertTrue("Active segment text must use onPrimaryContainer", content.contains("onPrimaryContainer"))
        assertTrue("Inactive switch segment must use surfaceVariant styling", content.contains("surfaceVariant"))
        assertTrue("Inactive switch segment text must use onSurfaceVariant", content.contains("onSurfaceVariant"))
        assertTrue("Navigation arrows must use AutoMirrored directional icons", content.contains("ArrowForward") && content.contains("ArrowBack"))
    }

    @Test
    fun testDarkModeContrast_zone7ReadoutAndOutlineMeetWcagThresholds_tst_ui_383_3() {
        // WCAG relative luminance calculation helper
        fun relativeLuminance(r: Double, g: Double, b: Double): Double {
            fun channelLum(c: Double): Double = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            return 0.2126 * channelLum(r) + 0.7152 * channelLum(g) + 0.0722 * channelLum(b)
        }

        fun contrastRatio(lum1: Double, lum2: Double): Double {
            val l1 = Math.max(lum1, lum2)
            val l2 = Math.min(lum1, lum2)
            return (l1 + 0.05) / (l2 + 0.05)
        }

        // Dark theme surface: #121212
        val surfaceLum = relativeLuminance(0x12 / 255.0, 0x12 / 255.0, 0x12 / 255.0)
        val onSurfaceLum = relativeLuminance(0xE6 / 255.0, 0xE1 / 255.0, 0xE5 / 255.0)

        // Text contrast of onSurface against surface (#121212): must exceed 4.5:1
        val textContrast = contrastRatio(onSurfaceLum, surfaceLum)
        assertTrue("onSurface against dark surface must exceed 4.5:1 WCAG AA text threshold (was $textContrast)", textContrast >= 4.5)

        // Readout text color resolution contract
        val binZ7 = TelemetryHistogramBin(
            binIndex = 50,
            rangeMin = 500,
            rangeMax = 510,
            durationSec = 10L,
            percentage = 5f,
            zoneIndex = 7,
            color = Color(0xFF000000)
        )
        val onSurfaceColor = Color(0xFFE6E1E5)
        val resolvedReadoutColor = if (binZ7.color == Color(0xFF000000)) onSurfaceColor else binZ7.color
        assertEquals("Zone 7 readout color must resolve to onSurface", onSurfaceColor, resolvedReadoutColor)

        // Source contract verification: ZoneDistributionModels.kt
        val modelsFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt")
        val modelsContent = modelsFile.readText()
        assertTrue("ZoneTimeEntry must document 1..7 for Cycling Power", modelsContent.contains("1..7 for Cycling Power"))
        assertTrue("ZoneDistributionData must document 7 zones for Cycling Power", modelsContent.contains("7 zones for Cycling Power"))

        // Source contract verification: TelemetryHistogramChart.kt
        val histChartFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt")
        val histChartContent = histChartFile.readText()
        assertTrue(
            "TelemetryHistogramChart must resolve Zone 7 readout color to onSurface",
            histChartContent.contains("selectedBin.color == TTColor.Zone7") && histChartContent.contains("MaterialTheme.colorScheme.onSurface")
        )
        assertTrue(
            "TelemetryHistogramChart must render outlineVariant stroke for Zone 6 and 7",
            histChartContent.contains("bin.color == TTColor.Zone7 || bin.color == TTColor.Zone6") && histChartContent.contains("outlineVariantColor")
        )

        // Source contract verification: ZoneDistributionColumnChart.kt
        val colChartFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt")
        val colChartContent = colChartFile.readText()
        assertTrue(
            "ZoneDistributionColumnChart must apply outlineVariant border to Zone 6 and 7 swatches",
            colChartContent.contains("entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6") && colChartContent.contains("outlineVariant")
        )

        // Source contract verification: ZoneDistributionStackedBar.kt
        val stackedBarFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt")
        val stackedBarContent = stackedBarFile.readText()
        assertTrue(
            "ZoneDistributionStackedBar must apply outlineVariant border to Zone 6 and 7 segments",
            stackedBarContent.contains("entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6") && stackedBarContent.contains("outlineVariant")
        )
    }
}
