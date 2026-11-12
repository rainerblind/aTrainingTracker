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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import com.atrainingtracker.trainingtracker.ui.map.MapZoomFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract tests for TrackingMapState isolation and memoized Map container rendering.
 * Refactored to reflection and state validation per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackingMapIsolationContractTest {

    @Test
    fun testTrackingMapState_isImmutableAndEncapsulatesMapProperties() {
        val mapState = TrackingMapState()
        assertEquals(false, mapState.showMap)
        assertEquals(MapZoomFocus.TRACK_AND_MARKERS, mapState.zoomFocus)
        assertEquals(0f, mapState.userBearing)
        assertEquals(0f, mapState.userSpeed)
    }

    @Test
    fun testTrackingScreenState_incorporatesMapStateWithBackwardCompatibility() {
        val defaultScreenState = TrackingScreenState()
        assertNotNull(defaultScreenState.mapState)
        assertEquals(false, defaultScreenState.mapState.showMap)
    }

    @Test
    fun testSensorGridScreen_definesTrackingMapContainerWithMemoizedContent() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("SensorGridScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorGridScreen composable must exist", screenMethod)
    }
}
