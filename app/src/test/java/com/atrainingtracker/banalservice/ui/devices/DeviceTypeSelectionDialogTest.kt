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

package com.atrainingtracker.banalservice.ui.devices

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for DeviceTypeSelectionDialog (REQ-UI-290, TST-UI-250, ATT-2625).
 * Verifies that the dialog defaults to omitting the "Alle" button (showAllOption = false)
 * during sensor pairing flows while preserving targeted device profile resolution.
 */
class DeviceTypeSelectionDialogTest {

    private val sourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt"),
            File("app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialog.kt")
    }

    @Test
    fun testSourceFileExists() {
        assertTrue("DeviceTypeSelectionDialog.kt source file must exist", sourceFile.exists())
    }

    @Test
    fun testDeviceTypeSelectionDialog_hasShowAllOptionDefaultParameter() {
        val content = sourceFile.readText()

        // 1. Must define showAllOption parameter with default false
        assertTrue(
            "DeviceTypeSelectionDialog must declare optional parameter showAllOption: Boolean = false",
            content.contains("showAllOption: Boolean = false")
        )
    }

    @Test
    fun testDeviceTypeSelectionDialog_conditionallyRendersActionsSlot() {
        val content = sourceFile.readText()

        // 2. Must conditionally pass actions slot based on showAllOption
        assertTrue(
            "DeviceTypeSelectionDialog must branch actions slot on showAllOption",
            content.contains("actions = if (showAllOption)")
        )
        assertTrue(
            "DeviceTypeSelectionDialog must pass null actions when showAllOption is false",
            content.contains("else null")
        )
    }

    @Test
    fun testDeviceTypeResolution_antPlusRemoteTypesAreNonEmpty() {
        val antPlusTypes = DeviceType.getRemoteDeviceTypes(Protocol.ANT_PLUS)
        assertTrue("ANT+ remote device types must not be empty", antPlusTypes.isNotEmpty())
        assertTrue("ANT+ remote device types must contain HRM", antPlusTypes.contains(DeviceType.HRM))
        assertFalse("ANT+ remote device types must NOT contain DeviceType.ALL in remote listing", antPlusTypes.contains(DeviceType.ALL))
    }

    @Test
    fun testDeviceTypeResolution_bleRemoteTypesAreNonEmpty() {
        val bleTypes = DeviceType.getRemoteDeviceTypes(Protocol.BLUETOOTH_LE)
        assertTrue("BLE remote device types must not be empty", bleTypes.isNotEmpty())
        assertTrue("BLE remote device types must contain HRM", bleTypes.contains(DeviceType.HRM))
        assertFalse("BLE remote device types must NOT contain DeviceType.ALL in remote listing", bleTypes.contains(DeviceType.ALL))
    }
}
