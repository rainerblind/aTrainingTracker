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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for DevicesTabbedScreen 5-tab structure (REQ-UI-284, TST-UI-244.1, ATT-2465).
 *
 * Verifies:
 * 1. Defines 5 tabs: available, paired, known, bikes, shoes.
 * 2. Uses PrimaryScrollableTabRow to prevent text clipping and wrapping.
 * 3. Maps pages 3 and 4 to EquipmentSportSensorMatrix with independent scroll states.
 * 4. Accepts EquipmentViewModel.
 */
class DevicesTabbedScreenContractTest {

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
    fun testDevicesTabbedScreen_fiveTabsContract() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        val content = file.readText()

        // 1. Must define 5 tabs including bikes and shoes
        assertTrue(
            "DevicesTabbedScreen must reference R.string.devices_tab_available",
            content.contains("R.string.devices_tab_available")
        )
        assertTrue(
            "DevicesTabbedScreen must reference R.string.devices_tab_paired",
            content.contains("R.string.devices_tab_paired")
        )
        assertTrue(
            "DevicesTabbedScreen must reference R.string.devices_tab_known",
            content.contains("R.string.devices_tab_known")
        )
        assertTrue(
            "DevicesTabbedScreen must reference R.string.devices_tab_bikes",
            content.contains("R.string.devices_tab_bikes")
        )
        assertTrue(
            "DevicesTabbedScreen must reference R.string.devices_tab_shoes",
            content.contains("R.string.devices_tab_shoes")
        )

        // 2. Must use PrimaryScrollableTabRow
        assertTrue(
            "DevicesTabbedScreen must use PrimaryScrollableTabRow",
            content.contains("PrimaryScrollableTabRow(")
        )

        // 3. Must accept EquipmentViewModel parameter
        assertTrue(
            "DevicesTabbedScreen must accept equipmentViewModel",
            content.contains("equipmentViewModel: EquipmentViewModel = viewModel()")
        )

        // 4. Must collect bikes, shoes, and sport-specific sensors
        assertTrue(
            "DevicesTabbedScreen must collect bikes state",
            content.contains("equipmentViewModel.bikes.collectAsState()")
        )
        assertTrue(
            "DevicesTabbedScreen must collect shoes state",
            content.contains("equipmentViewModel.shoes.collectAsState()")
        )
        assertTrue(
            "DevicesTabbedScreen must collect bikeSensors state",
            content.contains("equipmentViewModel.bikeSensors.collectAsState()")
        )
        assertTrue(
            "DevicesTabbedScreen must collect shoeSensors state",
            content.contains("equipmentViewModel.shoeSensors.collectAsState()")
        )

        // 5. Must instantiate independent horizontal scroll states
        assertTrue(
            "DevicesTabbedScreen must declare bikesHorizontalScrollState",
            content.contains("val bikesHorizontalScrollState = rememberScrollState()")
        )
        assertTrue(
            "DevicesTabbedScreen must declare shoesHorizontalScrollState",
            content.contains("val shoesHorizontalScrollState = rememberScrollState()")
        )

        // 6. Must map page 3 to bikes EquipmentSportSensorMatrix
        assertTrue(
            "DevicesTabbedScreen must map page 3 to EquipmentSportSensorMatrix with isBike = true",
            content.contains("3 -> EquipmentSportSensorMatrix(") &&
            content.contains("isBike = true")
        )

        // 7. Must map page 4 to shoes EquipmentSportSensorMatrix
        assertTrue(
            "DevicesTabbedScreen must map page 4 to EquipmentSportSensorMatrix with isBike = false",
            content.contains("4 -> EquipmentSportSensorMatrix(") &&
            content.contains("isBike = false")
        )
    }
}
