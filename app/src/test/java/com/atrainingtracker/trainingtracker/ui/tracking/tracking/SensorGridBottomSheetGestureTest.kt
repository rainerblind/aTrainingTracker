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
 * Architectural and gesture contract tests verifying live climb and segment bottom sheet
 * re-expansion via bottom swipe-up gestures, peek area tap-to-expand, and touch neutrality
 * when inactive (REQ-UI-359 / TST-UI-320 / ATT-3341).
 */
class SensorGridBottomSheetGestureTest {

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
    fun testSensorGridScreen_declaresCoroutineScopeForSheetExpansion() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must declare rememberCoroutineScope for bottom sheet expansion (REQ-UI-359.1 / TST-UI-320.1)",
            content.contains("val coroutineScope = rememberCoroutineScope()")
        )
    }

    @Test
    fun testSensorGridScreen_bottomSwipeGestureDetector_gatedOnShouldShowBottomSheetAndNotExpanded() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "Bottom swipe gesture detector must be gated on shouldShowBottomSheet and not Expanded (REQ-UI-359.1 / TST-UI-320.1)",
            content.contains("if (shouldShowBottomSheet && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded)")
        )
        assertTrue(
            "Gesture detector must cover the bottom edge and peek region (REQ-UI-359.1 / TST-UI-320.1)",
            content.contains("BottomSheetDesign.PeekHeightLiveSegment + navBarHeight")
        )
    }

    @Test
    fun testSensorGridScreen_bottomSwipeGestureDetector_triggersExpandOnUpwardDrag() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "Gesture detector must monitor vertical drags with detectVerticalDragGestures (REQ-UI-359.1 / TST-UI-320.1)",
            content.contains("detectVerticalDragGestures(")
        )
        assertTrue(
            "Upward drag or tap on bottom target must trigger scaffoldState.bottomSheetState.expand() (REQ-UI-359.1 / TST-UI-320.1)",
            content.contains("scaffoldState.bottomSheetState.expand()")
        )
    }

    @Test
    fun testSensorGridScreen_peekHeader_triggersExpandOnClick() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "Peek header must support tap-to-expand when partially expanded (REQ-UI-359.2 / TST-UI-320.1)",
            content.contains("scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded") &&
            content.contains("scaffoldState.bottomSheetState.expand()")
        )
    }

    @Test
    fun testSensorGridScreen_inactiveState_suppressesGestureInterceptors() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // REQ-UI-359.3 / TST-UI-320.2: Touch neutrality when inactive
        assertTrue(
            "Gesture detector is strictly guarded by shouldShowBottomSheet (REQ-UI-359.3 / TST-UI-320.2)",
            content.contains("if (shouldShowBottomSheet && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded)")
        )
        assertTrue(
            "sheetPeekHeight resolves to 0.dp when shouldShowBottomSheet is false",
            content.contains("sheetPeekHeight = if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp")
        )
    }
}
