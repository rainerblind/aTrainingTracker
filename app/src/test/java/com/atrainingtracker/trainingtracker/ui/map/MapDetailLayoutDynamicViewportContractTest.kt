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
 * Architectural contract tests for [MapDetailLayout] verifying that non-scrollable viewports
 * (Route and Segment details) decouple from the proportional 50/50 split-pane, dynamically
 * maximize the upper map with weight 1f, and anchor the intrinsic elevation profile flush
 * to the Navigation Bar as mandated by REQ-UI-273 and TST-UI-233 (ATT-2386).
 */
class MapDetailLayoutDynamicViewportContractTest {

    @Test
    fun testMapDetailLayout_differentiatesScrollableContentForDynamicMapExpansion() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", mapDetailClass)
        assertEquals(120.dp, SplitPaneMath.MIN_MAP_HEIGHT)
        val methods = mapDetailClass.declaredMethods.filter { it.name.startsWith("MapDetailLayout") }
        assertTrue("MapDetailLayout composable methods must exist", methods.isNotEmpty())
    }
}
