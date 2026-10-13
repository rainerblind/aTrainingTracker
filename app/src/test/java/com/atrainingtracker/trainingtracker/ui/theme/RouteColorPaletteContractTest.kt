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

package com.atrainingtracker.trainingtracker.ui.theme

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.ui.climbs.getClimbCategoryColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Contract and unit tests verifying the Royal Blue route palette transition,
 * multi-tier climb category contrast differentiation, and style guide synchronization
 * (REQ-UI-306, TST-UI-266, ATT-2761).
 */
class RouteColorPaletteContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    private fun colorDistance(c1: Color, c2: Color): Float {
        val dr = c1.red - c2.red
        val dg = c1.green - c2.green
        val db = c1.blue - c2.blue
        return sqrt(dr * dr + dg * dg + db * db)
    }

    @Test
    fun testRouteColorTokens_haveExactRoyalBlueValues() {
        // Core tokens must strictly match the Material Blue Royal palette
        assertEquals(
            "RouteSelected must be Material Blue 800 (Royal Blue)",
            Color(0xFF1565C0),
            TTColor.RouteSelected
        )
        assertEquals(
            "RouteUnselected must be Material Blue 200 (Soft Steel Blue)",
            Color(0xFF90CAF9),
            TTColor.RouteUnselected
        )
        assertEquals(
            "RouteActiveNavigation must be Material Blue 600 (Vibrant Sapphire Blue)",
            Color(0xFF1E88E5),
            TTColor.RouteActiveNavigation
        )
        assertEquals(
            "RouteActiveNavigationOverlay must be Material Blue 900 (Deep Midnight Navy)",
            Color(0xFF0D47A1),
            TTColor.RouteActiveNavigationOverlay
        )
    }

    @Test
    fun testClimbCategoryContrast_withRoyalBlueRoute() {
        val routeColor = TTColor.RouteSelected

        for (category in ClimbCategory.values()) {
            val (climbColor, _, _) = getClimbCategoryColors(category)
            val distance = colorDistance(routeColor, climbColor)

            // Every climb category overlay must maintain clear chromatic distance from the route ribbon
            assertTrue(
                "Climb category $category ($climbColor) must have color distance > 0.25 from RouteSelected ($routeColor), was $distance",
                distance > 0.25f
            )

            if (category == ClimbCategory.CAT_4) {
                // Category 4 Green was previously near-identical to ForestGreen route (distance < 0.10).
                // With Royal Blue, distance must be prominently distinguishable (> 0.35).
                assertTrue(
                    "Cat 4 Green ($climbColor) must achieve high contrast (> 0.35) against Royal Blue route, was $distance",
                    distance > 0.35f
                )
            }
        }
    }

    @Test
    fun testStravaOrange_retainsHighContrastWithRoyalBlue() {
        val distance = colorDistance(TTColor.RouteSelected, TTColor.StravaOrange)
        assertTrue(
            "Strava Orange must maintain high complementary contrast with Royal Blue route (distance > 0.50), was $distance",
            distance > 0.50f
        )
    }

    @Test
    fun testDesignGuidelines_codifiesRoyalBlueAndEliminatesGreenRouteMentions() {
        val file = findFile("docs/design_guidelines.md")
        assertTrue("docs/design_guidelines.md must exist", file.exists())

        val content = file.readText()

        assertTrue(
            "Section 5.4 must document Royal Blue tones for routes",
            content.contains("Royal Blue tones (`TTColor.RouteSelected`, `TTColor.RouteActiveNavigation`)")
        )
        assertTrue(
            "Section 5.7 must document Royal Blue border accent for in-ride navigation HUD cues",
            content.contains("subtle Royal Blue border accent (`TTColor.RouteActiveNavigation`)")
        )
        assertFalse(
            "Style guide must not reference obsolete green tones for routes",
            content.contains("green tones (`TTColor.RouteSelected`")
        )
        assertFalse(
            "Style guide must not reference obsolete green border accent for navigation cues",
            content.contains("subtle green border accent (`TTColor.RouteActiveNavigation`")
        )
    }
}
