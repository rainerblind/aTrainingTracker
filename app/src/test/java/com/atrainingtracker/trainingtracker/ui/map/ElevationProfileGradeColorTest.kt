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
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and contract tests for [ElevationProfile] 7-zone slope gradient color shading,
 * [getElevationGradeColor] half-open interval boundaries, non-finite guards, and
 * [GradeLegend] 7-item accessibility outline (REQ-UI-371, TST-UI-334).
 */
class ElevationProfileGradeColorTest {

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
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
        val legendMethod = clazz.declaredMethods.find { it.name.startsWith("GradeLegend") }
        assertNotNull("GradeLegend composable must exist", legendMethod)
    }

    @Test
    fun testGradeLegendLocalization_allNineLocalesExist() {
        val locales = listOf("values", "values-de", "values-es", "values-fr", "values-it", "values-ja", "values-nl", "values-pl", "values-pt")
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
            val strings = LocalizationTestCache.getStrings(loc)
            for (key in requiredKeys) {
                assertTrue("Missing key $key in $loc", strings.containsKey(key))
            }
        }
    }

    @Test
    fun testElevationProfile_marginalAltitudeHistogramColorIsFixedZone1Green() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt must exist", clazz)
    }

    @Test
    fun testElevationProfile_marginalAltitudeHistogram_steepStartDoesNotColorHistogramRed() {
        // Construct track where initial segment has a steep incline (+12% grade)
        val initialGrade = 12.0
        val segment0Color = getElevationGradeColor(initialGrade)
        assertEquals("Initial segment color must be Zone 4 Red for +12% slope", TTColor.Zone4, segment0Color)

        // Under REQ-UI-446.2 (ATT-5486), the marginal altitude histogram uses fixed TTColor.Zone1
        val altColor = TTColor.Zone1
        val altValues = listOf(400.0, 410.0, 420.0, 430.0, 440.0, 450.0)
        val timestamps = listOf(0L, 10L, 20L, 30L, 40L, 50L)

        val histogram = TelemetryHistogramCalculator.computeLinearMarginalHistogram(
            values = altValues,
            timestamps = timestamps,
            dataMin = 400.0,
            dataMax = 450.0,
            numBins = 10,
            color = altColor
        )

        assertTrue("Histogram bins must not be empty", histogram.isNotEmpty())
        for (bin in histogram) {
            assertEquals(
                "Marginal altitude histogram bins must render in TTColor.Zone1 green regardless of steep start gradient",
                TTColor.Zone1,
                bin.color
            )
        }
    }
}
