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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural modularity and file size constraint tests for AdvancedTuningDialog (REQ-UI-262, TST-UI-221.1).
 * Enforces AC-1: Every source file in the tuning package must not exceed 400 lines of code.
 */
class AdvancedTuningModularityTest {

    private fun findTuningDirectory(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Tuning directory not found in candidates: $candidates")
    }

    @Test
    fun testModularFilesExist() {
        val tuningDir = findTuningDirectory()

        val expectedFiles = listOf(
            "AdvancedTuningDialog.kt",
            "AdvancedTuningAccordion.kt",
            "AdvancedTuningDialogFragment.kt",
            "TuningFormControls.kt",
            "TuningPaceCeilingFormatter.kt",
            "categories/CockpitTypographySection.kt",
            "categories/AmoledBatterySaverSection.kt",
            "categories/SensorsGpsFilterSection.kt",
            "categories/AftermathAnalysisSection.kt",
            "categories/WorkoutMasksAndCardsSection.kt",
            "categories/NavigationSection.kt",
            "categories/SensorSearchTuningSection.kt"
        )

        for (relPath in expectedFiles) {
            val file = File(tuningDir, relPath)
            assertTrue("Expected modular file $relPath must exist at ${file.absolutePath}", file.exists())
        }
    }

    @Test
    fun testAllTuningSourceFilesUnder400Lines() {
        val tuningDir = findTuningDirectory()
        val ktFiles = tuningDir.walkTopDown().filter { it.extension == "kt" }.toList()

        assertTrue("Must discover at least 7 Kotlin files in tuning package", ktFiles.size >= 7)

        for (file in ktFiles) {
            val lineCount = file.readLines().size
            assertTrue(
                "File ${file.name} has $lineCount lines, exceeding the strict 400 lines threshold (AC-1 / REQ-UI-262)",
                lineCount < 400
            )
        }
    }
}
