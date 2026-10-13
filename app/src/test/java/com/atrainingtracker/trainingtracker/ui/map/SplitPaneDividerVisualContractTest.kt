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
 * Visual and structural contract tests for [SplitPaneDivider] and [MapDetailLayout] integration
 * verifying draggable splitter architecture, touch affordances, and gesture reset (REQ-UI-223, TST-UI-177.2).
 */
class SplitPaneDividerVisualContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val splitPaneDividerFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/SplitPaneDivider.kt")
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    @Test
    fun testSplitPaneDivider_definesPillGripAndTouchTarget() {
        assertTrue("SplitPaneDivider.kt must exist", splitPaneDividerFile.exists())
        val content = splitPaneDividerFile.readText()

        // 24dp touch target height
        assertTrue(
            "SplitPaneDivider must declare 24.dp touch target height (REQ-UI-223)",
            content.contains("val DIVIDER_TOUCH_HEIGHT: Dp = 24.dp") &&
                    content.contains(".height(SplitPaneMath.DIVIDER_TOUCH_HEIGHT)")
        )

        // 32dp x 4dp pill grip affordance
        assertTrue(
            "SplitPaneDivider must define 32.dp x 4.dp pill grip with rounded corners (REQ-UI-223)",
            content.contains("val GRIP_WIDTH: Dp = 32.dp") &&
                    content.contains("val GRIP_HEIGHT: Dp = 4.dp") &&
                    content.contains(".width(SplitPaneMath.GRIP_WIDTH)") &&
                    content.contains(".height(SplitPaneMath.GRIP_HEIGHT)") &&
                    content.contains("RoundedCornerShape(2.dp)")
        )

        // Accessibility semantics
        assertTrue(
            "SplitPaneDivider must wire map_splitter_content_description via semantics (REQ-UI-223)",
            content.contains("R.string.map_splitter_content_description") &&
                    content.contains("semantics { contentDescription = description }")
        )
    }

    @Test
    fun testSplitPaneDivider_handlesDragAndDoubleTap() {
        assertTrue("SplitPaneDivider.kt must exist", splitPaneDividerFile.exists())
        val content = splitPaneDividerFile.readText()

        // Drag gesture handling
        assertTrue(
            "SplitPaneDivider must support vertical dragging via Modifier.draggable (REQ-UI-223)",
            content.contains("orientation = Orientation.Vertical") &&
                    content.contains("rememberDraggableState { delta -> onDelta(delta) }")
        )

        // Double-tap reset handling
        assertTrue(
            "SplitPaneDivider must support double-tap reset via detectTapGestures (REQ-UI-223)",
            content.contains("detectTapGestures(") &&
                    content.contains("onDoubleTap = { onReset() }")
        )
    }

    @Test
    fun testMapDetailLayout_integratesSplitPaneDivider() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // rememberSaveable splitFraction
        assertTrue(
            "MapDetailLayout must hoist splitFraction using rememberSaveable (REQ-UI-223)",
            content.contains("var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }")
        )

        // Integration of SplitPaneDivider in Column
        assertTrue(
            "MapDetailLayout must place SplitPaneDivider between mapBox and lowerColumn (REQ-UI-223)",
            content.contains("SplitPaneDivider(") &&
                    content.contains("onDelta = { delta ->") &&
                    content.contains("onReset = {")
        )

        // Dynamic weights and min map height
        assertTrue(
            "MapDetailLayout must assign weight(splitFraction) and heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT) to mapBox",
            content.contains(".weight(splitFraction)") &&
                    content.contains(".heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)")
        )
        assertTrue(
            "MapDetailLayout must assign weight(1f - splitFraction) and verticalScroll to lower container",
            content.contains(".weight(1f - splitFraction)") &&
                    content.contains(".verticalScroll(rememberScrollState())")
        )
    }

    @Test
    fun testMapDetailLayout_gatesSplitterOnLowerSection() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must conditionally render splitter when showMap && hasLowerSection (REQ-UI-267)",
            content.contains("if (showMap && hasLowerSection)") || content.contains("if (showMap && hasScrollableContent)")
        )

        assertTrue(
            "MapDetailLayout must retain wrapContentHeight for compact bottom sheets (LiveSegmentSheet)",
            content.contains(".wrapContentHeight()")
        )
    }
}
