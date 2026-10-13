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

package com.atrainingtracker.trainingtracker.ui.climbs

import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.map.calculateElevationBounds
import com.atrainingtracker.trainingtracker.ui.map.calculateElevationProfileHeight
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests verifying adaptive elevation profile height scaling
 * based on climb elevation gain for [LiveClimbSheet] (REQ-UI-352, ATT-3316, Rule 36).
 */
class LiveClimbAdaptiveHeightTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val liveClimbSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt")
    }

    @Test
    fun testCalculateElevationProfileHeight_acrossClimbCategories() {
        // Flat or zero gain: minimum height 70dp
        assertEquals(70.dp, calculateElevationProfileHeight(0.0))

        // Minor rolling hill: ~20m gain
        assertEquals(82.dp, calculateElevationProfileHeight(20.0))

        // Category 4 climb: ~60m gain
        assertEquals(94.dp, calculateElevationProfileHeight(60.0))

        // Category 3 climb: ~150m gain
        assertEquals(111.dp, calculateElevationProfileHeight(150.0))

        // Category 2 climb: ~300m gain
        assertEquals(133.dp, calculateElevationProfileHeight(300.0))

        // Category 1 climb: ~600m gain
        assertEquals(165.dp, calculateElevationProfileHeight(600.0))

        // Hors Catégorie (HC) mountain pass: 1000m gain -> maximum 200dp
        assertEquals(200.dp, calculateElevationProfileHeight(1000.0))

        // Beyond 1000m: clamped to maximum 200dp
        assertEquals(200.dp, calculateElevationProfileHeight(1500.0))
    }

    @Test
    fun testLiveClimbElevationBounds_computesAdaptiveHeightFromPathPoints() {
        val climbPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 500.0, timeSec = 0L),
            PathPoint(distance = 500.0, latLng = LatLng(0.0, 0.0), altitude = 550.0, timeSec = 100L),
            PathPoint(distance = 1000.0, latLng = LatLng(0.0, 0.0), altitude = 650.0, timeSec = 200L),
            PathPoint(distance = 2000.0, latLng = LatLng(0.0, 0.0), altitude = 800.0, timeSec = 400L) // 300m elevation gain
        )

        val bounds = calculateElevationBounds(climbPoints)
        assertEquals(500.0, bounds.min, 0.01)
        assertEquals(800.0, bounds.max, 0.01)
        assertEquals(300.0, bounds.range, 0.01)

        val adaptiveHeight = calculateElevationProfileHeight(bounds.range)
        assertEquals(133.dp, adaptiveHeight)
    }

    @Test
    fun testLiveClimbSheet_sourceCodeDoesNotContainHardcodedFixedHeight() {
        assertTrue("LiveClimbSheet.kt must exist", liveClimbSheetFile.exists())
        val content = liveClimbSheetFile.readText()

        assertFalse(
            "LiveClimbSheet must not hardcode fixedHeight; it must scale adaptively per ATT-3316 and Rule 36",
            content.contains("fixedHeight")
        )
        assertTrue(
            "LiveClimbSheet must invoke ElevationProfile with climbGradientShading = true",
            content.contains("climbGradientShading = true")
        )
    }
}
