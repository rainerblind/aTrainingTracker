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

    /**
     * TST-UI-384.6: Zero-look double-tap gesture recognizer evaluates on PointerEventPass.Main,
     * preserves horizontal pager swiping, vertical scrolling, and triggers tabToggleActions.onRingBikeBell().
     */
    @Test
    fun testSensorGridScreen_doubleTapGesturePreservesHorizontalPagerSwiping() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // 1. Must define detectCockpitDoubleTap evaluating on PointerEventPass.Main with requireUnconsumed = false
        assertTrue(
            "detectCockpitDoubleTap must awaitFirstDown with PointerEventPass.Main and requireUnconsumed = false",
            content.contains("awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)")
        )

        // 2. Drag cancellation: must abort double-tap upon moving beyond touchSlop, preserving pager/scroll
        assertTrue(
            "detectCockpitDoubleTap must detect movement beyond touchSlop to preserve vertical scrolling and pager swiping",
            content.contains("(currentPos - downPos).getDistance() > touchSlop") &&
                content.contains("isDrag = true")
        )

        // 3. Must trigger tabToggleActions.onRingBikeBell() and haptic click on confirmed double-tap
        assertTrue(
            "Tracking mode BoxWithConstraints must apply detectCockpitDoubleTap when state.showBikeBell == true",
            content.contains("if (isTracking && state.showBikeBell)") &&
                content.contains("Modifier.detectCockpitDoubleTap(enabled = true)") &&
                content.contains("tabToggleActions.onRingBikeBell()")
        )

        // 4. In configuration mode, SpatialCockpitToggleCard for config_tracking__showBikeBell must be present
        assertTrue(
            "Configuration mode must contain SpatialCockpitToggleCard for config_tracking__showBikeBell",
            content.contains("SpatialCockpitToggleCard(") &&
                content.contains("R.string.config_tracking__showBikeBell") &&
                content.contains("tabToggleActions.onToggleBikeBell(it)")
        )
    }
}
