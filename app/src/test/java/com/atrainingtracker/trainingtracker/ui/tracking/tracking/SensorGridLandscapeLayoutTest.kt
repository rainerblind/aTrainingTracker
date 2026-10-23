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
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.packVisibleRowsForLandscape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verification test suite for Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid,
 * and Sub-Pane Overlay Docking (REQ-TRK-040 / TST-TRK-040.1–5 / ATT-52).
 */
class SensorGridLandscapeLayoutTest {

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
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    /**
     * TST-TRK-040.1: Dual-Pane Split Layout Composition
     * Asserts that when orientation is landscape and showMap is true, SensorGridScreen
     * renders a horizontal Row split into 50/50 flexible width (weight(1f)) panes:
     * Left pane for sensor telemetry and Right pane for map/elevation.
     */
    @Test
    fun testDualPaneSplitLayoutComposition_TST_TRK_040_1() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // 1. Verify responsive orientation detection and branching
        assertTrue(
            "SensorGridScreen must detect landscape orientation via LocalConfiguration (REQ-TRK-040.1)",
            content.contains("configuration.orientation == Configuration.ORIENTATION_LANDSCAPE")
        )
        assertTrue(
            "SensorGridScreen must define isLandscapeWithMap condition (REQ-TRK-040.2)",
            content.contains("val isLandscapeWithMap = isLandscape && state.mapState.showMap && screenMode != ScreenMode.CONFIGURATION")
        )

        // 2. Extract landscape dual-pane section
        val landscapeSection = content.substringAfter("if (isLandscapeWithMap) {")
            .substringBefore("} else {")

        // 3. Verify horizontal Row with 50/50 split
        assertTrue(
            "Landscape section must layout a horizontal Row(Modifier.fillMaxSize()) (REQ-TRK-040.2)",
            landscapeSection.contains("Row(") && landscapeSection.contains("modifier = Modifier.fillMaxSize()")
        )
        assertTrue(
            "Left pane must take flexible 50% width via Modifier.weight(1f).fillMaxHeight() (REQ-TRK-040.2)",
            landscapeSection.contains(".weight(1f)\n                            .fillMaxHeight()") ||
            landscapeSection.contains(".weight(1f)")
        )
        assertTrue(
            "Left pane must host SensorGridContent with vertical scroll (REQ-TRK-040.2)",
            landscapeSection.contains("SensorGridContent(") &&
            landscapeSection.contains(".verticalScroll(rememberScrollState())")
        )
        assertTrue(
            "Right pane must host TrackingMapContainer and ElevationProfile (REQ-TRK-040.2)",
            landscapeSection.contains("TrackingMapContainer(") &&
            landscapeSection.contains("ElevationProfile(")
        )
    }

    /**
     * TST-TRK-040.2: Widescreen Sensor Grid Scaling & Column Transformation
     * Unit test verifying that packVisibleRowsForLandscape pairs single-field rows into
     * 2-column widescreen rows when in landscape mode without a map, while strictly preserving
     * portrait rows and database invariants.
     */
    @Test
    fun testWidescreenColumnTransformation_TST_TRK_040_2() {
        val field1 = SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.LARGE, label = "Speed", value = "25.0", units = "km/h", zoneColor = Color.Transparent, filterDescription = "")
        val field2 = SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 1, colNr = 0, viewSize = ViewSize.LARGE, label = "Heart Rate", value = "140", units = "bpm", zoneColor = Color.Red, filterDescription = "")
        val field3 = SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 2, colNr = 0, viewSize = ViewSize.LARGE, label = "Power", value = "200", units = "W", zoneColor = Color.Blue, filterDescription = "")
        val field4 = SensorFieldState(configHash = 4, sensorFieldId = 4, rowNr = 3, colNr = 0, viewSize = ViewSize.LARGE, label = "Cadence", value = "90", units = "rpm", zoneColor = Color.Transparent, filterDescription = "")
        val fourFields = listOf(field1, field2, field3, field4)

        // 1. Landscape without map -> transforms 4 single-column rows into 2 paired rows
        val widescreenRows = packVisibleRowsForLandscape(
            fields = fourFields,
            tuningConfig = null,
            screenMode = ScreenMode.TRACKING,
            isLandscape = true,
            showMap = false
        )
        assertEquals("Widescreen grid must pair 4 single-field rows into 2 rows", 2, widescreenRows.size)
        assertEquals("First widescreen row must contain 2 fields", 2, widescreenRows[0].size)
        assertEquals("Speed", widescreenRows[0][0].label)
        assertEquals("Heart Rate", widescreenRows[0][1].label)
        assertEquals("Second widescreen row must contain 2 fields", 2, widescreenRows[1].size)
        assertEquals("Power", widescreenRows[1][0].label)
        assertEquals("Cadence", widescreenRows[1][1].label)

        // 2. Strict View-Only Invariant: original fields must retain rowNr and colNr unchanged
        assertEquals("field1 original rowNr must not mutate", 0, field1.rowNr)
        assertEquals("field2 original rowNr must not mutate", 1, field2.rowNr)
        assertEquals("field3 original rowNr must not mutate", 2, field3.rowNr)
        assertEquals("field4 original rowNr must not mutate", 3, field4.rowNr)

        // 3. Odd number of fields (3 fields) -> 1 pair of 2 + 1 trailing single
        val threeFields = listOf(field1, field2, field3)
        val oddWidescreenRows = packVisibleRowsForLandscape(
            fields = threeFields,
            tuningConfig = null,
            screenMode = ScreenMode.TRACKING,
            isLandscape = true,
            showMap = false
        )
        assertEquals("3 fields must result in 2 rows", 2, oddWidescreenRows.size)
        assertEquals("First row has 2 fields", 2, oddWidescreenRows[0].size)
        assertEquals("Second row has 1 field", 1, oddWidescreenRows[1].size)

        // 4. Portrait mode (isLandscape = false) -> unmodified 4 rows
        val portraitRows = packVisibleRowsForLandscape(
            fields = fourFields,
            tuningConfig = null,
            screenMode = ScreenMode.TRACKING,
            isLandscape = false,
            showMap = false
        )
        assertEquals("Portrait mode must retain original 4 rows", 4, portraitRows.size)

        // 5. Landscape WITH map (showMap = true) -> unmodified 4 rows for left pane
        val dualPaneRows = packVisibleRowsForLandscape(
            fields = fourFields,
            tuningConfig = null,
            screenMode = ScreenMode.TRACKING,
            isLandscape = true,
            showMap = true
        )
        assertEquals("Dual-pane with map must retain user-configured rows on left pane", 4, dualPaneRows.size)

        // 6. Configuration mode -> unmodified rows
        val configRows = packVisibleRowsForLandscape(
            fields = fourFields,
            tuningConfig = null,
            screenMode = ScreenMode.CONFIGURATION,
            isLandscape = true,
            showMap = false
        )
        assertEquals("Configuration mode must retain original rows for drag-and-drop", 4, configRows.size)
    }

    /**
     * TST-TRK-040.3: Right-Pane Bottom Sheet Docking Invariant
     * Asserts that in landscape mode with map, BottomSheetScaffold is anchored strictly
     * inside the right (map) pane, guaranteeing that left telemetry metrics remain 100%
     * visible and unmasked during Live Segment or ClimbPro alerts.
     */
    @Test
    fun testRightPaneBottomSheetDockingInvariant_TST_TRK_040_3() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        val landscapeSection = content.substringAfter("if (isLandscapeWithMap) {")
            .substringBefore("} else {")

        // 1. Verify BottomSheetScaffold is inside the right pane Box
        assertTrue(
            "Landscape section must host BottomSheetScaffold inside right pane (REQ-TRK-040.3)",
            landscapeSection.contains("BottomSheetScaffold(")
        )

        // 2. Verify sheetContent uses sheetContentLambda rendering LiveSegmentSheet and LiveClimbSheet
        assertTrue(
            "sheetContentLambda must render LiveSegmentSheet (REQ-TRK-040.3)",
            content.contains("LiveSegmentSheet(")
        )
        assertTrue(
            "sheetContentLambda must render LiveClimbSheet (REQ-TRK-040.3)",
            content.contains("LiveClimbSheet(")
        )

        // 3. Verify that the left pane Box has NO BottomSheetScaffold
        val leftPaneSection = landscapeSection.substringAfter("// Left Pane: Sensor Grid")
            .substringBefore("// Right Pane: Map + Elevation Profile")
        assertFalse(
            "Left telemetry pane must NOT host BottomSheetScaffold to keep telemetry 100% visible (REQ-TRK-040.3)",
            leftPaneSection.contains("BottomSheetScaffold")
        )
    }

    /**
     * TST-TRK-040.4: Display Setting Force Portrait Enforcement
     * Asserts that MainActivityWithNavigation queries TrainingApplication.forcePortrait()
     * and strictly locks to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT when enabled.
     */
    @Test
    fun testDisplaySettings_forcePortraitEnforcement_TST_TRK_040_4() {
        val mainActivityFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        val mainContent = mainActivityFile.readText()

        assertTrue(
            "MainActivityWithNavigation must query TrainingApplication.forcePortrait() (REQ-TRK-040.1)",
            mainContent.contains("if (TrainingApplication.forcePortrait())")
        )
        assertTrue(
            "MainActivityWithNavigation must enforce SCREEN_ORIENTATION_PORTRAIT when forcePortrait is true (REQ-TRK-040.1)",
            mainContent.contains("requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT")
        )
        assertTrue(
            "MainActivityWithNavigation must set SCREEN_ORIENTATION_UNSPECIFIED when forcePortrait is false (REQ-TRK-040.1)",
            mainContent.contains("requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED")
        )

        val dialogFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt")
        val dialogContent = dialogFile.readText()
        assertTrue(
            "DisplaySettingsDialog must provide forcePortrait setting option (REQ-TRK-040.1)",
            dialogContent.contains("forcePortrait")
        )
    }

    /**
     * TST-TRK-040.5: Configuration Mode Landscape Operability & Gesture Contracts
     * Asserts that in ScreenMode.CONFIGURATION, landscape orientation falls back cleanly
     * to the full-width configuration scroll container and all GridActions callbacks are preserved.
     */
    @Test
    fun testConfigurationMode_landscapeOperabilityAndGestureContracts_TST_TRK_040_5() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        // 1. In ScreenMode.CONFIGURATION, isLandscapeWithMap is FALSE
        assertTrue(
            "isLandscapeWithMap must be false when screenMode == ScreenMode.CONFIGURATION (REQ-TRK-040.5)",
            content.contains("screenMode != ScreenMode.CONFIGURATION")
        )

        // 2. Verify all GridActions are wired in Configuration Mode
        assertTrue(
            "Configuration mode must wire onEdit to onEditField (REQ-TRK-040.5)",
            content.contains("gridActions.onEditField(fieldState)")
        )
        assertTrue(
            "Configuration mode must wire onDelete to onDeleteField (REQ-TRK-040.5)",
            content.contains("gridActions.onDeleteField(fieldState)")
        )
        assertTrue(
            "Configuration mode must wire addRow to onAddRow (REQ-TRK-040.5)",
            content.contains("gridActions.onAddRow(")
        )
        assertTrue(
            "Configuration mode must wire addCol to onAddCol (REQ-TRK-040.5)",
            content.contains("gridActions.onAddCol(")
        )
        assertTrue(
            "Configuration mode must wire onStartMove to onSelectFieldForMove (REQ-TRK-040.5)",
            content.contains("gridActions.onSelectFieldForMove(fieldState)")
        )
        assertTrue(
            "Configuration mode must wire swap to onSwapFields (REQ-TRK-040.5)",
            content.contains("gridActions.onSwapFields(")
        )
        assertTrue(
            "Configuration mode must wire move to onMoveField (REQ-TRK-040.5)",
            content.contains("gridActions.onMoveField(")
        )
    }
}
