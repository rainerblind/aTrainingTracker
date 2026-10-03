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
    fun testEquipmentTabsScreen_threeTabsAndFabGatingContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val content = file.readText()

        // 1. Must define 3 tabs including sensor matrix
        assertTrue(
            "EquipmentTabsScreen must reference R.string.equipment_tab_sensor_matrix",
            content.contains("R.string.equipment_tab_sensor_matrix")
        )
        assertTrue(
            "EquipmentTabsScreen must reference R.string.equipment_type_bike",
            content.contains("R.string.equipment_type_bike")
        )
        assertTrue(
            "EquipmentTabsScreen must reference R.string.equipment_type_shoe",
            content.contains("R.string.equipment_type_shoe")
        )

        // 2. Must collect allRemoteSensors
        assertTrue(
            "EquipmentTabsScreen must collect allRemoteSensors state",
            content.contains("viewModel.allRemoteSensors.collectAsState()")
        )

        // 3. Must render EquipmentSensorMatrixScreen on page 2
        assertTrue(
            "EquipmentTabsScreen must invoke EquipmentSensorMatrixScreen",
            content.contains("EquipmentSensorMatrixScreen(")
        )

        // 4. Must gate FAB on page 2
        assertTrue(
            "EquipmentTabsScreen must suppress FAB on tab 2",
            content.contains("pagerState.currentPage != 2")
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
}
