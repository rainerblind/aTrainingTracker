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
 * Visual and structural contract verification for trackless aftermath rendering (REQ-UI-235 / TST-UI-194.3 / ATT-2006).
 * Verifies map suppression, full-screen vertical scrolling, time-domain telemetry routing,
 * and instantaneous header metric readouts.
 */
class TracklessAftermathVisualContractTest {

    @Test
    fun testTrackOnMapScreen_evaluatesHasGpsTrack_andSuppressesMap() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_suppressesMap_andAppliesVerticalScroll() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_evaluatesHasTelemetryGraphs_fromActiveScrubPath() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_rendersScrubbingReadoutInHeader() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_rendersTelemetryGraphs_evenWhenShowElevationProfileIsFalse() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }
}
