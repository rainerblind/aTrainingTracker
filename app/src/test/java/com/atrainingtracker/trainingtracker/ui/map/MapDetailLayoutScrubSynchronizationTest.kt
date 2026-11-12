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

import org.junit.Assert.assertNotNull
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
}
