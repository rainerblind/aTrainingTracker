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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and gesture contract tests for [TelemetryMetricGraph] verifying directional
 * slope disambiguation, elimination of unconditional detectDragGestures, and unconsumed vertical
 * swipe propagation as mandated by REQ-UI-226 and TST-UI-180.3 (ATT-1872).
 */
class TelemetryMetricGraphGestureTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val telemetryMetricGraphFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
    }

    @Test
    fun testTelemetryMetricGraph_eliminatesUnconditionalDragAndTapDetectors() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        // 1. detectDragGestures must be eliminated
        assertFalse(
            "TelemetryMetricGraph must NOT use unconditional detectDragGestures (REQ-UI-226)",
            content.contains("detectDragGestures(")
        )

        // 2. detectTapGestures must be eliminated in favor of unified awaitEachGesture
        assertFalse(
            "TelemetryMetricGraph must NOT use detectTapGestures (REQ-UI-226)",
            content.contains("detectTapGestures(")
        )
    }

    @Test
    fun testTelemetryMetricGraph_integratesChartGestureDisambiguator() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        // 1. References touchSlop and awaitEachGesture
        assertTrue(
            "TelemetryMetricGraph must use awaitEachGesture",
            content.contains("awaitEachGesture {")
        )
        assertTrue(
            "TelemetryMetricGraph must query viewConfiguration.touchSlop",
            content.contains("val touchSlop = viewConfiguration.touchSlop")
        )

        // 2. Evaluates directional dominance
        assertTrue(
            "TelemetryMetricGraph must invoke ChartGestureDisambiguator.isDominantVertical",
            content.contains("ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)")
        )
        assertTrue(
            "TelemetryMetricGraph must invoke ChartGestureDisambiguator.isDominantHorizontal",
            content.contains("ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)")
        )

        // 3. Tracks isVerticalScrolling and guards tap selection
        assertTrue(
            "TelemetryMetricGraph must track isVerticalScrolling",
            content.contains("var isVerticalScrolling = false")
        )
        assertTrue(
            "TelemetryMetricGraph must guard tap selection with !isVerticalScrolling",
            content.contains("else if (!isVerticalScrolling) {")
        )

        // 4. Clears distance on drag completion when not in pan mode
        assertTrue(
            "TelemetryMetricGraph must clear distance on drag completion when not in pan mode",
            content.contains("if (isDragging) {") &&
                    Regex("""if\s*\(!isPanMode\)\s*\{\s*onDistanceSelected\(null\)""").containsMatchIn(content)
        )
    }

    @Test
    fun testTelemetryMetricGraph_supportsPanModeGesturesAndZoomMath() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        // 1. Declares isPanMode and onZoomChanged parameters
        assertTrue(
            "TelemetryMetricGraph must declare isPanMode parameter with default false (REQ-UI-232)",
            content.contains("isPanMode: Boolean = false")
        )
        assertTrue(
            "TelemetryMetricGraph must declare onZoomChanged parameter with default null (REQ-UI-232)",
            content.contains("onZoomChanged: ((Float, Double) -> Unit)? = null")
        )

        // 2. Incorporates isPanMode into pointerInput keys
        assertTrue(
            "TelemetryMetricGraph must incorporate isPanMode into pointerInput remember keys",
            content.contains("pointerInput(totalSpan, isTimeDomain, zoomScale, startDist, isPanMode)")
        )

        // 3. Invokes ElevationProfileZoomMath.applyPan and dispatches onZoomChanged
        assertTrue(
            "TelemetryMetricGraph must calculate dragDeltaX and invoke ElevationProfileZoomMath.applyPan",
            content.contains("ElevationProfileZoomMath.applyPan(") &&
                    content.contains("dragDeltaX = pointer.position.x - prevX")
        )
        assertTrue(
            "TelemetryMetricGraph must dispatch updated startDist via onZoomChanged",
            Regex("""onZoomChanged\(\s*zoomScale\s*,\s*panStart\s*\)""").containsMatchIn(content)
        )

        // 4. Guards tap selection against pan mode
        assertTrue(
            "TelemetryMetricGraph must guard tap selection with !isPanMode",
            content.contains("else if (!isVerticalScrolling) {") &&
                    Regex("""else if \(!isVerticalScrolling\) \{\s*if \(!isPanMode\) \{""").containsMatchIn(content)
        )
    }
}
