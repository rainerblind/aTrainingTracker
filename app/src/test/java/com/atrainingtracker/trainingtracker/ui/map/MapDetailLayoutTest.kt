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
 * Visual and structural contract tests for [MapDetailLayout] verifying
 * resilient map preview visibility, overflow detection, and scrollable container architecture
 * as mandated by REQ-UI-213 and TST-UI-167 (ATT-1812).
 */
class MapDetailLayoutTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    @Test
    fun testMapDetailLayout_declaresNullableAnalyticsContent() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must declare nullable analyticsContent with default null (REQ-UI-213)",
            content.contains("analyticsContent: (@Composable ColumnScope.() -> Unit)? = null")
        )
    }

    @Test
    fun testMapDetailLayout_detectsScrollableContent() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must compute hasTelemetryGraphs checking HR, Speed, and Power data",
            content.contains("val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (") &&
                    content.contains("TelemetryMetricUtils.hasHeartRateData(activeScrubPath)") &&
                    content.contains("TelemetryMetricUtils.hasSpeedData(activeScrubPath)") &&
                    content.contains("TelemetryMetricUtils.hasPowerData(activeScrubPath)")
        )

        assertTrue(
            "MapDetailLayout must compute hasScrollableContent checking analyticsContent and telemetry graphs",
            content.contains("val hasScrollableContent = analyticsContent != null || hasTelemetryGraphs")
        )
    }

    @Test
    fun testMapDetailLayout_appliesResilientMinHeightAndScroll() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Minimum height on Map Box when scrollable content is present
        assertTrue(
            "MapDetailLayout must apply heightIn(min = 240.dp) to Map Box to prevent 0dp collapse (REQ-UI-213)",
            content.contains(".heightIn(min = 240.dp)")
        )

        // 2. Vertical scroll on lower charts and analytics container
        assertTrue(
            "MapDetailLayout must apply verticalScroll(rememberScrollState()) to lower container (REQ-UI-213)",
            content.contains(".verticalScroll(rememberScrollState())")
        )

        assertTrue(
            "MapDetailLayout lower container must use flexible weight (1.2f) when scrollable",
            content.contains("if (showMap && hasScrollableContent)") &&
                    content.contains(".weight(1.2f)")
        )
    }

    @Test
    fun testMapDetailLayout_preservesFullWeightForRoutesAndSegments() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must retain wrapContentHeight on lower container when hasScrollableContent is false",
            content.contains("Modifier\n                .fillMaxWidth()\n                .wrapContentHeight()") ||
                    content.contains(".fillMaxWidth().wrapContentHeight()")
        )
    }

    @Test
    fun testMapDetailLayout_telemetryGraphOrdering_speedPrecedesHeartRate() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        val speedIndex = content.indexOf("TelemetryMetricUtils.hasSpeedData(path)")
        val hrIndex = content.indexOf("TelemetryMetricUtils.hasHeartRateData(path)")
        val powerIndex = content.indexOf("TelemetryMetricUtils.hasPowerData(path)")

        assertTrue("hasSpeedData check must exist in MapDetailLayout", speedIndex != -1)
        assertTrue("hasHeartRateData check must exist in MapDetailLayout", hrIndex != -1)
        assertTrue("hasPowerData check must exist in MapDetailLayout", powerIndex != -1)

        assertTrue(
            "Speed/Pace graph must precede Heart Rate graph under showZoomControls (REQ-UI-214, TST-UI-168.1)",
            speedIndex < hrIndex
        )
        assertTrue(
            "Heart Rate graph must precede Power graph under showZoomControls (REQ-UI-214, TST-UI-168.1)",
            hrIndex < powerIndex
        )
    }
}
