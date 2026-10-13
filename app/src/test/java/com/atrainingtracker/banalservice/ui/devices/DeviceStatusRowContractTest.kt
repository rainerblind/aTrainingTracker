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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying DeviceStatusRow connection parameterization
 * and backward compatibility (REQ-UI-268, TST-UI-227.1, TST-UI-227.2, TST-UI-227.3, ATT-2194).
 */
class DeviceStatusRowContractTest {

    private val sourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRow.kt"),
            File("app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRow.kt"),
            File("../app/src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRow.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/banalservice/ui/devices/DeviceStatusRow.kt")
    }

    @Test
    fun testSourceFileExists() {
        assertTrue("DeviceStatusRow.kt source file must exist", sourceFile.exists())
    }

    @Test
    fun testDeviceStatusRow_hasIsConnectedDefaultParameter() {
        val content = sourceFile.readText()

        // 1. Must define isConnected with default value device.isConnected
        assertTrue(
            "DeviceStatusRow must declare optional parameter isConnected: Boolean = device.isConnected",
            content.contains("isConnected: Boolean = device.isConnected")
        )
    }

    @Test
    fun testDeviceStatusRow_evaluatesIsConnectedForStateText() {
        val content = sourceFile.readText()

        // 2. Must evaluate if (isConnected) instead of hardcoding if (device.isConnected)
        assertTrue(
            "DeviceStatusRow must evaluate stateText using if (isConnected)",
            content.contains("if (isConnected)")
        )

        // Must NOT use 'if (device.isConnected)' in stateText calculation
        assertFalse(
            "DeviceStatusRow must not hardcode device.isConnected in stateText condition",
            content.contains("if (device.isConnected)")
        )

        // 3. Must reference R.string.devices_available and R.string.devices_not_connected
        assertTrue(
            "DeviceStatusRow must reference R.string.devices_available",
            content.contains("R.string.devices_available")
        )
        assertTrue(
            "DeviceStatusRow must reference R.string.devices_not_connected",
            content.contains("R.string.devices_not_connected")
        )
    }
}
