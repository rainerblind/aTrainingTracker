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
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteClimbsBreakdownSection click wiring and RouteOnMapScreen climb detail sheet integration (REQ-UI-300, TST-UI-260, ATT-2511).
 */
class RouteClimbsBreakdownContractTest {

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

    @Test
    fun testRouteClimbsBreakdownSection_hasOnClimbClickParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val breakdownMethod = methods.find { it.name.startsWith("RouteClimbsBreakdownSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteClimbsBreakdownSection composable must exist and be public", breakdownMethod)
    }

    @Test
    fun testRouteOnMapScreen_integratesClimbDetailSheetAndClickCallback() {
        assertTrue("RouteOnMapScreen.kt must exist", routeOnMapScreenFile.exists())
        val content = routeOnMapScreenFile.readText()

        // 1. Verify onClimbClick parameter in RouteClimbsBreakdownSection
        assertTrue(
            "RouteClimbsBreakdownSection must declare onClimbClick callback parameter",
            content.contains("onClimbClick: ((Climb) -> Unit)? = null")
        )

        // 2. Verify ElevatedCard onClick wiring
        assertTrue(
            "ElevatedCard must wire onClimbClick invocation",
            content.contains("onClick = { onClimbClick?.invoke(climb) }")
        )

        // 3. Verify selectedClimbForDetail state declaration
        assertTrue(
            "RouteOnMapScreen must declare selectedClimbForDetail state",
            content.contains("var selectedClimbForDetail by remember { mutableStateOf<Climb?>(null) }")
        )

        // 4. Verify RouteClimbsBreakdownSection passes climb selection lambda
        assertTrue(
            "RouteClimbsBreakdownSection must receive onClimbClick updating selectedClimbForDetail",
            content.contains("onClimbClick = { climb -> selectedClimbForDetail = climb }")
        )

        // 5. Verify ClimbDetailSheet rendering
        assertTrue(
            "RouteOnMapScreen must render ClimbDetailSheet when climb selected",
            content.contains("ClimbDetailSheet(")
        )
    }
}
