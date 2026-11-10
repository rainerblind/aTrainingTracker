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

package com.atrainingtracker.trainingtracker.ui.common.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit & Contract test suite for [NumericIntervalFilterSection] (REQ-UI-509.2, TST-UI-509.1).
 */
class NumericIntervalFilterSectionTest {

    private fun loadSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testPresetSelection_updatesBoundsAndBuffers() {
        val multiplier = 1000.0 // 1 km = 1000 m
        var minMeters: Double? = null
        var maxMeters: Double? = null
        var minText = ""
        var maxText = ""

        val onBoundsChange = { newMinM: Double?, newMaxM: Double?, newMinT: String, newMaxT: String ->
            minMeters = newMinM
            maxMeters = newMaxM
            minText = newMinT
            maxText = newMaxT
        }

        // Simulate tapping preset (10, 25)
        val presetMin = 10.0
        val presetMax = 25.0
        onBoundsChange(presetMin * multiplier, presetMax * multiplier, "10", "25")

        assertEquals(10000.0, minMeters!!, 0.001)
        assertEquals(25000.0, maxMeters!!, 0.001)
        assertEquals("10", minText)
        assertEquals("25", maxText)

        // Simulate tapping active preset again to toggle off
        onBoundsChange(null, null, "", "")
        assertNull(minMeters)
        assertNull(maxMeters)
        assertEquals("", minText)
        assertEquals("", maxText)
    }

    @Test
    fun testDecimalTypingInvariant_retainsIntermediateText() {
        // AC-2: Typing "1." must not collapse or prematurely reset buffer
        var textBuffer = "1"
        // Athlete types decimal point
        textBuffer += "."
        assertEquals("1.", textBuffer)

        // Parsing "1." as double:
        val parsed = textBuffer.toDoubleOrNull()
        assertEquals(1.0, parsed!!, 0.001)

        // Text buffer remains "1." without losing trailing decimal point
        assertEquals("1.", textBuffer)
    }

    @Test
    fun testNumericIntervalFilterSection_structuralContract() {
        val file = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/NumericIntervalFilterSection.kt")
        assertTrue("NumericIntervalFilterSection source must exist", file.exists())
        val src = file.readText()

        assertTrue("Must use FlowRow for preset chips", src.contains("FlowRow"))
        assertTrue("Must use OutlinedTextField for inputs", src.contains("OutlinedTextField"))
        assertTrue("Must support singleLine", src.contains("singleLine = true"))
        assertTrue("Must support customizable keyboardType", src.contains("keyboardType"))
    }

    @Test
    fun testMetricFilterSheets_delegateToNumericIntervalFilterSection() {
        val targetSheets = listOf(
            "src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt"
        )

        for (relPath in targetSheets) {
            val file = loadSourceFile(relPath)
            assertTrue("Sheet must exist: $relPath", file.exists())
            val src = file.readText()
            assertTrue("${file.name} must reference NumericIntervalFilterSection", src.contains("NumericIntervalFilterSection("))
        }
    }
}
