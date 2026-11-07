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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract and swap logic verification for section reordering UI in AdvancedTuningDialog (REQ-UI-255, TST-UI-214-C).
 */
class WorkoutSectionReorderContractTest {

    private fun findTuningSourceContent(): String {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning")
        )
        val dir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: return ""
        return dir.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    private fun moveSection(order: List<WorkoutSectionType>, fromIndex: Int, toIndex: Int): List<WorkoutSectionType> {
        if (fromIndex !in order.indices || toIndex !in order.indices) return order
        val mutable = order.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    @Test
    fun testReorderingLogic_moveUpAndDown() {
        val initialOrder = WorkoutSectionType.DEFAULT_ORDER

        // 1. Move Up on second element (EXTREMA at index 1 -> index 0)
        val afterMoveUp = moveSection(initialOrder, 1, 0)
        assertEquals(WorkoutSectionType.EXTREMA, afterMoveUp[0])
        assertEquals(WorkoutSectionType.DESCRIPTION, afterMoveUp[1])
        assertEquals(11, afterMoveUp.size)

        // 2. Move Down on first element (EXTREMA at index 0 -> index 1)
        val afterMoveDown = moveSection(afterMoveUp, 0, 1)
        assertEquals(initialOrder, afterMoveDown)
    }

    @Test
    fun testReorderingBoundaries_disabledEdgeCases() {
        val order = WorkoutSectionType.DEFAULT_ORDER

        // First item can NOT move up (targetIndex -1 is out of bounds)
        val invalidUp = moveSection(order, 0, -1)
        assertEquals(order, invalidUp)

        // Last item can NOT move down (targetIndex 9 is out of bounds)
        val invalidDown = moveSection(order, order.size - 1, order.size)
        assertEquals(order, invalidDown)
    }

    @Test
    fun testAdvancedTuningDialog_structuralContract() {
        val content = findTuningSourceContent()

        // 1. References WorkoutSectionType
        assertTrue(
            "AdvancedTuningDialog must import or reference WorkoutSectionType",
            content.contains("WorkoutSectionType")
        )

        // 2. Supports section reordering parameter & callback
        assertTrue(
            "WorkoutMasksAndCardsSection must support workoutSectionsOrder parameter",
            content.contains("workoutSectionsOrder: List<WorkoutSectionType>")
        )
        assertTrue(
            "WorkoutMasksAndCardsSection must support onWorkoutSectionsOrderChange parameter",
            content.contains("onWorkoutSectionsOrderChange: (List<WorkoutSectionType>) -> Unit")
        )

        // 3. Drag-to-reorder controls (REQ-UI-456.1 / ATT-5761)
        assertTrue(
            "Must reference action_drag_to_reorder string",
            content.contains("R.string.action_drag_to_reorder")
        )
        assertTrue(
            "Must use ReorderDragHandle",
            content.contains("ReorderDragHandle")
        )

        // 4. Persistence call in onSave and reset
        assertTrue(
            "Must persist workoutSectionsOrder in onSave",
            content.contains("setWorkoutSectionsOrder")
        )

        // 5. Must support EXPORT_STATUS row in WorkoutMasksAndCardsSection
        assertTrue(
            "WorkoutMasksAndCardsSection must support EXPORT_STATUS",
            content.contains("WorkoutSectionType.EXPORT_STATUS")
        )

        // 6. Must support PHOTOS row in WorkoutMasksAndCardsSection (REQ-UI-414.6)
        assertTrue(
            "WorkoutMasksAndCardsSection must support PHOTOS",
            content.contains("WorkoutSectionType.PHOTOS")
        )
    }

    /**
     * TST-UI-456.1: Drag-and-Drop Reordering Logic & Boundary Swapping Unit Test.
     */
    @Test
    fun testReorderDragState_swapLogicAndBoundaryClamping() {
        var currentOrder = listOf(WorkoutSectionType.DESCRIPTION, WorkoutSectionType.EXTREMA, WorkoutSectionType.LAPS)
        val state = com.atrainingtracker.trainingtracker.ui.aftermath.ReorderDragState(
            thresholdPx = 44f,
            minIndex = 0,
            maxIndex = 2,
            onMoveItem = { from, to ->
                val mutable = currentOrder.toMutableList()
                val item = mutable.removeAt(from)
                mutable.add(to, item)
                currentOrder = mutable
            }
        )

        // 1. Drag start at index 0
        state.onDragStart(0)
        assertEquals(0, state.draggingIndex)

        // 2. Drag down by 50px (> threshold 44px) -> swaps with index 1
        state.onVerticalDrag(50f)
        assertEquals(1, state.draggingIndex)
        assertEquals(WorkoutSectionType.EXTREMA, currentOrder[0])
        assertEquals(WorkoutSectionType.DESCRIPTION, currentOrder[1])
        assertEquals(6f, state.dragOffsetPx, 0.001f)

        // 3. Drag up by 50px (< -44px from 6px = -44px) -> swaps back to index 0
        state.onVerticalDrag(-50f)
        assertEquals(0, state.draggingIndex)
        assertEquals(WorkoutSectionType.DESCRIPTION, currentOrder[0])
        assertEquals(WorkoutSectionType.EXTREMA, currentOrder[1])

        // 4. Drag up at minIndex 0 -> clamped, no index error
        state.onVerticalDrag(-100f)
        assertEquals(0, state.draggingIndex)
        assertEquals(-44f, state.dragOffsetPx, 0.001f)

        state.onDragEnd()
        assertEquals(null, state.draggingIndex)
        assertEquals(0f, state.dragOffsetPx, 0.001f)
    }

    @Test
    fun testWorkoutSummaryAndTrackOnMap_exportStatusContract() {
        val summaryFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
        if (summaryFile.exists()) {
            val content = summaryFile.readText()
            assertTrue(
                "WorkoutSummary.kt must handle WorkoutSectionType.EXPORT_STATUS inside loop",
                content.contains("WorkoutSectionType.EXPORT_STATUS ->")
            )
            assertTrue(
                "WorkoutSummary.kt must check preferences.showExportStatus",
                content.contains("preferences.showExportStatus")
            )
            assertTrue(
                "WorkoutSummary.kt must handle WorkoutSectionType.PHOTOS inside loop",
                content.contains("WorkoutSectionType.PHOTOS ->")
            )
            assertTrue(
                "WorkoutSummary.kt must check preferences.showPhotos",
                content.contains("preferences.showPhotos")
            )
        }

        val trackFile = File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        if (trackFile.exists()) {
            val content = trackFile.readText()
            assertTrue(
                "TrackOnMapScreen.kt must handle WorkoutSectionType.EXPORT_STATUS",
                content.contains("WorkoutSectionType.EXPORT_STATUS ->")
            )
            assertTrue(
                "TrackOnMapScreen.kt must check activeDetailPrefs.showExportStatus",
                content.contains("activeDetailPrefs.showExportStatus")
            )
        }
    }
}
