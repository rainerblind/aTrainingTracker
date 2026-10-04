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
 */

package com.atrainingtracker.trainingtracker.ui.segments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for [LiveSegmentSheet], [MapDetailLayout], and [SensorGridScreen]
 * verifying clamped bottom sheet height to elevation profile content height without expanding
 * to the top of the viewport (ATT-2231 / REQ-UI-270 / TST-UI-229).
 */
class LiveSegmentSheetContractTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Source file not found in candidate paths for: $relativePath")
    }

    @Test
    fun testLiveSegmentSheet_forwardsWrapContentHeightAndSuppressesMap() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt")
        val content = file.readText()

        // 1. Accepts optional modifier parameter
        assertTrue(
            "LiveSegmentSheet must accept an optional modifier parameter",
            content.contains("modifier: Modifier = Modifier")
        )

        // 2. Passes wrapContentHeight into MapDetailLayout
        assertTrue(
            "LiveSegmentSheet must apply modifier.fillMaxWidth().wrapContentHeight()",
            content.contains("modifier = modifier.fillMaxWidth().wrapContentHeight()")
        )

        // 3. Suppresses map and zoom controls
        assertTrue("LiveSegmentSheet must pass showMap = false", content.contains("showMap = false"))
        assertTrue("LiveSegmentSheet must pass showZoomControls = false", content.contains("showZoomControls = false"))
    }

    @Test
    fun testMapDetailLayout_branchesViewportModifierOnShowMapOrHasScrollableContent() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        val content = file.readText()

        // 1. Viewport Box modifier branches on showMap || hasScrollableContent
        assertTrue(
            "MapDetailLayout must branch viewport modifier based on showMap || hasScrollableContent",
            content.contains("val viewportModifier = if (showMap || hasScrollableContent)")
        )

        // 2. Uses fillMaxWidth().wrapContentHeight() when !showMap && !hasScrollableContent
        assertTrue(
            "MapDetailLayout must apply wrapContentHeight when !showMap && !hasScrollableContent",
            content.contains("Modifier.fillMaxWidth().wrapContentHeight()")
        )

        // 3. Viewport Box applies viewportModifier
        assertTrue(
            "Viewport Box must use viewportModifier with top padding",
            content.contains("modifier = viewportModifier") && content.contains(".padding(top = currentTopPaddingDp)")
        )
    }

    @Test
    fun testMapDetailLayout_attachesNestedScrollConditionally() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        val content = file.readText()

        // nestedScroll must only attach when showMap || hasScrollableContent
        assertTrue(
            "MapDetailLayout must only attach nestedScroll when showMap || hasScrollableContent",
            content.contains("if (showMap || hasScrollableContent) Modifier.nestedScroll(connection) else Modifier")
        )
    }

    @Test
    fun testSensorGridScreen_liveSegmentContainerWrapsContentHeight() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // In sheetContent, the LiveSegment container Box must explicitly wrap content height
        assertTrue(
            "SensorGridScreen sheetContent must include .wrapContentHeight() for LiveSegment container",
            content.contains(".fillMaxWidth()") &&
            content.contains(".wrapContentHeight()") &&
            content.contains("LiveSegmentSheet(")
        )
    }
}
