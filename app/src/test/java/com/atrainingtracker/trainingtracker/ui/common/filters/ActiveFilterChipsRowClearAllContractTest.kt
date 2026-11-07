/*
 * aTrainingTracker - Open Source Fitness Tracking on Android
 * Copyright (c) 2026. All rights reserved.
 *
 * Enforces REQ-UI-468 / TST-UI-468: Uniform 'Clear all' ('Alle zurücksetzen')
 * action across all active filter chips rows whenever any filter is active.
 */

package com.atrainingtracker.trainingtracker.ui.common.filters

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ActiveFilterChipsRowClearAllContractTest {

    private val targetFiles = listOf(
        "src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt",
        "src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/ActiveSegmentFilterChipsRow.kt",
        "src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt",
        "src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt",
        "src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/ActiveDeviceFilterChipsRow.kt",
        "src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/ActiveKnownLocationsFilterChipsRow.kt"
    )

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath),
            File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testAllActiveFilterChipsRows_forbidPluralGuards_and_enforceUniformClearAll() {
        for (relPath in targetFiles) {
            val file = findSourceFile(relPath)
            assertTrue("Source file must exist: $relPath", file.exists())

            val source = file.readText()
            val fileName = file.name

            // 1. REQ-UI-468.3: Empty state suppression
            assertTrue(
                "$fileName must suppress rendering when criteria.isEmpty",
                source.contains("if (criteria.isEmpty) return")
            )

            // 2. REQ-UI-468.2: Forbid legacy plural guards (> 1 or >= 2)
            assertFalse(
                "$fileName must NOT guard 'Clear all' behind activeFilterCount > 1",
                source.contains("activeFilterCount > 1")
            )
            assertFalse(
                "$fileName must NOT guard 'Clear all' behind activeFilterCount >= 2",
                source.contains("activeFilterCount >= 2")
            )

            // 3. REQ-UI-468.1: Unconditional Clear All presence
            assertTrue(
                "$fileName must reference R.string.filter_clear_all",
                source.contains("R.string.filter_clear_all")
            )
            assertTrue(
                "$fileName must invoke onClearAll onClick",
                source.contains("onClick = onClearAll")
            )

            // 4. REQ-UI-468.4: Padding parity
            assertTrue(
                "$fileName must apply 4.dp start padding to Clear All TextButton",
                source.contains("Modifier.padding(start = 4.dp)")
            )
        }
    }
}
