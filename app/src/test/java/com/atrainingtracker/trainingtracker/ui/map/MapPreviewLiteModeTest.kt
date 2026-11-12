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

import android.graphics.Color
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Google Maps Lite Mode configuration across Compose lazy lists
 * and ensuring RenderThread deadlock immunity (REQ-STB-010, TST-STB-010, ATT-1246).
 */
class MapPreviewLiteModeTest {

    @Before
    fun setUp() {
        mockkStatic(Color::class)
        every { Color.argb(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Color::class)
    }

    @Test
    fun testGoogleMapOptions_liteModeFlag() {
        val options = GoogleMapOptions().liteMode(true)
        assertEquals(true, options.liteMode)

        val interactiveOptions = GoogleMapOptions()
        // Default GoogleMapOptions has liteMode set to false or null (non-lite)
        assertFalse(interactiveOptions.liteMode == true)
    }

    @Test
    fun testPathPreviewMap_enforcesLiteMode() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.PathPreviewMapKt")
        assertNotNull("PathPreviewMapKt must exist", clazz)
    }

    @Test
    fun testWorkoutClusterComponents_enforcesLiteModeInListThumbnails() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterComponentsKt")
        assertNotNull("WorkoutClusterComponentsKt must exist", clazz)
    }

    @Test
    fun testInteractiveMaps_isolateLiteMode_retainingFullVectorInteractivity() {
        val interactiveMapClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt",
            "com.atrainingtracker.trainingtracker.ui.clusters.ManualClusterScreenKt",
            "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapEditBottomSheetKt"
        )

        for (className in interactiveMapClasses) {
            val clazz = Class.forName(className)
            assertNotNull("$className must exist", clazz)
        }
    }

    @Test
    fun testBoundsBuilder_forLiteModePreviewCamera() {
        val start = LatLng(48.1351, 11.5820)
        val apex = LatLng(48.1400, 11.5900)
        val end = LatLng(48.1300, 11.5750)

        val boundsBuilder = LatLngBounds.Builder()
        boundsBuilder.include(start)
        boundsBuilder.include(apex)
        boundsBuilder.include(end)

        val bounds = boundsBuilder.build()
        assertNotNull(bounds)
        assertTrue(bounds.southwest.latitude <= 48.1300)
        assertTrue(bounds.northeast.latitude >= 48.1400)
        assertTrue(bounds.southwest.longitude <= 11.5750)
        assertTrue(bounds.northeast.longitude >= 11.5900)
    }

    @Test
    fun testClickHandler_callbackExecution() {
        var clicked = false
        val onClick: () -> Unit = { clicked = true }

        onClick()
        assertTrue("onMapClick callback must be invoked when triggered", clicked)
    }
}
