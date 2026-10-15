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

package com.atrainingtracker.trainingtracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and unit test suite verifying Compose theme color centralization and zero hardcoded
 * color hex literals outside Color.kt (REQ-UI-381, TST-UI-344).
 */
class ThemeColorCentralizationAuditTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    /**
     * TST-UI-344.1: Recursively scans all Kotlin files in app/src/main/java and asserts strictly 0 occurrences
     * of Color(0x outside Color.kt.
     */
    @Test
    fun verifyZeroHexColorsOutsideColorKt() {
        val srcDir = File(projectRoot, "app/src/main/java")
        assertTrue("app/src/main/java must exist", srcDir.isDirectory)

        val violations = mutableListOf<String>()

        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Color.kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        // Match Color(0x with leading or trailing whitespace, ignoring comments that describe tokens
                        if (line.contains("Color(0x") && !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*")) {
                            violations.add("${file.relativeTo(projectRoot).path}:${index + 1}: ${line.trim()}")
                        }
                    }
                }
            }

        assertTrue(
            "Found ${violations.size} hardcoded Color(0x...) literals outside Color.kt:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * TST-UI-344.2: Verifies that TTColor.getClimbGradeColor resolves exact gradient colors
     * across all interior and boundary grade values.
     */
    @Test
    fun verifyClimbGradeColor_allBandsAndBoundaries() {
        // Flat (< 3.0%) -> ClimbGradeFlat
        assertEquals(TTColor.ClimbGradeFlat, TTColor.getClimbGradeColor(-5.0))
        assertEquals(TTColor.ClimbGradeFlat, TTColor.getClimbGradeColor(0.0))
        assertEquals(TTColor.ClimbGradeFlat, TTColor.getClimbGradeColor(2.99))

        // Mild [3.0%, 6.0%) -> ClimbGradeMild
        assertEquals(TTColor.ClimbGradeMild, TTColor.getClimbGradeColor(3.0))
        assertEquals(TTColor.ClimbGradeMild, TTColor.getClimbGradeColor(4.5))
        assertEquals(TTColor.ClimbGradeMild, TTColor.getClimbGradeColor(5.99))

        // Medium [6.0%, 9.0%) -> ClimbGradeMedium
        assertEquals(TTColor.ClimbGradeMedium, TTColor.getClimbGradeColor(6.0))
        assertEquals(TTColor.ClimbGradeMedium, TTColor.getClimbGradeColor(7.5))
        assertEquals(TTColor.ClimbGradeMedium, TTColor.getClimbGradeColor(8.99))

        // Steep [9.0%, 12.0%) -> ClimbGradeSteep
        assertEquals(TTColor.ClimbGradeSteep, TTColor.getClimbGradeColor(9.0))
        assertEquals(TTColor.ClimbGradeSteep, TTColor.getClimbGradeColor(10.5))
        assertEquals(TTColor.ClimbGradeSteep, TTColor.getClimbGradeColor(11.99))

        // Severe [12.0%, 15.0%) -> ClimbGradeSevere
        assertEquals(TTColor.ClimbGradeSevere, TTColor.getClimbGradeColor(12.0))
        assertEquals(TTColor.ClimbGradeSevere, TTColor.getClimbGradeColor(13.5))
        assertEquals(TTColor.ClimbGradeSevere, TTColor.getClimbGradeColor(14.99))

        // Extreme (>= 15.0%) -> ClimbGradeExtreme
        assertEquals(TTColor.ClimbGradeExtreme, TTColor.getClimbGradeColor(15.0))
        assertEquals(TTColor.ClimbGradeExtreme, TTColor.getClimbGradeColor(22.0))
        assertEquals(TTColor.ClimbGradeExtreme, TTColor.getClimbGradeColor(35.0))
    }

    /**
     * TST-UI-344.3: Asserts that ClimbDetailSheet delegates gradient resolution to TTColor.getClimbGradeColor
     * and contains zero duplicate inline slope threshold when blocks.
     */
    @Test
    fun verifyClimbSheetsDelegateToSharedHelper() {
        val climbDetailSheetFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt")
        assertTrue("ClimbDetailSheet.kt must exist", climbDetailSheetFile.exists())

        val content = climbDetailSheetFile.readText()
        assertTrue(
            "ClimbDetailSheet must invoke TTColor.getClimbGradeColor(grade)",
            content.contains("TTColor.getClimbGradeColor(grade)")
        )
        assertTrue(
            "ClimbDetailSheet must not contain duplicated inline gradient when block",
            !content.contains("grade < 3.0 ->")
        )
    }
}
