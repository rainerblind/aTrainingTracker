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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying [TrackingTabsPageResolver] initial page resolution across
 * screen mode transitions, activity type switching, and tab list mutations (REQ-UI-435, TST-UI-435, ATT-4838).
 */
class TrackingTabsPageResolverTest {

    // --- TST-UI-435.1: Drawer Configuration Entry Resolution ---

    @Test
    fun resolveInitialPage_enteringConfigurationFromTracking_alwaysResolvesToFirstTab() {
        // Given athlete was viewing Control Tab (page 0) in tracking
        val pageFromControl = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.TRACKING,
            newMode = ScreenMode.CONFIGURATION,
            lastKnownPage = 0,
            tabCount = 3
        )
        assertEquals("Entering config from Control Tab must start at page 0", 0, pageFromControl)

        // Given athlete was viewing Sensor Tab 0 (page 1) in tracking
        val pageFromSensorTab0 = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.TRACKING,
            newMode = ScreenMode.CONFIGURATION,
            lastKnownPage = 1,
            tabCount = 3
        )
        assertEquals("Entering config from Sensor Tab 0 must start at page 0", 0, pageFromSensorTab0)

        // Given athlete was viewing Sensor Tab 1 (page 2) in tracking
        val pageFromSensorTab1 = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.TRACKING,
            newMode = ScreenMode.CONFIGURATION,
            lastKnownPage = 2,
            tabCount = 3
        )
        assertEquals("Entering config from Sensor Tab 1 must start at page 0", 0, pageFromSensorTab1)
    }

    @Test
    fun resolveInitialPage_sportChangeWithinConfiguration_resetsToFirstTab() {
        // Given athlete was editing tab 2 in configuration, and switched sport via drawer
        val resolved = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.CONFIGURATION,
            newMode = ScreenMode.CONFIGURATION,
            lastKnownPage = 2,
            tabCount = 3,
            activityTypeChanged = true
        )
        assertEquals("Sport type change in configuration must reset to page 0", 0, resolved)
    }

    // --- TST-UI-435.2: Editor-Preview Round-Trip Resolution ---

    @Test
    fun resolveInitialPage_togglingBetweenConfigurationAndPreview_preservesActiveTabIndex() {
        // CONFIGURATION -> PREVIEW preserves page 1
        val configToPreview = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.CONFIGURATION,
            newMode = ScreenMode.PREVIEW,
            lastKnownPage = 1,
            tabCount = 3
        )
        assertEquals("Switching from CONFIG to PREVIEW must preserve active tab index", 1, configToPreview)

        // PREVIEW -> CONFIGURATION preserves page 2
        val previewToConfig = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.PREVIEW,
            newMode = ScreenMode.CONFIGURATION,
            lastKnownPage = 2,
            tabCount = 3
        )
        assertEquals("Switching from PREVIEW to CONFIG must preserve active tab index", 2, previewToConfig)
    }

    @Test
    fun resolveInitialPage_editorPreviewToggling_clampsOutOfBoundsPage() {
        val clamped = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.CONFIGURATION,
            newMode = ScreenMode.PREVIEW,
            lastKnownPage = 10,
            tabCount = 3
        )
        assertEquals("Out-of-bounds page must be clamped to max tab index (2)", 2, clamped)
    }

    // --- TST-UI-435.3: Preview Exit to Tracking Resolution ---

    @Test
    fun resolveInitialPage_exitingPreviewToTracking_mapsSensorTabToCockpitPageWithOffset() {
        // Previewing Sensor Tab 0 (page 0) maps to page 1 in tracking cockpit (Control Tab is at 0)
        val fromSensorTab0 = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.PREVIEW,
            newMode = ScreenMode.TRACKING,
            lastKnownPage = 0,
            tabCount = 3
        )
        assertEquals("Previewing sensor tab 0 maps to page 1 in tracking", 1, fromSensorTab0)

        // Previewing Sensor Tab 1 (page 1) maps to page 2 in tracking cockpit
        val fromSensorTab1 = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.PREVIEW,
            newMode = ScreenMode.TRACKING,
            lastKnownPage = 1,
            tabCount = 3
        )
        assertEquals("Previewing sensor tab 1 maps to page 2 in tracking", 2, fromSensorTab1)
    }

    @Test
    fun resolveInitialPage_exitingPreviewToTracking_handlesEmptyTabCountCleanly() {
        val emptyResolved = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.PREVIEW,
            newMode = ScreenMode.TRACKING,
            lastKnownPage = 0,
            tabCount = 0
        )
        assertEquals("Empty tab count must resolve to page 0 in tracking", 0, emptyResolved)
    }

    // --- TST-UI-435.4: Same-Mode Stability & Tab Count Mutation ---

    @Test
    fun resolveInitialPage_sameModeTracking_preservesAndClampsPage() {
        val trackingResolved = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.TRACKING,
            newMode = ScreenMode.TRACKING,
            lastKnownPage = 2,
            tabCount = 3
        )
        assertEquals("Tracking mode preserves valid page 2", 2, trackingResolved)

        val trackingClamped = TrackingTabsPageResolver.resolveInitialPage(
            previousMode = ScreenMode.TRACKING,
            newMode = ScreenMode.TRACKING,
            lastKnownPage = 10,
            tabCount = 3
        )
        assertEquals("Tracking mode clamps page 10 to pageCount 3", 3, trackingClamped)
    }
}
