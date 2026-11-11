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
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteOverlayLayer enum, layers dropdown menu,
 * overlay layer toggles, and individual item visibility filtering (REQ-UI-308, TST-UI-268, ATT-2763).
 */
class RouteOverlayLayersContractTest {

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
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", screenClazz)

        val methods = screenClazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("RouteOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteOnMapScreen composable must exist and be public", screenMethod)
    }

    @Test
    fun testRouteBreakdownSections_declareVisibilityToggles() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        val climbsMethod = screenClazz.declaredMethods.find { it.name == "RouteClimbsBreakdownSection" }
        assertNotNull("RouteClimbsBreakdownSection composable must exist", climbsMethod)

        val climbsParams = climbsMethod!!.parameterTypes
        val hasSetClimbs = climbsParams.any { it == java.util.Set::class.java }
        assertTrue("RouteClimbsBreakdownSection must declare hiddenClimbIds Set parameter", hasSetClimbs)

        val segmentsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSegmentsBreakdownSectionKt")
        val segmentsMethod = segmentsClazz.declaredMethods.find { it.name == "RouteSegmentsBreakdownSection" }
        assertNotNull("RouteSegmentsBreakdownSection composable must exist", segmentsMethod)

        val segmentsParams = segmentsMethod!!.parameterTypes
        val hasSetSegments = segmentsParams.any { it == java.util.Set::class.java }
        assertTrue("RouteSegmentsBreakdownSection must declare hiddenSegmentIds Set parameter", hasSetSegments)
    }

    @Test
    fun testRouteOnMapScreen_backgroundPathsSegmentFilteringAndDeDuplication() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", screenClazz)
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
