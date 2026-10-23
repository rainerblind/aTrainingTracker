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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract and layout tests for [WorkoutHeader] verifying single-line compact landscape mode
 * and portrait multi-row invariant preservation (REQ-UI-433.2 / TST-UI-398.2 / ATT-4774).
 */
class WorkoutHeaderLandscapeTest {

    private val headerSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
    }

    @Test
    fun testWorkoutHeader_branchesOnLandscapeOrientation() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        assertTrue(
            "WorkoutHeader must query LocalConfiguration for ORIENTATION_LANDSCAPE",
            content.contains("configuration.orientation == Configuration.ORIENTATION_LANDSCAPE")
        )
        assertTrue(
            "WorkoutHeader must branch on isLandscape",
            content.contains("if (isLandscape)")
        )
    }

    @Test
    fun testWorkoutHeader_landscapeRendersSingleCompactRow() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        // Single compact row with heightIn <= 44.dp
        assertTrue(
            "WorkoutHeader landscape layout must specify heightIn(max = 44.dp)",
            content.contains(".heightIn(max = 44.dp)")
        )

        // Landscape branch extracts and preserves all header metadata elements
        val landscapeSection = content.substringAfter("if (isLandscape) {", "")
            .substringBefore("} else {", "")

        assertTrue(
            "Landscape row must render sport icon",
            landscapeSection.contains("data.bSportType.iconResId")
        )
        assertTrue(
            "Landscape row must render workout name",
            landscapeSection.contains("data.workoutName")
        )
        assertTrue(
            "Landscape row must render cluster pill when linked to cluster",
            landscapeSection.contains("data.clusterId > 0") && landscapeSection.contains("data.clusterName")
        )
        assertTrue(
            "Landscape row must render sport name",
            landscapeSection.contains("data.sportName")
        )
        assertTrue(
            "Landscape row must render date and time",
            landscapeSection.contains("data.formattedDate") && landscapeSection.contains("data.formattedTime")
        )
        assertTrue(
            "Landscape row must include action buttons",
            landscapeSection.contains("actionButtonsContent()")
        )
    }

    @Test
    fun testWorkoutHeader_preservesPortraitMultiRowLayout() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        // Portrait branch in 'else' block
        val portraitSection = content.substringAfter("} else {", "")
            .substringBefore("// Context Menu for deletion", "")

        assertTrue(
            "Portrait branch must retain stacked Column layout",
            portraitSection.contains("Column(")
        )
        assertTrue(
            "Portrait branch must retain 1. TOP ROW: Sport Icon and Workout Name",
            portraitSection.contains("// 1. TOP ROW: Sport Icon and Workout Name")
        )
        assertTrue(
            "Portrait branch must retain 2. BOTTOM CONTENT: Organized in horizontal rows",
            portraitSection.contains("// 2. BOTTOM CONTENT: Organized in horizontal rows")
        )
    }
}
