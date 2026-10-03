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
            content.contains("val hasTelemetryGraphs = showZoomControls && showTelemetryCharts && activeScrubPath != null && (") &&
                    content.contains("TelemetryMetricUtils.hasHeartRateData(activeScrubPath)") &&
                    content.contains("TelemetryMetricUtils.hasSpeedData(activeScrubPath)") &&
                    content.contains("TelemetryMetricUtils.hasPowerData(activeScrubPath)")
        )

        assertTrue(
            "MapDetailLayout must compute hasScrollableContent checking metadataContent, analyticsContent and telemetry graphs (REQ-UI-245)",
            content.contains("val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs")
        )
    }

    @Test
    fun testMapDetailLayout_declaresShowTelemetryCharts() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must declare showTelemetryCharts parameter with default true (REQ-UI-240)",
            content.contains("showTelemetryCharts: Boolean = true")
        )
    }

    @Test
    fun testMapDetailLayout_appliesResilientMinHeightAndScroll() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Minimum height on Map Box when scrollable content is present (REQ-UI-213 evolved by REQ-UI-223)
        assertTrue(
            "MapDetailLayout must apply heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT) to Map Box to prevent 0dp collapse (REQ-UI-213, REQ-UI-223)",
            content.contains(".heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)")
        )

        // 2. Vertical scroll on lower charts and analytics container
        assertTrue(
            "MapDetailLayout must apply verticalScroll(rememberScrollState()) to lower container (REQ-UI-213)",
            content.contains(".verticalScroll(rememberScrollState())")
        )

        assertTrue(
            "MapDetailLayout lower container must use dynamic weight (1f - splitFraction) when scrollable (REQ-UI-223)",
            content.contains("if (showMap && hasScrollableContent)") &&
                    content.contains(".weight(1f - splitFraction)")
        )
    }

    @Test
    fun testMapDetailLayout_preservesFullWeightForRoutesAndSegments() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must retain wrapContentHeight on lower container when hasScrollableContent is false",
            Regex("""Modifier\s*\.fillMaxWidth\(\)\s*\.wrapContentHeight\(\)""").containsMatchIn(content)
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

    @Test
    fun testMapDetailLayout_wiresGlobalZoomState() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Zoom and Viewport Fraction state hoisting with remember(activeScrubPath) (REQ-UI-215, REQ-UI-232, REQ-UI-234)
        assertTrue(
            "MapDetailLayout must hoist profileZoomScale remembering across activeScrubPath (REQ-UI-215)",
            content.contains("var profileZoomScale by remember(activeScrubPath) { mutableFloatStateOf(1.0f) }")
        )
        assertTrue(
            "MapDetailLayout must hoist viewportStartFraction remembering across activeScrubPath (REQ-UI-232, REQ-UI-234)",
            content.contains("var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }")
        )

        // 2. Wired to ElevationProfile via MapDetailViewportMath
        assertTrue(
            "MapDetailLayout must pass zoomScale and startDist to ElevationProfile",
            content.contains("zoomScale = profileZoomScale") &&
                    content.contains("startDist = MapDetailViewportMath.fractionToDomain(viewportStartFraction, elevationTotalSpan, profileZoomScale)")
        )
        assertTrue(
            "MapDetailLayout must provide onZoomChanged callback to ElevationProfile updating viewportStartFraction",
            content.contains("onZoomChanged = { z, s ->") &&
                    content.contains("profileZoomScale = z") &&
                    content.contains("viewportStartFraction = MapDetailViewportMath.domainToFraction(s, elevationTotalSpan, z)")
        )

        // 3. Forwarded to TelemetryMetricGraphs
        val occurrencesZoomScale = Regex("""zoomScale\s*=\s*profileZoomScale""").findAll(content).count()
        assertTrue(
            "MapDetailLayout must forward zoomScale = profileZoomScale to ElevationProfile and 3 TelemetryMetricGraphs (count >= 4)",
            occurrencesZoomScale >= 4
        )

        val occurrencesFractionToDomain = Regex("""MapDetailViewportMath\.fractionToDomain""").findAll(content).count()
        assertTrue(
            "MapDetailLayout must forward mapped startDist via MapDetailViewportMath to ElevationProfile and TelemetryMetricGraphs (count >= 4)",
            occurrencesFractionToDomain >= 4
        )
    }

    @Test
    fun testMapDetailLayout_integratesGlobalTelemetryZoomToolbar() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Zoom toolbar height definition and subtraction in SplitPaneMath
        assertTrue(
            "MapDetailLayout must query GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT",
            content.contains("val toolbarHeightPx = if (hasZoomToolbar) with(density) { GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT.toPx() } else 0f")
        )
        assertTrue(
            "MapDetailLayout must subtract dividerHeightPx + toolbarHeightPx in SplitPaneMath.calculateAvailableHeight",
            content.contains("SplitPaneMath.calculateAvailableHeight(totalHeightPx, dividerHeightPx + toolbarHeightPx)")
        )

        // 2. Toolbar placement directly below SplitPaneDivider and outside scrollable lower container
        val dividerIndex = content.indexOf("SplitPaneDivider(")
        val toolbarIndex = content.indexOf("GlobalTelemetryZoomToolbar(")
        val lowerColIndex = content.indexOf("lowerColumn(")

        assertTrue("SplitPaneDivider must be present", dividerIndex != -1)
        assertTrue("GlobalTelemetryZoomToolbar must be present", toolbarIndex != -1)
        assertTrue("lowerColumn must be present", lowerColIndex != -1)

        assertTrue(
            "GlobalTelemetryZoomToolbar must be placed between SplitPaneDivider and lowerColumn (sticky outside vertical scroll)",
            dividerIndex < toolbarIndex && toolbarIndex < lowerColIndex
        )

        // 3. Pan mode hoisting and passing to ElevationProfile and TelemetryMetricGraphs
        assertTrue(
            "MapDetailLayout must hoist isPanMode",
            content.contains("var isPanMode by remember(activeScrubPath) { mutableStateOf(false) }")
        )
        val occurrencesPanMode = Regex("""isPanMode\s*=\s*isPanMode""").findAll(content).count()
        assertTrue(
            "MapDetailLayout must forward isPanMode = isPanMode to ElevationProfile and 3 TelemetryMetricGraphs (count >= 4) (REQ-UI-232)",
            occurrencesPanMode >= 4
        )

        val occurrencesOnZoomChanged = Regex("""onZoomChanged\s*=\s*\{\s*z,\s*s\s*->""").findAll(content).count()
        assertTrue(
            "MapDetailLayout must wire onZoomChanged callback to ElevationProfile and 3 TelemetryMetricGraphs (count >= 4) (REQ-UI-232)",
            occurrencesOnZoomChanged >= 4
        )
    }
}

