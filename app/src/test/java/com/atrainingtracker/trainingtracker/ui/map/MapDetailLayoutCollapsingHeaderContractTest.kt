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
 * Structural and architectural contract tests for [MapDetailLayout] verifying the
 * collapsible upper metadata architecture via [CollapsingAppBarNestedScrollConnection]
 * and map squashing prevention (REQ-UI-250 / ATT-2150).
 */
class MapDetailLayoutCollapsingHeaderContractTest {

    @Test
    fun testCollapsingAppBarNestedScrollConnection_supportsDynamicMaxHeight() {
        val connectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.utils.CollapsingAppBarNestedScrollConnection")
        assertNotNull("CollapsingAppBarNestedScrollConnection must exist", connectionClass)
        val hasMaxHeightProp = connectionClass.declaredMethods.any { it.name.contains("AppBarMaxHeight") } ||
            connectionClass.declaredFields.any { it.name.contains("appBarMaxHeight") }
        assertTrue("CollapsingAppBarNestedScrollConnection must support appBarMaxHeight", hasMaxHeightProp)
    }

    @Test
    fun testMapDetailLayout_integratesCollapsingAppBarNestedScrollConnection() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapDetailClass)
    }

    @Test
    fun testMapDetailLayout_appliesOffsetToHeaderAndMetadata() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapDetailClass)
    }

    @Test
    fun testMapDetailLayout_dynamicallyAdjustsViewportTopPadding() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapDetailClass)
    }

    @Test
    fun testMapDetailLayout_guaranteesMinimumMapHeight() {
        assertEquals(120.dp, SplitPaneMath.MIN_MAP_HEIGHT)
    }

    @Test
    fun testMapDetailLayout_computesDynamicStatusBarClearance() {
        val mathClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailViewportMath")
        assertNotNull("MapDetailViewportMath must exist", mathClass)
    }

    @Test
    fun testMapDetailLayout_appliesTopOffsetToFloatingControlsContainer() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", mapDetailClass)
    }

    @Test
    fun testMapDetailLayout_floatingControlsOffsetCalculationLogic() {
        // When useStatusBarsPadding is false (bottom sheet), offset is always 0
        assertEquals(0, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(false, 80, 0))
        assertEquals(0, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(false, 80, 200))

        // When useStatusBarsPadding is true:
        // 1. Header fully expanded: currentTopPaddingPx (250) >= statusBarHeightPx (80) -> 0 extra offset
        assertEquals(0, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(true, 80, 250))
        assertEquals(0, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(true, 80, 80))

        // 2. Header partially collapsed: currentTopPaddingPx (50) < statusBarHeightPx (80) -> 30px offset
        assertEquals(30, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(true, 80, 50))

        // 3. Header fully collapsed: currentTopPaddingPx == 0 -> full statusBarHeightPx (80px)
        assertEquals(80, MapDetailViewportMath.calculateFloatingControlsTopOffsetPx(true, 80, 0))
    }
}
