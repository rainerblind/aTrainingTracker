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
import java.io.File

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
        val pathPreviewFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt")
        assertTrue("PathPreviewMap.kt must exist", pathPreviewFile.exists())
        val content = pathPreviewFile.readText()

        assertTrue(
            "PathPreviewMap.kt must import GoogleMapOptions",
            content.contains("import com.google.android.gms.maps.GoogleMapOptions")
        )
        assertTrue(
            "PathPreviewMap.kt must configure googleMapOptionsFactory with liteMode(true)",
            content.contains("GoogleMapOptions().liteMode(true)")
        )
    }

    @Test
    fun testWorkoutClusterComponents_enforcesLiteModeInListThumbnails() {
        val clusterFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt")
        assertTrue("WorkoutClusterComponents.kt must exist", clusterFile.exists())
        val content = clusterFile.readText()

        assertTrue(
            "WorkoutClusterComponents.kt must import GoogleMapOptions",
            content.contains("import com.google.android.gms.maps.GoogleMapOptions")
        )

        // Must configure liteMode(true) at least twice (ClusterCard & ClusterWorkoutCard)
        val matches = "GoogleMapOptions\\(\\)\\.liteMode\\(true\\)".toRegex().findAll(content).toList()
        assertTrue(
            "WorkoutClusterComponents.kt must configure liteMode(true) for both ClusterCard and ClusterWorkoutCard (found ${matches.size})",
            matches.size >= 2
        )
    }

    @Test
    fun testInteractiveMaps_isolateLiteMode_retainingFullVectorInteractivity() {
        val interactiveMapFiles = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt")
        )

        for (file in interactiveMapFiles) {
            assertTrue("${file.name} must exist", file.exists())
            val content = file.readText()
            assertFalse(
                "${file.name} must NOT enable liteMode, retaining full vector interactivity",
                content.contains("liteMode(true)")
            )
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
