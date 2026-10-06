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

package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import com.atrainingtracker.trainingtracker.ui.map.MapRoute
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * TST-UI-234.2: Architectural UI contract tests for Route Climbs visualization and component signatures (REQ-UI-274).
 */
class RouteClimbsUiContractTest {

    @Test
    fun testClimbCategoryColors_allCategoriesMappedWithContrastColors() {
        for (category in ClimbCategory.entries) {
            val (bgColor, textColor, labelRes) = getClimbCategoryColors(category)
            assertNotNull(bgColor)
            assertNotNull(textColor)
            assertTrue(labelRes > 0)
        }

        // Specific category color invariants from LiveClimbSheet.kt
        val (hcBg, hcText, hcRes) = getClimbCategoryColors(ClimbCategory.HC)
        assertEquals(Color(0xFF880E4F), hcBg)
        assertEquals(Color.White, hcText)
        assertEquals(R.string.climb_category_hc, hcRes)

        val (cat1Bg, cat1Text, cat1Res) = getClimbCategoryColors(ClimbCategory.CAT_1)
        assertEquals(Color(0xFFC62828), cat1Bg)
        assertEquals(Color.White, cat1Text)
        assertEquals(R.string.climb_category_cat1, cat1Res)

        val (cat2Bg, cat2Text, cat2Res) = getClimbCategoryColors(ClimbCategory.CAT_2)
        assertEquals(Color(0xFFEF6C00), cat2Bg)
        assertEquals(Color.White, cat2Text)
        assertEquals(R.string.climb_category_cat2, cat2Res)

        val (cat3Bg, cat3Text, cat3Res) = getClimbCategoryColors(ClimbCategory.CAT_3)
        assertEquals(Color(0xFFF9A825), cat3Bg)
        assertEquals(Color.Black, cat3Text)
        assertEquals(R.string.climb_category_cat3, cat3Res)

        val (cat4Bg, cat4Text, cat4Res) = getClimbCategoryColors(ClimbCategory.CAT_4)
        assertEquals(Color(0xFF2E7D32), cat4Bg)
        assertEquals(Color.White, cat4Text)
        assertEquals(R.string.climb_category_cat4, cat4Res)
    }

    @Test
    fun testMapRoute_climbsContract() {
        val climb = Climb(
            id = 1L,
            name = "Alb Aufstieg",
            startLat = 48.4,
            startLng = 9.3,
            endLat = 48.42,
            endLng = 9.32,
            distanceMeters = 3500.0,
            elevationGainMeters = 240.0,
            avgGradePercent = 6.8,
            maxGradePercent = 11.2,
            category = ClimbCategory.CAT_2,
            pathPoints = listOf(
                PathPoint(0.0, LatLng(48.4, 9.3), 450.0),
                PathPoint(3500.0, LatLng(48.42, 9.32), 690.0)
            )
        )

        val mapRoute = MapRoute(
            id = 10L,
            name = "Test Alb Route",
            isSelected = true,
            bSportType = BSportType.BIKE,
            path = climb.pathPoints,
            climbs = listOf(climb)
        )

        assertEquals(1, mapRoute.climbs.size)
        assertEquals(climb.startLatLng, mapRoute.climbs[0].startLatLng)
        assertEquals(climb.endLatLng, mapRoute.climbs[0].endLatLng)
        assertEquals(48.4, mapRoute.climbs[0].startLat, 1e-6)
    }

    @Test
    fun testClimbCategoryChip_isAccessible() {
        val methods = Class.forName("com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetKt").methods
        val chipMethod = methods.find { it.name.startsWith("ClimbCategoryChip") }
        assertNotNull("ClimbCategoryChip composable should be accessible", chipMethod)
        assertTrue(Modifier.isPublic(chipMethod!!.modifiers))
    }

    @Test
    fun testRouteClimbsBreakdownSection_isAccessible() {
        val methods = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt").methods
        val breakdownMethod = methods.find { it.name.startsWith("RouteClimbsBreakdownSection") }
        assertNotNull("RouteClimbsBreakdownSection composable should be accessible", breakdownMethod)
        assertTrue(Modifier.isPublic(breakdownMethod!!.modifiers))
    }
}
