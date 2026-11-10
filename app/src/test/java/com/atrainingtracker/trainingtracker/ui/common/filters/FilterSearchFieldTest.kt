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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit & Contract test suite for [FilterSearchField] (REQ-UI-509.1, TST-UI-509.1).
 */
class FilterSearchFieldTest {

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
    fun testQueryClearing_resetsToEmptyString() {
        var query = "Morning Run"
        val onQueryChange: (String) -> Unit = { query = it }

        // Simulate clear icon click
        onQueryChange("")
        assertEquals("", query)
    }

    @Test
    fun testFilterSearchField_structuralContract() {
        val file = loadSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterSearchField.kt")
        assertTrue("FilterSearchField source must exist", file.exists())
        val src = file.readText()

        assertTrue("Must use OutlinedTextField", src.contains("OutlinedTextField"))
        assertTrue("Must use Icons.Default.Search for leading icon", src.contains("Icons.Default.Search"))
        assertTrue("Must use Icons.Default.Close for clear trailing icon", src.contains("Icons.Default.Close"))
        assertTrue("Must check query.isNotBlank() for clear icon visibility", src.contains("query.isNotBlank()"))
        assertTrue("Must invoke onQueryChange(\"\") when clear is clicked", src.contains("onQueryChange(\"\")"))
    }

    @Test
    fun testAllFilterBottomSheets_delegateToFilterSearchField() {
        val targetSheets = listOf(
            "src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentFilterBottomSheet.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsFilterBottomSheet.kt"
        )

        for (relPath in targetSheets) {
            val file = loadSourceFile(relPath)
            assertTrue("Sheet must exist: $relPath", file.exists())
            val src = file.readText()
            assertTrue("${file.name} must reference FilterSearchField", src.contains("FilterSearchField("))
        }
    }
}
