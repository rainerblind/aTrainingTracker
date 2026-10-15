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

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for [ElevationProfile] 7-zone slope gradient color shading,
 * [getElevationGradeColor] half-open interval boundaries, non-finite guards, and
 * [GradeLegend] 7-item accessibility outline (REQ-UI-371, TST-UI-334).
 */
class ElevationProfileGradeColorTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Test
    fun testElevationGradeColor_allZonesAndExactBoundaries() {
        // Zone 1: [-inf, 2.0%) -> TTColor.Zone1 (Chartreuse)
        assertEquals(TTColor.Zone1, getElevationGradeColor(-15.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(-5.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(0.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(1.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(1.99))
        assertEquals(TTColor.Zone1, getElevationGradeColor(1.999999))

        // Zone 2: [2.0%, 5.0%) -> TTColor.Zone2 (Green)
        assertEquals(TTColor.Zone2, getElevationGradeColor(2.0))
        assertEquals(TTColor.Zone2, getElevationGradeColor(3.5))
        assertEquals(TTColor.Zone2, getElevationGradeColor(4.99))
        assertEquals(TTColor.Zone2, getElevationGradeColor(4.999999))

        // Zone 3: [5.0%, 10.0%) -> TTColor.Zone3 (Orange)
        assertEquals(TTColor.Zone3, getElevationGradeColor(5.0))
        assertEquals(TTColor.Zone3, getElevationGradeColor(7.5))
        assertEquals(TTColor.Zone3, getElevationGradeColor(9.99))
        assertEquals(TTColor.Zone3, getElevationGradeColor(9.999999))

        // Zone 4: [10.0%, 15.0%) -> TTColor.Zone4 (Red)
        assertEquals(TTColor.Zone4, getElevationGradeColor(10.0))
        assertEquals(TTColor.Zone4, getElevationGradeColor(12.5))
        assertEquals(TTColor.Zone4, getElevationGradeColor(14.99))
        assertEquals(TTColor.Zone4, getElevationGradeColor(14.999999))

        // Zone 5: [15.0%, 20.0%) -> TTColor.Zone5 (Dark Violet)
        assertEquals(TTColor.Zone5, getElevationGradeColor(15.0))
        assertEquals(TTColor.Zone5, getElevationGradeColor(17.5))
        assertEquals(TTColor.Zone5, getElevationGradeColor(19.99))
        assertEquals(TTColor.Zone5, getElevationGradeColor(19.999999))

        // Zone 6: [20.0%, 25.0%) -> TTColor.Zone6 (Dark Chestnut Brown)
        assertEquals(TTColor.Zone6, getElevationGradeColor(20.0))
        assertEquals(TTColor.Zone6, getElevationGradeColor(22.5))
        assertEquals(TTColor.Zone6, getElevationGradeColor(24.99))
        assertEquals(TTColor.Zone6, getElevationGradeColor(24.999999))

        // Zone 7: [25.0%, +inf) -> TTColor.Zone7 (Black)
        assertEquals(TTColor.Zone7, getElevationGradeColor(25.0))
        assertEquals(TTColor.Zone7, getElevationGradeColor(30.0))
        assertEquals(TTColor.Zone7, getElevationGradeColor(45.0))
    }

    @Test
    fun testElevationGradeColor_descentsAndNonFiniteValues() {
        // Descents and negative grades map strictly to Zone 1 (recovery)
        assertEquals(TTColor.Zone1, getElevationGradeColor(-50.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(-15.0))
        assertEquals(TTColor.Zone1, getElevationGradeColor(-0.1))

        // Non-finite values safely return Zone 1 without exceptions
        assertEquals(TTColor.Zone1, getElevationGradeColor(Double.NaN))
        assertEquals(TTColor.Zone1, getElevationGradeColor(Double.POSITIVE_INFINITY))
        assertEquals(TTColor.Zone1, getElevationGradeColor(Double.NEGATIVE_INFINITY))

        // Extreme steep grades map to Zone 7
        assertEquals(TTColor.Zone7, getElevationGradeColor(100.0))
    }

    @Test
    fun testElevationProfile_contractAndLegendSevenZones() {
        val elevationProfileFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Contract verification: must delegate to getElevationGradeColor when climbGradientShading is false
        assertTrue(
            "ElevationProfile must delegate to getElevationGradeColor(grade)",
            content.contains("getElevationGradeColor(grade)")
        )

        // GradeLegend contract: all 7 string resources must be referenced
        assertTrue("Legend must reference grade_legend_under_2", content.contains("R.string.grade_legend_under_2"))
        assertTrue("Legend must reference grade_legend_2_to_5", content.contains("R.string.grade_legend_2_to_5"))
        assertTrue("Legend must reference grade_legend_5_to_10", content.contains("R.string.grade_legend_5_to_10"))
        assertTrue("Legend must reference grade_legend_10_to_15", content.contains("R.string.grade_legend_10_to_15"))
        assertTrue("Legend must reference grade_legend_15_to_20", content.contains("R.string.grade_legend_15_to_20"))
        assertTrue("Legend must reference grade_legend_20_to_25", content.contains("R.string.grade_legend_20_to_25"))
        assertTrue("Legend must reference grade_legend_over_25", content.contains("R.string.grade_legend_over_25"))

        // WCAG accessibility: outline border declared on GradeLegendItem for dark swatches
        assertTrue(
            "GradeLegendItem must declare border outline for dark swatches",
            content.contains("hasOutline: Boolean = color == TTColor.Zone6 || color == TTColor.Zone7")
        )
    }

    @Test
    fun testGradeLegendLocalization_allNineLocalesExist() {
        val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")
        val requiredKeys = listOf(
            "grade_legend_under_2",
            "grade_legend_2_to_5",
            "grade_legend_5_to_10",
            "grade_legend_10_to_15",
            "grade_legend_15_to_20",
            "grade_legend_20_to_25",
            "grade_legend_over_25"
        )

        for (loc in locales) {
            val file = File(projectRoot, "app/src/main/res/values$loc/strings.xml")
            assertTrue("Locale file $file must exist", file.exists())
            val text = file.readText()
            for (key in requiredKeys) {
                assertTrue("File $file must contain string name=\"$key\"", text.contains("name=\"$key\""))
            }
        }
    }
}
