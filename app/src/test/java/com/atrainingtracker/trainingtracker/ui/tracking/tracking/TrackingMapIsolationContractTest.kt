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

import androidx.compose.runtime.Immutable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying tracking map recomposition boundary isolation,
 * immutable sub-state modeling, and telemetry decoupling (REQ-UI-326, TST-UI-286 / ATT-2944).
 */
class TrackingMapIsolationContractTest {

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
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testTrackingMapState_isImmutableAndEncapsulatesMapProperties() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingMapState.kt")
        val content = sourceFile.readText()
        assertTrue("TrackingMapState must be annotated with @Immutable for Compose skipping", content.contains("@Immutable"))

        // Instantiate default state
        val state = TrackingMapState()
        assertEquals(false, state.showMap)
        assertTrue(state.currentTrack.isEmpty())
        assertTrue(state.mapTracks.isEmpty())
        assertTrue(state.mapSegments.isEmpty())
        assertTrue(state.mapRoutes.isEmpty())
        assertTrue(state.mapMarkers.isEmpty())
    }

    @Test
    fun testTrackingScreenState_incorporatesMapStateWithBackwardCompatibility() {
        val screenState = TrackingScreenState()
        assertNotNull("TrackingScreenState must expose mapState", screenState.mapState)
        assertEquals(screenState.showMap, screenState.mapState.showMap)

        // Verify delegated getters match mapState values
        assertEquals(screenState.zoomFocus, screenState.mapState.zoomFocus)
        assertEquals(screenState.userBearing, screenState.mapState.userBearing, 0.001f)
        assertEquals(screenState.userSpeed, screenState.mapState.userSpeed, 0.001f)
        assertEquals(screenState.bSportType, screenState.mapState.bSportType)
        assertEquals(screenState.currentTrack, screenState.mapState.currentTrack)
        assertEquals(screenState.mapTracks, screenState.mapTracks)
        assertEquals(screenState.mapRoutes, screenState.mapRoutes)
    }

    @Test
    fun testSensorGridScreen_definesTrackingMapContainerWithMemoizedContent() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = screenFile.readText()

        // 1. Verify TrackingMapContainer definition
        assertTrue("SensorGridScreen.kt must define TrackingMapContainer composable", content.contains("fun TrackingMapContainer("))

        // 2. Verify MapContentScope lambda is memoized with remember
        assertTrue(
            "TrackingMapContainer must memoize mapContent with remember to prevent layer DSL allocations",
            content.contains("remember(") && content.contains("mapState.mapTracks") && content.contains("mapState.mapRoutes")
        )

        // 3. Extract SensorGridScreen function body and verify it delegates to TrackingMapContainer
        val fnStart = content.indexOf("fun SensorGridScreen(")
        val fnEnd = content.indexOf("fun TrackingMapContainer(")
        assertTrue("SensorGridScreen and TrackingMapContainer must both be located", fnStart != -1 && fnEnd > fnStart)
        val sensorGridBody = content.substring(fnStart, fnEnd)

        assertTrue(
            "SensorGridScreen must invoke TrackingMapContainer",
            sensorGridBody.contains("TrackingMapContainer(")
        )
        assertFalse(
            "SensorGridScreen body must NOT invoke ATrainingTrackerMap directly",
            sensorGridBody.contains("ATrainingTrackerMap(")
        )
    }
}
