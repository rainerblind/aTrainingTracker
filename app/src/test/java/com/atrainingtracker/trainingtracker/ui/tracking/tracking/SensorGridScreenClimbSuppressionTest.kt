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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying tracking tab live climb and segment bottom sheet
 * suppression, active-tab parameterization, and state synchronization (REQ-UI-327, TST-UI-287 / ATT-2945).
 */
class SensorGridScreenClimbSuppressionTest {

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

    @Test
    fun testSensorGridScreen_acceptsIsTabActiveParameterWithDefaultTrue() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        assertTrue(
            "SensorGridScreen must accept isTabActive: Boolean = true parameter",
            content.contains("isTabActive: Boolean = true")
        )
    }

    @Test
    fun testSensorGridScreen_shouldShowBottomSheet_incorporatesTabActiveAndScreenMode() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        assertTrue(
            "SensorGridScreen must gate shouldShowBottomSheet on isTabActive, segments/climbs, and TRACKING mode",
            content.contains("val shouldShowBottomSheet = isTabActive && (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING")
        )
    }

    @Test
    fun testSensorGridScreen_scaffoldState_initializesWithConditionalHiddenState() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        assertTrue(
            "initialSheetValue must be PartiallyExpanded when shouldShowBottomSheet is true, else Hidden",
            content.contains("val initialSheetValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden")
        )
        assertTrue(
            "scaffoldState must initialize with initialSheetValue",
            content.contains("initialValue = initialSheetValue")
        )
    }

    @Test
    fun testSensorGridScreen_launchedEffect_drivesSheetStateHideAndPartialExpand() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        assertTrue(
            "LaunchedEffect must listen to shouldShowBottomSheet",
            content.contains("LaunchedEffect(shouldShowBottomSheet)")
        )
        assertTrue(
            "LaunchedEffect must call scaffoldState.bottomSheetState.hide() when shouldShowBottomSheet is false",
            content.contains("scaffoldState.bottomSheetState.hide()")
        )
        assertTrue(
            "LaunchedEffect must call scaffoldState.bottomSheetState.partialExpand() when shouldShowBottomSheet is true",
            content.contains("scaffoldState.bottomSheetState.partialExpand()")
        )
    }

    @Test
    fun testSensorGridScreen_suppressesPeekHeightAndSwipeGesturesWhenInactive() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        assertTrue(
            "sheetContainerColor must resolve to MaterialTheme.colorScheme.surface",
            content.contains("sheetContainerColor = MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "sheetShadowElevation must resolve to BottomSheetDesign.SheetShadowElevation",
            content.contains("sheetShadowElevation = BottomSheetDesign.SheetShadowElevation")
        )
        assertTrue(
            "sheetTonalElevation must resolve to BottomSheetDesign.SheetTonalElevation",
            content.contains("sheetTonalElevation = BottomSheetDesign.SheetTonalElevation")
        )
        assertTrue(
            "sheetPeekHeight must resolve to 0.dp when shouldShowBottomSheet is false",
            content.contains("sheetPeekHeight = if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp")
        )
        assertTrue(
            "sheetSwipeEnabled must incorporate isTabActive",
            content.contains("sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING && isTabActive")
        )
    }

    @Test
    fun testTrackingScreenState_defaultsShowLiveClimbsToTrueForBackwardCompatibility() {
        val defaultState = TrackingScreenState()
        assertTrue(
            "TrackingScreenState must default showLiveClimbs to true for backward compatibility (REQ-UI-275)",
            defaultState.showLiveClimbs
        )
    }

    @Test
    fun testTrackingTabsScreen_passesIsTabActiveDerivedFromCurrentPage() {
        val tabsFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        val content = tabsFile.readText()

        assertTrue(
            "TrackingTabsScreen must pass isTabActive = pagerState.currentPage == page to TrackingTabGridContent",
            content.contains("isTabActive = pagerState.currentPage == page")
        )
    }
}
