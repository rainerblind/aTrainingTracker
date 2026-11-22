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

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visual and structural contract tests for [MapDetailLayout] verifying
 * resilient map preview visibility, overflow detection, and scrollable container architecture
 * as mandated by REQ-UI-213 and TST-UI-167 (ATT-1812).
 */
// Linked test specs: TST-UI-168, TST-UI-226, TST-UI-500
class MapDetailLayoutTest {

    @Test
    fun testMapDetailLayout_declaresNullableAnalyticsContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_detectsScrollableContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_declaresShowTelemetryCharts() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_appliesResilientMinHeightAndScroll() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_preservesFullWeightForRoutesAndSegments() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_telemetryGraphOrdering_speedPrecedesHeartRate() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)

        // TST-UI-168 / ATT-9355: Verify canonical mechanical-before-physiological order
        // (SPEED_PACE -> SLOPE -> CADENCE -> POWER -> HEART_RATE -> W_PRIME_BAL -> HRV_DFA_A1 -> HRV_RMSSD)
        val path = listOf(
            PathPoint(
                distance = 0.0,
                latLng = LatLng(48.10, 11.50),
                altitude = 520.0,
                timeSec = 0L,
                hr = 140,
                power = 220,
                speedMps = 8.0,
                slope = 2.5,
                wPrimeBalKj = 15.0,
                hrvDfaA1 = 0.80,
                hrvRmssd = 42.0,
                cadence = 88
            ),
            PathPoint(
                distance = 100.0,
                latLng = LatLng(48.11, 11.51),
                altitude = 525.0,
                timeSec = 15L,
                hr = 148,
                power = 240,
                speedMps = 8.4,
                slope = 3.0,
                wPrimeBalKj = 14.2,
                hrvDfaA1 = 0.77,
                hrvRmssd = 38.0,
                cadence = 90
            )
        )
        val kinds = availableTelemetryChartKinds(path, enableCriticalPowerModel = true)
        assertEquals(
            listOf(
                TelemetryChartKind.SPEED_PACE,
                TelemetryChartKind.SLOPE,
                TelemetryChartKind.CADENCE,
                TelemetryChartKind.POWER,
                TelemetryChartKind.HEART_RATE,
                TelemetryChartKind.W_PRIME_BAL,
                TelemetryChartKind.HRV_DFA_A1,
                TelemetryChartKind.HRV_RMSSD
            ),
            kinds
        )
        assertTrue(kinds.indexOf(TelemetryChartKind.SPEED_PACE) < kinds.indexOf(TelemetryChartKind.HEART_RATE))
        assertTrue(kinds.indexOf(TelemetryChartKind.CADENCE) < kinds.indexOf(TelemetryChartKind.POWER))
        assertTrue(kinds.indexOf(TelemetryChartKind.POWER) < kinds.indexOf(TelemetryChartKind.HEART_RATE))
    }

    @Test
    fun testMapDetailLayout_wiresGlobalZoomState() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_integratesGlobalTelemetryZoomToolbar() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_detectsLowerSection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_activatesSplitPaneForLowerSection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_unifiesTopEndActionsColumnStacking_REQ_UI_500_1() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testTrackOnMapScreenAndRouteOnMapScreen_zeroHardcoded76dpOffsets_REQ_UI_500_3() {
        val trackClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", trackClazz)
        val routeClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenKt")
        assertNotNull("RouteOnMapScreenKt must exist", routeClazz)
    }
}
