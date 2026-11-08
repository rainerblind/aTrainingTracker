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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests verifying Y-axis intermediate elevation grid label clearance
 * and dynamic right alignment in [ElevationProfile] (REQ-UI-494.1, REQ-UI-494.2, TST-UI-494.1, TST-UI-494.2).
 */
class ElevationProfileClearanceContractTest {

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
    fun testShouldRenderYTickLabel_suppressesBottomCollision() {
        val canvasHeight = 100f
        val maxLabelBaselineY = 20f
        val textSize = 24f
        // 400m intermediate tick scenario: y = 75f, labelBaselineY = 85f, distance to height = 15f < 32.4f (24 * 1.35)
        val yNearBottom = 75f
        val shouldRender = ElevationProfileZoomMath.shouldRenderYTickLabel(
            y = yNearBottom,
            canvasHeight = canvasHeight,
            maxLabelBaselineY = maxLabelBaselineY,
            lastY = -1000f,
            textSize = textSize
        )
        assertFalse("Tick label whose baseline falls within clearance of bottom label must be suppressed", shouldRender)
    }

    @Test
    fun testShouldRenderYTickLabel_rendersWhenClearanceSatisfied() {
        val canvasHeight = 150f
        val maxLabelBaselineY = 20f
        val textSize = 24f
        // Well-spaced tick at y = 75f, labelBaselineY = 85f
        // height - labelBaselineY = 65f >= 32.4f, labelBaselineY - maxLabelBaselineY = 65f >= 32.4f
        val yMiddle = 75f
        val shouldRender = ElevationProfileZoomMath.shouldRenderYTickLabel(
            y = yMiddle,
            canvasHeight = canvasHeight,
            maxLabelBaselineY = maxLabelBaselineY,
            lastY = -1000f,
            textSize = textSize
        )
        assertTrue("Tick label with sufficient vertical clearance must be rendered", shouldRender)
    }

    @Test
    fun testShouldRenderYTickLabel_suppressesTopCollision() {
        val canvasHeight = 150f
        val maxLabelBaselineY = 25f
        val textSize = 24f
        // Tick too close to maxAltLabel: y = 20f, labelBaselineY = 30f, distance to top baseline = 5f < 32.4f
        val yNearTop = 20f
        val shouldRender = ElevationProfileZoomMath.shouldRenderYTickLabel(
            y = yNearTop,
            canvasHeight = canvasHeight,
            maxLabelBaselineY = maxLabelBaselineY,
            lastY = -1000f,
            textSize = textSize
        )
        assertFalse("Tick label whose baseline falls within clearance of maxAltLabel must be suppressed", shouldRender)
    }

    @Test
    fun testShouldRenderYTickLabel_suppressesAdjacentTickCollision() {
        val canvasHeight = 200f
        val maxLabelBaselineY = 20f
        val textSize = 24f
        val lastY = 100f
        // Next tick at y = 115f, |115 - 100| = 15f < 32.4f
        val yAdjacent = 115f
        val shouldRender = ElevationProfileZoomMath.shouldRenderYTickLabel(
            y = yAdjacent,
            canvasHeight = canvasHeight,
            maxLabelBaselineY = maxLabelBaselineY,
            lastY = lastY,
            textSize = textSize
        )
        assertFalse("Tick label within minimum spacing of previous tick must be suppressed", shouldRender)
    }

    @Test
    fun testElevationProfile_contractInspection_usesDynamicRightAlignmentAndClearanceGuard() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // REQ-UI-494.1: Clearance evaluation via shouldRenderYTickLabel
        assertTrue(
            "ElevationProfile must invoke ElevationProfileZoomMath.shouldRenderYTickLabel",
            content.contains("ElevationProfileZoomMath.shouldRenderYTickLabel")
        )

        // REQ-UI-494.2: Dynamic right alignment (-altWidth - 10f)
        assertTrue(
            "ElevationProfile must measure altText width for right alignment",
            content.contains("val altWidth = textPaint.measureText(altText)")
        )
        assertTrue(
            "ElevationProfile must draw intermediate text at -altWidth - 10f",
            content.contains("canvas.nativeCanvas.drawText(altText, -altWidth - 10f, y + 10f, textPaint)")
        )

        // Must not contain hardcoded -110f for intermediate Y labels
        assertFalse(
            "ElevationProfile must not hardcode -110f for intermediate Y labels",
            content.contains("-110f, y + 10f")
        )
    }
}
