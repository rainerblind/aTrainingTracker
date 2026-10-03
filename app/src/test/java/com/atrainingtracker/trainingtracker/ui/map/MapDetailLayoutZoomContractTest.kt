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
 * Architectural contract test for [com.atrainingtracker.trainingtracker.ui.map.MapDetailLayout]
 * verifying the reactive suppression of zoom controls when no zoomable graphs are rendered
 * (REQ-UI-249 / TST-UI-208 / ATT-2138).
 */
class MapDetailLayoutZoomContractTest {

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

    @Test
    fun testMapDetailLayout_gatesHasZoomToolbarOnActiveGraphs() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Assert hasZoomToolbar requires (showElevationProfile || hasTelemetryGraphs)
        assertTrue(
            "hasZoomToolbar must evaluate (showElevationProfile || hasTelemetryGraphs)",
            content.contains("val hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()")
        )
    }

    @Test
    fun testMapDetailLayout_reclaimsToolbarHeightWhenToolbarInactive() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 2. Assert toolbarHeightPx evaluates to 0f when !hasZoomToolbar
        assertTrue(
            "toolbarHeightPx must evaluate to 0f when hasZoomToolbar is false",
            content.contains("val toolbarHeightPx = if (hasZoomToolbar) with(density) { GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT.toPx() } else 0f")
        )

        // 3. Assert SplitPaneMath uses toolbarHeightPx
        assertTrue(
            "SplitPaneMath.calculateAvailableHeight must account for toolbarHeightPx",
            content.contains("SplitPaneMath.calculateAvailableHeight(totalHeightPx, dividerHeightPx + toolbarHeightPx)")
        )
    }
}
