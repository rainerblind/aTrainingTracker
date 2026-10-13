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

package com.atrainingtracker.trainingtracker.ui.equipment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for EditEquipmentDialog sensor compatibility filtering
 * (REQ-UI-256, TST-UI-230, ATT-2382).
 */
class EditEquipmentDialogContractTest {

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
    fun testEditEquipmentDialog_compatibilityFilteringContract() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt")
        val content = file.readText()

        // 1. Must implement defensive compatibility filtering
        assertTrue(
            "EditEquipmentDialog must compute compatibleSensors",
            content.contains("compatibleSensors")
        )

        // 2. Must filter using DevicesDatabaseManager helper methods
        assertTrue(
            "Must check isBikeSensor and isSharedSensor for bikes",
            content.contains("DevicesDatabaseManager.isBikeSensor") &&
            content.contains("DevicesDatabaseManager.isSharedSensor")
        )

        assertTrue(
            "Must check isRunSensor and isSharedSensor for shoes",
            content.contains("DevicesDatabaseManager.isRunSensor")
        )

        // 3. MultiSelectSensorSpinner must receive compatibleSensors
        assertTrue(
            "MultiSelectSensorSpinner must receive compatibleSensors",
            content.contains("allSensors = compatibleSensors")
        )
    }
}
