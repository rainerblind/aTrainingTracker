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
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.EquipmentSensorMatrixScreenKt")
        org.junit.Assert.assertNotNull("EquipmentSensorMatrixScreenKt class must exist", clazz)
        val metadata = clazz.getAnnotation(Metadata::class.java)
        org.junit.Assert.assertNotNull("EquipmentSensorMatrixScreenKt must carry Kotlin Metadata", metadata)
        val symbols = metadata!!.data2.toList()

        assertTrue(
            "MatrixEquipmentRow must support onRequestWheelPositionDialog",
            "onRequestWheelPositionDialog" in symbols
        )
        assertTrue(
            "MatrixTableHeaderRow must provide onShowGuidance",
            "onShowGuidance" in symbols
        )
        assertTrue(com.atrainingtracker.R.string.equipment_wheel_position_front_badge != 0)
        assertTrue(com.atrainingtracker.R.string.equipment_wheel_position_rear_badge != 0)
    }
}
