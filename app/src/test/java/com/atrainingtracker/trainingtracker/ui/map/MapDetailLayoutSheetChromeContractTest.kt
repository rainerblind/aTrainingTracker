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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test for [MapDetailLayout] verifying that bottom-sheet chrome
 * (MinimumDragHandle and SheetShape) is decoupled from useStatusBarsPadding via isBottomSheet,
 * suppressing unwanted drag handles in full-screen tabbed layouts (REQ-UI-498.2, REQ-UI-498.3 / TST-UI-498.2, TST-UI-498.3).
 */
class MapDetailLayoutSheetChromeContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    private val tabbedContentFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
    }

    private val trackOnMapFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
    }

    @Test
    fun testMapDetailLayout_declaresIsBottomSheetWithDefault_TST_UI_498_2() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // Verify isBottomSheet parameter exists with !useStatusBarsPadding default
        assertTrue(
            "MapDetailLayout must declare isBottomSheet parameter with default !useStatusBarsPadding",
            content.contains("isBottomSheet: Boolean = !useStatusBarsPadding")
        )

        // Verify MinimumDragHandle is gated on isBottomSheet, NOT !useStatusBarsPadding
        assertTrue(
            "MinimumDragHandle must be gated on isBottomSheet",
            Regex("""if\s*\(\s*isBottomSheet\s*\)\s*\{\s*MinimumDragHandle\(\)\s*\}""").containsMatchIn(content)
        )
        assertFalse(
            "MinimumDragHandle must NOT be gated directly on !useStatusBarsPadding",
            Regex("""if\s*\(\s*!useStatusBarsPadding\s*\)\s*\{\s*MinimumDragHandle\(\)\s*\}""").containsMatchIn(content)
        )

        // Verify shape is gated on isBottomSheet
        assertTrue(
            "Surface shape must be gated on isBottomSheet",
            content.contains("shape = if (isBottomSheet) BottomSheetDesign.SheetShape else RectangleShape")
        )
    }

    @Test
    fun testFullScreenCallers_passIsBottomSheetFalse_TST_UI_498_3() {
        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedContentFile.exists())
        val tabbedContent = tabbedContentFile.readText()

        val tabbedOccurrences = Regex("""isBottomSheet\s*=\s*false""").findAll(tabbedContent).count()
        assertEquals(
            "WorkoutDetailTabbedContent must pass isBottomSheet = false across all 3 MapDetailLayout invocations",
            3,
            tabbedOccurrences
        )

        assertTrue("TrackOnMapScreen.kt must exist", trackOnMapFile.exists())
        val trackOnMapContent = trackOnMapFile.readText()

        val trackOccurrences = Regex("""isBottomSheet\s*=\s*false""").findAll(trackOnMapContent).count()
        assertTrue(
            "TrackOnMapScreen must pass isBottomSheet = false to MapDetailLayout",
            trackOccurrences >= 1
        )
    }
}
