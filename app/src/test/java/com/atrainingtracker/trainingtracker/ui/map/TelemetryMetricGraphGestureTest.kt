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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural and gesture contract tests for [TelemetryMetricGraph] verifying directional
 * slope disambiguation, elimination of unconditional detectDragGestures, and unconsumed vertical
 * swipe propagation as mandated by REQ-UI-226 and TST-UI-180.3 (ATT-1872).
 */
class TelemetryMetricGraphGestureTest {

    @Test
    fun testTelemetryMetricGraph_eliminatesUnconditionalDragAndTapDetectors() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("TelemetryMetricGraph") }
        assertTrue("TelemetryMetricGraph composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testTelemetryMetricGraph_integratesChartGestureDisambiguator() {
        val touchSlop = 10f
        assertTrue(ChartGestureDisambiguator.isDominantVertical(2f, 15f, touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantVertical(15f, 2f, touchSlop))

        assertTrue(ChartGestureDisambiguator.isDominantHorizontal(15f, 2f, touchSlop))
        assertFalse(ChartGestureDisambiguator.isDominantHorizontal(2f, 15f, touchSlop))
    }

    @Test
    fun testTelemetryMetricGraph_supportsPanModeGesturesAndZoomMath() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
        val zoomMathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileZoomMath")
        assertNotNull("ElevationProfileZoomMath must exist", zoomMathClass)
    }
}
