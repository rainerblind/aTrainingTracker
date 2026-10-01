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

package com.atrainingtracker.trainingtracker.ui.segments

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying [LiveSegmentSheet] composition, design tokens, and layout integration (REQ-UI-196, TST-UI-150, ATT-1644).
 */
class LiveSegmentSheetLayoutTest {

    @Test
    fun testLiveSegmentSheet_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.segments.LIveSegmentSheetKt")
        assertNotNull("LIveSegmentSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("LiveSegmentSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LiveSegmentSheet composable function must exist and be public", sheetMethod)
        assertTrue(Modifier.isPublic(sheetMethod!!.modifiers))
    }

    @Test
    fun testLiveSegmentSheet_designTokensConformToRefinedSpecs() {
        assertEquals("DragHandleWidth must be 32.dp", 32.dp, BottomSheetDesign.DragHandleWidth)
        assertEquals("DragHandleHeight must be 3.dp", 3.dp, BottomSheetDesign.DragHandleHeight)
    }

    @Test
    fun testMapDetailLayout_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val layoutMethod = methods.find { it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers) }
        assertNotNull("MapDetailLayout composable function must exist and be public", layoutMethod)
        assertTrue(Modifier.isPublic(layoutMethod!!.modifiers))
    }

    @Test
    fun testLiveSegmentSheet_suppressesZoomControls() {
        var dir = java.io.File(System.getProperty("user.dir") ?: ".")
        while (!java.io.File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        val file = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt")
        assertTrue("LIveSegmentSheet.kt must exist", file.exists())
        val content = file.readText()
        assertTrue(
            "LiveSegmentSheet must pass showZoomControls = false to MapDetailLayout (REQ-UI-197, ATT-1736)",
            content.contains("showZoomControls = false")
        )
    }

    @Test
    fun testLiveSegmentSheet_unifiedSurfaceBackgroundContract() {
        var dir = java.io.File(System.getProperty("user.dir") ?: ".")
        while (!java.io.File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }

        // 1. Verify SensorGridScreen.kt configures sheetContainerColor and Box background
        val sensorGridFile = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", sensorGridFile.exists())
        val sensorGridContent = sensorGridFile.readText()
        assertTrue(
            "SensorGridScreen must configure sheetContainerColor = MaterialTheme.colorScheme.surface (REQ-UI-196, ATT-1735)",
            sensorGridContent.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "SensorGridScreen sheet content Box must apply background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape) (REQ-UI-196, ATT-1735)",
            sensorGridContent.contains(".background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)")
        )

        // 2. Verify MapDetailLayout.kt applies background(surface) in sheet mode (!useStatusBarsPadding)
        val mapDetailFile = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        assertTrue("MapDetailLayout.kt must exist", mapDetailFile.exists())
        val mapDetailContent = mapDetailFile.readText()
        assertTrue(
            "MapDetailLayout must apply background(surface) when !useStatusBarsPadding (REQ-UI-196, ATT-1735)",
            mapDetailContent.contains("if (!useStatusBarsPadding) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier")
        )

        // 3. Verify LIveSegmentSheet.kt header applies background(surface)
        val liveSheetFile = java.io.File(dir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt")
        assertTrue("LIveSegmentSheet.kt must exist", liveSheetFile.exists())
        val liveSheetContent = liveSheetFile.readText()
        assertTrue(
            "LIveSegmentSheet header must apply background(MaterialTheme.colorScheme.surface) (REQ-UI-196, ATT-1735)",
            liveSheetContent.contains(".background(MaterialTheme.colorScheme.surface)")
        )
    }
}
