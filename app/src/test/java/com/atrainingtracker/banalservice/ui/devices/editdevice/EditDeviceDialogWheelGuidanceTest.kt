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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import com.atrainingtracker.banalservice.devices.DeviceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Unit and structural contract test verifying the in-app guidance entry point in EditDeviceDialog
 * for BIKE_SPEED and SPEED_AND_LOCATION_ATT_FUSED sensors (REQ-SEN-029.4, TST-SEN-029.5, ATT-7752).
 */
class EditDeviceDialogWheelGuidanceTest {

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
    fun testShouldShowWheelSensorGuidance_trueOnlyForWheelAndFusedSensors() {
        // Must show for wheel speed and fused speed/location sensors
        assertTrue("Must show for BIKE_SPEED", shouldShowWheelSensorGuidance(DeviceType.BIKE_SPEED))
        assertTrue("Must show for BIKE_SPEED_AND_CADENCE", shouldShowWheelSensorGuidance(DeviceType.BIKE_SPEED_AND_CADENCE))
        assertTrue("Must show for SPEED_AND_LOCATION_ATT_FUSED", shouldShowWheelSensorGuidance(DeviceType.SPEED_AND_LOCATION_ATT_FUSED))

        // Must NOT show for unrelated sensors
        assertFalse("Must not show for HRM", shouldShowWheelSensorGuidance(DeviceType.HRM))
        assertFalse("Must not show for BIKE_CADENCE", shouldShowWheelSensorGuidance(DeviceType.BIKE_CADENCE))
        assertFalse("Must not show for BIKE_POWER", shouldShowWheelSensorGuidance(DeviceType.BIKE_POWER))
        assertFalse("Must not show for RUN_SPEED", shouldShowWheelSensorGuidance(DeviceType.RUN_SPEED))
        assertFalse("Must not show for BIKE_RADAR", shouldShowWheelSensorGuidance(DeviceType.BIKE_RADAR))
        assertFalse("Must not show for ALTITUDE_FROM_PRESSURE", shouldShowWheelSensorGuidance(DeviceType.ALTITUDE_FROM_PRESSURE))
        assertFalse("Must not show for ALTITUDE_FROM_POSITION", shouldShowWheelSensorGuidance(DeviceType.ALTITUDE_FROM_POSITION))
        assertFalse("Must not show for ALTITUDE_AND_SLOPE_ATT_FUSED", shouldShowWheelSensorGuidance(DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED))
        assertFalse("Must not show for ENVIRONMENT", shouldShowWheelSensorGuidance(DeviceType.ENVIRONMENT))
        assertFalse("Must not show for CLOCK", shouldShowWheelSensorGuidance(DeviceType.CLOCK))
    }

    @Test
    fun testEditDeviceDialog_guidanceCardAndDialogWiringContract() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt")
        val content = file.readText()

        // 1. Must import and wire WheelSensorGuidanceDialog
        assertTrue(
            "Must import WheelSensorGuidanceDialog",
            content.contains("import com.atrainingtracker.trainingtracker.ui.equipment.WheelSensorGuidanceDialog")
        )
        assertTrue(
            "Must conditionally invoke WheelSensorGuidanceDialog",
            content.contains("WheelSensorGuidanceDialog(onDismiss = { showWheelGuidanceDialog = false })")
        )

        // 2. Must conditionally render WheelSensorGuidanceCard using shouldShowWheelSensorGuidance
        assertTrue(
            "Must check shouldShowWheelSensorGuidance(data.deviceType)",
            content.contains("shouldShowWheelSensorGuidance(data.deviceType)")
        )
        assertTrue(
            "Must render WheelSensorGuidanceCard",
            content.contains("WheelSensorGuidanceCard(onShowGuidance = { showWheelGuidanceDialog = true })")
        )

        // 3. Must include test tags for card and help action button
        assertTrue(
            "Must include edit_device_wheel_guidance_card test tag",
            content.contains("edit_device_wheel_guidance_card")
        )
        assertTrue(
            "Must include edit_device_wheel_guidance_button test tag",
            content.contains("edit_device_wheel_guidance_button")
        )

        // 4. Must reference localized guidance strings
        assertTrue(
            "Must reference equipment_guidance_title",
            content.contains("R.string.equipment_guidance_title")
        )
        assertTrue(
            "Must reference equipment_guidance_subtitle",
            content.contains("R.string.equipment_guidance_subtitle")
        )

        // 5. Verify Composable method existence via reflection
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogKt")
        assertNotNull("EditDeviceDialogKt class must exist", clazz)
        val guidanceMethod = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelSensorGuidanceCard") }
        assertNotNull("WheelSensorGuidanceCard composable must exist", guidanceMethod)
    }
}
