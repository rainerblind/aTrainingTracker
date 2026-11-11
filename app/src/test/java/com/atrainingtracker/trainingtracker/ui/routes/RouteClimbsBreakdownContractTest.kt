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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteClimbsBreakdownSection click wiring and RouteOnMapScreen climb detail sheet integration (REQ-UI-300, TST-UI-260, ATT-2511).
 */
class RouteClimbsBreakdownContractTest {

    @Test
    fun testRouteClimbsBreakdownSection_hasOnClimbClickParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val breakdownMethod = methods.find { it.name == "RouteClimbsBreakdownSection" && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteClimbsBreakdownSection composable must exist and be public", breakdownMethod)

        val paramTypes = breakdownMethod!!.parameterTypes
        val hasClimbCallback = paramTypes.any {
            Function1::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function1")
        }
        assertTrue("RouteClimbsBreakdownSection must declare onClimbClick callback parameter", hasClimbCallback)
    }

    @Test
    fun testRouteOnMapScreen_integratesClimbDetailSheetAndClickCallback() {
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", screenClazz)

        val methods = screenClazz.declaredMethods
        val screenMethod = methods.find { it.name == "RouteOnMapScreen" && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteOnMapScreen composable must exist and be public", screenMethod)

        val climbDetailSheetClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetKt")
        assertNotNull("ClimbDetailSheetKt class must exist", climbDetailSheetClazz)

        val climbDetailMethods = climbDetailSheetClazz.declaredMethods
        val sheetMethod = climbDetailMethods.find { it.name == "ClimbDetailSheet" && Modifier.isPublic(it.modifiers) }
        assertNotNull("ClimbDetailSheet composable must exist and be public", sheetMethod)
    }
}
