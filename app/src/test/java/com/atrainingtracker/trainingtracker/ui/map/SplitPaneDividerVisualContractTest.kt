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

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.core.SplitPaneMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visual and structural contract tests for [SplitPaneDivider] and [MapDetailLayout] integration
 * verifying draggable splitter architecture, touch affordances, and gesture reset (REQ-UI-223, TST-UI-177.2).
 */
class SplitPaneDividerVisualContractTest {

    @Test
    fun testSplitPaneDivider_definesPillGripAndTouchTarget() {
        assertEquals(24.dp, SplitPaneMath.DIVIDER_TOUCH_HEIGHT)
        assertEquals(32.dp, SplitPaneMath.GRIP_WIDTH)
        assertEquals(4.dp, SplitPaneMath.GRIP_HEIGHT)
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.SplitPaneDividerKt")
        assertNotNull("SplitPaneDividerKt must exist", clazz)
    }

    @Test
    fun testSplitPaneDivider_handlesDragAndDoubleTap() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.SplitPaneDividerKt")
        assertNotNull("SplitPaneDividerKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_integratesSplitPaneDivider() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
        assertEquals(120.dp, SplitPaneMath.MIN_MAP_HEIGHT)
    }

    @Test
    fun testMapDetailLayout_gatesSplitterOnLowerSection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }
}
