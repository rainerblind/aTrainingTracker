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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Unit and structural contract tests verifying [LiveTrackingSheetContainer]
 * composition, design tokens, and layout integration (REQ-UI-366, TST-UI-329, ATT-3364).
 */
class LiveTrackingSheetContainerTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val containerFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainer.kt")
    }

    @Test
    fun testLiveTrackingSheetContainer_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.LiveTrackingSheetContainerKt")
        assertNotNull("LiveTrackingSheetContainerKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val containerMethod = methods.find { it.name.startsWith("LiveTrackingSheetContainer") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LiveTrackingSheetContainer composable function must exist and be public", containerMethod)
        assertTrue(Modifier.isPublic(containerMethod!!.modifiers))
    }

    @Test
    fun testLiveTrackingSheetContainer_sourceStructureAndTokens() {
        assertTrue("LiveTrackingSheetContainer.kt must exist", containerFile.exists())
        val content = containerFile.readText()

        assertTrue(
            "LiveTrackingSheetContainer must apply fillMaxWidth() and wrapContentHeight() (REQ-UI-270, REQ-UI-366)",
            content.contains(".fillMaxWidth()") && content.contains(".wrapContentHeight()")
        )
        assertTrue(
            "LiveTrackingSheetContainer must apply sheetContour() boundary stroke (REQ-UI-189, REQ-UI-366)",
            content.contains(".sheetContour()")
        )
        assertTrue(
            "LiveTrackingSheetContainer must apply surface background with BottomSheetDesign.SheetShape (REQ-UI-196, REQ-UI-366)",
            content.contains(".background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)")
        )
        assertTrue(
            "LiveTrackingSheetContainer must conditionally wire clickable to onExpand when isPartiallyExpanded",
            content.contains("if (isPartiallyExpanded)") &&
            content.contains("Modifier.clickable(onClick = onExpand)")
        )
    }

    @Test
    fun testLiveTrackingSheetContainer_designTokensConformToRefinedSpecs() {
        assertEquals("SheetCornerRadius must be 20.dp", 20.dp, BottomSheetDesign.SheetCornerRadius)
        assertEquals("PeekHeightLiveSegment must be 126.dp", 126.dp, BottomSheetDesign.PeekHeightLiveSegment)
        assertEquals("SheetTonalElevation must be 0.dp", 0.dp, BottomSheetDesign.SheetTonalElevation)
    }
}
