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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for [RouteDetailSheet] and [calculateRouteBounds]
 * (REQ-UI-316, TST-UI-276, ATT-2861).
 */
class RouteDetailSheetContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val routeDetailSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt")
    }

    @Test
    fun testRouteDetailSheet_composablesExistAndArePublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheetKt")
        assertNotNull("RouteDetailSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("RouteDetailSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteDetailSheet composable function must exist and be public", sheetMethod)
    }

    @Test
    fun testRouteDetailSheet_structuralTokensAndContracts() {
        assertTrue("RouteDetailSheet.kt must exist", routeDetailSheetFile.exists())
        val content = routeDetailSheetFile.readText()

        // 1. Material 3 ModalBottomSheet with BottomSheetDesign shape tokens
        assertTrue("Must use ModalBottomSheet", content.contains("ModalBottomSheet"))
        assertTrue("Must use BottomSheetDesign.SheetShape", content.contains("BottomSheetDesign.SheetShape"))

        // 2. Direct reuse of RouteOnMapScreen (REQ-UI-315.2 / REQ-UI-316)
        assertTrue("Must host RouteOnMapScreen directly for visual/functional parity with route screen", content.contains("RouteOnMapScreen("))
        assertTrue("Must pass useStatusBarsPadding = false", content.contains("useStatusBarsPadding = false"))
        assertTrue("Must map route via toMapRoute", content.contains("toMapRoute("))

        // 3. Overlay dismiss button
        assertTrue("Must include close icon button for non-destructive dismissal", content.contains("Icons.Default.Close"))

        // 4. Verify obsolete card composables are eliminated (Rule 23 - no duplicated code)
        assertFalse("Obsolete RouteDetailMetricsCard must not exist", content.contains("RouteDetailMetricsCard"))
        assertFalse("Obsolete RouteDetailMapCard must not exist", content.contains("RouteDetailMapCard"))
        assertFalse("Obsolete RouteDetailElevationProfileCard must not exist", content.contains("RouteDetailElevationProfileCard"))
    }

    @Test
    fun testCalculateRouteBounds_validRoute_computesCorrectBounds() {
        val p1 = PathPoint(0.0, LatLng(48.1351, 11.5820), 500.0)
        val p2 = PathPoint(1000.0, LatLng(48.1400, 11.5900), 580.0)
        val summary = RouteSummary(
            id = 123L,
            externalId = "ext-1",
            name = "Test Route",
            description = "",
            isSelected = false,
            distance = 1000.0,
            elevationGain = 80.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX
        )
        val route = RouteWithPath(summary = summary, path = listOf(p1, p2))

        val bounds = calculateRouteBounds(route)
        assertNotNull("Bounds must be non-null for valid points", bounds)
        assertEquals(48.1351, bounds!!.southwest.latitude, 0.0001)
        assertEquals(11.5820, bounds.southwest.longitude, 0.0001)
        assertEquals(48.1400, bounds.northeast.latitude, 0.0001)
        assertEquals(11.5900, bounds.northeast.longitude, 0.0001)
    }

    @Test
    fun testCalculateRouteBounds_emptyPath_fallbackToSummary() {
        val summary = RouteSummary(
            id = 456L,
            externalId = "ext-2",
            name = "Summary Extrema Route",
            description = "",
            isSelected = false,
            distance = 5000.0,
            elevationGain = 120.0,
            bSportType = BSportType.BIKE,
            source = RouteSource.LOCAL_GPX,
            minLat = 47.5,
            maxLat = 47.6,
            minLng = 10.1,
            maxLng = 10.2
        )
        val route = RouteWithPath(summary = summary, path = emptyList())

        val bounds = calculateRouteBounds(route)
        assertNotNull("Bounds must fall back to summary coordinates", bounds)
        assertEquals(47.5, bounds!!.southwest.latitude, 0.0001)
        assertEquals(47.6, bounds.northeast.latitude, 0.0001)
        assertEquals(10.1, bounds.southwest.longitude, 0.0001)
        assertEquals(10.2, bounds.northeast.longitude, 0.0001)
    }

    @Test
    fun testCalculateRouteBounds_singlePoint_expandsBounds() {
        val p1 = PathPoint(0.0, LatLng(48.0, 11.0), 450.0)
        val summary = RouteSummary(
            id = 789L,
            externalId = "ext-3",
            name = "Single Point Route",
            description = "",
            isSelected = false,
            distance = 0.0,
            elevationGain = 0.0,
            bSportType = BSportType.RUN,
            source = RouteSource.LOCAL_GPX
        )
        val route = RouteWithPath(summary = summary, path = listOf(p1))

        val bounds = calculateRouteBounds(route)
        assertNotNull("Bounds must be non-null for single point", bounds)
        assertTrue(bounds!!.southwest.latitude < bounds.northeast.latitude)
        assertTrue(bounds.southwest.longitude < bounds.northeast.longitude)
    }

    @Test
    fun testCalculateRouteBounds_emptyEverything_returnsNull() {
        val summary = RouteSummary(
            id = 999L,
            externalId = "ext-4",
            name = "Empty Route",
            description = "",
            isSelected = false,
            distance = 0.0,
            elevationGain = 0.0,
            bSportType = BSportType.UNKNOWN,
            source = RouteSource.LOCAL_GPX
        )
        val route = RouteWithPath(summary = summary, path = emptyList())

        val bounds = calculateRouteBounds(route)
        assertNull("Bounds must be null if both path and summary extrema are empty", bounds)
    }
}
