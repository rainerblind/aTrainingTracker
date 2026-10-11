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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.calculateElevationBounds
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for [ElevationProfile] slope gradient color shading,
 * [getClimbGradeColor] grade bands, and degenerate geometry safeguards (REQ-UI-352, TST-UI-312.1).
 */
class ElevationProfileClimbShadingTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Test
    fun testGetClimbGradeColor_allBandsAndBoundaries() {
        // Band 1: < 3% -> Green (0xFF4CAF50)
        assertEquals(Color(0xFF4CAF50), getClimbGradeColor(-5.0))
        assertEquals(Color(0xFF4CAF50), getClimbGradeColor(0.0))
        assertEquals(Color(0xFF4CAF50), getClimbGradeColor(2.9))

        // Band 2: 3% - 6% -> Light Green (0xFF8BC34A)
        assertEquals(Color(0xFF8BC34A), getClimbGradeColor(3.0))
        assertEquals(Color(0xFF8BC34A), getClimbGradeColor(4.5))
        assertEquals(Color(0xFF8BC34A), getClimbGradeColor(5.99))

        // Band 3: 6% - 9% -> Amber (0xFFFFC107)
        assertEquals(Color(0xFFFFC107), getClimbGradeColor(6.0))
        assertEquals(Color(0xFFFFC107), getClimbGradeColor(7.4))
        assertEquals(Color(0xFFFFC107), getClimbGradeColor(8.99))

        // Band 4: 9% - 12% -> Orange (0xFFFF9800)
        assertEquals(Color(0xFFFF9800), getClimbGradeColor(9.0))
        assertEquals(Color(0xFFFF9800), getClimbGradeColor(10.5))
        assertEquals(Color(0xFFFF9800), getClimbGradeColor(11.99))

        // Band 5: 12% - 20% -> Red (0xFFE53935)
        assertEquals(Color(0xFFE53935), getClimbGradeColor(12.0))
        assertEquals(Color(0xFFE53935), getClimbGradeColor(15.0))
        assertEquals(Color(0xFFE53935), getClimbGradeColor(19.99))

        // Band 6: >= 20% -> Dark Red / Near Black (0xFF212121)
        assertEquals(Color(0xFF212121), getClimbGradeColor(20.0))
        assertEquals(Color(0xFF212121), getClimbGradeColor(25.0))
        assertEquals(Color(0xFF212121), getClimbGradeColor(35.0))
    }

    @Test
    fun testCalculateElevationBounds_flatClimbDoesNotDivideByZero() {
        val flatPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 500.0),
            PathPoint(distance = 500.0, latLng = LatLng(48.01, 11.01), altitude = 500.0),
            PathPoint(distance = 1000.0, latLng = LatLng(48.02, 11.02), altitude = 500.0)
        )

        val bounds = calculateElevationBounds(flatPoints)
        assertTrue("Altitude range must be >= 1.0", bounds.range >= 1.0)
        assertTrue("Sanitized max must be >= sanitized min", bounds.max >= bounds.min)
    }

    @Test
    fun testCalculateElevationBounds_emptyAndSinglePointBoundsResilient() {
        val emptyBounds = calculateElevationBounds(emptyList())
        assertTrue("Empty points range must be >= 1.0", emptyBounds.range >= 1.0)

        val singlePoint = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.0), altitude = 750.0)
        )
        val singleBounds = calculateElevationBounds(singlePoint)
        assertTrue("Single point range must be >= 1.0", singleBounds.range >= 1.0)
        assertEquals(750.0, singleBounds.min, 15.0)
    }

    @Test
    fun testElevationProfile_contractContainsDegenerateGuardAndClimbShadingParameters() {
        val elevationProfileFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        assertTrue(
            "ElevationProfile must contain degenerate points guard: pathPoints.size < 2",
            content.contains("if (pathPoints.size < 2) return")
        )
        assertTrue(
            "ElevationProfile must declare climbGradientShading parameter",
            content.contains("climbGradientShading: Boolean = false")
        )
        assertTrue(
            "ElevationProfile must declare segmentColorProvider parameter",
            content.contains("segmentColorProvider: ((Double) -> Color)? = null")
        )
        assertTrue(
            "ElevationProfile must declare fixedHeight parameter",
            content.contains("fixedHeight: androidx.compose.ui.unit.Dp? = null")
        )
        assertTrue(
            "ElevationProfile must invoke getClimbGradeColor when climbGradientShading is true",
            content.contains("getClimbGradeColor(grade)")
        )
    }
}
