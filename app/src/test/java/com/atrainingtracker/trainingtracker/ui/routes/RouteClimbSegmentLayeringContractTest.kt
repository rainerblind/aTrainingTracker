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
import com.atrainingtracker.trainingtracker.ui.map.MapContentScope
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
import java.io.File

/**
 * Architectural contract test for multi-tier route map polyline layering:
 * solid climb overlay above route line and dashed segment overlay above climbs (REQ-UI-319, TST-UI-279, ATT-2864).
 */
class RouteClimbSegmentLayeringContractTest {

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

    private val mapLayersFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt")
    }

    private val mapContentScopeFile: File by lazy {
        val file1 = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt")
        if (file1.exists()) file1 else File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt")
    }

    /**
     * TST-UI-279.1: MapVisualization and MapStyle z-index hierarchy and styling constants.
     */
    @Test
    fun testMapVisualizationAndStyleConstants_zIndexHierarchy() {
        // Z-Index ordering: Route Base < Climb < Segment
        assertTrue(
            "CLIMB_Z_INDEX (${MapVisualization.CLIMB_Z_INDEX}) must be strictly greater than ROUTE_BASE_Z_INDEX (${MapVisualization.ROUTE_BASE_Z_INDEX})",
            MapVisualization.CLIMB_Z_INDEX > MapVisualization.ROUTE_BASE_Z_INDEX
        )
        assertTrue(
            "SEGMENT_Z_INDEX (${MapVisualization.SEGMENT_Z_INDEX}) must be strictly greater than CLIMB_Z_INDEX (${MapVisualization.CLIMB_Z_INDEX})",
            MapVisualization.SEGMENT_Z_INDEX > MapVisualization.CLIMB_Z_INDEX
        )
        assertEquals("CLIMB_Z_INDEX must be 28.0f", 28.0f, MapVisualization.CLIMB_Z_INDEX, 0.001f)
        assertEquals("CLIMB_WIDTH must be 10.0f", 10.0f, MapVisualization.CLIMB_WIDTH, 0.001f)
        assertEquals("SEGMENT_DASH_LENGTH must be 20.0f", 20.0f, MapVisualization.SEGMENT_DASH_LENGTH, 0.001f)
        assertEquals("SEGMENT_GAP_LENGTH must be 15.0f", 15.0f, MapVisualization.SEGMENT_GAP_LENGTH, 0.001f)

        // MapStyle defaults
        val defaultStyle = MapStyle()
        assertEquals("MapStyle climbZIndex must be 28f", 28f, defaultStyle.climbZIndex, 0.001f)
        assertEquals("MapStyle climbWidth must be 10f", 10f, defaultStyle.climbWidth, 0.001f)
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
        assertTrue("MapContentScope.kt must exist", mapContentScopeFile.exists())
        val content = mapContentScopeFile.readText()

        assertTrue(
            "ClimbHighlightData default zIndex must be MapVisualization.CLIMB_Z_INDEX",
            content.contains("val zIndex: Float = MapVisualization.CLIMB_Z_INDEX")
        )
        assertTrue(
            "ClimbHighlightData default width must be MapVisualization.CLIMB_WIDTH",
            content.contains("val width: Float = MapVisualization.CLIMB_WIDTH")
        )
        assertTrue(
            "Climb highlights polyline must use jointType = JointType.ROUND",
            content.contains("jointType = JointType.ROUND")
        )
    }

    /**
     * TST-UI-279.4: MappablePathLayer and RouteOnMapScreen dashed segment transparent gap synergy.
     */
    @Test
    fun testMapLayersAndRouteOnMapScreen_dashedSegmentTransparentGapContract() {
        assertTrue("MapLayers.kt must exist", mapLayersFile.exists())
        val mapLayersContent = mapLayersFile.readText()

        assertTrue(
            "XRayPolyline must declare hasSolidBase parameter defaulting to true",
            mapLayersContent.contains("hasSolidBase: Boolean = true")
        )
        assertTrue(
            "XRayPolyline must guard solid base polyline rendering with if (hasSolidBase)",
            mapLayersContent.contains("if (hasSolidBase)")
        )
        assertTrue(
            "MappablePathLayer must compute isDashedSegment for MapSegment",
            mapLayersContent.contains("val isDashedSegment = path is MapSegment && path.isDashed")
        )
        assertTrue(
            "MappablePathLayer must pass hasSolidBase = !isDashedSegment to XRayPolyline",
            mapLayersContent.contains("hasSolidBase = !isDashedSegment")
        )
        assertTrue(
            "MappablePathLayer must preserve pattern for dashed segments regardless of alpha",
            mapLayersContent.contains("val pattern = if (isDashedSegment || alpha >= 1.0f) path.pattern else null")
        )

        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val routeScreenContent = routeOnMapScreenFile.readText()

        assertTrue(
            "RouteOnMapScreen must configure matched segments with isDashed = true",
            routeScreenContent.contains("isDashed = true")
        )
        assertTrue(
            "RouteOnMapScreen must copy background segments with isDashed = true",
            routeScreenContent.contains("path.copy(isDashed = true)")
        )
    }
}
