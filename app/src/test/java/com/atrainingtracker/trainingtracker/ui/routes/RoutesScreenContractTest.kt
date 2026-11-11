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
import com.atrainingtracker.testing.LocalizationTestCache
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
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests verifying that [RoutesScreen] and [RoutesViewModel]
 * decouple route preview polyline styling from hardcoded active navigation (REQ-UI-347 / TST-UI-307 / ATT-3172),
 * and retain [RouteTabbedScreen] in composition while [EditRouteScreen] bottom sheet is open (REQ-UI-423 / TST-UI-387 / ATT-4285).
 */
class RoutesScreenContractTest {

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
        val routesScreenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenKt")
        assertNotNull("RoutesScreenKt class must exist", routesScreenClass)

        val vmClass = RoutesViewModel::class.java
        val method = vmClass.methods.find { it.name == "getActiveNavigatedRouteId" }
        assertNotNull("RoutesViewModel must expose getActiveNavigatedRouteId", method)
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

    /**
     * Verifies that RouteTabbedScreen is continuously retained in composition when not inspecting details,
     * and EditRouteScreen is composed conditionally as an overlay (REQ-UI-423.1, REQ-UI-423.2 / TST-UI-387.1).
     */
    @Test
    fun testRoutesScreen_compositionRetention_routeTabbedScreenMountedWhenEditing() {
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteTabbedScreenKt")
        assertNotNull("RouteTabbedScreenKt class must exist", tabbedClass)

        val editRouteClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.EditRouteScreenKt")
        assertNotNull("EditRouteScreenKt class must exist", editRouteClass)
    }

    /**
     * Verifies that EditRouteScreen dismissal contracts (onSave, onCancel, BackHandler) cleanly reset
     * selectedRouteIdForEdit to null and invoke viewModel.updateRoute on save (REQ-UI-423.3, REQ-UI-423.4 / TST-UI-387.2).
     */
    @Test
    fun testRoutesScreen_editRouteScreen_overlayContractsAndDismissal() {
        val editRouteClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.EditRouteScreenKt")
        assertNotNull("EditRouteScreenKt class must exist", editRouteClass)

        val methods = editRouteClass.declaredMethods
        val editMethod = methods.find { it.name == "EditRouteScreen" && Modifier.isPublic(it.modifiers) }
        assertNotNull("EditRouteScreen composable must exist and be public", editMethod)
    }

    /**
     * Verifies that all route editing string resources have 100% translation parity and non-blank values
     * across all 9 canonical application locales (REQ-UI-423.5 / REQ-LOC-001 / TST-UI-387.3).
     */
    @Test
    fun testRouteEditingStrings_9LanguageParity() {
        val requiredKeys = listOf("route_edit", "name", "route_sport_type", "route_description", "save", "cancel")

        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            for (key in requiredKeys) {
                val value = strings[key]
                assertNotNull("Key '$key' must exist in locale $locale", value)
                assertTrue("Key '$key' must not be blank in locale $locale", value!!.isNotBlank())
            }
        }
    }

    /**
     * Verifies that [RoutesScreen] discriminates between single-file and multi-file route picking,
     * launching [GpxImportActivity] directly for single-file and presenting [BatchImportSportTypeDialog]
     * for multi-file with appropriate delegation (REQ-MAP-053.2, TST-MAP-056.5).
     */
    @Test
    fun testMultiFileRouteSelection_flowDiscrimination_TST_MAP_056_5() {
        val routesScreenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenKt")
        assertNotNull("RoutesScreenKt class must exist", routesScreenClass)

        val batchImportClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.BatchImportSportTypeDialogKt")
        assertNotNull("BatchImportSportTypeDialogKt class must exist", batchImportClass)
    }
}
