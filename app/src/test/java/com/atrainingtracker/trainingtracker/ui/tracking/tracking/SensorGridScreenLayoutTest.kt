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
 * Architectural and layout contract tests verifying dynamic cockpit weight allocation,
 * sensor grid scrollability constraints, and flexible elevation profile expansion
 * when the map is disabled on the tracking screen (REQ-UI-339 / TST-UI-299 / ATT-3107).
 */
class SensorGridScreenLayoutTest {

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
    fun testSensorGrid_appliesWeightConstraintWithFillFalse() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "Sensor grid Column must apply .weight(1f, fill = false) to prevent crowding bottom cockpit elements (TST-UI-299.1)",
            content.contains(".weight(1f, fill = false)")
        )
    }

    @Test
    fun testElevationProfile_appliesDynamicWeightWhenMapDisabled() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile Surface must conditionally apply Modifier.weight(1f) when !state.mapState.showMap (TST-UI-299.2)",
            content.contains("if (!state.mapState.showMap) Modifier.weight(1f) else Modifier")
        )
    }

    @Test
    fun testElevationProfile_passesFillParentHeightAndFillMaxSizeWhenMapDisabled() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile must receive fillParentHeight = !state.mapState.showMap (TST-UI-299.3)",
            content.contains("fillParentHeight = !state.mapState.showMap")
        )
        assertTrue(
            "ElevationProfile must receive modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth() (TST-UI-299.3)",
            content.contains("modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()")
        )
    }

    @Test
    fun testElevationProfile_guardedByPathPointsSize() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile rendering must be guarded by state.pathPoints.size >= 2 to prevent empty canvas stretching (TST-UI-299.4)",
            content.contains("state.showElevationProfile && state.pathPoints.size >= 2")
        )
    }
}
