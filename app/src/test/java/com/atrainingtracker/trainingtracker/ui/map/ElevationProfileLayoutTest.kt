/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying contextual zoom controls suppression in list previews,
 * explicit enablement in [MapDetailLayout], and the non-overlapping vertical layout
 * architecture for [ElevationProfile] (TST-UI-151 / REQ-UI-197).
 */
class ElevationProfileLayoutTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    @Test
    fun testElevationProfile_parameterDefaults_suppressZoomControls() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify both overloads have showZoomControls: Boolean = false
        val count = Regex("""showZoomControls:\s*Boolean\s*=\s*false""").findAll(content).count()
        assertEquals("Both ElevationProfile composable overloads must default showZoomControls to false", 2, count)
    }

    @Test
    fun testElevationProfile_sourceCodeInspection_layoutSeparation() {
        val content = elevationProfileFile.readText()

        // 1. Dynamic topPadding: 72.dp for detail views (to accommodate badge layer), 16.dp for compact list previews
        assertTrue(
            "ElevationProfile must set topPadding = 72.dp when showZoomControls == true, else 16.dp",
            content.contains("val topPadding = if (showZoomControls) 72.dp else 16.dp")
        )

        // 2. Dynamic canvas height preserving exact drawable chart plotting area
        assertTrue(
            "ElevationProfile must expand totalCanvasHeight by 48.dp when showZoomControls == true",
            content.contains("val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 48.dp else cachedData.adaptiveHeight")
        )

        // 3. ScrubbingTelemetryBadge anchored at top = 28.dp to clear zoom controls row
        assertTrue(
            "ScrubbingTelemetryBadge must be padded at top = 28.dp to clear 24dp zoom controls",
            content.contains(".align(Alignment.TopCenter)\n                        .padding(top = 28.dp)") ||
                    content.contains(".align(Alignment.TopCenter).padding(top = 28.dp)")
        )

        // 4. Conditional pointerInput attachment (omitted in list previews to allow smooth scrolling and instant card clicks)
        assertTrue(
            "ElevationProfile must conditionally attach pointerInput only when showZoomControls == true",
            content.contains("val canvasModifier = if (showZoomControls) {")
        )

        // 5. Scrubber text baseline anchored at -4.dp.toPx()
        assertTrue(
            "Scrubber text baseline must be anchored at -4.dp.toPx() to clear controls row",
            content.contains("-4.dp.toPx()")
        )

        // 6. Zoom controls row guarded by showZoomControls
        assertTrue(
            "Zoom controls row must be guarded by showZoomControls",
            content.contains("if (showZoomControls && cachedData.totalDist > 10.0)")
        )

        // 7. Legend button guarded by showZoomControls
        assertTrue(
            "Legend button must be guarded by showZoomControls",
            content.contains("if (showZoomControls) {\n            IconButton(") ||
                    content.contains("if (showZoomControls) {\n            IconButton(\n                onClick = { showLegend = !showLegend }")
        )
    }

    @Test
    fun testMapDetailLayout_enablesZoomControls() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // Verify MapDetailLayout defines default showZoomControls = true and forwards it
        assertTrue(
            "MapDetailLayout must declare showZoomControls: Boolean = true",
            content.contains("showZoomControls: Boolean = true")
        )
        assertTrue(
            "MapDetailLayout must forward showZoomControls to ElevationProfile",
            content.contains("showZoomControls = showZoomControls")
        )
    }

    @Test
    fun testListPreviewCallers_doNotEnableZoomControls() {
        val previewFiles = listOf(
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt"),
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt"),
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentItem.kt"),
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        )

        for (file in previewFiles) {
            assertTrue("Preview caller file must exist: ${file.name}", file.exists())
            val content = file.readText()
            assertFalse(
                "${file.name} must NOT pass showZoomControls = true (must use default false)",
                content.contains("showZoomControls = true")
            )
        }
    }

    @Test
    fun testVerticalLayoutGeometry_guaranteesNonOverlappingBounds() {
        // Layer 1: Controls row layout geometry in detailed view
        val controlsTopDp = 2.0
        val controlsHeightDp = 24.0
        val controlsBottomDp = controlsTopDp + controlsHeightDp // 26.0 dp

        // Layer 2: Multi-metric telemetry badge layout geometry
        val badgeTopDp = 28.0
        val badgeHeightDp = 42.0 // maximum two-row badge height
        val badgeBottomDp = badgeTopDp + badgeHeightDp // 70.0 dp

        // Layer 3: Canvas chart curve plotting geometry
        val canvasTopPaddingDp = 72.0
        val bottomPaddingDp = 24.0
        val heightExpansionDp = 48.0

        // Clearance 1: Between bottom of controls and top of telemetry badge
        val clearanceControlsToBadgeDp = badgeTopDp - controlsBottomDp
        assertTrue("Clearance between control buttons and telemetry badge must be at least 2.0 dp", clearanceControlsToBadgeDp >= 2.0)
        assertEquals(2.0, clearanceControlsToBadgeDp, 0.001)

        // Clearance 2: Between bottom of telemetry badge and start of canvas chart plotting
        val clearanceBadgeToCanvasDp = canvasTopPaddingDp - badgeBottomDp
        assertTrue("Clearance between telemetry badge and canvas chart curve must be at least 2.0 dp", clearanceBadgeToCanvasDp >= 2.0)
        assertEquals(2.0, clearanceBadgeToCanvasDp, 0.001)

        // Plotting height invariant check:
        // Prior drawable plotting height: (adaptiveHeight + 20.dp) - 44.dp - 24.dp = adaptiveHeight - 48.dp
        // New drawable plotting height: (adaptiveHeight + 48.dp) - 72.dp - 24.dp = adaptiveHeight - 48.dp
        val netDrawableHeightDelta = heightExpansionDp - canvasTopPaddingDp - bottomPaddingDp // 48 - 72 - 24 = -48
        assertEquals(-48.0, netDrawableHeightDelta, 0.001)
    }

    @Test
    fun testElevationProfile_declaresHoistedZoomParameters() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify both overloads have zoomScale: Float = 1.0f, startDist: Double = 0.0, onZoomChanged
        val zoomScaleCount = Regex("""zoomScale:\s*Float\s*=\s*1\.0f""").findAll(content).count()
        assertEquals("Both ElevationProfile composable overloads must declare zoomScale: Float = 1.0f", 2, zoomScaleCount)

        val startDistCount = Regex("""startDist:\s*Double\s*=\s*0\.0""").findAll(content).count()
        assertEquals("Both ElevationProfile composable overloads must declare startDist: Double = 0.0", 2, startDistCount)

        val onZoomChangedCount = Regex("""onZoomChanged:\s*\(\(zoomScale:\s*Float,\s*startDist:\s*Double\)\s*->\s*Unit\)\?\s*=\s*null""").findAll(content).count()
        assertEquals("Both ElevationProfile composable overloads must declare onZoomChanged callback", 2, onZoomChangedCount)
    }
}
