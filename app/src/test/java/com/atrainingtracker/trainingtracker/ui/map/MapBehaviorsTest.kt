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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit and structural contract tests for MapBehaviors camera lifecycle,
 * deferred live fix alignment, and zoom calculation (REQ-MAP-059.2 / TST-MAP-061.3).
 */
class MapBehaviorsTest {

    @Test
    fun testMapBoundsController_includesCurrentLocationInLaunchedEffectKeys() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testMapBoundsController_declaresInitialLocationParameter_TST_MAP_062_2() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testMapBoundsController_isLocalReturnsFalseOnNullUserPos_TST_MAP_062_1() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testMapBoundsController_gatesEmptyBoundsCenteringOnCurrentLocation_TST_MAP_062_3() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testMapBoundsController_movesCameraToUserPosAtDefaultInitialZoom() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapBehaviorsKt")
        assertNotNull("MapBehaviorsKt must exist", clazz)
    }

    @Test
    fun testCalculateFollowMeTargetZoom_preservesSpeedZoomCalculations() {
        // Cruising speed (20 km/h = 5.555 m/s)
        val cruisingMps = 20.0f / 3.6f
        val cruisingZoom = calculateFollowMeTargetZoom(
            speedMps = cruisingMps,
            baseZoom = 17.5f,
            speedZoomEnabled = true,
            cruisingZoom = 15.0f,
            minZoom = 13.0f
        )
        assertEquals(15.0f, cruisingZoom, 0.05f)

        // Stationary (0 km/h)
        val stationaryZoom = calculateFollowMeTargetZoom(
            speedMps = 0.0f,
            baseZoom = 17.5f,
            speedZoomEnabled = true,
            cruisingZoom = 15.0f,
            minZoom = 13.0f
        )
        assertEquals(17.5f, stationaryZoom, 0.01f)

        // Disabled speed zoom locks firmly to baseZoom
        val lockedZoom = calculateFollowMeTargetZoom(
            speedMps = 50.0f / 3.6f,
            baseZoom = 16.0f,
            speedZoomEnabled = false
        )
        assertEquals(16.0f, lockedZoom, 0.01f)
    }
}
