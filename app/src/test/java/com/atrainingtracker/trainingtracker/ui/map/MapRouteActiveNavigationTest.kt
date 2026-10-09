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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying prominent high-contrast rendering for actively navigated routes
 * and multi-layer X-Ray synergy invariants (REQ-MAP-023 / TST-MAP-025 / ATT-1841).
 */
class MapRouteActiveNavigationTest {

    private val samplePath = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
        PathPoint(distance = 100.0, latLng = LatLng(48.001, 11.001), altitude = 505.0)
    )

    private fun createRoute(isSelected: Boolean, isActiveNavigation: Boolean): MapRoute {
        return MapRoute(
            id = 1L,
            name = "Test Route",
            isSelected = isSelected,
            bSportType = BSportType.BIKE,
            path = samplePath,
            isActiveNavigation = isActiveNavigation
        )
    }

    @Test
    fun testMapRoute_whenActiveNavigation_usesProminentStyling() {
        val activeRoute = createRoute(isSelected = true, isActiveNavigation = true)

        assertTrue(activeRoute.isActiveNavigation)
        assertEquals(TTColor.RouteActiveNavigation, activeRoute.color)
        assertEquals(Color(0xFF1E88E5), activeRoute.color)
        assertEquals(MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH, activeRoute.width, 0.001f)
        assertEquals(16f, activeRoute.width, 0.001f)
        assertEquals(MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX, activeRoute.zIndex, 0.001f)
        assertEquals(25.0f, activeRoute.zIndex, 0.001f)
        assertEquals(MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX, activeRoute.overlayZIndex, 0.001f)
        assertEquals(45.0f, activeRoute.overlayZIndex, 0.001f)
        assertEquals(TTColor.RouteActiveNavigationOverlay, activeRoute.overlayColor)
        assertEquals(Color(0xFF0D47A1), activeRoute.overlayColor)
        assertEquals(MapVisualization.ROUTE_ACTIVE_OVERLAY_WIDTH, activeRoute.overlayWidth, 0.001f)
        assertEquals(8f, activeRoute.overlayWidth, 0.001f)

        assertNotNull(activeRoute.pattern)
        val pattern = activeRoute.pattern
        assertEquals(2, pattern.size)
        assertTrue(pattern[0] is Dash)
        assertTrue(pattern[1] is Gap)
        assertEquals(MapVisualization.ROUTE_ACTIVE_DASH_LENGTH, (pattern[0] as Dash).length, 0.001f)
        assertEquals(MapVisualization.ROUTE_ACTIVE_GAP_LENGTH, (pattern[1] as Gap).length, 0.001f)
    }

    @Test
    fun testMapRoute_whenSelectedPassive_usesStandardStyling() {
        val passiveRoute = createRoute(isSelected = true, isActiveNavigation = false)

        assertFalse(passiveRoute.isActiveNavigation)
        assertEquals(TTColor.RouteSelected, passiveRoute.color)
        assertEquals(Color(0xFF1565C0), passiveRoute.color)
        assertEquals(MapVisualization.ROUTE_WIDTH, passiveRoute.width, 0.001f)
        assertEquals(10f, passiveRoute.width, 0.001f)
        assertEquals(MapVisualization.ROUTE_BASE_Z_INDEX, passiveRoute.zIndex, 0.001f)
        assertEquals(20.0f, passiveRoute.zIndex, 0.001f)
        assertEquals(MapVisualization.ROUTE_OVERLAY_Z_INDEX, passiveRoute.overlayZIndex, 0.001f)
        assertEquals(40.0f, passiveRoute.overlayZIndex, 0.001f)
        assertEquals(passiveRoute.color, passiveRoute.overlayColor)
        assertEquals(passiveRoute.width, passiveRoute.overlayWidth, 0.001f)
    }

    @Test
    fun testMapRoute_whenUnselected_usesSubordinateStyling() {
        val unselectedRoute = createRoute(isSelected = false, isActiveNavigation = false)

        assertFalse(unselectedRoute.isActiveNavigation)
        assertEquals(TTColor.RouteUnselected, unselectedRoute.color)
        assertEquals(Color(0xFF90CAF9), unselectedRoute.color)
        assertEquals(MapVisualization.ROUTE_UNSELECTED_WIDTH, unselectedRoute.width, 0.001f)
        assertEquals(6f, unselectedRoute.width, 0.001f)
        assertEquals(MapVisualization.ROUTE_UNSELECTED_Z_INDEX, unselectedRoute.zIndex, 0.001f)
        assertEquals(5.0f, unselectedRoute.zIndex, 0.001f)
    }

    @Test
    fun testXRayPolylineHierarchy_preservesSegmentInterleavingInvariants() {
        val passiveRoute = createRoute(isSelected = true, isActiveNavigation = false)
        val activeRoute = createRoute(isSelected = true, isActiveNavigation = true)
        val segmentZIndex = MapVisualization.SEGMENT_Z_INDEX
        val userLocationZIndex = MapVisualization.USER_LOCATION_Z_INDEX

        // Layer 1: Passive base (20f) < Active base (25f) < Strava Live Segment (30f)
        assertTrue(
            "Passive route base must be below active route base",
            passiveRoute.zIndex < activeRoute.zIndex
        )
        assertTrue(
            "Active route base must be below Strava Live Segment",
            activeRoute.zIndex < segmentZIndex
        )

        // Layer 2: Strava Live Segment (30f) < Passive overlay (40f) < Active overlay (45f)
        assertTrue(
            "Strava segment must be below passive overlay",
            segmentZIndex < passiveRoute.overlayZIndex
        )
        assertTrue(
            "Passive overlay must be below active overlay",
            passiveRoute.overlayZIndex < activeRoute.overlayZIndex
        )

        // Layer 3: GPS user location puck (100f) above all layers
        assertTrue(
            "User location must be above active route overlay",
            activeRoute.overlayZIndex < userLocationZIndex
        )
    }

    @Test
    fun testToMapRoute_propagatesIsActiveNavigationFlag() {
        val summary = RouteSummary(
            id = 42L,
            externalId = "ext_42",
            name = "Alps Crossing",
            description = "Scenic Alps trail",
            isSelected = true,
            distance = 10000.0,
            elevationGain = 500.0,
            bSportType = BSportType.BIKE,
            source = com.atrainingtracker.trainingtracker.database.RouteSource.LOCAL_GPX
        )
        val routeWithPath = RouteWithPath(summary = summary, path = samplePath)

        val passiveMapRoute = routeWithPath.toMapRoute(isActiveNavigation = false)
        assertFalse(passiveMapRoute.isActiveNavigation)
        assertEquals(10f, passiveMapRoute.width, 0.001f)

        val activeMapRoute = routeWithPath.toMapRoute(isActiveNavigation = true)
        assertTrue(activeMapRoute.isActiveNavigation)
        assertEquals(16f, activeMapRoute.width, 0.001f)
        assertEquals(TTColor.RouteActiveNavigation, activeMapRoute.color)
    }
}
