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
import com.atrainingtracker.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GradeZoneDistributionCardTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testGradeZoneDistributionCard_contractAndLayoutArchitecture() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/GradeZoneDistributionCard.kt")
        val content = file.readText()

        // 1. Delegation to generic ZoneDistributionCard
        assertTrue("GradeZoneDistributionCard must delegate to ZoneDistributionCard", content.contains("ZoneDistributionCard("))
        assertTrue("Must use R.drawable.ic_grade", content.contains("R.drawable.ic_grade"))
        assertTrue("Must use R.string.aftermath_grade_zones_title", content.contains("R.string.aftermath_grade_zones_title"))
        assertTrue("Must pass unit = \"%\"", content.contains("unit = \"%\""))
        assertTrue("Must declare showModeSelector: Boolean = true", content.contains("showModeSelector: Boolean = true"))
        assertTrue("Default internal display mode should be COLUMNS", content.contains("mutableStateOf(ZoneCardDisplayMode.COLUMNS)"))

        // 2. Underlying ZoneDistributionCard architecture
        val genericFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCard.kt")
        val genericContent = genericFile.readText()
        assertTrue("Header must format duration via ZoneDistributionChartMath", genericContent.contains("ZoneDistributionChartMath.formatZoneDuration"))
        assertTrue("Must render ZoneDistributionStackedBar in STACKED_BAR mode", genericContent.contains("ZoneDistributionStackedBar("))
        assertTrue("Must render ZoneDistributionColumnChart in COLUMNS mode", genericContent.contains("ZoneDistributionColumnChart("))
        assertTrue("Must render TelemetryHistogramChart in HISTOGRAM mode", genericContent.contains("TelemetryHistogramChart("))
        assertTrue("Must contain SingleChoiceSegmentedButtonRow", genericContent.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue("Must reference zone_mode_stacked_bar", genericContent.contains("R.string.zone_mode_stacked_bar"))
        assertTrue("Must reference zone_mode_columns", genericContent.contains("R.string.zone_mode_columns"))
        assertTrue("Must reference zone_mode_histogram", genericContent.contains("R.string.zone_mode_histogram"))
    }

    @Test
    fun testGradeZoneDistribution_displayModeFallbackResolution() {
        val entry = ZoneTimeEntry(
            zoneIndex = 1,
            zoneLabelResId = R.string.grade_legend_under_2,
            durationSec = 120L,
            percentage = 100f,
            color = Color.Green
        )

        // Without histogram: HISTOGRAM mode falls back to COLUMNS
        val withoutHistogram = ZoneDistributionData(
            totalActiveTimeSec = 120L,
            entries = listOf(entry),
            histogram = null
        )
        val resolvedWithoutHist = ZoneCardDisplayMode.HISTOGRAM
            .resolveWithHistogramFallback(withoutHistogram.histogram != null)
        assertEquals(ZoneCardDisplayMode.COLUMNS, resolvedWithoutHist)

        // With histogram: HISTOGRAM mode is retained
        val bin = TelemetryHistogramBin(
            binIndex = 0,
            rangeMin = 0,
            rangeMax = 1,
            durationSec = 120L,
            percentage = 100f,
            zoneIndex = 1,
            color = Color.Green
        )
        val withHistogram = ZoneDistributionData(
            totalActiveTimeSec = 120L,
            entries = listOf(entry),
            histogram = TelemetryHistogramData(
                binWidth = 1,
                dataMin = 0,
                dataMax = 1,
                totalActiveTimeSec = 120L,
                bins = listOf(bin)
            )
        )
        val resolvedWithHist = ZoneCardDisplayMode.HISTOGRAM
            .resolveWithHistogramFallback(withHistogram.histogram != null)
        assertEquals(ZoneCardDisplayMode.HISTOGRAM, resolvedWithHist)
    }

    @Test
    fun testGradeZoneDistribution_badgeLabelDecouplingAndThresholds() {
        // 1. Invariant: standard zone entry defaults to Z$zoneIndex
        val hrEntry = ZoneTimeEntry(
            zoneIndex = 3,
            zoneLabelResId = R.string.zone_3_label,
            durationSec = 60L,
            percentage = 50f,
            color = Color.Yellow
        )
        assertEquals("Z3", hrEntry.badgeLabel)

        // 2. Custom threshold label overrides default Z$zoneIndex
        val gradeEntry = ZoneTimeEntry(
            zoneIndex = 1,
            zoneLabelResId = R.string.grade_legend_under_2,
            durationSec = 120L,
            percentage = 100f,
            color = Color.Green,
            customBadgeLabel = "<2%"
        )
        assertEquals("<2%", gradeEntry.badgeLabel)

        // 3. Structural verification: ColumnChart and StackedBar use entry.badgeLabel
        val columnChartContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt").readText()
        assertTrue("ZoneDistributionColumnChart must render entry.badgeLabel", columnChartContent.contains("entry.badgeLabel"))
        assertFalse("ZoneDistributionColumnChart must not hardcode Z\${entry.zoneIndex}", columnChartContent.contains("\"Z\${entry.zoneIndex}\""))

        val stackedBarContent = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt").readText()
        assertTrue("ZoneDistributionStackedBar must render entry.badgeLabel", stackedBarContent.contains("entry.badgeLabel"))
        assertFalse("ZoneDistributionStackedBar must not hardcode Z\${entry.zoneIndex}", stackedBarContent.contains("\"Z\${entry.zoneIndex}\""))
    }
}
