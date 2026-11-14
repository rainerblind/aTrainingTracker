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
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionSelectionDialog.kt")
        val content = file.readText()

        assertTrue("Must include dialog testTag", content.contains("wheel_position_dialog"))
        assertTrue("Must include front selection testTag", content.contains("wheel_position_front"))
        assertTrue("Must include rear selection testTag", content.contains("wheel_position_rear"))
        assertTrue("Must include unspecified selection testTag", content.contains("wheel_position_unspecified"))
        assertTrue("Must include cancel button testTag", content.contains("wheel_position_cancel"))
        assertTrue("Must include unlink button testTag", content.contains("wheel_position_unlink"))

        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.WheelPositionSelectionDialogKt")
        assertNotNull("WheelPositionSelectionDialogKt class must exist", clazz)
        val method = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelPositionSelectionDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WheelPositionSelectionDialog composable must exist", method)
    }

    @Test
    fun testWheelSensorGuidanceDialogStructureAndTags() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelSensorGuidanceDialog.kt")
        val content = file.readText()

        assertTrue("Must include guidance dialog testTag", content.contains("wheel_sensor_guidance_dialog"))
        assertTrue("Must include got it button testTag", content.contains("wheel_sensor_guidance_got_it"))
        assertTrue("Must reference 1 sensor title", content.contains("R.string.equipment_guidance_1_sensor_title"))
        assertTrue("Must reference 1 sensor body", content.contains("R.string.equipment_guidance_1_sensor_body"))
        assertTrue("Must reference 2 sensors title", content.contains("R.string.equipment_guidance_2_sensors_title"))
        assertTrue("Must reference 2 sensors body", content.contains("R.string.equipment_guidance_2_sensors_body"))

        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.WheelSensorGuidanceDialogKt")
        assertNotNull("WheelSensorGuidanceDialogKt class must exist", clazz)
        val method = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelSensorGuidanceDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WheelSensorGuidanceDialog composable must exist", method)
    }
}
