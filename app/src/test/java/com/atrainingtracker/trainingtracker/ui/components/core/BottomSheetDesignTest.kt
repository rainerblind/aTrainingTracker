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

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [BottomSheetDesign] design tokens and contracts (REQ-UI-189, TST-UI-143.1, ATT-1588).
 */
class BottomSheetDesignTest {

    private val density = Density(1f, 1f)

    @Test
    fun testBottomSheetDesign_tokenConstants() {
        assertEquals("SheetCornerRadius must be 20.dp", 20.dp, BottomSheetDesign.SheetCornerRadius)
        assertEquals("SheetShadowElevation must be 8.dp", 8.dp, BottomSheetDesign.SheetShadowElevation)
        assertEquals("SheetTonalElevation must be 2.dp", 2.dp, BottomSheetDesign.SheetTonalElevation)
        assertEquals("BorderWidth must be 1.dp", 1.dp, BottomSheetDesign.BorderWidth)
        assertEquals("DragHandleWidth must be 36.dp", 36.dp, BottomSheetDesign.DragHandleWidth)
        assertEquals("DragHandleHeight must be 4.dp", 4.dp, BottomSheetDesign.DragHandleHeight)
    }

    @Test
    fun testBottomSheetDesign_sheetShapeCurvature() {
        val shape = BottomSheetDesign.SheetShape
        assertTrue("SheetShape must be a RoundedCornerShape", shape is RoundedCornerShape)

        val roundedShape = shape as RoundedCornerShape
        val dummySize = Size(400f, 800f)

        val topStartPx = roundedShape.topStart.toPx(dummySize, density)
        val topEndPx = roundedShape.topEnd.toPx(dummySize, density)
        val bottomStartPx = roundedShape.bottomStart.toPx(dummySize, density)
        val bottomEndPx = roundedShape.bottomEnd.toPx(dummySize, density)

        assertEquals("TopStart radius must equal 20px at 1x density", 20f, topStartPx, 0.001f)
        assertEquals("TopEnd radius must equal 20px at 1x density", 20f, topEndPx, 0.001f)
        assertEquals("BottomStart radius must be 0px", 0f, bottomStartPx, 0.001f)
        assertEquals("BottomEnd radius must be 0px", 0f, bottomEndPx, 0.001f)
    }

    @Test
    fun testBottomSheetDesign_sheetContourModifier() {
        val modifier = Modifier.sheetContour()
        assertNotNull("sheetContour extension must return a non-null modifier", modifier)
    }
}
