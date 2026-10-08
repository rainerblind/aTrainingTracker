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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for RouteOnMapScreen climb polyline integration (REQ-UI-298, TST-UI-258, ATT-2509).
 */
class RouteOnMapScreenClimbContractTest {

    @Test
    fun testRouteOnMapScreen_invokesClimbsWithinMapContent() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        assertTrue("RouteOnMapScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "RouteOnMapScreen.kt must call climbs(climbs) in mapContent",
            content.contains("climbs(climbs)")
        )

        val routesIndex = content.indexOf("routes(listOf(route))")
        val climbsIndex = content.indexOf("climbs(climbs)")

        assertTrue(
            "routes(listOf(route)) must be present",
            routesIndex != -1
        )
        assertTrue(
            "climbs(climbs) must be called after routes(listOf(route)) in mapContent",
            climbsIndex > routesIndex
        )
    }

    @Test
    fun testRouteOnMapScreen_doesNotRenderClimbStartAscentMarkers() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        assertTrue("RouteOnMapScreen.kt must exist", file.exists())
        val content = file.readText()

        // REQ-UI-307: Redundant climb ascent pin markers must be eliminated from the route map
        org.junit.Assert.assertFalse(
            "RouteOnMapScreen must not reference ic_ascent pin markers on map (REQ-UI-307)",
            content.contains("ic_ascent")
        )
        org.junit.Assert.assertFalse(
            "RouteOnMapScreen must not declare or compute climbMarkers",
            content.contains("climbMarkers")
        )
    }

    @Test
    fun testRouteOnMapScreen_preservesStartAndEndMarkers() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        assertTrue("RouteOnMapScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "RouteOnMapScreen must preserve control_start marker for route inception",
            content.contains("R.drawable.control_start")
        )
        assertTrue(
            "RouteOnMapScreen must preserve control_stop marker for route termination",
            content.contains("R.drawable.control_stop")
        )
    }
}

