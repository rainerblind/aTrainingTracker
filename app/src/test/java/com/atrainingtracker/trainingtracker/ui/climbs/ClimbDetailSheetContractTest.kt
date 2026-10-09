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

import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for [ClimbDetailSheet], [ClimbDetailElevationProfile],
 * and [calculateClimbBounds] (REQ-UI-300, TST-UI-260).
 */
class ClimbDetailSheetContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val climbDetailSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt")
    }

    @Test
    fun testClimbDetailSheet_composablesExistAndArePublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetKt")
        assertNotNull("ClimbDetailSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("ClimbDetailSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ClimbDetailSheet composable function must exist and be public", sheetMethod)

        val profileMethod = methods.find { it.name.startsWith("ClimbDetailElevationProfile") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ClimbDetailElevationProfile composable function must exist and be public", profileMethod)
    }

    @Test
    fun testClimbDetailSheet_structuralTokensAndContracts() {
        assertTrue("ClimbDetailSheet.kt must exist", climbDetailSheetFile.exists())
        val content = climbDetailSheetFile.readText()

        // Material 3 AppModalBottomSheet usage
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet"))

        // Category chip in header
        assertTrue("Must display ClimbCategoryChip in header", content.contains("ClimbCategoryChip(category = climb.category)"))

        // Telemetry metrics HUD
        assertTrue("Must render distance metric", content.contains("climb.distanceMeters"))
        assertTrue("Must render elevation gain metric", content.contains("climb.elevationGainMeters"))
        assertTrue("Must render average grade metric", content.contains("routes_climb_avg_grade"))
        assertTrue("Must render maximum grade metric", content.contains("routes_climb_max_grade"))
        assertTrue("Must use climb_max_grade_label for maximum grade label (REQ-UI-315.1)", content.contains("climb_max_grade_label"))
        assertTrue("Must display metric icons in HUD (REQ-UI-315.1)", content.contains("ic_distance") && content.contains("ic_ascent") && content.contains("ic_grade"))

        // Embedded map with EXPLICIT_BOUNDS
        assertTrue("Must use ATrainingTrackerMap", content.contains("ATrainingTrackerMap"))
        assertTrue("Must use MapZoomFocus.EXPLICIT_BOUNDS", content.contains("MapZoomFocus.EXPLICIT_BOUNDS"))
        assertTrue("Must invoke calculateClimbBounds", content.contains("calculateClimbBounds(climb)"))

        // Material 3 16.dp card corners
        assertTrue("Must use 16.dp corner radius for cards (REQ-UI-315.4)", content.contains("RoundedCornerShape(16.dp)"))

        // Isolated zoomed elevation profile
        assertTrue("Must render ClimbDetailElevationProfileCard", content.contains("ClimbDetailElevationProfileCard"))
    }

    @Test
    fun testCalculateClimbBounds_validClimb_computesCorrectBounds() {
        val p1 = PathPoint(0.0, LatLng(48.1351, 11.5820), 500.0)
        val p2 = PathPoint(1000.0, LatLng(48.1400, 11.5900), 580.0)
        val climb = Climb(
            id = 1L,
            name = "Test Climb",
            startLat = 48.1351,
            startLng = 11.5820,
            endLat = 48.1400,
            endLng = 11.5900,
            distanceMeters = 1000.0,
            elevationGainMeters = 80.0,
            avgGradePercent = 8.0,
            maxGradePercent = 12.0,
            category = ClimbCategory.CAT_3,
            pathPoints = listOf(p1, p2)
        )

        val bounds = calculateClimbBounds(climb)
        assertNotNull("Bounds must not be null for valid climb", bounds)
        assertEquals(48.1351, bounds!!.southwest.latitude, 0.0001)
        assertEquals(11.5820, bounds.southwest.longitude, 0.0001)
        assertEquals(48.1400, bounds.northeast.latitude, 0.0001)
        assertEquals(11.5900, bounds.northeast.longitude, 0.0001)
    }

    @Test
    fun testCalculateClimbBounds_singlePoint_expandsZeroAreaGracefully() {
        val climb = Climb(
            id = 2L,
            name = "Single Point Climb",
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            distanceMeters = 0.0,
            elevationGainMeters = 0.0,
            avgGradePercent = 0.0,
            maxGradePercent = 0.0,
            category = ClimbCategory.UNCATEGORIZED,
            pathPoints = emptyList()
        )

        val bounds = calculateClimbBounds(climb)
        assertNotNull("Bounds must not be null even for single point climb", bounds)
        assertTrue("Northeast latitude must be strictly greater than southwest", bounds!!.northeast.latitude > bounds.southwest.latitude)
        assertTrue("Northeast longitude must be strictly greater than southwest", bounds.northeast.longitude > bounds.southwest.longitude)
    }
}
