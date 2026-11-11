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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.MapSegment
import com.atrainingtracker.trainingtracker.ui.map.MapStyle
import com.atrainingtracker.trainingtracker.ui.map.MapVisualization
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for multi-tier route map polyline layering:
 * solid climb overlay above route line and dashed segment overlay above climbs (REQ-UI-319, TST-UI-279, ATT-2864).
 */
class RouteClimbSegmentLayeringContractTest {

    /**
     * TST-UI-279.1: MapVisualization and MapStyle z-index hierarchy and styling constants.
     */
    @Test
    fun testMapVisualizationAndStyleConstants_zIndexHierarchy() {
        // Z-Index ordering: Route Base < Route Overlay < Active Route Base < Active Route Overlay < Climb < Segment
        assertTrue(
            "ROUTE_BASE_Z_INDEX (${MapVisualization.ROUTE_BASE_Z_INDEX}) must be strictly less than ROUTE_OVERLAY_Z_INDEX (${MapVisualization.ROUTE_OVERLAY_Z_INDEX})",
            MapVisualization.ROUTE_BASE_Z_INDEX < MapVisualization.ROUTE_OVERLAY_Z_INDEX
        )
        assertTrue(
            "ROUTE_OVERLAY_Z_INDEX (${MapVisualization.ROUTE_OVERLAY_Z_INDEX}) must be strictly less than ROUTE_ACTIVE_BASE_Z_INDEX (${MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX})",
            MapVisualization.ROUTE_OVERLAY_Z_INDEX < MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX
        )
        assertTrue(
            "ROUTE_ACTIVE_BASE_Z_INDEX (${MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX}) must be strictly less than ROUTE_ACTIVE_OVERLAY_Z_INDEX (${MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX})",
            MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX < MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX
        )
        assertTrue(
            "ROUTE_ACTIVE_OVERLAY_Z_INDEX (${MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX}) must be strictly less than CLIMB_Z_INDEX (${MapVisualization.CLIMB_Z_INDEX})",
            MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX < MapVisualization.CLIMB_Z_INDEX
        )
        assertTrue(
            "CLIMB_Z_INDEX (${MapVisualization.CLIMB_Z_INDEX}) must be strictly less than SEGMENT_Z_INDEX (${MapVisualization.SEGMENT_Z_INDEX})",
            MapVisualization.CLIMB_Z_INDEX < MapVisualization.SEGMENT_Z_INDEX
        )

        assertEquals("ROUTE_BASE_Z_INDEX must be 20.0f", 20.0f, MapVisualization.ROUTE_BASE_Z_INDEX, 0.001f)
        assertEquals("ROUTE_OVERLAY_Z_INDEX must be 22.0f", 22.0f, MapVisualization.ROUTE_OVERLAY_Z_INDEX, 0.001f)
        assertEquals("ROUTE_ACTIVE_BASE_Z_INDEX must be 24.0f", 24.0f, MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX, 0.001f)
        assertEquals("ROUTE_ACTIVE_OVERLAY_Z_INDEX must be 26.0f", 26.0f, MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX, 0.001f)
        assertEquals("CLIMB_Z_INDEX must be 28.0f", 28.0f, MapVisualization.CLIMB_Z_INDEX, 0.001f)
        assertEquals("CLIMB_WIDTH must be 10.0f", 10.0f, MapVisualization.CLIMB_WIDTH, 0.001f)
        assertEquals("SEGMENT_Z_INDEX must be 30.0f", 30.0f, MapVisualization.SEGMENT_Z_INDEX, 0.001f)
        assertEquals("SEGMENT_DASH_LENGTH must be 20.0f", 20.0f, MapVisualization.SEGMENT_DASH_LENGTH, 0.001f)
        assertEquals("SEGMENT_GAP_LENGTH must be 15.0f", 15.0f, MapVisualization.SEGMENT_GAP_LENGTH, 0.001f)

        // MapStyle defaults
        val defaultStyle = MapStyle()
        assertEquals("MapStyle routeOverlayZIndex must be 22f", 22f, defaultStyle.routeOverlayZIndex, 0.001f)
        assertEquals("MapStyle routeActiveBaseZIndex must be 24f", 24f, defaultStyle.routeActiveBaseZIndex, 0.001f)
        assertEquals("MapStyle routeActiveOverlayZIndex must be 26f", 26f, defaultStyle.routeActiveOverlayZIndex, 0.001f)
        assertEquals("MapStyle climbZIndex must be 28f", 28f, defaultStyle.climbZIndex, 0.001f)
        assertEquals("MapStyle climbWidth must be 10f", 10f, defaultStyle.climbWidth, 0.001f)
        assertEquals("MapStyle segmentZIndex must be 30f", 30f, defaultStyle.segmentZIndex, 0.001f)
        assertEquals("MapStyle segmentDashLength must be 20f", 20f, defaultStyle.segmentDashLength, 0.001f)
        assertEquals("MapStyle segmentGapLength must be 15f", 15f, defaultStyle.segmentGapLength, 0.001f)
    }

    /**
     * TST-UI-279.2: MapSegment pattern & isDashed contract.
     */
    @Test
    fun testMapSegment_patternAndIsDashedContract() {
        val solidSegment = MapSegment(
            stravaId = 1001L,
            name = "Test Solid Segment",
            bSportType = BSportType.BIKE,
            path = emptyList()
        )
        assertFalse("isDashed must default to false", solidSegment.isDashed)
        assertNull("Solid segment pattern must be null", solidSegment.pattern)

        val dashedSegment = MapSegment(
            stravaId = 1002L,
            name = "Test Dashed Segment",
            bSportType = BSportType.BIKE,
            path = emptyList(),
            isDashed = true
        )
        assertTrue("isDashed must be true", dashedSegment.isDashed)
        val pattern = dashedSegment.pattern
        assertNotNull("Dashed segment pattern must not be null", pattern)
        assertEquals("Dashed segment pattern must contain 2 items (Dash and Gap)", 2, pattern!!.size)

        val dashItem = pattern[0] as? Dash
        assertNotNull("First pattern item must be Dash", dashItem)
        assertEquals("Dash length must match MapVisualization.SEGMENT_DASH_LENGTH", MapVisualization.SEGMENT_DASH_LENGTH, dashItem!!.length, 0.001f)

        val gapItem = pattern[1] as? Gap
        assertNotNull("Second pattern item must be Gap", gapItem)
        assertEquals("Gap length must match MapVisualization.SEGMENT_GAP_LENGTH", MapVisualization.SEGMENT_GAP_LENGTH, gapItem!!.length, 0.001f)
    }

    /**
     * TST-UI-279.3: ClimbHighlightData z-index and MapContentScope climb rendering.
     */
    @Test
    fun testClimbHighlightData_zIndexAndMapContentScopeContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapContentScopeImpl\$ClimbHighlightData")
        assertNotNull("MapContentScopeImpl\$ClimbHighlightData class must exist", clazz)

        val fieldNames = clazz.declaredFields.map { it.name }
        assertTrue("ClimbHighlightData must declare zIndex field", fieldNames.contains("zIndex"))
        assertTrue("ClimbHighlightData must declare width field", fieldNames.contains("width"))
        assertTrue("ClimbHighlightData must declare path field", fieldNames.contains("path"))
        assertTrue("ClimbHighlightData must declare color field", fieldNames.contains("color"))

        assertEquals("CLIMB_Z_INDEX must be 28.0f", 28.0f, MapVisualization.CLIMB_Z_INDEX, 0.001f)
        assertEquals("CLIMB_WIDTH must be 10.0f", 10.0f, MapVisualization.CLIMB_WIDTH, 0.001f)
    }

    /**
     * TST-UI-279.4: MappablePathLayer and RouteOnMapScreen dashed segment transparent gap synergy.
     */
    @Test
    fun testMapLayersAndRouteOnMapScreen_dashedSegmentTransparentGapContract() {
        val mapLayersClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapLayersKt")
        assertNotNull("MapLayersKt class must exist", mapLayersClass)

        val methods = mapLayersClass.declaredMethods
        val xRayMethod = methods.find { it.name.startsWith("XRayPolyline") }
        assertNotNull("XRayPolyline composable must be declared in MapLayersKt", xRayMethod)

        val routeScreenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", routeScreenClass)

        val screenMethods = routeScreenClass.declaredMethods
        val routeScreenMethod = screenMethods.find {
            it.name.startsWith("RouteOnMapScreen") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("RouteOnMapScreen composable must be declared and public", routeScreenMethod)
    }
}
