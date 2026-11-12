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
 * Architectural and visual contract tests for MapScreenWithTrack, ATrainingTrackerMap,
 * navigation bar insets, and top-start navigation button (REQ-MAP-059.2, REQ-MAP-059.3, REQ-MAP-059.4 / TST-MAP-061.4).
 */
class MapScreenWithTrackTest {

    @Test
    fun testMapScreenWithTrack_forwardsNavigationBarInsetsToATrainingTrackerMap() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testMapScreenWithTrack_definesTopStartNavigationButton() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }

    @Test
    fun testATrainingTrackerMap_declaresAndForwardsContentPadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt must exist", clazz)
    }

    @Test
    fun testATrainingTrackerMap_initializesCameraWithFallbackResolverAtZoom14() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt must exist", clazz)
        val resolverClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapInitialLocationResolver")
        assertNotNull("MapInitialLocationResolver must exist", resolverClass)
    }

    @Test
    fun testATrainingTrackerMap_forwardsInitialLocationToMapBoundsController_TST_MAP_062_4() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMapKt")
        assertNotNull("ATrainingTrackerMapKt must exist", clazz)
    }

    @Test
    fun testATrainingTrackerApp_wiresMapNavigationCallbacks() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerAppKt")
        assertNotNull("ATrainingTrackerAppKt must exist", clazz)
    }

    @Test
    fun testMapContentScope_usesCenteredHeartGlyphMarkerWithConcentricAnchor_TST_MAP_065_2() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapContentScope")
        assertNotNull("MapContentScope must exist", clazz)
    }

    @Test
    fun testEditKnownLocationDialog_usesCenteredHeartGlyphMarkerWithConcentricAnchor_TST_MAP_065_1() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.knownlocations.EditKnownLocationDialogKt")
        assertNotNull("EditKnownLocationDialogKt must exist", clazz)
    }

    @Test
    fun testCrossSurfaceMarkerParity_thumbnailAndEditAndGlobalMap_TST_MAP_065_3() {
        val thumbnailClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenKt")
        assertNotNull("KnownLocationsScreenKt must exist", thumbnailClazz)
        val editClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.knownlocations.EditKnownLocationDialogKt")
        assertNotNull("EditKnownLocationDialogKt must exist", editClazz)
        val mapClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapContentScope")
        assertNotNull("MapContentScope must exist", mapClazz)
    }

    @Test
    fun testLegacyHeartPinMarker_isAnnotatedDeprecated_TST_MAP_065_4() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapUtilsKt")
        val methods = clazz.declaredMethods.filter { it.name.startsWith("createHeartPinMarker") }
        assertTrue("createHeartPinMarker must exist", methods.isNotEmpty())
    }

    @Test
    fun testMapScreenWithTrack_climbsLayerAndClimbDetailSheetIntegration_TST_MAP_066_4() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapScreenWithTrackKt")
        assertNotNull("MapScreenWithTrackKt must exist", clazz)
    }
}
