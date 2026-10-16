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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test enforcing REQ-UI-312 / TST-UI-272.3:
 * SportTypeSelector Defensive Loading Contract Verification.
 */
class SportTypeSelectorContractTest {

    @Test
    fun testSportTypeSelectorUsesSafePainterResourceAndFallbackVectors() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = when {
            File(projectDir, "src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt").exists() ->
                File(projectDir, "src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt")
            File(projectDir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt").exists() ->
                File(projectDir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt")
            else -> File(projectDir, "../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt")
        }

        assertTrue("SportTypeSelector.kt must exist: ${sourceFile.absolutePath}", sourceFile.exists())

        val content = sourceFile.readText()
        assertTrue("Must use safePainterResource for icon rendering", content.contains("safePainterResource("))
        assertTrue("Must supply DirectionsRun fallback for running", content.contains("DirectionsRun"))
        assertTrue("Must supply DirectionsBike fallback for cycling", content.contains("DirectionsBike"))
        assertTrue("Must supply FitnessCenter fallback for other", content.contains("FitnessCenter"))
    }

    @Test
    fun testAllSportTypeDrawablesExistInRootDrawableDirectory() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val resDir = when {
            File(projectDir, "src/main/res/drawable").exists() -> File(projectDir, "src/main/res/drawable")
            File(projectDir, "app/src/main/res/drawable").exists() -> File(projectDir, "app/src/main/res/drawable")
            else -> File(projectDir, "../app/src/main/res/drawable")
        }

        assertTrue("Root drawable directory must exist", resDir.exists() && resDir.isDirectory)

        val rootDrawables = resDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val requiredDrawables = listOf(
            "bsport_run.png",
            "bsport_run_gray.png",
            "bsport_bike.png",
            "bsport_bike_gray.png",
            "bsport_other.png",
            "bsport_other_gray.png"
        )

        for (drawable in requiredDrawables) {
            assertTrue("Drawable $drawable must exist in root res/drawable/", drawable in rootDrawables)
        }
    }
}
