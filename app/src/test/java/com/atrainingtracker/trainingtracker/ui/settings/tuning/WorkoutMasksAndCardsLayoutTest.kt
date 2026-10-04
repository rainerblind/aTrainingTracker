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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Layout, typography, and reordering ergonomics verification for WorkoutMasksAndCardsSection
 * (REQ-UI-264, TST-UI-223, ATT-2305).
 */
class WorkoutMasksAndCardsLayoutTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testReorderControlsAndCheckboxWidthsContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // 1. Reorder controls must use a compact 32.dp vertical Column
        assertTrue(
            "Reorder controls must use width(32.dp)",
            content.contains(".width(32.dp)")
        )

        // 2. Header spacer must match reorder controls width (32.dp)
        assertTrue(
            "Header spacer must be 32.dp",
            content.contains("Spacer(modifier = Modifier.width(32.dp))")
        )

        // 3. Checkbox columns must be 50.dp in width
        assertTrue(
            "Checkbox header containers must use width(50.dp)",
            content.contains(".width(50.dp)")
        )
        assertTrue(
            "Row checkbox containers must use 50.dp width",
            content.contains(".size(50.dp, 44.dp)")
        )
    }

    @Test
    fun testTypographyAndWrappingContract() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // Verify title Text supports maxLines = 2, TextOverflow.Ellipsis, and lineHeight = 18.sp
        assertTrue(
            "Section title Text must configure maxLines = 2",
            content.contains("maxLines = 2")
        )
        assertTrue(
            "Section title Text must configure TextOverflow.Ellipsis",
            content.contains("overflow = TextOverflow.Ellipsis")
        )
        assertTrue(
            "Section title Text must configure lineHeight = 18.sp",
            content.contains("lineHeight = 18.sp")
        )
    }

    @Test
    fun testDecoupledLapDisplayModeSubControl() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // LapDisplayMode sub-control must NOT be nested inside MatrixFeatureRow
        assertFalse(
            "MatrixFeatureRow must not have isLaps flag",
            content.contains("isLaps = true") || content.contains("val isLaps: Boolean")
        )

        // SegmentedButton must be rendered outside the row loop
        assertTrue(
            "SegmentedButton must exist for lap display mode",
            content.contains("SingleChoiceSegmentedButtonRow(") &&
            content.contains("SegmentedButton(")
        )
    }

    @Test
    fun testFileSizeConstraint_strictlyUnder400Lines() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val lineCount = file.readLines().size

        assertTrue(
            "WorkoutMasksAndCardsSection.kt line count must be strictly < 400 lines (current: $lineCount)",
            lineCount < 400
        )
    }
}
