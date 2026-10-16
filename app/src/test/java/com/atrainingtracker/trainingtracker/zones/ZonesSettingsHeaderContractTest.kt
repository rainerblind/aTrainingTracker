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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for ZonesSettingsActivity header row.
 *
 * Enforces [REQ-UI-378], [REQ-UI-385], and [TST-UI-348]:
 * 1. Single compact Calculate IconButton (`Icons.Default.Calculate`).
 * 2. Complete removal of status SuggestionChip, RestartAlt reset action, last-updated row, and athlete weight row.
 * 3. Bounded title container with maxLines = 1 and TextOverflow.Ellipsis.
 */
class ZonesSettingsHeaderContractTest {

    private fun resolveActivityFile(): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        val root = current ?: File(".")
        return File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
    }

    @Test
    fun testZonesSettingsActivityFileExists() {
        val activityFile = resolveActivityFile()
        assertTrue("ZonesSettingsActivity.kt must exist", activityFile.exists())
    }

    @Test
    fun testHeaderUsesCompactIconButtonsInsteadOfTextButtons() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()

        // Extract the header block: between "THE HEADER" and the PrimaryScrollableTabRow
        val headerStartIndex = content.indexOf("// 1. THE HEADER")
        assertTrue("Header comment must exist in ZonesSettingsActivity.kt", headerStartIndex != -1)
        val headerEndIndex = content.indexOf("PrimaryScrollableTabRow(", headerStartIndex)
        assertTrue("PrimaryScrollableTabRow marker must exist in ZonesSettingsActivity.kt", headerEndIndex != -1)

        val headerBlock = content.substring(headerStartIndex, headerEndIndex)

        // Must NOT use TextButton in the header block
        assertFalse(
            "Header block must not contain TextButton calls (REQ-UI-378.1 / REQ-UI-385.2)",
            headerBlock.contains("TextButton")
        )

        // Must use IconButton and Calculate icon
        assertTrue(
            "Header block must contain IconButton call per REQ-UI-385.1",
            headerBlock.contains("IconButton")
        )
        assertTrue(
            "Header block must use Icons.Default.Calculate per REQ-UI-385.1",
            headerBlock.contains("Icons.Default.Calculate")
        )
        assertTrue(
            "Header block must reference R.string.zone_calc_button per REQ-UI-385.1",
            headerBlock.contains("R.string.zone_calc_button")
        )

        // Must NOT contain removed header elements per REQ-UI-385.2
        assertFalse(
            "Header block must not contain SuggestionChip per REQ-UI-385.2",
            headerBlock.contains("SuggestionChip")
        )
        assertFalse(
            "Header block must not contain Icons.Default.RestartAlt per REQ-UI-385.2",
            headerBlock.contains("Icons.Default.RestartAlt")
        )
        assertFalse(
            "Header block must not contain R.string.reset_to_defaults per REQ-UI-385.2",
            headerBlock.contains("reset_to_defaults")
        )
        assertFalse(
            "Header block must not contain zone_calc_status_customized per REQ-UI-385.2",
            headerBlock.contains("zone_calc_status_customized")
        )
        assertFalse(
            "Header block must not contain zone_calc_status_calculated per REQ-UI-385.2",
            headerBlock.contains("zone_calc_status_calculated")
        )
        assertFalse(
            "Header block must not contain zone_calc_last_updated per REQ-UI-385.2",
            headerBlock.contains("zone_calc_last_updated")
        )
        assertFalse(
            "Header block must not contain zone_calc_calibration_recommended per REQ-UI-385.2",
            headerBlock.contains("zone_calc_calibration_recommended")
        )
        assertFalse(
            "Header block must not contain athlete_weight_title per REQ-UI-385.2",
            headerBlock.contains("athlete_weight_title")
        )
        assertFalse(
            "Header block must not contain athlete_weight_edit per REQ-UI-385.2",
            headerBlock.contains("athlete_weight_edit")
        )
        assertFalse(
            "ZonesSettingsActivity.kt must not contain showAthleteWeightDialog per REQ-UI-385.2",
            content.contains("showAthleteWeightDialog")
        )
        assertFalse(
            "ZonesSettingsActivity.kt must not collect athleteWeightKgFlow per REQ-UI-385.2",
            content.contains("athleteWeightKgFlow")
        )
        assertFalse(
            "ZonesSettingsActivity.kt must not collect ftpUpdatedAtFlow per REQ-UI-385.2",
            content.contains("ftpUpdatedAtFlow")
        )
        assertFalse(
            "ZonesSettingsActivity.kt must not call resetToDefaults per REQ-UI-385.2",
            content.contains("resetToDefaults")
        )
        assertTrue(
            "ZonesSettingsActivity.kt must preserve ZoneCalculatorStateHolder and ZoneCalculatorDialog per REQ-UI-385.3",
            content.contains("ZoneCalculatorStateHolder(") && content.contains("ZoneCalculatorDialog(")
        )
    }

    @Test
    fun testHeaderTitleIsBoundedWithMaxLinesAndEllipsis() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()
        val headerStartIndex = content.indexOf("// 1. THE HEADER")
        val headerEndIndex = content.indexOf("PrimaryScrollableTabRow(", headerStartIndex)
        val headerBlock = content.substring(headerStartIndex, headerEndIndex)

        assertTrue(
            "Title Text in header must declare maxLines = 1 per REQ-UI-385.1",
            headerBlock.contains("maxLines = 1")
        )
        assertTrue(
            "Title Text in header must declare overflow = TextOverflow.Ellipsis per REQ-UI-385.1",
            headerBlock.contains("overflow = TextOverflow.Ellipsis")
        )
        assertTrue(
            "Left title container in header must declare weight(1f, fill = false) per REQ-UI-385.1",
            headerBlock.contains("weight(1f, fill = false)")
        )
    }
}

