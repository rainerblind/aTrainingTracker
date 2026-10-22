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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and UI contract test for DeviceListScreen empty-state search action and scan timeout guard
 * (REQ-SEN-020, TST-SEN-020.4, ATT-4051).
 *
 * Verifies:
 * 1. DeviceListScreen accepts onScanForSensors lambda parameter.
 * 2. Empty-state conditionally evaluates showSearchAction based on !isSearching && filterCriteria.isEmpty.
 * 3. Uses localized strings devices_empty_state_search_hint and devices_action_search_sensors.
 * 4. Renders OutlinedButton with Icons.Default.Add and onScanForSensors callback.
 */
class DeviceListScreenContractTest {

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
    fun testDeviceListScreen_emptyStateActionContract() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt")
        val content = file.readText()

        // 1. Must accept onScanForSensors parameter
        assertTrue(
            "DeviceListScreen must accept onScanForSensors parameter",
            content.contains("onScanForSensors: (() -> Unit)? = null")
        )

        // 2. Must evaluate showSearchAction
        assertTrue(
            "DeviceListScreen must compute showSearchAction based on !isSearching && filterCriteria.isEmpty",
            content.contains("val showSearchAction = !isSearching && filterCriteria.isEmpty")
        )

        // 3. Must reference localized hint and button text
        assertTrue(
            "DeviceListScreen must reference R.string.devices_empty_state_search_hint",
            content.contains("R.string.devices_empty_state_search_hint")
        )
        assertTrue(
            "DeviceListScreen must reference R.string.devices_action_search_sensors",
            content.contains("R.string.devices_action_search_sensors")
        )

        // 4. Must render OutlinedButton with Icons.Default.Add
        assertTrue(
            "DeviceListScreen must reference Icons.Default.Add",
            content.contains("Icons.Default.Add")
        )
        assertTrue(
            "DeviceListScreen must render OutlinedButton with onClick = onScanForSensors",
            content.contains("OutlinedButton(") && content.contains("onClick = onScanForSensors")
        )
    }
}
