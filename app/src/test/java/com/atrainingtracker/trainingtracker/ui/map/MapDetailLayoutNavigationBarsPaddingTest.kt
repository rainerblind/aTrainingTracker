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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests for [MapDetailLayout] and bottom sheet popups
 * verifying navigation bar inset clearance and upward travel calibration
 * as mandated by REQ-UI-237 and TST-UI-196 (ATT-2008).
 */
class MapDetailLayoutNavigationBarsPaddingTest {

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

    private val liveSegmentSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt")
    }

    private val mapScreenWithTrackFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt")
    }

    private val sensorGridScreenFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
    }

    @Test
    fun testMapDetailLayout_appliesNavigationBarsPaddingToElevationProfileWhenAnalyticsContentIsNull() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout lowerColumn must conditionally apply navigationBarsPadding when analyticsContent is null (REQ-UI-237)",
            content.contains("if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier")
        )
    }

    @Test
    fun testMapDetailLayout_preservesNavigationBarsPaddingOnAnalyticsContent() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must retain navigationBarsPadding on analyticsContent (REQ-UI-205, REQ-UI-237)",
            content.contains("analyticsContent?.let { content ->") &&
            content.contains("modifier = Modifier.fillMaxWidth().navigationBarsPadding()")
        )
    }

    @Test
    fun testMapDetailLayout_providesDefensiveSpacerWhenBothElevationAndAnalyticsAreAbsent() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must provide defensive Spacer with navigationBarsPadding when both elevation and analytics are absent in bottom sheet (REQ-UI-237)",
            content.contains("else if (analyticsContent == null && !useStatusBarsPadding)") &&
            content.contains("Spacer(modifier = Modifier.navigationBarsPadding())")
        )
    }

    @Test
    fun testLiveSegmentSheet_configuresMapDetailLayoutForBottomSheetMode() {
        assertTrue("LIveSegmentSheet.kt must exist", liveSegmentSheetFile.exists())
        val content = liveSegmentSheetFile.readText()

        assertTrue(
            "LiveSegmentSheet must pass useStatusBarsPadding = false (REQ-UI-196, REQ-UI-237)",
            content.contains("useStatusBarsPadding = false")
        )
        assertTrue(
            "LiveSegmentSheet must pass showMap = false (REQ-UI-047)",
            content.contains("showMap = false")
        )
    }

    @Test
    fun testMapScreenWithTrack_sizesBottomSheetToMaxSheetHeight() {
        assertTrue("MapScreenWithTrack.kt must exist", mapScreenWithTrackFile.exists())
        val content = mapScreenWithTrackFile.readText()

        assertTrue(
            "MapScreenWithTrack must calculate maxSheetHeight accounting for statusBarHeight",
            content.contains("val maxSheetHeight = maxHeight - statusBarHeight")
        )
        assertTrue(
            "MapScreenWithTrack must host SegmentOnMapScreen with useStatusBarsPadding = false",
            content.contains("SegmentOnMapScreen(") && content.contains("useStatusBarsPadding = false")
        )
        assertTrue(
            "MapScreenWithTrack must host RouteOnMapScreen with useStatusBarsPadding = false",
            content.contains("RouteOnMapScreen(") && content.contains("useStatusBarsPadding = false")
        )
    }

    @Test
    fun testSensorGridScreen_configuresLiveSegmentBottomSheet() {
        assertTrue("SensorGridScreen.kt must exist", sensorGridScreenFile.exists())
        val content = sensorGridScreenFile.readText()

        assertTrue(
            "SensorGridScreen must include navBarHeight in peek height (ATT-1645)",
            content.contains("sheetPeekHeight = if ((showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp") ||
            content.contains("BottomSheetDesign.PeekHeightLiveSegment + navBarHeight")
        )
        assertTrue(
            "SensorGridScreen must host LiveSegmentSheet inside sheetContent",
            content.contains("LiveSegmentSheet(")
        )
        assertTrue(
            "SensorGridScreen must host LiveClimbSheet inside sheetContent (REQ-MAP-027)",
            content.contains("LiveClimbSheet(")
        )
    }
}
