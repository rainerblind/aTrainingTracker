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

package com.atrainingtracker.trainingtracker.ui.components.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying [MinimumDragHandle] contract and proportions (REQ-UI-189, REQ-UI-196, TST-UI-143.3, TST-UI-150, ATT-1588, ATT-1644).
 */
class MinimumDragHandleTest {

    @Test
    fun testMinimumDragHandle_functionExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandleKt")
        assertNotNull("MinimumDragHandleKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val dragHandleMethod = methods.find { it.name.startsWith("MinimumDragHandle") && Modifier.isPublic(it.modifiers) }
        assertNotNull("MinimumDragHandle composable function must exist", dragHandleMethod)
        assertTrue("MinimumDragHandle must be public", Modifier.isPublic(dragHandleMethod!!.modifiers))
    }

    @Test
    fun testMinimumDragHandle_dimensionsConformToDesignTokens() {
        assertEquals(32, BottomSheetDesign.DragHandleWidth.value.toInt())
        assertEquals(3, BottomSheetDesign.DragHandleHeight.value.toInt())
    }
}
