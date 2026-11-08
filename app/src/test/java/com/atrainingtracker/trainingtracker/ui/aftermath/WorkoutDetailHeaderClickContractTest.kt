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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for [WorkoutDetailTabbedContent] verifying that full-card click
 * does not trigger workout editing, reserving edit triggering strictly to the dedicated
 * Edit pencil icon button (REQ-UI-498.1 / TST-UI-498.1).
 */
class WorkoutDetailHeaderClickContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val tabbedContentFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
    }

    @Test
    fun testWorkoutHeader_onClickedIsNull_TST_UI_498_1() {
        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedContentFile.exists())
        val content = tabbedContentFile.readText()

        // Verify that onClicked is null on WorkoutHeader in tabbed content
        assertTrue(
            "WorkoutDetailTabbedContent must bind onClicked = null on WorkoutHeader to prevent accidental edits",
            content.contains("onClicked = null")
        )

        // Verify that onClicked is not bound to onEditWorkout
        assertFalse(
            "WorkoutDetailTabbedContent must NOT bind onClicked to onEditWorkout",
            Regex("""onClicked\s*=\s*onEditWorkout""").containsMatchIn(content)
        )

        // Verify dedicated onEditWorkout parameter remains wired
        assertTrue(
            "WorkoutDetailTabbedContent must keep onEditWorkout wired to the pencil icon button",
            Regex("""onEditWorkout\s*=\s*onEditWorkout\?\.let""").containsMatchIn(content)
        )
    }
}
