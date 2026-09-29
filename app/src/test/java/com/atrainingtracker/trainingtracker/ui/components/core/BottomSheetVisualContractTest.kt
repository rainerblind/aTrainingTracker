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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual contract tests verifying that scaffolds and modal sheets consume
 * [BottomSheetDesign] tokens and contouring (REQ-UI-189, TST-UI-143.2, ATT-1588).
 */
class BottomSheetVisualContractTest {

    @Test
    fun testSensorGridScreen_consumesBottomSheetDesignTokens() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must set sheetShape to BottomSheetDesign.SheetShape",
            content.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "SensorGridScreen must set sheetShadowElevation to BottomSheetDesign.SheetShadowElevation",
            content.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "SensorGridScreen must apply sheetContour to live segment sheet container",
            content.contains("sheetContour()")
        )
    }

    @Test
    fun testMapDetailLayout_appliesSheetShapeWhenInSheetMode() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        assertTrue("MapDetailLayout.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "MapDetailLayout must use BottomSheetDesign.SheetShape when !useStatusBarsPadding",
            content.contains("shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape")
        )
    }

    @Test
    fun testFilterBottomSheetScaffold_consumesBottomSheetDesignTokens() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterBottomSheetScaffold.kt")
        assertTrue("FilterBottomSheetScaffold.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "FilterBottomSheetScaffold must set shape to BottomSheetDesign.SheetShape",
            content.contains("shape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "FilterBottomSheetScaffold must set tonalElevation to BottomSheetDesign.SheetTonalElevation",
            content.contains("tonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
    }

    @Test
    fun testAppModalBottomSheet_consumesBottomSheetDesignTokensAndBorder() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt")
        assertTrue("AppModalBottomSheet.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "AppModalBottomSheet must set shape to BottomSheetDesign.SheetShape",
            content.contains("shape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "AppBottomSheetContent must set border with BorderWidth",
            content.contains("BorderStroke(BottomSheetDesign.BorderWidth")
        )
    }

    @Test
    fun testPersistentMapScaffolds_consumeBottomSheetDesignTokens() {
        val mapWithTrack = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()
        assertTrue(
            "MapScreenWithTrack must set sheetShape",
            mapWithTrack.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "MapScreenWithTrack must set sheetShadowElevation",
            mapWithTrack.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )

        val periodMap = File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt").readText()
        assertTrue(
            "PeriodMapScreen must set sheetShape",
            periodMap.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "PeriodMapScreen must set sheetShadowElevation",
            periodMap.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )

        val clusterMap = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt").readText()
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetShape",
            clusterMap.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetShadowElevation",
            clusterMap.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
    }
}
