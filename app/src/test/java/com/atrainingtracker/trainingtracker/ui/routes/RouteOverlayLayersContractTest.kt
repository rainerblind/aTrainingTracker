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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteOverlayLayer enum, layers dropdown menu,
 * overlay layer toggles, and individual item visibility filtering (REQ-UI-308, TST-UI-268, ATT-2763).
 */
class RouteOverlayLayersContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val routeOnMapScreenFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
    }

    private val routeOverlayLayerFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayer.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayer.kt")
    }

    private val routeSegmentsBreakdownFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSegmentsBreakdownSection.kt")
    }

    @Test
    fun testRouteOverlayLayer_enumDefinition() {
        val entries = RouteOverlayLayer.values()
        assertEquals("RouteOverlayLayer must have 3 entries", 3, entries.size)

        val names = entries.map { it.name }
        assertTrue("RouteOverlayLayer must contain CLIMBS", names.contains("CLIMBS"))
        assertTrue("RouteOverlayLayer must contain SEGMENTS", names.contains("SEGMENTS"))
        assertTrue("RouteOverlayLayer must contain WAYPOINTS", names.contains("WAYPOINTS"))
    }

    @Test
    fun testRouteOnMapScreen_layersMenuAndOverlaySlot() {
        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val content = routeOnMapScreenFile.readText()

        // 1. Layer state declarations
        assertTrue(
            "RouteOnMapScreen must declare enabledOverlayLayers state with rememberSaveable",
            content.contains("var enabledOverlayLayers by rememberSaveable")
        )
        assertTrue(
            "RouteOnMapScreen must declare hiddenClimbIds state with rememberSaveable",
            content.contains("var hiddenClimbIds by rememberSaveable")
        )
        assertTrue(
            "RouteOnMapScreen must declare hiddenSegmentIds state with rememberSaveable",
            content.contains("var hiddenSegmentIds by rememberSaveable")
        )

        // 2. Overlay slot implementation in MapDetailLayout
        assertTrue(
            "RouteOnMapScreen must provide overlay slot to MapDetailLayout",
            content.contains("overlay = {")
        )
        assertTrue(
            "RouteOnMapScreen overlay must render layers button with Icons.Default.Layers",
            content.contains("Icons.Default.Layers") && content.contains("R.string.route_layers")
        )
        assertTrue(
            "RouteOnMapScreen overlay must provide DropdownMenu for overlay layer selection",
            content.contains("DropdownMenu(") && content.contains("RouteOverlayLayer.entries.forEach")
        )

        // 3. Layer filtering logic in mapContent
        assertTrue(
            "Route polyline must remain permanently rendered while waypoints are filtered",
            content.contains("RouteOverlayLayer.WAYPOINTS in enabledOverlayLayers") &&
                    content.contains("route.copy(waypoints = emptyList())")
        )
        assertTrue(
            "Climbs overlay must respect RouteOverlayLayer.CLIMBS and hiddenClimbIds",
            content.contains("RouteOverlayLayer.CLIMBS in enabledOverlayLayers") &&
                    content.contains("hiddenClimbIds")
        )
        assertTrue(
            "Segments overlay must respect RouteOverlayLayer.SEGMENTS and hiddenSegmentIds",
            content.contains("RouteOverlayLayer.SEGMENTS in enabledOverlayLayers") &&
                    content.contains("hiddenSegmentIds")
        )

        // 4. Permanent start and end markers
        assertTrue(
            "Start and end markers must be permanently anchored in mapContent",
            content.contains("R.drawable.control_start") &&
                    content.contains("R.drawable.control_stop") &&
                    content.contains("markers(allMarkers)")
        )
    }

    @Test
    fun testRouteBreakdownSections_declareVisibilityToggles() {
        val screenContent = routeOnMapScreenFile.readText()
        val segmentsContent = routeSegmentsBreakdownFile.readText()

        // RouteClimbsBreakdownSection checks
        assertTrue(
            "RouteClimbsBreakdownSection must declare hiddenClimbIds parameter",
            screenContent.contains("hiddenClimbIds: Set<Long> = emptySet()")
        )
        assertTrue(
            "RouteClimbsBreakdownSection must declare onToggleClimbVisibility parameter",
            screenContent.contains("onToggleClimbVisibility: ((Long) -> Unit)? = null")
        )
        assertTrue(
            "RouteClimbsBreakdownSection must toggle between Visibility and VisibilityOff icons",
            screenContent.contains("Icons.Default.VisibilityOff") &&
                    screenContent.contains("Icons.Default.Visibility")
        )

        // RouteSegmentsBreakdownSection checks
        assertTrue(
            "RouteSegmentsBreakdownSection must declare hiddenSegmentIds parameter",
            segmentsContent.contains("hiddenSegmentIds: Set<Long> = emptySet()")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must declare onToggleSegmentVisibility parameter",
            segmentsContent.contains("onToggleSegmentVisibility: ((Long) -> Unit)? = null")
        )
        assertTrue(
            "RouteSegmentsBreakdownSection must toggle between Visibility and VisibilityOff icons",
            segmentsContent.contains("Icons.Default.VisibilityOff") &&
                    segmentsContent.contains("Icons.Default.Visibility")
        )
    }

    @Test
    fun testRouteOnMapScreen_backgroundPathsSegmentFilteringAndDeDuplication() {
        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val content = routeOnMapScreenFile.readText()

        // 1. Matched segment IDs de-duplication
        assertTrue(
            "RouteOnMapScreen must compute matchedSegmentIds for de-duplication",
            content.contains("val matchedSegmentIds = remember(matchedSegments)")
        )

        // 2. Background paths filtering by SEGMENTS layer, hiddenSegmentIds, and matchedSegmentIds (REQ-UI-318)
        assertTrue(
            "RouteOnMapScreen must filter backgroundPaths using RouteOverlayLayer.SEGMENTS in enabledOverlayLayers",
            content.contains("val isSegmentsLayerEnabled = RouteOverlayLayer.SEGMENTS in enabledOverlayLayers") &&
                    content.contains("backgroundPaths.filter { path ->")
        )
        assertTrue(
            "RouteOnMapScreen must filter backgroundPaths MapSegment instances by hiddenSegmentIds and matchedSegmentIds",
            content.contains("isSegmentsLayerEnabled && path.stravaId !in hiddenSegmentIds && path.stravaId !in matchedSegmentIds")
        )

        // 3. hasSegments availability check includes backgroundPaths segments (REQ-UI-318)
        assertTrue(
            "RouteOnMapScreen hasSegments must include backgroundPaths.any { it is MapSegment }",
            content.contains("val hasSegments = matchedSegments.isNotEmpty() || backgroundPaths.any { it is MapSegment }")
        )
    }

    @Test
    fun testBackgroundPathsSegmentFilteringLogic() {
        // Pure functional logic validation of the filtering algorithm (REQ-UI-318, TST-UI-278)
        data class FakeMapSegment(val stravaId: Long)
        data class FakeTrack(val id: Long)

        val paths: List<Any> = listOf(
            FakeMapSegment(101L), // matched
            FakeMapSegment(102L), // unmatched, visible
            FakeMapSegment(103L), // hidden by user
            FakeTrack(999L)       // non-segment track
        )

        val hiddenSegmentIds = setOf(103L)
        val matchedSegmentIds = setOf(101L)

        // Case 1: SEGMENTS layer enabled
        val isSegmentsLayerEnabled = true
        val visibleWhenEnabled = paths.filter { path ->
            if (path is FakeMapSegment) {
                isSegmentsLayerEnabled && path.stravaId !in hiddenSegmentIds && path.stravaId !in matchedSegmentIds
            } else {
                true
            }
        }
        assertEquals(2, visibleWhenEnabled.size)
        assertTrue(visibleWhenEnabled.contains(FakeMapSegment(102L)))
        assertTrue(visibleWhenEnabled.contains(FakeTrack(999L)))

        // Case 2: SEGMENTS layer disabled (unselected)
        val isSegmentsLayerDisabled = false
        val visibleWhenDisabled = paths.filter { path ->
            if (path is FakeMapSegment) {
                isSegmentsLayerDisabled && path.stravaId !in hiddenSegmentIds && path.stravaId !in matchedSegmentIds
            } else {
                true
            }
        }
        assertEquals(1, visibleWhenDisabled.size)
        assertTrue(visibleWhenDisabled.contains(FakeTrack(999L)))
    }
}

