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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and structural contract tests for MapBehaviors camera lifecycle,
 * deferred live fix alignment, and zoom calculation (REQ-MAP-059.2 / TST-MAP-061.3).
 */
class MapBehaviorsTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testMapBoundsController_includesCurrentLocationInLaunchedEffectKeys() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBoundsController must include currentLocation and initialLocation in LaunchedEffect keys for deferred GPS fix alignment (REQ-MAP-059.2, REQ-MAP-060.2)",
            content.contains("LaunchedEffect(tracks, markers, segments, routes, isMapLoaded, hasFittedInitialBounds, initialBounds, boundsFocusTrigger, currentLocation, initialLocation)")
        )
    }

    @Test
    fun testMapBoundsController_declaresInitialLocationParameter_TST_MAP_062_2() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBoundsController must declare initialLocation: LatLng? = null parameter (REQ-MAP-060.2)",
            content.contains("initialLocation: LatLng? = null")
        )
        assertTrue(
            "MapBoundsController must anchor userPos to currentLocation ?: initialLocation (REQ-MAP-060.1, REQ-MAP-060.2)",
            content.contains("val userPos = currentLocation ?: initialLocation")
        )
    }

    @Test
    fun testMapBoundsController_isLocalReturnsFalseOnNullUserPos_TST_MAP_062_1() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBoundsController.isLocal must return false when userPos is null to prevent worldwide segment fitting (REQ-MAP-060.1)",
            content.contains("if (userPos == null) return false")
        )
    }

    @Test
    fun testMapBoundsController_gatesEmptyBoundsCenteringOnCurrentLocation_TST_MAP_062_3() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBoundsController must gate fallback camera move on currentLocation != null so hasFittedInitialBounds is not locked when currentLocation is null (REQ-MAP-060.3)",
            content.contains("else if (currentLocation != null")
        )
        assertTrue(
            "MapBoundsController must move camera to userPos at MapInitialLocationResolver.DEFAULT_INITIAL_ZOOM when currentLocation != null",
            content.contains("cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(userPos, MapInitialLocationResolver.DEFAULT_INITIAL_ZOOM))")
        )
    }

    @Test
    fun testMapBoundsController_movesCameraToUserPosAtDefaultInitialZoom() {
        val content = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt").readText()

        assertTrue(
            "MapBoundsController must center on userPos at MapInitialLocationResolver.DEFAULT_INITIAL_ZOOM (14f) on empty bounds",
            content.contains("CameraUpdateFactory.newLatLngZoom(userPos, MapInitialLocationResolver.DEFAULT_INITIAL_ZOOM)")
        )
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
