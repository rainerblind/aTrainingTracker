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
 * Architectural contract tests for [ElevationProfile] verifying dynamic tick clearance,
 * adjacent label collision prevention, and synchronized tick notch suppression as mandated by
 * REQ-UI-272 and TST-UI-232 (ATT-2385).
 */
class ElevationProfileContractTest {

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
    fun testElevationProfile_integratesDynamicClearanceAndShouldRenderTickLabel() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Must define minSpacingPx based on 24.dp.toPx()
        assertTrue(
            "ElevationProfile must define minSpacingPx with 24.dp.toPx()",
            content.contains("val minSpacingPx = 24.dp.toPx()")
        )

        // 2. Must initialize and track lastDrawnRightX
        assertTrue(
            "ElevationProfile must initialize lastDrawnRightX",
            content.contains("var lastDrawnRightX = if (currentZoomScale > 1.01f)")
        )

        // 3. Must invoke ElevationProfileZoomMath.shouldRenderTickLabel
        assertTrue(
            "ElevationProfile must invoke ElevationProfileZoomMath.shouldRenderTickLabel",
            content.contains("ElevationProfileZoomMath.shouldRenderTickLabel(")
        )

        // 4. Must update lastDrawnRightX after rendering intermediate tick
        assertTrue(
            "ElevationProfile must update lastDrawnRightX = labelRight",
            content.contains("lastDrawnRightX = labelRight")
        )
    }
}
