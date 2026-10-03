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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for [TelemetryMetricGraph] verifying the optional
 * enableGestures parameter and conditional pointerInput attachment (REQ-UI-247 / TST-UI-206.1).
 */
class TelemetryMetricGraphGesturesContractTest {

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
    fun testTelemetryMetricGraph_declaresEnableGesturesParameterWithDefaultTrue() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        assertTrue(
            "TelemetryMetricGraph must declare enableGestures: Boolean = true (REQ-UI-247)",
            Regex("""enableGestures:\s*Boolean\s*=\s*true""").containsMatchIn(content)
        )
    }

    @Test
    fun testTelemetryMetricGraph_conditionallyAttachesPointerInputBasedOnEnableGestures() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryMetricGraphFile.exists())
        val content = telemetryMetricGraphFile.readText()

        assertTrue(
            "TelemetryMetricGraph must evaluate enableGestures before attaching pointerInput (REQ-UI-247)",
            content.contains("if (enableGestures)") &&
            content.contains("baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode)") &&
            content.contains("Canvas(\n            modifier = canvasModifier\n        )")
        )
    }
}
