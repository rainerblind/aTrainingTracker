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

/**
 * Unit test for GradeZoneDistributionCard and zone architecture.
 * Refactored to JVM reflection and behavioral tests per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class GradeZoneDistributionCardTest {

    @Test
    fun testGradeZoneDistributionCard_contractAndLayoutArchitecture() {
        val gradeCardClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.GradeZoneDistributionCardKt")
        val gradeMethods = gradeCardClass.declaredMethods.filter { it.name == "GradeZoneDistributionCard" }
        assertTrue("GradeZoneDistributionCard composable must exist", gradeMethods.isNotEmpty())

        val genericCardClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardKt")
        val genericMethods = genericCardClass.declaredMethods.filter { it.name == "ZoneDistributionCard" }
        assertTrue("ZoneDistributionCard composable must exist", genericMethods.isNotEmpty())

        val stackedBarClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionStackedBarKt")
        val stackedBarMethods = stackedBarClass.declaredMethods.filter { it.name.startsWith("ZoneDistributionStackedBar") }
        assertTrue("ZoneDistributionStackedBar composable must exist", stackedBarMethods.isNotEmpty())

        val columnChartClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionColumnChartKt")
        val columnChartMethods = columnChartClass.declaredMethods.filter { it.name.startsWith("ZoneDistributionColumnChart") }
        assertTrue("ZoneDistributionColumnChart composable must exist", columnChartMethods.isNotEmpty())

        val histogramChartClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramChartKt")
        val histogramChartMethods = histogramChartClass.declaredMethods.filter { it.name.startsWith("TelemetryHistogramChart") }
        assertTrue("TelemetryHistogramChart composable must exist", histogramChartMethods.isNotEmpty())
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

        // 3. Structural verification: ColumnChart and StackedBar classes exist and have entry rendering composables
        val columnChartClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionColumnChartKt")
        assertTrue("ZoneDistributionColumnChartKt exists", columnChartClass.declaredMethods.isNotEmpty())

        val stackedBarClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionStackedBarKt")
        assertTrue("ZoneDistributionStackedBarKt exists", stackedBarClass.declaredMethods.isNotEmpty())
    }
}
