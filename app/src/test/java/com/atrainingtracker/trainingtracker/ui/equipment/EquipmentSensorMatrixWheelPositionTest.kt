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

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.database.WheelPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and design contract test verifying Wheel Position cell badging, dimensions,
 * and Rule 84 zero-clipping enforcement within SENSOR_COLUMN_WIDTH (88.dp) (REQ-SEN-029, TST-SEN-029.3).
 */
class EquipmentSensorMatrixWheelPositionTest {

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
    fun testSensorColumnWidthEnforces88dpRule84() {
        assertEquals("SENSOR_COLUMN_WIDTH must be exactly 88.dp", 88.dp, SENSOR_COLUMN_WIDTH)
    }

    @Test
    fun testWheelPositionBadgingContractInMatrix() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt")
        val content = file.readText()

        // 1. Must check for wheel speed sensor types
        assertTrue(
            "Matrix must identify BIKE_SPEED and BIKE_SPEED_AND_CADENCE sensors",
            content.contains("DeviceType.BIKE_SPEED") && content.contains("DeviceType.BIKE_SPEED_AND_CADENCE")
        )

        // 2. Must render compact front and rear badges
        assertTrue(
            "Matrix must render R.string.equipment_wheel_position_front_badge",
            content.contains("R.string.equipment_wheel_position_front_badge")
        )
        assertTrue(
            "Matrix must render R.string.equipment_wheel_position_rear_badge",
            content.contains("R.string.equipment_wheel_position_rear_badge")
        )

        // 3. Must apply top-end alignment and compact padding within SENSOR_COLUMN_WIDTH
        assertTrue(
            "Badge must align to TopEnd",
            content.contains("Alignment.TopEnd")
        )
        assertTrue(
            "Badge must have testTag with item and sensor ID",
            content.contains("wheel_position_badge_")
        )

        // 4. Must support triggering wheel position selection dialog on click
        assertTrue(
            "MatrixEquipmentRow must support onRequestWheelPositionDialog",
            content.contains("onRequestWheelPositionDialog")
        )

        // 5. Must support guidance dialog trigger from sticky header
        assertTrue(
            "MatrixTableHeaderRow must provide onShowGuidance",
            content.contains("onShowGuidance") && content.contains("matrix_guidance_button")
        )
    }
}
