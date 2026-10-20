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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Component and architectural contract tests for [BatchImportSportTypeDialog]
 * (REQ-MAP-053.6, TST-MAP-056.4).
 */
class BatchImportSportTypeDialogTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val dialogSourceFile: File by lazy {
        File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/BatchImportSportTypeDialog.kt"
        )
    }

    @Test
    fun testBatchSportModeEnum_containsExpectedVariants() {
        val values = BatchSportMode.values().map { it.name }
        assertEquals(2, values.size)
        assertTrue("BatchSportMode must contain ALL", values.contains("ALL"))
        assertTrue("BatchSportMode must contain INDIVIDUAL", values.contains("INDIVIDUAL"))
    }

    @Test
    fun testBatchImportSportTypeDialog_sourceArchitectureAndTokens() {
        assertTrue("BatchImportSportTypeDialog.kt must exist", dialogSourceFile.exists())
        val content = dialogSourceFile.readText()

        // 1. Must use AppModalBottomSheet (replaces legacy AlertDialog)
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet("))

        // 2. Title format specifier
        assertTrue(
            "Must use batch_import_dialog_title_format with routeCount argument",
            content.contains("R.string.batch_import_dialog_title_format, routeCount")
        )

        // 3. Binary mode switcher using SingleChoiceSegmentedButtonRow
        assertTrue(
            "Must use SingleChoiceSegmentedButtonRow (Design Guidelines Section 1.1)",
            content.contains("SingleChoiceSegmentedButtonRow(")
        )
        assertTrue(
            "Segment 0 must use batch_import_mode_all",
            content.contains("R.string.batch_import_mode_all")
        )
        assertTrue(
            "Segment 1 must use batch_import_mode_individual",
            content.contains("R.string.batch_import_mode_individual")
        )
        assertTrue(
            "Must use SegmentedButtonDefaults.itemShape for rounded boundary pills",
            content.contains("SegmentedButtonDefaults.itemShape(")
        )
        assertTrue(
            "Must configure multi-line text with maxLines = 2 and labelMedium (REQ-MAP-053.6.2)",
            content.contains("maxLines = 2") && content.contains("labelMedium")
        )
        assertFalse(
            "Must not enforce rigid softWrap = false on segmented buttons",
            content.contains("softWrap = false")
        )

        // 4. Sport Type Selection Chips in ALL mode using FlowRow and defaulting to UNKNOWN
        assertTrue(
            "Must render FilterChip items for sport types in ALL mode",
            content.contains("FilterChip(")
        )
        assertTrue(
            "Must use FlowRow for sport chips (Rule 54 / REQ-MAP-053.6.3)",
            content.contains("FlowRow(")
        )
        assertTrue(
            "Must default preselection to BSportType.UNKNOWN (REQ-MAP-053.6.4)",
            content.contains("mutableStateOf(BSportType.UNKNOWN)")
        )
        assertTrue(
            "Must include BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN",
            content.contains("BSportType.UNKNOWN") &&
                    content.contains("BSportType.BIKE") &&
                    content.contains("BSportType.RUN")
        )

        // 5. Explanatory hint in INDIVIDUAL mode
        assertTrue(
            "Must display batch_import_individual_hint when in INDIVIDUAL mode",
            content.contains("R.string.batch_import_individual_hint")
        )

        // 6. Action buttons
        assertTrue(
            "Confirm button must switch between batch_import_action_import and batch_import_action_continue",
            content.contains("R.string.batch_import_action_import") &&
                    content.contains("R.string.batch_import_action_continue")
        )
        assertTrue(
            "Dismiss button must use cancel string",
            content.contains("R.string.cancel")
        )

        // 7. Light and Dark Theme Previews
        assertTrue("Must provide Light theme preview", content.contains("@Preview"))
        assertTrue("Must provide Dark theme preview", content.contains("UI_MODE_NIGHT_YES"))
    }

    @Test
    fun testAllRequiredBatchImportStrings_existInValues() {
        val stringsFile = File(projectRoot, "app/src/main/res/values/strings.xml")
        assertTrue("values/strings.xml must exist", stringsFile.exists())
        val xml = stringsFile.readText()

        val expectedKeys = listOf(
            "batch_import_dialog_title_format",
            "batch_import_mode_all",
            "batch_import_mode_individual",
            "batch_import_individual_hint",
            "batch_import_action_import",
            "batch_import_action_continue",
            "batch_import_individual_progress_format",
            "batch_import_skip_route"
        )

        for (key in expectedKeys) {
            val pattern = Regex("<string name=\"$key\">([^<]+)</string>")
            val match = pattern.find(xml)
            assertNotNull("Key '$key' must exist in values/strings.xml", match)
            assertTrue("Key '$key' must not be blank", match!!.groupValues[1].isNotBlank())
        }
    }
}
