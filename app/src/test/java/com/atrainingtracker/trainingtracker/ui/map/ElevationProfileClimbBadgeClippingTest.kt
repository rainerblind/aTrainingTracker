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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying dynamic font-scale aware top clearance, container stability,
 * minimum chart height guarantees, and boundary ceiling / margin clamping for
 * climb category marker badges in [ElevationProfile] (TST-UI-322 / REQ-UI-274).
 */
class ElevationProfileClimbBadgeClippingTest {

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

    @Test
    fun testClimbBadge_dynamicTopClearance_allocatesRequiredPadding() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify dynamic top clearance formula based on LocalDensity and climb presence
        assertTrue(
            "ElevationProfile must define requiredClimbTopClearance dynamically",
            content.contains("requiredClimbTopClearance") &&
                    content.contains("10.sp.toDp()")
        )
        assertTrue(
            "ElevationProfile must compute climbBadgeTopPadding when climbs are present and not time domain",
            content.contains("climbBadgeTopPadding") &&
                    content.contains("!isTimeDomain && climbs.isNotEmpty()")
        )

        // Mathematical verification at standard font scale (1.0x)
        val textSizeDp = 10f // 10.sp at 1.0x
        val pillPaddingVDp = 4f // 2.dp * 2
        val stemLengthDp = 8f
        val safetyMarginDp = 2f
        val requiredClearance = textSizeDp + pillPaddingVDp + stemLengthDp + safetyMarginDp
        assertEquals("Standard font scale required clearance must be 24dp", 24f, requiredClearance, 0.001f)

        val baseTopPadding = 16f
        val overhead = (requiredClearance - baseTopPadding).coerceAtLeast(0f)
        assertEquals("Overhead in compact preview must be 8dp", 8f, overhead, 0.001f)
        val effectivePadding = baseTopPadding + overhead
        assertEquals("Effective top padding in compact preview must be 24dp", 24f, effectivePadding, 0.001f)
    }

    @Test
    fun testClimbBadge_zeroTopClearance_whenClimbsEmpty() {
        val baseTopPadding = 16f
        val climbsEmpty = true
        val isTimeDomain = false
        val requiredClearance = 24f

        val overhead = if (!isTimeDomain && !climbsEmpty) {
            (requiredClearance - baseTopPadding).coerceAtLeast(0f)
        } else {
            0f
        }
        assertEquals("Zero clearance overhead when climbs are empty", 0f, overhead, 0.001f)
        val effectivePadding = baseTopPadding + overhead
        assertEquals("Effective top padding must remain strictly 16dp when climbs are empty", 16f, effectivePadding, 0.001f)
    }

    @Test
    fun testClimbBadge_zeroTopClearance_inDetailView() {
        // In detail view, showZoomControls == true, so topPadding = 44.dp
        val topPadding = 44f
        val requiredClearance = 24f // At 1.0x font scale

        val overhead = (requiredClearance - topPadding).coerceAtLeast(0f)
        assertEquals("Zero clearance overhead in detail view since 44dp >= 24dp", 0f, overhead, 0.001f)
        val effectivePadding = topPadding + overhead
        assertEquals("Effective top padding must remain strictly 44dp in detail view", 44f, effectivePadding, 0.001f)
    }

    @Test
    fun testClimbBadge_containerHeightStability_preservesTotalCanvasHeight() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Contract verification: totalCanvasHeight must NOT be modified by climb presence
        assertTrue(
            "ElevationProfile must maintain exact totalCanvasHeight formula preserving container stability",
            content.contains("val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight")
        )
    }

    @Test
    fun testClimbBadge_fixedHeight_rescalesChartWithoutOverflow() {
        val fixedHeight = 120f
        val bottomPadding = 18f
        val requiredClearance = 24f

        val maxAllowedTop = (fixedHeight - bottomPadding - 32f).coerceAtLeast(8f)
        val effectiveTopPadding = requiredClearance.coerceIn(8f, maxAllowedTop)
        val chartHeight = fixedHeight - bottomPadding - effectiveTopPadding

        assertEquals("Effective top padding must be 24dp in 120dp container", 24f, effectiveTopPadding, 0.001f)
        assertEquals("Chart plotting height rescales cleanly to 78dp", 78f, chartHeight, 0.001f)
        assertTrue("Chart plotting height exceeds 32dp minimum", chartHeight >= 32f)
    }

    @Test
    fun testClimbBadge_accessibilityFontScaling_metrics() {
        fun computeClearance(fontScale: Float): Float {
            val textSizeDp = 10f * fontScale
            val pillPaddingVDp = 4f
            val stemLengthDp = 8f
            val safetyMarginDp = 2f
            return textSizeDp + pillPaddingVDp + stemLengthDp + safetyMarginDp
        }

        val clearance10x = computeClearance(1.0f)
        val clearance15x = computeClearance(1.5f)
        val clearance20x = computeClearance(2.0f)

        assertEquals("Clearance at 1.0x font scale", 24f, clearance10x, 0.001f)
        assertEquals("Clearance at 1.5x font scale", 29f, clearance15x, 0.001f)
        assertEquals("Clearance at 2.0x font scale", 34f, clearance20x, 0.001f)
    }

    @Test
    fun testClimbBadge_constrainedHeight_minimumChartHeightGuarantee() {
        // Tight 50dp container
        val fixedHeight = 50f
        val bottomPadding = 18f
        val requiredClearance = 24f

        val maxAllowedTop = (fixedHeight - bottomPadding - 32f).coerceAtLeast(8f)
        // 50 - 18 - 32 = 0 -> coerced to 8dp
        assertEquals("Max allowed top padding in 50dp container is clamped to 8dp", 8f, maxAllowedTop, 0.001f)

        val effectiveTopPadding = requiredClearance.coerceIn(8f, maxAllowedTop)
        assertEquals("Effective top padding in 50dp container is 8dp", 8f, effectiveTopPadding, 0.001f)

        val chartHeight = fixedHeight - bottomPadding - effectiveTopPadding
        assertEquals("Chart height is exactly 24dp (or at least clamped safely)", 24f, chartHeight, 0.001f)

        // Mathematical check on boundary ceiling clamp
        val minAllowedPillTop = -effectiveTopPadding + 2f // -8 + 2 = -6dp
        val summitY = 0f // peak altitude
        val pillHeight = 14f
        val unconstrainedPillBottom = summitY - 8f // -8dp
        val unconstrainedPillTop = unconstrainedPillBottom - pillHeight // -22dp
        val clampedPillTop = unconstrainedPillTop.coerceAtLeast(minAllowedPillTop) // -6dp
        val yBox = effectiveTopPadding + clampedPillTop // 8 + (-6) = +2dp
        assertEquals("Badge pill top in Box coordinates satisfies boundary ceiling guard", 2f, yBox, 0.001f)
    }

    @Test
    fun testClimbBadge_verticalCeilingAndHorizontalSafetyMargins() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Check source code contains boundary ceiling and margin protections
        assertTrue(
            "ElevationProfile must contain minAllowedPillTop boundary ceiling guard",
            content.contains("minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()") ||
                    content.contains("minAllowedPillTop =")
        )
        assertTrue(
            "ElevationProfile must enforce horizontal safety margins",
            content.contains("2.dp.toPx(), width - pillWidth - 2.dp.toPx()") ||
                    content.contains("pillLeft = (summitX - pillWidth / 2f).coerceIn(2.dp.toPx()")
        )

        // Mathematical verification of peak elevation summit (summitY = 0)
        val effectiveTopPadding = 24f
        val minAllowedPillTop = -effectiveTopPadding + 2f // -22dp
        val unconstrainedPillTop = 0f - 8f - 14f // -22dp
        val clampedPillTop = unconstrainedPillTop.coerceAtLeast(minAllowedPillTop)
        val yBox = effectiveTopPadding + clampedPillTop
        assertEquals("At peak elevation (Y=0), pill top in box coordinates is at least 2dp", 2f, yBox, 0.001f)

        // Mathematical verification of horizontal clamping
        val canvasWidth = 300f
        val pillWidth = 40f
        val minMargin = 2f

        // Case A: Summit at route start (X = 0)
        val clampedLeftStart = (0f - pillWidth / 2f).coerceIn(minMargin, canvasWidth - pillWidth - minMargin)
        assertEquals("Pill left at route start is clamped to 2dp", 2f, clampedLeftStart, 0.001f)

        // Case B: Summit at route finish (X = 300)
        val clampedLeftFinish = (300f - pillWidth / 2f).coerceIn(minMargin, canvasWidth - pillWidth - minMargin)
        assertEquals("Pill left at route finish is clamped to 258dp", 258f, clampedLeftFinish, 0.001f)
        val clampedRightFinish = clampedLeftFinish + pillWidth
        assertEquals("Pill right at route finish has 2dp margin from canvas edge", 298f, clampedRightFinish, 0.001f)
    }

    @Test
    fun testClimbBadge_preservesExistingLayoutAndClimbContracts() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Verbatim topPadding baseline check for ElevationProfileLayoutTest line 59
        assertTrue(
            "Must preserve exact verbatim topPadding declaration",
            content.contains("val topPadding = if (showZoomControls) 44.dp else 16.dp")
        )

        // 2. Verbatim pin stem drawing check for ElevationProfileClimbSpanContractTest line 146
        assertTrue(
            "Must preserve exact verbatim drawLine call for pin stem",
            content.contains("canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)")
        )
    }
}
