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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.ui.equipment

import com.atrainingtracker.trainingtracker.database.WheelPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract verification for WheelPositionSelectionDialog and WheelSensorGuidanceDialog
 * (REQ-SEN-029, TST-SEN-029.4).
 */
class WheelPositionDialogsTest {

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
    fun testWheelPositionEnumDefinitions() {
        assertEquals("WheelPosition must have 3 values", 3, WheelPosition.values().size)
        assertEquals(WheelPosition.FRONT, WheelPosition.fromString("FRONT"))
        assertEquals(WheelPosition.REAR, WheelPosition.fromString("REAR"))
        assertEquals(WheelPosition.UNSPECIFIED, WheelPosition.fromString("UNSPECIFIED"))
        assertEquals(WheelPosition.UNSPECIFIED, WheelPosition.fromString("invalid"))
        assertEquals(WheelPosition.UNSPECIFIED, WheelPosition.fromString(null))
    }

    @Test
    fun testWheelPositionSelectionDialogStructureAndTags() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.WheelPositionSelectionDialogKt")
        assertNotNull("WheelPositionSelectionDialogKt class must exist", clazz)
        val method = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelPositionSelectionDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WheelPositionSelectionDialog composable must exist", method)
    }

    @Test
    fun testWheelSensorGuidanceDialogStructureAndTags() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.WheelSensorGuidanceDialogKt")
        assertNotNull("WheelSensorGuidanceDialogKt class must exist", clazz)
        val method = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelSensorGuidanceDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WheelSensorGuidanceDialog composable must exist", method)

        assertTrue(com.atrainingtracker.R.string.equipment_guidance_1_sensor_title != 0)
        assertTrue(com.atrainingtracker.R.string.equipment_guidance_1_sensor_body != 0)
        assertTrue(com.atrainingtracker.R.string.equipment_guidance_2_sensors_title != 0)
        assertTrue(com.atrainingtracker.R.string.equipment_guidance_2_sensors_body != 0)
    }

    @Test
    fun testEditDeviceDialogGuidanceEntryPointContract() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogKt")
        assertNotNull("EditDeviceDialogKt class must exist", clazz)
        val methods = clazz.declaredMethods.map { it.name }.toSet()
        assertTrue("EditDeviceDialogKt must declare shouldShowWheelSensorGuidance", "shouldShowWheelSensorGuidance" in methods)
        assertTrue(
            "EditDeviceDialogKt must declare WheelSensorGuidanceCard",
            methods.any { it.startsWith("WheelSensorGuidanceCard") }
        )
    }
}
