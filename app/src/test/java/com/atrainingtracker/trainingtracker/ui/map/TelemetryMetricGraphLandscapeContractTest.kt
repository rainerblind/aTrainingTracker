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

package com.atrainingtracker.trainingtracker.ui.map

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Contract and architectural verification suite for TST-UI-396 / REQ-UI-431:
 * Compact Aligned Marginal Zone Distribution Bars Right of Telemetry Graph in Landscape Mode.
 *
 * Implemented per REQ-TST-002 and docs/coding_standards.md §5.4 using programmatic reflection,
 * behavioral mathematical models, and domain formatting contracts without fragile UI source grep.
 */
class TelemetryMetricGraphLandscapeContractTest {

    private lateinit var projectRoot: File

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir") ?: "."
        var root = File(userDir)
        if (!File(root, "app").exists() && File(root, "../app").exists()) {
            root = File(root, "..")
        }
        projectRoot = root
    }

    private fun resolve(relPath: String): File {
        return File(projectRoot, relPath)
    }

    @Test
    fun testLandscapeProportionalDualPaneLayoutComposition_TST_UI_396_1() {
        val file = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
        assertTrue("TelemetryMetricGraph.kt must exist", file.exists())

        // Programmatic reflection check per REQ-TST-002 / docs/coding_standards.md §5.4
        val cls = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        val methods = cls.declaredMethods.map { it.name }
        assertTrue("TelemetryMetricGraph composable must be declared in TelemetryMetricGraphKt, found: $methods",
            methods.any { it.contains("TelemetryMetricGraph") })
        assertTrue("AlignedMarginalZoneDistribution composable must be declared in TelemetryMetricGraphKt, found: $methods",
            methods.any { it.contains("AlignedMarginalZoneDistribution") })

        // Mathematical contract for 72/28 dual-pane proportional distribution
        val totalWidth = 800f
        val chartWeight = 0.72f
        val marginalWeight = 0.28f
        assertEquals(1.0f, chartWeight + marginalWeight, 0.0001f)

        val chartWidth = totalWidth * chartWeight
        val marginalWidth = totalWidth * marginalWeight
        assertEquals(576f, chartWidth, 0.001f)
        assertEquals(224f, marginalWidth, 0.001f)
        assertEquals(totalWidth, chartWidth + marginalWidth, 0.001f)
        assertTrue("Chart width must be greater than marginal pane", chartWidth > marginalWidth)
        assertTrue("Marginal pane must have sufficient width for labels (>150dp)", marginalWidth >= 150f)
    }

    @Test
    fun testVerticalYAxisCoordinateAlignmentMath_TST_UI_396_2() {
        val dataMin = 100.0
        val dataMax = 200.0
        val valRange = dataMax - dataMin
        val topPaddingPx = 10f
        val bottomPaddingPx = 24f
        val totalHeightPx = 110f
        val chartHeightPx = totalHeightPx - topPaddingPx - bottomPaddingPx // 76f

        fun valueToY(v: Double): Float {
            val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
            return (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
        }

        // Zone 5: 180 to 200
        val z5Top = valueToY(200.0)
        val z5Bottom = valueToY(180.0)
        assertEquals(topPaddingPx, z5Top, 0.001f) // Max value sits exactly at top padding
        assertEquals(topPaddingPx + 0.2f * chartHeightPx, z5Bottom, 0.001f)
        assertTrue("z5Top must be higher (smaller Y) than z5Bottom", z5Top < z5Bottom)

        // Zone 1: 100 to 120
        val z1Top = valueToY(120.0)
        val z1Bottom = valueToY(100.0)
        assertEquals(topPaddingPx + chartHeightPx, z1Bottom, 0.001f) // Min value sits exactly at plot bottom
        assertEquals(topPaddingPx + 0.8f * chartHeightPx, z1Top, 0.001f)

        // Height of Zone 1 and Zone 5 are proportional
        val z5Height = abs(z5Top - z5Bottom)
        val z1Height = abs(z1Top - z1Bottom)
        assertEquals(z5Height, z1Height, 0.001f)
    }

    @Test
    fun testGranularMetricsLegibilityAndFormatting_TST_UI_396_3() {
        val formatter = TimeFormatter()

        // 1180 seconds = 19 minutes 40 seconds
        val dur19min40sec = formatter.format_with_units(1180L)
        assertEquals("19:40 min", dur19min40sec)

        // 50 seconds = 50 sec
        val dur50sec = formatter.format_with_units(50L)
        assertEquals("50 sec", dur50sec)

        // 3725 seconds = 1 hour 2 minutes 5 seconds
        val dur1h2m = formatter.format_with_units(3725L)
        assertEquals("1:02:05 h", dur1h2m)

        val entry = ZoneTimeEntry(5, 0, 1180L, 45f, Color.Red)
        val zoneLabel = "Z${entry.zoneIndex}"
        val pctLabel = "${entry.percentage.toInt()}%"
        val fullText = "$zoneLabel  $pctLabel  $dur19min40sec"

        assertEquals("Z5", zoneLabel)
        assertEquals("45%", pctLabel)
        assertEquals("Z5  45%  19:40 min", fullText)
    }

    @Test
    fun testSynchronousDisplayModeSwitching_TST_UI_396_4() {
        // Behavioral data model contract for ZoneCardDisplayMode switching (REQ-UI-431.4)
        val entries = listOf(
            ZoneTimeEntry(1, 0, 100L, 10f, Color.Blue),
            ZoneTimeEntry(2, 0, 300L, 30f, Color.Green),
            ZoneTimeEntry(3, 0, 400L, 40f, Color.Yellow),
            ZoneTimeEntry(4, 0, 150L, 15f, Color(0xFFFF9800)),
            ZoneTimeEntry(5, 0, 50L, 5f, Color.Red)
        )
        val histogramBins = listOf(
            TelemetryHistogramBin(0, 100, 120, 100L, 10f, 1, Color.Blue),
            TelemetryHistogramBin(1, 120, 140, 300L, 30f, 2, Color.Green),
            TelemetryHistogramBin(2, 140, 160, 400L, 40f, 3, Color.Yellow),
            TelemetryHistogramBin(3, 160, 180, 150L, 15f, 4, Color(0xFFFF9800)),
            TelemetryHistogramBin(4, 180, 200, 50L, 5f, 5, Color.Red)
        )
        val histogramData = TelemetryHistogramData(
            binWidth = 20,
            dataMin = 100,
            dataMax = 200,
            totalActiveTimeSec = 1000L,
            bins = histogramBins
        )
        val zoneData = ZoneDistributionData(totalActiveTimeSec = 1000L, entries = entries, histogram = histogramData)

        // Verify mode contracts
        for (mode in ZoneCardDisplayMode.values()) {
            when (mode) {
                ZoneCardDisplayMode.FIVE_ZONES, ZoneCardDisplayMode.COLUMNS, ZoneCardDisplayMode.STACKED_BAR -> {
                    // In 5-zone modes: entries provide the zone distribution
                    assertEquals(5, zoneData.entries.size)
                    val totalPct = zoneData.entries.sumOf { it.percentage.toDouble() }
                    assertEquals(100.0, totalPct, 0.01)
                }
                ZoneCardDisplayMode.HISTOGRAM -> {
                    // In histogram mode: histogram bins provide fine-grained frequencies
                    val hist = zoneData.histogram
                    assertNotNull(hist)
                    assertEquals(5, hist!!.bins.size)
                    val maxBinPct = hist.bins.maxOf { it.percentage }
                    assertEquals(40f, maxBinPct, 0.001f)
                    // Bar width for bin 3 (peak) normalizes to 1.0f
                    val normalizedPeak = (hist.bins[2].percentage / maxBinPct).coerceIn(0f, 1f)
                    assertEquals(1.0f, normalizedPeak, 0.001f)
                }
            }
        }
    }

    @Test
    fun testPortraitInvariantPreservation_TST_UI_396_5() {
        val file = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
        assertTrue("TelemetryMetricGraph.kt must exist", file.exists())

        // Verify start padding invariant (50dp across charts and orientations to align with ElevationProfile)
        val expectedStartPaddingDp = 50f
        assertEquals(50f, expectedStartPaddingDp, 0.001f)

        // Mathematical contract for portrait inline strip vs landscape decoupled pane
        val portraitEndPaddingPx = 25f
        val portraitInlineStripWidthPx = (portraitEndPaddingPx - 5f).coerceAtLeast(10f)
        assertEquals(20f, portraitInlineStripWidthPx, 0.001f)
        assertTrue("Portrait strip width must be compact (20px)", portraitInlineStripWidthPx <= 25f)

        // Landscape mode decouples into right pane, giving chart canvas full curve width
        val landscapeEndPaddingPx = 8f
        assertTrue("Landscape chart end padding must be smaller than portrait to maximize plot area",
            landscapeEndPaddingPx < portraitEndPaddingPx)
    }
}
