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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and visual contract tests verifying that the favorite location (Lieblingsort)
 * bottom sheet on the central navigation map is locked to its designated peek height,
 * upward dragging and swiping are disabled when active, full-screen expansion is rejected,
 * and the misleading drag handle is removed (REQ-UI-238, TST-UI-197, ATT-2042).
 */
class FavoriteLocationSheetContractTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testMapScreenWithTrack_disablesSheetSwipeWhenLocationSelected() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "BottomSheetScaffold must configure sheetSwipeEnabled = selectedLocationId == null",
            content.contains("sheetSwipeEnabled = selectedLocationId == null")
        )
    }

    @Test
    fun testMapScreenWithTrack_confirmValueChange_rejectsExpandedWhenLocationSelected() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "rememberStandardBottomSheetState must configure confirmValueChange",
            content.contains("confirmValueChange")
        )
        assertTrue(
            "confirmValueChange must check targetValue == SheetValue.Expanded and reject when selectedLocationId != null",
            content.contains("targetValue == SheetValue.Expanded") &&
                content.contains("selectedLocationId != null")
        )
    }

    @Test
    fun testKnownLocationOnMapSheet_doesNotContainDragHandle() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertFalse(
            "MapScreenWithTrack must not import or invoke MinimumDragHandle in KnownLocationOnMapSheet",
            content.contains("MinimumDragHandle")
        )
    }

    @Test
    fun testKnownLocationOnMapSheet_hasCorrectTopPadding() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "KnownLocationOnMapSheet must set top = 16.dp padding",
            content.contains("top = 16.dp")
        )
    }

    @Test
    fun testMapScreenWithTrack_consumesPeekHeightKnownLocation() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()

        assertTrue(
            "MapScreenWithTrack must consume BottomSheetDesign.PeekHeightKnownLocation + navBarHeight",
            content.contains("selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight")
        )
    }
}
