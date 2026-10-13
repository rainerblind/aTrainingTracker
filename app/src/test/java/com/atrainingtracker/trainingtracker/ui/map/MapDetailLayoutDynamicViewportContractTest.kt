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
 * Architectural contract tests for [MapDetailLayout] verifying that non-scrollable viewports
 * (Route and Segment details) decouple from the proportional 50/50 split-pane, dynamically
 * maximize the upper map with weight 1f, and anchor the intrinsic elevation profile flush
 * to the Navigation Bar as mandated by REQ-UI-273 and TST-UI-233 (ATT-2386).
 */
class MapDetailLayoutDynamicViewportContractTest {

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
    fun testMapDetailLayout_differentiatesScrollableContentForDynamicMapExpansion() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Must check hasScrollableContent within showMap && hasLowerSection
        assertTrue(
            "MapDetailLayout must check if (hasScrollableContent) inside showMap && hasLowerSection",
            content.contains("if (showMap && hasLowerSection) {\n                if (hasScrollableContent) {") ||
            content.contains("if (showMap && hasLowerSection) {\r\n                if (hasScrollableContent) {")
        )

        // 2. In non-scrollable viewports (!hasScrollableContent), mapBox must receive weight(1f) and min map height
        assertTrue(
            "MapDetailLayout must assign weight(1f) to mapBox in non-scrollable viewports",
            content.contains(".weight(1f)\n                                .heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)") ||
            content.contains(".weight(1f)\r\n                                .heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)")
        )

        // 3. Lower container must wrap content height
        assertTrue(
            "MapDetailLayout must wrap content height for lower container in non-scrollable viewports",
            content.contains(".fillMaxWidth()\n                                .wrapContentHeight()") ||
            content.contains(".fillMaxWidth()\r\n                                .wrapContentHeight()")
        )

        // 4. Navigation bar padding must be applied when analyticsContent is null
        assertTrue(
            "MapDetailLayout must apply navigationBarsPadding when analyticsContent == null",
            content.contains("if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier")
        )
    }
}
