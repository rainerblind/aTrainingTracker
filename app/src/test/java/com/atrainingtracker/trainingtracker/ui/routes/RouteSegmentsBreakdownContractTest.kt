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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteSegmentsBreakdownSection and RouteOnMapScreen tab coexistence
 * (REQ-UI-302, TST-UI-262, ATT-2583).
 */
class RouteSegmentsBreakdownContractTest {

    @Test
    fun testRouteSegmentsBreakdownSection_isAccessible() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSegmentsBreakdownSectionKt")
        assertNotNull("RouteSegmentsBreakdownSectionKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sectionMethod = methods.find { it.name.startsWith("RouteSegmentsBreakdownSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSegmentsBreakdownSection composable must exist and be public", sectionMethod)
    }

    @Test
    fun testRouteSegmentsBreakdownSection_declaresRequiredParametersAndCard() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSegmentsBreakdownSectionKt")
        assertNotNull("RouteSegmentsBreakdownSectionKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sectionMethod = methods.find { it.name == "RouteSegmentsBreakdownSection" }
        assertNotNull("RouteSegmentsBreakdownSection composable must exist", sectionMethod)

        val paramTypes = sectionMethod!!.parameterTypes
        val hasListParam = paramTypes.any { it == java.util.List::class.java }
        assertTrue("RouteSegmentsBreakdownSection must declare List parameter for segments", hasListParam)

        val hasCallback = paramTypes.any {
            Function1::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function1")
        }
        assertTrue("RouteSegmentsBreakdownSection must declare onSegmentClick callback parameter", hasCallback)
    }

    @Test
    fun testRouteOnMapScreen_integratesSegmentsAndTabCoexistence() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", screenClazz)

        val screenMethods = screenClazz.declaredMethods
        val screenMethod = screenMethods.find { it.name.startsWith("RouteOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteOnMapScreen composable must exist and be public", screenMethod)

        // Verify RouteBreakdownTab enum
        val tabClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteBreakdownTab")
        assertNotNull("RouteBreakdownTab enum must exist", tabClass)
        assertTrue("RouteBreakdownTab must be an enum", tabClass.isEnum)

        val enumConstants = tabClass.enumConstants ?: emptyArray()
        val names = enumConstants.map { (it as Enum<*>).name }
        assertTrue("RouteBreakdownTab must contain CLIMBS", names.contains("CLIMBS"))
        assertTrue("RouteBreakdownTab must contain SEGMENTS", names.contains("SEGMENTS"))
    }
}
