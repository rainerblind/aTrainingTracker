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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and UI layout contract tests for [CardioPowerTrendsCard] (REQ-REV-011 / TST-REV-012.4).
 */
class CardioPowerTrendsCardTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath across candidates $candidates")
    }

    @Test
    fun testRangePills_containsOneMonthAndAllStandardRanges() {
        // REQ-REV-011.2c: The card SHALL provide time range selector pills (1M, 3M, 6M, 1Y, All) with localized label athlete_profile_range_1m
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/CardioPowerTrendsCard.kt")
        val content = file.readText()

        assertTrue(
            "CardioPowerTrendsCard must include MetricsTimeRange.ONE_MONTH with athlete_profile_range_1m",
            content.contains("MetricsTimeRange.ONE_MONTH to stringResource(R.string.athlete_profile_range_1m)")
        )
        assertTrue(content.contains("MetricsTimeRange.THREE_MONTHS to stringResource(R.string.athlete_profile_range_3m)"))
        assertTrue(content.contains("MetricsTimeRange.SIX_MONTHS to stringResource(R.string.athlete_profile_range_6m)"))
        assertTrue(content.contains("MetricsTimeRange.ONE_YEAR to stringResource(R.string.athlete_profile_range_1y)"))
        assertTrue(content.contains("MetricsTimeRange.ALL to stringResource(R.string.athlete_profile_range_all)"))
    }

    @Test
    fun testTouchScrubbing_integratesChartGestureDisambiguator() {
        // REQ-REV-011.3d: The card SHALL support horizontal touch scrubbing using ChartGestureDisambiguator
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/CardioPowerTrendsCard.kt")
        val content = file.readText()

        assertTrue(
            "CardioPowerTrendsCard must use ChartGestureDisambiguator.isDominantVertical",
            content.contains("ChartGestureDisambiguator.isDominantVertical")
        )
        assertTrue(
            "CardioPowerTrendsCard must use ChartGestureDisambiguator.isDominantHorizontal",
            content.contains("ChartGestureDisambiguator.isDominantHorizontal")
        )
        assertTrue(
            "CardioPowerTrendsCard must invoke findNearestPoint for touch coordinates",
            content.contains("CardioPowerTrendsPlotMath.findNearestPoint")
        )
        assertTrue(
            "CardioPowerTrendsCard must draw dashPathEffect cursor line",
            content.contains("pathEffect = PathEffect.dashPathEffect")
        )
    }

    @Test
    fun testIntermediateDateTicks_renderedAcrossMultiMonthSpans() {
        // REQ-REV-011.3b: Canvas SHALL render explicit X-axis date labels including intermediate date ticks across multi-month spans
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/CardioPowerTrendsCard.kt")
        val content = file.readText()

        assertTrue(
            "CardioPowerTrendsCard must compute intermediate tickSteps across multi-month spans",
            content.contains("val tickSteps = if (tsSpan > 60L * CardioPowerTrendsPlotMath.MILLIS_PER_DAY) 3 else 2")
        )
        assertTrue(
            "CardioPowerTrendsCard must format intermediate tick dates",
            content.contains("CardioPowerTrendsPlotMath.formatAxisDate(tickTs, tsSpan)")
        )
    }
}
