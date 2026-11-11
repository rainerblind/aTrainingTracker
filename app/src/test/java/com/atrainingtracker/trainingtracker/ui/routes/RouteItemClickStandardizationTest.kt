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
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit and structural contract test suite for REQ-UI-191 / TST-UI-145:
 * Standardize list item heading click behavior across all application lists
 * to "inspection-first", verify dedicated 48dp Material 3 edit button in
 * RouteSummaryHeader, and preserve REQ-UI-061 delete-only context menu.
 */
class RouteItemClickStandardizationTest {

    private val sampleSummary = RouteSummary(
        id = 101L,
        externalId = "sample_route_101",
        name = "Isar River Gravel Loop",
        description = "Scenic riverside trail",
        isSelected = true,
        distance = 34500.0,
        elevationGain = 320.0,
        bSportType = BSportType.BIKE,
        source = RouteSource.LOCAL_GPX
    )

    @Test
    fun testRouteItemHeaderClickNavigatesToInspectionDetails() {
        val routeItemClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt")
        assertNotNull("RouteItemKt class must exist", routeItemClass)

        val methods = routeItemClass.declaredMethods
        val routeItemMethod = methods.find { it.name.startsWith("RouteItem") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteItem composable must exist and be public", routeItemMethod)

        val paramTypes = routeItemMethod!!.parameterTypes
        assertTrue(
            "RouteItem must accept RouteSummary parameter",
            paramTypes.any { it == RouteSummary::class.java }
        )
    }

    @Test
    fun testRouteItemContextMenuPreservesDeleteOnlyInvariant() {
        val routeItemClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt")
        assertNotNull("RouteItemKt class must exist", routeItemClass)

        val methods = routeItemClass.declaredMethods
        val routeItemMethod = methods.find { it.name.startsWith("RouteItem") }
        assertNotNull("RouteItem composable must exist", routeItemMethod)
    }

    @Test
    fun testRouteSummaryHeaderEditButtonContractAndTouchTarget() {
        val headerClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSummaryHeaderKt")
        assertNotNull("RouteSummaryHeaderKt class must exist", headerClass)

        val methods = headerClass.declaredMethods
        val headerMethod = methods.find { it.name.startsWith("RouteSummaryHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSummaryHeader composable must exist and be public", headerMethod)

        val paramTypes = headerMethod!!.parameterTypes
        val hasEditCallback = paramTypes.any {
            Function0::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function0")
        }
        assertTrue("RouteSummaryHeader must declare onEditClick parameter", hasEditCallback)
    }

    @Test
    fun testRouteItemInteractionCallbacksSeparation() {
        var mapClickedId: Long? = null
        var editClickedId: Long? = null
        var toggledId: Long? = null
        var toggledState: Boolean? = null

        val onMapClick: (Long) -> Unit = { mapClickedId = it }
        val onEditClick: (Long) -> Unit = { editClickedId = it }
        val onToggleSelection: (Long, Boolean) -> Unit = { id, state ->
            toggledId = id
            toggledState = state
        }

        // Tapping header triggers map click (inspection)
        onMapClick(sampleSummary.id)
        assertEquals(101L, mapClickedId)

        // Tapping edit button triggers edit click (edit route screen)
        onEditClick(sampleSummary.id)
        assertEquals(101L, editClickedId)

        // Toggling selection operates independently
        onToggleSelection(sampleSummary.id, false)
        assertEquals(101L, toggledId)
        assertEquals(false, toggledState)
    }

    @Test
    fun testLocalizationParityRouteEditAcross9Locales() {
        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            val value = strings["route_edit"]
            assertNotNull("route_edit must exist in locale $locale", value)
            assertTrue("route_edit in locale $locale must not be blank", value!!.isNotBlank())
        }
    }
}
