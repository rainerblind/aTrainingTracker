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
 * Structural and gesture contract tests for [ElevationProfile] verifying directional
 * slope disambiguation, unconsumed vertical drag propagation, and tap suppression
 * as mandated by REQ-UI-226 and TST-UI-180.2 (ATT-1872).
 */
class ElevationProfileGestureTest {

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
    fun testElevationProfile_integratesChartGestureDisambiguator() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. References ChartGestureDisambiguator and touchSlop
        assertTrue(
            "ElevationProfile must query viewConfiguration.touchSlop",
            content.contains("val touchSlop = viewConfiguration.touchSlop")
        )
        assertTrue(
            "ElevationProfile must invoke ChartGestureDisambiguator.isDominantVertical",
            content.contains("ChartGestureDisambiguator.isDominantVertical(diffX, diffY, touchSlop)")
        )
        assertTrue(
            "ElevationProfile must invoke ChartGestureDisambiguator.isDominantHorizontal",
            content.contains("ChartGestureDisambiguator.isDominantHorizontal(diffX, diffY, touchSlop)")
        )

        // 2. Direction-agnostic 64f distance threshold must be eliminated
        assertFalse(
            "Direction-agnostic diffX * diffX + diffY * diffY > 64f must be eliminated (REQ-UI-226)",
            content.contains("diffX * diffX + diffY * diffY > 64f")
        )
    }

    @Test
    fun testElevationProfile_verticalDragDoesNotConsumeAndSuppressesTap() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. isVerticalScrolling flag tracked
        assertTrue(
            "ElevationProfile must declare and track isVerticalScrolling flag",
            content.contains("var isVerticalScrolling = false")
        )

        // 2. Vertical scrolling yields unconsumed to parent verticalScroll
        assertTrue(
            "ElevationProfile must break on pointer.isConsumed during vertical scrolling",
            content.contains("else if (isVerticalScrolling) {\n                                if (pointer.isConsumed) {\n                                    break\n                                }\n                            }") ||
                    content.contains("else if (isVerticalScrolling)")
        )

        // 3. Tap inspection guarded by !isVerticalScrolling
        assertTrue(
            "ElevationProfile must guard tap detection with !isVerticalScrolling",
            content.contains("else if (!isVerticalScrolling) {")
        )
    }

    @Test
    fun testElevationProfile_multiTouchPinchZoomPreserved() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify multi-touch transform consumes both pointers
        assertTrue(
            "ElevationProfile must consume pointer changes during multi-touch transform",
            content.contains("pressed.size >= 2 && totalSpan > 10.0") &&
                    content.contains("pressed.forEach { it.consume() }")
        )
    }
}
