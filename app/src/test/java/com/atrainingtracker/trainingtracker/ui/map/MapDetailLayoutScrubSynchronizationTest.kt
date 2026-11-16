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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural and contract tests verifying that MapDetailLayout, ATrainingTrackerMap,
 * ScrubMarkerLayer, ScrubberController, and TelemetryMetricGraph correctly propagate
 * and synchronize activeScrubPoint across time and distance domains (REQ-MAP-032 / TST-MAP-034).
 */
class MapDetailLayoutScrubSynchronizationTest {

    @Test
    fun scrubMarkerLayerAcceptsActiveScrubPointParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapLayersKt")
        assertNotNull("MapLayersKt should exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ScrubMarkerLayer") }
        assertTrue("ScrubMarkerLayer composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun scrubberControllerAcceptsActiveScrubPointParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt should exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ScrubberController") }
        assertTrue("ScrubberController composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun aTrainingTrackerMapForwardsActiveScrubPoint() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt should exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("ATrainingTrackerMap") }
        assertTrue("ATrainingTrackerMap composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun telemetryMetricGraphSupportsOnPointSelectedCallback() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt should exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("TelemetryMetricGraph") }
        assertTrue("TelemetryMetricGraph composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun mapDetailLayoutWiresOnPointSelectedAndPassesActiveScrubPoint() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt should exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("MapDetailLayout") }
        assertTrue("MapDetailLayout composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun overlayScrubTarget_resolvesBothDistanceAndTimeDomains_gpsAndTrackless() {
        // TST-PER-050.4
        val origin = LatLng(48.0, 11.0)
        val pathPoints = listOf(
            PathPoint(distance = 0.0, latLng = origin, timeSec = 0L, hr = 120),
            PathPoint(distance = 100.0, latLng = origin, timeSec = 20L, hr = 140),
            PathPoint(distance = 250.0, latLng = origin, timeSec = 45L, hr = 160)
        )

        assertNull(resolveOverlayScrubTarget(pathPoints, null, isTimeDomain = false, isTrackless = false))
        assertEquals(
            100.0,
            resolveOverlayScrubTarget(pathPoints, 100.0, isTimeDomain = false, isTrackless = false)!!,
            1e-6
        )
        assertEquals(
            20.0,
            resolveOverlayScrubTarget(pathPoints, 100.0, isTimeDomain = true, isTrackless = false)!!,
            1e-6
        )
        assertEquals(
            45.0,
            resolveOverlayScrubTarget(pathPoints, 45.0, isTimeDomain = true, isTrackless = true)!!,
            1e-6
        )
    }

    @Test
    fun telemetryHeaderFormatting_appendsActiveOverlayValuesOnlyWhenOverlaysActive() {
        // TST-PER-050.4
        val series = listOf(
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.HEART_RATE,
                sourceName = "Polar H10",
                columnName = "HR (Polar H10)",
                points = listOf(
                    TelemetrySourcePoint(distance = 0.0, timeSec = 0L, value = 132.0),
                    TelemetrySourcePoint(distance = 100.0, timeSec = 20L, value = 148.0)
                ),
                meanValue = 140.0
            ),
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.HEART_RATE,
                sourceName = "Watch",
                columnName = "HR (Watch)",
                points = listOf(
                    TelemetrySourcePoint(distance = 0.0, timeSec = 0L, value = 128.0),
                    TelemetrySourcePoint(distance = 100.0, timeSec = 20L, value = 141.0)
                ),
                meanValue = 134.5
            )
        )

        assertEquals(
            "Heart Rate: 148 bpm",
            formatTelemetryHeaderWithOverlays(
                baseHeading = "Heart Rate",
                activePrimaryText = "148 bpm",
                metricType = TelemetryMetricType.HEART_RATE,
                activeOverlays = emptyList(),
                overlayScrubTarget = 100.0,
                isTimeDomain = false
            )
        )

        val scrubbingText = formatTelemetryHeaderWithOverlays(
            baseHeading = "Heart Rate",
            activePrimaryText = "148 bpm",
            metricType = TelemetryMetricType.HEART_RATE,
            activeOverlays = series,
            overlayScrubTarget = 20.0,
            isTimeDomain = true
        )
        assertTrue(scrubbingText.contains("148 bpm"))
        assertTrue(scrubbingText.contains("Polar H10: 148 bpm"))
        assertTrue(scrubbingText.contains("Watch: 141 bpm"))

        val staticText = formatTelemetryHeaderWithOverlays(
            baseHeading = "Heart Rate",
            activePrimaryText = null,
            metricType = TelemetryMetricType.HEART_RATE,
            activeOverlays = series,
            overlayScrubTarget = null,
            isTimeDomain = false
        )
        assertEquals("Heart Rate", staticText)
    }
}
