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
 * Visual contract tests verifying that scaffolds, modal sheets, and dialogs consume
 * [BottomSheetDesign] tokens, surface background colors, and zero tonal elevation
 * (REQ-UI-189, REQ-UI-196, REQ-UI-218, TST-UI-172, ATT-1817).
 */
class BottomSheetVisualContractTest {

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
    fun testSensorGridScreen_consumesBottomSheetDesignTokens() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must set sheetShape to BottomSheetDesign.SheetShape",
            content.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "SensorGridScreen must set sheetContainerColor to surface",
            content.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "SensorGridScreen must set sheetShadowElevation to BottomSheetDesign.SheetShadowElevation",
            content.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "SensorGridScreen must set sheetTonalElevation to BottomSheetDesign.SheetTonalElevation",
            content.contains("sheetTonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
        assertTrue(
            "SensorGridScreen must apply sheetContour to live segment sheet container",
            content.contains("sheetContour()")
        )
    }

    @Test
    fun testMapDetailLayout_appliesSheetShapeAndSurfaceBackground() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        val content = file.readText()

        assertTrue(
            "MapDetailLayout must use BottomSheetDesign.SheetShape when !useStatusBarsPadding",
            content.contains("shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "MapDetailLayout must apply surface background when !useStatusBarsPadding",
            content.contains("if (!useStatusBarsPadding) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier")
        )
        assertTrue(
            "MapDetailLayout header Surface must bind surface color",
            content.contains("color = MaterialTheme.colorScheme.surface")
        )
    }

    @Test
    fun testFilterBottomSheetScaffold_consumesBottomSheetDesignTokens() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterBottomSheetScaffold.kt")
        val content = file.readText()

        assertTrue(
            "FilterBottomSheetScaffold must set shape to BottomSheetDesign.SheetShape",
            content.contains("shape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "FilterBottomSheetScaffold must set containerColor to surface",
            content.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "FilterBottomSheetScaffold must set tonalElevation to BottomSheetDesign.SheetTonalElevation",
            content.contains("tonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
    }

    @Test
    fun testAppModalBottomSheet_consumesBottomSheetDesignTokensAndBorder() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt")
        val content = file.readText()

        assertTrue(
            "AppModalBottomSheet must set shape to BottomSheetDesign.SheetShape",
            content.contains("shape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "AppModalBottomSheet must set containerColor to surface",
            content.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "AppModalBottomSheet must set tonalElevation to BottomSheetDesign.SheetTonalElevation",
            content.contains("tonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
        assertTrue(
            "AppBottomSheetContent must set border with BorderWidth",
            content.contains("BorderStroke(BottomSheetDesign.BorderWidth")
        )
        assertTrue(
            "AppBottomSheetContent must set color to surface",
            content.contains("color = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "AppBottomSheetContent must set tonalElevation to BottomSheetDesign.SheetTonalElevation",
            content.contains("tonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
    }

    @Test
    fun testPersistentMapScaffolds_consumeBottomSheetDesignTokens() {
        val mapWithTrack = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()
        assertTrue(
            "MapScreenWithTrack must set sheetShape",
            mapWithTrack.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "MapScreenWithTrack must set sheetContainerColor",
            mapWithTrack.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "MapScreenWithTrack must set sheetShadowElevation",
            mapWithTrack.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "MapScreenWithTrack must set sheetTonalElevation",
            mapWithTrack.contains("sheetTonalElevation = BottomSheetDesign.SheetTonalElevation")
        )

        val periodMap = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt").readText()
        assertTrue(
            "PeriodMapScreen must set sheetShape",
            periodMap.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "PeriodMapScreen must set sheetContainerColor",
            periodMap.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "PeriodMapScreen must set sheetShadowElevation",
            periodMap.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "PeriodMapScreen must set sheetTonalElevation",
            periodMap.contains("sheetTonalElevation = BottomSheetDesign.SheetTonalElevation")
        )

        val clusterMap = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt").readText()
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetShape",
            clusterMap.contains("sheetShape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetContainerColor",
            clusterMap.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetShadowElevation",
            clusterMap.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen must set sheetTonalElevation",
            clusterMap.contains("sheetTonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
    }

    @Test
    fun testConfirmationDialogs_consumeSurfaceAndZeroTonalElevation() {
        val stravaDialog = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt").readText()
        assertTrue(
            "StravaSettingsDialog AlertDialog must set containerColor to surface",
            stravaDialog.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "StravaSettingsDialog AlertDialog must set tonalElevation to 0.dp",
            stravaDialog.contains("tonalElevation = 0.dp")
        )

        val clusterHeatmap = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt").readText()
        assertTrue(
            "WorkoutClusterHeatmapScreen AlertDialog must set containerColor to surface",
            clusterHeatmap.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen AlertDialog must set tonalElevation to 0.dp",
            clusterHeatmap.contains("tonalElevation = 0.dp")
        )

        val workoutClusters = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt").readText()
        assertTrue(
            "WorkoutClustersScreen AlertDialog must set containerColor to surface",
            workoutClusters.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "WorkoutClustersScreen AlertDialog must set tonalElevation to 0.dp",
            workoutClusters.contains("tonalElevation = 0.dp")
        )

        val devicesScreen = resolveSourceFile("app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt").readText()
        assertTrue(
            "DevicesTabbedScreen AlertDialog must set containerColor to surface",
            devicesScreen.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "DevicesTabbedScreen AlertDialog must set tonalElevation to 0.dp",
            devicesScreen.contains("tonalElevation = 0.dp")
        )

        val importBackup = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt").readText()
        assertTrue(
            "ImportBackupTabsScreen AlertDialog must set containerColor to surface",
            importBackup.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "ImportBackupTabsScreen AlertDialog must set tonalElevation to 0.dp",
            importBackup.contains("tonalElevation = 0.dp")
        )
    }

    @Test
    fun testScreens_consumeStandardizedPeekHeightTokens() {
        // MapScreenWithTrack.kt
        val mapScreen = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt").readText()
        assertTrue(
            "MapScreenWithTrack must consume BottomSheetDesign.PeekHeightSegment",
            mapScreen.contains("BottomSheetDesign.PeekHeightSegment")
        )
        assertTrue(
            "MapScreenWithTrack must consume BottomSheetDesign.PeekHeightRoute",
            mapScreen.contains("BottomSheetDesign.PeekHeightRoute")
        )
        assertTrue(
            "MapScreenWithTrack must consume BottomSheetDesign.PeekHeightKnownLocation",
            mapScreen.contains("BottomSheetDesign.PeekHeightKnownLocation")
        )
        assertTrue(
            "MapScreenWithTrack must not contain hardcoded 185.dp peek height",
            !mapScreen.contains("185.dp + navBarHeight")
        )

        // SensorGridScreen.kt
        val sensorGrid = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt").readText()
        assertTrue(
            "SensorGridScreen must consume BottomSheetDesign.PeekHeightLiveSegment",
            sensorGrid.contains("BottomSheetDesign.PeekHeightLiveSegment")
        )
        assertTrue(
            "SensorGridScreen must not contain hardcoded 140.dp peek height",
            !sensorGrid.contains("140.dp + navBarHeight")
        )

        // WorkoutClusterHeatmapScreen.kt
        val clusterHeatmap = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt").readText()
        assertTrue(
            "WorkoutClusterHeatmapScreen must consume BottomSheetDesign.PeekHeightWorkout",
            clusterHeatmap.contains("BottomSheetDesign.PeekHeightWorkout")
        )
        assertTrue(
            "WorkoutClusterHeatmapScreen must not contain hardcoded 120.dp peek height",
            !clusterHeatmap.contains("120.dp + navBarHeight")
        )

        // PeriodMapScreen.kt
        val periodMap = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt").readText()
        assertTrue(
            "PeriodMapScreen must consume BottomSheetDesign.PeekHeightWorkout",
            periodMap.contains("BottomSheetDesign.PeekHeightWorkout")
        )
        assertTrue(
            "PeriodMapScreen must not contain hardcoded 120.dp peek height",
            !periodMap.contains("120.dp + navBarHeight")
        )
    }
}
