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
 * (REQ-UI-264, REQ-UI-414, TST-UI-223, TST-UI-377, ATT-2305, ATT-4043).
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

        // 3. Checkbox column must be 50.dp in width
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

    /**
     * TST-UI-377.2: Single-Column Checkbox & Layout Structure Contract Test.
     * Verifies removal of redundant Details column and header (REQ-UI-414.1).
     */
    @Test
    fun `TST-UI-377_2 single-column checkbox table contract and details column removal`() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // Verify tuning_matrix_col_list text composable enforces defensive single-line typography (REQ-UI-271)
        val listHeaderIndex = content.indexOf("R.string.tuning_matrix_col_list")
        assertTrue("Header must reference tuning_matrix_col_list", listHeaderIndex >= 0)
        val listHeaderBlock = content.substring(listHeaderIndex, listHeaderIndex + 450)
        assertTrue(
            "List column header must enforce textAlign = TextAlign.Center",
            listHeaderBlock.contains("textAlign = TextAlign.Center")
        )
        assertTrue(
            "List column header must enforce maxLines = 1",
            listHeaderBlock.contains("maxLines = 1")
        )
        assertTrue(
            "List column header must enforce overflow = TextOverflow.Ellipsis",
            listHeaderBlock.contains("overflow = TextOverflow.Ellipsis")
        )

        // Redundant details column header must NOT be present in WorkoutMasksAndCardsSection.kt (REQ-UI-414.1)
        assertFalse(
            "WorkoutMasksAndCardsSection.kt must NOT reference tuning_matrix_col_details",
            content.contains("R.string.tuning_matrix_col_details")
        )

        // Feature row must contain single checkbox (feature.listChecked) and no feature.detailChecked
        assertTrue(
            "Row must bind feature.listChecked",
            content.contains("Checkbox(checked = feature.listChecked, onCheckedChange = feature.onListChange)")
        )
        assertFalse(
            "Row must NOT bind feature.detailChecked",
            content.contains("feature.detailChecked")
        )
    }

    /**
     * TST-UI-377.3: Collapsible Summary Reorder List Contract Test (REQ-UI-414.3).
     */
    @Test
    fun `TST-UI-377_3 collapsible summary reorder list contract`() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // 1. Collapsed by default state
        assertTrue(
            "isSummaryListExpanded must default to false",
            content.contains("var isSummaryListExpanded by remember { mutableStateOf(false) }")
        )

        // 2. Expand/collapse chevron icons
        assertTrue(
            "Must use KeyboardArrowUp and KeyboardArrowDown for toggle",
            content.contains("Icons.Default.KeyboardArrowUp") && content.contains("Icons.Default.KeyboardArrowDown")
        )

        // 3. Formatted toggle string with active card count
        assertTrue(
            "Must reference R.string.workout_card_reorder_toggle",
            content.contains("R.string.workout_card_reorder_toggle")
        )
    }

    /**
     * TST-UI-377.4: Scoped Zone Display Mode & Detail Tabs Action Contract Test (REQ-UI-414.4).
     */
    @Test
    fun `TST-UI-377_4 domain-scoped zone display mode and detail tabs config contract`() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt")
        val content = file.readText()

        // Verify domain section titles
        assertTrue(
            "Must display R.string.settings_workout_card_title",
            content.contains("R.string.settings_workout_card_title")
        )
        assertTrue(
            "Must display R.string.settings_workout_details_title",
            content.contains("R.string.settings_workout_details_title")
        )

        // Verify Summary zone selector binds workoutCardPrefs.zoneCardDisplayMode
        assertTrue(
            "Must bind workoutCardPrefs.zoneCardDisplayMode in summary block",
            content.contains("workoutCardPrefs.zoneCardDisplayMode")
        )

        // Verify Details zone selector binds workoutDetailPrefs.zoneCardDisplayMode in details block
        assertTrue(
            "Must bind workoutDetailPrefs.zoneCardDisplayMode in details block",
            content.contains("workoutDetailPrefs.zoneCardDisplayMode")
        )

        // Verify Detail Tabs sheet launcher button
        assertTrue(
            "Must host WorkoutDetailTabsConfigSheet in details block",
            content.contains("WorkoutDetailTabsConfigSheet(")
        )
    }

    /**
     * TST-UI-377.5: Modularity & Strictly Under 400 Lines Constraint (REQ-PRO-011 / REQ-UI-414.5).
     */
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
