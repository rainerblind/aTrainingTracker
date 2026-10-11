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

import org.junit.Assert.assertFalse
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
    fun testElevationProfile_doesNotApplyWeightWhenMapDisabled() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-346 / TST-UI-306.1: Surface must not apply Modifier.weight(1f) to prevent half-screen stretching
        assertFalse(
            "ElevationProfile Surface must NOT apply Modifier.weight(1f) when !state.mapState.showMap (TST-UI-306.1)",
            content.contains("if (!state.mapState.showMap) Modifier.weight(1f) else Modifier")
        )
        assertFalse(
            "ElevationProfile Surface must not apply weight(1f) (TST-UI-306.1)",
            content.contains(".then(\n                                    if (!state.mapState.showMap) Modifier.weight(1f)")
        )
    }

    @Test
    fun testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-346 / TST-UI-306.2: ElevationProfile must use canonical adaptive height (not fillParentHeight or fillMaxSize)
        assertFalse(
            "ElevationProfile must NOT receive fillParentHeight = !state.mapState.showMap (TST-UI-306.2)",
            content.contains("fillParentHeight = !state.mapState.showMap")
        )
        assertFalse(
            "ElevationProfile must NOT receive dynamic fillMaxSize modifier (TST-UI-306.2)",
            content.contains("modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()")
        )
        assertTrue(
            "ElevationProfile must receive modifier = Modifier.fillMaxWidth() (TST-UI-306.2)",
            content.contains("modifier = Modifier.fillMaxWidth()")
        )
    }

    @Test
    fun testElevationProfile_guardedByPathPointsSize() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile rendering must be guarded by path points size >= 2 to prevent empty canvas stretching (TST-UI-306.4 / TST-UI-315.3)",
            content.contains("state.showElevationProfile && state.pathPoints.size >= 2") ||
            content.contains("state.showElevationProfile && resolvedProfile.pathPoints.size >= 2")
        )
    }
}
