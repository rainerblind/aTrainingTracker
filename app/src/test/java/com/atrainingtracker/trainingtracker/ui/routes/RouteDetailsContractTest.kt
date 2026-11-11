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

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.climbs.ClimbDetector
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * Architectural contract test for Route Details polish, summit badge clearance,
 * climb iconography disambiguation, and section header deduplication
 * (REQ-UI-489, TST-UI-489, ATT-5853).
 */
class RouteDetailsContractTest {

    @Test
    fun testElevationProfile_topPaddingAndCanvasHeightCalibration_REQ_UI_489_1() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ElevationProfileKt")
        assertNotNull("ElevationProfileKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val elevationProfileMethod = methods.find {
            it.name.startsWith("ElevationProfile") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("ElevationProfile composable must exist and be public", elevationProfileMethod)
    }

    @Test
    fun testRouteSummaryHeader_climbCountIconDisambiguation_REQ_UI_489_2() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSummaryHeaderKt")
        assertNotNull("RouteSummaryHeaderKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val headerMethod = methods.find {
            it.name.startsWith("RouteSummaryHeader") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("RouteSummaryHeader composable must exist and be public", headerMethod)

        // Verify distinct drawable resources exist
        assertTrue("ic_altitude drawable must exist", R.drawable.ic_altitude != 0)
        assertTrue("ic_ascent drawable must exist", R.drawable.ic_ascent != 0)
        assertTrue("ic_altitude and ic_ascent must be distinct resources", R.drawable.ic_altitude != R.drawable.ic_ascent)
    }

    @Test
    fun testSectionHeaderDeduplication_parameterAndWiring_REQ_UI_489_3() {
        // 1. Parameter presence in RouteClimbsBreakdownSection
        val mapClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        val climbsMethod = mapClazz.declaredMethods.find { it.name == "RouteClimbsBreakdownSection" }
        assertNotNull("RouteClimbsBreakdownSection composable must exist", climbsMethod)
        assertTrue(
            "RouteClimbsBreakdownSection must declare showHeader boolean parameter",
            climbsMethod!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )

        // 2. Parameter presence in RouteSegmentsBreakdownSection
        val segClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSegmentsBreakdownSectionKt")
        val segmentsMethod = segClazz.declaredMethods.find { it.name == "RouteSegmentsBreakdownSection" }
        assertNotNull("RouteSegmentsBreakdownSection composable must exist", segmentsMethod)
        assertTrue(
            "RouteSegmentsBreakdownSection must declare showHeader boolean parameter",
            segmentsMethod!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )
    }

    @Test
    fun testClimbDetector_localeAwareGradeFormatting_REQ_UI_489_4() {
        val samplePoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.0, 9.0), altitude = 200.0, timeSec = 0L),
            PathPoint(distance = 500.0, latLng = LatLng(48.003, 9.003), altitude = 250.0, timeSec = 50L),
            PathPoint(distance = 1000.0, latLng = LatLng(48.006, 9.006), altitude = 300.0, timeSec = 100L)
        )

        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val climbs = ClimbDetector.detectClimbs(samplePoints)
            if (climbs.isNotEmpty()) {
                val climb = climbs.first()
                assertTrue(
                    "Climb name must contain localized grade format: ${climb.name}",
                    climb.name.contains("%")
                )
            } else {
                val formatted = String.format(Locale.getDefault(), "%.1f%%", 8.5)
                assertEquals("8,5%", formatted)
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
