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
 * Architectural and wiring contract tests verifying that [SensorGridScreen] wires
 * onEdit to gridActions.onEditField in tracking mode (REQ-UI-336 / TST-UI-296.1, TST-UI-296.2 / ATT-3101).
 */
class SensorGridScreenContractTest {

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
    fun testTrackingMode_wiresOnEditToGridActions() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must wire onEdit to gridActions.onEditField(fieldState) in tracking mode (REQ-UI-336 / TST-UI-296.1)",
            content.contains("onEdit = { gridActions.onEditField(fieldState) }")
        )
    }

    @Test
    fun testTrackingMode_gatesMoveAndDeleteToNoOps() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // Verify that the tracking branch keeps onStartMove = {} and onDelete = {}
        val trackingSectionPattern = Regex(
            """onStartMove\s*=\s*\{\}\s*,\s*onEdit\s*=\s*\{\s*gridActions\.onEditField\(fieldState\)\s*\}\s*,\s*onDelete\s*=\s*\{\}"""
        )
        assertTrue(
            "SensorGridScreen must keep onStartMove and onDelete as no-ops in tracking mode (REQ-UI-336 / TST-UI-296.2)",
            trackingSectionPattern.containsMatchIn(content)
        )
    }
}
