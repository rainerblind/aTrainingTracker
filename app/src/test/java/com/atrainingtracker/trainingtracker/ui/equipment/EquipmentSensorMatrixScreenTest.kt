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

package com.atrainingtracker.trainingtracker.ui.equipment

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual design, column alignment, and spacing verification for EquipmentSensorMatrixScreen
 * (REQ-UI-263, TST-UI-222, ATT-2306).
 */
class EquipmentSensorMatrixScreenTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testMetricsAndConstantsContract() {
        // Verify internal layout constants match REQ-UI-263 specification
        assertEquals("STICKY_COLUMN_WIDTH must be 184.dp", 184.dp, STICKY_COLUMN_WIDTH)
        assertEquals("SENSOR_COLUMN_WIDTH must be 88.dp", 88.dp, SENSOR_COLUMN_WIDTH)
        assertEquals("ROW_HEIGHT must be 56.dp", 56.dp, ROW_HEIGHT)
        assertEquals("SECTION_HEADER_HEIGHT must be 36.dp", 36.dp, SECTION_HEADER_HEIGHT)
        assertEquals("HEADER_ROW_HEIGHT must be 60.dp", 60.dp, HEADER_ROW_HEIGHT)
    }

    @Test
    fun testTopLeftCornerHeaderContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // Verify top-left cell uses R.string.Equipment instead of redundant tab name
        assertTrue(
            "Top-left corner cell must display R.string.Equipment",
            content.contains("R.string.Equipment")
        )
    }

    @Test
    fun testContinuousVerticalDividerContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // Section header must contain sticky column width Box/Row and 1dp divider
        assertTrue(
            "MatrixSectionHeader must define STICKY_COLUMN_WIDTH partition",
            content.contains("Modifier\n                    .width(STICKY_COLUMN_WIDTH)") ||
            content.contains(".width(STICKY_COLUMN_WIDTH)")
        )

        // Count occurrences of vertical divider (top header, section header, equipment row)
        val dividerSnippet = "outlineVariant.copy(alpha = 0.6f)"
        val occurrences = content.split(dividerSnippet).size - 1
        assertTrue(
            "Continuous 1dp vertical divider (alpha = 0.6f) must be present in header, section header, and rows (found $occurrences)",
            occurrences >= 3
        )
    }

    @Test
    fun testEquipmentNameTwoLineWrappingContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // Verify maxLines = 2 in MatrixEquipmentRow for equipment item name
        assertTrue(
            "Equipment name Text composable must support maxLines = 2",
            content.contains("maxLines = 2")
        )
        assertTrue(
            "Equipment name Text composable must configure 18.sp line height",
            content.contains("lineHeight = 18.sp")
        )
    }

    @Test
    fun testSensorColumnSubtleGuidesContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // Verify subtle column guides (alpha = 0.2f)
        val guideSnippet = "outlineVariant.copy(alpha = 0.2f)"
        val occurrences = content.split(guideSnippet).size - 1
        assertTrue(
            "Subtle vertical guides (alpha = 0.2f) must be present in both header and rows (found $occurrences)",
            occurrences >= 2
        )
    }

    @Test
    fun testLocalizationParity_equipmentTitleAcrossAllNineLocales() {
        val localeDirs = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        for (locale in localeDirs) {
            val stringsFile = findFile("src/main/res/$locale/strings.xml")
            assertTrue("File must exist: ${stringsFile.path}", stringsFile.exists())
            val xmlContent = stringsFile.readText()

            assertTrue(
                "Locale '$locale' must contain string resource 'Equipment'",
                xmlContent.contains("name=\"Equipment\"")
            )
        }
    }

    @Test
    fun testEquipmentIconTintContract_unifiesActiveBikeAndShoeToPrimary() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // Extract MatrixEquipmentRow block to verify tint assignment
        val rowStartIndex = content.indexOf("MatrixEquipmentRow(")
        assertTrue("MatrixEquipmentRow composable must exist", rowStartIndex != -1)
        val rowEndIndex = content.indexOf("fun EquipmentSportSensorMatrix", rowStartIndex)
        assertTrue("EquipmentSportSensorMatrix must exist after MatrixEquipmentRow", rowEndIndex != -1)
        val rowBlock = content.substring(rowStartIndex, rowEndIndex)

        // Assert that active equipment icons use MaterialTheme.colorScheme.primary
        assertTrue(
            "MatrixEquipmentRow must assign MaterialTheme.colorScheme.primary for active equipment",
            rowBlock.contains("else MaterialTheme.colorScheme.primary")
        )

        // Assert that MaterialTheme.colorScheme.secondary is NOT used for equipment icon tinting
        org.junit.Assert.assertFalse(
            "MatrixEquipmentRow must not tint shoe icons with secondary (REQ-UI-288, ATT-2630)",
            rowBlock.contains("MaterialTheme.colorScheme.secondary")
        )

        // Assert that retired items preserve outline tint
        assertTrue(
            "MatrixEquipmentRow must assign MaterialTheme.colorScheme.outline for retired equipment",
            rowBlock.contains("if (item.isRetired) MaterialTheme.colorScheme.outline")
        )
    }
}

