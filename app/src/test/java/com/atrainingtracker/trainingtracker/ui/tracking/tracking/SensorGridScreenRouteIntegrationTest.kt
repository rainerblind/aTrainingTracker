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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests verifying that [SensorGridScreen] directly integrates
 * the Quick Route Selector HUD entry point, Auto-Detected Route Banner, and Modal Bottom Sheet
 * (REQ-MAP-024 / TST-MAP-026 / ATT-1835).
 */
class SensorGridScreenRouteIntegrationTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testSensorGridScreen_declaresRouteSelectorViewModelParameter() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must declare optional routeSelectorViewModel parameter",
            content.contains("routeSelectorViewModel: RouteSelectorViewModel? = null")
        )
    }

    @Test
    fun testSensorGridScreen_integratesRouteActionChipRow() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must render RouteActionChipRow in tracking mode",
            content.contains("RouteActionChipRow(")
        )
        assertTrue(
            "SensorGridScreen must define RouteActionChipRow composable",
            content.contains("fun RouteActionChipRow(")
        )
        assertTrue(
            "RouteActionChipRow must use R.drawable.ic_route",
            content.contains("R.drawable.ic_route")
        )
        assertTrue(
            "RouteActionChipRow must use R.string.route_action_select",
            content.contains("R.string.route_action_select")
        )
    }

    @Test
    fun testSensorGridScreen_integratesAutoDetectedRouteBanner() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must host AutoDetectedRouteBanner",
            content.contains("AutoDetectedRouteBanner(")
        )
        assertTrue(
            "AutoDetectedRouteBanner must hook into activateCandidate",
            content.contains("activateCandidate")
        )
        assertTrue(
            "AutoDetectedRouteBanner must hook into dismissCandidate",
            content.contains("dismissCandidate")
        )
    }

    @Test
    fun testSensorGridScreen_integratesRouteSelectorModalBottomSheet() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must invoke RouteSelectorModalBottomSheet",
            content.contains("RouteSelectorModalBottomSheet(")
        )
        assertTrue(
            "RouteSelectorModalBottomSheet must be toggled by showRouteSelectorSheet",
            content.contains("if (showRouteSelectorSheet)")
        )
    }

    @Test
    fun testSensorGridScreen_forwardsLocationUpdatesToRouteSelectorViewModel() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must observe currentLocationFlow and dispatch to onLocationChanged",
            content.contains("onLocationChanged(location)")
        )
    }
}
