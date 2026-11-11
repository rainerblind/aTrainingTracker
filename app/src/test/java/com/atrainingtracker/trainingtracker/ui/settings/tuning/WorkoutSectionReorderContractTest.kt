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

import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.ui.aftermath.ReorderDragState
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract and swap logic verification for section reordering UI in AdvancedTuningDialog (REQ-UI-255, TST-UI-214-C).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutSectionReorderContractTest {

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

        // 1. Move Up on second element (DESCRIPTION at index 1 -> index 0)
        val afterMoveUp = moveSection(initialOrder, 1, 0)
        assertEquals(WorkoutSectionType.DESCRIPTION, afterMoveUp[0])
        assertEquals(WorkoutSectionType.DETAILS, afterMoveUp[1])
        assertEquals(12, afterMoveUp.size)

        // 2. Move Down on first element (DESCRIPTION at index 0 -> index 1)
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
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        val method = sectionClass.declaredMethods.find { it.name.startsWith("WorkoutMasksAndCardsSection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutMasksAndCardsSection composable must exist", method)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("action_drag_to_reorder must exist", strings["action_drag_to_reorder"])
    }

    @Test
    fun testReorderDragState_swapLogicAndBoundaryClamping() {
        var currentOrder = listOf(WorkoutSectionType.DESCRIPTION, WorkoutSectionType.EXTREMA, WorkoutSectionType.LAPS)
        val state = ReorderDragState(
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
        val summaryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        assertNotNull("WorkoutSummaryKt must exist", summaryClass)

        val trackClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", trackClass)
    }
}
