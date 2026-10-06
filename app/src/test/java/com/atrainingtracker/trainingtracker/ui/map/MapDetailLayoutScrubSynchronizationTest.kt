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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying that MapDetailLayout, ATrainingTrackerMap,
 * ScrubMarkerLayer, ScrubberController, and TelemetryMetricGraph correctly propagate
 * and synchronize activeScrubPoint across time and distance domains (REQ-MAP-032 / TST-MAP-034).
 */
class MapDetailLayoutScrubSynchronizationTest {

    @Test
    fun scrubMarkerLayerAcceptsActiveScrubPointParameter() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt")
        assertTrue("MapLayers.kt should exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ScrubMarkerLayer must declare activeScrubPoint parameter",
            content.contains("activeScrubPoint: PathPoint? = null")
        )
        assertTrue(
            "ScrubMarkerLayer must resolve point prioritizing activeScrubPoint",
            content.contains("val point = activeScrubPoint ?: selectedDistance?.let")
        )
        assertTrue(
            "ScrubMarkerLayer must guard against (0,0) trackless coordinates",
            content.contains("point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0")
        )
    }

    @Test
    fun scrubberControllerAcceptsActiveScrubPointParameter() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt")
        assertTrue("MapBehaviors.kt should exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ScrubberController must declare activeScrubPoint parameter",
            content.contains("activeScrubPoint: PathPoint? = null")
        )
        assertTrue(
            "ScrubberController must resolve point prioritizing activeScrubPoint",
            content.contains("val scrubPoint = activeScrubPoint ?: selectedDistance?.let")
        )
        assertTrue(
            "ScrubberController must guard against (0,0) trackless coordinates",
            content.contains("point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0")
        )
    }

    @Test
    fun aTrainingTrackerMapForwardsActiveScrubPoint() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt")
        assertTrue("ATrainingTrackerMap.kt should exist", file.exists())
        val content = file.readText()

        assertTrue(
            "ATrainingTrackerMap must declare activeScrubPoint parameter",
            content.contains("activeScrubPoint: PathPoint? = null")
        )
        assertTrue(
            "ATrainingTrackerMap must forward activeScrubPoint to ScrubberController",
            content.contains("ScrubberController(selectedDistance, scrubPath, cameraPositionState, activeScrubPoint = activeScrubPoint)")
        )
        assertTrue(
            "ATrainingTrackerMap must forward activeScrubPoint to ScrubMarkerLayer",
            content.contains("ScrubMarkerLayer(selectedDistance, scrubPath, scrubIcons.second, scrubIcons.first, activeScrubPoint = activeScrubPoint)")
        )
    }

    @Test
    fun telemetryMetricGraphSupportsOnPointSelectedCallback() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
        assertTrue("TelemetryMetricGraph.kt should exist", file.exists())
        val content = file.readText()

        assertTrue(
            "TelemetryMetricGraph must declare onPointSelected parameter",
            content.contains("onPointSelected: (PathPoint?) -> Unit = {}")
        )
        assertTrue(
            "TelemetryMetricGraph must remember currentOnPointSelectedState",
            content.contains("val currentOnPointSelectedState by rememberUpdatedState(onPointSelected)")
        )
        assertTrue(
            "TelemetryMetricGraph must invoke currentOnPointSelectedState on drag/tap",
            content.contains("currentOnPointSelectedState(nearest)")
        )
        assertTrue(
            "TelemetryMetricGraph must invoke currentOnPointSelectedState(null) when drag completes",
            content.contains("currentOnPointSelectedState(null)")
        )
    }

    @Test
    fun mapDetailLayoutWiresOnPointSelectedAndPassesActiveScrubPoint() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        assertTrue("MapDetailLayout.kt should exist", file.exists())
        val content = file.readText()

        assertTrue(
            "MapDetailLayout must remember touchedScrubPoint state",
            content.contains("var touchedScrubPoint by remember { mutableStateOf<PathPoint?>(null) }")
        )
        assertTrue(
            "MapDetailLayout must forward activeScrubPoint to ATrainingTrackerMap",
            content.contains("activeScrubPoint = activeScrubPoint,")
        )
        assertTrue(
            "MapDetailLayout must wire onPointSelected on ElevationProfile",
            content.contains("onPointSelected = { touchedScrubPoint = it },")
        )
    }
}
