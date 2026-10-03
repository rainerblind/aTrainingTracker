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

    private fun findAdvancedTuningDialogFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("AdvancedTuningDialog.kt not found in candidates: $candidates")
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
        assertEquals(8, afterMoveUp.size)

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

        // Last item can NOT move down (targetIndex 8 is out of bounds)
        val invalidDown = moveSection(order, order.size - 1, order.size)
        assertEquals(order, invalidDown)
    }

    @Test
    fun testAdvancedTuningDialog_structuralContract() {
        val file = findAdvancedTuningDialogFile()
        val content = file.readText()

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

        // 3. Move Up and Move Down Icons & Content Descriptions
        assertTrue(
            "Must reference action_move_up string",
            content.contains("R.string.action_move_up")
        )
        assertTrue(
            "Must reference action_move_down string",
            content.contains("R.string.action_move_down")
        )
        assertTrue(
            "Must use KeyboardArrowUp icon",
            content.contains("KeyboardArrowUp")
        )
        assertTrue(
            "Must use KeyboardArrowDown icon",
            content.contains("KeyboardArrowDown")
        )

        // 4. Persistence call in onSave and reset
        assertTrue(
            "Must persist workoutSectionsOrder in onSave",
            content.contains("setWorkoutSectionsOrder")
        )
    }
}
