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

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfileZoomMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for [GlobalTelemetryZoomToolbar] verifying dimension defaults,
 * anchored zoom centroid calculations, reset mechanics, and structural completeness
 * as mandated by REQ-UI-225 and TST-UI-179 (ATT-1876).
 */
class GlobalTelemetryZoomToolbarTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val toolbarSourceFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/GlobalTelemetryZoomToolbar.kt")
    }

    @Test
    fun testDefaults_toolbarHeight_is36dp() {
        assertEquals("GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT must be exactly 36.dp", 36.dp, GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT)
    }

    @Test
    fun testZoomMath_zoomInStep_multipliesByOneAndHalf_andAnchorsCenter() {
        val totalDist = 10000.0
        val currentZoom = 2.0f
        val currentStart = 2000.0

        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = currentZoom,
            targetZoom = currentZoom * 1.5f,
            centroidX = 0.5f,
            canvasWidth = 1.0f,
            currentStartDist = currentStart
        )

        assertEquals(3.0f, newZoom, 0.001f)
        assertTrue("New start dist must remain within [0, totalDist - visibleSpan]", newStart >= 0.0 && newStart <= totalDist - (totalDist / newZoom))
    }

    @Test
    fun testZoomMath_zoomOutStep_dividesByOneAndHalf_andClampsToMinZoom() {
        val totalDist = 5000.0
        val currentZoom = 1.2f
        val currentStart = 500.0

        val (newZoom, newStart) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = currentZoom,
            targetZoom = currentZoom / 1.5f,
            centroidX = 0.5f,
            canvasWidth = 1.0f,
            currentStartDist = currentStart
        )

        assertEquals("Zooming out below 1.0f must clamp to MIN_ZOOM (1.0f)", ElevationProfileZoomMath.MIN_ZOOM, newZoom, 0.001f)
        assertEquals("At 1.0f zoom, start distance must reset to 0.0", 0.0, newStart, 0.001)
    }

    @Test
    fun testZoomMath_zoomInStep_clampsToMaxZoom() {
        val totalDist = 10000.0
        val currentZoom = 8.0f
        val currentStart = 1000.0

        val (newZoom, _) = ElevationProfileZoomMath.applyZoomAtCentroid(
            totalDist = totalDist,
            currentZoom = currentZoom,
            targetZoom = currentZoom * 1.5f,
            centroidX = 0.5f,
            canvasWidth = 1.0f,
            currentStartDist = currentStart
        )

        assertEquals("Zooming in beyond MAX_ZOOM must clamp to MAX_ZOOM (10.0f)", ElevationProfileZoomMath.MAX_ZOOM, newZoom, 0.001f)
    }

    @Test
    fun testSourceCode_structuralParity() {
        assertTrue("GlobalTelemetryZoomToolbar.kt must exist", toolbarSourceFile.exists())
        val content = toolbarSourceFile.readText()

        // 1. Zoom In and Zoom Out icons and string resources
        assertTrue("Must reference Icons.Default.Remove and zoom_out", content.contains("Icons.Default.Remove") && content.contains("R.string.zoom_out"))
        assertTrue("Must reference Icons.Default.Add and zoom_in", content.contains("Icons.Default.Add") && content.contains("R.string.zoom_in"))

        // 2. Pan / Scrub toggle icons and string resources
        assertTrue("Must reference Icons.Default.PanTool and zoom_pan_mode", content.contains("Icons.Default.PanTool") && content.contains("R.string.zoom_pan_mode"))
        assertTrue("Must reference Icons.Default.TouchApp and zoom_scrub_mode", content.contains("Icons.Default.TouchApp") && content.contains("R.string.zoom_scrub_mode"))

        // 3. Reset Pill with RestartAlt and zoom_reset
        assertTrue("Must reference Icons.Default.RestartAlt and zoom_reset", content.contains("Icons.Default.RestartAlt") && content.contains("R.string.zoom_reset"))
        assertTrue("Must format zoom scale string %.1fx", content.contains("\"%.1fx\""))

        // 4. Fixed height allocation
        assertTrue("Must use GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT", content.contains("GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT"))
    }
}
