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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit & contract tests for SensorGridScreen reordering and GridActions interface (REQ-UI-200, TST-UI-154.4).
 */
class SensorGridScreenReorderTest {

    private fun sampleField(id: Long, row: Int, col: Int): SensorFieldState {
        return SensorFieldState(
            configHash = id.toInt(),
            sensorFieldId = id,
            rowNr = row,
            colNr = col,
            viewSize = ViewSize.NORMAL,
            label = "Speed",
            filterDescription = "GPS 5s",
            value = "25.4",
            units = "km/h",
            zoneColor = Color.Transparent
        )
    }

    @Test
    fun testGridActions_defaultImplementationsDoNotThrow() {
        var editCalled = false
        var deleteCalled = false
        var addRowCalled = false
        var addColCalled = false

        val actions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) { editCalled = true }
            override fun onDeleteField(fieldState: SensorFieldState) { deleteCalled = true }
            override fun onAddRow(beforeRow: Int) { addRowCalled = true }
            override fun onAddCol(atRow: Int, beforeCol: Int) { addColCalled = true }
        }

        val field = sampleField(1L, 1, 1)

        // Default methods should execute cleanly without UnsupportedOperationException
        actions.onSelectFieldForMove(field)
        actions.onCancelMove()
        actions.onSwapFields(1L, 2L)
        actions.onMoveField(1L, 2, 3)

        actions.onEditField(field)
        actions.onDeleteField(field)
        actions.onAddRow(1)
        actions.onAddCol(1, 1)

        assertTrue(editCalled)
        assertTrue(deleteCalled)
        assertTrue(addRowCalled)
        assertTrue(addColCalled)
    }

    @Test
    fun testGridActions_customImplementationsTrackCalls() {
        var selectedField: SensorFieldState? = null
        var cancelCount = 0
        var swappedSource = -1L
        var swappedTarget = -1L
        var movedId = -1L
        var movedRow = -1
        var movedCol = -1

        val actions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}

            override fun onSelectFieldForMove(fieldState: SensorFieldState) {
                selectedField = fieldState
            }

            override fun onCancelMove() {
                cancelCount++
            }

            override fun onSwapFields(sourceFieldId: Long, targetFieldId: Long) {
                swappedSource = sourceFieldId
                swappedTarget = targetFieldId
            }

            override fun onMoveField(sourceFieldId: Long, targetRow: Int, targetCol: Int) {
                movedId = sourceFieldId
                movedRow = targetRow
                movedCol = targetCol
            }
        }

        val field = sampleField(5L, 2, 3)

        actions.onSelectFieldForMove(field)
        assertEquals(5L, selectedField?.sensorFieldId)

        actions.onSwapFields(5L, 8L)
        assertEquals(5L, swappedSource)
        assertEquals(8L, swappedTarget)

        actions.onMoveField(5L, 1, -1)
        assertEquals(5L, movedId)
        assertEquals(1, movedRow)
        assertEquals(-1, movedCol)

        actions.onCancelMove()
        assertEquals(1, cancelCount)
    }
}
