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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract tests for [ElevationProfile] verifying pointerInput key
 * stability, direct callback state capture via rememberUpdatedState without reflection function
 * allocations, touch slop jump elimination, and gesture loop continuity as mandated by
 * REQ-UI-232, REQ-UI-234, and TST-UI-190 (ATT-1987).
 */
class ElevationProfileGestureContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    @Test
    fun testElevationProfile_excludesMutableStateFromPointerInputKeys() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. pointerInput must observe only stable configuration keys
        assertTrue(
            "ElevationProfile must declare pointerInput(totalSpan, isTimeDomain, isPanMode)",
            content.contains("pointerInput(totalSpan, isTimeDomain, isPanMode)")
        )

        // 2. Mutable running distance and zoom scale must NOT be in pointerInput keys
        assertFalse(
            "ElevationProfile must NOT key pointerInput on currentStartDist or startDist",
            content.contains("pointerInput(currentStartDist") || content.contains("pointerInput(startDist")
        )
        assertFalse(
            "ElevationProfile must NOT key pointerInput on currentZoomScale or zoomScale",
            content.contains("pointerInput(currentZoomScale") || content.contains("pointerInput(zoomScale")
        )
    }

    @Test
    fun testElevationProfile_capturesOnZoomChangedDirectlyViaRememberUpdatedState() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // 1. Directly captures onZoomChanged via rememberUpdatedState
        assertTrue(
            "ElevationProfile must capture onZoomChanged via rememberUpdatedState",
            content.contains("val currentOnZoomChangedState by rememberUpdatedState(onZoomChanged)")
        )

        // 2. Reflection function allocation (::updateZoom) must be eliminated
        assertFalse(
            "ElevationProfile must NOT use rememberUpdatedState(::updateZoom)",
            content.contains("rememberUpdatedState(::updateZoom)")
        )
    }

    @Test
    fun testElevationProfile_resetsPrevCentroidOnDragEngagement() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // When dominant horizontal drag is detected, prevCentroid must be reset to pointer.position
        assertTrue(
            "ElevationProfile must reset prevCentroid = pointer.position when isDragging becomes true",
            content.contains("isDragging = true\n                                    prevCentroid = pointer.position") ||
            content.contains("isDragging = true\r\n                                    prevCentroid = pointer.position")
        )
    }

    @Test
    fun testElevationProfile_removesPrematureVerticalScrollBreak() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Premature break on pointer.isConsumed during vertical scrolling must be eliminated
        assertFalse(
            "ElevationProfile must NOT break gesture loop prematurely when pointer.isConsumed during vertical scroll",
            content.contains("else if (isVerticalScrolling) {\n                                if (pointer.isConsumed) {\n                                    break")
        )
    }

    @Test
    fun testElevationProfile_suppressesSingleTapInspectionInPanMode() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Single tap inspection must be guarded by !isPanMode
        assertTrue(
            "ElevationProfile must guard single tap inspection with !isPanMode",
            content.contains("else if (!isPanMode) {")
        )
    }
}
