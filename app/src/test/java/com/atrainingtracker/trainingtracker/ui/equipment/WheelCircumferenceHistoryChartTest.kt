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

import com.atrainingtracker.trainingtracker.database.WheelCircumferenceRecord
import com.atrainingtracker.trainingtracker.database.WheelPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract verification for WheelCircumferenceHistoryChart (REQ-SEN-030.4, TST-SEN-030.4, ATT-7753).
 */
class WheelCircumferenceHistoryChartTest {

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
    fun testWheelCircumferenceRecordDomainModel() {
        val record = WheelCircumferenceRecord(
            id = 1L,
            equipmentId = 10L,
            timestampMs = 1700000000000L,
            wheelPosition = WheelPosition.FRONT,
            circumferenceMm = 2105,
            source = WheelCircumferenceRecord.SOURCE_GNSS_AUTO
        )
        assertEquals(1L, record.id)
        assertEquals(10L, record.equipmentId)
        assertEquals(1700000000000L, record.timestampMs)
        assertEquals(WheelPosition.FRONT, record.wheelPosition)
        assertEquals(2105, record.circumferenceMm)
        assertEquals(WheelCircumferenceRecord.SOURCE_GNSS_AUTO, record.source)
    }

    @Test
    fun testWheelCircumferenceHistoryChartStructureAndTags() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelCircumferenceHistoryChart.kt")
        val content = file.readText()

        assertTrue("Must include chart root testTag", content.contains("wheel_circumference_history_chart"))
        assertTrue("Must include baseline reset button testTag", content.contains("wheel_circumference_reset_baseline_button"))
        assertTrue("Must reference history title string", content.contains("R.string.equipment_tpms_history_title"))
        assertTrue("Must reference reset baseline string", content.contains("R.string.equipment_tpms_reset_baseline"))
        assertTrue("Must reference no history empty string", content.contains("R.string.equipment_tpms_no_history"))
        assertTrue("Must reference front legend string", content.contains("R.string.equipment_tpms_front_legend"))
        assertTrue("Must reference rear legend string", content.contains("R.string.equipment_tpms_rear_legend"))

        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.equipment.WheelCircumferenceHistoryChartKt")
        assertNotNull("WheelCircumferenceHistoryChartKt class must exist", clazz)
        val method = clazz.declaredMethods.firstOrNull { it.name.startsWith("WheelCircumferenceHistoryChart") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WheelCircumferenceHistoryChart composable must exist", method)
    }

    @Test
    fun testEditEquipmentDialog_rendersChartEvenWhenHistoryEmpty() {
        val file = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt")
        val content = file.readText()

        assertTrue(
            "EditEquipmentDialog must render chart for bikes (frameType > 0)",
            content.contains("if (item.frameType > 0)")
        )
        org.junit.Assert.assertFalse(
            "EditEquipmentDialog must NOT suppress chart when history is empty (Finding 3)",
            content.contains("if (item.frameType > 0 && historyRecords.isNotEmpty())")
        )
    }
}
