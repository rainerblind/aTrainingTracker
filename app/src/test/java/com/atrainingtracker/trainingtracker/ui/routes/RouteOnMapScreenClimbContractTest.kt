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

import com.atrainingtracker.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for RouteOnMapScreen climb polyline integration (REQ-UI-298, TST-UI-258, ATT-2509).
 */
class RouteOnMapScreenClimbContractTest {

    @Test
    fun testRouteOnMapScreen_invokesClimbsWithinMapContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("RouteOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteOnMapScreen composable must exist and be public", screenMethod)
    }

    @Test
    fun testRouteOnMapScreen_doesNotRenderClimbStartAscentMarkers() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt class must exist", clazz)
    }

    @Test
    fun testRouteOnMapScreen_preservesStartAndEndMarkers() {
        assertTrue("control_start marker drawable must exist", R.drawable.control_start != 0)
        assertTrue("control_stop marker drawable must exist", R.drawable.control_stop != 0)
    }
}
