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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying SensorSourceDialog live telemetry propagation
 * to DeviceStatusRow across all sensor redundancy groups (REQ-UI-268, TST-UI-227.4, ATT-2194).
 */
class SensorSourceDialogContractTest {

    private val sourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt")
    }

    @Test
    fun testSourceFileExists() {
        assertTrue("SensorSourceDialog.kt source file must exist", sourceFile.exists())
    }

    @Test
    fun testDeviceIdentityBlock_forwardsIsConnectedToDeviceStatusRow() {
        val content = sourceFile.readText()

        // 1. DeviceIdentityBlock must declare isConnected: Boolean parameter
        assertTrue(
            "DeviceIdentityBlock must accept isConnected parameter",
            content.contains("isConnected: Boolean")
        )

        // 2. DeviceIdentityBlock must pass isConnected = isConnected to DeviceStatusRow
        assertTrue(
            "DeviceIdentityBlock must pass isConnected = isConnected to DeviceStatusRow",
            content.contains("DeviceStatusRow(\n                device = device,\n                isConnected = isConnected,") ||
            content.contains("isConnected = isConnected")
        )
    }

    @Test
    fun testSensorSourceDialog_passesIsConnectedTrueForActiveSensors() {
        val content = sourceFile.readText()

        // 3. sourceDevice must pass isConnected = true
        assertTrue(
            "sourceDevice must invoke DeviceIdentityBlock with isConnected = true",
            content.contains("device = sourceDevice, \n                        isConnected = true,") ||
            content.contains("device = sourceDevice,\n                        isConnected = true") ||
            content.contains("device = sourceDevice, \n                        isConnected = true")
        )

        // 4. activeBackups must pass isConnected = true
        assertTrue(
            "activeBackups must invoke DeviceIdentityBlock with isConnected = true",
            content.contains("device = device, \n                                    isConnected = true,") ||
            content.contains("device = device,\n                                    isConnected = true") ||
            content.contains("device = device, \n                                    isConnected = true")
        )

        // 5. notConnected must pass isConnected = false
        assertTrue(
            "notConnected must invoke DeviceIdentityBlock with isConnected = false",
            content.contains("device = device, \n                            isConnected = false,") ||
            content.contains("device = device,\n                            isConnected = false") ||
            content.contains("device = device, \n                            isConnected = false")
        )
    }
}
