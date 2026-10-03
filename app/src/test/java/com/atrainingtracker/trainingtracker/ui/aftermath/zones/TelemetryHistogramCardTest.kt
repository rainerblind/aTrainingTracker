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
        assertEquals(2, modes.size)
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
}
