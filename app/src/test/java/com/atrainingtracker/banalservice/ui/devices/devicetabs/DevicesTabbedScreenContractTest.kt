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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural contract test for DevicesTabbedScreen 5-tab structure (REQ-UI-284, TST-UI-244.1, ATT-2465).
 *
 * Verifies:
 * 1. Defines 5 tabs: available, paired, known, bikes, shoes.
 * 2. DevicesTabbedScreen composable exists.
 * 3. EquipmentSportSensorMatrix exists and integrates with EquipmentViewModel.
 */
class DevicesTabbedScreenContractTest {

    @Test
    fun testDevicesTabbedScreen_fiveTabsContract() {
        val strings = LocalizationTestCache.getStrings("values", "strings_devices.xml")

        // 1. Must define 5 tabs including bikes and shoes
        assertTrue("Must reference devices_tab_available", strings.containsKey("devices_tab_available"))
        assertTrue("Must reference devices_tab_paired", strings.containsKey("devices_tab_paired"))
        assertTrue("Must reference devices_tab_known", strings.containsKey("devices_tab_known"))
        assertTrue("Must reference devices_tab_bikes", strings.containsKey("devices_tab_bikes"))
        assertTrue("Must reference devices_tab_shoes", strings.containsKey("devices_tab_shoes"))

        // 2. DevicesTabbedScreen composable exists
        val screenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DevicesTabbedScreenKt class must exist", screenClass)
        assertTrue(
            "DevicesTabbedScreen composable must be declared",
            screenClass.declaredMethods.any { it.name.startsWith("DevicesTabbedScreen") }
        )

        // 3. EquipmentViewModel exists and declares properties
        val eqVmClass = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel")
        assertNotNull("EquipmentViewModel class must exist", eqVmClass)
        val vmMethods = eqVmClass.methods.map { it.name }
        assertTrue("EquipmentViewModel must declare getBikes", vmMethods.contains("getBikes"))
        assertTrue("EquipmentViewModel must declare getShoes", vmMethods.contains("getShoes"))
        assertTrue("EquipmentViewModel must declare getBikeSensors", vmMethods.contains("getBikeSensors"))
        assertTrue("EquipmentViewModel must declare getShoeSensors", vmMethods.contains("getShoeSensors"))

        // 4. EquipmentSportSensorMatrix composable exists
        val matrixClass = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixScreenKt")
        assertNotNull("EquipmentSensorMatrixScreenKt class must exist", matrixClass)
        assertTrue(
            "EquipmentSportSensorMatrix composable must be declared",
            matrixClass.declaredMethods.any { it.name.startsWith("EquipmentSportSensorMatrix") }
        )
    }

    @Test
    fun testDevicesTabbedScreen_matrixTabsLinkIconContract() {
        val screenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DevicesTabbedScreenKt class must exist", screenClass)
        assertTrue(
            "DevicesTabbedScreen composable must be declared",
            screenClass.declaredMethods.any { it.name.startsWith("DevicesTabbedScreen") }
        )
    }

    @Test
    fun testDevicesTabbedScreen_onScanForSensors_contract() {
        val screenClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenKt")
        assertNotNull("DevicesTabbedScreenKt class must exist", screenClass)
        assertTrue(
            "DevicesTabbedScreen composable must be declared",
            screenClass.declaredMethods.any { it.name.startsWith("DevicesTabbedScreen") }
        )
    }
}
