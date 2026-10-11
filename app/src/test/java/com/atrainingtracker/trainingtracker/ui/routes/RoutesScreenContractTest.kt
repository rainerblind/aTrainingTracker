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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0>.
 */

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.MapVisualization
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.toMapRoute
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying that [RoutesScreen] and [RoutesViewModel]
 * decouple route preview polyline styling from hardcoded active navigation (REQ-UI-347 / TST-UI-307 / ATT-3172).
 */
class RoutesScreenContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val routesScreenFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt"
        )
    }

    private val samplePath = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
        PathPoint(distance = 100.0, latLng = LatLng(48.001, 11.001), altitude = 505.0)
    )

    private fun createRouteWithPath(id: Long, isSelected: Boolean): RouteWithPath {
        val summary = RouteSummary(
            id = id,
            externalId = "ext_$id",
            name = "Test Route $id",
            description = "Test description",
            isSelected = isSelected,
            distance = 5000.0,
            elevationGain = 200.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        return RouteWithPath(summary = summary, path = samplePath)
    }

    @Test
    fun testRoutesScreen_doesNotHardcodeActiveNavigation() {
        assertTrue("RoutesScreen.kt must exist", routesScreenFile.exists())
        val content = routesScreenFile.readText()

        // 1. Must NOT unconditionally hardcode isActiveNavigation = true
        assertFalse(
            "RoutesScreen.kt must not hardcode isActiveNavigation = true (REQ-UI-347)",
            content.contains("toMapRoute(isActiveNavigation = true)")
        )

        // 2. Must observe activeNavigatedRouteId from RoutesViewModel
        assertTrue(
            "RoutesScreen.kt must collect activeNavigatedRouteId",
            content.contains("activeNavigatedRouteId")
        )

        // 3. Must dynamically evaluate whether route is actively navigated
        assertTrue(
            "RoutesScreen.kt must dynamically evaluate isActivelyNavigating",
            content.contains("isActivelyNavigating") || content.contains("activeNavigatedRouteId")
        )
    }

    @Test
    fun testRoutesViewModel_exposesActiveNavigatedRouteId() {
        val clazz = RoutesViewModel::class.java
        val method = clazz.methods.find { it.name == "getActiveNavigatedRouteId" }
        assertNotNull(
            "RoutesViewModel must expose getActiveNavigatedRouteId StateFlow getter (REQ-UI-347)",
            method
        )
        assertTrue(
            "activeNavigatedRouteId must return a StateFlow",
            StateFlow::class.java.isAssignableFrom(method!!.returnType)
        )
    }

    @Test
    fun testRoutePreview_whenPassiveUnselected_usesUnselectedDashedStyling() {
        val route = createRouteWithPath(id = 101L, isSelected = false)
        val activeNavigatedRouteId: Long? = null

        val isActivelyNavigating = route.summary.id == activeNavigatedRouteId
        val mapRoute = route.toMapRoute(isActiveNavigation = isActivelyNavigating)

        assertFalse("MapRoute must not be active navigation", mapRoute.isActiveNavigation)
        assertFalse("MapRoute must be unselected", mapRoute.isSelected)
        assertEquals(TTColor.RouteUnselected, mapRoute.color)
        assertEquals(MapVisualization.ROUTE_UNSELECTED_WIDTH, mapRoute.width, 0.001f)
        assertEquals(6f, mapRoute.width, 0.001f)
        assertNotNull("Unselected route pattern must be dashed", mapRoute.pattern)
    }

    @Test
    fun testRoutePreview_whenPassiveSelected_usesSelectedDashedStyling() {
        val route = createRouteWithPath(id = 101L, isSelected = true)
        val activeNavigatedRouteId: Long? = null

        val isActivelyNavigating = route.summary.id == activeNavigatedRouteId
        val mapRoute = route.toMapRoute(isActiveNavigation = isActivelyNavigating)

        assertFalse("MapRoute must not be active navigation", mapRoute.isActiveNavigation)
        assertTrue("MapRoute must be selected", mapRoute.isSelected)
        assertEquals(TTColor.RouteSelected, mapRoute.color)
        assertEquals(MapVisualization.ROUTE_WIDTH, mapRoute.width, 0.001f)
        assertEquals(10f, mapRoute.width, 0.001f)
        assertNotNull("Selected route pattern must be dashed", mapRoute.pattern)
    }

    @Test
    fun testRoutePreview_whenActivelyNavigating_usesProminentSolidStyling() {
        val route = createRouteWithPath(id = 101L, isSelected = true)
        val activeNavigatedRouteId: Long? = 101L

        val isActivelyNavigating = route.summary.id == activeNavigatedRouteId
        val mapRoute = route.toMapRoute(isActiveNavigation = isActivelyNavigating)

        assertTrue("MapRoute must be active navigation", mapRoute.isActiveNavigation)
        assertEquals(TTColor.RouteActiveNavigation, mapRoute.color)
        assertEquals(MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH, mapRoute.width, 0.001f)
        assertEquals(26f, mapRoute.width, 0.001f)
        assertNull("Actively navigated route pattern must be solid null (REQ-MAP-040)", mapRoute.pattern)
    }

    @Test
    fun testRoutePreview_dynamicSelectionToggle_updatesPolylineStyling() {
        // Initially unselected
        val unselectedRoute = createRouteWithPath(id = 202L, isSelected = false)
        val unselectedMapRoute = unselectedRoute.toMapRoute(isActiveNavigation = false)
        assertEquals(TTColor.RouteUnselected, unselectedMapRoute.color)
        assertEquals(6f, unselectedMapRoute.width, 0.001f)

        // Athlete toggles switch -> route becomes selected
        val selectedRoute = createRouteWithPath(id = 202L, isSelected = true)
        val selectedMapRoute = selectedRoute.toMapRoute(isActiveNavigation = false)
        assertEquals(TTColor.RouteSelected, selectedMapRoute.color)
        assertEquals(10f, selectedMapRoute.width, 0.001f)

        // Verify difference
        assertFalse(unselectedMapRoute.color == selectedMapRoute.color)
        assertTrue(selectedMapRoute.width > unselectedMapRoute.width)
    }
}
