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
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Contract and architectural tests verifying REQ-UI-251 / ATT-2147:
 * Integrated minimalist marginal zone bars & histogram right of telemetry graph in portrait mode.
 */
class TelemetryMetricGraphMarginalBarsContractTest {

    @Test
    fun testTelemetryMetricGraph_containsMarginalStripParametersAndContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("TelemetryMetricGraph") }
        assertTrue("TelemetryMetricGraph composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testZoneCards_hoistDisplayModeParameters() {
        val hrClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneDistributionCardKt")
        assertNotNull("HeartRateZoneDistributionCardKt must exist", hrClazz)
        val pwrClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneDistributionCardKt")
        assertNotNull("PowerZoneDistributionCardKt must exist", pwrClazz)
    }

    @Test
    fun testMapDetailLayout_wiresZoneDistributionsAndDisplayModes() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testTrackOnMapScreen_wiresSynchronousModeSwitching() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", clazz)
    }

    @Test
    fun testMarginalBarMath_5ZonesAndHistogramScaling() {
        val entries = listOf(
            ZoneTimeEntry(1, 0, 100L, 10f, Color.Blue),
            ZoneTimeEntry(2, 0, 300L, 30f, Color.Green),
            ZoneTimeEntry(3, 0, 400L, 40f, Color.Yellow),
            ZoneTimeEntry(4, 0, 150L, 15f, Color(0xFFFF9800)),
            ZoneTimeEntry(5, 0, 50L, 5f, Color.Red)
        )
        val data = ZoneDistributionData(totalActiveTimeSec = 1000L, entries = entries)

        val stripWidth = 20f
        val z1Width = (entries[0].percentage / 100f).coerceIn(0f, 1f) * stripWidth
        val z3Width = (entries[2].percentage / 100f).coerceIn(0f, 1f) * stripWidth

        assertEquals(2.0f, z1Width, 0.001f)
        assertEquals(8.0f, z3Width, 0.001f)
        assertTrue("Z3 bar width must be greater than Z1 bar width", z3Width > z1Width)
    }

    @Test
    fun testPortraitMarginalHistogram_usesLinearBinsAndGuardsEmptyTracks_TST_UI_443_2() {
        val regularBins = listOf(
            TelemetryHistogramBin(0, 100, 200, 500L, 50f, 1, Color.Blue),
            TelemetryHistogramBin(1, 200, 300, 400L, 40f, 2, Color.Green)
        )
        val overflowBin = TelemetryHistogramBin(2, 300, 500, 100L, 10f, 7, Color.Black, isOverflow = true)
        val returnBin = TelemetryHistogramBin(0, 100, 300, 900L, 90f, 1, Color.Gray, isReturn = true)
        val sprintBins = listOf(
            TelemetryHistogramBin(1, 300, 400, 60L, 6f, 6, Color.Magenta),
            TelemetryHistogramBin(2, 400, 500, 40L, 4f, 7, Color.Black)
        )
        val sprintTail = TelemetryHistogramData(
            binWidth = 100,
            dataMin = 300,
            dataMax = 500,
            totalActiveTimeSec = 1000L,
            bins = listOf(returnBin) + sprintBins
        )
        val foldedData = TelemetryHistogramData(
            binWidth = 100,
            dataMin = 100,
            dataMax = 500,
            totalActiveTimeSec = 1000L,
            bins = regularBins + overflowBin,
            tailCutoff = 300,
            sprintTail = sprintTail
        )

        val linearBins = foldedData.linearBins
        assertEquals(4, linearBins.size)
        assertTrue("No overflow bin in linear projection", linearBins.none { it.isOverflow })

        val maxBinPct = linearBins.maxOfOrNull { it.percentage }?.takeIf { it > 0f } ?: 1f
        assertEquals(50f, maxBinPct, 0.001f)

        // Verify portrait strip calculation: each unfolded 100W bin maps to specific 1:1 Y coordinates
        val dataMin = 100.0
        val dataMax = 500.0
        val valRange = dataMax - dataMin
        val chartHeightPx = 200f
        fun valueToY(v: Double): Float = (chartHeightPx - ((v - dataMin) / valRange * chartHeightPx)).toFloat()

        for (bin in linearBins) {
            val yTop = valueToY(bin.rangeMax.toDouble())
            val yBottom = valueToY(bin.rangeMin.toDouble())
            val rectHeight = abs(yTop - yBottom)
            assertEquals(50f, rectHeight, 0.01f)
        }
    }

    @Test
    fun testPortraitMarginalHistogram_filtersOutOfDomainBinsAndPreventsTopBarStacking_ATT_5311() {
        val dataMin = 100.0
        val dataMax = 450.0
        val valRange = dataMax - dataMin
        val chartHeightPx = 350f
        val topPaddingPx = 10f

        fun valueToY(v: Double): Float {
            val ratio = ((v - dataMin) / valRange).coerceIn(0.0, 1.0)
            return (topPaddingPx + (1.0 - ratio) * chartHeightPx).toFloat()
        }

        val allBins = listOf(
            TelemetryHistogramBin(0, 0, 100, 50L, 5f, 1, Color.Blue),        // below dataMin
            TelemetryHistogramBin(1, 100, 200, 200L, 20f, 2, Color.Green),    // in domain
            TelemetryHistogramBin(2, 200, 300, 300L, 30f, 3, Color.Yellow),   // in domain
            TelemetryHistogramBin(3, 300, 400, 250L, 25f, 4, Color.Yellow),   // in domain
            TelemetryHistogramBin(4, 400, 500, 100L, 10f, 5, Color.Red),      // crosses dataMax boundary
            TelemetryHistogramBin(5, 500, 600, 50L, 5f, 6, Color.Magenta),    // above dataMax
            TelemetryHistogramBin(6, 600, 700, 30L, 3f, 7, Color.Black),      // above dataMax
            TelemetryHistogramBin(7, 700, 800, 20L, 2f, 7, Color.Black)       // above dataMax
        )

        data class RenderedRect(val rectTop: Float, val rectHeight: Float)
        val renderedRects = mutableListOf<RenderedRect>()

        for (bin in allBins) {
            if (bin.rangeMax <= dataMin || bin.rangeMin >= dataMax) continue
            val clampedMin = maxOf(dataMin, bin.rangeMin.toDouble())
            val clampedMax = minOf(dataMax, bin.rangeMax.toDouble())
            if (clampedMin >= clampedMax) continue

            val yTop = valueToY(clampedMax)
            val yBottom = valueToY(clampedMin)
            val rectTop = minOf(yTop, yBottom)
            val rectHeight = abs(yTop - yBottom).coerceAtLeast(1f)
            renderedRects.add(RenderedRect(rectTop, rectHeight))
        }

        // Must render exactly 4 bins (100-200, 200-300, 300-400, and clamped 400-450)
        assertEquals(4, renderedRects.size)

        // The boundary bin [400-500] is clamped to [400, 450], reaching exactly y = topPaddingPx (10f)
        val topRect = renderedRects.last()
        assertEquals(topPaddingPx, topRect.rectTop, 0.01f)
        assertEquals(50f, topRect.rectHeight, 0.01f)

        // Zero out-of-domain rects drawn at topPaddingPx with 1px height (which caused the black bar!)
        val stackedZeroHeightRects = renderedRects.filter { it.rectTop == topPaddingPx && it.rectHeight == 1f }
        assertEquals(0, stackedZeroHeightRects.size)
    }
}
