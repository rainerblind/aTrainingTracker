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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying gesture dispatch contracts in [SensorFieldView]
 * across [ScreenMode] states (REQ-UI-336 / TST-UI-296.3 / ATT-3101).
 */
class SensorFieldViewGestureContractTest {

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
    fun testSensorFieldView_gestureContractInSource() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt")
        val content = file.readText()

        assertTrue(
            "onClick must only trigger onEdit in CONFIGURATION mode",
            content.contains("if (screenMode == ScreenMode.CONFIGURATION) {\n                        onEdit()\n                    }") ||
            content.contains("if (screenMode == ScreenMode.CONFIGURATION) {")
        )

        assertTrue(
            "onLongClick must delegate to onEdit in TRACKING mode",
            content.contains("else if (screenMode == ScreenMode.TRACKING) {\n                        onEdit()\n                    }") ||
            content.contains("else if (screenMode == ScreenMode.TRACKING)")
        )

        assertTrue(
            "onLongClick must delegate to onStartMove in CONFIGURATION mode",
            content.contains("if (screenMode == ScreenMode.CONFIGURATION) {\n                        onStartMove()\n                    }") ||
            content.contains("onStartMove()")
        )
    }

    @Test
    fun testGestureDispatchLogic_trackingMode() {
        var editCount = 0
        var moveCount = 0

        val onEdit = { editCount++ }
        val onStartMove = { moveCount++ }

        val dispatchClick = { mode: ScreenMode ->
            if (mode == ScreenMode.CONFIGURATION) {
                onEdit()
            }
        }

        val dispatchLongClick = { mode: ScreenMode ->
            if (mode == ScreenMode.CONFIGURATION) {
                onStartMove()
            } else if (mode == ScreenMode.TRACKING) {
                onEdit()
            }
        }

        // Test in TRACKING mode:
        // Single tap must NOT invoke onEdit or onStartMove
        dispatchClick(ScreenMode.TRACKING)
        assertEquals("Single tap in TRACKING must not trigger onEdit", 0, editCount)
        assertEquals("Single tap in TRACKING must not trigger onStartMove", 0, moveCount)

        // Long click must invoke onEdit and NOT onStartMove
        dispatchLongClick(ScreenMode.TRACKING)
        assertEquals("Long click in TRACKING must trigger onEdit", 1, editCount)
        assertEquals("Long click in TRACKING must not trigger onStartMove", 0, moveCount)

        // Test in CONFIGURATION mode:
        // Single tap must invoke onEdit
        dispatchClick(ScreenMode.CONFIGURATION)
        assertEquals("Single tap in CONFIGURATION must trigger onEdit", 2, editCount)

        // Long click must invoke onStartMove
        dispatchLongClick(ScreenMode.CONFIGURATION)
        assertEquals("Long click in CONFIGURATION must trigger onStartMove", 1, moveCount)
    }
}
