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

package com.atrainingtracker.trainingtracker.ui.tracking

import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingScreenState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Architectural and contract test verifying the unified WYSIWYG tracking tab configuration
 * with spatial overlays, per-tab popup toggles, and runtime gating (REQ-UI-275, TST-UI-235.3).
 */
class TrackingTabWysiwygContractTest {

    @Test
    fun testTrackingScreenState_defaultsAreBackwardCompatible() {
        val defaultState = TrackingScreenState()
        assertTrue("showLiveClimbs must default to true for backward compatibility", defaultState.showLiveClimbs)
        assertTrue("showNavigationHints must default to true for backward compatibility", defaultState.showNavigationHints)
        assertTrue("showLapButton must default to true for backward compatibility", defaultState.showLapButton)
    }

    @Test
    fun testTrackingTabConfigHeader_removedGenericCheckboxes() {
        val headerFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt")
        assertTrue("TrackingTabConfigHeader.kt must exist", headerFile.exists())
        val content = headerFile.readText()

        assertFalse("TrackingTabConfigHeader must not contain ConfigCheckbox", content.contains("ConfigCheckbox"))
        assertFalse("TrackingTabConfigHeader must not contain Checkbox composable", content.contains("Checkbox("))
        assertFalse("TrackingTabConfigHeader must not contain FlowRow", content.contains("FlowRow"))
    }

    @Test
    fun testSensorGridScreen_definesSpatialWysiwygToggles() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue("SensorGridScreen must define SpatialCockpitToggleCard", content.contains("fun SpatialCockpitToggleCard("))
        assertTrue("SensorGridScreen must bind navigation hints toggle", content.contains("config_tracking__show_navigation_hints"))
        assertTrue("SensorGridScreen must bind map toggle", content.contains("config_tracking__show_map"))
        assertTrue("SensorGridScreen must bind elevation profile toggle", content.contains("config_tracking__showElevationProfile"))
        assertTrue("SensorGridScreen must bind live segments toggle", content.contains("config_tracking__showLiveSegments"))
        assertTrue("SensorGridScreen must bind live climbs toggle", content.contains("config_tracking__show_live_climbs"))
        assertTrue("SensorGridScreen must bind lap button toggle", content.contains("config_tracking__showLapButton"))
    }

    @Test
    fun testSensorGridScreen_enforcesRuntimeGating() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue(
            "TurnPromptBanner must be gated by state.showNavigationHints",
            content.contains("promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled")
        )
        assertTrue(
            "LiveClimbSheet must be gated by state.showLiveClimbs",
            content.contains("state.showLiveClimbs && tuningConfig.showLiveClimbs")
        )
        assertTrue(
            "ForkDecisionCard must be gated by state.showNavigationHints (REQ-UI-321 / ATT-2874)",
            content.contains("if (state.showNavigationHints)") && content.contains("ForkDecisionCard(")
        )
    }

    @Test
    fun testSensorGridScreen_configurationMode_unifiedScrollableContainer() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue(
            "Configuration mode must declare unified verticalScroll container",
            content.contains("screenMode == ScreenMode.CONFIGURATION") &&
            content.contains(".verticalScroll(rememberScrollState())") &&
            content.contains(".navigationBarsPadding()")
        )
    }

    @Test
    fun testSensorGridScreen_configurationMode_excludesLiveMapAndElevationProfile() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        // ATrainingTrackerMap must only be present in the non-CONFIGURATION branch
        val configIndex = content.indexOf("if (screenMode == ScreenMode.CONFIGURATION)")
        val elseIndex = content.indexOf("} else {", configIndex)
        val mapIndex = content.indexOf("ATrainingTrackerMap(", elseIndex)

        assertTrue("Configuration branch must exist", configIndex != -1)
        assertTrue("Else branch must exist", elseIndex != -1)
        assertTrue("ATrainingTrackerMap must only reside in the tracking/preview else branch", mapIndex > elseIndex)
    }

    @Test
    fun testSensorGridScreen_isolatesBottomSheetScaffoldToTrackingMode() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue(
            "sheetPeekHeight must be gated to ScreenMode.TRACKING",
            content.contains("screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment")
        )
        assertTrue(
            "sheetSwipeEnabled must be gated to ScreenMode.TRACKING",
            content.contains("sheetSwipeEnabled = (showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING")
        )
        assertTrue(
            "sheetContent must be gated to ScreenMode.TRACKING",
            content.contains("screenMode == ScreenMode.TRACKING && showLiveSegments") &&
            content.contains("screenMode == ScreenMode.TRACKING && showLiveClimbs")
        )
    }

    @Test
    fun testSensorGridScreen_configurationMode_fullWidthTogglesEliminatesTwoColumnRows() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        val configIndex = content.indexOf("if (screenMode == ScreenMode.CONFIGURATION)")
        val elseIndex = content.indexOf("// TRACKING & PREVIEW MODES", configIndex)
        val configContent = content.substring(configIndex, elseIndex)

        val togglesIndex = content.indexOf("Spatial WYSIWYG Toggles for Map, Elevation, Segments, Climbs & Lap Button")
        assertTrue("Toggles section must exist", togglesIndex != -1)
        val togglesContent = content.substring(togglesIndex, elseIndex)

        // Verify that toggles are rendered full width in a unified Column
        assertTrue("Must declare unified Column for spatial toggles", togglesContent.contains("Column("))
        assertFalse("Must eliminate 2-column side-by-side Row constructs for spatial toggles in configuration mode",
            togglesContent.contains("Modifier.weight(1f)")
        )
        assertFalse("Must eliminate arbitrary nested grey dock Surface container",
            togglesContent.contains("Spatial WYSIWYG Dock for Live Segments")
        )
        assertTrue("Map toggle must be full width", togglesContent.contains("show_map") && togglesContent.contains("fillMaxWidth()"))
        assertTrue("Elevation toggle must be full width", togglesContent.contains("showElevationProfile") && togglesContent.contains("fillMaxWidth()"))
        assertTrue("Live Segments toggle must be full width", togglesContent.contains("showLiveSegments") && togglesContent.contains("fillMaxWidth()"))
        assertTrue("Live Climbs toggle must be full width", togglesContent.contains("show_live_climbs") && togglesContent.contains("fillMaxWidth()"))
        assertTrue("Lap Button toggle must be full width", togglesContent.contains("showLapButton") && togglesContent.contains("fillMaxWidth()"))
    }

    @Test
    fun testTrackingTabsScreen_suppressesLapButtonInConfigurationMode() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue("TrackingTabsScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        assertTrue(
            "shouldShowLapButton must suppress lap button in CONFIGURATION mode",
            content.contains("shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION")
        )
    }
}
