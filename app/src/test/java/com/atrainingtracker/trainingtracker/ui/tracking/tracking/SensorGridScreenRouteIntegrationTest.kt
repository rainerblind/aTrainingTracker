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
    fun testSensorGridScreen_doesNotDeclareRouteSelectorViewModelParameter() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not declare routeSelectorViewModel parameter (REQ-UI-311)",
            content.contains("routeSelectorViewModel")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateRouteActionChipRow() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not render RouteActionChipRow in tracking mode (REQ-UI-279.1 / ATT-2458)",
            content.contains("RouteActionChipRow(")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not define RouteActionChipRow composable",
            content.contains("fun RouteActionChipRow(")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not host AutoDetectedRouteBanner (REQ-UI-311)",
            content.contains("AutoDetectedRouteBanner(")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not hook into activateCandidate",
            content.contains("activateCandidate")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not hook into dismissCandidate",
            content.contains("dismissCandidate")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotHostRedundantRouteSelectorModalBottomSheet() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not host redundant RouteSelectorModalBottomSheet (REQ-UI-279.1)",
            content.contains("RouteSelectorModalBottomSheet(")
        )

        val tabsFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue(
            "TrackingTabsScreen must host RouteSelectorModalBottomSheet (REQ-UI-279.2)",
            tabsFile.readText().contains("RouteSelectorModalBottomSheet(")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotForwardLocationUpdatesToRouteSelectorViewModel() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not dispatch location updates to RouteSelectorViewModel (REQ-UI-311)",
            content.contains("actualRouteSelectorViewModel.onLocationChanged")
        )
    }

    @Test
    fun testRouteSelectionButton_integratesClearRouteAction() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt")
        val content = file.readText()

        assertTrue(
            "RouteSelectionButton must declare onClearRoute parameter",
            content.contains("onClearRoute: (() -> Unit)? = null")
        )
        assertTrue(
            "RouteSelectionButton must use route_action_clear string resource",
            content.contains("R.string.route_action_clear")
        )
    }
}
