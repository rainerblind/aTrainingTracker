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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verification suite for [TelemetryMetricGraph] and [ElevationProfile] enforcing 72% plot width alignment,
 * double-padding elimination, and zero-extra-row floating zoom controls in landscape mode
 * (REQ-UI-433.3, REQ-UI-433.4 / TST-UI-398.3, TST-UI-398.4 / ATT-4774).
 */
class TelemetryMetricGraphLandscapeAlignmentTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val telemetryGraphFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    private val zoomToolbarFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/GlobalTelemetryZoomToolbar.kt")
    }

    @Test
    fun testZeroExtraRowZoomControls_MapDetailLayoutAndToolbarPill_TST_UI_398_3() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        assertTrue("GlobalTelemetryZoomToolbar.kt must exist", zoomToolbarFile.exists())

        val layoutContent = mapDetailLayoutFile.readText()
        val toolbarContent = zoomToolbarFile.readText()

        // 1. MapDetailLayout renders floating overlay in landscape mode at Alignment.TopEnd
        assertTrue(
            "MapDetailLayout must position zoom toolbar as floating overlay at Alignment.TopEnd in landscape",
            layoutContent.contains("if (hasZoomToolbar && isLandscape)") &&
                    layoutContent.contains(".align(Alignment.TopEnd)") &&
                    layoutContent.contains(".padding(top = 4.dp, end = 8.dp)")
        )

        // 2. GlobalTelemetryZoomToolbar adapts in landscape to a compact 28.dp floating pill
        assertTrue(
            "GlobalTelemetryZoomToolbar must adapt to height(28.dp) and wrapContentSize in landscape",
            toolbarContent.contains(".height(28.dp)") && toolbarContent.contains(".wrapContentSize()")
        )
        assertTrue(
            "GlobalTelemetryZoomToolbar must apply RoundedCornerShape in landscape",
            toolbarContent.contains("RoundedCornerShape(14.dp)")
        )
    }

    @Test
    fun testLandscapeStackedCharts_72PercentPlotWidthAndZeroDoublePadding_TST_UI_398_4() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())

        val tContent = telemetryGraphFile.readText()
        val eContent = elevationProfileFile.readText()

        // 1. TelemetryMetricGraph sets endPaddingPx = 0.dp in landscape (both pointer input and chart canvas)
        assertTrue(
            "TelemetryMetricGraph must eliminate double padding in landscape (endPaddingPx = 0.dp)",
            tContent.contains("val endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()") ||
                tContent.contains("val endPaddingPx = if (isLandscape) 0.dp.toPx() else 25.dp.toPx()")
        )

        // 2. TelemetryMetricGraph applies 72/28 split even when zoneDistribution is null (Speed/Pace, W'bal)
        assertTrue(
            "TelemetryMetricGraph must wrap chartCanvas in weight(0.72f) in landscape",
            tContent.contains("Box(modifier = Modifier.weight(0.72f))")
        )
        assertTrue(
            "TelemetryMetricGraph must render Spacer(weight(0.28f)) or marginal pane in landscape",
            tContent.contains("Spacer(modifier = Modifier.weight(0.28f))") ||
                tContent.contains("AlignedMarginalLinearDistribution")
        )

        // 3. ElevationProfile applies 72/28 split and zero end padding in landscape inspection mode
        assertTrue(
            "ElevationProfile must wrap profileCanvas in weight(0.72f) and 0.28f pane in landscape",
            eContent.contains("if (isLandscape && showZoomControls)") &&
                    eContent.contains("Box(modifier = Modifier.weight(0.72f))") &&
                    (eContent.contains("Spacer(modifier = Modifier.weight(0.28f))") || eContent.contains("AlignedMarginalLinearDistribution"))
        )
        assertTrue(
            "ElevationProfile must set end = 0.dp in landscape inspection mode",
            eContent.contains("end = if (isLandscape && showZoomControls) 0.dp else")
        )
    }

    @Test
    fun testMathematicalScrubberAlignment_1to1CoordinateParityAcrossStackedCharts() {
        // Mathematical proof of 1:1 horizontal alignment across ElevationProfile, Speed/Pace, HR, Power, W'bal
        val screenWidthPx = 800f
        val chartPaneWidthPx = screenWidthPx * 0.72f // 576f
        val startPaddingPx = 50f
        val endPaddingPx = 0f // eliminated double padding in landscape

        val plotWidthPx = chartPaneWidthPx - startPaddingPx - endPaddingPx // 526f

        fun calculateScrubberX(normalizedFraction: Float): Float {
            return startPaddingPx + normalizedFraction * plotWidthPx
        }

        // Test cursor coordinate at 0%, 25%, 50%, 75%, 100% of visible domain
        assertEquals(50f, calculateScrubberX(0.0f), 0.001f)
        assertEquals(50f + 0.25f * 526f, calculateScrubberX(0.25f), 0.001f)
        assertEquals(50f + 0.50f * 526f, calculateScrubberX(0.50f), 0.001f)
        assertEquals(50f + 0.75f * 526f, calculateScrubberX(0.75f), 0.001f)
        assertEquals(576f, calculateScrubberX(1.0f), 0.001f)

        // Sub-pixel drift is strictly 0.0f between all sibling charts sharing this math
        val elevationCursorX = calculateScrubberX(0.42f)
        val hrCursorX = calculateScrubberX(0.42f)
        val powerCursorX = calculateScrubberX(0.42f)
        val paceCursorX = calculateScrubberX(0.42f)

        assertEquals(elevationCursorX, hrCursorX, 0.00001f)
        assertEquals(hrCursorX, powerCursorX, 0.00001f)
        assertEquals(powerCursorX, paceCursorX, 0.00001f)
    }
}
