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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract and layout tests for [TrackingTabsScreen] and [SensorStatus] verifying landscape cockpit
 * vertical space optimization: centered status bar sensor icons, auto-hiding floating tab row overlay,
 * and portrait/configuration invariant preservation (REQ-UI-434 / TST-UI-399 / ATT-4773).
 */
class TrackingTabsLandscapeLayoutTest {

    private val trackingTabsSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
    }

    private val sensorStatusSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt")
        ).firstOrNull { it.exists() } ?: File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt")
    }

    @Test
    fun testSensorStatus_landscapeCenterStatusBarLayoutContract() {
        assertTrue("SensorStatus.kt source file must exist", sensorStatusSourceFile.exists())
        val content = sensorStatusSourceFile.readText()

        // 1. Must support isCompactLandscape parameter with default false (REQ-UI-434.1)
        assertTrue(
            "SensorStatus must declare isCompactLandscape parameter",
            content.contains("isCompactLandscape: Boolean = false")
        )

        // 2. Compact landscape branch must render max 28dp height row with centered icons (REQ-UI-434.1)
        val landscapeBranch = content.substringAfter("if (isCompactLandscape) {", "")
            .substringBefore("} else {", "")

        assertTrue(
            "Landscape SensorStatus must specify heightIn(max = 28.dp)",
            landscapeBranch.contains(".heightIn(max = 28.dp)")
        )
        assertTrue(
            "Landscape SensorStatus must center content horizontally",
            landscapeBranch.contains("horizontalArrangement = Arrangement.Center")
        )
        assertTrue(
            "Landscape SensorStatus must position menu button next to centered icons",
            landscapeBranch.contains("onMenuClick != null") && landscapeBranch.contains("imageVector = Icons.Default.Menu")
        )

        // 3. Portrait branch must retain left-aligned menu button and centered Box
        val portraitBranch = content.substringAfter("} else {", "")
            .substringBefore("// Source Dialog", "")

        assertTrue(
            "Portrait SensorStatus must retain left-aligned menu icon button",
            portraitBranch.contains("onMenuClick != null")
        )
        assertTrue(
            "Portrait SensorStatus must retain weighted Box for centered icons",
            portraitBranch.contains("modifier = Modifier.weight(1f)")
        )
    }

    @Test
    fun testTrackingTabsScreen_floatingTabRowOverlayAndAutoHideContract() {
        assertTrue("TrackingTabsScreen.kt source file must exist", trackingTabsSourceFile.exists())
        val content = trackingTabsSourceFile.readText()

        // 1. Orientation check and landscape tracking mode detection
        assertTrue(
            "TrackingTabsScreen must detect ORIENTATION_LANDSCAPE",
            content.contains("configuration.orientation == Configuration.ORIENTATION_LANDSCAPE")
        )
        assertTrue(
            "TrackingTabsScreen must declare isLandscapeTracking flag",
            content.contains("val isLandscapeTracking = isLandscape && screenMode == ScreenMode.TRACKING")
        )

        // 2. Auto-hide timer state and delay(2500)
        assertTrue(
            "TrackingTabsScreen must manage auto-hide delay (2500ms)",
            content.contains("delay(2500)")
        )
        assertTrue(
            "TrackingTabsScreen must track pager scroll in progress for tab row visibility",
            content.contains("pagerState.isScrollInProgress")
        )
        assertTrue(
            "TrackingTabsScreen must keep floating tab row visible on Control Tab (Page 0)",
            content.contains("val isControlTab = screenMode == ScreenMode.TRACKING && pagerState.currentPage == 0")
        )

        // 3. Floating overlay layout with zIndex(1f) and slide/fade animations
        assertTrue(
            "TrackingTabsScreen must place floating tab row at zIndex(1f)",
            content.contains(".zIndex(1f)")
        )
        assertTrue(
            "TrackingTabsScreen must align floating tab row to TopCenter",
            content.contains(".align(Alignment.TopCenter)")
        )
        assertTrue(
            "TrackingTabsScreen must animate floating tab row with slideInVertically and fadeIn",
            content.contains("slideInVertically") && content.contains("fadeIn")
        )
    }

    @Test
    fun testTrackingTabsScreen_portraitAndConfigurationModeInvariants() {
        assertTrue("TrackingTabsScreen.kt source file must exist", trackingTabsSourceFile.exists())
        val content = trackingTabsSourceFile.readText()

        // 1. Portrait mode retains statusBarsPadding in static header Column
        val portraitSection = content.substringAfter("} else {", "")
            .substringBefore("// First-time Stop Tracking Coachmark", "")

        assertTrue(
            "Portrait mode must retain statusBarsPadding() pushes header below status bar",
            portraitSection.contains(".statusBarsPadding()")
        )
        assertTrue(
            "Portrait mode must retain static tabRowContent() inside top Surface",
            portraitSection.contains("tabRowContent()")
        )

        // 2. Configuration and Preview headers preserved
        assertTrue(
            "ScreenMode.CONFIGURATION must retain TrackingTabConfigHeader",
            portraitSection.contains("TrackingTabConfigHeader(")
        )
        assertTrue(
            "ScreenMode.PREVIEW must retain TrackingTabPreviewHeader",
            portraitSection.contains("TrackingTabPreviewHeader(")
        )
    }
}
