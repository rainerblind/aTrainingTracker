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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and localization parity verification for EquipmentSensorMatrixScreen
 * and EquipmentTabsScreen integration (REQ-UI-256, TST-UI-215, ATT-2126).
 */
class EquipmentSensorMatrixContractTest {

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
    fun testEquipmentTabsScreen_twoTabsAndUnconditionalFabContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val content = file.readText()

        // 1. Must define exactly 2 tabs (Bikes and Shoes); must NOT contain sensor matrix tab
        assertTrue(
            "EquipmentTabsScreen must reference R.string.equipment_type_bike",
            content.contains("R.string.equipment_type_bike")
        )
        assertTrue(
            "EquipmentTabsScreen must reference R.string.equipment_type_shoe",
            content.contains("R.string.equipment_type_shoe")
        )
        assertTrue(
            "EquipmentTabsScreen must NOT reference R.string.equipment_tab_sensor_matrix",
            !content.contains("R.string.equipment_tab_sensor_matrix")
        )

        // 2. Must NOT invoke EquipmentSensorMatrixScreen
        assertTrue(
            "EquipmentTabsScreen must NOT invoke EquipmentSensorMatrixScreen",
            !content.contains("EquipmentSensorMatrixScreen(")
        )

        // 3. Must NOT gate FAB on page 2 (FAB active on all tabs)
        assertTrue(
            "EquipmentTabsScreen must NOT suppress FAB on any tab",
            !content.contains("pagerState.currentPage != 2")
        )
    }

    @Test
    fun testEquipmentSensorMatrixScreen_structuralContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // 1. Signature components
        assertTrue(
            "EquipmentSensorMatrixScreen must accept bikes, shoes, sensors, onToggleLink",
            content.contains("bikes: List<EquipmentItem>") &&
            content.contains("shoes: List<EquipmentItem>") &&
            content.contains("sensors: List<SimpleSensorInfo>") &&
            content.contains("onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit")
        )

        // 2. Sticky header and sticky column
        assertTrue(
            "Must use stickyHeader for sensor names",
            content.contains("stickyHeader")
        )
        assertTrue(
            "Must define STICKY_COLUMN_WIDTH",
            content.contains("STICKY_COLUMN_WIDTH")
        )

        // 3. Horizontal scroll synchronization
        assertTrue(
            "Must use rememberScrollState and horizontalScroll modifier",
            content.contains("rememberScrollState()") && content.contains("horizontalScroll(")
        )

        // 4. Section headers for Bikes and Shoes
        assertTrue(
            "Must render Bikes section header",
            content.contains("R.string.equipment_type_bike")
        )
        assertTrue(
            "Must render Shoes section header",
            content.contains("R.string.equipment_type_shoe")
        )

        // 5. Material 3 Checkbox with toggle trigger
        assertTrue(
            "Must render Checkbox with checked state and onCheckedChange",
            content.contains("Checkbox(") && content.contains("onToggleLink(")
        )

        // 6. Empty states
        assertTrue(
            "Must handle empty sensors state",
            content.contains("R.string.equipment_matrix_no_sensors")
        )
        assertTrue(
            "Must handle empty equipment state",
            content.contains("R.string.equipment_matrix_no_equipment")
        )
    }

    @Test
    fun testLocalizationParity_allNineLocalesMustContainNewStringKeys() {
        val requiredKeys = listOf(
            "equipment_tab_sensor_matrix",
            "equipment_matrix_no_sensors",
            "equipment_matrix_no_equipment"
        )

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

            for (key in requiredKeys) {
                assertTrue(
                    "Locale '$locale' must contain string resource '$key'",
                    xmlContent.contains("name=\"$key\"")
                )
            }
        }
    }

    @Test
    fun testEquipmentSensorOrderingContract_screenAndDialogMustApplyPrioritizedOrdering() {
        val matrixFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val matrixContent = matrixFile.readText()
        assertTrue(
            "EquipmentSensorMatrixScreen must apply EquipmentSensorOrdering.sortSensors in fallback path",
            matrixContent.contains("EquipmentSensorOrdering.sortSensors")
        )

        val dialogFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt")
        val dialogContent = dialogFile.readText()
        assertTrue(
            "EditEquipmentDialog must apply EquipmentSensorOrdering.sortSensors for compatible sensors",
            dialogContent.contains("EquipmentSensorOrdering.sortSensors")
        )
    }

    @Test
    fun testEquipmentSportSensorMatrix_structuralContract() {
        val matrixFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val matrixContent = matrixFile.readText()

        // 1. Signature components of EquipmentSportSensorMatrix
        assertTrue(
            "EquipmentSensorMatrixScreen must define EquipmentSportSensorMatrix composable",
            matrixContent.contains("fun EquipmentSportSensorMatrix(")
        )
        assertTrue(
            "EquipmentSportSensorMatrix must accept items, sensors, isBike, onToggleLink",
            matrixContent.contains("items: List<EquipmentItem>") &&
            matrixContent.contains("sensors: List<SimpleSensorInfo>") &&
            matrixContent.contains("isBike: Boolean") &&
            matrixContent.contains("onToggleLink: (equipmentId: Long, sensorId: Long, isLinked: Boolean) -> Unit")
        )

        // 2. Uses stickyHeader and STICKY_COLUMN_WIDTH
        assertTrue(
            "EquipmentSportSensorMatrix must use stickyHeader",
            matrixContent.contains("stickyHeader(key = \"table_header_\")") || matrixContent.contains("stickyHeader(")
        )
        assertTrue(
            "EquipmentSportSensorMatrix must use STICKY_COLUMN_WIDTH",
            matrixContent.contains("STICKY_COLUMN_WIDTH")
        )

        // 3. FastScrollableBox integration
        assertTrue(
            "EquipmentSportSensorMatrix must wrap in FastScrollableBox",
            matrixContent.contains("FastScrollableBox(")
        )

        // 4. Material 3 Checkbox with toggle trigger
        assertTrue(
            "EquipmentSportSensorMatrix must render Checkbox with onToggleLink",
            matrixContent.contains("Checkbox(") && matrixContent.contains("onToggleLink(")
        )
    }
}
