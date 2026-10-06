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
    }
}
