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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract tests for [MapDetailLayout] verifying the
 * collapsible upper metadata architecture via [CollapsingAppBarNestedScrollConnection]
 * and map squashing prevention (REQ-UI-250 / ATT-2150).
 */
class MapDetailLayoutCollapsingHeaderContractTest {

    private lateinit var mapDetailLayoutFile: File
    private lateinit var connectionFile: File

    @Before
    fun setUp() {
        val userDir = System.getProperty("user.dir") ?: "."
        var projectRoot = File(userDir)
        if (!File(projectRoot, "app").exists() && File(projectRoot, "../app").exists()) {
            projectRoot = File(projectRoot, "..")
        }
        mapDetailLayoutFile =
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        connectionFile =
            File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnection.kt")
    }

    @Test
    fun testCollapsingAppBarNestedScrollConnection_supportsDynamicMaxHeight() {
        assertTrue("CollapsingAppBarNestedScrollConnection.kt must exist", connectionFile.exists())
        val content = connectionFile.readText()

        assertTrue(
            "CollapsingAppBarNestedScrollConnection must declare mutable appBarMaxHeight",
            content.contains("var appBarMaxHeight by mutableIntStateOf") || content.contains("var appBarMaxHeight")
        )
    }

    @Test
    fun testMapDetailLayout_integratesCollapsingAppBarNestedScrollConnection() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must remember CollapsingAppBarNestedScrollConnection",
            content.contains("CollapsingAppBarNestedScrollConnection") &&
                    content.contains("val connection = remember")
        )
        assertTrue(
            "MapDetailLayout root container must attach nestedScroll(connection)",
            content.contains(".nestedScroll(connection)")
        )
    }

    @Test
    fun testMapDetailLayout_appliesOffsetToHeaderAndMetadata() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "Upper metadata container must translate with IntOffset(0, connection.appBarOffset)",
            content.contains("IntOffset(0, connection.appBarOffset)")
        )
    }

    @Test
    fun testMapDetailLayout_dynamicallyAdjustsViewportTopPadding() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must compute currentTopPadding from headerHeightPx and connection.appBarOffset",
            content.contains("headerHeightPx + connection.appBarOffset")
        )
        assertTrue(
            "Viewport Box must apply padding(top = currentTopPaddingDp)",
            content.contains(".padding(top = currentTopPaddingDp)")
        )
    }

    @Test
    fun testMapDetailLayout_guaranteesMinimumMapHeight() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must account for SplitPaneMath.MIN_MAP_HEIGHT when bounding upper metadata",
            content.contains("SplitPaneMath.MIN_MAP_HEIGHT")
        )
        assertTrue(
            "Upper metadata container must specify heightIn and verticalScroll",
            content.contains(".heightIn(max = maxMetadataHeightDp)") &&
                    content.contains(".verticalScroll(rememberScrollState())")
        )
    }

    @Test
    fun testMapDetailLayout_computesDynamicStatusBarClearance() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must compute statusBarHeightPx based on useStatusBarsPadding",
            content.contains("val statusBarHeightPx = if (useStatusBarsPadding)") &&
                    content.contains("WindowInsets.statusBars.getTop(density)")
        )
        assertTrue(
            "MapDetailLayout must calculate dynamic floatingControlsTopOffset",
            content.contains("floatingControlsTopOffset")
        )
    }

    @Test
    fun testMapDetailLayout_appliesTopOffsetToFloatingControlsContainer() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "mapBox must enclose overlay and Share button in a container with controlsTopOffset padding",
            content.contains(".padding(top = controlsTopOffsetDp)")
        )
        assertTrue(
            "mapBox must pass floatingControlsTopOffsetDp to its invocations",
            content.contains("floatingControlsTopOffsetDp")
        )
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
